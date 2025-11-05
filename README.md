# Runtime Dependency Plugin

A Gradle plugin that downloads and organizes runtime dependencies with automatic Paper PluginLoader generation.

## Features

- Zero-config dependency management
- Automatic dependency organization by artifact
- Private repository support
- Configuration cache compatible
- **Paper plugin integration** - Auto-generates PluginLoader

## Quick Start

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

## Paper Plugin Support

For Paper plugin developers:

```kotlin
runtimeDependency {
    paper {
        enabled.set(true)
    }
}
```

Automatically generates PluginLoader and updates paper-plugin.yml.

## Documentation

See [USAGE.md](USAGE.md) for detailed configuration and examples.

## Project Structure

- `gradle-plugin/` - Plugin source code

## Commands

- `./gradlew :gradle-plugin:build` - Build plugin
- `./gradlew :gradle-plugin:publishToMavenLocal` - Publish to local Maven
- `./gradlew downloadRuntimeDependencies` - Download dependencies (when using plugin)
- `./gradlew generatePaperLoader` - Generate Paper PluginLoader (when Paper enabled)
