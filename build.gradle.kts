tasks.register("test") {
    group = "verification"
    description = "Runs all tests in included builds"

    dependsOn(gradle.includedBuild("gradle-plugin").task(":test"))
}

tasks.register("build") {
    group = "build"
    description = "Builds all included builds"

    dependsOn(gradle.includedBuild("gradle-plugin").task(":build"))
}

tasks.register("clean") {
    group = "build"
    description = "Cleans all included builds"

    dependsOn(gradle.includedBuild("gradle-plugin").task(":clean"))
}

tasks.register("publishToMavenLocal") {
    group = "publishing"
    description = "Publishes all publications to the local Maven repository"

    dependsOn(gradle.includedBuild("gradle-plugin").task(":publishToMavenLocal"))
}

tasks.register("publish") {
    group = "publishing"
    description = "Publishes all publications to configured repositories"

    dependsOn(gradle.includedBuild("gradle-plugin").task(":publish"))
}