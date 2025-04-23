package com.github.somtooo.gitnotify.lib.github

import com.github.somtooo.gitnotify.lib.github.data.NotificationThreadResponse
import com.github.somtooo.gitnotify.lib.github.data.PullRequestResponse
import com.github.somtooo.gitnotify.services.ConfigurationCheckerService
import com.github.somtooo.gitnotify.services.GithubUrlPathParameters
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class GithubRequests {
    private val client = HttpClient {
        expectSuccess = true
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
            connectTimeoutMillis = 15000
        }
    }
    private var etag: String? = null
    private var lastNotifications: List<NotificationThreadResponse> = emptyList()
    private var pullRequestLastModified: MutableMap<String, String> = mutableMapOf()
    private var lastPullRequest: MutableMap<String, PullRequestResponse> = mutableMapOf()

    suspend fun getRepositoryNotifications(): List<NotificationThreadResponse> {
        val githubUrlPathParameters = GithubUrlPathParameters.fromEnv()
        val url =
            "https://api.github.com/repos/${githubUrlPathParameters.owner}/${githubUrlPathParameters.repo}/notifications"

        try {
            val response = client.get(url) {
                buildRequest()
                etag?.let {
                    headers {
                        append("If-None-Match", it)
                    }
                }
            }

            // Store the ETag header for future requests
            response.headers["etag"]?.let {
                etag = it
            }

            val notifications = response.body<List<NotificationThreadResponse>>()
            lastNotifications = notifications
            return notifications

        } catch (e: RedirectResponseException) {
            if (e.response.status == HttpStatusCode.NotModified) {
                // Even on 304, update the ETag if present
                e.response.headers["etag"]?.let {
                    etag = it
                }
                return lastNotifications
            }
            throw e
        }
    }

    suspend fun getAPullRequest(pullNumber: String): PullRequestResponse {
        val githubUrlPathParameters = GithubUrlPathParameters.fromEnv()
        val url =
            "https://api.github.com/repos/${githubUrlPathParameters.owner}/${githubUrlPathParameters.repo}/pulls/$pullNumber"

        try {
            val response = client.get(url) {
                buildRequest()
                pullRequestLastModified[pullNumber]?.let {
                    headers {
                        append("If-Modified-Since", it)
                    }
                }
            }

            // Store the Last-Modified header for future requests
            response.headers["last-modified"]?.let {
                pullRequestLastModified[pullNumber] = it
            }

            val pullRequest = response.body<PullRequestResponse>()
            lastPullRequest[pullNumber] = pullRequest
            return pullRequest

        } catch (e: RedirectResponseException) {
            if (e.response.status == HttpStatusCode.NotModified) {
                // Even on 304, update the Last-Modified if present
                e.response.headers["last-modified"]?.let {
                    pullRequestLastModified[pullNumber] = it
                }
                return lastPullRequest[pullNumber]
                    ?: throw IllegalStateException("No cached pull request found for $pullNumber")
            }
            throw e
        }
    }

    // check what headers this returns
    suspend fun markNotificationThreadAsRead(threadId: String) {
        val url = "https://api.github.com/notifications/threads/$threadId"
        try {
            client.patch(url) {
                buildRequest()
            }
        } catch (e: RedirectResponseException) {
            if (e.response.status !== HttpStatusCode.NotModified) {
                throw e
            }
        }
    }

    private fun buildRequest() = HttpRequestBuilder().apply {
        headers {
            append("Authorization", System.getenv(ConfigurationCheckerService.GIT_HUB_TOKEN_KEY))
            append("Accept", "application/vnd.github+json")
            append("X-GitHub-Api-Version", "2022-11-28")
        }
    }

    companion object {
        const val DEFAULT_POLL_INTERVAL = 60L
    }

}