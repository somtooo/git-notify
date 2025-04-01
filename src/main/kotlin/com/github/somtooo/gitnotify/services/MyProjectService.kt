package com.github.somtooo.gitnotify.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.github.somtooo.gitnotify.MyBundle
import com.intellij.notification.*
import com.jetbrains.rd.generator.nova.PredefinedType

@Service(Service.Level.PROJECT)
class MyProjectService(project: Project) {

    init {
        thisLogger().info(MyBundle.message("projectService", project.name))
        thisLogger().warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")
    }

    fun getRandomNumberNotify(project: Project): Int {
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
