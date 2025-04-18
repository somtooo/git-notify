package com.github.somtooo.gitnotify.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
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
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileNotFoundException

import com.intellij.util.io.HttpRequests

data class GithubUrlPathParameters(val owner: String, val repo: String)
@Service(Service.Level.PROJECT)
class ConfigurationCheckerService(private val project: Project, private val scope: CoroutineScope) {
    val rcFilePath: String = "${System.getProperty("user.home")}/.gitnotifyrc"
    val githubTokenKey: String = "GITHUB_TOKEN"

    companion object {
       private val LOG = logger<ConfigurationCheckerService>()
       fun getInstance(project: Project): ConfigurationCheckerService {
           return project.service();
       }
   }


    fun canConnectToTheInternet(): Boolean = runBlocking {
        val client = HttpClient(CIO)
        return@runBlocking try {
            val response: HttpResponse = client.head("https://www.github.com")
            response.status.value in 200..299 // Check for successful HTTP status codes
        } catch (e: Exception) {
            LOG.debug("Error checking internet connectivity: ${e.message}")
            false
        } finally {
            client.close()
        }
    }

    fun hasVcsEnabled(): Boolean {
        val repositoryManager: GitRepositoryManager = GitRepositoryManager.getInstance(project)
        val repositories: List<GitRepository> = repositoryManager.repositories

        if (repositories.isEmpty()) {
            LOG.warn("Git VCS is not  enabled in project. Plugin wont work until enabled")
            return false
        }

        val url: String = findFirstGithubUrl(repositories)

        if (url.isEmpty()) {
            LOG.warn("No github origin remote found for project. Pls add a github remote to start receiving notifications")
            return false
        }
        return true
    }

    fun hasGithubTokenSetInRcFile(): Boolean {
        try {
            val result = parseRcFile(rcFilePath);
            if (result[githubTokenKey] === null || result[githubTokenKey]?.isEmpty() == true) {
                LOG.warn("Github token key not set in rc file. Please set for plugin to function")
                return false;
            }

            val isTokenValid = checkTokenIsValid(result[githubTokenKey]!!)
            if (!isTokenValid) {
                LOG.warn("Github token authentication failed set a valid token in rc file")
                return false;
            }
             return true;
        } catch (e: Exception) {
            when (e) {
                is FileNotFoundException -> {
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

     fun checkTokenIsValid(token: String): Boolean {
        val repositoryManager: GitRepositoryManager = GitRepositoryManager.getInstance(project)
        val repositories: List<GitRepository> = repositoryManager.repositories
        val url = "https://github.com/somtooo/DbGO"
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

        val result =   status.all {
            it in  200..299
        }

        if (!result) {
            LOG.debug("Status codes are $status")
        }

        return result
    }

    private fun findFirstGithubUrl(repositories: List<GitRepository>): String {
        for (repository in repositories) {
            val remotes = repository.remotes
            for (remote in remotes) {
                val urls = remote.urls
                for (url in urls) {
                    if (remote.name === "origin" && url.contains("github.com")) return url;
                }
            }
        }
        return ""
    }

    private fun parseRcFile(filePath: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val file = File(filePath)

        if (!file.exists()) {
            logger<MyProjectService>().debug(".gitnotifyrc file does not exist")
            throw FileNotFoundException("RC File does not exist");
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

        return result;
    }

     fun buildGithubUrlPathParams(url: String): GithubUrlPathParameters {
        val urlPaths = url.split(Regex("//|/"))
        val repo = urlPaths[urlPaths.size -1].split(".")[0]
        val owner = urlPaths[urlPaths.size -2]

        return GithubUrlPathParameters(owner, repo);
    }
}
