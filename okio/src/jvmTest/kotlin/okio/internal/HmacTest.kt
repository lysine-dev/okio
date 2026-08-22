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

import app.cash.burst.Burst
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random
import okio.ByteString
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * Check the [Hmac] implementation against the reference [Mac] JVM implementation.
 */
@Burst
class HmacTest(
  keySize: KeySize,
  dataSize: DataSize,
  algorithm: Algorithm,
) {
  private val random = Random(682741861446)
  private val key = random.nextBytes(keySize.size)
  private val bytes = random.nextBytes(dataSize.size)
  private val mac = algorithm.HmacFactory(ByteString(key))
  private val expected = hmac(algorithm.algorithmName, key, bytes)

  @Test
  fun hmac() {
    mac.update(bytes)
    val hmacValue = mac.digest()

    Assertions.assertArrayEquals(expected, hmacValue)
  }

  @Test
  fun hmacBytes() {
    for (byte in bytes) {
      mac.update(byteArrayOf(byte))
    }
    val hmacValue = mac.digest()

    Assertions.assertArrayEquals(expected, hmacValue)
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

private fun hmac(algorithm: String, key: ByteArray, bytes: ByteArray) =
  Mac.getInstance(algorithm).apply { init(SecretKeySpec(key, algorithm)) }.doFinal(bytes)
