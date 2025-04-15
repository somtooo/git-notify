package com.github.somtooo.gitnotify.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class ConfigurationCheckerStartupActivity: ProjectActivity {
    override suspend fun execute(project: Project) {
        ConfigurationCheckerService.getInstance(project).check();
    }
}

@Service(Service.Level.PROJECT)
class ConfigurationCheckerService(private val project: Project) {
   companion object {
       private val LOG = logger<ConfigurationCheckerService>()
       fun getInstance(project: Project): ConfigurationCheckerService {
           return project.service();
       }
   }

    public fun check() {
        ConfigurationCheckerService.LOG.info("Starting Check on ${project.name}")
    }
}