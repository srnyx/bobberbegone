import xyz.srnyx.gradlegalaxy.utility.inGitHubPublish
import xyz.srnyx.gradlegalaxy.utility.inGitHubWorkflow

plugins {
    id("dev.kikugie.loom-back-compat")
    id("xyz.srnyx.gradle-galaxy") version "c99868f"
    id("me.modmuss50.mod-publish-plugin") version "04fb4ab"

    // Fletching Table
    alias(ft.plugins.default)
    alias(ft.plugins.fabric)
    alias(ft.plugins.mixin)
    alias(ft.plugins.lang)
}

group = property("mod.group").toString()
description = "A simple Fabric client mod that removes the bobber when hooked (updated from Bobber Begone)"

// Properties
val modId = property("mod.id").toString()
val modName = property("mod.name").toString()
val mcVersionStart = property("deps.minecraft.start").toString()
val mcVersionEnd = if (hasProperty("deps.minecraft.end")) property("deps.minecraft.end").toString() else null
val loaderVersion = property("deps.loader").toString()
val fabricApiVersion = if (hasProperty("deps.fabric_api")) property("deps.fabric_api").toString() else null
val yaclVersion = property("deps.yacl").toString()
val modMenuVersion = property("deps.modmenu").toString()

// Java version
val is261Plus: Boolean = sc.current.parsed >= "26.1"
val javaVersionProject = when {
    is261Plus -> JavaVersion.VERSION_25
    sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21
    else -> JavaVersion.VERSION_17
}

// Mod version
val modVersion = when {
    !inGitHubWorkflow -> "0.0.0-snapshot"
    !inGitHubPublish -> "0.0.0-snapshot.${galaxy.java.version.get()}"
    else -> galaxy.java.version.get()
}

