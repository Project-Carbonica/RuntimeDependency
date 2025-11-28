package net.cubizor.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.io.File

abstract class RuntimeDependencyTask : DefaultTask() {
    @get:Input
    abstract val organizeByGroup: Property<Boolean>

    @get:InputFiles
    abstract val runtimeConfiguration: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun download() {
        val output = outputDir.get().asFile
        output.deleteRecursively()
        output.mkdirs()

        val organize = organizeByGroup.get()

        runtimeConfiguration.files.forEach { file ->
            val destFile = if (organize) {
                val parts = file.nameWithoutExtension.split("-")
                val artifact = if (parts.size >= 2) {
                    parts.dropLast(1).joinToString("-")
                } else {
                    "unknown"
                }
                File(output, "$artifact/${file.name}")
            } else {
                File(output, file.name)
            }

            destFile.parentFile.mkdirs()
            file.copyTo(destFile, overwrite = true)
        }

        println("Runtime dependencies downloaded to: ${output.absolutePath}")
        output.walkTopDown().filter { it.isFile }.forEach {
            println("  - ${it.relativeTo(output)}")
        }
    }
}

abstract class GeneratePaperLoaderTask : DefaultTask() {
    @get:Input
    abstract val loaderPackage: Property<String>

    @get:Input
    abstract val loaderClassName: Property<String>

    @get:Input
    abstract val dependencies: ListProperty<DependencyInfo>

    @get:Input
    abstract val repositories: MapProperty<String, RepositoryInfo>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Internal
    abstract val resourcesDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val packageName = loaderPackage.get()
        val className = loaderClassName.get()
        val deps = dependencies.get()
        val repos = repositories.get()

        val loaderClass = generateLoaderClass(packageName, className, deps, repos)

        val packagePath = packageName.replace('.', '/')
        val outputFile = outputDir.get().asFile.resolve("$packagePath/$className.java")
        outputFile.parentFile.mkdirs()
        outputFile.writeText(loaderClass)

        println("Generated Paper PluginLoader: ${outputFile.absolutePath}")

