@file:Suppress("PROVIDED_RUNTIME_TOO_LOW")

package com.github.somtooo.gitnotify.lib.github.data

import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class PullRequestsResponse(
    val pullRequest: PullRequest,
    val headers: Headers,
)

@Serializable
data class PullRequest(
    val state: PullRequestState,
    val user: SimpleUser,
    val number: Int,
)

@Serializable
enum class PullRequestState {
    @SerialName("open")
    OPEN,

    @SerialName("closed")
    CLOSED
}