galaxy {
    java {
        version = "${sc.current.version}-$modVersion" // ex: 1.21.6-1.0.0, 1.21.4-0.0.0-snapshot, 1.21.11-0.0.0-snapshot.25fsf52
        javaVersion = javaVersionProject
    }

    repository {
        add(
            ISXANDER, GNOMECRAFT_RELEASES,
            FASTSTATS_RELEASES, FASTSTATS_SNAPSHOTS,
            FABRIC, MAVEN_CENTRAL)
    }

    minecraft {
        replacementFiles = setOf("fabric.mod.json", "bobberbegone.mixins.json")
        replacements.putAll(replacements.get() + mapOf(
            "mod_id" to modId,
            "name" to property("mod.name").toString(),
            "version" to modVersion,
            "java" to "JAVA_${javaVersionProject.majorVersion}",
            "deps_minecraft" to buildString {
                append(">=$mcVersionStart")
                if (mcVersionEnd != null) append(" <=$mcVersionEnd")
            },
            "deps_loader" to loaderVersion,
            "deps_yacl" to yaclVersion,
            "deps_modmenu" to modMenuVersion))

        platformPublishing {
            minecraftVersionStart = mcVersionStart
            minecraftVersionEnd = mcVersionEnd
            apiTiers.addAll(FABRIC, QUILT)
            addAnnoyingApiDependency = false

            val mainFile = loomx.modJar.flatMap { it.archiveFile }
            modrinth("3mdJf8V2") {
                file = mainFile
                environment = CLIENT_ONLY

                // Fabric API
                fabricApiVersion?.let { requires {
                    id = "P7dR8mSH"
                    version = it
                } }
                // YetAnotherConfigLib (YACL)
                requires {
                    id = "1eAoo2KR"
                    version = if (hasProperty("deps.yacl_modrinth")) {
                        property("deps.yacl_modrinth").toString()
                    } else {
                        yaclVersion
                    }
                }
                // Mod Menu
                optional {
                    id = "mOgUt4GM"
                    version = modMenuVersion
                }
            }
            curseforge("1678228") {
                file = mainFile
                client = true

                // Fabric API
                fabricApiVersion?.let { requires { slug = "fabric-api" } }
                // YetAnotherConfigLib (YACL)
                requires { slug = "yacl" }
                // Mod Menu
                optional { slug = "modmenu" }
            }
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")

    // Mappings
    loomx.applyMojangMappings()

    // Fabric
    modImplementation("net.fabricmc:fabric-loader:$loaderVersion")
    fabricApiVersion?.let { modImplementation("net.fabricmc.fabric-api:fabric-api:$it") }

    // Mods
    when {
        sc.current.parsed >= "1.20.5" -> "dev.isxander:yet-another-config-lib"
        else -> "dev.isxander.yacl:yet-another-config-lib-fabric"
    }.let { modImplementation("$it:$yaclVersion") {
        exclude(group = "com.twelvemonkeys.imageio")
        exclude(group = "com.twelvemonkeys.common")
    } }
    modImplementation("com.terraformersmc:modmenu:$modMenuVersion")

    // Library: FastStats (1.16.1-1.17.1, 1.18-1.21.8, 1.21.9-1.21.11, 26.1-26.3)
    when {
        sc.current.parsed >= "1.16.1" && sc.current.parsed <= "1.17.1" -> "1.16.1-1.17.1"
        sc.current.parsed >= "1.18" && sc.current.parsed <= "1.21.8" -> "1.18-1.21.8"
        sc.current.parsed >= "1.21.9" && sc.current.parsed <= "1.21.11" -> "1.21.9-1.21.11"
        sc.current.parsed >= "26.1" && sc.current.parsed <= "26.3" -> "26.1-26.3"
        else -> {
            logger.warn("Unsupported Minecraft version for FastStats: ${sc.current.version}")
            null
        }
    }?.let { jijLibrary("dev.faststats.metrics:fabric:${property("library.faststats")}+mc$it") }
}

base.archivesName = modId

stonecutter {
    dependencies["java"] = javaVersionProject.majorVersion

    // Swaps
    swaps["mod_id"] = "\"$modId\""
    swaps["mod_name"] = "\"$modName\""
    swaps["mod_version"] = "\"$modVersion\""
    swaps["mod_version_full"] = "\"$version\""
}

fletchingTable {
    fabric.configure("main") {
        entrypoint("modmenu", "com.terraformersmc.modmenu.api.ModMenuApi")
    }

    mixins.configure("main") {
        mixin("bobberbegone.mixins.json") {
            env("client")
        }
    }

    lang.configure("main") {}
}

loom {
    fabricModJsonPath = rootProject.file("src/main/resources/fabric.mod.json") // Useful for interface injection

    if (!is261Plus) decompilerOptions.named("vineflower") {
        options.put("mark-corresponding-synthetics", "1") // Adds names to lambdas - useful for mixins
    }

    runConfigs.all {
        preferGradleTask = true
        jvmArguments.add("-Dmixin.debug.export=true") // Exports transformed classes for debugging
        runDirectory = file("../../run") // Shares the run directory between versions
    }
}

tasks {
    // Builds the version into a shared folder in `build/libs`
    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds the mod and copies the jar to a shared folder"

        // loomx.modJar returns the jar task for the applied loom variant
        from(loomx.modJar.map { it.archiveFile })
        into(rootProject.layout.buildDirectory.file("libs"))

        dependsOn("build")
    }
}

// Register tasks for active version
if (sc.current.isActive) {
    rootProject.tasks.register("buildActive") {
        group = "build"
        description = "Builds the mod for the currently active Minecraft version"

        // Build mod + copy built jar to shared folder
        dependsOn(tasks.named("build"), tasks.named("buildAndCollect"))
    }

    rootProject.tasks.register("runActive") {
        group = "fabric"
        description = "Runs the mod for the currently active Minecraft version"

        // Run mod
        dependsOn(tasks.named("runClient"))
    }
}

// Custom jijLibrary (modImplementation + include) dependency configurations
fun DependencyHandler.jijLibrary(dependency: String) {
    modImplementation(dependency)
    include(dependency)
}
