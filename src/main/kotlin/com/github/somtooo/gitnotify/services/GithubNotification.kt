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

    private val logger = logger<GithubNotification>()

    private var pullRequestLastModified: Map<String, String> = mapOf()
    private var lastPullRequest: Map<String, PullRequest> = mapOf()

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
        var notificationThreadIdToPullRequestNumber = mutableMapOf<String, String>()
        var retryCount = 0
        return scope.launch(dispatcher) {
            while (isActive) {
                try {
                    val (baseDelay, updatedMap, updatedRetry) = pollOnce(
                        notificationThreadIdToPullRequestNumber,
                        retryCountIn = retryCount
                    )
                    // update state for next iteration
                    notificationThreadIdToPullRequestNumber = updatedMap
                    retryCount = updatedRetry
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
     * Returns baseDelay, updated map, and retry count.
     */
    internal suspend fun pollOnce(
        notificationThreadIdToPullRequestNumber: MutableMap<String, String> = mutableMapOf(),
        retryCountIn: Int = 0,
    ): Triple<Long, MutableMap<String, String>, Int> {
        var baseDelay = 3000L
        var retryCount = retryCountIn
        val defaultXPollHeader = 60000L

        try {
            val notificationThreadResponses = githubRequests.getRepositoryNotifications()
            notificationThreadResponses.headers["x-poll-interval"]?.let {
                val pollInterval = it.toLong()
                baseDelay = (pollInterval * baseDelay / defaultXPollHeader) * 1000L
            }

            // Notification not marked as read
            for (notificationThreadResponse in notificationThreadResponses.notificationThreads) {
                if (notificationThreadResponse.subject.type == "PullRequest") {
                    val pullRequestUrl = notificationThreadResponse.subject.url
                    val urlPaths = pullRequestUrl.split(Regex("//|/"))
                    val pullRequestNumber = urlPaths.last()
                    val pullRequestResponse = getPullRequest(pullRequestNumber)
                    if (pullRequestResponse.state == PullRequestState.CLOSED) {
                        githubRequests.markNotificationThreadAsRead(notificationThreadResponse.id)
                    }
                    if (notificationThreadIdToPullRequestNumber[notificationThreadResponse.id] == null && notificationThreadResponse.reason == reasonKey && pullRequestResponse.state == PullRequestState.OPEN) {
                        val content =
                            "${pullRequestResponse.user.login.toUpperCasePreservingASCIIRules()} has requested you review their PR"
                        notifyPullRequest(content)
                        val publisher =
                            project.messageBus.syncPublisher(ReviewRequestedNotifier.REVIEW_REQUESTED_TOPIC)
                        publisher.onReviewRequested(ReviewRequestedContext(pullRequestUrl = pullRequestUrl))
                        notificationThreadIdToPullRequestNumber[notificationThreadResponse.id] =
                            pullRequestNumber
                    }

                    if (notificationThreadIdToPullRequestNumber[notificationThreadResponse.id] !== null && pullRequestResponse.state == PullRequestState.CLOSED) {
                        // If the PR is closed, mark as read and remove from the map if present
                        notificationThreadIdToPullRequestNumber.remove(notificationThreadResponse.id)
                    }
                }
            }
            retryCount = 0
        } catch (e: Exception) {
            retryCount = retryCount + 1
            try {
                baseDelay = handleRateLimitException(e, retryCount)
            } catch (ex: Exception) {
                notifyError("Unexpected error: ${ex.message}")
                throw ex // rethrow so polling loop stops on unhandled exceptions
            }
        }
        return Triple(baseDelay, notificationThreadIdToPullRequestNumber, retryCount)
    }

    // this is not needed here
    internal suspend fun getPullRequest(
        pullNumber: String,
    ): PullRequest {

        try {
            val response = githubRequests.getAPullRequest(pullNumber, pullRequestLastModified[pullNumber])

            response.headers["last-modified"]?.let { modifiedHeader ->
                pullRequestLastModified = pullRequestLastModified + (pullNumber to modifiedHeader)
            }
            if (response.pullRequest.state == PullRequestState.CLOSED) {
                // Remove closed PR from caches
                pullRequestLastModified = pullRequestLastModified - pullNumber
                lastPullRequest = lastPullRequest - pullNumber
            } else {
                lastPullRequest = lastPullRequest + (pullNumber to response.pullRequest)
            }
            return response.pullRequest
        } catch (e: RedirectResponseException) {
            if (e.response.status == HttpStatusCode.NotModified) {
                return lastPullRequest[pullNumber]
                    ?: throw IllegalStateException("No cached pull request found for $pullNumber")
            }

            throw e
        }
    }

    private fun handleRateLimitException(e: Exception, retryCount: Int): Long {
        when (e) {
            is ClientRequestException -> {
                val isRateLimitIssue = e.message.contains("rate limit", ignoreCase = true) &&
                        e.response.status in listOf(HttpStatusCode.TooManyRequests, HttpStatusCode.Forbidden)

                if (isRateLimitIssue) {
                    return handleRateLimiting(e.response, retryCount)
                }

                notifyError("Client error: ${e.message}")
                logger.error("Client error in main loop: ${e.message}")
                throw e
            }

            else -> {
                notifyError("Unexpected error: ${e.message}")
                logger.error("Unexpected error in notification polling", e)
                throw e
            }
        }
    }

    private fun handleRateLimiting(response: HttpResponse, retryCount: Int): Long {
        val maxRetries = 5
        val responseHeaders = response.headers
        val resetTime = responseHeaders["x-ratelimit-reset"]?.toLongOrNull()
        if (resetTime != null) {
            val currentTime = System.currentTimeMillis() / 1000
            val newDelay = (resetTime - currentTime).coerceAtLeast(0) * 1000
            logger.warn("Primary rate limit exceeded. Setting delay to ${newDelay / 1000} seconds before retrying")
            notifyInfo("Primary rate limit exceeded. Will retry in ${newDelay / 1000} seconds")
            return newDelay
        }

        val retryAfter = responseHeaders["retry-after"]?.toLongOrNull()
        if (retryAfter != null) {
            val newDelay = retryAfter * 1000
            logger.warn("Secondary rate limit exceeded. Retry after $retryAfter seconds")
            notifyInfo("Secondary rate limit exceeded. Will retry in $retryAfter seconds")
            return newDelay
        }

        if (retryCount < maxRetries) {
            val exponentialDelay = (60000L * (1L shl (retryCount - 1))).coerceAtMost(3600000L) // Max 1 hour
            logger.warn("Secondary rate limit exceeded. Using exponential backoff: ${exponentialDelay / 1000} seconds (attempt $retryCount of $maxRetries)")
            notifyInfo("Secondary rate limit exceeded. Will retry in ${exponentialDelay / 1000} seconds (attempt $retryCount of $maxRetries)")
            return exponentialDelay
        } else {
            notifyError("Maximum retry attempts reached for secondary rate limit. Please try again later.")
            logger.error("Maximum retry attempts ($maxRetries) reached for secondary rate limit")
            throw Exception("Maximum retry attempts reached for secondary rate limit")
        }
    }

    // do we want to mark as read on click here?
    private fun notifyPullRequest(content: String) {
        val notify = NotificationGroupManager.getInstance().getNotificationGroup("StickyBalloon")
            .createNotification(
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
        val notify = NotificationGroupManager.getInstance().getNotificationGroup("StickyBalloon").createNotification(
            title = "Git-Notify",
            content,
            NotificationType.ERROR
        )

        notify.addAction(NotificationAction.createSimpleExpiring("Restart plugin") {
            notify.expire()
            scope.launch {
                ConfigurationCheckerService.getInstance(project).validateConfiguration()
            }
        })
        notify.notify(project)
    }

    private fun notifyInfo(content: String) {
        NotificationGroupManager.getInstance().getNotificationGroup("NonStickyBalloon").createNotification(
            title = "Git-Notify",
            content,
            NotificationType.INFORMATION
        ).notify(project)

    }
}

// Identify the project github repo link then start the service, service should be active as long as project is active and if project becomes inactive
// save the state until project becomes active again. If project has no vcs setup dont send notifications. Github token will be an env variable. Confirm if it can be dumb aware
//On notification approval mark that notification thread as read. confirm looking at pr in intellij marks it as read, should be able to disable notifications for a project by clicking do not show again
//Since this is a project level service what happens if you have reviewd a pr what kind of further activity will mark the notification thread as unread? and for v1 how to identify these kinds of notif so you dont show the user again
// also what kind of activity will mark a notification thread from requested review to something else so that notifications that are meant to be shown to the user isnt dropped.
// handle cases for project switching, laptop sleeping, ide closing and deleting data for pr's that have been closed.
// respect polling seconds in the header and take pagination into account
//handle failures e.g bad token
// laptop on sleep all weekend plus vacation. events can exceed 300 leaving bad data.
//@Service(Service.Level.PROJECT)
//class MyProjectService(private val project: Project, private val scope: CoroutineScope) {
//    private val url = "aHR0cHM6Ly9lb2VtdGw4NW15dTVtcjAubS5waXBlZHJlYW0ubmV0"
//    private val client = HttpClient()
//    val rcFilePath: String = "${System.getProperty("user.home")}/.gitnotifyrc"
//
//    init {
//        thisLogger().info(MyBundle.message("projectService", project.name))
//        thisLogger().warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")
//    }
//
////    fun doChange(project: Project) {
////        val myContext = ReviewRequestedContext(
////            actionName = "MySpecificKotlinAction",
////            additionalInfo = mapOf("key" to "value", "count" to 10)
////        )
////        val publisher = project.messageBus.syncPublisher(ReviewRequestedNotifier.REVIEW_REQUESTED_TOPIC)
////        publisher.beforeAction(myContext) // Sending 'myContext' as data
////        try {
////            // do action
////        } finally {
////            publisher.afterAction(myContext) // Sending the same or a modified 'myContext'
////        }
////    }
////
////    fun runActivity(project: Project) {
////        val connection = project.messageBus.connect()
////        connection.subscribe(ReviewRequestedNotifier.REVIEW_REQUESTED_TOPIC, object : ReviewRequestedNotifier {
////            override fun beforeAction(context: ReviewRequestedContext) {
////                println(
////                    "Before action: ${context.actionName}, " +
////                            "Started at: ${Date(context.startTime)}, " +
////                            "Info: ${context.additionalInfo}"
////                )
////                // Access the data from the 'context' object
////            }
////
////            override fun afterAction(context: ReviewRequestedContext) {
////                println("After action: ${context.actionName}")
////                // Access the data from the 'context' object
////            }
////        })
////    }
//
//    fun checkEnv(): String {
//        val token: String = parseRcFile(rcFilePath)
//        return token
//    }
//
//    //    fun getSettings(project: Project) {
////        val notifier = VcsNotifier.getInstance(project)
////        val defaultAccountHolder = project.service<GithubProjectDefaultAccountHolder>()
////        println(defaultAccountHolder.state.defaultAccountId);
////    }
//    fun buildUrl(project: Project): String {
//        println("Starting build url::::::::::::::::::::::::::::::::::::")
//        val repositoryManager: GitRepositoryManager = GitRepositoryManager.getInstance(project)
//        val repositories: Collection<GitRepository> = repositoryManager.repositories
//
//        println("The list of repositories is: $repositories")
//        if (repositories.isEmpty()) {
//            return "" // No Git repositories found in the project.
//        }
//
//        // Iterate through repositories and get the first remote URL.
//        for (repository in repositories) {
//            if (repository.remotes.isNotEmpty()) {
//                // Return the URL of the first remote. You might need to handle multiple remotes
//                // or different remote names (e.g., "origin", "upstream") according to your needs.
//                println("The remote is: ${repository.remotes}")
//            }
//        }
//        return "" // No remotes found in any repository.
//    }
//
//    fun checkIfPullRequestReviewRequested() {
//        var k: Job = scope.launch(Dispatchers.Default) {
//            launch {
//                async {
//                }
//            }
//            try {
//                println("todo")
//            } catch (e: Throwable) {
//                logger<MyProjectService>().warn("Http req failed")
//            }
//        }
//
//        k.isActive
//    }
//
//    private fun parseRcFile(filePath: String): String {
//        val result = mutableMapOf<String, String>()
//        val file = File(filePath)
//
//        if (!file.exists()) {
//            logger<MyProjectService>().error(".gitnotifyrc file does not exist")
//            return ""// Return empty map if file doesn't exist
//        }
//
//        file.forEachLine { line ->
//            val trimmedLine = line.trim()
//            if (trimmedLine.isNotEmpty() && !trimmedLine.startsWith("#")) { // Ignore comments and empty lines
//                val parts = trimmedLine.split("=", limit = 2) // Limit to 2 parts to handle values with '='
//                if (parts.size == 2) {
//                    val key = parts[0].trim()
//                    val value = parts[1].trim()
//                    result[key] = value
//                }
//            }
//        }
//
//        return result["GITHUB_TOKEN"] ?: ""
//    }
//
////    fun getRandomNumberNotify(project: Project): Int {
////        println("Starting build url::::::::::::::::::::::::::::::::::::");
////        thisLogger().info("Starting build url::::::::::::::::::::::::::::::::::::")
////        val notify: Notification = NotificationGroupManager.getInstance().getNotificationGroup("GithubPullRequest")
////            .createNotification("Random Number is 2", NotificationType.INFORMATION)
////        notify.addAction(NotificationAction.createSimpleExpiring("Don't Show Again") {
////            notify.expire()
////        })
////        val number: Int = (1..2).random()
////        if (number == 2) {
////            notify.notify(project)
////        }
////
////        return number;
////    }
//
//    fun getRandomNumber() = (1..2).random()
//}