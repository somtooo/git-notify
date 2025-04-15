import com.github.somtooo.gitnotify.services.MyProjectService
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ConfigurationCheckerStartupActivityTest : BasePlatformTestCase() {

    fun testEnv() {
        val projectService = project.service<MyProjectService>()
        assert(projectService.checkEnv().isNotEmpty())
    }

}