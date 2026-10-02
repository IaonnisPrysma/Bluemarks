plugins {
    `java-library`
}

group = "dev.kugge"
version = "0.1.0"

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Oldest APIs we support, so the jar also runs on everything newer.
    compileOnly("io.papermc.paper:paper-api:1.19.4-R0.1-SNAPSHOT")
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
    archiveFileName.set("SignMarkers-${project.version}.jar")
}
