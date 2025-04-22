package com.github.somtooo.gitnotify.lib.github.data

class GitHubApiException(
    message: String,
    val statusCode: Int? = null,
    cause: Throwable? = null
) : Exception(message, cause)