package net.cubizor.gradle.launcher

import java.io.File
import java.net.URL
import java.net.URLClassLoader

/**
 * Custom ClassLoader for loading runtime dependencies from a specified directory.
 * 
 * This ClassLoader extends URLClassLoader and automatically discovers and loads
 * all JAR files from the provided library path.
 *
 * @param libraryPath The directory containing JAR files to load
 * @param parent The parent ClassLoader (usually the system ClassLoader)
 */
class RuntimeDependencyClassLoader(
    libraryPath: File,
    parent: ClassLoader = ClassLoader.getSystemClassLoader()
) : URLClassLoader(
    collectJarUrls(libraryPath),
    parent
) {
    companion object {
        /**
         * Recursively scans the library path and collects all JAR file URLs.
         *
         * @param libraryPath The root directory to scan for JAR files
         * @return Array of URLs pointing to JAR files
         */
        @JvmStatic
        private fun collectJarUrls(libraryPath: File): Array<URL> {
            if (!libraryPath.exists() || !libraryPath.isDirectory) {
                println("[RuntimeDependency] Warning: Library path does not exist or is not a directory: ${libraryPath.absolutePath}")
                return emptyArray()
            }

            val jarFiles = mutableListOf<URL>()
            
            libraryPath.walkTopDown()
                .filter { it.isFile && it.extension.equals("jar", ignoreCase = true) }
                .forEach { jarFile ->
                    try {
                        jarFiles.add(jarFile.toURI().toURL())
                        println("[RuntimeDependency] Loaded: ${jarFile.name}")
                    } catch (e: Exception) {
                        System.err.println("[RuntimeDependency] Failed to load JAR: ${jarFile.absolutePath}")
                        e.printStackTrace()
                    }
                }

            println("[RuntimeDependency] Total ${jarFiles.size} JAR files loaded from ${libraryPath.absolutePath}")
            return jarFiles.toTypedArray()
        }
    }

    /**
     * Creates a RuntimeDependencyClassLoader from a library path string.
     *
     * @param libraryPath Path to the directory containing JAR files
     * @param parent The parent ClassLoader
     */
    constructor(libraryPath: String, parent: ClassLoader = ClassLoader.getSystemClassLoader()) 
        : this(File(libraryPath), parent)
}
