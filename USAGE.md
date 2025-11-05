# Usage Guide

RuntimeDependency plugin supports three modes:
- **Paper Mode** - For Minecraft Paper plugins
- **Standalone Mode** - For standalone Java applications
- **Basic Mode** - Just download dependencies (default)

---

## Standalone Mode

For standalone Java/Kotlin applications that need runtime dependency loading.

### Quick Start

```kotlin
plugins {
    id("net.cubizor.runtime-dependency")
}

runtimeDependency {
    standalone {
        enabled.set(true)
        mainClass.set("com.example.MyApp")  // Your actual main class
    }
}

dependencies {
    runtimeDownload("com.google.code.gson:gson:2.10.1")
    runtimeDownload("org.apache.commons:commons-lang3:3.14.0")
}
```

### Build and Run

```bash
# Build your application
./gradlew build

# Run with automatic dependency loading
java -jar build/libs/yourapp.jar
```

The plugin automatically:
1. Downloads dependencies to `build/runtime-dependencies/`
2. Configures JAR manifest with bootstrap launcher
3. Loads dependencies before your main class runs
4. No manual classpath configuration needed

### How It Works

```
┌─────────────────────────────────────────┐
│  java -jar yourapp.jar                  │
└───────────────┬─────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────┐
│  LauncherBootstrap (from plugin)        │
│  1. Scans runtime-dependencies/         │
│  2. Loads all JARs into classpath       │
│  3. Invokes your main class             │
└───────────────┬─────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────┐
│  com.example.MyApp.main()               │
│  Your application starts with all       │
│  dependencies available!                │
└─────────────────────────────────────────┘
```

### Configuration Options

```kotlin
runtimeDependency {
    standalone {
        enabled.set(true)
        mainClass.set("com.example.Main")              // Required: Your main class
        libraryPath.set("runtime-dependencies")        // Optional: Library directory
        includeBootstrapInJar.set(true)               // Optional: Include launcher
    }
}
```

### Advanced: Custom Library Path

```kotlin
runtimeDependency {
    standalone {
        enabled.set(true)
        mainClass.set("com.example.Main")
        libraryPath.set("libs")  // Custom directory
    }
    outputDirectory.set("libs")  // Must match libraryPath
}
```

Run with custom path:
```bash
java -Druntime.dependency.library.path=/custom/path -jar yourapp.jar
```

### Distribution

When distributing your app:
```
yourapp/
  ├── yourapp.jar                    # Your application
  └── runtime-dependencies/          # Copy this folder too!
      ├── gson/
      │   └── gson-2.10.1.jar
      └── commons-lang3/
          └── commons-lang3-3.14.0.jar
```

Users just run: `java -jar yourapp.jar`

---

## Basic Mode (Default)

Simple dependency download without automatic loading.

### 1. Apply Plugin

```kotlin
plugins {
    id("net.cubizor.runtime-dependency")
}
```

### 2. Add Dependencies

```kotlin
dependencies {
    runtimeDownload("com.google.code.gson:gson:2.10.1")
    runtimeDownload("org.apache.commons:commons-lang3:3.14.0")
}
```

Dependencies are available at both compile-time and runtime.

### 3. Build

```bash
./gradlew build
```

Dependencies will be downloaded to `build/runtime-dependencies/`:

```
build/runtime-dependencies/
  gson/
    gson-2.10.1.jar
  commons-lang3/
    commons-lang3-3.14.0.jar
```

---

## Configuration (All Modes)

### Custom Output Directory

```kotlin
runtimeDependency {
    outputDirectory.set("custom-libs")
}
```

### Disable Organization

```kotlin
runtimeDependency {
    organizeByGroup.set(false)
}
```

Result:
```
build/runtime-dependencies/
  gson-2.10.1.jar
  commons-lang3-3.14.0.jar
```

---

## Private Repository Support

Works with **all modes** (Basic, Standalone, Paper).

### 1. Define Repository

