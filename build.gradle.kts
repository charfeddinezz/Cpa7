import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.google.services) apply false
}

abstract class CopyDebugApkTask : DefaultTask() {
  @get:Internal
  abstract val projectRootDir: DirectoryProperty

  @TaskAction
  fun copyApk() {
    val rootDir = projectRootDir.get().asFile
    val nestedApk = File(rootDir, "app/build/outputs/apk/apk/debug/app-debug.apk")
    val standardApk = File(rootDir, "app/build/outputs/apk/debug/app-debug.apk")
    val buildOutputsApk = File(rootDir, ".build-outputs/app-debug.apk")
    val apkOutputsDir = File(rootDir, "app/build/outputs/apk")
    val destination = File(rootDir, "app-debug.apk")

    // Locate the freshly built debug APK from AGP output directory
    val sourceApk = when {
      standardApk.isFile && standardApk.length() > 0L -> standardApk
      nestedApk.isFile && nestedApk.length() > 0L -> nestedApk
      else -> {
        val discovered = if (apkOutputsDir.exists()) {
          apkOutputsDir.walkTopDown()
            .filter { it.isFile && it.name == "app-debug.apk" && it.length() > 0L }
            .maxByOrNull { it.lastModified() }
        } else null
        discovered ?: buildOutputsApk.takeIf { it.isFile && it.length() > 0L }
          ?: throw GradleException(
            "Could not find the generated debug APK under ${apkOutputsDir.path}"
          )
      }
    }

    // Ensure app/build/outputs/apk/apk/debug/app-debug.apk exists and matches the latest assembleDebug output
    nestedApk.parentFile?.mkdirs()
    if (sourceApk.canonicalPath != nestedApk.canonicalPath) {
      sourceApk.copyTo(nestedApk, overwrite = true)
    }

    // Copy app/build/outputs/apk/apk/debug/app-debug.apk to project root as app-debug.apk
    nestedApk.copyTo(destination, overwrite = true)
    destination.setReadable(true, false)

    if (!destination.isFile || destination.length() <= 0L) {
      throw GradleException("Failed to verify generated APK at ${destination.absolutePath}")
    }

    // Keep .build-outputs/app-debug.apk synchronized if directory exists
    if (buildOutputsApk.parentFile?.exists() == true &&
      destination.canonicalPath != buildOutputsApk.canonicalPath
    ) {
      destination.copyTo(buildOutputsApk, overwrite = true)
    }

    println("DEBUG APK READY AT ROOT: ${destination.absolutePath} (${destination.length()} bytes)")
  }
}

tasks.register<CopyDebugApkTask>("copyDebugApkToProjectRoot") {
  description = "Builds the debug APK via assembleDebug and copies app/build/outputs/apk/apk/debug/app-debug.apk to the project root as app-debug.apk"
  group = "build"
  dependsOn(":app:assembleDebug")
  projectRootDir.set(layout.projectDirectory)
  outputs.upToDateWhen { false }
}
