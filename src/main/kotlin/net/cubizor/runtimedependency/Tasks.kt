package net.cubizor.runtimedependency

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.io.File

abstract class RuntimeDependencyTask : DefaultTask() {
    @get:Input
    abstract val organizeByGroup: Property<Boolean>

    @get:InputFiles
    abstract val runtimeConfiguration: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun download() {
        val output = outputDir.get().asFile
        output.deleteRecursively()
        output.mkdirs()

        val organize = organizeByGroup.get()

        runtimeConfiguration.files.forEach { file ->
            val destFile = if (organize) {
                val parts = file.nameWithoutExtension.split("-")
                val artifact = if (parts.size >= 2) {
                    parts.dropLast(1).joinToString("-")
                } else {
                    "unknown"
                }
                File(output, "$artifact/${file.name}")
            } else {
                File(output, file.name)
            }

            destFile.parentFile.mkdirs()
            file.copyTo(destFile, overwrite = true)
        }

        println("Runtime dependencies downloaded to: ${output.absolutePath}")
        output.walkTopDown().filter { it.isFile }.forEach {
            println("  - ${it.relativeTo(output)}")
        }
    }
}

abstract class GeneratePaperLoaderTask : DefaultTask() {
    @get:Input
    abstract val loaderPackage: Property<String>

    @get:Input
    abstract val loaderClassName: Property<String>

    @get:Input
    abstract val dependencies: ListProperty<DependencyInfo>

    @get:Input
    abstract val repositories: MapProperty<String, RepositoryInfo>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Internal
    abstract val resourcesDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val packageName = loaderPackage.get()
        val className = loaderClassName.get()
        val deps = dependencies.get()
        val repos = repositories.get()

        val loaderClass = generateLoaderClass(packageName, className, deps, repos)

        val packagePath = packageName.replace('.', '/')
        val outputFile = outputDir.get().asFile.resolve("$packagePath/$className.java")
        outputFile.parentFile.mkdirs()
        outputFile.writeText(loaderClass)

        println("Generated Paper PluginLoader: ${outputFile.absolutePath}")

        updatePluginYml(packageName, className)
    }

    private fun generateLoaderClass(
        packageName: String,
        className: String,
        deps: List<DependencyInfo>,
        repos: Map<String, RepositoryInfo>
    ): String {
        val repoMap = repos.filter { !it.value.isMavenCentral }
        val needsAuth = repoMap.values.any { it.usernameProperty != null || it.passwordProperty != null }
        val authImport = if (needsAuth) {
            "import org.eclipse.aether.util.repository.AuthenticationBuilder;"
        } else ""

        val repoCode = repoMap.values.distinctBy { it.url }.joinToString("\n") { repo ->
            val builderCode = StringBuilder()
            builderCode.append("        RemoteRepository.Builder ${repo.name}Builder = new RemoteRepository.Builder(\"${repo.name}\", \"default\", \"${repo.url}\");")

            // Only apply authentication for HTTP/HTTPS repositories
            // File protocol (mavenLocal) does not support authentication
            val isFileProtocol = repo.url.startsWith("file://") || repo.url.startsWith("file:")
            if (!isFileProtocol && repo.usernameProperty != null && repo.passwordProperty != null) {
                builderCode.append("\n")
                val envPrefix = repo.name.uppercase().replace("-", "_").replace(".", "_")
                builderCode.append("        // Try environment variable first, then system property\n")
                builderCode.append("        String ${repo.name}User = System.getenv(\"${envPrefix}_USERNAME\");\n")
                builderCode.append("        System.out.println(\"[RuntimeDependency] ${repo.name} - ENV ${envPrefix}_USERNAME: \" + (${repo.name}User != null ? \"[SET]\" : \"[NOT SET]\"));\n")
                builderCode.append("        if (${repo.name}User == null) {\n")
                builderCode.append("            ${repo.name}User = System.getProperty(\"${repo.usernameProperty}\");\n")
                builderCode.append("            System.out.println(\"[RuntimeDependency] ${repo.name} - SYSPROP ${repo.usernameProperty}: \" + (${repo.name}User != null ? \"[SET]\" : \"[NOT SET]\"));\n")
                builderCode.append("        }\n")
                builderCode.append("        String ${repo.name}Pass = System.getenv(\"${envPrefix}_PASSWORD\");\n")
                builderCode.append("        if (${repo.name}Pass == null) {\n")
                builderCode.append("            ${repo.name}Pass = System.getProperty(\"${repo.passwordProperty}\");\n")
                builderCode.append("        }\n")
                builderCode.append("        if (${repo.name}User != null && ${repo.name}Pass != null) {\n")
                builderCode.append("            System.out.println(\"[RuntimeDependency] ${repo.name} - Authentication ENABLED\");\n")
                builderCode.append("            ${repo.name}Builder.setAuthentication(new AuthenticationBuilder()\n")
                builderCode.append("                .addUsername(${repo.name}User)\n")
                builderCode.append("                .addPassword(${repo.name}Pass)\n")
                builderCode.append("                .build());\n")
                builderCode.append("        } else {\n")
                builderCode.append("            System.out.println(\"[RuntimeDependency] ${repo.name} - Authentication DISABLED (credentials not found)\");\n")
                builderCode.append("        }\n")
            }

            builderCode.append("        resolver.addRepository(${repo.name}Builder.build());")
            builderCode.toString()
        }

        return """
package $packageName;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
$authImport
import org.jetbrains.annotations.NotNull;

public class $className implements PluginLoader {
    @Override
    public void classloader(@NotNull PluginClasspathBuilder classpathBuilder) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();

${deps.joinToString("\n") { dep ->
        "        resolver.addDependency(new Dependency(new DefaultArtifact(\"${dep.groupId}:${dep.artifactId}:${dep.version}\"), null));"
}}

$repoCode

        classpathBuilder.addLibrary(resolver);
    }
}
""".trimIndent()
    }

    private fun updatePluginYml(packageName: String, className: String) {
        val srcDir = resourcesDir.get().asFile
        val paperPluginYml = srcDir.resolve("paper-plugin.yml")
        val pluginYml = srcDir.resolve("plugin.yml")

        val targetFile = when {
            paperPluginYml.exists() -> paperPluginYml
            pluginYml.exists() -> pluginYml
            else -> {
                println("Warning: No plugin.yml or paper-plugin.yml found, skipping loader update")
                return
            }
        }

        val isPaperPluginYml = targetFile.name == "paper-plugin.yml"
        val loaderKey = if (isPaperPluginYml) "loader:" else "paper-plugin-loader:"
        val loaderLine = "$loaderKey $packageName.$className"

        val lines = targetFile.readLines().toMutableList()
        val loaderIndex = lines.indexOfFirst {
            it.trim().startsWith("loader:") || it.trim().startsWith("paper-plugin-loader:")
        }

        if (loaderIndex >= 0) {
            lines[loaderIndex] = loaderLine
        } else {
            lines.add(0, loaderLine)
        }

        targetFile.writeText(lines.joinToString("\n"))
        println("Updated ${targetFile.name} with $loaderKey $packageName.$className")
    }
}
