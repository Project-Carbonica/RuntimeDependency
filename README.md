# Runtime Dependency Plugin

A Gradle plugin for runtime dependency management with support for standalone applications, Paper plugins, and manual dependency control.

## Features

- **Standalone Mode**: Automatic runtime dependency loading for standalone Java/Kotlin applications
- **Paper Mode**: Auto-generated PluginLoader for Paper plugins with Maven dependency resolution
- **Basic Mode**: Simple dependency download and organization
- Zero-config dependency management with custom ClassLoader
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

## Standalone Mode

For standalone Java/Kotlin applications that need runtime dependency loading without embedding dependencies in the JAR:

```kotlin
plugins {
    id("net.cubizor.runtime-dependency")
}

runtimeDependency {
    standalone {
        enabled.set(true)
        mainClass.set("com.example.Main")  // Your main class
        libraryPath.set("libs")            // Optional, default: "libs"
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

# Copy dependencies to libs folder
cp -r build/runtime-dependencies/* libs/

# Run your application
java -jar build/libs/yourapp.jar
```

### How It Works

The plugin uses a **Bootstrap ClassLoader** approach:

1. **Build Time**: 
   - Plugin injects `BootstrapMain` as the JAR's entry point
   - Sets `Original-Main-Class` in manifest to your actual main class
   - Downloads dependencies to `build/runtime-dependencies/`

2. **Runtime**:
   - `BootstrapMain` creates a custom `RuntimeClassLoader`
   - Loads all JARs from the library path into this ClassLoader
   - Loads your main class through the custom ClassLoader
   - Invokes your `main()` method with full access to dependencies

This approach is **Java 21+ compatible** and doesn't require any reflection hacks.

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
| Standalone | Java/Kotlin apps | ✅ Yes | ✅ Yes | ❌ No |
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
      InjectionTasks.kt            # Standalone JAR configuration
      Extensions.kt                # DSL extensions
      Models.kt                    # Data classes
      RepositoryUtils.kt           # Repository helpers
    java/net/cubizor/gradle/loader/
      BootstrapMain.java           # Standalone entry point
```

## Gradle Tasks

| Task | Description |
|------|-------------|
| `downloadRuntimeDependencies` | Downloads dependencies to build directory |
| `generatePaperLoader` | Generates Paper PluginLoader (Paper mode) |
| `configureStandaloneJar` | Configures JAR with bootstrap (Standalone mode) |
| `generateDependencyManifest` | Generates dependency list (Standalone mode) |

## License

MIT License - see [LICENSE](LICENSE) for details.