        updatePluginYml(packageName, className)
    }

    private fun generateLoaderClass(
        packageName: String,
        className: String,
        deps: List<DependencyInfo>,
        repos: Map<String, RepositoryInfo>
    ): String {
        val repoMap = repos.filter { !it.value.isMavenCentral }
        val needsAuth = repoMap.values.any { it.usernameProperty != null || it.passwordProperty != null }
        val authImport = if (needsAuth) {
            "import org.eclipse.aether.util.repository.AuthenticationBuilder;"
        } else ""

        val repoCode = repoMap.values.distinctBy { it.url }.joinToString("\n") { repo ->
            val builderCode = StringBuilder()
            builderCode.append("        RemoteRepository.Builder ${repo.name}Builder = new RemoteRepository.Builder(\"${repo.name}\", \"default\", \"${repo.url}\");")

            if (repo.usernameProperty != null && repo.passwordProperty != null) {
                builderCode.append("\n")
                val envPrefix = repo.name.uppercase().replace("-", "_").replace(".", "_")
                builderCode.append("        // Try environment variable first, then system property\n")
                builderCode.append("        String ${repo.name}User = System.getenv(\"${envPrefix}_USERNAME\");\n")
                builderCode.append("        System.out.println(\"[RuntimeDependency] ${repo.name} - ENV ${envPrefix}_USERNAME: \" + (${repo.name}User != null ? \"[SET]\" : \"[NOT SET]\"));\n")
                builderCode.append("        if (${repo.name}User == null) {\n")
                builderCode.append("            ${repo.name}User = System.getProperty(\"${repo.usernameProperty}\");\n")
                builderCode.append("            System.out.println(\"[RuntimeDependency] ${repo.name} - SYSPROP ${repo.usernameProperty}: \" + (${repo.name}User != null ? \"[SET]\" : \"[NOT SET]\"));\n")
                builderCode.append("        }\n")
                builderCode.append("        String ${repo.name}Pass = System.getenv(\"${envPrefix}_PASSWORD\");\n")
                builderCode.append("        if (${repo.name}Pass == null) {\n")
                builderCode.append("            ${repo.name}Pass = System.getProperty(\"${repo.passwordProperty}\");\n")
                builderCode.append("        }\n")
                builderCode.append("        if (${repo.name}User != null && ${repo.name}Pass != null) {\n")
                builderCode.append("            System.out.println(\"[RuntimeDependency] ${repo.name} - Authentication ENABLED\");\n")
                builderCode.append("            ${repo.name}Builder.setAuthentication(new AuthenticationBuilder()\n")
                builderCode.append("                .addUsername(${repo.name}User)\n")
                builderCode.append("                .addPassword(${repo.name}Pass)\n")
                builderCode.append("                .build());\n")
                builderCode.append("        } else {\n")
                builderCode.append("            System.out.println(\"[RuntimeDependency] ${repo.name} - Authentication DISABLED (credentials not found)\");\n")
                builderCode.append("        }\n")
            }

            builderCode.append("        resolver.addRepository(${repo.name}Builder.build());")
            builderCode.toString()
        }

        return """
package $packageName;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
$authImport
import org.jetbrains.annotations.NotNull;

public class $className implements PluginLoader {
    @Override
    public void classloader(@NotNull PluginClasspathBuilder classpathBuilder) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();

${deps.joinToString("\n") { dep ->
        "        resolver.addDependency(new Dependency(new DefaultArtifact(\"${dep.groupId}:${dep.artifactId}:${dep.version}\"), null));"
}}

$repoCode

        classpathBuilder.addLibrary(resolver);
    }
}
""".trimIndent()
    }

    private fun updatePluginYml(packageName: String, className: String) {
        val srcDir = resourcesDir.get().asFile
        val paperPluginYml = srcDir.resolve("paper-plugin.yml")
        val pluginYml = srcDir.resolve("plugin.yml")

        val targetFile = when {
            paperPluginYml.exists() -> paperPluginYml
            pluginYml.exists() -> pluginYml
            else -> {
                println("Warning: No plugin.yml or paper-plugin.yml found, skipping loader update")
                return
            }
        }

        val isPaperPluginYml = targetFile.name == "paper-plugin.yml"
        val loaderKey = if (isPaperPluginYml) "loader:" else "paper-plugin-loader:"
        val loaderLine = "$loaderKey $packageName.$className"

        val lines = targetFile.readLines().toMutableList()
        val loaderIndex = lines.indexOfFirst {
            it.trim().startsWith("loader:") || it.trim().startsWith("paper-plugin-loader:")
        }

        if (loaderIndex >= 0) {
            lines[loaderIndex] = loaderLine
        } else {
            lines.add(0, loaderLine)
        }

        targetFile.writeText(lines.joinToString("\n"))
        println("Updated ${targetFile.name} with $loaderKey $packageName.$className")
    }
}

/**
 * Task that generates a Velocity utility class for runtime dependency loading.
 *
 * Similar to Standalone mode's BootstrapMain, this generates a utility class
 * that uses Maven Resolver to download dependencies at runtime from Maven repositories.
 */
abstract class GenerateVelocityUtilityTask : DefaultTask() {
    @get:Input
    abstract val utilityPackage: Property<String>

    @get:Input
    abstract val utilityClassName: Property<String>

    @get:Input
    abstract val dependencies: ListProperty<DependencyInfo>

    @get:Input
    abstract val repositories: MapProperty<String, RepositoryInfo>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val packageName = utilityPackage.get()
        val className = utilityClassName.get()
        val deps = dependencies.get()
        val repos = repositories.get()

        val utilityClass = generateUtilityClass(packageName, className, deps, repos)

        val packagePath = packageName.replace('.', '/')
        val outputFile = outputDir.get().asFile.resolve("$packagePath/$className.java")
        outputFile.parentFile.mkdirs()
        outputFile.writeText(utilityClass)

