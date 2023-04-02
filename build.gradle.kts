plugins {
    kotlin("jvm") version "1.8.0"
    java
    application
}

group = "co.tantleffbeef"
version = "0.0.0"

repositories {
    mavenCentral()
    maven(url = "https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

dependencies {
    testImplementation(kotlin("test"))
    compileOnly("org.spigotmc:spigot-api:1.19.4-R0.1-SNAPSHOT")
    compileOnly("com.offbytwo.jenkins:jenkins-client:0.3.8")
    compileOnly("org.tomlj:tomlj:1.1.0")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("MainKt")
}