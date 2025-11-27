# Usage Guide

RuntimeDependency plugin supports three modes:
- **Standalone Mode** - For standalone Java/Kotlin applications
- **Paper Mode** - For Minecraft Paper plugins
- **Basic Mode** - Just download dependencies (default)

---

## Standalone Mode

For standalone Java/Kotlin applications that need runtime dependency loading without embedding dependencies in the JAR.

### Quick Start

```kotlin
plugins {
    id("net.cubizor.runtime-dependency")
}

runtimeDependency {
    standalone {
        enabled.set(true)
        mainClass.set("com.example.MyApp")  // Your actual main class
        libraryPath.set("libs")              // Optional, default: "libs"
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

# Dependencies are downloaded to build/runtime-dependencies/
# Copy them to your library path
mkdir -p libs
cp -r build/runtime-dependencies/* libs/

# Run with automatic dependency loading
java -jar build/libs/yourapp.jar
```

### How It Works

The plugin uses a **Bootstrap ClassLoader** approach that is fully compatible with Java 21+:

```
┌─────────────────────────────────────────────────────────────┐
│  java -jar yourapp.jar                                      │
└─────────────────────────┬───────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│  BootstrapMain (injected by plugin)                         │
│                                                             │
│  1. Reads Original-Main-Class from MANIFEST.MF              │
│  2. Reads Library-Path from MANIFEST.MF                     │
│  3. Reads META-INF/runtime-dependencies.txt                 │
│  4. Creates RuntimeClassLoader (custom URLClassLoader)      │
│  5. Loads all dependency JARs into RuntimeClassLoader       │
│  6. Loads your main class through RuntimeClassLoader        │
│  7. Invokes your main() method via reflection               │
└─────────────────────────┬───────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│  com.example.MyApp.main(args)                               │
│                                                             │
│  ✅ All dependencies available!                             │
│  ✅ No ClassNotFoundException!                               │
│  ✅ Works on Java 21+!                                       │
└─────────────────────────────────────────────────────────────┘
```

### JAR Structure

After build, your JAR contains:

```
yourapp.jar
├── META-INF/
│   ├── MANIFEST.MF                    # Main-Class: BootstrapMain
│   └── runtime-dependencies.txt       # List of required JARs
├── net/cubizor/gradle/loader/
│   ├── BootstrapMain.class            # Entry point (from plugin)
│   └── BootstrapMain$RuntimeClassLoader.class
└── com/example/
    └── MyApp.class                    # Your application
```

### Manifest Attributes

```
Manifest-Version: 1.0
Main-Class: net.cubizor.gradle.loader.BootstrapMain
Original-Main-Class: com.example.MyApp
Library-Path: libs
```

### Configuration Options

```kotlin
runtimeDependency {
    standalone {
        enabled.set(true)
        mainClass.set("com.example.Main")   // Required: Your main class
        libraryPath.set("libs")              // Optional: Library directory (default: "libs")
    }
    
    // Optional: Change where dependencies are downloaded during build
    outputDirectory.set("runtime-dependencies")  // default
    
    // Optional: Organize by artifact name
    organizeByGroup.set(true)  // default: true
}
```

### Distribution

When distributing your application:

```
myapp/
├── myapp.jar                          # Your application JAR
└── libs/                              # Library path (must match libraryPath setting)
    ├── gson/
    │   └── gson-2.10.1.jar
    └── commons-lang3/
        └── commons-lang3-3.14.0.jar
```

Users simply run:
```bash
java -jar myapp.jar
```

### Custom Library Path at Runtime

You can override the library path at runtime:

```bash
# Using system property
java -Druntime.dependency.library.path=/custom/libs -jar myapp.jar

# Or set working directory
cd /path/to/myapp
java -jar myapp.jar  # Uses ./libs relative to working directory
```

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
| **Runtime Download** | ❌ | ❌ | ✅ (by Paper) |
| **JAR Modification** | ❌ | ✅ Auto | ❌ |
| **Java 21+ Compatible** | ✅ | ✅ | ✅ |

### Which Mode to Choose?

- **Building a Paper plugin?** → Use Paper Mode
- **Building a standalone Java/Kotlin app?** → Use Standalone Mode  
- **Need manual control over loading?** → Use Basic Mode (default)

---

## Troubleshooting

### Standalone Mode Issues

**Problem:** `ClassNotFoundException` at runtime

**Solutions:**
1. Ensure the `libs/` folder (or your custom `libraryPath`) exists next to the JAR
2. Copy dependencies: `cp -r build/runtime-dependencies/* libs/`
3. Check that JAR names match in `META-INF/runtime-dependencies.txt`

**Problem:** Main class not found

**Solution:** Verify `mainClass` in build.gradle.kts uses fully qualified name:
```kotlin
runtimeDependency {
    standalone {
        mainClass.set("com.example.Main")  // NOT just "Main"
    }
}
```

**Problem:** Dependencies not loading on Java 21+

**Solution:** This shouldn't happen with the Bootstrap approach. Check:
```bash
# Verify manifest
unzip -p yourapp.jar META-INF/MANIFEST.MF

# Should show:
# Main-Class: net.cubizor.gradle.loader.BootstrapMain
# Original-Main-Class: com.example.Main
```

**Problem:** Library path not found

**Solution:** Override at runtime:
```bash
java -Druntime.dependency.library.path=./my-libs -jar yourapp.jar
```

### Paper Mode Issues

**Problem:** PluginLoader not found

**Solution:** Ensure `paper-plugin.yml` exists and rebuild:
```bash
./gradlew clean build
```

**Problem:** Private repository authentication fails at runtime

**Solution:** Set environment variables:
```bash
export MYREPO_USERNAME=your_username
export MYREPO_PASSWORD=your_password
java -jar paper.jar
```

### General Issues

**Problem:** Dependencies not downloading

**Solution:** Check network and repository access:
```bash
./gradlew downloadRuntimeDependencies --info
```

**Problem:** Build fails with "Both modes enabled"

**Solution:** Only enable one mode:
```kotlin
runtimeDependency {
    // Choose ONE:
    standalone { enabled.set(true) }
    // OR
    paper { enabled.set(true) }
}
```

