package com.github.somtooo.gitnotify.lib.github

import com.github.somtooo.gitnotify.lib.github.data.GitHubApiException
import com.github.somtooo.gitnotify.lib.github.data.NotificationThreadResponse
import com.github.somtooo.gitnotify.lib.github.data.PullRequestResponse
import com.github.somtooo.gitnotify.services.ConfigurationCheckerService
import com.github.somtooo.gitnotify.services.GithubUrlPathParameters
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.network.sockets.*
import io.ktor.client.plugins.*
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
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
            connectTimeoutMillis = 15000
        }
    }

    suspend fun getRepositoryNotifications(): List<NotificationThreadResponse> {
        val githubUrlPathParameters = GithubUrlPathParameters.fromEnv()
        val url =
            "https://api.github.com/repos/${githubUrlPathParameters.owner}/${githubUrlPathParameters.repo}/notifications"

        try {
            return client.get(url) { buildRequest() }.body()
        } catch (e: ClientRequestException) {
            when (e.response.status.value) {
                401 -> throw GitHubApiException("Unauthorized: Check your GitHub token", 401, e)
                403 -> throw GitHubApiException("Forbidden: Rate limit exceeded or token lacks permissions", 403, e)
                404 -> throw GitHubApiException("Repository not found", 404, e)
                else -> throw GitHubApiException("HTTP error: ${e.response.status.value}", e.response.status.value, e)
            }
        } catch (e: ServerResponseException) {
            throw GitHubApiException("GitHub server error: ${e.response.status.value}", e.response.status.value, e)
        } catch (e: SocketTimeoutException) {
            throw GitHubApiException("Connection timeout", cause = e)
        } catch (e: Exception) {
            throw GitHubApiException("Failed to fetch notifications: ${e.message}", cause = e)
        }
    }

    suspend fun getAPullRequest(pullNumber: String): PullRequestResponse {
        val githubUrlPathParameters = GithubUrlPathParameters.fromEnv()
        val url =
            "https://api.github.com/repos/${githubUrlPathParameters.owner}/${githubUrlPathParameters.repo}/pulls/$pullNumber"

        try {
            return client.get(url) { buildRequest() }.body()
        } catch (e: ClientRequestException) {
            when (e.response.status.value) {
                401 -> throw GitHubApiException("Unauthorized: Check your GitHub token", 401, e)
                403 -> throw GitHubApiException("Forbidden: Rate limit exceeded or token lacks permissions", 403, e)
                404 -> throw GitHubApiException("Pull request not found", 404, e)
                else -> throw GitHubApiException("HTTP error: ${e.response.status.value}", e.response.status.value, e)
            }
        } catch (e: ServerResponseException) {
            throw GitHubApiException("GitHub server error: ${e.response.status.value}", e.response.status.value, e)
        } catch (e: Exception) {
            throw GitHubApiException("Failed to fetch pull request: ${e.message}", cause = e)
        }
    }

    suspend fun markNotificationThreadAsRead(threadId: String) {
        val url = "https://api.github.com/notifications/threads/$threadId"

        try {
            client.patch(url) { buildRequest() }
        } catch (e: ClientRequestException) {
            when (e.response.status.value) {
                401 -> throw GitHubApiException("Unauthorized: Check your GitHub token", 401, e)
                403 -> throw GitHubApiException("Forbidden: Rate limit exceeded or token lacks permissions", 403, e)
                404 -> throw GitHubApiException("Notification thread not found", 404, e)
                else -> throw GitHubApiException("HTTP error: ${e.response.status.value}", e.response.status.value, e)
            }
        } catch (e: ServerResponseException) {
            throw GitHubApiException("GitHub server error: ${e.response.status.value}", e.response.status.value, e)
        } catch (e: Exception) {
            throw GitHubApiException("Failed to mark notification as read: ${e.message}", cause = e)
        }
    }

    private fun buildRequest() = HttpRequestBuilder {
        headers {
            append("Authorization", System.getenv(ConfigurationCheckerService.GIT_HUB_TOKEN_KEY))
            append("Accept", "application/vnd.github+json")
            append("X-GitHub-Api-Version", "2022-11-28")
        }
    }
}