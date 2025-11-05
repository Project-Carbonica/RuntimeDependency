package net.cubizor.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.*

class RuntimeDependencyPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.apply("java-library")

        val extension = project.extensions.create("runtimeDependency", RuntimeDependencyExtension::class.java)
        val paperExtension = project.objects.newInstance(PaperExtension::class.java)
        (extension as org.gradle.api.plugins.ExtensionAware).extensions.add("paper", paperExtension)

        val runtimeDownload = project.configurations.create("runtimeDownload") {
            isCanBeConsumed = false
            isCanBeResolved = true
        }

        project.configurations.named("implementation") {
            extendsFrom(runtimeDownload)
        }

        val downloadRuntimeDependencies = project.tasks.register("downloadRuntimeDependencies", RuntimeDependencyTask::class.java) {
            group = "runtime"
            description = "Downloads and organizes runtime dependencies"
            runtimeConfiguration.from(runtimeDownload)
            outputDir.set(project.layout.buildDirectory.dir(extension.outputDirectory))
            organizeByGroup.set(extension.organizeByGroup)
        }

        val generatePaperLoader = project.tasks.register("generatePaperLoader", GeneratePaperLoaderTask::class.java) {
            group = "paper"
            description = "Generates Paper PluginLoader for runtime dependencies"
            loaderPackage.set(paperExtension.loaderPackage)
            loaderClassName.set(paperExtension.loaderClassName)
            outputDir.set(project.layout.buildDirectory.dir("generated/sources/paper-loader/java"))
            resourcesDir.set(project.layout.projectDirectory.dir("src/main/resources"))
        }

        project.afterEvaluate {
            RepositoryUtils.detectAndApplyCredentials(project)

            if (paperExtension.enabled.get()) {
                val deps = analyzeDependencies(project, runtimeDownload)
                val repos = RepositoryUtils.collectAllRepositories(project)

                generatePaperLoader.configure {
                    dependencies.set(deps)
                    repositories.set(repos)
                }

                project.tasks.findByName("compileJava")?.dependsOn(generatePaperLoader)
                project.tasks.findByName("compileKotlin")?.dependsOn(generatePaperLoader)
                project.tasks.findByName("processResources")?.dependsOn(generatePaperLoader)

                project.extensions.findByType(JavaPluginExtension::class.java)?.let { java ->
                    java.sourceSets.getByName("main").java.srcDir(
                        project.layout.buildDirectory.dir("generated/sources/paper-loader/java")
                    )
                }
            } else {
                project.tasks.named("build") {
                    dependsOn(downloadRuntimeDependencies)
                }
            }
        }
    }

    private fun analyzeDependencies(project: Project, configuration: Configuration): List<DependencyInfo> {
        val result = mutableListOf<DependencyInfo>()

        configuration.resolvedConfiguration.resolvedArtifacts.forEach { artifact ->
            val id = artifact.moduleVersion.id
            val repoInfo = findRepositoryForArtifact(project, id.group, id.name, id.version)

            result.add(
                DependencyInfo(
                    groupId = id.group,
                    artifactId = id.name,
                    version = id.version,
                    repository = repoInfo
                )
            )
        }

        return result
    }

    private fun findRepositoryForArtifact(
        project: Project,
        group: String,
        name: String,
        version: String
    ): RepositoryInfo {
        val privateRepos = mutableListOf<RepositoryInfo>()
        val projectsToScan = mutableListOf(project)
        if (project.parent != null) {
            projectsToScan.add(project.parent!!)
        }

        for (proj in projectsToScan) {
            for (repo in proj.repositories) {
                if (repo is MavenArtifactRepository) {
                    val repoUrl = repo.url.toString().removeSuffix("/")
                    val repoName = repo.name.ifEmpty { "maven" }

                    if (RepositoryUtils.isMavenCentral(repoUrl)) {
                        continue
                    }

                    if (privateRepos.any { it.url == repoUrl }) {
                        continue
                    }

                    privateRepos.add(
                        RepositoryInfo(
                            name = repoName,
                            url = repoUrl,
                            usernameProperty = "${repoName}.username",
                            passwordProperty = "${repoName}.password",
                            isMavenCentral = false
                        )
                    )
                }
            }
        }

        return privateRepos.firstOrNull() ?: RepositoryInfo(
            name = "MavenCentral",
            url = "https://repo1.maven.org/maven2/",
            isMavenCentral = true
        )
    }
}
