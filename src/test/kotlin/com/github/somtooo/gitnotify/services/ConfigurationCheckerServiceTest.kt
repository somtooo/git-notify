package com.github.somtooo.gitnotify.services

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import junit.framework.TestCase
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

@RunWith(JUnit4::class)
class ConfigurationCheckerServiceTest : BasePlatformTestCase() {
    private lateinit var gitRepositoryManager: GitRepositoryManager
    private lateinit var configurationCheckerService: ConfigurationCheckerService

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)
        // Mock the static getInstance method
        gitRepositoryManager = mock(GitRepositoryManager::class.java)

        val mockedManager = Mockito.mockStatic(GitRepositoryManager::class.java)
        mockedManager.`when`<GitRepositoryManager> { GitRepositoryManager.getInstance(project) }
            .thenReturn(gitRepositoryManager)
        configurationCheckerService = project.service<ConfigurationCheckerService>()

    }


    @Test
    fun  `it can connect to the internet`() {
        assertTrue(configurationCheckerService.canConnectToTheInternet())
    }

    @Test
    fun `it returns false if no vcs is enabled in project`() {
        TestCase.assertFalse(configurationCheckerService.hasVcsEnabled())
    }

    @Test
    fun `it returns false if no github remote is found`() {
        val repository = mock(GitRepository::class.java)
        val remote = mock(GitRemote::class.java)

        `when`(gitRepositoryManager.repositories).thenReturn(listOf(repository))
        `when`(repository.remotes).thenReturn(listOf(remote))
        `when`(remote.name).thenReturn("origin")
        `when`(remote.urls).thenReturn(listOf("https://gitlab.com/some/repo.git"))

        assertFalse(configurationCheckerService.hasVcsEnabled())
    }

    @Test
    fun `it returns true if a github remote is found`() {
        val repository = mock(GitRepository::class.java)
        val remote = mock(GitRemote::class.java)

        `when`(gitRepositoryManager.repositories).thenReturn(listOf(repository))
        `when`(repository.remotes).thenReturn(listOf(remote))
        `when`(remote.name).thenReturn("origin")
        `when`(remote.urls).thenReturn(listOf("https://github.com/some/repo.git"))

        assertTrue(configurationCheckerService.hasVcsEnabled())
    }

}