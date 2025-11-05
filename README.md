# Runtime Dependency Plugin

A Gradle plugin for runtime dependency management with support for standalone applications, Paper plugins, and manual dependency control.

## Features

- Automatic runtime dependency loading for standalone applications
- Zero-config dependency management
- Paper plugin integration with auto-generated PluginLoader
- Private repository support
- Automatic dependency organization by artifact
- Configuration cache compatible
- Works with Java and Kotlin projects

## Standalone Mode

For standalone Java/Kotlin applications that need runtime dependency loading:

```kotlin
plugins {
    id("net.cubizor.runtime-dependency")
}

runtimeDependency {
    standalone {
        enabled.set(true)
        mainClass.set("com.example.Main")  // Your main class
    }
}

dependencies {
    runtimeDownload("com.google.code.gson:gson:2.10.1")
    runtimeDownload("org.apache.commons:commons-lang3:3.14.0")
}
```

Build and run:
```bash
./gradlew build
java -jar build/libs/yourapp.jar  # Dependencies auto-loaded
```

The plugin automatically:
1. Downloads dependencies to `build/runtime-dependencies/`
2. Configures JAR manifest with bootstrap launcher
3. Loads all dependencies before your main class runs

## Paper Plugin Mode

For Paper plugin developers:

```kotlin
runtimeDependency {
    paper {
        enabled.set(true)
    }
}
```

Automatically generates PluginLoader and updates paper-plugin.yml.

## Basic Mode

Download and organize dependencies without automatic loading:

```kotlin
plugins {
    id("net.cubizor.runtime-dependency")
}

dependencies {
    runtimeDownload("com.google.code.gson:gson:2.10.1")
    runtimeDownload("org.apache.commons:commons-lang3:3.14.0")
}
```

Build and dependencies will be downloaded to `build/runtime-dependencies/`.

## Mode Comparison

| Mode | Use Case | Auto Loading | Requires Paper |
|------|----------|--------------|----------------|
| Standalone | Java/Kotlin apps | Yes | No |
| Paper | Paper plugins | Yes | Yes |
| Basic | Manual control | No | No |

## Documentation

See [USAGE.md](USAGE.md) for detailed configuration and examples.

## Project Structure

- `gradle-plugin/` - Plugin source code
  - `src/main/kotlin/net/cubizor/gradle/launcher/` - Standalone mode launcher

## Commands

- `./gradlew :gradle-plugin:build` - Build plugin
- `./gradlew :gradle-plugin:publishToMavenLocal` - Publish to local Maven
- `./gradlew downloadRuntimeDependencies` - Download dependencies (Basic/Standalone mode)
- `./gradlew generatePaperLoader` - Generate Paper PluginLoader (Paper mode)
- `./gradlew configureStandaloneManifest` - Configure JAR manifest (Standalone mode)
