import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    alias(libs.plugins.errorprone)
}

version = "0.1.0"

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(25)) }
}

repositories { mavenCentral() }

// The same pinning as the mod itself: every resolved dependency is in this project's gradle.lockfile.
dependencyLocking {
    lockAllConfigurations()
    lockMode.set(LockMode.STRICT)
}

dependencies {
    compileOnly(fileTree(rootProject.file("libs/compile")) { include("*.jar") })
    errorprone(libs.errorprone.core)
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
    options.encoding = "UTF-8"
    // A test driver, but held to the same compiler bar: every warning fails the build.
    options.compilerArgs.addAll(listOf("-Xlint:all,-classfile,-processing,-serial", "-Werror"))
    options.errorprone {
        disableWarningsInGeneratedCode.set(true)
        disable("StringConcatToTextBlock")
        val mixinHandlers = listOf(
            "org.spongepowered.asm.mixin.injection.Inject",
            "org.spongepowered.asm.mixin.injection.ModifyVariable",
            "org.spongepowered.asm.mixin.injection.Redirect",
            "org.spongepowered.asm.mixin.gen.Accessor",
            "org.spongepowered.asm.mixin.gen.Invoker").joinToString(",")
        option("UnusedMethod:ExemptingMethodAnnotations", mixinHandlers)
        option("Unused:methodAnnotationsExemptingParameters", mixinHandlers)
    }
}
