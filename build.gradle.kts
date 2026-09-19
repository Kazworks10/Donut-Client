plugins {
    java
    id("fabric-loom") version "1.17.21"
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName = project.property("archives_base_name") as String
}

repositories {
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${project.property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")

    // Config: human-readable TOML, bundled into the jar
    modImplementation(include("com.moandjiezana.toml:toml4j:0.7.2")!!)

    // Tests (pure-Java cores are unit-tested headless)
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

loom {
    mods {
        create("donut") {
            sourceSet(sourceSets.main.get())
        }
    }
}

tasks.processResources {
    inputs.property("version", project.property("mod_version"))

    filesMatching("fabric.mod.json") {
        expand(mapOf("version" to project.property("mod_version")))
    }
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}
