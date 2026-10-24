plugins {
  kotlin("multiplatform")
  id("com.gradleup.tapmoc")
}

kotlin {
  jvm {
    binaries {
      executable {
        mainClass.set(System.getProperty("mainClass"))
      }
    }
  }
  sourceSets {
    commonMain {
      dependencies {
        implementation(projects.okio)
      }
    }
    val jvmTest by getting {
      dependencies {
        implementation(libs.test.assertk)
        implementation(libs.test.junit5)
        implementation(projects.okioTestingSupport)
      }
    }
  }
}

tapmoc {
  java(8)
  kotlin(project.getVersionByName("kotlinCoreLibrariesVersion"))
  checkDependencies()
}
