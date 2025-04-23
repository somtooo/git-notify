package com.github.somtooo.gitnotify.services

import com.github.somtooo.gitnotify.lib.github.GithubRequests
import com.github.somtooo.gitnotify.lib.github.data.PullRequest
import com.github.somtooo.gitnotify.lib.github.data.PullRequestState
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import io.ktor.client.plugins.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
    private val githubRequests = GithubRequests()
    private val reasonKey = "review_requested"

    // Get a non sticky ballon for errors
    private val notifier =
        NotificationGroupManager.getInstance().getNotificationGroup("StickyBalloon")
    private val logger = logger<GithubNotification>()

    private var retryCount = 0
    private val maxRetries = 5

    // set this from the X-Poll_interval from response header of notifications
    // let the notification response include its header so it can be used to configure base delay
    // lets keep at 3 seconds unless x-poll is bigger than that
    private val initialBaseDelay = 3000L
    private var pullRequestLastModified: Map<String, String> = mapOf()
    private var lastPullRequest: Map<String, PullRequest> = mapOf()

    fun pollForNotifications() {
        val notificationThreadIdToPullRequestNumber = mutableMapOf<String, String>()
        var cleanupCounter = 0
        var baseDelay = initialBaseDelay
        var cleanupInterval: Long

        scope.launch {
            while (isActive) {
                try {
                    val notificationThreadResponses = githubRequests.getRepositoryNotifications()
                    retryCount = 0
                    //should be set from the response header
                    baseDelay = initialBaseDelay
                    cleanupInterval = 3600 / (baseDelay / 1000)

                    for (notificationThreadResponse in notificationThreadResponses) {
                        if (notificationThreadResponse.reason == reasonKey) {
                            if (notificationThreadIdToPullRequestNumber[notificationThreadResponse.id] !== null) {
                                val pullRequestUrl = notificationThreadResponse.subject.url
                                val urlPaths = pullRequestUrl.split(Regex("//|/"))
                                val pullRequestNumber = urlPaths.last()

                                val pullRequestResponse = getPullRequest(pullRequestNumber)
                                if (pullRequestResponse.state !== PullRequestState.CLOSED) {
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

                    if (cleanupCounter >= cleanupInterval) {
                        val iterator = notificationThreadIdToPullRequestNumber.iterator()
                        while (iterator.hasNext()) {
                            val entry = iterator.next()
                            val pullRequestResponse = getPullRequest(entry.value)
                            if (pullRequestResponse.state == PullRequestState.CLOSED) {
                                githubRequests.markNotificationThreadAsRead(entry.key)
                                iterator.remove()
                            }
                        }
                        cleanupCounter = 0
                    }
                    cleanupCounter++
                } catch (e: Exception) {
                    when (e) {
                        is ClientRequestException -> {
                            if (e.message.contains("rate limit", ignoreCase = true)) {
                                handleRateLimiting(e.response, baseDelay)?.let { newDelay ->
                                    baseDelay = newDelay
                                }
                            } else {
                                logger.error("Client error in main loop: ${e.message}")
                                notifyError("Client error: ${e.message}")
                                return@launch
                            }
                        }

                        else -> {
                            logger.error("Unexpected error in notification polling", e)
                            notifyError("Unexpected error: ${e.message}")
                            return@launch
                        }
                    }
                }

                delay(baseDelay)
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

    private fun handleRateLimiting(response: HttpResponse, currentDelay: Long): Long? {
        // This is not needed here let the top level function decide
        val isRateLimit = response.status.value == 429 ||
                (response.status.value == 403)

        if (!isRateLimit) return null

        // Primary rate limit check
        if (response.headers["x-ratelimit-remaining"] == "0") {
            val resetTime = response.headers["x-ratelimit-reset"]?.toLongOrNull()
            if (resetTime != null) {
                val currentTime = System.currentTimeMillis() / 1000
                val newDelay = (resetTime - currentTime).coerceAtLeast(0) * 1000
                logger.warn("Primary rate limit exceeded. Setting delay to ${newDelay / 1000} seconds before retrying")
                notifyError("Primary rate limit exceeded. Will retry in ${newDelay / 1000} seconds")
                retryCount = 0 // Reset retry count for primary rate limit
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
            retryCount++
            val exponentialDelay = (60000L * (1L shl (retryCount - 1))).coerceAtMost(3600000L) // Max 1 hour
            logger.warn("Secondary rate limit exceeded. Using exponential backoff: ${exponentialDelay / 1000} seconds (attempt $retryCount of $maxRetries)")
            notifyError("Secondary rate limit exceeded. Will retry in ${exponentialDelay / 1000} seconds (attempt $retryCount of $maxRetries)")
            return exponentialDelay
        } else {
            logger.error("Maximum retry attempts ($maxRetries) reached for secondary rate limit")
            notifyError("Maximum retry attempts reached for secondary rate limit. Please try again later.")
            return currentDelay
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
        notifier.createNotification(
            title = "Git-Notify Error",
            content,
            NotificationType.ERROR
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
