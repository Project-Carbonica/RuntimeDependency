# Runtime Dependency Plugin

A Gradle plugin for runtime dependency management for Paper plugins.

## Features

- **Paper Mode**: Auto-generated PluginLoader for Paper plugins with Maven dependency resolution
- **Basic Mode**: Simple dependency download and organization
- Private repository support with authentication
- Automatic dependency organization by artifact
- Configuration cache compatible
- Java 21+ compatible

## Installation

Add to your `build.gradle.kts`:

```kotlin
plugins {
    id("net.cubizor.runtime-dependency") version "1.4.0"
}
```

## Paper Plugin Mode

For Paper plugin developers:

```kotlin
runtimeDependency {
    paper {
        enabled.set(true)
        loaderPackage.set("com.example.loader")  // Optional
        loaderClassName.set("DependencyLoader")  // Optional
    }
}

dependencies {
    runtimeDownload("com.google.code.gson:gson:2.10.1")
}
```

The plugin automatically:
- Generates a `PluginLoader` class with all dependencies
- Updates `paper-plugin.yml` with the loader reference
- Handles private repository authentication

## Basic Mode

Download and organize dependencies without automatic loading:

```kotlin
plugins {
    id("net.cubizor.runtime-dependency")
}

dependencies {
    runtimeDownload("com.google.code.gson:gson:2.10.1")
}
```

Dependencies are downloaded to `build/runtime-dependencies/`.

## Mode Comparison

| Mode | Use Case | Auto Loading | Build Time Download | Runtime Download |
|------|----------|--------------|---------------------|------------------|
| Paper | Paper plugins | ✅ Yes | ❌ No | ✅ Yes (by Paper) |
| Basic | Manual control | ❌ No | ✅ Yes | ❌ No |

## Documentation

See [USAGE.md](USAGE.md) for detailed configuration, private repository setup, and troubleshooting.

## Project Structure

```
gradle-plugin/
  src/main/
    kotlin/net/cubizor/gradle/
      RuntimeDependencyPlugin.kt   # Main plugin
      Tasks.kt                     # Download & Paper loader tasks
      Extensions.kt                # DSL extensions
      Models.kt                    # Data classes
      RepositoryUtils.kt           # Repository helpers
```

## Gradle Tasks

| Task | Description |
|------|-------------|
| `downloadRuntimeDependencies` | Downloads dependencies to build directory |
| `generatePaperLoader` | Generates Paper PluginLoader (Paper mode) |

## License

MIT License - see [LICENSE](LICENSE) for details.
