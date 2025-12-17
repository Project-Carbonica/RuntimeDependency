rootProject.name = "test-paper"

pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://storehouse.okaeri.eu/repository/maven-public/")
        maven {
            name = "CubizorReleases"
            url = uri("https://nexus.cubizor.net/repository/maven-releases/")
            credentials {
                username = System.getenv("NEXUS_USERNAME")
                password = System.getenv("NEXUS_PASSWORD")
            }
        }
    }
}
