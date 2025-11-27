plugins {
    java
    id("net.cubizor.runtime-dependency") version "1.4.0"
}

group = "com.example"
version = "1.0.0"

repositories {
    mavenCentral()
}

runtimeDependency {
    standalone {
        enabled.set(true)
        mainClass.set("com.example.MyPlugin")
        libraryPath.set("libs")
    }
}

dependencies {
    // Runtime dependency - will be loaded at runtime, not embedded
    runtimeDownload("com.google.code.gson:gson:2.10.1")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
