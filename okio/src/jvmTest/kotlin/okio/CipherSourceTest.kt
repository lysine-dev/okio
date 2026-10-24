/*
 * Copyright (C) 2020 Square, Inc. and others.
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

import app.cash.burst.Burst
import kotlin.random.Random
import org.junit.jupiter.api.Test

@Burst
class CipherSourceTest(
  private val cipherAlgorithm: CipherAlgorithm,
) {
  @Test
  fun encrypt() {
    val random = Random(787679144228763091)
    val cipherFactory = cipherAlgorithm.createCipherFactory(random)
    val data = random.nextBytes(32)

    val buffer = Buffer().apply { write(data) }
    val cipherSource = buffer.cipherSource(cipherFactory.encrypt)
    val actualEncryptedData = cipherSource.buffer().use { it.readByteArray() }

    val expectedEncryptedData = cipherFactory.encrypt.doFinal(data)
    assertArrayEquals(expectedEncryptedData, actualEncryptedData)
  }

  @Test
  fun encryptEmpty() {
    val random = Random(1057830944394705953)
    val cipherFactory = cipherAlgorithm.createCipherFactory(random)
    val data = ByteArray(0)

    val buffer = Buffer()
    val cipherSource = buffer.cipherSource(cipherFactory.encrypt)
    val actualEncryptedData = cipherSource.buffer().use { it.readByteArray() }

    val expectedEncryptedData = cipherFactory.encrypt.doFinal(data)
    assertArrayEquals(expectedEncryptedData, actualEncryptedData)
  }

  @Test
  fun encryptLarge() {
    val random = Random(8185922876836480815)
    val cipherFactory = cipherAlgorithm.createCipherFactory(random)
    val data = random.nextBytes(Segment.SIZE * 16 + Segment.SIZE / 2)

    val buffer = Buffer().apply { write(data) }
    val cipherSource = buffer.cipherSource(cipherFactory.encrypt)
    val actualEncryptedData = cipherSource.buffer().use { it.readByteArray() }

    val expectedEncryptedData = cipherFactory.encrypt.doFinal(data)
    assertArrayEquals(expectedEncryptedData, actualEncryptedData)
  }

  @Test
  fun encryptSingleByteSource() {
    val random = Random(6085265142433950622)
    val cipherFactory = cipherAlgorithm.createCipherFactory(random)
    val data = random.nextBytes(32)

    val buffer = Buffer().apply { write(data) }
    val cipherSource = buffer.emitSingleBytes().cipherSource(cipherFactory.encrypt)
    val actualEncryptedData = cipherSource.buffer().use { it.readByteArray() }

    val expectedEncryptedData = cipherFactory.encrypt.doFinal(data)
    assertArrayEquals(expectedEncryptedData, actualEncryptedData)
  }

  /** Only relevant for algorithms which handle padding. */
  @Test
  fun encryptPaddingRequired() {
    val random = Random(4190481737015278225)
    val cipherFactory = cipherAlgorithm.createCipherFactory(random)
    val blockSize = cipherFactory.blockSize
    val dataSize = blockSize * 4 + if (cipherAlgorithm.padding) blockSize / 2 else 0
    val data = random.nextBytes(dataSize)

    val buffer = Buffer().apply { write(data) }
    val cipherSource = buffer.cipherSource(cipherFactory.encrypt)
    val actualEncryptedData = cipherSource.buffer().use { it.readByteArray() }

    val expectedEncryptedData = cipherFactory.encrypt.doFinal(data)
    assertArrayEquals(expectedEncryptedData, actualEncryptedData)
  }

  @Test
  fun decrypt() {
    val random = Random(8067587635762239433)
    val cipherFactory = cipherAlgorithm.createCipherFactory(random)
    val expectedData = random.nextBytes(32)
    val encryptedData = cipherFactory.encrypt.doFinal(expectedData)

    val buffer = Buffer().apply { write(encryptedData) }
    val cipherSource = buffer.cipherSource(cipherFactory.decrypt)
    val actualData = cipherSource.buffer().use { it.readByteArray() }

    assertArrayEquals(expectedData, actualData)
  }

  @Test
  fun decryptEmpty() {
    val random = Random(8722996896871347396)
    val cipherFactory = cipherAlgorithm.createCipherFactory(random)
    val expectedData = ByteArray(0)
    val encryptedData = cipherFactory.encrypt.doFinal(expectedData)

    val buffer = Buffer().apply { write(encryptedData) }
    val cipherSource = buffer.cipherSource(cipherFactory.decrypt)
    val actualData = cipherSource.buffer().use { it.readByteArray() }

    assertArrayEquals(expectedData, actualData)
  }

  @Test
  fun decryptLarge() {
    val random = Random(4007116131070653181)
    val cipherFactory = cipherAlgorithm.createCipherFactory(random)
    val expectedData = random.nextBytes(Segment.SIZE * 16 + Segment.SIZE / 2)
    val encryptedData = cipherFactory.encrypt.doFinal(expectedData)

    val buffer = Buffer().apply { write(encryptedData) }
    val cipherSource = buffer.cipherSource(cipherFactory.decrypt)
    val actualData = cipherSource.buffer().use { it.readByteArray() }

    assertArrayEquals(expectedData, actualData)
  }

  @Test
  fun decryptSingleByteSource() {
    val random = Random(1555017938547616655)
    val cipherFactory = cipherAlgorithm.createCipherFactory(random)
    val expectedData = random.nextBytes(32)
    val encryptedData = cipherFactory.encrypt.doFinal(expectedData)

    val buffer = Buffer().apply { write(encryptedData) }
    val cipherSource = buffer.emitSingleBytes().cipherSource(cipherFactory.decrypt)
    val actualData = cipherSource.buffer().use {
      it.readByteArray()
    }

    assertArrayEquals(expectedData, actualData)
  }

  /** Only relevant for algorithms which handle padding. */
  @Test
  fun decryptPaddingRequired() {
    val random = Random(5717921427007554469)
    val cipherFactory = cipherAlgorithm.createCipherFactory(random)
    val blockSize = cipherFactory.blockSize
    val dataSize = blockSize * 4 + if (cipherAlgorithm.padding) blockSize / 2 else 0
    val expectedData = random.nextBytes(dataSize)
    val encryptedData = cipherFactory.encrypt.doFinal(expectedData)

    val buffer = Buffer().apply { write(encryptedData) }
    val cipherSource = buffer.cipherSource(cipherFactory.decrypt)
    val actualData = cipherSource.buffer().use { it.readByteArray() }

    assertArrayEquals(expectedData, actualData)
  }

  private fun Source.emitSingleBytes(): Source =
    SingleByteSource(this)

  private class SingleByteSource(source: Source) : ForwardingSource(source) {
    override fun read(sink: Buffer, byteCount: Long): Long =
      delegate.read(sink, 1L)
  }
}
