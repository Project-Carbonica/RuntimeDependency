package net.cubizor.gradle.loader;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * Bootstrap main class that loads runtime dependencies before running the actual main class.
 * <p>
 * This class is injected into standalone JARs as the entry point.
 * It creates a custom classloader with all dependencies and the application JAR,
 * then loads and runs the actual main class through this classloader.
 * <p>
 * The JAR manifest should have:
 * - Main-Class: net.cubizor.gradle.loader.BootstrapMain
 * - Original-Main-Class: com.example.YourActualMainClass
 * - Library-Path: libs (or custom path)
 */
public final class BootstrapMain {
    
    public static void main(String[] args) throws Exception {
        // Get manifest attributes from our JAR
        String originalMainClass = null;
        String libraryPath = "libs";
        
        // Try to read from system properties first (set by manifest)
        originalMainClass = System.getProperty("runtime.dependency.main.class");
        String customLibPath = System.getProperty("runtime.dependency.library.path");
        if (customLibPath != null && !customLibPath.isEmpty()) {
            libraryPath = customLibPath;
        }
        
        // If not in system properties, read from manifest
        if (originalMainClass == null) {
            try {
                java.util.jar.Manifest manifest = getManifest();
                if (manifest != null) {
                    java.util.jar.Attributes attrs = manifest.getMainAttributes();
                    originalMainClass = attrs.getValue("Original-Main-Class");
                    String manifestLibPath = attrs.getValue("Library-Path");
                    if (manifestLibPath != null && !manifestLibPath.isEmpty()) {
                        libraryPath = manifestLibPath;
                    }
                }
            } catch (Exception e) {
                // Ignore manifest read errors
            }
        }
        
        if (originalMainClass == null || originalMainClass.isEmpty()) {
            System.err.println("[RuntimeDependency] ERROR: No Original-Main-Class specified!");
            System.err.println("[RuntimeDependency] Please set 'Original-Main-Class' in manifest or");
            System.err.println("[RuntimeDependency] set system property 'runtime.dependency.main.class'");
            System.exit(1);
            return;
        }
        
        // Read dependencies from manifest
        List<String> dependencies = readDependencies();
        
        // Get the location of this JAR
        URL jarLocation = BootstrapMain.class.getProtectionDomain().getCodeSource().getLocation();
        
        // Create custom classloader with all dependencies
        List<URL> urls = new ArrayList<>();
        
        // Add our JAR first
        urls.add(jarLocation);
        
        // Add all dependencies
        File libDir = new File(libraryPath);
        if (libDir.exists() && libDir.isDirectory()) {
            for (String dep : dependencies) {
                File jarFile = findJarFile(libDir, dep);
                if (jarFile != null) {
                    urls.add(jarFile.toURI().toURL());
                } else {
                    System.err.println("[RuntimeDependency] WARNING: Library not found: " + dep);
                }
            }
        } else if (!dependencies.isEmpty()) {
            System.err.println("[RuntimeDependency] WARNING: Library path not found: " + libDir.getAbsolutePath());
        }
        
        // Create classloader with child-first strategy
        RuntimeClassLoader loader = new RuntimeClassLoader(
            urls.toArray(new URL[0]),
            BootstrapMain.class.getClassLoader()
        );
        
        // Set as thread context classloader
        Thread.currentThread().setContextClassLoader(loader);
        
        // Load and run the original main class
        try {
            Class<?> mainClass = loader.loadClass(originalMainClass);
            Method mainMethod = mainClass.getMethod("main", String[].class);
            mainMethod.invoke(null, (Object) args);
        } catch (ClassNotFoundException e) {
            System.err.println("[RuntimeDependency] ERROR: Main class not found: " + originalMainClass);
            e.printStackTrace();
            System.exit(1);
        } catch (NoSuchMethodException e) {
            System.err.println("[RuntimeDependency] ERROR: Main method not found in: " + originalMainClass);
            e.printStackTrace();
            System.exit(1);
        } catch (Exception e) {
            System.err.println("[RuntimeDependency] ERROR: Failed to run main class");
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static java.util.jar.Manifest getManifest() {
        try {
            URL manifestUrl = BootstrapMain.class.getResource("/META-INF/MANIFEST.MF");
            if (manifestUrl != null) {
                try (InputStream is = manifestUrl.openStream()) {
                    return new java.util.jar.Manifest(is);
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }
    
    private static List<String> readDependencies() {
        List<String> deps = new ArrayList<>();
        String resourcePath = "/META-INF/runtime-dependencies.txt";
        
        try (InputStream is = BootstrapMain.class.getResourceAsStream(resourcePath)) {
            if (is != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty() && !line.startsWith("#")) {
                            deps.add(line);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        
        return deps;
    }
    
    private static File findJarFile(File libDir, String jarName) {
        File direct = new File(libDir, jarName);
        if (direct.exists()) {
            return direct;
        }
        return findJarRecursively(libDir, jarName);
    }
    
    private static File findJarRecursively(File dir, String jarName) {
        File[] files = dir.listFiles();
        if (files == null) return null;
        
        for (File file : files) {
            if (file.isFile() && file.getName().equals(jarName)) {
                return file;
            }
            if (file.isDirectory()) {
                File found = findJarRecursively(file, jarName);
                if (found != null) return found;
            }
        }
        return null;
    }
    
    /**
     * Custom classloader with child-first strategy for loaded dependencies.
     */
    private static class RuntimeClassLoader extends URLClassLoader {
        
        static {
            ClassLoader.registerAsParallelCapable();
        }
        
        public RuntimeClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }
        
        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                // Check if already loaded
                Class<?> c = findLoadedClass(name);
                if (c != null) {
                    if (resolve) resolveClass(c);
                    return c;
                }
                
                // JDK classes must go to parent
                if (name.startsWith("java.") || 
                    name.startsWith("javax.") ||
                    name.startsWith("sun.") ||
                    name.startsWith("jdk.")) {
                    return super.loadClass(name, resolve);
                }
                
                // Bootstrap class stays in parent
                if (name.equals(BootstrapMain.class.getName()) ||
                    name.equals(RuntimeClassLoader.class.getName())) {
                    return super.loadClass(name, resolve);
                }
                
                // Try to find in our URLs first (child-first)
                try {
                    c = findClass(name);
                    if (resolve) resolveClass(c);
                    return c;
                } catch (ClassNotFoundException e) {
                    // Not found in our URLs, delegate to parent
                }
                
                // Fallback to parent
                return super.loadClass(name, resolve);
            }
        }
    }
}
