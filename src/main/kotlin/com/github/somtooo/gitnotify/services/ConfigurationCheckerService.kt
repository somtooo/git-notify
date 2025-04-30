package com.github.somtooo.gitnotify.services

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileNotFoundException

data class GithubUrlPathParameters(val owner: String, val repo: String) {
    companion object {
        const val OWNER_KEY = "GITHUB_OWNER"
        const val REPO_KEY = "GITHUB_REPO"

        fun fromEnv(): GithubUrlPathParameters {
            val owner = System.getProperty(OWNER_KEY)
                ?: throw IllegalStateException("$OWNER_KEY environment variable not set")
            val repo = System.getProperty(REPO_KEY)
                ?: throw IllegalStateException("$REPO_KEY environment variable not set")
            return GithubUrlPathParameters(owner, repo)
        }
    }

    fun setToEnv() {
        System.setProperty(OWNER_KEY, owner)
        System.setProperty(REPO_KEY, repo)
    }
}


@Service(Service.Level.PROJECT)
internal class ConfigurationCheckerService(private val project: Project, private val scope: CoroutineScope) {
    private val rcFilePath: String = "${System.getProperty("user.home")}/.gitnotifyrc"
    private val configurationCheckerNotification =
        NotificationGroupManager.getInstance().getNotificationGroup("StickyBalloon")

    companion object {
        const val GIT_HUB_TOKEN_KEY = "GITHUB_TOKEN"
        private val LOG = logger<ConfigurationCheckerService>()
        fun getInstance(project: Project): ConfigurationCheckerService {
            return project.service()
        }
    }

    fun validateConfiguration(): Boolean {
        val checks: List<() -> Boolean> = listOf(
            ::canConnectToTheInternet,
            ::hasVcsEnabled,
            ::hasValidGithubTokenSetInRcFile
        )

        for (check in checks) {
            val result = check()
            if (!result) {
                val notifyError = NotificationGroupManager.getInstance().getNotificationGroup("NonStickyBalloon")
                    .createNotification(title = "Git-Notify", "Validation failed", NotificationType.ERROR)
                notifyError.addAction(NotificationAction.createSimpleExpiring("Restart plugin") {
                    notifyError.expire()
                    scope.launch {
                        project.service<ConfigurationCheckerService>().validateConfiguration()
                    }
                })
                return false
            }
        }

        val notify = NotificationGroupManager.getInstance().getNotificationGroup("NonStickyBalloon")
            .createNotification(title = "Git-Notify", "Validation successful", NotificationType.INFORMATION)
        notify.notify(project)

        scope.launch {
            GithubNotification.getInstance(project).pollForNotifications()
        }
        return true
    }

    fun canConnectToTheInternet(): Boolean = runBlocking {
        val client = HttpClient(CIO)
        return@runBlocking try {
            val response: HttpResponse = client.head("https://www.github.com")
            response.status.value in 200..299 // Check for successful HTTP status codes
        } catch (e: Exception) {
            notifyError("Error checking internet connectivity")
            LOG.warn("Error checking internet connectivity: $e")
            false
        } finally {
            client.close()
        }
    }

    fun hasVcsEnabled(): Boolean {
        val repositoryManager: GitRepositoryManager = GitRepositoryManager.getInstance(project)
        val repositories: List<GitRepository> = repositoryManager.repositories

        if (repositories.isEmpty()) {
            notifyError("Git VCS is not  enabled in project. Plugin wont work until enabled")
            LOG.warn("Git VCS is not  enabled in project. Plugin wont work until enabled")
            return false
        }

        val url: String = findFirstGithubUrl(repositories)

        if (url.isEmpty()) {
            notifyError("No github origin remote found for project. Pls add a github remote to start receiving notifications")
            LOG.warn("No github origin remote found for project. Pls add a github remote to start receiving notifications")
            return false
        }
        return true
    }

