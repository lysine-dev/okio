/*
 * Copyright (C) 2019 Square, Inc.
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
package okio

import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.aroundEachTest
import de.infix.testBalloon.framework.core.testScope
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import okio.ByteString.Companion.toByteString
import okio.Path.Companion.toPath

fun Char.repeat(count: Int): String {
  return toString().repeat(count)
}

fun assertArrayEquals(a: ByteArray, b: ByteArray) {
  assertEquals(a.contentToString(), b.contentToString())
}

fun randomBytes(length: Int, seed: Int = 0): ByteString {
  val random = Random(seed)
  val randomBytes = ByteArray(length)
  random.nextBytes(randomBytes)
  return ByteString.of(*randomBytes)
}

fun randomToken(length: Int) = Random.nextBytes(length).toByteString(0, length).hex()

expect fun isBrowser(): Boolean

/**
 * Returns true if the host file system probably exposes metadata like file creation time.
 *
 * The file system that GitHub actions gives us doesn't do anything when we `touch` a file.
 */
val fileSystemHasGoodMetadata: Boolean
  get() = getEnv("GITHUB_WORKSPACE") == null

val FileMetadata.createdAt: Instant?
  get() {
    val createdAt = createdAtMillis ?: return null
    return Instant.fromEpochMilliseconds(createdAt)
  }

val FileMetadata.lastModifiedAt: Instant?
  get() {
    val lastModifiedAt = lastModifiedAtMillis ?: return null
    return Instant.fromEpochMilliseconds(lastModifiedAt)
  }

val FileMetadata.lastAccessedAt: Instant?
  get() {
    val lastAccessedAt = lastAccessedAtMillis ?: return null
    return Instant.fromEpochMilliseconds(lastAccessedAt)
  }

fun fromIso8601String(iso8601String: String): Instant =
  Instant.fromEpochMilliseconds(Instant.parse(iso8601String).toEpochMilliseconds())

expect val FileSystem.isFakeFileSystem: Boolean

expect val FileSystem.allowSymlinks: Boolean

expect val FileSystem.allowReadsWhileWriting: Boolean

expect var FileSystem.workingDirectory: Path

expect fun getEnv(name: String): String?

val okioRoot: Path by lazy {
  getEnv("OKIO_ROOT")!!.toPath()
}

fun TestConfig.withTestTimeout(timeout: Duration) = this
  .testScope(isEnabled = false)
  .aroundEachTest { action ->
    try {
      withTimeout(timeout) {
        action()
      }
    } catch (cancellation: TimeoutCancellationException) {
      throw AssertionError("$cancellation", cancellation)
    }
  }
