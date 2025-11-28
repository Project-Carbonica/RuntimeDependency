package net.cubizor.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.io.FileOutputStream
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

/**
 * Task that transforms a JAR to use BootstrapMain as the entry point.
 *
 * This task:
 * 1. Copies the BootstrapMain class and Maven Resolver classes into the JAR
 * 2. Changes the Main-Class to BootstrapMain
 * 3. Sets Original-Main-Class to the user's main class
 *
 * The BootstrapMain will then resolve and load dependencies at runtime from Maven repositories.
 */
abstract class ConfigureStandaloneJarTask : DefaultTask() {

    /** The original main class that should be invoked */
    @get:Input
    abstract val originalMainClass: Property<String>

    /** The library path where runtime dependencies are located (legacy, not used in runtime mode) */
    @get:Input
    abstract val libraryPath: Property<String>

    /** The input JAR file to transform */
    @get:InputFile
    abstract val inputJar: RegularFileProperty

    /** The output JAR file */
    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    /** Maven Resolver and dependencies to embed */
    @get:InputFiles
    abstract val resolverJars: org.gradle.api.file.ConfigurableFileCollection
    
    @TaskAction
    fun configure() {
        val mainClass = originalMainClass.get()
        val libPath = libraryPath.get()
        val input = inputJar.get().asFile
        val output = outputJar.get().asFile

        if (mainClass.isEmpty()) {
            throw IllegalStateException("[RuntimeDependency] No main class specified for standalone mode")
        }

        println("[RuntimeDependency] Configuring standalone JAR: ${input.name}")
        println("[RuntimeDependency] Original main class: $mainClass")
        println("[RuntimeDependency] Embedding Maven Resolver and dependencies...")

        // Extract bootstrap classes from plugin JAR
        val bootstrapClasses = extractBootstrapClasses()

        // Extract Maven Resolver classes from resolver JARs
        val resolverClasses = extractResolverClasses()

        println("[RuntimeDependency] Bootstrap classes: ${bootstrapClasses.size}")
        println("[RuntimeDependency] Resolver classes: ${resolverClasses.size}")

        // Track added entries to avoid duplicates
        val addedEntries = mutableSetOf<String>()

        // Transform the JAR
        JarFile(input).use { jarIn ->
            val originalManifest = jarIn.manifest ?: Manifest()

            // Create new manifest with BootstrapMain as entry point
            val newManifest = Manifest(originalManifest)
            newManifest.mainAttributes.putValue("Main-Class", "net.cubizor.gradle.loader.BootstrapMain")
            newManifest.mainAttributes.putValue("Original-Main-Class", mainClass)

            JarOutputStream(FileOutputStream(output), newManifest).use { jarOut ->
                // Copy all existing entries (except manifest)
                val entries = jarIn.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name.equals("META-INF/MANIFEST.MF", ignoreCase = true)) {
                        continue // Skip manifest, we already wrote it
                    }

                    if (!addedEntries.contains(entry.name)) {
                        jarOut.putNextEntry(JarEntry(entry.name))
                        jarIn.getInputStream(entry).use { it.copyTo(jarOut) }
                        jarOut.closeEntry()
                        addedEntries.add(entry.name)
                    }
                }

                // Add bootstrap classes
                for ((className, classBytes) in bootstrapClasses) {
                    if (!addedEntries.contains(className)) {
                        jarOut.putNextEntry(JarEntry(className))
                        jarOut.write(classBytes)
                        jarOut.closeEntry()
                        addedEntries.add(className)
                    }
                }

                // Add resolver classes
                for ((className, classBytes) in resolverClasses) {
                    if (!addedEntries.contains(className)) {
                        jarOut.putNextEntry(JarEntry(className))
                        jarOut.write(classBytes)
                        jarOut.closeEntry()
                        addedEntries.add(className)
                    }
                }
            }
        }

        // Replace original JAR with transformed one
        if (input != output) {
            output.copyTo(input, overwrite = true)
            output.delete()
        }

        println("[RuntimeDependency] Successfully configured standalone JAR")
        println("[RuntimeDependency] Total entries embedded: ${addedEntries.size}")
    }
    
    /**
     * Extract BootstrapMain and its inner classes from the plugin JAR.
     */
    private fun extractBootstrapClasses(): Map<String, ByteArray> {
        val classes = mutableMapOf<String, ByteArray>()
        
        // Get the location of the plugin JAR
        val pluginClassUrl = ConfigureStandaloneJarTask::class.java
            .protectionDomain.codeSource?.location
        
        if (pluginClassUrl == null) {
            // Fallback: try to load from classloader resources
            return extractFromClassLoader()
        }
        
        val pluginJar = java.io.File(pluginClassUrl.toURI())
        
        if (!pluginJar.exists() || !pluginJar.isFile) {
            return extractFromClassLoader()
        }
        
        JarFile(pluginJar).use { jar ->
            val entries = jar.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                // Match BootstrapMain and its inner classes
                if (entry.name.startsWith("net/cubizor/gradle/loader/BootstrapMain") && 
                    entry.name.endsWith(".class")) {
                    classes[entry.name] = jar.getInputStream(entry).readBytes()
                }
            }
        }
        
        if (classes.isEmpty()) {
            throw IllegalStateException(
                "[RuntimeDependency] Could not find BootstrapMain classes in plugin JAR"
            )
        }
        
        return classes
    }
    
    /**
     * Fallback method to extract classes from classloader resources.
     */
    private fun extractFromClassLoader(): Map<String, ByteArray> {
        val classes = mutableMapOf<String, ByteArray>()
        val classLoader = ConfigureStandaloneJarTask::class.java.classLoader

        // List of classes to extract
        val classNames = listOf(
            "net/cubizor/gradle/loader/BootstrapMain.class",
            "net/cubizor/gradle/loader/BootstrapMain\$RuntimeClassLoader.class",
            "net/cubizor/gradle/loader/BootstrapMain\$ManifestData.class",
            "net/cubizor/gradle/loader/BootstrapMain\$DependencyCoordinate.class",
            "net/cubizor/gradle/loader/BootstrapMain\$RepositoryInfo.class"
        )

        for (className in classNames) {
            val stream = classLoader.getResourceAsStream(className)
            if (stream != null) {
                classes[className] = stream.use { it.readBytes() }
            }
        }

        if (classes.isEmpty()) {
            throw IllegalStateException(
                "[RuntimeDependency] Could not load BootstrapMain classes from classloader"
            )
        }

        return classes
    }

    /**
     * Extract all classes and resources from Maven Resolver JARs.
     */
    private fun extractResolverClasses(): Map<String, ByteArray> {
        val classes = mutableMapOf<String, ByteArray>()

        resolverJars.files.forEach { jarFile ->
            if (!jarFile.exists() || !jarFile.isFile || !jarFile.name.endsWith(".jar")) {
                return@forEach
            }

            try {
                JarFile(jarFile).use { jar ->
                    val entries = jar.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (!entry.isDirectory) {
                            // Skip module-info, multi-release classes, signature files, and manifests
                            if (entry.name == "module-info.class" ||
                                entry.name.startsWith("META-INF/versions/") ||
                                entry.name.startsWith("META-INF/maven/") ||
                                entry.name == "META-INF/MANIFEST.MF" ||
                                entry.name.endsWith(".SF") ||
                                entry.name.endsWith(".DSA") ||
                                entry.name.endsWith(".RSA")) {
                                continue
                            }

                            // Only add if not already present
                            if (!classes.containsKey(entry.name)) {
                                classes[entry.name] = jar.getInputStream(entry).readBytes()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                println("[RuntimeDependency] Warning: Could not read JAR ${jarFile.name}: ${e.message}")
            }
        }

        return classes
    }
}

/**
 * Task that generates the runtime-dependencies.txt file in META-INF.
 * Format:
 * DEP:groupId:artifactId:version
 * REPO:name:url:needsAuth:envPrefix
 */
abstract class GenerateDependencyManifestTask : DefaultTask() {

    /** List of dependency coordinates */
    @get:Input
    abstract val dependencies: ListProperty<DependencyInfo>

    /** Repository information */
    @get:Input
    abstract val repositories: org.gradle.api.provider.MapProperty<String, RepositoryInfo>

    /** Output resources directory */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val deps = dependencies.get()
        val repos = repositories.get()

        val metaInf = outputDir.get().asFile.resolve("META-INF")
        metaInf.mkdirs()

        val manifestFile = metaInf.resolve("runtime-dependencies.txt")
        val lines = mutableListOf<String>()

        lines.add("# Runtime dependencies manifest")
        lines.add("# Format: DEP:groupId:artifactId:version")
        lines.add("# Format: REPO:name:url:needsAuth:envPrefix")
        lines.add("")

        // Write dependencies
        deps.forEach { dep ->
            lines.add("DEP:${dep.groupId}:${dep.artifactId}:${dep.version}")
        }

        lines.add("")

        // Write repositories (exclude Maven Central as it's always available)
        repos.values.forEach { repo ->
            if (!repo.isMavenCentral) {
                val needsAuth = repo.usernameProperty != null && repo.passwordProperty != null
                val envPrefix = repo.name.uppercase().replace("-", "_").replace(".", "_")
                lines.add("REPO:${repo.name}:${repo.url}:$needsAuth:$envPrefix")
            }
        }

        // Always add Maven Central as fallback
        lines.add("REPO:MavenCentral:https://repo1.maven.org/maven2/:false:")

        manifestFile.writeText(lines.joinToString("\n"))

        println("[RuntimeDependency] Generated ${manifestFile.absolutePath}")
        println("[RuntimeDependency] Dependencies: ${deps.size} items")
        println("[RuntimeDependency] Repositories: ${repos.size} items")
    }
}
