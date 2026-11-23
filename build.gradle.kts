plugins {
    java
}

group = "dev.aimob"
version = providers.gradleProperty("version").orElse("1.0.0").get()

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("${property("paperApiGroup")}:${property("paperApiArtifact")}:${property("paperApiVersion")}")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.processResources {
    val pluginVersion = project.version.toString()
    inputs.property("version", pluginVersion)
    filesMatching("plugin.yml") {
        expand("version" to pluginVersion)
    }
}

tasks.jar {
    archiveBaseName.set("AiMob")
    archiveVersion.set(project.version.toString())
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}
