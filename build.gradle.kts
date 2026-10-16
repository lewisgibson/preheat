import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

// Preheat is compiled straight against the jars the game runs (scripts/fetch-deps.py downloads them, pinned by sha1,
// into libs/). Minecraft 26.x ships unobfuscated, so there is nothing to remap and no Loom: a jar is plain classes plus
// both loaders' metadata, and the same jar loads on Fabric and on NeoForge.
//
// One jar per Minecraft line, named the way Sodium, Lithium and Iris name theirs (preheat-<version>+mc<minecraft>.jar):
// 26.3 from the compiled classes, and 26.2 from the same classes with 26.3's renamed OpenGL packages mapped back
// (src/relocate). `./gradlew build` builds both and runs every check: formatting (Spotless), Error Prone with NullAway,
// the tests (the compatibility matrix among them) and the coverage gate.

buildscript {
    configurations.classpath { resolutionStrategy.activateDependencyLocking() }
}

plugins {
    java
    jacoco
    alias(libs.plugins.spotless)
    alias(libs.plugins.errorprone)
}

val modVersion = providers.gradleProperty("mod_version").get()
version = modVersion
description = "Cuts the freeze at portals with Iris shaders by preparing every dimension ahead of time"
base.archivesName.set("preheat")

// The release workflow reads the version from here, and releases it when it has no GitHub release yet.
tasks.register("printVersion") {
    val projectVersion = project.version.toString()
    doLast { println(projectVersion) }
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(25)) }
}

repositories { mavenCentral() }

// Every resolved external dependency is pinned in gradle.lockfile (refresh both files as docs/building.md says).
dependencyLocking {
    lockAllConfigurations()
    lockMode.set(LockMode.STRICT)
}

val relocate: SourceSet = sourceSets.create("relocate")

/** The game's jars: compile-time only, since the game provides them. */
val gameJars = fileTree(rootProject.file("libs/compile")) { include("*.jar") }

dependencies {
    compileOnly(gameJars)
    compileOnly(libs.asm.tree)
    compileOnly(libs.jspecify)
    // NeoForge's mod list reports versions as Maven's ArtifactVersion.
    compileOnly(libs.maven.artifact)

    "relocateImplementation"(libs.asm.commons)
    "relocateCompileOnly"(libs.jspecify)

    errorprone(libs.errorprone.core)
    errorprone(libs.nullaway)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.asm.tree)
    testImplementation(libs.asm.commons)
    testImplementation(libs.jspecify)
    testImplementation(libs.maven.artifact)
    // The tests exercise the pure parts and name a few game types; the whole compile set is on their classpath.
    testImplementation(gameJars)
    testImplementation(relocate.output)
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all,-classfile,-processing,-serial", "-Werror"))
    options.errorprone {
        disableWarningsInGeneratedCode.set(true)
        check("NullAway", CheckSeverity.ERROR)
        // Error Prone 2.50.0 crashes inside this suggestion-only check on List.of() enum arguments
        // (NoSuchElementException in StringConcatToTextBlock.matchLiteral); nothing here concatenates strings into text.
        disable("StringConcatToTextBlock")
        // Every package is @NullMarked (package-info.java); NullAway checks those, in JSpecify's semantics.
        option("NullAway:OnlyNullMarked", "true")
        option("NullAway:JSpecifyMode", "true")
        // A mixin's @Shadow fields are the target class's own and are assigned by it, never by the mixin, so NullAway's
        // initialisation rules cannot apply to them; the mixins' logic lives in the checked helper classes.
        option("NullAway:ExcludedClassAnnotations", "org.spongepowered.asm.mixin.Mixin")
        // Mixin calls these handlers from the target class, and their parameters are fixed by the method they hook, so
        // to Error Prone they look unused when they are not.
        val mixinHandlers = listOf(
            "org.spongepowered.asm.mixin.injection.Inject",
            "org.spongepowered.asm.mixin.injection.ModifyArg",
            "org.spongepowered.asm.mixin.gen.Accessor",
            "org.spongepowered.asm.mixin.gen.Invoker",
            "org.spongepowered.asm.mixin.Shadow").joinToString(",")
        option("UnusedMethod:ExemptingMethodAnnotations", mixinHandlers)
        option("Unused:methodAnnotationsExemptingParameters", mixinHandlers)
    }
}

// The test sources are not @NullMarked; NullAway reads none of them, and Error Prone's other checks still run.
tasks.named<JavaCompile>("compileTestJava") {
    options.errorprone.check("NullAway", CheckSeverity.OFF)
}

