package com.github.somtooo.gitnotify.services

import com.github.somtooo.gitnotify.lib.github.GithubRequests
import com.github.somtooo.gitnotify.lib.github.data.PullRequest
import com.github.somtooo.gitnotify.lib.github.data.PullRequestState
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import io.ktor.client.plugins.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlin.time.TimeMark
import kotlin.time.TimeSource

data class ReviewRequestedContext(
    val pullRequestUrl: String,
)

interface ReviewRequestedNotifier {
    companion object {
        @Topic.ProjectLevel
        val REVIEW_REQUESTED_TOPIC: Topic<ReviewRequestedNotifier> = Topic.create(
            "GitNotify.ReviewRequested",
            ReviewRequestedNotifier::class.java
        )
    }

    fun onReviewRequested(context: ReviewRequestedContext)
}


@Service(Service.Level.PROJECT)
class GithubNotification(private val project: Project, private val scope: CoroutineScope) {
    private var githubRequests = GithubRequests()
    private val reasonKey = "review_requested"

    private val notifier =
        NotificationGroupManager.getInstance().getNotificationGroup("StickyBalloon")
    private val errorNotifier = NotificationGroupManager.getInstance().getNotificationGroup("NonStickyBalloon")
    private val logger = logger<GithubNotification>()

    private val maxRetries = 5

    private val initialBaseDelay = 3000L
    private var pullRequestLastModified: Map<String, String> = mapOf()
    private var lastPullRequest: Map<String, PullRequest> = mapOf()
    private var defaultXPollHeader = 60000L

    companion object {
        fun getInstance(project: Project): GithubNotification {
            return project.service()
        }
    }

    // Use for testing to mock requests
    internal fun setGithubRequestsForTest(instance: GithubNotification, requests: GithubRequests) {
        instance.githubRequests = requests
    }

    fun pollForNotifications(
        dispatcher: CoroutineDispatcher = Dispatchers.Default

    ): Job {
        val notificationThreadIdToPullRequestNumber = mutableMapOf<String, String>()
        var retryCount = 0
        var lastCleanupTime = TimeSource.Monotonic.markNow()
        val hourInMillis = 3600000L
        return scope.launch(dispatcher) {
            while (isActive) {
                try {
                    val (baseDelay, updatedMap, updatedRetry, updatedCleanup) = pollOnce(
                        notificationThreadIdToPullRequestNumber,
                        retryCount,
                        lastCleanupTime,
                        hourInMillis
                    )
                    // update state for next iteration
                    notificationThreadIdToPullRequestNumber.clear()
                    notificationThreadIdToPullRequestNumber.putAll(updatedMap)
                    retryCount = updatedRetry
                    lastCleanupTime = updatedCleanup
                    delay(baseDelay)
                } catch (e: Exception) {
                    logger.error("Unexpected error in notification polling", e)
                    return@launch
                }
            }
        }
    }

