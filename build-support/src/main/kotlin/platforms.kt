/*
 * Copyright (C) 2023 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.kotlin.dsl.get
import org.jetbrains.kotlin.gradle.dsl.JsModuleKind.MODULE_UMD
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

fun KotlinMultiplatformExtension.configureOrCreateOkioPlatforms() {
  jvm {
  }
  if (kmpJsEnabled) {
    configureOrCreateJsPlatforms()
  }
  if (kmpNativeEnabled) {
    configureOrCreateNativePlatforms()
  }
  if (kmpWasmEnabled) {
    configureOrCreateWasmPlatform()
  }
}

fun KotlinMultiplatformExtension.configureOrCreateNativePlatforms() {
  androidNativeArm64()
  androidNativeArm32()
  androidNativeX64()
  androidNativeX86()
  iosX64()
  iosArm64()
  iosSimulatorArm64()
  tvosX64()
  tvosArm64()
  tvosSimulatorArm64()
  watchosArm32()
  watchosArm64()
  watchosDeviceArm64()
  watchosX64()
  watchosSimulatorArm64()
  // Required to generate tests tasks: https://youtrack.jetbrains.com/issue/KT-26547
  linuxX64()
  linuxArm64()
  macosX64()
  macosArm64()
  mingwX64()
}

val androidNativeTargets = listOf(
  "androidNativeArm64",
  "androidNativeArm32",
  "androidNativeX64",
  "androidNativeX86",
)

val appleTargets = listOf(
  "iosArm64",
  "iosX64",
  "iosSimulatorArm64",
  "macosX64",
  "macosArm64",
  "tvosArm64",
  "tvosX64",
  "tvosSimulatorArm64",
  "watchosArm32",
  "watchosArm64",
  "watchosDeviceArm64",
  "watchosX64",
  "watchosSimulatorArm64",
)

val mingwTargets = listOf(
  "mingwX64",
)

val linuxTargets = listOf(
  "linuxX64",
  "linuxArm64",
)

val nativeTargets = androidNativeTargets + appleTargets + linuxTargets + mingwTargets

val wasmTargets = listOf(
  "wasmJs",
  "wasmWasi",
)

/**
 * Creates a source set for a directory that isn't already a built-in platform. Use this to create
 * custom shared directories like `nonJvmMain` or `unixMain`.
 */
fun NamedDomainObjectContainer<KotlinSourceSet>.createSourceSet(
  name: String,
  parent: KotlinSourceSet? = null,
  children: List<String> = listOf()
): KotlinSourceSet {
  val result = create(name)

  if (parent != null) {
    result.dependsOn(parent)
  }

  val suffix = when {
    name.endsWith("Main") -> "Main"
    name.endsWith("Test") -> "Test"
    else -> error("unexpected source set name: ${name}")
  }

  for (childTarget in children) {
    val childSourceSet = get("${childTarget}$suffix")
    childSourceSet.dependsOn(result)
  }

  return result
}

fun KotlinMultiplatformExtension.configureOrCreateJsPlatforms() {
  js {
    compilerOptions {
      moduleKind.set(MODULE_UMD)
      sourceMap.set(true)
    }
    nodejs {
      testTask {
        useMocha {
          timeout = "30s"
        }
      }
    }
    browser {
    }
  }
}

fun KotlinMultiplatformExtension.configureOrCreateWasmPlatform(
  js: Boolean = true,
  wasi: Boolean = true,
) {
  if (js) {
    wasmJs {
      nodejs()
    }
  }
  if (wasi) {
    wasmWasi {
      nodejs()
    }
  }
}