```kotlin
repositories {
    maven {
        name = "myRepo"  // This name is important!
        url = uri("https://private.company.com/maven")
    }
}

dependencies {
    runtimeDownload("com.company:secret-lib:1.0.0")
}
```

### 2. Build Time Credentials (gradle.properties)

For the plugin to access the repository during build:

```properties
# ~/.gradle/gradle.properties or project's gradle.properties
myRepo.username=your_username
myRepo.password=your_password
```

### 3. Runtime Credentials (Paper Mode Only)

For **Paper mode**, server needs credentials to download dependencies at runtime using **environment variables**:

```bash
# Repository name "myRepo" -> Environment variable "MYREPO"
export MYREPO_USERNAME=your_username
export MYREPO_PASSWORD=your_password

# Start the server
java -jar paper.jar
```

**Note:** Standalone mode downloads dependencies at **build time**, so runtime credentials are not needed.

**Naming Convention:** Convert repository name to uppercase, replace special characters with `_`:
- `myRepo` → `MYREPO_USERNAME`, `MYREPO_PASSWORD`
- `nexus-repo` → `NEXUS_REPO_USERNAME`, `NEXUS_REPO_PASSWORD`
- `company.internal` → `COMPANY_INTERNAL_USERNAME`, `COMPANY_INTERNAL_PASSWORD`

### Alternative: System Properties (Paper Mode)

Instead of environment variables:

```bash
java -DmyRepo.username=user -DmyRepo.password=pass -jar paper.jar
```

**Warning:** System properties are visible in `ps` output. Use environment variables in production!

### Multiple Private Repositories Example

```kotlin
repositories {
    maven {
        name = "nexus"
        url = uri("https://nexus.company.com/repository/maven-releases/")
    }
    maven {
        name = "artifactory"
        url = uri("https://artifactory.company.com/libs-release/")
    }
}
```

Runtime:
```bash
export NEXUS_USERNAME=admin
export NEXUS_PASSWORD=secret123
export ARTIFACTORY_USERNAME=deploy
export ARTIFACTORY_PASSWORD=xyz789

java -jar paper.jar
```

---

## Paper Plugin Mode

For Minecraft Paper plugin developers, auto-generates PluginLoader.

**Note:** When Paper mode is enabled, dependencies are loaded by Paper at runtime. The `downloadRuntimeDependencies` task is disabled.

### 1. Enable Paper Extension

```kotlin
runtimeDependency {
    paper {
        enabled.set(true)
        loaderPackage.set("com.example.plugin.loader")  // optional
        loaderClassName.set("DependencyLoader")         // optional
    }
}
```

### 2. Add Paper API

```kotlin
repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")

    runtimeDownload("com.google.code.gson:gson:2.10.1")
}
```

### 3. Build

```bash
./gradlew build
```

The plugin automatically:
- Generates PluginLoader class
- Updates paper-plugin.yml or plugin.yml with `loader:` entry
- Adds all runtimeDownload dependencies and repositories
- Paper loads dependencies at runtime (no local download needed)

### Generated PluginLoader (Public Dependencies)

```java
package com.example.plugin.loader;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.jetbrains.annotations.NotNull;

public class DependencyLoader implements PluginLoader {
    @Override
    public void classloader(@NotNull PluginClasspathBuilder classpathBuilder) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();

        resolver.addDependency(new Dependency(new DefaultArtifact("com.google.code.gson:gson:2.10.1"), null));

        // Maven Central is provided by Paper, so no repository needed

        classpathBuilder.addLibrary(resolver);
    }
}
```

### Generated PluginLoader (With Private Repository)

If your project uses a private repository (e.g., Nexus):

