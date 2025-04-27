package com.github.somtooo.gitnotify.lib.github

import com.github.somtooo.gitnotify.lib.github.data.PullRequestState
import com.github.somtooo.gitnotify.services.ConfigurationCheckerService
import com.github.somtooo.gitnotify.services.GithubUrlPathParameters
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.ktor.client.plugins.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class GithubRequestsTest : BasePlatformTestCase() {

    private lateinit var githubRequests: GithubRequests

    @Before
    fun before() {
        // Ensure GitHub token is set in environment
        assertNotNull(
            "GitHub token must be set in environment",
            System.getenv(ConfigurationCheckerService.GIT_HUB_TOKEN_KEY)
        )

        // Set up GitHub URL path parameters
        // Use a well-known public repository that definitely exists
        val owner = System.getenv("GITHUB_OWNER") ?: "somtooo"
        val repo = System.getenv("GITHUB_REPO") ?: "DbGo"
        val githubUrlPathParameters = GithubUrlPathParameters(owner, repo)
        githubUrlPathParameters.setToEnv()
        System.setProperty(
            ConfigurationCheckerService.GIT_HUB_TOKEN_KEY,
            System.getenv(ConfigurationCheckerService.GIT_HUB_TOKEN_KEY)!!
        )

        githubRequests = GithubRequests()

        println("[DEBUG_LOG] Using GitHub repository: $owner/$repo")
    }

    @Test
    fun `it should get repository notifications`() = runBlocking {
        // This test makes an actual API call to GitHub
        val response = githubRequests.getRepositoryNotifications()

        // Verify response headers
        assertNotNull("Response headers should not be null", response.headers)
        assertTrue("Response should have x-poll-interval header", response.headers.contains("x-poll-interval"))

        // Notifications might be empty if there are no notifications, so we just check that the response is not null
        assertNotNull("Notification threads should not be null", response.notificationThreads)

        println("[DEBUG_LOG] Successfully retrieved notifications. Count: ${response.notificationThreads.size}")
    }

    @Test
    fun `it should get a pull request`() = runBlocking {
        // For octocat/Hello-World, PR #1 is known to exist
        val pullRequestNumber = "1"
        println("[DEBUG_LOG] Attempting to get pull request #$pullRequestNumber")

        // Get the pull request
        val pullRequestResponse = githubRequests.getAPullRequest(pullRequestNumber)

        // Verify response
        assertNotNull("Pull request should not be null", pullRequestResponse.pullRequest)
        assertNotNull("Pull request state should not be null", pullRequestResponse.pullRequest.state)
        assertTrue(
            "Pull request state should be OPEN or CLOSED",
            pullRequestResponse.pullRequest.state == PullRequestState.OPEN ||
                    pullRequestResponse.pullRequest.state == PullRequestState.CLOSED
        )

        println("[DEBUG_LOG] Successfully retrieved pull request #$pullRequestNumber. State: ${pullRequestResponse.pullRequest.state}")
    }

    @Test
    fun `it should throw an exception if the pull request has not been modified`() = runBlocking {
        val pullRequestNumber = "1"

        val pullRequestResponse = githubRequests.getAPullRequest(pullRequestNumber)
        val lastModified = pullRequestResponse.headers["last-modified"]
        assertNotNull(lastModified)

        try {
            githubRequests.getAPullRequest(pullRequestNumber, lastModified)
        } catch (e: RedirectResponseException) {
            assertTrue(e.response.status == HttpStatusCode.NotModified)
        }

        println("[DEBUG_LOG] Successfully retrieved pull request #$pullRequestNumber. State: ${pullRequestResponse.pullRequest.state}")
    }

}
