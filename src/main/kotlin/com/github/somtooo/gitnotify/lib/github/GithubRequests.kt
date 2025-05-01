package com.github.somtooo.gitnotify.lib.github

import com.github.somtooo.gitnotify.lib.github.data.NotificationThread
import com.github.somtooo.gitnotify.lib.github.data.NotificationThreadResponse
import com.github.somtooo.gitnotify.lib.github.data.PullRequest
import com.github.somtooo.gitnotify.lib.github.data.PullRequestsResponse
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

open class GithubRequests {

    // Todo: figure out caching
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

    private var lastModifiedNotification: String? = null
    private var lastNotifications: List<NotificationThread> = emptyList()

    open suspend fun getRepositoryNotifications(): NotificationThreadResponse {
        val githubUrlPathParameters = GithubUrlPathParameters.fromEnv()
        val url =
            "https://api.github.com/repos/${githubUrlPathParameters.owner}/${githubUrlPathParameters.repo}/notifications"

        try {
            val response = client.get(url) {
                headers {
                    addGithubHeaders()
                    lastModifiedNotification?.let {
                        append("If-Modified-Since", lastModifiedNotification!!)
                    }
                }
            }


            response.headers["last-modified"]?.let {
                lastModifiedNotification = response.headers["last-modified"]
            }

            val notifications = response.body<List<NotificationThread>>()
            lastNotifications = notifications
            return NotificationThreadResponse(notificationThreads = notifications, headers = response.headers)

        } catch (e: RedirectResponseException) {
            if (e.response.status == HttpStatusCode.NotModified) {
                // Even on 304, update the ETag if present
                return NotificationThreadResponse(notificationThreads = lastNotifications, headers = e.response.headers)
            }
            throw e
        } catch (e: ClientRequestException) {
            throw e

        }
    }

    open suspend fun getAPullRequest(pullNumber: String, lastModified: String? = null): PullRequestsResponse {
        val githubUrlPathParameters = GithubUrlPathParameters.fromEnv()
        println("githubUrlPathParameters: $githubUrlPathParameters")
        val url =
            "https://api.github.com/repos/${githubUrlPathParameters.owner}/${githubUrlPathParameters.repo}/pulls/$pullNumber"

        val response = client.get(url) {
            headers {
                addGithubHeaders()
                lastModified?.let {
                    append("If-Modified-Since", lastModified)
                }
            }
        }

        val pullRequest = response.body<PullRequest>()
        return PullRequestsResponse(pullRequest = pullRequest, headers = response.headers)
    }

    // Skip no need to test
    open suspend fun markNotificationThreadAsRead(threadId: String) {
        try {
            val url = "https://api.github.com/notifications/threads/$threadId"
            client.patch(url) {
                headers {
                    addGithubHeaders()
                }
            }
            lastNotifications = lastNotifications.filter { it.id != threadId }
        } catch (e: Exception) {
            throw e
        }

    }

    private fun HeadersBuilder.addGithubHeaders() {
        append("Authorization", "Bearer ${System.getProperty(ConfigurationCheckerService.GIT_HUB_TOKEN_KEY)}")
        append("Accept", "application/vnd.github+json")
        append("X-GitHub-Api-Version", "2022-11-28")
    }

    companion object {
        const val DEFAULT_POLL_INTERVAL = 60L
    }

}