```java
package com.example.plugin.loader;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.jetbrains.annotations.NotNull;

public class DependencyLoader implements PluginLoader {
    @Override
    public void classloader(@NotNull PluginClasspathBuilder classpathBuilder) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();

        resolver.addDependency(new Dependency(new DefaultArtifact("com.company:private-lib:1.0.0"), null));

        // Private repository with authentication
        RemoteRepository.Builder nexusBuilder = new RemoteRepository.Builder("nexus", "default", "https://nexus.company.com/repository/maven-public");

        // Try environment variable first, fallback to system property
        String nexusUser = System.getenv("NEXUS_USERNAME");
        if (nexusUser == null) {
            nexusUser = System.getProperty("nexus.username");
        }
        String nexusPass = System.getenv("NEXUS_PASSWORD");
        if (nexusPass == null) {
            nexusPass = System.getProperty("nexus.password");
        }

        // Add authentication if credentials are available
        if (nexusUser != null && nexusPass != null) {
            nexusBuilder.setAuthentication(new AuthenticationBuilder()
                .addUsername(nexusUser)
                .addPassword(nexusPass)
                .build());
        }

        resolver.addRepository(nexusBuilder.build());

        classpathBuilder.addLibrary(resolver);
    }
}
```

**Runtime credentials:**
```bash
# Repository name "nexus" becomes environment variable "NEXUS"
export NEXUS_USERNAME=your_username
export NEXUS_PASSWORD=your_password

java -jar paper.jar
```

### Updated paper-plugin.yml

```yaml
loader: com.example.plugin.loader.DependencyLoader
name: MyPlugin
version: 1.0.0
main: com.example.plugin.MyPlugin
api-version: "1.20"
```

## Manual Task Execution

### Download Dependencies Only

```bash
./gradlew downloadRuntimeDependencies
```

### Generate Paper Loader Only

```bash
./gradlew generatePaperLoader
```

## Multi-Module Projects

Each module can have its own configuration:

```kotlin
// app/build.gradle.kts
plugins {
    id("net.cubizor.runtime-dependency")
}

runtimeDependency {
    outputDirectory.set("app-libs")
}

dependencies {
    runtimeDownload("com.google.code.gson:gson:2.10.1")
}
```

```kotlin
// server/build.gradle.kts
plugins {
    id("net.cubizor.runtime-dependency")
}

runtimeDependency {
    outputDirectory.set("server-libs")
}

dependencies {
    runtimeDownload("io.ktor:ktor-server-core:2.3.7")
}
```

---

## Transitive Dependencies

The plugin automatically handles transitive dependencies:

```kotlin
dependencies {
    runtimeDownload("org.springframework.boot:spring-boot-starter-web:3.2.1")
}
```

Downloads Spring Boot and all its dependencies.

---

## Mode Comparison

| Feature | Basic Mode | Standalone Mode | Paper Mode |
|---------|-----------|-----------------|------------|
| **Use Case** | Manual loading | Standalone apps | Paper plugins |
| **Auto Loading** | ❌ No | ✅ Yes | ✅ Yes |
| **Requires Paper** | ❌ | ❌ | ✅ |
| **Build Time Download** | ✅ | ✅ | ❌ |
| **Runtime Download** | ❌ | ❌ | ✅ |
| **Manifest Config** | ❌ | ✅ Auto | ❌ |
| **Distribution** | Manual | Automatic | Paper handles |

### Which Mode to Choose?

- **Building a Paper plugin?** Use Paper Mode
- **Building a standalone app?** Use Standalone Mode
- **Need manual control?** Use Basic Mode (default)

---

## Troubleshooting

### Standalone Mode Issues

**Problem:** `ClassNotFoundException` at runtime

**Solution:** Ensure `runtime-dependencies/` folder is copied with your JAR.

**Problem:** Custom main class not found

**Solution:** Verify `mainClass` in build.gradle.kts:
```kotlin
runtimeDependency {
    standalone {
        mainClass.set("com.example.Main")  // Fully qualified name
    }
}
```

**Problem:** Dependencies not loaded

**Solution:** Check library path:
```bash
java -Druntime.dependency.library.path=./runtime-dependencies -jar yourapp.jar
```

### Paper Mode Issues

**Problem:** PluginLoader not found

**Solution:** Ensure `paper-plugin.yml` exists and plugin is built after configuration.

### General Issues

**Problem:** Private repository authentication fails

**Solution:** Check credentials in `gradle.properties` and environment variables.

