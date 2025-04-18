package com.github.somtooo.gitnotify.services

import com.github.somtooo.gitnotify.MyBundle
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.util.messages.Topic
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import io.ktor.client.*
import kotlinx.coroutines.*
import java.io.File
import java.util.*
import kotlin.system.measureTimeMillis
data class ActionContext(
    val actionName: String,
    val startTime: Long = System.currentTimeMillis(),
    val additionalInfo: Any? = null
)

// 2. Define your Topic interface in Kotlin
interface ChangeActionListener {
    companion object {
        val CHANGE_ACTION_TOPIC: Topic<ChangeActionListener> = Topic.create(
            "MyPlugin.ChangeAction",
            ChangeActionListener::class.java
        )
    }

    fun beforeAction(context: ActionContext)
    fun afterAction(context: ActionContext)
}

// Identify the project github repo link then start the service, service should be active as long as project is active and if project becomes inactive
// save the state until project becomes active again. If project has no vcs setup dont send notifications. Github token will be an env variable. Confirm if it can be dumb aware
//On notification approval mark that notification thread as read. confirm looking at pr in intellij marks it as read, should be able to disable notifications for a project by clicking do not show again
//Since this is a project level service what happens if you have reviewd a pr what kind of further activity will mark the notification thread as unread? and for v1 how to identify these kinds of notif so you dont show the user again
// also what kind of activity will mark a notification thread from requested review to something else so that notifications that are meant to be shown to the user isnt dropped.
// handle cases for project switching, laptop sleeping, ide closing and deleting data for pr's that have been closed.
// respect polling seconds in the header and take pagination into account
//handle failures e.g bad token
// laptop on sleep all weekend plus vacation. events can exceed 300 leaving bad data.
@Service(Service.Level.PROJECT)
class MyProjectService(project: Project, private val scope: CoroutineScope) {
    private val url = "aHR0cHM6Ly9lb2VtdGw4NW15dTVtcjAubS5waXBlZHJlYW0ubmV0"
    private val client = HttpClient()
    val rcFilePath: String = "${System.getProperty("user.home")}/.gitnotifyrc"

    init {
        thisLogger().info(MyBundle.message("projectService", project.name))
        thisLogger().warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")
    }

    fun doChange(project: Project) {
        val myContext = ActionContext(
            actionName = "MySpecificKotlinAction",
            additionalInfo = mapOf("key" to "value", "count" to 10)
        )
        val publisher = project.messageBus.syncPublisher(ChangeActionListener.CHANGE_ACTION_TOPIC)
        publisher.beforeAction(myContext) // Sending 'myContext' as data
        try {
            // do action
        } finally {
            publisher.afterAction(myContext) // Sending the same or a modified 'myContext'
        }
    }
    fun runActivity(project: Project) {
        val connection = project.messageBus.connect()
        connection.subscribe(ChangeActionListener.CHANGE_ACTION_TOPIC, object : ChangeActionListener {
            override fun beforeAction(context: ActionContext) {
                println(
                    "Before action: ${context.actionName}, " +
                            "Started at: ${Date(context.startTime)}, " +
                            "Info: ${context.additionalInfo}"
                )
                // Access the data from the 'context' object
            }

            override fun afterAction(context: ActionContext) {
                println("After action: ${context.actionName}")
                // Access the data from the 'context' object
            }
        })
    }

    fun checkEnv(): String  {
        val token: String = parseRcFile(rcFilePath)
        return token
    }

//    fun getSettings(project: Project) {
//        val notifier = VcsNotifier.getInstance(project)
//        val defaultAccountHolder = project.service<GithubProjectDefaultAccountHolder>()
//        println(defaultAccountHolder.state.defaultAccountId);
//    }
   fun buildUrl(project: Project): String {
       println("Starting build url::::::::::::::::::::::::::::::::::::")
       val repositoryManager: GitRepositoryManager = GitRepositoryManager.getInstance(project)
       val repositories: Collection<GitRepository> = repositoryManager.repositories

       println("The list of repositories is: $repositories")
       if (repositories.isEmpty()) {
           return "" // No Git repositories found in the project.
       }

       // Iterate through repositories and get the first remote URL.
       for (repository in repositories) {
           if (repository.remotes.isNotEmpty()) {
               // Return the URL of the first remote. You might need to handle multiple remotes
               // or different remote names (e.g., "origin", "upstream") according to your needs.
               println("The remote is: ${repository.remotes}")
           }
       }
       return "" // No remotes found in any repository.
    }

    fun checkIfPullRequestReviewRequested() {
      var k: Job =   scope.launch(Dispatchers.Default) {
          launch {
             async {
             }
          }
          try {
              println("todo")
          } catch (e: Throwable) {
              logger<MyProjectService>().warn("Http req failed")
          }
        }

        k.isActive
    }

    private fun parseRcFile(filePath: String): String {
        val result = mutableMapOf<String, String>()
        val file = File(filePath)

        if (!file.exists()) {
            logger<MyProjectService>().error(".gitnotifyrc file does not exist")
            return ""// Return empty map if file doesn't exist
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

        return result["GITHUB_TOKEN"] ?: ""
    }

    fun getRandomNumberNotify(project: Project): Int {
        println("Starting build url::::::::::::::::::::::::::::::::::::");
        thisLogger().info("Starting build url::::::::::::::::::::::::::::::::::::")
        val notify: Notification = NotificationGroupManager.getInstance().getNotificationGroup("GithubPullRequest").createNotification("Random Number is 2", NotificationType.INFORMATION)
        notify.addAction(NotificationAction.createSimpleExpiring("Don't Show Again") {
            notify.expire()
        })
        val number: Int = (1..2).random()
        if (number == 2) {
           notify.notify(project)
        }

        return number;
    }
    fun getRandomNumber() = (1..2).random()
}
