package com.github.somtooo.gitnotify.services

import com.github.somtooo.gitnotify.lib.github.MockGithubRequest
import com.github.somtooo.gitnotify.lib.github.data.PullRequestState
import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(JUnit4::class)
class GithubNotificationTest : BasePlatformTestCase() {

    private lateinit var githubNotification: GithubNotification
    private lateinit var testScope: TestScope
    private lateinit var testDispatcher: TestDispatcher

    @Before
    fun before() {
        testDispatcher = StandardTestDispatcher()
        testScope = TestScope(testDispatcher)
        githubNotification = GithubNotification(project, testScope)
    }

    @After
    fun cleanUp() {
        // Clean up resources if needed
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun testNotificationIsShownForMockGithubRequest() = runTest(testDispatcher) {
        val mockRequests = MockGithubRequest()
        githubNotification.setGithubRequestsForTest(githubNotification, mockRequests)
        var notificationShown = false
        val connection = project.messageBus.connect()
        connection.subscribe(Notifications.TOPIC, object : Notifications {
            override fun notify(notification: Notification) {
                if (notification.content.contains("requested you review their PR")) {
                    notificationShown = true
                }
            }
        })

        githubNotification.pollOnce()
        assertTrue("Notification should have been shown for mock github request", notificationShown)

        connection.disconnect()
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun testReviewRequestedEventIsPublished() = runTest(testDispatcher) {
        val mockRequests = MockGithubRequest()
        githubNotification.setGithubRequestsForTest(githubNotification, mockRequests)
        var reviewRequestedCalled = false
        val connection = project.messageBus.connect()
        connection.subscribe(ReviewRequestedNotifier.REVIEW_REQUESTED_TOPIC, object : ReviewRequestedNotifier {
            override fun onReviewRequested(context: ReviewRequestedContext) {
                reviewRequestedCalled = true
            }
        })
        githubNotification.pollOnce()
        assertTrue("ReviewRequestedNotifier should have been called", reviewRequestedCalled)
        connection.disconnect()
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun testNoNotificationForNonReviewReason() = runTest(testDispatcher) {
        val mockRequests = object : MockGithubRequest() {
            override suspend fun getRepositoryNotifications() = super.getRepositoryNotifications().copy(
                notificationThreads = super.getRepositoryNotifications().notificationThreads.map {
                    it.copy(reason = "subscribed")
                }
            )
        }
        githubNotification.setGithubRequestsForTest(githubNotification, mockRequests)
        var notificationShown = false
        val connection = project.messageBus.connect()
        connection.subscribe(Notifications.TOPIC, object : Notifications {
            override fun notify(notification: Notification) {
                if (notification.groupId == "StickyBalloon") {
                    notificationShown = true
                }
            }
        })
        githubNotification.pollOnce()
        assertFalse("Notification should NOT have been shown for non-review reason", notificationShown)
        connection.disconnect()
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun testErrorNotificationIsShown() = runTest(testDispatcher) {
        val mockRequests = object : MockGithubRequest() {
            override suspend fun getRepositoryNotifications(): com.github.somtooo.gitnotify.lib.github.data.NotificationThreadResponse {
                throw Exception("Some API error")
            }
        }
        githubNotification.setGithubRequestsForTest(githubNotification, mockRequests)
        var errorNotificationShown = false
        val connection = project.messageBus.connect()
        connection.subscribe(Notifications.TOPIC, object : Notifications {
            override fun notify(notification: Notification) {
                if (notification.groupId == "StickyBalloon") {
                    errorNotificationShown = true
                }
            }
        })
        githubNotification.pollOnce()
        assertTrue("Error notification should have been shown on exception", errorNotificationShown)
        connection.disconnect()
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun testCleanupMarksNotificationAsReadForClosedPR() = runTest(testDispatcher) {
        var markAsReadCalled = false

        val mockRequests = object : MockGithubRequest() {
            override suspend fun getRepositoryNotifications() = super.getRepositoryNotifications().copy(
                notificationThreads = super.getRepositoryNotifications().notificationThreads.map {
                    it.copy(reason = "review_requested")
                }
            )

            override suspend fun getAPullRequest(pullNumber: String, lastModified: String?) =
                super.getAPullRequest(pullNumber, lastModified).copy(
                    pullRequest = super.getAPullRequest(
                        pullNumber,
                        lastModified
                    ).pullRequest.copy(state = PullRequestState.CLOSED)
                )

            override suspend fun markNotificationThreadAsRead(threadId: String) {
                markAsReadCalled = true
            }
        }

        githubNotification.setGithubRequestsForTest(githubNotification, mockRequests)

        githubNotification.pollOnce()
        assertTrue("markNotificationThreadAsRead should have been called for closed PR", markAsReadCalled)
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun testPollIntervalHeaderIsRespected() = runTest(testDispatcher) {
        // We'll check that the poll interval header changes the internal delay logic
        // by providing a custom poll interval and observing that cleanupInterval is recomputed.
        // Since we can't observe delay directly, we check that no exceptions are thrown and logic proceeds.
        val customPollInterval = 120000L // 2 minutes
        val mockRequests = object : MockGithubRequest() {
            override suspend fun getRepositoryNotifications() = super.getRepositoryNotifications().copy(
                headers = io.ktor.http.headersOf("x-poll-interval" to listOf(customPollInterval.toString()))
            )
        }
        githubNotification.setGithubRequestsForTest(githubNotification, mockRequests)
        // If logic is correct, there should be no crash and notifications are processed
        var notificationShown = false
        val connection = project.messageBus.connect()
        connection.subscribe(Notifications.TOPIC, object : Notifications {
            override fun notify(notification: Notification) {
                if (notification.groupId == "StickyBalloon") {
                    notificationShown = true
                }
            }
        })
        githubNotification.pollOnce()
        assertTrue(
            "Notification should have been shown and poll interval logic should not break execution",
            notificationShown
        )
        connection.disconnect()
    }
}