package com.github.somtooo.gitnotify.lib.github.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PullRequestResponse(
    @SerialName("state")
    val state: PullRequestState
)

@Serializable
enum class PullRequestState {
    @SerialName("open")
    OPEN,

    @SerialName("closed")
    CLOSED
}