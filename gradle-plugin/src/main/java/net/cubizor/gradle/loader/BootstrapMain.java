package net.cubizor.gradle.loader;

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

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Bootstrap main class that resolves and loads runtime dependencies from Maven repositories.
 * <p>
 * This class is injected into standalone JARs as the entry point.
 * It reads dependency coordinates from META-INF/runtime-dependencies.txt,
 * resolves them from Maven repositories at runtime,
 * creates a custom classloader with all dependencies,
 * then loads and runs the actual main class through this classloader.
 * <p>
 * The JAR manifest should have:
 * - Main-Class: net.cubizor.gradle.loader.BootstrapMain
 * - Original-Main-Class: com.example.YourActualMainClass
 */
public final class BootstrapMain {

    private static final String CACHE_DIR_PROPERTY = "runtime.dependency.cache.dir";
    private static final String DEFAULT_CACHE_DIR = ".runtime-dependencies";

    public static void main(String[] args) throws Exception {
        System.out.println("[RuntimeDependency] Starting Bootstrap ClassLoader");

        // Get original main class from manifest
        String originalMainClass = getOriginalMainClass();
        if (originalMainClass == null || originalMainClass.isEmpty()) {
            System.err.println("[RuntimeDependency] ERROR: No Original-Main-Class specified in manifest!");
            System.exit(1);
            return;
        }

        System.out.println("[RuntimeDependency] Original Main-Class: " + originalMainClass);

        // Read dependencies and repositories from manifest
        ManifestData manifestData = readManifest();

        if (manifestData.dependencies.isEmpty()) {
            System.out.println("[RuntimeDependency] No dependencies to resolve");
            runMainClass(originalMainClass, args, new URL[0]);
            return;
        }

        System.out.println("[RuntimeDependency] Found " + manifestData.dependencies.size() + " dependencies");
        System.out.println("[RuntimeDependency] Found " + manifestData.repositories.size() + " repositories");

        // Setup Maven Resolver
        RepositorySystem repositorySystem = newRepositorySystem();
        DefaultRepositorySystemSession session = newSession(repositorySystem);

        // Resolve dependencies
        List<RemoteRepository> repositories = buildRepositories(manifestData.repositories);
        List<URL> urls = resolveDependencies(repositorySystem, session, repositories, manifestData.dependencies);

        System.out.println("[RuntimeDependency] Resolved " + urls.size() + " JARs");

        // Run original main class with dependencies
        runMainClass(originalMainClass, args, urls.toArray(new URL[0]));
    }

