package net.cubizor.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.*
import java.io.File

class RuntimeDependencyPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.apply("java-library")

        val extension = project.extensions.create("runtimeDependency", RuntimeDependencyExtension::class.java)
        val paperExtension = project.objects.newInstance(PaperExtension::class.java)
        val standaloneExtension = project.objects.newInstance(StandaloneExtension::class.java)
        (extension as org.gradle.api.plugins.ExtensionAware).extensions.add("paper", paperExtension)
        (extension as org.gradle.api.plugins.ExtensionAware).extensions.add("standalone", standaloneExtension)

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
            val isPaperEnabled = paperExtension.enabled.get()
            val isStandaloneEnabled = standaloneExtension.enabled.get()

            // Validation: Both modes cannot be enabled simultaneously
            if (isPaperEnabled && isStandaloneEnabled) {
                throw IllegalStateException(
                    "Cannot enable both Paper and Standalone modes simultaneously. " +
                    "Please enable only one mode in your build.gradle.kts"
                )
            }

            if (isPaperEnabled) {
                configurePaperMode(project, paperExtension, generatePaperLoader, runtimeDownload)
            } else if (isStandaloneEnabled) {
                configureStandaloneMode(project, standaloneExtension, runtimeDownload, downloadRuntimeDependencies)
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
    }

    private fun configureStandaloneMode(
        project: Project,
        standaloneExtension: StandaloneExtension,
        runtimeDownload: Configuration,
        downloadTask: org.gradle.api.tasks.TaskProvider<RuntimeDependencyTask>
    ) {
        val mainClassName = standaloneExtension.mainClass.get()
        if (mainClassName.isEmpty()) {
            throw IllegalStateException(
                "Standalone mode requires mainClass to be set. Example:\n" +
                "runtimeDependency {\n" +
                "    standalone {\n" +
                "        enabled.set(true)\n" +
                "        mainClass.set(\"com.example.Main\")\n" +
                "    }\n" +
                "}"
            )
        }

        val libraryPath = standaloneExtension.libraryPath.get()
        
        // Get the list of runtime dependency JAR filenames
        val dependencyJarNames = runtimeDownload.resolvedConfiguration.resolvedArtifacts.map { artifact ->
            "${artifact.name}-${artifact.moduleVersion.id.version}.jar"
        }

        // Register the dependency manifest task
        val generateManifest = project.tasks.register("generateDependencyManifest", GenerateDependencyManifestTask::class.java) {
            group = "runtime"
            description = "Generates runtime-dependencies.txt in META-INF"
            dependencies.set(dependencyJarNames)
            outputDir.set(project.layout.buildDirectory.dir("resources/main"))
        }

        // Generate manifest before processResources
        project.tasks.findByName("processResources")?.let { processResources ->
            processResources.dependsOn(generateManifest)
        }

        // Get the jar task
        val jarTask = project.tasks.named("jar", Jar::class.java)
        
        // Register the standalone JAR configuration task
        val configureStandaloneJar = project.tasks.register("configureStandaloneJar", ConfigureStandaloneJarTask::class.java) {
            group = "runtime"
            description = "Configures JAR for standalone mode with BootstrapMain"
            originalMainClass.set(mainClassName)
            this.libraryPath.set(libraryPath)
            inputJar.set(jarTask.flatMap { it.archiveFile })
            outputJar.set(project.layout.buildDirectory.file("tmp/standalone-configured.jar"))
            
            dependsOn(jarTask)
        }

        // Configure the JAR task
        jarTask.configure {
            // Include the BootstrapMain class from plugin - this will be done by ConfigureStandaloneJarTask
            // but we still need to set a temporary Main-Class for the initial JAR
            manifest {
                attributes(
                    "Main-Class" to mainClassName, // Will be replaced by ConfigureStandaloneJarTask
                    "Runtime-Dependency-Library-Path" to libraryPath,
                    "Implementation-Title" to project.name,
                    "Implementation-Version" to project.version.toString()
                )
            }
            
            // Finalized by configureStandaloneJar
            finalizedBy(configureStandaloneJar)
        }

        // Ensure dependencies are downloaded before build
        project.tasks.named("build") {
            dependsOn(downloadTask)
            dependsOn(configureStandaloneJar)
        }

        println("[RuntimeDependency] Standalone mode enabled (Bootstrap)")
        println("[RuntimeDependency] Main-Class: $mainClassName")
        println("[RuntimeDependency] Library path: $libraryPath")
        println("[RuntimeDependency] Dependencies: ${dependencyJarNames.joinToString(", ")}")
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
                    repository = repoInfo
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
