package net.cubizor.runtimedependency

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.plugins.JavaPluginExtension

class RuntimeDependencyPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Apply java-library only if no java plugin is already applied
        if (!project.plugins.hasPlugin("java") && !project.plugins.hasPlugin("java-library")) {
            project.plugins.apply("java-library")
        }

        val extension = project.extensions.create("runtimeDependency", RuntimeDependencyExtension::class.java)
        val paperExtension = project.objects.newInstance(PaperExtension::class.java)
        (extension as org.gradle.api.plugins.ExtensionAware).extensions.add("paper", paperExtension)

        // Configuration for remote dependencies (Maven Central, private repos with HTTP/HTTPS)
        val runtimeDownload = project.configurations.create("runtimeDownload") {
            isCanBeConsumed = false
            isCanBeResolved = true
        }

        // Configuration for local test dependencies (MavenLocal) that are bundled in the JAR
        // These dependencies will NOT be added to Paper's PluginLoader but will be in implementation
        val runtimeLocalTest = project.configurations.create("runtimeLocalTest") {
            isCanBeConsumed = false
            isCanBeResolved = true
        }

        project.configurations.named("implementation") {
            extendsFrom(runtimeDownload)
            extendsFrom(runtimeLocalTest)  // Bundle local deps automatically
        }

        val downloadRuntimeDependencies = project.tasks.register("downloadRuntimeDependencies", RuntimeDependencyTask::class.java) {
            group = "runtime"
            description = "Downloads and organizes runtime dependencies"
            runtimeConfiguration.from(runtimeDownload)
            runtimeConfiguration.from(runtimeLocalTest)
            outputDir.set(project.layout.buildDirectory.dir(extension.outputDirectory))
            organizeByGroup.set(extension.organizeByGroup)
        }

        // Configure JAR task to include runtimeLocalTest dependencies
        project.afterEvaluate {
            project.tasks.named("jar", org.gradle.jvm.tasks.Jar::class.java) {
                from({
                    runtimeLocalTest.filter { it.exists() }.map { if (it.isDirectory) it else project.zipTree(it) }
                })
                duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.EXCLUDE
            }
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
            val isPaperEnabled = paperExtension.enabled.get()

            if (isPaperEnabled) {
                // Auto-detect plugin package if user hasn't customized it
                val currentPackage = paperExtension.loaderPackage.get()
                if (currentPackage == "net.cubizor.loader") {
                    detectPluginPackage(project)?.let { detectedPackage ->
                        generatePaperLoader.configure {
                            loaderPackage.set(detectedPackage)
                        }
                    }
                }
                
                configurePaperMode(project, paperExtension, generatePaperLoader, runtimeDownload)
            } else {
                // Default mode: just download dependencies
                project.tasks.named("build") {
                    dependsOn(downloadRuntimeDependencies)
                }
            }
        }
    }

    private fun configurePaperMode(
        project: Project,
        paperExtension: PaperExtension,
        generatePaperLoader: org.gradle.api.tasks.TaskProvider<GeneratePaperLoaderTask>,
        runtimeDownload: Configuration
    ) {
        // Only analyze runtimeDownload configuration for Paper loader
        // runtimeLocalTest deps should be shadowJar'd, not loaded at runtime
        val deps = analyzeDependencies(project, runtimeDownload)
        val repos = RepositoryUtils.collectAllRepositories(project)

        generatePaperLoader.configure {
            dependencies.set(deps)
            repositories.set(repos)
        }

        // Add generated sources to main source set BEFORE compile tasks run
        project.extensions.findByType(JavaPluginExtension::class.java)?.let { java ->
            java.sourceSets.getByName("main").java.srcDir(
                project.layout.buildDirectory.dir("generated/sources/paper-loader/java")
            )
        }

        // Wire task dependencies safely - ensure generatePaperLoader runs before any compilation
        project.tasks.configureEach {
            when {
                name == "compileJava" || name == "compileKotlin" -> {
                    dependsOn(generatePaperLoader)
                }
                name == "processResources" -> {
                    dependsOn(generatePaperLoader)
                }
                // Kapt support - ensure generated sources are available before stub generation
                name.startsWith("kapt") && name.contains("Kotlin") -> {
                    dependsOn(generatePaperLoader)
                }
            }
        }
    }

    /**
     * Detects the plugin's main package from plugin.yml or paper-plugin.yml.
     * Falls back to project.group if not found.
     */
    private fun detectPluginPackage(project: Project): String? {
        val resourcesDir = project.layout.projectDirectory.dir("src/main/resources").asFile
        val pluginYmlFiles = listOf(
            resourcesDir.resolve("paper-plugin.yml"),
            resourcesDir.resolve("plugin.yml")
        )

        for (ymlFile in pluginYmlFiles) {
            if (!ymlFile.exists()) continue

            try {
                val mainClassLine = ymlFile.readLines()
                    .firstOrNull { it.trim().startsWith("main:") }
                    ?: continue

                val mainClass = mainClassLine.substringAfter("main:")
                    .trim()
                    .trim('"', '\'')

                if (mainClass.isNotBlank() && mainClass.contains('.')) {
                    val detectedPackage = mainClass.substringBeforeLast('.')
                    project.logger.lifecycle("[RuntimeDependency] Detected plugin package from ${ymlFile.name}: $detectedPackage")
                    return detectedPackage
                }
            } catch (e: Exception) {
                project.logger.warn("[RuntimeDependency] Failed to read ${ymlFile.name}: ${e.message}")
            }
        }

        // Fallback to project.group if available
        val projectGroup = project.group.toString()
        if (projectGroup.isNotBlank() && projectGroup != "unspecified") {
            project.logger.lifecycle("[RuntimeDependency] Using project.group as package: $projectGroup")
            return projectGroup
        }

        return null
    }

    private fun analyzeDependencies(project: Project, configuration: Configuration): List<DependencyInfo> {
        val result = mutableListOf<DependencyInfo>()

        configuration.resolvedConfiguration.resolvedArtifacts.forEach { artifact ->
            val id = artifact.moduleVersion.id
            val repoInfo = findRepositoryForArtifact(project)

            result.add(
                DependencyInfo(
                    groupId = id.group,
                    artifactId = id.name,
                    version = id.version,
                    repository = repoInfo,
                    isFromMavenLocal = false  // runtimeDownload only has remote deps
                )
            )
        }

        return result
    }

    private fun findRepositoryForArtifact(project: Project): RepositoryInfo {
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

                    val isMavenLocal = RepositoryUtils.isMavenLocal(repoUrl)

                    privateRepos.add(
                        RepositoryInfo(
                            name = repoName,
                            url = repoUrl,
                            usernameProperty = if (!isMavenLocal) "${repoName}.username" else null,
                            passwordProperty = if (!isMavenLocal) "${repoName}.password" else null,
                            isMavenCentral = false,
                            isMavenLocal = isMavenLocal
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
