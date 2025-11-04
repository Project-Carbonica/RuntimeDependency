package net.cubizor.runtimedependency.app

import com.google.gson.Gson
import org.apache.commons.lang3.StringUtils
import java.io.File

fun main() {
    println("=== Runtime Dependency Plugin Demo ===\n")

    // Runtime'da indirilen dependency'lerin yeri
    val runtimeDepsDir = File("build/runtime-dependencies")

    if (runtimeDepsDir.exists()) {
        println("Runtime dependencies found at: ${runtimeDepsDir.absolutePath}")
        runtimeDepsDir.walkTopDown()
            .filter { it.extension == "jar" }
            .forEach { println("  - ${it.relativeTo(runtimeDepsDir)}") }
        println()
    }

    // Gson kullanimi (runtime dependency)
    val gson = Gson()
    val person = Person("Ahmet", 30, "Istanbul")
    val json = gson.toJson(person)
    println("Gson serialization:")
    println("  Object: $person")
    println("  JSON: $json")
    println()

    // Commons Lang3 kullanimi (runtime dependency)
    val text = "  runtime dependency plugin  "
    val capitalized = StringUtils.capitalize(text.trim())
    println("Commons Lang3 string utils:")
    println("  Original: '$text'")
    println("  Capitalized: '$capitalized'")
    println()

    // Gson deserialization
    val jsonStr = """{"name":"Mehmet","age":25,"city":"Ankara"}"""
    val deserializedPerson = gson.fromJson(jsonStr, Person::class.java)
    println("Gson deserialization:")
    println("  JSON: $jsonStr")
    println("  Object: $deserializedPerson")
}

data class Person(
    val name: String,
    val age: Int,
    val city: String
)
