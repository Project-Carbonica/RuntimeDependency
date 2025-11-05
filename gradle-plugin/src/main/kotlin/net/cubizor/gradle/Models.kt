package net.cubizor.gradle

import java.io.Serializable

data class DependencyInfo(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val repository: RepositoryInfo
) : Serializable

data class RepositoryInfo(
    val name: String,
    val url: String,
    val usernameProperty: String? = null,
    val passwordProperty: String? = null,
    val isMavenCentral: Boolean = false
) : Serializable
