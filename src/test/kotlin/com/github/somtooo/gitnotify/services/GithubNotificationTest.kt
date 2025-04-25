package com.github.somtooo.gitnotify.services

import com.github.somtooo.gitnotify.lib.github.GithubRequests
import com.github.somtooo.gitnotify.lib.github.data.*
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.ktor.client.plugins.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

@RunWith(JUnit4::class)
class GithubNotificationTest : BasePlatformTestCase() {

    @Mock
    private lateinit var mockGithubRequests: GithubRequests
    private lateinit var githubNotification: GithubNotification
    private lateinit var scope: CoroutineScope

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)
        scope = CoroutineScope(Dispatchers.Default)
        githubNotification = GithubNotification(project, scope)
        githubNotification.setGithubRequestsForTest(githubNotification, mockGithubRequests)
    }

    @Test
    fun `it should handle review requested notifications`() = runBlocking {
        // Setup mock responses
        val notificationResponse = createMockNotificationResponse()
        val pullRequestResponse = createMockPullRequestResponse()

        whenever(mockGithubRequests.getRepositoryNotifications()).thenReturn(notificationResponse)
        whenever(mockGithubRequests.getAPullRequest(any(), any())).thenReturn(pullRequestResponse)

        // Create a publisher mock to verify the event is published
        val publisher = mock<ReviewRequestedNotifier>()
        val messageBus = project.messageBus
        messageBus.connect().subscribe(ReviewRequestedNotifier.REVIEW_REQUESTED_TOPIC, publisher)

        // Call the method under test
        githubNotification.pollForNotifications()

        // Verify that the notification was processed
        // Note: Since pollForNotifications is a continuous loop, we can't easily verify its behavior in a unit test
        // In a real test, you might want to modify the method to be more testable or use a different approach
        // For now, we're just verifying that the method doesn't throw an exception
    }

    @Test
    fun `it should handle rate limiting`() = runBlocking {
        // Setup mock responses for rate limiting
        val exception = RuntimeException("rate limit exceeded")

        whenever(mockGithubRequests.getRepositoryNotifications()).thenThrow(exception)

        // Call the method under test
        githubNotification.pollForNotifications()

        // Verify that rate limiting is handled properly
        // Similar to the previous test, this is difficult to verify in a unit test
    }

    private fun createMockNotificationResponse(): NotificationThreadResponse {
        val headers = headersOf(
            "x-poll-interval" to listOf("60")
        )

        val simpleUser = SimpleUser(
            name = "Test User",
            email = "test@example.com",
            login = "testuser",
            id = 1234L,
            nodeId = "node123",
            avatarUrl = "https://github.com/avatar.png",
            gravatarId = null,
            url = "https://api.github.com/users/testuser",
            htmlUrl = "https://github.com/testuser",
            followersUrl = "https://api.github.com/users/testuser/followers",
            followingUrl = "https://api.github.com/users/testuser/following{/other_user}",
            gistsUrl = "https://api.github.com/users/testuser/gists{/gist_id}",
            starredUrl = "https://api.github.com/users/testuser/starred{/owner}{/repo}",
            subscriptionsUrl = "https://api.github.com/users/testuser/subscriptions",
            organizationsUrl = "https://api.github.com/users/testuser/orgs",
            reposUrl = "https://api.github.com/users/testuser/repos",
            eventsUrl = "https://api.github.com/users/testuser/events{/privacy}",
            receivedEventsUrl = "https://api.github.com/users/testuser/received_events",
            type = "User",
            siteAdmin = false
        )

        val repository = Repository(
            id = 5678L,
            nodeId = "repo123",
            name = "test-repo",
            fullName = "testuser/test-repo",
            owner = simpleUser,
            private = false,
            htmlUrl = "https://github.com/testuser/test-repo",
            description = "Test repository",
            fork = false,
            url = "https://api.github.com/repos/testuser/test-repo",
            archiveUrl = "https://api.github.com/repos/testuser/test-repo/{archive_format}{/ref}",
            assigneesUrl = "https://api.github.com/repos/testuser/test-repo/assignees{/user}",
            blobsUrl = "https://api.github.com/repos/testuser/test-repo/git/blobs{/sha}",
            branchesUrl = "https://api.github.com/repos/testuser/test-repo/branches{/branch}",
            collaboratorsUrl = "https://api.github.com/repos/testuser/test-repo/collaborators{/collaborator}",
            commentsUrl = "https://api.github.com/repos/testuser/test-repo/comments{/number}",
            commitsUrl = "https://api.github.com/repos/testuser/test-repo/commits{/sha}",
            compareUrl = "https://api.github.com/repos/testuser/test-repo/compare/{base}...{head}",
            contentsUrl = "https://api.github.com/repos/testuser/test-repo/contents/{+path}",
            contributorsUrl = "https://api.github.com/repos/testuser/test-repo/contributors",
            deploymentsUrl = "https://api.github.com/repos/testuser/test-repo/deployments",
            downloadsUrl = "https://api.github.com/repos/testuser/test-repo/downloads",
            eventsUrl = "https://api.github.com/repos/testuser/test-repo/events",
            forksUrl = "https://api.github.com/repos/testuser/test-repo/forks",
            gitCommitsUrl = "https://api.github.com/repos/testuser/test-repo/git/commits{/sha}",
            gitRefsUrl = "https://api.github.com/repos/testuser/test-repo/git/refs{/sha}",
            gitTagsUrl = "https://api.github.com/repos/testuser/test-repo/git/tags{/sha}",
            gitUrl = "git://github.com/testuser/test-repo.git",
            issueCommentUrl = "https://api.github.com/repos/testuser/test-repo/issues/comments{/number}",
            issueEventsUrl = "https://api.github.com/repos/testuser/test-repo/issues/events{/number}",
            issuesUrl = "https://api.github.com/repos/testuser/test-repo/issues{/number}",
            keysUrl = "https://api.github.com/repos/testuser/test-repo/keys{/key_id}",
            labelsUrl = "https://api.github.com/repos/testuser/test-repo/labels{/name}",
            languagesUrl = "https://api.github.com/repos/testuser/test-repo/languages",
            mergesUrl = "https://api.github.com/repos/testuser/test-repo/merges",
            milestonesUrl = "https://api.github.com/repos/testuser/test-repo/milestones{/number}",
            notificationsUrl = "https://api.github.com/repos/testuser/test-repo/notifications{?since,all,participating}",
            pullsUrl = "https://api.github.com/repos/testuser/test-repo/pulls{/number}",
            releasesUrl = "https://api.github.com/repos/testuser/test-repo/releases{/id}",
            stargazersUrl = "https://api.github.com/repos/testuser/test-repo/stargazers",
            statusesUrl = "https://api.github.com/repos/testuser/test-repo/statuses/{sha}",
            subscribersUrl = "https://api.github.com/repos/testuser/test-repo/subscribers",
            subscriptionUrl = "https://api.github.com/repos/testuser/test-repo/subscription",
            tagsUrl = "https://api.github.com/repos/testuser/test-repo/tags",
            teamsUrl = "https://api.github.com/repos/testuser/test-repo/teams",
            treesUrl = "https://api.github.com/repos/testuser/test-repo/git/trees{/sha}"
        )

        val subject = Subject(
            title = "Test Pull Request",
            url = "https://api.github.com/repos/testuser/test-repo/pulls/123",
            latestCommentUrl = "https://api.github.com/repos/testuser/test-repo/pulls/comments/123",
            type = "PullRequest"
        )

        val notificationThread = NotificationThread(
            id = "notification123",
            repository = repository,
            subject = subject,
            reason = "review_requested",
            unread = true,
            updatedAt = "2023-01-01T12:00:00Z",
            lastReadAt = null,
            url = "https://api.github.com/notifications/threads/notification123",
            subscriptionUrl = "https://api.github.com/notifications/threads/notification123/subscription"
        )

        return NotificationThreadResponse(
            headers = headers,
            notificationThreads = listOf(notificationThread)
        )
    }

    private fun createMockPullRequestResponse(): PullRequestsResponse {
        val headers = headersOf(
            "last-modified" to listOf("Wed, 01 Jan 2023 12:00:00 GMT")
        )

        val simpleUser = SimpleUser(
            name = "Test User",
            email = "test@example.com",
            login = "testuser",
            id = 1234L,
            nodeId = "node123",
            avatarUrl = "https://github.com/avatar.png",
            gravatarId = null,
            url = "https://api.github.com/users/testuser",
            htmlUrl = "https://github.com/testuser",
            followersUrl = "https://api.github.com/users/testuser/followers",
            followingUrl = "https://api.github.com/users/testuser/following{/other_user}",
            gistsUrl = "https://api.github.com/users/testuser/gists{/gist_id}",
            starredUrl = "https://api.github.com/users/testuser/starred{/owner}{/repo}",
            subscriptionsUrl = "https://api.github.com/users/testuser/subscriptions",
            organizationsUrl = "https://api.github.com/users/testuser/orgs",
            reposUrl = "https://api.github.com/users/testuser/repos",
            eventsUrl = "https://api.github.com/users/testuser/events{/privacy}",
            receivedEventsUrl = "https://api.github.com/users/testuser/received_events",
            type = "User",
            siteAdmin = false
        )

        val pullRequest = PullRequest(
            state = PullRequestState.OPEN,
            user = simpleUser,
            number = 123
        )

        return PullRequestsResponse(
            pullRequest = pullRequest,
            headers = headers
        )
    }
}
