plugins {
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.scalar-labs:scalardb:3.15.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.1")
    implementation("org.slf4j:slf4j-simple:2.0.13")
    implementation("org.postgresql:postgresql:42.7.2")
}

application {
    mainClass.set("io.example.scalardbmaelstrom.Main")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "io.example.scalardbmaelstrom.Main"
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    manifest {
        attributes["Main-Class"] = "io.example.scalardbmaelstrom.Main"
    }
    mergeServiceFiles()
}