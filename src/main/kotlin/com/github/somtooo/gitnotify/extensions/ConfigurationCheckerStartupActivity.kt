package com.github.somtooo.gitnotify.extensions

import com.github.somtooo.gitnotify.services.ConfigurationCheckerService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class ConfigurationCheckerStartupActivity: ProjectActivity {
    override suspend fun execute(project: Project) {
        ConfigurationCheckerService.getInstance(project).hasVcsEnabled();
    }
}