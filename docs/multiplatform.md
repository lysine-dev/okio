Multiplatform
=============

Okio is a [Kotlin Multiplatform][kotlin_multiplatform] project. We're still completing our feature
coverage.


### Compression (Deflater, Inflater, Gzip)

JVM and native platforms.


### Concurrency (Pipe, Timeouts, Throttler)

JVM-only.

Timeout is on all platforms, but only the JVM has a useful implementation.


### Core (Buffer, ByteString, Source, Sink)

Available on all platforms.


### File System

Available on all platforms. For JavaScript this requires [Node.js][node_js].


### Hashing

Okio includes Kotlin implementations of MD5, SHA-1, SHA-256, and SHA-512. This includes both hash
functions and HMAC functions.

Okio uses the built-in implementations of these functions on the JVM.


[kotlin_multiplatform]: https://kotlinlang.org/docs/reference/multiplatform.html
[node_js]: https://nodejs.org/api/fs.html

## Gradle configuration

```kotlin
// build.gradle.kts
kotlin {
    sourceSets {
        val okioVersion = "3.XXX"
        val commonMain by getting {
            dependencies {
                implementation("com.squareup.okio:okio:$okioVersion")
            }
        }
        val jsMain by getting {
            dependencies {
                implementation("com.squareup.okio:okio-nodefilesystem:$okioVersion")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation("com.squareup.okio:okio-fakefilesystem:$okioVersion")
            }
        }
    }
}
```
