/**
 * export.gradle.kts – Plugin packager tasks (bootstrap removed).
 * -------------------------------------------------------------
 * Apply **after** your module’s `plugins { id("com.android.library") }` block:
 *
 * ```kotlin
 * plugins { id("com.android.library") }
 * apply(from = rootProject.file("export.gradle.kts"))
 * ```
 *
 * This script avoids hard-coding AGP classes by using **reflection** to read
 * `compileSdk` and `namespace` from the `android` extension. That way the
 * script compiles even when applied via `apply(from = …)`.
 */

import org.gradle.internal.os.OperatingSystem
import java.io.File
import java.util.Properties

// -------------------------------------------------------------------------
//  Helper utilities
// -------------------------------------------------------------------------
fun sdkRootDir(): File {
    val lp = Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    lp.getProperty("sdk.dir")?.let { return File(it) }
    System.getenv("ANDROID_SDK_ROOT")?.let { return File(it) }
    System.getenv("ANDROID_HOME")?.let { return File(it) }
    error("Android SDK not found. Set sdk.dir in local.properties or ANDROID_SDK_ROOT.")
}

fun latestBuildTools(sdk: File): File {
    val dir = File(sdk, "build-tools")
    val all = dir.listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.name } ?: emptyList()
    require(all.isNotEmpty()) { "No build-tools in $dir" }
    return all.first()
}

// -------------------------------------------------------------------------
//  Read properties from the android {} block via reflection
// -------------------------------------------------------------------------
val androidExt = extensions.getByName("android")

val compileSdkInt: Int = run {
    val m = androidExt.javaClass.methods.firstOrNull { it.name == "getCompileSdk" }
        ?: error("Couldn't find getCompileSdk on android extension; ensure AGP 7.0+")
    (m.invoke(androidExt) as Number).toInt()
}

val namespaceStr: String = run {
    val getter = androidExt.javaClass.methods.firstOrNull { it.name == "getNamespace" }
    getter?.invoke(androidExt) as? String
        ?: "com.dark.${project.name.replace('-', '_')}"
}

// -------------------------------------------------------------------------
//  Common paths / files
// -------------------------------------------------------------------------
val sdkDir        = sdkRootDir()
val buildToolsDir = latestBuildTools(sdkDir)
val d8Exe         = File(buildToolsDir, if (OperatingSystem.current().isWindows) "d8.bat" else "d8")
val androidJarFile = File(sdkDir, "platforms/android-$compileSdkInt/android.jar")

val manifestSrc  = layout.projectDirectory.file("src/main/Manifest.json")
val tmpDir       = layout.buildDirectory.dir("tmp/pluginDex")
val dexOutDir    = layout.buildDirectory.dir("outputs/pluginDex")
val pluginOutDir = layout.buildDirectory.dir("outputs/plugin")

// -------------------------------------------------------------------------
//  Task 1 – extract classes.jar from release AAR / build intermediates
// -------------------------------------------------------------------------
val extractClassesJar = tasks.register<Copy>("extractClassesJar") {
    dependsOn("assembleRelease")

    val aar = layout.buildDirectory.file("outputs/aar/${project.name}-release.aar")
    from({ zipTree(aar.get().asFile) })
    include("classes.jar")
    into(tmpDir)
    doLast { println(">> Extracted classes.jar -> ${tmpDir.get().asFile.resolve("classes.jar")}") }
}

// -------------------------------------------------------------------------
//  Task 2 – run d8 to create classes.dex
// -------------------------------------------------------------------------
val makeDex = tasks.register<Exec>("makeDex") {
    dependsOn(extractClassesJar)

    doFirst {
        require(d8Exe.exists()) { "d8 not found: $d8Exe" }
        require(androidJarFile.exists()) { "android.jar not found for compileSdk $compileSdkInt" }
        dexOutDir.get().asFile.mkdirs()
    }

    val classesJar = tmpDir.get().asFile.resolve("classes.jar")
    val kotlinStdlibJar = configurations.named("releaseCompileClasspath").get()
        .filter { it.name.startsWith("kotlin-stdlib") }
        .firstOrNull()
        ?: error("kotlin-stdlib not found in releaseCompileClasspath")

    commandLine(
        d8Exe.absolutePath,
        "--release",
        "--min-api", "26",
        "--lib", androidJarFile.absolutePath,
        "--output", dexOutDir.get().asFile.absolutePath,
        classesJar.absolutePath,
        kotlinStdlibJar.absolutePath
    )

    doLast {
        val dex = dexOutDir.get().asFile.resolve("classes.dex")
        check(dex.exists()) { "d8 finished but no classes.dex produced." }
        println(">> d8 wrote: $dex")
    }
}


// -------------------------------------------------------------------------
//  Task 3 – package plugin.dex.jar
// -------------------------------------------------------------------------
val packDexJar = tasks.register<Zip>("packDexJar") {
    dependsOn(makeDex)
    archiveFileName.set("plugin.dex.jar")
    destinationDirectory.set(dexOutDir)
    from(dexOutDir) { include("classes.dex") }
    doLast { println(">> Created plugin.dex.jar") }
}

// -------------------------------------------------------------------------
//  Task 4 – copy Manifest.json into output dir
// -------------------------------------------------------------------------
val copyManifest = tasks.register<Copy>("copyManifest") {
    dependsOn(packDexJar)
    from(manifestSrc)
    into(pluginOutDir)
    rename { "Manifest.json" }
    doFirst {
        check(manifestSrc.asFile.exists()) {
            "Manifest.json not found at ${manifestSrc.asFile}. Place it at src/main/Manifest.json."
        }
    }
    doLast { println(">> Copied manifest to ${pluginOutDir.get().asFile}") }
}

// -------------------------------------------------------------------------
//  Task 5 – final distributable zip
// -------------------------------------------------------------------------
val packagePluginZip = tasks.register<Zip>("packagePluginZip") {
    dependsOn(copyManifest)
    group = "build"
    description = "Builds a distributable plugin ZIP."

    archiveFileName.set("${project.name}-plugin.zip")
    destinationDirectory.set(pluginOutDir)

    from(dexOutDir)    { include("plugin.dex.jar") }
    from(pluginOutDir) { include("Manifest.json") }

    eachFile { path = name } // flatten
    includeEmptyDirs = false

    doLast {
        println(">> Plugin package: ${destinationDirectory.get().asFile.resolve(archiveFileName.get())}")
    }
}

// -------------------------------------------------------------------------
//  Convenience aliases
// -------------------------------------------------------------------------

tasks.register("buildPluginDexJar") {
    group = "build"
    description = "Builds plugin.dex.jar (jar containing classes.dex)."
    dependsOn(packDexJar)
}

tasks.register("buildPluginZip") {
    group = "build"
    description = "Builds the final plugin zip (dex + manifest)."
    dependsOn(packagePluginZip)
}