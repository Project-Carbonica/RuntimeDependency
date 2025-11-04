plugins {
    kotlin("jvm") version "2.1.0"
    application
    id("net.cubizor.runtime-dependency")
}

kotlin {
    jvmToolchain(21)
}

runtimeDependency {
    paper {
        enabled.set(true)
        loaderPackage.set("net.cubizor.runtimedependency.loader")
        loaderClassName.set("RuntimeDependencyLoader")
    }
}

repositories {
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    // Paper API for PluginLoader
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")

    // Runtime dependency download
    runtimeDownload("com.google.code.gson:gson:2.10.1")
    runtimeDownload("org.apache.commons:commons-lang3:3.14.0")
}

application {
    // Define the Fully Qualified Name for the application main class
    // (Note that Kotlin compiles `App.kt` to a class with FQN `com.example.app.AppKt`.)
    mainClass = "net.cubizor.runtimedependency.app.AppKt"
}
