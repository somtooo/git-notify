package com.github.somtooo.gitnotify.lib.github

import com.github.somtooo.gitnotify.lib.github.data.NotificationThread
import com.github.somtooo.gitnotify.lib.github.data.NotificationThreadResponse
import com.github.somtooo.gitnotify.lib.github.data.PullRequest
import com.github.somtooo.gitnotify.lib.github.data.PullRequestsResponse
import io.ktor.http.*
import kotlinx.serialization.json.Json
import java.io.File

open class MockGithubRequest : GithubRequests() {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun getRepositoryNotifications(): NotificationThreadResponse {
        val jsonString = readJsonFile("src/test/resources/notificationThread.json")
        val notificationThread = json.decodeFromString<NotificationThread>(jsonString)

        val headers = headersOf(
            "x-poll-interval" to listOf("60000")
        )

        return NotificationThreadResponse(
            headers = headers,
            notificationThreads = listOf(notificationThread)
        )
    }

    override suspend fun getAPullRequest(pullNumber: String, lastModified: String?): PullRequestsResponse {
        val jsonString = readJsonFile("src/test/resources/pullRequest.json")
        val pullRequest = json.decodeFromString<PullRequest>(jsonString)

        val headers = headersOf(
            "last-modified" to listOf("Wed, 01 Jan 2023 12:00:00 GMT")
        )

        return PullRequestsResponse(
            pullRequest = pullRequest,
            headers = headers
        )
    }

    override suspend fun markNotificationThreadAsRead(threadId: String) {
        // No-op for testing
    }

    private fun readJsonFile(filePath: String): String {
        return File(filePath).readText()
    }
}
