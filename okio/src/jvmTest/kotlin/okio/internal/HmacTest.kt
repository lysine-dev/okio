/*
 * Copyright (C) 2020 Square, Inc.
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
package okio.internal

import de.infix.testBalloon.framework.core.testSuite
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random
import okio.ByteString
import org.junit.jupiter.api.Assertions

/**
 * Check the [Hmac] implementation against the reference [Mac] JVM implementation.
 */

val HmacTest by testSuite {
  fun hmac(algorithm: String, key: ByteArray, bytes: ByteArray) =
    Mac.getInstance(algorithm).apply { init(SecretKeySpec(key, algorithm)) }.doFinal(bytes)

  val testCases = KeySize.entries.flatMap { k ->
    DataSize.entries.flatMap { d ->
      Algorithm.entries.map { a -> Triple(k, d, a) }
    }
  }

  for ((keySize, dataSize, algorithm) in testCases) {
    testSuite("${keySize}_${dataSize}_$algorithm") {
      testFixture {
        object {
          val random = Random(682741861446)
          val key = random.nextBytes(keySize.size)
          val bytes = random.nextBytes(dataSize.size)
          val mac = algorithm.HmacFactory(ByteString(key))
          val expected = hmac(algorithm.algorithmName, key, bytes)
        }
      } asContextForEach {
        test("hmac") {
          mac.update(bytes)
          val hmacValue = mac.digest()
          Assertions.assertArrayEquals(expected, hmacValue)
        }

        test("hmacBytes") {
          for (byte in bytes) {
            mac.update(byteArrayOf(byte))
          }
          val hmacValue = mac.digest()

          Assertions.assertArrayEquals(expected, hmacValue)
        }
      }
    }
  }
}

enum class KeySize(val size: Int) {
  K8(8), K32(32), K48(48), K64(64), K128(128), K256(256),
}

enum class DataSize(val size: Int) {
  V0(0), V32(32), V64(64), V128(128), V256(256), V512(512),
}

enum class Algorithm(
  val algorithmName: String,
  internal val HmacFactory: (key: ByteString) -> Hmac,
) {
  Sha1("HmacSha1", Hmac.Companion::sha1),
  Sha256("HmacSha256", Hmac.Companion::sha256),
  Sha512("HmacSha512", Hmac.Companion::sha512),
}