    fun hasValidGithubTokenSetInRcFile(): Boolean {
        try {
            val result = parseRcFile(rcFilePath)
            if (result[GIT_HUB_TOKEN_KEY] === null || result[GIT_HUB_TOKEN_KEY]?.isEmpty() == true) {
                notifyError("Github token key not set in rc file. Please set for plugin to function")
                LOG.warn("Github token key not set in rc file. Please set for plugin to function")
                return false
            }

            val isTokenValid = checkTokenIsValid(result[GIT_HUB_TOKEN_KEY]!!)
            if (!isTokenValid) {
                notifyError("Github token authentication failed set a valid token in rc file")
                LOG.warn("Github token authentication failed set a valid token in rc file")
                return false
            }
            return true
        } catch (e: Exception) {
            when (e) {
                is FileNotFoundException -> {
                    notifyError("Git-notify rc file not found put rc file in home path and set key GITHUB_TOKEN")
                    LOG.warn("Git-notify rc file not found put rc file in home path and set key GITHUB_TOKEN")
                    return false
                }

                else -> {
                    LOG.debug(e.message)
                    return false
                }
            }
        }
    }

    private fun notifyError(content: String) {
        val notify: Notification =
            configurationCheckerNotification.createNotification(title = "Git-Notify", content, NotificationType.ERROR)
        notify.addAction(NotificationAction.createSimpleExpiring("Validate again") {
            notify.expire()

            // Verify later that this is OK
            scope.launch {
                validateConfiguration()
            }

        })
        notify.notify(project)
    }

    fun checkTokenIsValid(token: String): Boolean {
        val repositoryManager: GitRepositoryManager = GitRepositoryManager.getInstance(project)
        val repositories: List<GitRepository> = repositoryManager.repositories
        val url = findFirstGithubUrl(repositories)
        val pathParameters = buildGithubUrlPathParams(url)
        val endpoints = listOf(
            "https://api.github.com/repos/${pathParameters.owner}/${pathParameters.repo}/notifications",
            "https://api.github.com/repos/${pathParameters.owner}/${pathParameters.repo}/pulls"
        )
        val client = HttpClient(CIO)
        val status = mutableListOf<Int>()
        for (endpoint in endpoints) {
            runBlocking {
                try {
                    val response = client.get(endpoint) {
                        headers {
                            append(HttpHeaders.Accept, "application/vnd.github+json")
                            append(HttpHeaders.Authorization, "Bearer $token")
                            append("X-GitHub-Api-Version", "2022-11-28")
                        }
                    }
                    status.add(response.status.value)
                } catch (e: Error) {
                    LOG.debug(e.message)
                    return@runBlocking false
                }
            }
        }

        val result = status.all {
            it in 200..299
        }

        if (!result) {
            LOG.debug("Status codes are $status")
        } else {
            System.setProperty(GIT_HUB_TOKEN_KEY, token)
            pathParameters.setToEnv()
        }


        return result
    }

    fun findFirstGithubUrl(repositories: List<GitRepository>): String {
        for (repository in repositories) {
            val remotes = repository.remotes
            for (remote in remotes) {
                val urls = remote.urls
                for (url in urls) {
                    if (remote.name == "origin" && url.contains("github.com")) return url
                }
            }
        }
        return ""
    }

    fun parseRcFile(filePath: String): MutableMap<String, String> {
        val result = mutableMapOf<String, String>()
        val file = File(filePath)

        if (!file.exists()) {
            logger<ConfigurationCheckerService>().debug(".gitnotifyrc file does not exist")
            throw FileNotFoundException("RC File does not exist")
        }

        file.forEachLine { line ->
            val trimmedLine = line.trim()
            if (trimmedLine.isNotEmpty() && !trimmedLine.startsWith("#")) { // Ignore comments and empty lines
                val parts = trimmedLine.split("=", limit = 2) // Limit to 2 parts to handle values with '='
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim()
                    result[key] = value
                }
            }
        }

        return result
    }

    fun buildGithubUrlPathParams(url: String): GithubUrlPathParameters {
        val urlPaths = url.split(Regex("//|/"))
        val repo = urlPaths[urlPaths.size - 1].split(".")[0]
        val owner = urlPaths[urlPaths.size - 2]

        return GithubUrlPathParameters(owner, repo)
    }

}
