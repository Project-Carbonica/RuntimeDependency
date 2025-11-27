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
 * 1. Copies the BootstrapMain class into the JAR
 * 2. Changes the Main-Class to BootstrapMain
 * 3. Sets Original-Main-Class to the user's main class
 * 4. Sets Library-Path attribute
 * 
 * The BootstrapMain will then load dependencies at runtime and
 * invoke the original main class through a custom classloader.
 */
abstract class ConfigureStandaloneJarTask : DefaultTask() {
    
    /** The original main class that should be invoked */
    @get:Input
    abstract val originalMainClass: Property<String>
    
    /** The library path where runtime dependencies are located */
    @get:Input
    abstract val libraryPath: Property<String>
    
    /** The input JAR file to transform */
    @get:InputFile
    abstract val inputJar: RegularFileProperty
    
    /** The output JAR file */
    @get:OutputFile
    abstract val outputJar: RegularFileProperty
    
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
        println("[RuntimeDependency] Library path: $libPath")
        
        // Extract bootstrap classes from plugin JAR
        val bootstrapClasses = extractBootstrapClasses()
        
        // Transform the JAR
        JarFile(input).use { jarIn ->
            val originalManifest = jarIn.manifest ?: Manifest()
            
            // Create new manifest with BootstrapMain as entry point
            val newManifest = Manifest(originalManifest)
            newManifest.mainAttributes.putValue("Main-Class", "net.cubizor.gradle.loader.BootstrapMain")
            newManifest.mainAttributes.putValue("Original-Main-Class", mainClass)
            newManifest.mainAttributes.putValue("Library-Path", libPath)
            
            JarOutputStream(FileOutputStream(output), newManifest).use { jarOut ->
                // Copy all existing entries (except manifest)
                val entries = jarIn.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name.equals("META-INF/MANIFEST.MF", ignoreCase = true)) {
                        continue // Skip manifest, we already wrote it
                    }
                    
                    jarOut.putNextEntry(JarEntry(entry.name))
                    jarIn.getInputStream(entry).use { it.copyTo(jarOut) }
                    jarOut.closeEntry()
                }
                
                // Add bootstrap classes
                for ((className, classBytes) in bootstrapClasses) {
                    jarOut.putNextEntry(JarEntry(className))
                    jarOut.write(classBytes)
                    jarOut.closeEntry()
                }
            }
        }
        
        // Replace original JAR with transformed one
        if (input != output) {
            output.copyTo(input, overwrite = true)
            output.delete()
        }
        
        println("[RuntimeDependency] Successfully configured standalone JAR")
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
            "net/cubizor/gradle/loader/BootstrapMain\$RuntimeClassLoader.class"
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
}

/**
 * Task that generates the runtime-dependencies.txt file in META-INF.
 */
abstract class GenerateDependencyManifestTask : DefaultTask() {
    
    /** List of dependency JAR filenames */
    @get:Input
    abstract val dependencies: ListProperty<String>
    
    /** Output resources directory */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty
    
    @TaskAction
    fun generate() {
        val deps = dependencies.get()
        
        if (deps.isEmpty()) {
            println("[RuntimeDependency] No dependencies to write to manifest")
            return
        }
        
        val metaInf = outputDir.get().asFile.resolve("META-INF")
        metaInf.mkdirs()
        
        val manifestFile = metaInf.resolve("runtime-dependencies.txt")
        manifestFile.writeText(
            "# Runtime dependencies for this module\n" +
            "# Generated by RuntimeDependency plugin\n" +
            deps.joinToString("\n")
        )
        
        println("[RuntimeDependency] Generated ${manifestFile.absolutePath}")
        println("[RuntimeDependency] Dependencies: ${deps.joinToString(", ")}")
    }
}
