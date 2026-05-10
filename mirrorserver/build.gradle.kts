import java.io.File
import java.util.Properties

/*
 * mirrorserver — produces megingiard_mirror.dex used by Privileged Mirror.
 * The DEX is loaded by /system/bin/app_process at runtime under shell UID
 * (spawned by megingiard_privd). It uses reflection to call hidden
 * SurfaceControl APIs and MediaCodec to encode the primary display.
 */
plugins {
    java
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

fun resolveSdkRoot(): File {
    val env = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    if (env != null) return file(env)
    val localProps = rootProject.file("local.properties")
    if (localProps.exists()) {
        val props = Properties()
        localProps.inputStream().use { props.load(it) }
        val dir = props.getProperty("sdk.dir")
        if (dir != null) return file(dir)
    }
    return file("${System.getProperty("user.home")}/Library/Android/sdk")
}

val androidJarFile: File = resolveSdkRoot().resolve("platforms/android-33/android.jar")

dependencies {
    compileOnly(files(androidJarFile))
}

val dexOutputDir: File = rootProject.projectDir.resolve("app/src/main/assets")
val dexOutputFile: File = dexOutputDir.resolve("megingiard_mirror.dex")

abstract class DexTask : DefaultTask() {
    @get:org.gradle.api.tasks.InputFile
    abstract val inputJar: org.gradle.api.file.RegularFileProperty

    @get:org.gradle.api.tasks.OutputFile
    abstract val outputDex: org.gradle.api.file.RegularFileProperty

    @get:org.gradle.api.tasks.Internal
    abstract val sdkRoot: org.gradle.api.file.DirectoryProperty

    @get:javax.inject.Inject
    abstract val execOps: org.gradle.process.ExecOperations

    @org.gradle.api.tasks.TaskAction
    fun run() {
        val buildToolsDir = sdkRoot.get().asFile.resolve("build-tools")
        val newest = buildToolsDir.listFiles()?.filter { it.isDirectory }?.maxByOrNull { it.name }
            ?: error("No build-tools installed under $buildToolsDir")
        val d8 = newest.resolve("d8")
        check(d8.exists()) { "d8 not found at $d8" }

        val out = outputDex.get().asFile
        out.parentFile.mkdirs()
        val tmpDir = temporaryDir.apply { mkdirs() }
        execOps.exec {
            commandLine(
                d8.absolutePath,
                "--min-api", "33",
                "--output", tmpDir.absolutePath,
                inputJar.get().asFile.absolutePath
            )
        }
        val produced = tmpDir.resolve("classes.dex")
        check(produced.exists()) { "d8 produced no classes.dex in $tmpDir" }
        if (out.exists()) out.delete()
        produced.copyTo(out, overwrite = true)
    }
}

val dex by tasks.registering(DexTask::class) {
    dependsOn(tasks.named("jar"))
    inputJar.set(tasks.named<Jar>("jar").flatMap { it.archiveFile })
    outputDex.set(dexOutputFile)
    sdkRoot.set(resolveSdkRoot())
}

tasks.named("build") { dependsOn(dex) }
