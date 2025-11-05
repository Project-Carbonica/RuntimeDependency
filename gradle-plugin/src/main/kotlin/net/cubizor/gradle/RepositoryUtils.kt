package net.cubizor.gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository

object RepositoryUtils {

    fun isMavenCentral(url: String): Boolean {
        val normalizedUrl = url.removeSuffix("/").lowercase()
        return normalizedUrl.contains("repo.maven.apache.org") ||
               normalizedUrl.contains("repo1.maven.org") ||
               normalizedUrl.contains("repo.maven.org")
    }

    fun collectAllRepositories(project: Project): Map<String, RepositoryInfo> {
        val repos = mutableMapOf<String, RepositoryInfo>()

        project.repositories.forEach { repo ->
            if (repo is MavenArtifactRepository) {
                val repoUrl = repo.url.toString().removeSuffix("/")
                val repoName = repo.name.ifEmpty { "maven-${repos.size}" }

                if (isMavenCentral(repoUrl)) {
                    return@forEach
                }

                if (repos.values.any { it.url == repoUrl }) {
                    return@forEach
                }

                val hasCredentialsConfigured = try {
                    val creds = repo.credentials
                    val username = creds.username
                    val password = creds.password
                    username != null && password != null
                } catch (e: Exception) {
                    false
                }

                repos[repoName] = RepositoryInfo(
                    name = repoName,
                    url = repoUrl,
                    usernameProperty = if (hasCredentialsConfigured) "${repoName}.username" else null,
                    passwordProperty = if (hasCredentialsConfigured) "${repoName}.password" else null,
                    isMavenCentral = false
                )
            }
        }

        return repos
    }

    fun detectAndApplyCredentials(project: Project) {
        project.repositories.withType(MavenArtifactRepository::class.java) {
            if (url.scheme in listOf("http", "https")) {
                val user = project.findProperty("${name}.username")?.toString()
                    ?: System.getenv("${name.uppercase()}_USERNAME")
                val pass = project.findProperty("${name}.password")?.toString()
                    ?: System.getenv("${name.uppercase()}_PASSWORD")

                if (user != null && pass != null) {
                    credentials {
                        username = user
                        password = pass
                    }
                }
            }
        }
    }
}
