plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "okio-parent"

includeBuild("build-support")

include(":okio")
include(":okio-assetfilesystem")
include(":okio-bom")
include(":okio-fakefilesystem")
if (System.getProperty("kjs", "true").toBoolean()) {
  include(":okio-nodefilesystem")
}
include(":okio-testing-support")
include(":okio:jvm:jmh")
if (System.getProperty("kwasm", "true").toBoolean()) {
  include(":okio-wasifilesystem")
}
include(":samples")

// The Android test module doesn't work in IntelliJ. Use Android Studio or the command line.
if (System.getProperties().containsKey("android.injected.invoked.from.ide") ||
  System.getenv("ANDROID_HOME") != null) {
  include(":android-test")
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
