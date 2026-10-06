import org.jetbrains.kotlin.gradle.tasks.KotlinCompileTool

plugins {
  kotlin("multiplatform")
  id("build-support")
}

kotlin {
  configureOrCreateOkioPlatforms()

  sourceSets {
    all {
      languageSettings.apply {
        optIn("kotlin.time.ExperimentalTime")
      }
    }

    val commonMain by getting {
      dependencies {
        api(projects.okio)
        api(libs.kotlin.test)
        api(libs.test.assertk)
        api(libs.test.balloon.core)
      }
    }

    val nonWasmMain by creating {
      dependsOn(commonMain)
      dependencies {
        api(libs.kotlin.time)
        implementation(projects.okioFakefilesystem)
      }
    }

    val zlibMain by creating {
      dependsOn(commonMain)
    }

    if (kmpJsEnabled) {
      val jsMain by getting {
        dependsOn(nonWasmMain)
      }
    }

    val jvmMain by getting {
      dependsOn(nonWasmMain)
      dependsOn(zlibMain)
      dependencies {
        // On the JVM the kotlin-test library resolves to one of three implementations based on
        // which testing framework is in use. JUnit is used downstream, but Gradle can't know that
        // here and thus fails to select a variant automatically. Declare it manually instead.
        api(libs.kotlin.test.junit5)
      }
    }

    if (kmpNativeEnabled) {
      createSourceSet("nativeMain", children = nativeTargets)
        .also { nativeMain ->
          nativeMain.dependsOn(nonWasmMain)
          nativeMain.dependsOn(zlibMain)
        }
    }

    if (kmpWasmEnabled) {
      createSourceSet("wasmMain", children = wasmTargets)
        .also { wasmMain ->
          wasmMain.dependsOn(commonMain)
        }
      val wasmWasiMain by getting {
        dependencies {
          implementation(project(":okio-wasifilesystem"))
        }
      }
    }
  }
}

/*
 * AbstractFileSystemTest expects a file with specific metadata:
 *
 *   path: build/AbstractFileSystemTestFiles/metadata.txt
 *   createdAt: 2026-01-01T01:01:01Z
 *   lastModifiedAt: 2026-02-02T02:02:02Z
 *
 * It would be nicer to do this in the test itself with exec(), but we don't yet have a handy
 * multiplatform API for that.
 */
val touchAbstractFileSystemTestFilesCreatedAt by tasks.registering(Exec::class) {
  val sampleFile = project.file("build/AbstractFileSystemTestFiles/metadata.txt")
  doFirst {
    sampleFile.parentFile.mkdirs()
  }
  environment("TZ" to "UTC")
  commandLine("touch", "-t", "202601010101.01", sampleFile.path)
}
val touchAbstractFileSystemTestFilesModifiedAt by tasks.registering(Exec::class) {
  dependsOn(touchAbstractFileSystemTestFilesCreatedAt)
  val sampleFile = project.file("build/AbstractFileSystemTestFiles/metadata.txt")
  environment("TZ" to "UTC")
  commandLine("touch", "-t", "202602020202.02", sampleFile.path)
}
tasks.withType<KotlinCompileTool> {
  dependsOn(
    touchAbstractFileSystemTestFilesCreatedAt,
    touchAbstractFileSystemTestFilesModifiedAt,
  )
}
