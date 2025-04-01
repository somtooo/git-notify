package com.github.somtooo.gitnotify

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.components.service
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.PsiErrorElementUtil
import com.github.somtooo.gitnotify.services.MyProjectService
import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications

@TestDataPath("\$CONTENT_ROOT/src/test/testData")
class MyPluginTest : BasePlatformTestCase() {

    fun testXMLFile() {
        val psiFile = myFixture.configureByText(XmlFileType.INSTANCE, "<foo>bar</foo>")
        val xmlFile = assertInstanceOf(psiFile, XmlFile::class.java)

        assertFalse(PsiErrorElementUtil.hasErrors(project, xmlFile.virtualFile))

        assertNotNull(xmlFile.rootTag)

        xmlFile.rootTag?.let {
            assertEquals("foo", it.name)
            assertEquals("bar", it.value.text)
        }
    }

    fun testRename() {
        myFixture.testRename("foo.xml", "foo_after.xml", "a2")
    }

    fun testProjectService() {
        val projectService = project.service<MyProjectService>()
        assertNotSame(projectService.getRandomNumber(), projectService.getRandomNumber())
    }

    fun testGetRandomNumberNotify() {
        var notificationShown = false
        val connection = project.messageBus.connect()
        
        connection.subscribe(Notifications.TOPIC, object : Notifications {
            override fun notify(notification: Notification) {
                if (notification.groupId == "GithubPullRequest" &&
                    notification.content == "Random Number is 2" &&
                    notification.type == NotificationType.INFORMATION) {
                    notificationShown = true
                }
            }
        })
        
        val projectService = project.service<MyProjectService>()
        val result = projectService.getRandomNumberNotify(project)
        assertTrue(result in 1..2)
        
        // fix this test is broken
        if (result == 2) {
            assertTrue(notificationShown, "Notification should have been shown when number is 2")
        } else {
            assertFalse(notificationShown, "Notification should not have been shown when number is 1")
        }
        
        connection.disconnect()
    }

    override fun getTestDataPath() = "src/test/testData/rename"
}
