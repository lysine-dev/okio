/*
 * Copyright (c) 2026 Okio Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.dokka.gradle.DokkaExtension
import org.jetbrains.dokka.gradle.DokkaPlugin

val dokkaEnabled = System.getProperty("okio.build.dokka", "false").toBoolean()

fun Project.configureRootDokka() {
  if (!dokkaEnabled) return

  apply(plugin = "org.jetbrains.dokka")

  dependencies {
    add("dokka", project(":okio"))
    add("dokka", project(":okio-assetfilesystem"))
    add("dokka", project(":okio-fakefilesystem"))
    add("dokka", project(":okio-nodefilesystem"))
    add("dokka", project(":okio-wasifilesystem"))
  }
}

fun Project.configureDokka() {
  if (!dokkaEnabled) return

  plugins.withType<DokkaPlugin> {
    extensions.configure<DokkaExtension> {
      dokkaPublications.all {
        dokkaSourceSets.configureEach {
          reportUndocumented.set(false)
          skipDeprecated.set(true)
          perPackageOption {
            matchingRegex.set("""com[.]squareup[.]okio.*""")
            suppress.set(true)
          }
          perPackageOption {
            matchingRegex.set(""".*[.]internal([.].*)?""")
            suppress.set(true)
          }
        }
      }
    }
  }
}
