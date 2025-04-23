@file:Suppress("PROVIDED_RUNTIME_TOO_LOW")

package com.github.somtooo.gitnotify.lib.github.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NotificationThreadResponse(
    val id: String,
    val repository: Repository,
    val subject: Subject,
    val reason: String,
    val unread: Boolean,
    @SerialName("updated_at")
    val updatedAt: String,
    @SerialName("last_read_at")
    val lastReadAt: String?,
    val url: String,
    @SerialName("subscription_url")
    val subscriptionUrl: String
)

@Serializable
data class Repository(
    val id: Long,
    @SerialName("node_id")
    val nodeId: String,
    val name: String,
    @SerialName("full_name")
    val fullName: String,
    val owner: SimpleUser,
    val private: Boolean,
    @SerialName("html_url")
    val htmlUrl: String,
    val description: String?,
    val fork: Boolean,
    val url: String,
    @SerialName("archive_url")
    val archiveUrl: String,
    @SerialName("assignees_url")
    val assigneesUrl: String,
    @SerialName("blobs_url")
    val blobsUrl: String,
    @SerialName("branches_url")
    val branchesUrl: String,
    @SerialName("collaborators_url")
    val collaboratorsUrl: String,
    @SerialName("comments_url")
    val commentsUrl: String,
    @SerialName("commits_url")
    val commitsUrl: String,
    @SerialName("compare_url")
    val compareUrl: String,
    @SerialName("contents_url")
    val contentsUrl: String,
    @SerialName("contributors_url")
    val contributorsUrl: String,
    @SerialName("deployments_url")
    val deploymentsUrl: String,
    @SerialName("downloads_url")
    val downloadsUrl: String,
    @SerialName("events_url")
    val eventsUrl: String,
    @SerialName("forks_url")
    val forksUrl: String,
    @SerialName("git_commits_url")
    val gitCommitsUrl: String,
    @SerialName("git_refs_url")
    val gitRefsUrl: String,
    @SerialName("git_tags_url")
    val gitTagsUrl: String,
    @SerialName("git_url")
    val gitUrl: String?,
    @SerialName("issue_comment_url")
    val issueCommentUrl: String,
    @SerialName("issue_events_url")
    val issueEventsUrl: String,
    @SerialName("issues_url")
    val issuesUrl: String,
    @SerialName("keys_url")
    val keysUrl: String,
    @SerialName("labels_url")
    val labelsUrl: String,
    @SerialName("languages_url")
    val languagesUrl: String,
    @SerialName("merges_url")
    val mergesUrl: String,
    @SerialName("milestones_url")
    val milestonesUrl: String,
    @SerialName("notifications_url")
    val notificationsUrl: String,
    @SerialName("pulls_url")
    val pullsUrl: String,
    @SerialName("releases_url")
    val releasesUrl: String,
    @SerialName("stargazers_url")
    val stargazersUrl: String,
    @SerialName("statuses_url")
    val statusesUrl: String,
    @SerialName("subscribers_url")
    val subscribersUrl: String,
    @SerialName("subscription_url")
    val subscriptionUrl: String,
    @SerialName("tags_url")
    val tagsUrl: String,
    @SerialName("teams_url")
    val teamsUrl: String,
    @SerialName("trees_url")
    val treesUrl: String
)

@Serializable
data class SimpleUser(
    val name: String?,
    val email: String?,
    val login: String,
    val id: Long,
    @SerialName("node_id")
    val nodeId: String,
    @SerialName("avatar_url")
    val avatarUrl: String,
    @SerialName("gravatar_id")
    val gravatarId: String?,
    val url: String,
    @SerialName("html_url")
    val htmlUrl: String,
    @SerialName("followers_url")
    val followersUrl: String,
    @SerialName("following_url")
    val followingUrl: String,
    @SerialName("gists_url")
    val gistsUrl: String,
    @SerialName("starred_url")
    val starredUrl: String,
    @SerialName("subscriptions_url")
    val subscriptionsUrl: String,
    @SerialName("organizations_url")
    val organizationsUrl: String,
    @SerialName("repos_url")
    val reposUrl: String,
    @SerialName("events_url")
    val eventsUrl: String,
    @SerialName("received_events_url")
    val receivedEventsUrl: String,
    val type: String,
    @SerialName("site_admin")
    val siteAdmin: Boolean
)

@Serializable
data class Subject(
    val title: String,
    val url: String,
    @SerialName("latest_comment_url")
    val latestCommentUrl: String,
    val type: String
)