    /**
     * pollOnce now takes state as parameters (with defaults for tests).
     * Returns baseDelay, updated map, retry count, and cleanup time.
     */
    internal suspend fun pollOnce(
        notificationThreadIdToPullRequestNumber: MutableMap<String, String> = mutableMapOf(),
        retryCountIn: Int = 0,
        lastCleanupTimeIn: TimeMark = TimeSource.Monotonic.markNow(),
        hourInMillis: Long = 3600000L
    ): Quad<Long, MutableMap<String, String>, Int, TimeMark> {
        var baseDelay = initialBaseDelay
        var retryCount = retryCountIn
        var lastCleanupTime = lastCleanupTimeIn
        try {
            val notificationThreadResponses = githubRequests.getRepositoryNotifications()
            notificationThreadResponses.headers["x-poll-interval"]?.let {
                val pollInterval = it.toLong()
                baseDelay = pollInterval * baseDelay / defaultXPollHeader
            }
            for (notificationThreadResponse in notificationThreadResponses.notificationThreads) {
                if (notificationThreadResponse.reason == reasonKey) {
                    if (notificationThreadIdToPullRequestNumber[notificationThreadResponse.id] == null) {
                        val pullRequestUrl = notificationThreadResponse.subject.url
                        val urlPaths = pullRequestUrl.split(Regex("//|/"))
                        val pullRequestNumber = urlPaths.last()
                        val pullRequestResponse = getPullRequest(pullRequestNumber)
                        if (pullRequestResponse.state == PullRequestState.CLOSED) {
                            githubRequests.markNotificationThreadAsRead(notificationThreadResponse.id)
                        } else {
                            val content =
                                "${pullRequestResponse.user.login.toUpperCasePreservingASCIIRules()} has requested you review their PR"
                            notifyPullRequest(content)
                            val publisher =
                                project.messageBus.syncPublisher(ReviewRequestedNotifier.REVIEW_REQUESTED_TOPIC)
                            publisher.onReviewRequested(ReviewRequestedContext(pullRequestUrl = pullRequestUrl))
                            notificationThreadIdToPullRequestNumber[notificationThreadResponse.id] =
                                pullRequestNumber
                        }
                    }
                }
            }
            val currentMark = lastCleanupTime.elapsedNow().inWholeMilliseconds
            if (currentMark > hourInMillis) {
                val iterator = notificationThreadIdToPullRequestNumber.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    val pullRequestResponse = getPullRequest(entry.value)
                    if (pullRequestResponse.state == PullRequestState.CLOSED) {
                        iterator.remove()
                    }
                }
                lastCleanupTime = TimeSource.Monotonic.markNow()
            }
            retryCount = 0
        } catch (e: Exception) {
            retryCount = retryCount + 1
            try {
                baseDelay = handleRateLimitException(e, retryCount)
            } catch (ex: Exception) {
                // propagate to outer loop
                throw ex
            }
        }
        return Quad(baseDelay, notificationThreadIdToPullRequestNumber, retryCount, lastCleanupTime)
    }

    private fun handleRateLimitException(e: Exception, retryCount: Int): Long {
        when (e) {
            is ClientRequestException -> {
                val isRateLimitIssue = e.message.contains("rate limit", ignoreCase = true) &&
                        e.response.status in listOf(HttpStatusCode.TooManyRequests, HttpStatusCode.Forbidden)

                if (isRateLimitIssue) {
                    return handleRateLimiting(e.response, retryCount)
                }

                logger.debug("Client error in main loop: ${e.message}")
                notifyError("Client error: ${e.message}")
                throw e
            }

            else -> {
                logger.debug("Unexpected error in notification polling", e)
                notifyError("Unexpected error: ${e.message}")
                throw e
            }
        }
    }

    private suspend fun getPullRequest(
        pullNumber: String,
    ): PullRequest {

        try {
            val response = githubRequests.getAPullRequest(pullNumber, pullRequestLastModified[pullNumber])

            response.headers["last-modified"]?.let { modifiedHeader ->
                pullRequestLastModified = pullRequestLastModified + (pullNumber to modifiedHeader)
            }
            lastPullRequest = lastPullRequest + (pullNumber to response.pullRequest)
            return response.pullRequest
        } catch (e: RedirectResponseException) {
            if (e.response.status == HttpStatusCode.NotModified) {
                e.response.headers["last-modified"]?.let { modifiedHeader ->
                    pullRequestLastModified = pullRequestLastModified + (pullNumber to modifiedHeader)
                }
                return lastPullRequest[pullNumber]
                    ?: throw IllegalStateException("No cached pull request found for $pullNumber")
            }

            throw e
        }
    }

    private fun handleRateLimiting(response: HttpResponse, retryCount: Int): Long {

        // Primary rate limit check
        if (response.headers["x-ratelimit-remaining"] == "0") {
            val resetTime = response.headers["x-ratelimit-reset"]?.toLongOrNull()
            if (resetTime != null) {
                val currentTime = System.currentTimeMillis() / 1000
                val newDelay = (resetTime - currentTime).coerceAtLeast(0) * 1000
                logger.warn("Primary rate limit exceeded. Setting delay to ${newDelay / 1000} seconds before retrying")
                notifyError("Primary rate limit exceeded. Will retry in ${newDelay / 1000} seconds")
                return newDelay
            }
        }

        // Secondary rate limit check
        val retryAfter = response.headers["retry-after"]?.toLongOrNull()
        if (retryAfter != null) {
            val newDelay = retryAfter * 1000
            logger.warn("Secondary rate limit exceeded. Retry after $retryAfter seconds")
            notifyError("Secondary rate limit exceeded. Will retry in $retryAfter seconds")
            return newDelay
        }

        // Exponential backoff for secondary rate limit without retry-after header
        if (retryCount < maxRetries) {
            val exponentialDelay = (60000L * (1L shl (retryCount - 1))).coerceAtMost(3600000L) // Max 1 hour
            logger.warn("Secondary rate limit exceeded. Using exponential backoff: ${exponentialDelay / 1000} seconds (attempt $retryCount of $maxRetries)")
            notifyError("Secondary rate limit exceeded. Will retry in ${exponentialDelay / 1000} seconds (attempt $retryCount of $maxRetries)")
            return exponentialDelay
        } else {
            return 0;
//            logger.error("Maximum retry attempts ($maxRetries) reached for secondary rate limit")
//            notifyError("Maximum retry attempts reached for secondary rate limit. Please try again later.")
//            throw Exception("Maximum retry attempts reached for secondary rate limit")
        }
    }

    private fun notifyPullRequest(content: String) {
        val notify = notifier.createNotification(
            title = "Git-Notify",
            content,
            NotificationType.INFORMATION
        )
        notify.addAction(NotificationAction.createSimpleExpiring("Reviewed") {
            notify.expire()
        })
        notify.notify(project)
    }

    private fun notifyError(content: String) {
        errorNotifier.createNotification(
            title = "Git-Notify Error",
            content,
            NotificationType.ERROR
        ).notify(project)
    }

    // Helper data class for returning 4 values
    data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}