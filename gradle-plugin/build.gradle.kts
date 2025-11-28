plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
}

group = "net.cubizor.gradle"
version = findProperty("version") ?: "1.0.0"

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
    publications {
        // java-gradle-plugin already creates 'pluginMaven' publication automatically
        // We customize it with POM metadata
        withType<MavenPublication> {
            pom {
                name.set("${rootProject.name} - ${project.name}")
                description.set("RuntimeDependency - Runtime dependency management")
                url.set("https://github.com/Cubizor/RuntimeDependency")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        id.set("cubizor")
                        name.set("Cubizor")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/Cubizor/RuntimeDependency.git")
                    developerConnection.set("scm:git:ssh://github.com/Cubizor/RuntimeDependency.git")
                    url.set("https://github.com/Cubizor/RuntimeDependency")
                }
            }
        }
    }
    repositories {
        // Nexus repository (sadece CI'da kullanılır)
        val nexusReleaseUrl = System.getenv("NEXUS_RELEASE_URL")
        val nexusSnapshotUrl = System.getenv("NEXUS_SNAPSHOT_URL")

        if (nexusReleaseUrl != null && nexusSnapshotUrl != null) {
            maven {
                name = "nexus"
                url = uri(if (version.toString().endsWith("SNAPSHOT")) nexusSnapshotUrl else nexusReleaseUrl)

                credentials {
                    username = project.findProperty("nexusUsername")?.toString()
                        ?: System.getenv("NEXUS_USERNAME")
                    password = project.findProperty("nexusPassword")?.toString()
                        ?: System.getenv("NEXUS_PASSWORD")
                }
            }
        }
    }
}

dependencies {
    implementation(gradleApi())
    implementation(kotlin("stdlib"))

    // Maven Resolver for BootstrapMain runtime dependency resolution
    implementation("org.apache.maven.resolver:maven-resolver-api:1.9.18")
    implementation("org.apache.maven.resolver:maven-resolver-spi:1.9.18")
    implementation("org.apache.maven.resolver:maven-resolver-util:1.9.18")
    implementation("org.apache.maven.resolver:maven-resolver-impl:1.9.18")
    implementation("org.apache.maven.resolver:maven-resolver-connector-basic:1.9.18")
    implementation("org.apache.maven.resolver:maven-resolver-transport-http:1.9.18")
    implementation("org.apache.maven:maven-resolver-provider:3.9.6")
    implementation("org.slf4j:slf4j-simple:2.0.9")
}
