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
import java.nio.charset.StandardCharsets
import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import tapmoc.configureKotlinCompatibility

class BuildSupport : Plugin<Project> {
  override fun apply(project: Project) {
    project.configureKotlinCompatibility(project.getVersionByName("kotlinCoreLibrariesVersion"))

    // `project.configureJavaCompatibility(8)` is not used as the code would compile with JDK 8.
    // This will fail as `RealBufferedSource.kt` uses a Java API not available in Java 8:` InputStream.transferTo`
    // To use `project.configureJavaCompatibility`, the min supported Java version would need to be bumped to 11
    project.tasks.withType(KotlinCompile::class.java).configureEach {
      compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
        jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)
      }
    }
    project.tasks.withType(JavaCompile::class.java) {
      options.encoding = StandardCharsets.UTF_8.toString()
      sourceCompatibility = JavaVersion.VERSION_1_8.toString()
      targetCompatibility = JavaVersion.VERSION_1_8.toString()
    }

    project.configureDokka()
  }
}

val Project.versionCatalog: VersionCatalog
  get() = project.extensions.getByType(VersionCatalogsExtension::class.java).find("libs").get()

fun Project.getVersionByName(name: String): String {
  val version = versionCatalog.findVersion(name)
  return if (version.isPresent) {
    version.get().requiredVersion
  } else {
    throw GradleException("Could not find a version for `$name`")
  }
}
