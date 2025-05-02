package com.github.somtooo.gitnotify
//
//import com.github.somtooo.gitnotify.services.MyProjectService
//import com.intellij.ide.highlighter.XmlFileType
//import com.intellij.openapi.components.service
//import com.intellij.psi.xml.XmlFile
//import com.intellij.testFramework.TestDataPath
//import com.intellij.testFramework.fixtures.BasePlatformTestCase
//import com.intellij.util.PsiErrorElementUtil
//
//@TestDataPath("\$CONTENT_ROOT/src/test/testData")
//class MyPluginTest : BasePlatformTestCase() {
//
//    fun testXMLFile() {
//        val psiFile = myFixture.configureByText(XmlFileType.INSTANCE, "<foo>bar</foo>")
//        val xmlFile = assertInstanceOf(psiFile, XmlFile::class.java)
//
//        assertFalse(PsiErrorElementUtil.hasErrors(project, xmlFile.virtualFile))
//
//        assertNotNull(xmlFile.rootTag)
//
//        xmlFile.rootTag?.let {
//            assertEquals("foo", it.name)
//            assertEquals("bar", it.value.text)
//        }
//    }
//
//    fun testRename() {
//        myFixture.testRename("foo.xml", "foo_after.xml", "a2")
//    }
//
//    fun testProjectService() {
//        val projectService = project.service<MyProjectService>()
//        assertNotSame(projectService.getRandomNumber(), projectService.getRandomNumber())
//    }
//
//    fun testEnv() {
//        val projectService = project.service<MyProjectService>()
//        assert(projectService.checkEnv().isNotEmpty())
//    }
//
//    fun testUrl() {
//        val projectService = project.service<MyProjectService>()
//        assertNotNull(projectService.buildUrl(project))
//    }
//
//    fun testGetRandomNumberNotify() {
//        var notificationShown = false
//        val connection = project.messageBus.connect()
//
//        connection.subscribe(Notifications.TOPIC, object : Notifications {
//            override fun notify(notification: Notification) {
//                if (notification.groupId == "GithubPullRequest" &&
//                    notification.content == "Random Number is 2" &&
//                    notification.type == NotificationType.INFORMATION) {
//                    notificationShown = true
//                }
//            }
//        })
//
//        val projectService = project.service<MyProjectService>()
//        val result = projectService.getRandomNumberNotify(project)
//        assertTrue(result in 1..2)
//
//        // fix this test is broken
//        if (result == 2) {
//            assertTrue("Notification should have been shown when number is 2", notificationShown)
//        } else {
//            assertFalse("Notification should not have been shown when number is 1", notificationShown)
//        }
//
//        connection.disconnect()
//    }

//    override fun getTestDataPath() = "src/test/testData/rename
//}