    private static String getOriginalMainClass() {
        try {
            java.util.jar.Manifest manifest = getManifest();
            if (manifest != null) {
                return manifest.getMainAttributes().getValue("Original-Main-Class");
            }
        } catch (Exception e) {
            System.err.println("[RuntimeDependency] ERROR reading manifest: " + e.getMessage());
        }
        return null;
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

    private static ManifestData readManifest() {
        ManifestData data = new ManifestData();
        String resourcePath = "/META-INF/runtime-dependencies.txt";

        try (InputStream is = BootstrapMain.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                System.out.println("[RuntimeDependency] No runtime-dependencies.txt found");
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
                        String content = line.substring(5); // Remove "REPO:"

                        int firstColon = content.indexOf(":");
                        if (firstColon == -1) continue;

                        String name = content.substring(0, firstColon);

                        // Find last two colons for needsAuth and envPrefix
                        int lastColon = content.lastIndexOf(":");
                        int secondLastColon = content.lastIndexOf(":", lastColon - 1);

                        if (secondLastColon <= firstColon) {
                            // Fallback: assume no auth fields
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
            System.err.println("[RuntimeDependency] ERROR reading manifest: " + e.getMessage());
            e.printStackTrace();
        }

        return data;
    }

    private static RepositorySystem newRepositorySystem() {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
        locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
            @Override
            public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
                System.err.println("[RuntimeDependency] Service creation failed for " + type.getName() + ": " + exception.getMessage());
            }
        });
        return locator.getService(RepositorySystem.class);
    }

    private static DefaultRepositorySystemSession newSession(RepositorySystem system) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

        // Setup local repository (cache)
        String cacheDir = System.getProperty(CACHE_DIR_PROPERTY);
        if (cacheDir == null || cacheDir.isEmpty()) {
            Path userHome = Paths.get(System.getProperty("user.home"));
            cacheDir = userHome.resolve(DEFAULT_CACHE_DIR).toString();
        }

        LocalRepository localRepo = new LocalRepository(cacheDir);
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));

        System.out.println("[RuntimeDependency] Using cache directory: " + cacheDir);

        return session;
    }

    private static List<RemoteRepository> buildRepositories(List<RepositoryInfo> repoInfos) {
        List<RemoteRepository> repositories = new ArrayList<>();

        for (RepositoryInfo info : repoInfos) {
            System.out.println("[RuntimeDependency] Building repository: " + info.name);
            System.out.println("[RuntimeDependency]   URL: " + info.url);

            RemoteRepository.Builder builder = new RemoteRepository.Builder(info.name, "default", info.url);

            if (info.needsAuth && !info.envPrefix.isEmpty()) {
                String username = System.getenv(info.envPrefix + "_USERNAME");
                String password = System.getenv(info.envPrefix + "_PASSWORD");

                if (username != null && password != null) {
                    builder.setAuthentication(new AuthenticationBuilder()
                            .addUsername(username)
                            .addPassword(password)
                            .build());
                    System.out.println("[RuntimeDependency] Authentication enabled for repository: " + info.name);
                } else {
                    System.out.println("[RuntimeDependency] No credentials found for repository: " + info.name);
                }
            }

            RemoteRepository repo = builder.build();
            System.out.println("[RuntimeDependency]   Protocol: " + repo.getProtocol());
            System.out.println("[RuntimeDependency]   Content Type: " + repo.getContentType());
            repositories.add(repo);
        }

        return repositories;
    }

    private static List<URL> resolveDependencies(
            RepositorySystem system,
            DefaultRepositorySystemSession session,
            List<RemoteRepository> repositories,
            List<DependencyCoordinate> dependencies
    ) throws Exception {
        List<URL> urls = new ArrayList<>();

        for (DependencyCoordinate coord : dependencies) {
            System.out.println("[RuntimeDependency] Resolving: " + coord.groupId + ":" + coord.artifactId + ":" + coord.version);

            Artifact artifact = new DefaultArtifact(coord.groupId + ":" + coord.artifactId + ":" + coord.version);
            Dependency dependency = new Dependency(artifact, "runtime");

            CollectRequest collectRequest = new CollectRequest();
            collectRequest.setRoot(dependency);
            collectRequest.setRepositories(repositories);

            DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, null);

            try {
                DependencyResult result = system.resolveDependencies(session, dependencyRequest);

                for (ArtifactResult ar : result.getArtifactResults()) {
                    File file = ar.getArtifact().getFile();
                    if (file != null && file.exists()) {
                        urls.add(file.toURI().toURL());
                        System.out.println("[RuntimeDependency]   -> " + file.getName());
                    }
                }
            } catch (Exception e) {
                System.err.println("[RuntimeDependency] ERROR resolving dependency: " + e.getMessage());
                throw e;
            }
        }

        return urls;
    }

    private static void runMainClass(String mainClassName, String[] args, URL[] urls) throws Exception {
        // Add application JAR to URLs
        URL jarLocation = BootstrapMain.class.getProtectionDomain().getCodeSource().getLocation();
        URL[] allUrls = new URL[urls.length + 1];
        allUrls[0] = jarLocation;
        System.arraycopy(urls, 0, allUrls, 1, urls.length);

        // Create custom classloader
        RuntimeClassLoader loader = new RuntimeClassLoader(allUrls, BootstrapMain.class.getClassLoader());
        Thread.currentThread().setContextClassLoader(loader);

        System.out.println("[RuntimeDependency] Loading main class: " + mainClassName);

        try {
            Class<?> mainClass = loader.loadClass(mainClassName);
            Method mainMethod = mainClass.getMethod("main", String[].class);
            mainMethod.invoke(null, (Object) args);
        } catch (ClassNotFoundException e) {
            System.err.println("[RuntimeDependency] ERROR: Main class not found: " + mainClassName);
            e.printStackTrace();
            System.exit(1);
        } catch (NoSuchMethodException e) {
            System.err.println("[RuntimeDependency] ERROR: Main method not found in: " + mainClassName);
            e.printStackTrace();
            System.exit(1);
        } catch (Exception e) {
            System.err.println("[RuntimeDependency] ERROR: Failed to run main class");
            e.printStackTrace();
            System.exit(1);
        }
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
                        name.startsWith("jdk.") ||
                        name.startsWith("org.xml.") ||
                        name.startsWith("org.w3c.")) {
                    return super.loadClass(name, resolve);
                }

                // Bootstrap class stays in parent
                if (name.equals(BootstrapMain.class.getName()) ||
                        name.equals(RuntimeClassLoader.class.getName()) ||
                        name.startsWith("net.cubizor.gradle.loader.") ||
                        name.startsWith("org.eclipse.aether.") ||
                        name.startsWith("org.apache.maven.")) {
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
