# Usage Guide

RuntimeDependency plugin supports two modes:
- **Paper Mode** - For Minecraft Paper plugins
- **Basic Mode** - Just download dependencies (default)

---

## Paper Mode

For Paper plugin developers, auto-generates PluginLoader.

### Quick Start

```kotlin
plugins {
    id("net.cubizor.runtime-dependency")
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

runtimeDependency {
    paper {
        enabled.set(true)
        loaderPackage.set("com.example.plugin.loader")  // optional
        loaderClassName.set("DependencyLoader")         // optional
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")

    runtimeDownload("com.google.code.gson:gson:2.10.1")
}
```

### Build

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

        classpathBuilder.addLibrary(resolver);
    }
}
```

---

## Basic Mode (Default)

Simple dependency download without automatic loading.

### Usage

```kotlin
plugins {
    id("net.cubizor.runtime-dependency")
}

dependencies {
    runtimeDownload("com.google.code.gson:gson:2.10.1")
    runtimeDownload("org.apache.commons:commons-lang3:3.14.0")
}
```

Dependencies are available at both compile-time and runtime.

### Build

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

Works with **Paper mode** and **Basic mode**.

### Define Repository

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

### Build Time Credentials (gradle.properties)

For the plugin to access the repository during build:

```properties
# ~/.gradle/gradle.properties or project's gradle.properties
myRepo.username=your_username
myRepo.password=your_password
```

### Runtime Credentials (Paper Mode Only)

For **Paper mode**, server needs credentials to download dependencies at runtime using **environment variables**:

```bash
# Repository name "myRepo" -> Environment variable "MYREPO"
export MYREPO_USERNAME=your_username
export MYREPO_PASSWORD=your_password

# Start the server
java -jar paper.jar
```

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

---

## License

MIT License
