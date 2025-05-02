package com.github.somtooo.gitnotify.extensions

import com.github.somtooo.gitnotify.services.ConfigurationCheckerService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vcs.ProjectLevelVcsManager

class ConfigurationCheckerStartupActivity: ProjectActivity {
    override suspend fun execute(project: Project) {
        ProjectLevelVcsManager.getInstance(project).runAfterInitialization {
            if(System.getenv("CI")?.equals("true", ignoreCase = true) != true) {
                ConfigurationCheckerService.getInstance(project).validateConfiguration();
            }
        }
    }
}