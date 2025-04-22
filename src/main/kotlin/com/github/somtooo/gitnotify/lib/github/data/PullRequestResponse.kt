package com.github.somtooo.gitnotify.lib.github.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PullRequestResponse(
    val state: PullRequestState,
    val user: SimpleUser,
)

@Serializable
enum class PullRequestState {
    @SerialName("open")
    OPEN,

    @SerialName("closed")
    CLOSED
}

