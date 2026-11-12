plugins {
    id("net.neoforged.moddev") version "2.0.148"
    id("neoforge-mutex")
}

version = "${sc.properties["mod.version"]}+${sc.current.version}"
group = sc.properties["mod.group"]
base.archivesName = "${sc.properties["mod.id"]}-neoforge"

val requiredJava: JavaVersion = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    else -> JavaVersion.VERSION_21
}

repositories {
    fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
        forRepository { maven(url) { name = alias } }
        filter { groups.forEach(::includeGroup) }
    }
    strictMaven("https://api.modrinth.com/maven", "Modrinth", "maven.modrinth")
    maven("https://maven.shedaniel.me/")
}

dependencies {
    fun dep(key: String) = sc.properties["deps.$key"]

    // Tom's Simple Storage: cable network, storage API, filters and GUI base classes
    implementation("maven.modrinth:toms-storage:${dep("toms_storage")}")

    // JEI: recipe transfer; JEI + MezzConfig also run in the dev client (gradlew runClient -PnoJei: without)
    compileOnly("maven.modrinth:jei:${dep("jei")}")
    if (!project.hasProperty("noJei")) {
        runtimeOnly("maven.modrinth:jei:${dep("jei")}")
        runtimeOnly("maven.modrinth:mezzconfig:${dep("mezzconfig")}")
    }
    // REI: API only
    compileOnly("me.shedaniel:RoughlyEnoughItems-api-neoforge:${dep("rei")}") { isTransitive = false }
    compileOnly("me.shedaniel.cloth:basic-math:0.6.0") { isTransitive = false }
    // Config screen: Tom's NeoForge jar does not ship Cloth Config, so the screen needs it installed (dev client: included, -PnoCloth: without)
    compileOnly("maven.modrinth:cloth-config:${dep("cloth_config")}") { isTransitive = false }
    if (!project.hasProperty("noCloth")) runtimeOnly("maven.modrinth:cloth-config:${dep("cloth_config")}")
    // Jade: block tooltips (-PnoJade: dev client without)
    compileOnly("maven.modrinth:jade:${dep("jade")}")
    if (!project.hasProperty("noJade")) runtimeOnly("maven.modrinth:jade:${dep("jade")}")
}

// Loader specific sources next to the shared src/main (same class names in src/fabric)
sourceSets.main {
    java.srcDir(rootProject.file("src/neoforge/java"))
    resources.srcDir(rootProject.file("src/neoforge/resources"))
}

// NeoForge game tests (gradlew :26.3-neoforge:runGameTestServer): shared tests in src/gametest + test mod, not in the jar
val gametest: SourceSet = sourceSets.create("gametest") {
    // src/gametest/java is added by Stonecutter (source set name)
    java.srcDir(rootProject.file("src/gametest-neoforge/java"))
    resources.srcDir(rootProject.file("src/gametest-neoforge/resources"))
    compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().runtimeClasspath + sourceSets.main.get().output
}

neoForge {
    version = sc.properties["deps.neo_loader"]

    mods {
        register(sc.properties["mod.id"]) {
            sourceSet(sourceSets.main.get())
        }
        register("tsautocraft_gametest") {
            sourceSet(gametest)
        }
    }

    runs {
        register("client") {
            gameDirectory = rootProject.file("run-neoforge")
            client()
        }
        register("server") {
            gameDirectory = rootProject.file("run-neoforge")
            server()
        }
        // Runs all tests of the test mod headless and exits (code = failed required tests)
        register("gameTestServer") {
            type = "gameTestServer"
            sourceSet = gametest
            gameDirectory.set(project.layout.buildDirectory.dir("gametest"))
            programArguments.addAll("--tests", "tsautocraft_gametest:*", "--report", project.layout.buildDirectory.file("gametest/report.xml").get().asFile.absolutePath)
        }
    }
}

java {
    withSourcesJar()
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava
}

tasks.withType<JavaCompile>().configureEach {
    options.release = requiredJava.majorVersion.toInt()
    options.encoding = "UTF-8"
}

tasks {
    processResources {
        val props = mapOf(
            "version" to project.version.toString(),
            "minecraft" to sc.properties["mod.mc_compat"],
            "neoforge" to sc.properties["mod.neoforge_compat"],
            "toms_storage" to sc.properties["mod.toms_compat"],
        )
        inputs.properties(props)
        filesMatching("META-INF/neoforge.mods.toml") { expand(props) }
        exclude("fabric.mod.json")
    }

    named("createMinecraftArtifacts") {
        dependsOn("stonecutterGenerate")
    }

    withType<Jar> {
        from(rootProject.file("LICENSE"))
    }

    // gradlew buildAndCollect: jars of all loaders/versions in build/libs/<mod version>/
    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds the mod jars and copies them to build/libs/<mod version>/"
        inputs.property("version", sc.properties["mod.version"])
        from(jar.flatMap { it.archiveFile }, named<Jar>("sourcesJar").flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.dir("libs/${sc.properties["mod.version"]}"))
    }
}
