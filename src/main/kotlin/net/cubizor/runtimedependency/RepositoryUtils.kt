package net.cubizor.runtimedependency

import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository

object RepositoryUtils {

    fun isMavenCentral(url: String): Boolean {
        val normalizedUrl = url.removeSuffix("/").lowercase()
        return normalizedUrl.contains("repo.maven.apache.org") ||
               normalizedUrl.contains("repo1.maven.org") ||
               normalizedUrl.contains("repo.maven.org")
    }

    fun isMavenLocal(url: String): Boolean {
        val normalizedUrl = url.removeSuffix("/").lowercase()
        return normalizedUrl.startsWith("file://") ||
               normalizedUrl.startsWith("file:") ||
               normalizedUrl.contains("/.m2/repository")
    }

    fun collectAllRepositories(project: Project): Map<String, RepositoryInfo> {
        val repos = mutableMapOf<String, RepositoryInfo>()
        var nextIndex = 0

        // Collect from project repositories
        // NOTE: Repositories defined in settings.gradle.kts dependencyResolutionManagement
        // are NOT included here due to Gradle API limitations.
        // Users must declare repositories in both settings.gradle.kts AND build.gradle.kts
        project.repositories.forEach { repo ->
            if (repo is MavenArtifactRepository) {
                val repoUrl = repo.url.toString().removeSuffix("/")
                
                // Skip Maven Central
                if (isMavenCentral(repoUrl)) {
                    return@forEach
                }

                // Skip duplicates
                if (repos.values.any { it.url == repoUrl }) {
                    return@forEach
                }

                val repoName = repo.name.ifEmpty {
                    if (isMavenLocal(repoUrl)) "MavenLocal" else "maven-$nextIndex"
                }
                nextIndex++

                // Only collect credentials for HTTP/HTTPS repositories
                // File protocol (mavenLocal) does not support authentication
                val isFileProtocol = repoUrl.startsWith("file://") || repoUrl.startsWith("file:")
                val hasCredentials = if (!isFileProtocol) {
                    try {
                        val creds = repo.credentials
                        creds.username != null && creds.password != null
                    } catch (e: Exception) {
                        false
                    }
                } else {
                    false
                }

                repos[repoName] = RepositoryInfo(
                    name = repoName,
                    url = repoUrl,
                    usernameProperty = if (hasCredentials) "${repoName}.username" else null,
                    passwordProperty = if (hasCredentials) "${repoName}.password" else null,
                    isMavenCentral = false,
                    isMavenLocal = isMavenLocal(repoUrl)
                )
            }
        }

        return repos
    }

}
