plugins {
    java
    id("net.cubizor.runtime-dependency") version "1.4.0-test"
}

group = "com.example"
version = "1.0.0"

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")

    // Also declare settings.gradle.kts repositories here for Paper mode
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

runtimeDependency {
    paper {
        enabled.set(true)
        // loaderPackage should be auto-detected from plugin.yml
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    runtimeDownload("com.google.code.gson:gson:2.10.1")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
