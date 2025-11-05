package net.cubizor.gradle.launcher

import java.io.File
import java.lang.reflect.Method
import kotlin.system.exitProcess

/**
 * Bootstrap launcher for applications using RuntimeDependency plugin.
 * 
 * This class is set as the Main-Class in the JAR manifest. It:
 * 1. Loads runtime dependencies from the specified library path
 * 2. Creates a custom ClassLoader with those dependencies
 * 3. Invokes the actual main class with the custom ClassLoader context
 *
 * Configuration is done via system properties or environment variables:
 * - runtime.dependency.library.path: Path to runtime dependencies (required)
 * - runtime.dependency.main.class: Fully qualified main class name (required)
 */
object LauncherBootstrap {
    
    private const val LIBRARY_PATH_PROPERTY = "runtime.dependency.library.path"
    private const val MAIN_CLASS_PROPERTY = "runtime.dependency.main.class"
    
    private const val LIBRARY_PATH_ENV = "RUNTIME_DEPENDENCY_LIBRARY_PATH"
    private const val MAIN_CLASS_ENV = "RUNTIME_DEPENDENCY_MAIN_CLASS"

    @JvmStatic
    fun main(args: Array<String>) {
        println("=".repeat(60))
        println("RuntimeDependency Launcher Bootstrap")
        println("=".repeat(60))
        
        try {
            // 1. Get library path
            val libraryPath = getLibraryPath()
            println("[Bootstrap] Library path: $libraryPath")
            
            // 2. Get main class name
            val mainClassName = getMainClassName()
            println("[Bootstrap] Main class: $mainClassName")
            
            // 3. Create custom ClassLoader with dependencies
            println("[Bootstrap] Loading runtime dependencies...")
            val classLoader = RuntimeDependencyClassLoader(libraryPath)
            
            // 4. Load and invoke main class
            println("[Bootstrap] Launching application...")
            println("=".repeat(60))
            println()
            
            invokeMainClass(classLoader, mainClassName, args)
            
        } catch (e: Exception) {
            System.err.println("[Bootstrap] Failed to launch application: ${e.message}")
            e.printStackTrace()
            exitProcess(1)
        }
    }

    /**
     * Gets the library path from system properties or environment variables.
     * Falls back to default "runtime-dependencies" if not specified.
     */
    private fun getLibraryPath(): String {
        // Try system property first
        System.getProperty(LIBRARY_PATH_PROPERTY)?.let { return it }
        
        // Try environment variable
        System.getenv(LIBRARY_PATH_ENV)?.let { return it }
        
        // Default: relative to JAR location
        val jarLocation = getJarLocation()
        val defaultPath = File(jarLocation, "runtime-dependencies").absolutePath
        
        println("[Bootstrap] Using default library path: $defaultPath")
        return defaultPath
    }

    /**
     * Gets the main class name from system properties or environment variables.
     * Throws exception if not found.
     */
    private fun getMainClassName(): String {
        // Try system property first
        System.getProperty(MAIN_CLASS_PROPERTY)?.let { return it }
        
        // Try environment variable
        System.getenv(MAIN_CLASS_ENV)?.let { return it }
        
        // If not found, throw error
        throw IllegalStateException(
            "Main class not specified! Set one of:\n" +
            "  - System property: -D$MAIN_CLASS_PROPERTY=com.example.Main\n" +
            "  - Environment variable: $MAIN_CLASS_ENV=com.example.Main\n" +
            "  - Or configure in build.gradle.kts"
        )
    }

    /**
     * Gets the directory where the current JAR is located.
     */
    private fun getJarLocation(): File {
        val codeSource = LauncherBootstrap::class.java.protectionDomain.codeSource
        val jarFile = File(codeSource.location.toURI())
        return if (jarFile.isFile) jarFile.parentFile else jarFile
    }

    /**
     * Invokes the main method of the specified class using the custom ClassLoader.
     */
    private fun invokeMainClass(classLoader: ClassLoader, className: String, args: Array<String>) {
        // Set context ClassLoader
        val currentThread = Thread.currentThread()
        val originalClassLoader = currentThread.contextClassLoader
        
        try {
            currentThread.contextClassLoader = classLoader
            
            // Load main class
            val mainClass = classLoader.loadClass(className)
            
            // Find main method: public static void main(String[] args)
            val mainMethod: Method = try {
                mainClass.getMethod("main", Array<String>::class.java)
            } catch (e: NoSuchMethodException) {
                throw IllegalStateException("Main class $className does not have a main(String[]) method", e)
            }
            
            // Verify main method is static
            if (!java.lang.reflect.Modifier.isStatic(mainMethod.modifiers)) {
                throw IllegalStateException("Main method in $className is not static")
            }
            
            // Invoke main method
            mainMethod.invoke(null, args)
            
        } finally {
            // Restore original ClassLoader
            currentThread.contextClassLoader = originalClassLoader
        }
    }
}
