# Usage Guide

## Basic Usage

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

## Configuration

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

## Private Repository

### Using gradle.properties

```properties
myRepo.username=your_username
myRepo.password=your_password
```

### Using Environment Variables

```bash
export MYREPO_USERNAME=your_username
export MYREPO_PASSWORD=your_password
```

### Repository Configuration

```kotlin
repositories {
    maven {
        name = "myRepo"
        url = uri("https://private.company.com/maven")
    }
}
```

Credentials are automatically detected and applied.

## Paper Plugin Integration

For Paper plugin developers, the plugin can auto-generate PluginLoader.

**Note:** When Paper integration is enabled, dependencies are loaded by Paper at runtime. The `downloadRuntimeDependencies` task is disabled.

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

### Generated PluginLoader

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

        resolver.addRepository(new RemoteRepository.Builder("central", "default", "https://repo1.maven.org/maven2/").build());

        classpathBuilder.addLibrary(resolver);
    }
}
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

## Transitive Dependencies

The plugin automatically handles transitive dependencies:

```kotlin
dependencies {
    runtimeDownload("org.springframework.boot:spring-boot-starter-web:3.2.1")
}
```

Downloads Spring Boot and all its dependencies.
