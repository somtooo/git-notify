package com.github.somtooo.gitnotify.lib.github

import com.github.somtooo.gitnotify.lib.github.data.NotificationThreadResponse
import com.github.somtooo.gitnotify.lib.github.data.PullRequestResponse
import com.github.somtooo.gitnotify.services.ConfigurationCheckerService
import com.github.somtooo.gitnotify.services.GithubUrlPathParameters
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class GithubRequests {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }


    suspend fun getRepositoryNotifications(): List<NotificationThreadResponse> {
        val githubUrlPathParameters = GithubUrlPathParameters.fromEnv()
        val url =
            "https://api.github.com/repos/$githubUrlPathParameters.owner/${githubUrlPathParameters.repo}/notifications"
        val threadResponses: List<NotificationThreadResponse> = client.get(url) { buildRequest() }.body()

        return threadResponses

    }

    suspend fun getAPullRequest(pullNumber: String): PullRequestResponse {
        val githubUrlPathParameters = GithubUrlPathParameters.fromEnv()
        val url =
            "https://api.github.com/repos/${githubUrlPathParameters.owner}/${githubUrlPathParameters.repo}/pulls/$pullNumber"

        val pullRequestResponse: PullRequestResponse = client.get(url) { buildRequest() }.body()

        return pullRequestResponse
    }

    private fun buildRequest() = HttpRequestBuilder {
        headers {
            append("Authorization", System.getenv(ConfigurationCheckerService.GIT_HUB_TOKEN_KEY))
            append("Accept", "application/vnd.github+json")
            append("X-GitHub-Api-Version", "2022-11-28")
        }
    }
}
