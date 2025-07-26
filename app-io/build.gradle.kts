import com.android.build.api.dsl.LibraryExtension
import java.util.Properties
import kotlin.apply

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.mp.app_io"
    compileSdk = 36

    defaultConfig {
        minSdk = 33

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
    buildFeatures {
        compose = true
    }
}

dependencies {

    compileOnly(files("libs/plugins-release.aar"))

    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.animation)
    implementation(libs.material)

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.runtime.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

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

fun pkgToDir(pkg: String) = pkg.replace('.', '/')

// ---- Paths / providers ------------------------------------------------

val sdkDir = sdkRootDir()
val buildToolsDir = latestBuildTools(sdkDir)
val d8Exe = File(buildToolsDir, if (org.gradle.internal.os.OperatingSystem.current().isWindows) "d8.bat" else "d8")

// read compileSdk and namespace from the Android extension
val androidExt = extensions.getByName("android") as LibraryExtension
val compileSdkInt = androidExt.compileSdk
val namespaceStr = androidExt.namespace ?: "com.dark.${project.name.replace('-', '_')}"

val androidJarFile = File(sdkDir, "platforms/android-$compileSdkInt/android.jar")

val srcMainDir = layout.projectDirectory.dir("src/main")
val javaPkgDir = layout.projectDirectory.dir("src/main/java/${pkgToDir(namespaceStr)}")
val libsDir = layout.projectDirectory.dir("libs")
val manifestSrc = layout.projectDirectory.file("src/main/Manifest.json")

val tmpDir = layout.buildDirectory.dir("tmp/pluginDex")
val dexOutDir = layout.buildDirectory.dir("outputs/pluginDex")
val pluginOutDir = layout.buildDirectory.dir("outputs/plugin")

// ---- Bootstrap: create missing files/dirs -----------------------------

val bootstrapPlugin by tasks.registering {
    group = "setup"
    description = "Creates default Manifest.json, package dirs and a sample plugin class if missing."
    doLast {
        // dirs
        srcMainDir.asFile.mkdirs()
        javaPkgDir.asFile.mkdirs()
        libsDir.asFile.mkdirs()

        // Manifest.json
        val mf = manifestSrc.asFile
        if (!mf.exists()) {
            mf.parentFile.mkdirs()
            mf.writeText(
                """
                {
                  "name": "${project.name}",
                  "mainClass": "$namespaceStr.DemoPlugin"
                }
                """.trimIndent()
            )
            println(">> Wrote default Manifest.json -> ${mf.path}")
        }

        // Sample Kotlin plugin
        val kFile = File(javaPkgDir.asFile, "DemoPlugin.kt")
        if (!kFile.exists()) {
            kFile.writeText(
                """
                package $namespaceStr

                import android.content.Context
                import androidx.compose.material3.Button
                import androidx.compose.material3.Text
                import androidx.compose.runtime.Composable
                import com.dark.plugins.engine.PluginApi
                import com.dark.plugins.engine.PluginInfo
                import com.dark.plugins.engine.ComposableBlock

                class DemoPlugin(private val context: Context) : PluginApi(context) {

                    override fun getPluginInfo(): PluginInfo =
                        PluginInfo(name = "DemoPlugin", description = "Sample plugin")

                    override fun onCreate(data: Any) {
                        // initialize if needed
                    }

                    @Composable
                    override fun AppContent() {
                        Button(onClick = { /* do something */ }) {
                            Text("Hello from DemoPlugin")
                        }
                    }

                    override fun content(): ComposableBlock = { AppContent() }
                }
                """.trimIndent()
            )
            println(">> Wrote sample plugin class -> ${kFile.path}")
        }
    }
}

// ---- Build steps ------------------------------------------------------

// 1) Extract classes.jar from this module's AAR
val extractClassesJar by tasks.registering(Copy::class) {
    dependsOn(bootstrapPlugin)
    dependsOn("assembleRelease")
    val aar = layout.buildDirectory.file("outputs/aar/${project.name}-release.aar")
    from({ zipTree(aar.get().asFile) })
    include("classes.jar")
    into(tmpDir)
    doLast { println(">> Extracted: ${tmpDir.get().asFile.resolve("classes.jar")}") }
}

// 2) Run d8 to create classes.dex
val makeDex by tasks.registering(Exec::class) {
    dependsOn(extractClassesJar)
    doFirst {
        require(d8Exe.exists()) { "d8 not found: $d8Exe" }
        require(androidJarFile.exists()) { "android.jar not found: $androidJarFile (install platforms;android-$compileSdkInt)" }
        dexOutDir.get().asFile.mkdirs()
    }
    val classesJar = tmpDir.get().asFile.resolve("classes.jar")
    commandLine(
        d8Exe.absolutePath,
        "--release",
        "--min-api", "26",
        "--lib", androidJarFile.absolutePath,
        "--output", dexOutDir.get().asFile.absolutePath,
        classesJar.absolutePath
    )
    doLast {
        val dex = dexOutDir.get().asFile.resolve("classes.dex")
        check(dex.exists()) { "d8 finished but no classes.dex was produced. Re-run with --info." }
        println(">> d8 wrote: $dex")
    }
}

// 3) Pack classes.dex into plugin.dex.jar
val packDexJar by tasks.registering(Zip::class) {
    dependsOn(makeDex)
    archiveFileName.set("plugin.dex.jar")
    destinationDirectory.set(dexOutDir)
    from(dexOutDir) { include("classes.dex") }
    doLast { println(">> Created: ${destinationDirectory.get().asFile.resolve(archiveFileName.get())}") }
}

// 4) Copy/normalize Manifest.json to output
val copyManifest by tasks.registering(Copy::class) {
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

// 5) Final distributable ZIP
val packagePluginZip by tasks.registering(Zip::class) {
    dependsOn(copyManifest)
    archiveFileName.set("${project.name}-plugin.zip")
    destinationDirectory.set(pluginOutDir)

    from(dexOutDir) { include("plugin.dex.jar") }
    from(pluginOutDir) { include("Manifest.json") }

    // put both files at the root of the zip
    eachFile { path = name }
    includeEmptyDirs = false

    doLast {
        println(">> Plugin package: ${destinationDirectory.get().asFile.resolve(archiveFileName.get())}")
    }
}

// Convenience aliases
tasks.register("buildPluginDexJar") {
    group = "build"
    description = "Builds plugin.dex.jar (jar containing classes.dex)."
    dependsOn(packDexJar)
}
tasks.register("buildPluginZip") {
    group = "build"
    description = "Builds a distributable plugin zip with Manifest.json and plugin.dex.jar."
    dependsOn(packagePluginZip)
}