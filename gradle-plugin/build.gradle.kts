plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
}

group = "net.cubizor.gradle"
version = project.findProperty("version")?.toString() ?: "1.0.0"

kotlin {
    jvmToolchain(21)
}

gradlePlugin {
    plugins {
        create("runtimeDependency") {
            id = "net.cubizor.runtime-dependency"
            implementationClass = "net.cubizor.gradle.RuntimeDependencyPlugin"
            displayName = "Runtime Dependency Plugin"
            description = "Downloads and organizes runtime dependencies with automatic Paper PluginLoader generation"
        }
    }
}

publishing {
    repositories {
        maven {
            name = "nexus"
            val isSnapshot = version.toString().endsWith("-SNAPSHOT")
            url = uri(
                if (isSnapshot) {
                    System.getenv("NEXUS_SNAPSHOT_URL") ?: "https://nexus.example.com/repository/maven-snapshots/"
                } else {
                    System.getenv("NEXUS_RELEASE_URL") ?: "https://nexus.example.com/repository/maven-releases/"
                }
            )
            credentials {
                username = System.getenv("NEXUS_USERNAME")
                password = System.getenv("NEXUS_PASSWORD")
            }
        }
    }
}

dependencies {
    implementation(gradleApi())
    implementation(kotlin("stdlib"))
}
