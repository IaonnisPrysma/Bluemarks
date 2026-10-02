plugins {
    `java-library`
}

group = "dev.iaonnis"
version = "0.2.0"

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Oldest API we support, so the jar also runs on everything newer. folia-api is a superset of paper-api,
    // so compiling against it keeps the plugin honest about Folia (region threads, schedulers) while still
    // running on plain Paper / Spigot-derived servers.
    compileOnly("dev.folia:folia-api:1.19.4-R0.1-SNAPSHOT")
    compileOnly("com.github.BlueMap-Minecraft:BlueMapAPI:v2.4.0")
}

tasks.compileJava {
    options.encoding = Charsets.UTF_8.name()
    // Java 17 bytecode runs on 17, 21, 25...; works with any installed JDK >= 17, no toolchain download needed.
    options.release.set(17)
}

tasks.processResources {
    filesMatching("plugin.yml") {
        filter { line -> line.replace("\${version}", project.version.toString()) }
    }
}

tasks.jar {
    archiveFileName.set("Bluemarks-${project.version}.jar")
}