        println("Generated Velocity utility class: ${outputFile.absolutePath}")
        println("  - Dependencies: ${deps.size}")
        println("  - Repositories: ${repos.size}")
    }

    private fun generateUtilityClass(
        packageName: String,
        className: String,
        deps: List<DependencyInfo>,
        repos: Map<String, RepositoryInfo>
    ): String {
        // This code is heavily inspired by BootstrapMain.java
        // It reads runtime-dependencies.txt and uses Maven Resolver to download dependencies
        
        return """
package $packageName;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.slf4j.Logger;

import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Auto-generated utility class for loading runtime dependencies in Velocity plugins.
 * 
 * This class uses Maven Resolver to download dependencies at runtime from Maven repositories,
 * similar to how Paper's PluginLoader works.
 * 
 * Usage:
 * <pre>
 * @Plugin(id = "myplugin")
 * public class MyVelocityPlugin {
 *     @Inject
 *     public MyVelocityPlugin(ProxyServer server, Logger logger) {
 *         $className.initialize(logger);
 *         // Now runtime dependencies are available
 *     }
 * }
 * </pre>
 */
public class $className {
    private static final String CACHE_DIR_PROPERTY = "runtime.dependency.cache.dir";
    private static final String DEFAULT_CACHE_DIR = ".runtime-dependencies";
    private static final String MANIFEST_PATH = "/META-INF/runtime-dependencies.txt";
    
    private static boolean initialized = false;
    private static URLClassLoader runtimeClassLoader;

    /**
     * Initializes runtime dependencies by downloading them from Maven repositories
     * and loading them into an isolated classloader.
     *
     * @param logger The Velocity logger for status messages
     */
    public static synchronized void initialize(Logger logger) {
        if (initialized) {
            logger.info("[RuntimeDependency] Already initialized");
            return;
        }

        logger.info("[RuntimeDependency] Initializing runtime dependencies...");

        try {
            // Read manifest data
            ManifestData manifestData = readManifest(logger);

            if (manifestData.dependencies.isEmpty()) {
                logger.info("[RuntimeDependency] No dependencies to resolve");
                initialized = true;
                return;
            }

            logger.info("[RuntimeDependency] Found {} dependencies", manifestData.dependencies.size());
            logger.info("[RuntimeDependency] Found {} repositories", manifestData.repositories.size());

            // Setup Maven Resolver
            RepositorySystem repositorySystem = newRepositorySystem(logger);
            DefaultRepositorySystemSession session = newSession(repositorySystem, logger);

            // Resolve dependencies
            List<RemoteRepository> repositories = buildRepositories(manifestData.repositories, logger);
            List<URL> urls = resolveDependencies(repositorySystem, session, repositories, manifestData.dependencies, logger);

            logger.info("[RuntimeDependency] Resolved {} JARs", urls.size());

            // Create classloader with resolved dependencies
            ClassLoader parentClassLoader = $className.class.getClassLoader();
            runtimeClassLoader = new URLClassLoader(
                urls.toArray(new URL[0]),
                parentClassLoader
            );

            // Set thread context classloader
            Thread.currentThread().setContextClassLoader(runtimeClassLoader);

            logger.info("[RuntimeDependency] Successfully initialized");
            initialized = true;

        } catch (Exception e) {
            logger.error("[RuntimeDependency] Failed to initialize runtime dependencies", e);
            throw new RuntimeException("Failed to load runtime dependencies", e);
        }
    }

    private static ManifestData readManifest(Logger logger) {
        ManifestData data = new ManifestData();

        try (InputStream is = $className.class.getResourceAsStream(MANIFEST_PATH)) {
            if (is == null) {
                logger.warn("[RuntimeDependency] No runtime-dependencies.txt found");
                return data;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }

                    if (line.startsWith("DEP:")) {
                        // Format: DEP:groupId:artifactId:version
                        String[] parts = line.substring(4).split(":");
                        if (parts.length == 3) {
                            data.dependencies.add(new DependencyCoordinate(parts[0], parts[1], parts[2]));
                        }
                    } else if (line.startsWith("REPO:")) {
                        // Format: REPO:name:url:needsAuth:envPrefix
                        // Parse manually because URL contains ':'
                        String content = line.substring(5);
                        
                        int firstColon = content.indexOf(":");
                        if (firstColon == -1) continue;

                        String name = content.substring(0, firstColon);
                        
                        int lastColon = content.lastIndexOf(":");
                        int secondLastColon = content.lastIndexOf(":", lastColon - 1);

                        if (secondLastColon <= firstColon) {
                            String url = content.substring(firstColon + 1);
                            data.repositories.add(new RepositoryInfo(name, url, false, ""));
                        } else {
                            String url = content.substring(firstColon + 1, secondLastColon);
                            String needsAuthStr = content.substring(secondLastColon + 1, lastColon);
                            String envPrefix = content.substring(lastColon + 1);
                            boolean needsAuth = Boolean.parseBoolean(needsAuthStr);
                            data.repositories.add(new RepositoryInfo(name, url, needsAuth, envPrefix));
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("[RuntimeDependency] ERROR reading manifest: {}", e.getMessage());
        }

        return data;
    }

    private static RepositorySystem newRepositorySystem(Logger logger) {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
        locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
            @Override
            public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
                logger.error("[RuntimeDependency] Service creation failed for {}: {}", 
                    type.getName(), exception.getMessage());
            }
        });
        return locator.getService(RepositorySystem.class);
    }

    private static DefaultRepositorySystemSession newSession(RepositorySystem system, Logger logger) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

        String cacheDir = System.getProperty(CACHE_DIR_PROPERTY);
        if (cacheDir == null || cacheDir.isEmpty()) {
            Path userHome = Paths.get(System.getProperty("user.home"));
            cacheDir = userHome.resolve(DEFAULT_CACHE_DIR).toString();
        }

        LocalRepository localRepo = new LocalRepository(cacheDir);
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));

        logger.info("[RuntimeDependency] Using cache directory: {}", cacheDir);

        return session;
    }

    private static List<RemoteRepository> buildRepositories(List<RepositoryInfo> repoInfos, Logger logger) {
        List<RemoteRepository> repositories = new ArrayList<>();

        for (RepositoryInfo info : repoInfos) {
            logger.info("[RuntimeDependency] Building repository: {}", info.name);
            logger.info("[RuntimeDependency]   URL: {}", info.url);

            RemoteRepository.Builder builder = new RemoteRepository.Builder(info.name, "default", info.url);

            if (info.needsAuth && !info.envPrefix.isEmpty()) {
                String username = System.getenv(info.envPrefix + "_USERNAME");
                String password = System.getenv(info.envPrefix + "_PASSWORD");

                if (username != null && password != null) {
                    builder.setAuthentication(new AuthenticationBuilder()
                            .addUsername(username)
                            .addPassword(password)
                            .build());
                    logger.info("[RuntimeDependency] Authentication enabled for repository: {}", info.name);
                } else {
                    logger.info("[RuntimeDependency] No credentials found for repository: {}", info.name);
                }
            }

            repositories.add(builder.build());
        }

        return repositories;
    }

    private static List<URL> resolveDependencies(
            RepositorySystem system,
            DefaultRepositorySystemSession session,
            List<RemoteRepository> repositories,
            List<DependencyCoordinate> dependencies,
            Logger logger
    ) throws Exception {
        List<URL> urls = new ArrayList<>();

        for (DependencyCoordinate coord : dependencies) {
            logger.info("[RuntimeDependency] Resolving: {}:{}:{}", 
                coord.groupId, coord.artifactId, coord.version);

            Artifact artifact = new DefaultArtifact(coord.groupId + ":" + coord.artifactId + ":" + coord.version);
            Dependency dependency = new Dependency(artifact, "runtime");

            CollectRequest collectRequest = new CollectRequest();
            collectRequest.setRoot(dependency);
            collectRequest.setRepositories(repositories);

            DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, null);

            try {
                DependencyResult result = system.resolveDependencies(session, dependencyRequest);

                for (ArtifactResult ar : result.getArtifactResults()) {
                    java.io.File file = ar.getArtifact().getFile();
                    if (file != null && file.exists()) {
                        urls.add(file.toURI().toURL());
                        logger.info("[RuntimeDependency]   -> {}", file.getName());
                    }
                }
            } catch (Exception e) {
                logger.error("[RuntimeDependency] ERROR resolving dependency: {}", e.getMessage());
                throw e;
            }
        }

        return urls;
    }

    /**
     * Returns the runtime classloader containing the loaded dependencies.
     *
     * @return The runtime classloader, or null if not initialized
     */
    public static ClassLoader getRuntimeClassLoader() {
        return runtimeClassLoader;
    }

    /**
     * Checks if runtime dependencies have been initialized.
     *
     * @return true if initialized, false otherwise
     */
    public static boolean isInitialized() {
        return initialized;
    }

    // Data classes
    private static class ManifestData {
        List<DependencyCoordinate> dependencies = new ArrayList<>();
        List<RepositoryInfo> repositories = new ArrayList<>();
    }

    private static class DependencyCoordinate {
        String groupId;
        String artifactId;
        String version;

        DependencyCoordinate(String groupId, String artifactId, String version) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
        }
    }

    private static class RepositoryInfo {
        String name;
        String url;
        boolean needsAuth;
        String envPrefix;

        RepositoryInfo(String name, String url, boolean needsAuth, String envPrefix) {
            this.name = name;
            this.url = url;
            this.needsAuth = needsAuth;
            this.envPrefix = envPrefix;
        }
    }
}
""".trimIndent()
    }
}
