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
        val velocityExtension = project.objects.newInstance(VelocityExtension::class.java)
        (extension as org.gradle.api.plugins.ExtensionAware).extensions.add("paper", paperExtension)
        (extension as org.gradle.api.plugins.ExtensionAware).extensions.add("standalone", standaloneExtension)
        (extension as org.gradle.api.plugins.ExtensionAware).extensions.add("velocity", velocityExtension)

        val runtimeDownload = project.configurations.create("runtimeDownload") {
            isCanBeConsumed = false
            isCanBeResolved = true
        }

        project.configurations.named("implementation") {
            extendsFrom(runtimeDownload)
        }

        // Create configuration for Maven Resolver dependencies (for standalone mode)
        val resolverConfiguration = project.configurations.create("runtimeDependencyResolver") {
            isCanBeResolved = true
            isCanBeConsumed = false
            isTransitive = true
        }

        // Add Maven Resolver dependencies for runtime resolution
        project.dependencies.add("runtimeDependencyResolver", "org.apache.maven.resolver:maven-resolver-api:1.9.18")
        project.dependencies.add("runtimeDependencyResolver", "org.apache.maven.resolver:maven-resolver-spi:1.9.18")
        project.dependencies.add("runtimeDependencyResolver", "org.apache.maven.resolver:maven-resolver-util:1.9.18")
        project.dependencies.add("runtimeDependencyResolver", "org.apache.maven.resolver:maven-resolver-impl:1.9.18")
        project.dependencies.add("runtimeDependencyResolver", "org.apache.maven.resolver:maven-resolver-connector-basic:1.9.18")
        project.dependencies.add("runtimeDependencyResolver", "org.apache.maven.resolver:maven-resolver-transport-http:1.9.18")
        project.dependencies.add("runtimeDependencyResolver", "org.apache.maven:maven-resolver-provider:3.9.6")
        project.dependencies.add("runtimeDependencyResolver", "org.slf4j:slf4j-simple:2.0.9")

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

        val generateVelocityUtility = project.tasks.register("generateVelocityUtility", GenerateVelocityUtilityTask::class.java) {
            group = "velocity"
            description = "Generates Velocity utility class for runtime dependencies"
            utilityPackage.set(velocityExtension.utilityPackage)
            utilityClassName.set(velocityExtension.utilityClassName)
            outputDir.set(project.layout.buildDirectory.dir("generated/sources/velocity-utility/java"))
        }

        project.afterEvaluate {
            val isPaperEnabled = paperExtension.enabled.get()
            val isStandaloneEnabled = standaloneExtension.enabled.get()
            val isVelocityEnabled = velocityExtension.enabled.get()

            // Validation: Only one mode can be enabled at a time
            val enabledModes = listOf(isPaperEnabled, isStandaloneEnabled, isVelocityEnabled).count { it }
            if (enabledModes > 1) {
                throw IllegalStateException(
                    "Cannot enable multiple modes simultaneously. " +
                    "Please enable only one of: Paper, Standalone, or Velocity mode in your build.gradle.kts"
                )
            }

            if (isPaperEnabled) {
                configurePaperMode(project, paperExtension, generatePaperLoader, runtimeDownload)
            } else if (isStandaloneEnabled) {
                configureStandaloneMode(project, standaloneExtension, runtimeDownload, downloadRuntimeDependencies, resolverConfiguration)
            } else if (isVelocityEnabled) {
                configureVelocityMode(project, velocityExtension, generateVelocityUtility, runtimeDownload, downloadRuntimeDependencies)
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
        downloadTask: org.gradle.api.tasks.TaskProvider<RuntimeDependencyTask>,
        resolverConfiguration: Configuration
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

        // Analyze dependencies and repositories for runtime resolution
        val deps = analyzeDependencies(project, runtimeDownload)
        val repos = RepositoryUtils.collectAllRepositories(project)

        // Register the dependency manifest task
        val generateManifest = project.tasks.register("generateDependencyManifest", GenerateDependencyManifestTask::class.java) {
            group = "runtime"
            description = "Generates runtime-dependencies.txt in META-INF with dependency coordinates"
            dependencies.set(deps)
            repositories.set(repos)
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
            description = "Configures JAR for standalone mode with BootstrapMain and Maven Resolver"
            originalMainClass.set(mainClassName)
            this.libraryPath.set(libraryPath)
            inputJar.set(jarTask.flatMap { it.archiveFile })
            outputJar.set(project.layout.buildDirectory.file("tmp/standalone-configured.jar"))
            resolverJars.from(resolverConfiguration)

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

        // No build-time download in standalone mode - runtime resolution via BootstrapMain
        project.tasks.named("build") {
            dependsOn(configureStandaloneJar)
        }

        println("[RuntimeDependency] Standalone mode enabled (Runtime Resolution)")
        println("[RuntimeDependency] Main-Class: $mainClassName")
        println("[RuntimeDependency] Dependencies will be resolved at runtime from Maven repositories")
        println("[RuntimeDependency] Dependencies: ${deps.joinToString(", ") { "${it.groupId}:${it.artifactId}:${it.version}" }}")
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

    private fun configureVelocityMode(
        project: Project,
        velocityExtension: VelocityExtension,
        generateVelocityUtility: org.gradle.api.tasks.TaskProvider<GenerateVelocityUtilityTask>,
        runtimeDownload: Configuration,
        downloadTask: org.gradle.api.tasks.TaskProvider<RuntimeDependencyTask>
    ) {
        // Analyze dependencies and repositories for runtime resolution
        val deps = analyzeDependencies(project, runtimeDownload)
        val repos = RepositoryUtils.collectAllRepositories(project)

        // Register the dependency manifest task (same as Standalone mode)
        val generateManifest = project.tasks.register("generateVelocityManifest", GenerateDependencyManifestTask::class.java) {
            group = "velocity"
            description = "Generates runtime-dependencies.txt in META-INF for Velocity mode"
            dependencies.set(deps)
            repositories.set(repos)
            outputDir.set(project.layout.buildDirectory.dir("resources/main"))
        }

        // Configure the utility generator task
        generateVelocityUtility.configure {
            dependencies.set(deps)
            repositories.set(repos)
        }

        // Generate manifest before processResources
        project.tasks.findByName("processResources")?.let { processResources ->
            processResources.dependsOn(generateManifest)
        }

        // Add generated sources to main source set
        project.tasks.findByName("compileJava")?.dependsOn(generateVelocityUtility)
        project.tasks.findByName("compileKotlin")?.dependsOn(generateVelocityUtility)

        project.extensions.findByType(JavaPluginExtension::class.java)?.let { java ->
            java.sourceSets.getByName("main").java.srcDir(
                project.layout.buildDirectory.dir("generated/sources/velocity-utility/java")
            )
        }

        // Create configuration for Maven Resolver dependencies
        val resolverConfiguration = project.configurations.findByName("runtimeDependencyResolver")
            ?: project.configurations.create("velocityResolver") {
                isCanBeResolved = true
                isCanBeConsumed = false
                isTransitive = true
            }

        // Embed Maven Resolver into the plugin JAR (like Standalone mode)
        val jarTask = project.tasks.named("jar", Jar::class.java)
        
        jarTask.configure {
            // Embed Maven Resolver classes and resources (flatten all JARs)
            from(resolverConfiguration.elements.map { artifacts ->
                artifacts.map { artifact ->
                    project.zipTree(artifact.asFile)
                }
            }) {
                exclude("META-INF/MANIFEST.MF")
                exclude("META-INF/*.SF")
                exclude("META-INF/*.DSA")
                exclude("META-INF/*.RSA")
                exclude("META-INF/maven/**")
                exclude("META-INF/versions/**")
                exclude("module-info.class")
            }
        }

        println("[RuntimeDependency] Velocity mode enabled (Runtime Resolution)")
        println("[RuntimeDependency] Utility class: ${velocityExtension.utilityPackage.get()}.${velocityExtension.utilityClassName.get()}")
        println("[RuntimeDependency] Dependencies will be resolved at runtime from Maven repositories")
        println("[RuntimeDependency] Dependencies: ${deps.joinToString(", ") { "${it.groupId}:${it.artifactId}:${it.version}" }}")
        println("[RuntimeDependency] Repositories: ${repos.size} configured")
    }
}