spotless {
    java {
        target("src/**/*.java")
        palantirJavaFormat(libs.versions.palantir.java.format.get()).formatJavadoc(false)
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

/** A Minecraft line Preheat ships a jar for: the classes it uses and the version ranges its metadata declares. */
data class Target(val minecraft: String, val relocated: Boolean, val fabricRange: String, val neoforgeRange: String, val neoforge: String)

val targets = listOf(
    Target("26.3", false, "~26.3", "[26.3,26.4)", "[26.3,)"),
    Target("26.2", true, "~26.2", "[26.2,26.3)", "[26.2,)"),
)

val blaze3dClasses = layout.buildDirectory.dir("classes/blaze3d")
val mapToBlaze3d = tasks.register<JavaExec>("mapToBlaze3d") {
    description = "Maps 26.3's com.mojang.renderpearl packages back to com.mojang.blaze3d for the 26.2 jar."
    dependsOn(tasks.classes)
    val mainClasses = sourceSets.main.get().output.classesDirs.singleFile
    inputs.dir(mainClasses)
    outputs.dir(blaze3dClasses)
    classpath = relocate.runtimeClasspath
    mainClass.set("com.lewisgibson.preheat.build.Relocate")
    val out = blaze3dClasses.get().asFile
    args(mainClasses.absolutePath, out.absolutePath)
    doFirst { out.deleteRecursively() }
}

val targetJars = targets.map { t ->
    val resources = tasks.register<Copy>("resourcesMc${t.minecraft.replace(".", "_")}") {
        val props = mapOf(
            // Shown as is by Mod Menu and the mod lists, so it is the plain version, as Sodium and Iris show
            // theirs; the jar's file name carries the Minecraft line.
            "version" to modVersion,
            "minecraft_range_fabric" to t.fabricRange,
            "minecraft_range_neoforge" to t.neoforgeRange,
            "neoforge_range" to t.neoforge)
        inputs.properties(props)
        from(sourceSets.main.get().resources.srcDirs)
        filesMatching(listOf("fabric.mod.json", "META-INF/neoforge.mods.toml")) { expand(props) }
        into(layout.buildDirectory.dir("resources/mc${t.minecraft}"))
    }
    tasks.register<Jar>("jarMc${t.minecraft.replace(".", "_")}") {
        group = "build"
        description = "The Preheat jar for Minecraft ${t.minecraft}."
        archiveVersion.set("$modVersion+mc${t.minecraft}")
        if (t.relocated) {
            dependsOn(mapToBlaze3d)
            from(blaze3dClasses)
        } else {
            from(sourceSets.main.get().output.classesDirs)
        }
        // The @NullMarked package-info classes are for the compiler; in a mixin package Mixin refuses to load any class
        // that is not a mixin, so they are not shipped there.
        exclude("com/lewisgibson/preheat/mixin/**/package-info.class")
        from(resources)
        from("LICENSE") { rename { "LICENSE_preheat" } }
        // The LGPL is the GPL's additional permissions: both texts ship with the jar.
        from("COPYING") { rename { "COPYING_preheat" } }
        from("COPYING.LESSER") { rename { "COPYING.LESSER_preheat" } }
        manifest {
            attributes("Implementation-Title" to "Preheat", "Implementation-Version" to "$modVersion+mc${t.minecraft}")
        }
    }
}

// The plain `jar` task is not a release artifact; the per-Minecraft jars are.
tasks.jar { enabled = false }
tasks.assemble { dependsOn(targetJars) }

tasks.processResources {
    // The resources in the test classpath; the jars expand their own copies per target.
    val props = mapOf("version" to "$modVersion+dev", "minecraft_range_fabric" to "*", "minecraft_range_neoforge" to "*", "neoforge_range" to "*")
    inputs.properties(props)
    filesMatching(listOf("fabric.mod.json", "META-INF/neoforge.mods.toml")) { expand(props) }
}

tasks.test {
    useJUnitPlatform()
    dependsOn(mapToBlaze3d)
    // The compatibility matrix test reads the compiled classes, the relocated ones and the jars under libs/matrix.
    systemProperty("preheat.classes", sourceSets.main.get().output.classesDirs.asPath)
    systemProperty("preheat.classes.blaze3d", blaze3dClasses.get().asFile.absolutePath)
    systemProperty("preheat.matrix", rootProject.file("libs/matrix").absolutePath)
    systemProperty("preheat.reports", layout.buildDirectory.dir("reports/compat").get().asFile.absolutePath)
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
    finalizedBy(tasks.jacocoTestReport)
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

// The classes the coverage gate does not count: thin adapters that only hand over to the game, the driver, Mixin or a
// mod loader, which exist only in a launched game, so a unit test could only mock what they exist to call. Each holds
// no decision of its own; the decisions are in the counted classes named beside it, tested against fakes. The CPU lab
// (scripts/lab/) and the real-game harness exercise the adapters.
val coverageExclusions = listOf(
    // Mixins and accessors: merged into Iris's and Minecraft's classes at load time; they call the helpers below.
    "com/lewisgibson/preheat/mixin/**",
    // Mixin's hook: reads class files from Mixin's bytecode service; decisions in MixinGate.
    "com/lewisgibson/preheat/PreheatMixinPlugin.class",
    // The loader bridges: FabricLoader and NeoForge's FML exist only in a launched game.
    "com/lewisgibson/preheat/platform/FabricPlatform.class",
    "com/lewisgibson/preheat/platform/NeoForgePlatform.class",
    "com/lewisgibson/preheat/neoforge/PreheatNeoForge.class",
    "com/lewisgibson/preheat/ui/ModMenuEntry.class",
    // OpenGL on the game's context: the shader cache's calls (BinaryCache, BinaryChecks, ProgramInterface decide), the
    // GL state a loading frame restores (GlStateSnapshot), and the video memory reading (VideoMemory).
    "com/lewisgibson/preheat/cache/LwjglProgramGl.class",
    "com/lewisgibson/preheat/progress/LwjglStateGl.class",
    "com/lewisgibson/preheat/keep/LwjglVideoMemory.class",
    // The game's one shader cache, on the driver and its writer thread (BinaryCache).
    "com/lewisgibson/preheat/cache/ProgramBinaries.class",
    // Iris's patch types, its config and the render device (SavedPatches).
    "com/lewisgibson/preheat/cache/PatchCache.class",
    // Iris's pipelines, buffers and settings when a kept pipeline is put aside and back (Reactivation), and Distant
    // Horizons' own classes (DhOverrides).
    "com/lewisgibson/preheat/keep/IrisReactivation.class",
    "com/lewisgibson/preheat/dh/DhBridge.class",
    // Minecraft's frame, window and loading overlay, and SDL's message pump (Frames, LoadingScreen, ProgressReload).
    "com/lewisgibson/preheat/progress/FrameRenderer.class",
    "com/lewisgibson/preheat/progress/BuildProgress.class",
    "com/lewisgibson/preheat/progress/SdlPump.class",
    // The game's one build tracking, wired to the cache and the overlay (BuildTracking).
    "com/lewisgibson/preheat/build/BuildTracker.class",
    // Iris's first build at the end of loading (StartupBuilds).
    "com/lewisgibson/preheat/startup/Startup.class",
    // Minecraft's screens and drawing, and Sodium's page API (SettingsFacts, PanelLayout, FolderChoice, CacheButtons,
    // FolderPicker, OneAnswer, Words), and SDL's folder dialog.
    "com/lewisgibson/preheat/ui/SettingsText.class",
    "com/lewisgibson/preheat/ui/SettingsScreens.class",
    "com/lewisgibson/preheat/ui/PreheatScreen.class",
    "com/lewisgibson/preheat/ui/PreheatScreen\$Tick.class",
    "com/lewisgibson/preheat/ui/CacheFolderScreen.class",
    "com/lewisgibson/preheat/ui/MoveCacheScreen.class",
    "com/lewisgibson/preheat/ui/CacheActions.class",
    "com/lewisgibson/preheat/ui/FolderDialogs.class",
    "com/lewisgibson/preheat/ui/SdlFolderDialog.class",
    "com/lewisgibson/preheat/ui/sodium/SodiumOptions.class",
)

val coveredClasses = files(sourceSets.main.get().output.classesDirs.map { dir -> fileTree(dir) { exclude(coverageExclusions) } })

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    classDirectories.setFrom(coveredClasses)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(true)
    }
}

// Every counted line and branch is tested: anything that cannot be is an adapter above, with its reason.
tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    classDirectories.setFrom(coveredClasses)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "1.00".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                minimum = "1.00".toBigDecimal()
            }
        }
    }
}

tasks.check { dependsOn(tasks.jacocoTestCoverageVerification) }
