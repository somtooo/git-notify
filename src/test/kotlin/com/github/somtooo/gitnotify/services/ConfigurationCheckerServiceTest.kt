package com.github.somtooo.gitnotify.services

import com.github.somtooo.gitnotify.lib.SkipCi
import com.github.somtooo.gitnotify.lib.SkipCiRule
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import junit.framework.TestCase
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mockito
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

@RunWith(JUnit4::class)
class ConfigurationCheckerServiceTest : BasePlatformTestCase() {
    @get:Rule
    val skipCiRule = SkipCiRule()

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

    @After
    fun cleanup() {
        framework().clearInlineMocks()
    }

    @Test
    fun `it validates correct configuration`() {
        val repository = mock(GitRepository::class.java)
        val remote = mock(GitRemote::class.java)

        whenever(gitRepositoryManager.repositories).thenReturn(listOf(repository))
        whenever(repository.remotes).thenReturn(listOf(remote))
        whenever(remote.name).thenReturn("origin")
        whenever(remote.urls).thenReturn(listOf("https://github.com/somtooo/DbGO"))


        val spiedService = spy(configurationCheckerService)

        val rcFileKeyValuePairs = mutableMapOf<String, String>()
        rcFileKeyValuePairs[configurationCheckerService.githubTokenKey] = System.getenv(configurationCheckerService.githubTokenKey)

        doReturn(rcFileKeyValuePairs).`when`(spiedService).parseRcFile(anyString())


        assertTrue(spiedService.validateConfiguration())
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

    @Test
    @SkipCi
    fun `checks if token is set in rc file`() {
        val spiedService = spy(configurationCheckerService)

        `when`(spiedService.findFirstGithubUrl(anyList())).thenReturn("https://github.com/somtooo/DbGO")

        assertTrue(spiedService.hasValidGithubTokenSetInRcFile())
    }

    @Test
    fun `checkTokenIsValid returns true when all API calls return 200`() {
        val spiedService = spy(configurationCheckerService)

        `when`(spiedService.findFirstGithubUrl(anyList())).thenReturn("https://github.com/somtooo/DbGO")

        assertTrue(spiedService.checkTokenIsValid(System.getenv("GITHUB_TOKEN")))
    }

    @Test
    fun `checkTokenIsValid returns false when all API calls return failed status code`() {

        val spiedService = spy(configurationCheckerService)

        `when`(spiedService.findFirstGithubUrl(anyList())).thenReturn("https://github.com/somtooo/DbGO")
        assertFalse(spiedService.checkTokenIsValid("wrong token"))
    }

    @Test
    fun `it builds GithubPathParamCorrectly`() {
        val url = "https://github.com/somtooo/DbGO.git"
        val pathParameters = configurationCheckerService.buildGithubUrlPathParams(url)
        assertTrue(pathParameters.repo == "DbGO")
        assertTrue(pathParameters.owner == "somtooo")
    }
}