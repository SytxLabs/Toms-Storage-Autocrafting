plugins {
    // Picks the right Loom for the Minecraft version (26.x is unobfuscated: no mappings)
    id("dev.kikugie.loom-back-compat")
}

version = "${sc.properties["mod.version"]}+${sc.current.version}"
group = sc.properties["mod.group"]
base.archivesName = "${sc.properties["mod.id"]}-fabric"

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
    // REI API and Cloth (Cloth Config ships inside Tom's Storage at runtime)
    maven("https://maven.shedaniel.me/")
    maven("https://maven.terraformersmc.com/releases/")
}

dependencies {
    fun dep(key: String) = sc.properties["deps.$key"]

    minecraft("com.mojang:minecraft:${sc.current.version}")
    loomx.applyMojangMappings()
    modImplementation("net.fabricmc:fabric-loader:${dep("fabric_loader")}")

    modCompileOnly("net.fabricmc.fabric-api:fabric-api:${dep("fabric_api")}")
    modLocalRuntime("net.fabricmc.fabric-api:fabric-api:${dep("fabric_api")}")

    // Tom's Simple Storage: cable network, storage API, filters and GUI base classes
    modImplementation("maven.modrinth:toms-storage:${dep("toms_storage")}")

    // JEI: recipe transfer; JEI + MezzConfig also run in the dev client (gradlew runClient -PnoJei: without)
    modCompileOnly("maven.modrinth:jei:${dep("jei")}")
    if (!project.hasProperty("noJei")) {
        modLocalRuntime("maven.modrinth:jei:${dep("jei")}")
        modLocalRuntime("maven.modrinth:mezzconfig:${dep("mezzconfig")}")
    }
    // REI: API only, REI itself is not in the dev client
    modCompileOnly("me.shedaniel:RoughlyEnoughItems-api-fabric:${dep("rei")}") { isTransitive = false }
    compileOnly("me.shedaniel.cloth:basic-math:0.6.0") { isTransitive = false }
    // Config screen: Cloth Config ships inside Tom's Storage, Mod Menu is optional (-PwithModMenu: in the dev client)
    modCompileOnly("maven.modrinth:cloth-config:${dep("cloth_config")}") { isTransitive = false }
    modCompileOnly("maven.modrinth:modmenu:${dep("modmenu")}") { isTransitive = false }
    if (project.hasProperty("withModMenu")) modLocalRuntime("maven.modrinth:modmenu:${dep("modmenu")}")
    // Jade: block tooltips (-PnoJade: dev client without)
    modCompileOnly("maven.modrinth:jade:${dep("jade")}")
    if (!project.hasProperty("noJade")) modLocalRuntime("maven.modrinth:jade:${dep("jade")}")
}

// Loader specific sources next to the shared src/main (same class names in src/neoforge)
sourceSets.main {
    java.srcDir(rootProject.file("src/fabric/java"))
    resources.srcDir(rootProject.file("src/fabric/resources"))
}

// Fabric game tests (gradlew :<mc>-fabric:runGametest): own source set + test mod, not part of the mod jar
val gametest: SourceSet = sourceSets.create("gametest") {
    resources.srcDir(rootProject.file("src/gametest-fabric/resources"))
    compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().runtimeClasspath + sourceSets.main.get().output
}

loom {
    fabricModJsonPath = rootProject.file("src/fabric/resources/fabric.mod.json")

    mods {
        register("tsautocraft") { sourceSet(sourceSets.main.get()) }
        register("tsautocraft-gametest") { sourceSet(gametest) }
    }

    runConfigs.all {
        runDir("../../run") // shared by all versions
        ideConfigGenerated(true)
        // Native crashes of the dev client (driver/overlay DLLs) otherwise leave no trace
        vmArg("-XX:ErrorFile=${rootProject.file("run")}/hs_err_pid%p.log")
    }

    runs {
        register("gametest") {
            server()
            name = "Game Test"
            source(gametest)
            vmArg("-Dfabric-api.gametest")
            vmArg("-Dfabric-api.gametest.report-file=${project.layout.buildDirectory.get().asFile}/gametest/report.xml")
            runDir("build/gametest")
            ideConfigGenerated(false)
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
            "toms_storage" to sc.properties["mod.toms_compat"],
        )
        inputs.properties(props)
        filesMatching("fabric.mod.json") { expand(props) }
        exclude("META-INF/neoforge.mods.toml")
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
        from(named<Jar>("apiJar").flatMap { it.archiveFile }, named<Jar>("apiSourcesJar").flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.dir("libs/${sc.properties["mod.version"]}"))
    }
}

// API for other mods: the api package plus the data types its signatures use (compile against it, the full mod at runtime).
// No loader classes in there, so one jar (tsautocraft-<ver>+<mc>-api.jar) serves Fabric and NeoForge; built here only.
val apiClasses = listOf("eu/sytxlabs/tsautocraft/api/**", "eu/sytxlabs/tsautocraft/job/CraftingJob*", "eu/sytxlabs/tsautocraft/job/JobScheduler*",
    "eu/sytxlabs/tsautocraft/pattern/PatternData*", "eu/sytxlabs/tsautocraft/pattern/PatternEncoder*", "eu/sytxlabs/tsautocraft/util/ItemKey*")
val apiJar = tasks.register<Jar>("apiJar") {
    group = "build"
    archiveBaseName = sc.properties["mod.id"]
    archiveClassifier = "api"
    from(sourceSets.main.get().output) { include(apiClasses.map { if (it.endsWith("**")) it else "$it.class" }) }
    from(rootProject.file("LICENSE"))
}
val apiSourcesJar = tasks.register<Jar>("apiSourcesJar") {
    group = "build"
    archiveBaseName = sc.properties["mod.id"]
    archiveClassifier = "api-sources"
    from(sourceSets.main.get().allJava) { include(apiClasses.map { if (it.endsWith("**")) it else "$it.java" }) }
}
tasks.assemble { dependsOn(apiJar, apiSourcesJar) }

// Plain Java test of the crafting planner (no Minecraft): gradlew plannerTest
tasks.register<JavaExec>("plannerTest") {
    group = "verification"
    dependsOn(tasks.compileJava)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "eu.sytxlabs.tsautocraft.planner.PlannerSelfTest"
}
