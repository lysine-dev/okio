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

import de.infix.testBalloon.framework.core.testSuite
import kotlin.random.Random

val CipherSinkTest by testSuite {
  for (cipherAlgorithm in CipherAlgorithm.entries) {
    testSuite(cipherAlgorithm.name) {
      test("encrypt") {
        val random = Random(8912860393601532863)
        val cipherFactory = cipherAlgorithm.createCipherFactory(random)
        val data = random.nextBytes(32)

        val buffer = Buffer()
        val cipherSink = buffer.cipherSink(cipherFactory.encrypt)
        cipherSink.buffer().use { it.write(data) }
        val actualEncryptedData = buffer.readByteArray()

        val expectedEncryptedData = cipherFactory.encrypt.doFinal(data)
        assertArrayEquals(expectedEncryptedData, actualEncryptedData)
      }

      test("encryptEmpty") {
        val random = Random(3014415396541767201)
        val cipherFactory = cipherAlgorithm.createCipherFactory(random)
        val data = ByteArray(0)

        val buffer = Buffer()
        val cipherSink = buffer.cipherSink(cipherFactory.encrypt)
        cipherSink.buffer().close()
        val actualEncryptedData = buffer.readByteArray()

        val expectedEncryptedData = cipherFactory.encrypt.doFinal(data)
        assertArrayEquals(expectedEncryptedData, actualEncryptedData)
      }

      test("encryptLarge") {
        val random = Random(4800508322764694019)
        val cipherFactory = cipherAlgorithm.createCipherFactory(random)
        val data = random.nextBytes(Segment.SIZE * 16 + Segment.SIZE / 2)

        val buffer = Buffer()
        val cipherSink = buffer.cipherSink(cipherFactory.encrypt)
        cipherSink.buffer().use { it.write(data) }
        val actualEncryptedData = buffer.readByteArray()

        val expectedEncryptedData = cipherFactory.encrypt.doFinal(data)
        assertArrayEquals(expectedEncryptedData, actualEncryptedData)
      }

      test("encryptSingleByteWrite") {
        val random = Random(4374178522096702290)
        val cipherFactory = cipherAlgorithm.createCipherFactory(random)
        val data = random.nextBytes(32)

        val buffer = Buffer()
        val cipherSink = buffer.cipherSink(cipherFactory.encrypt)
        cipherSink.buffer().use {
          data.forEach { byte ->
            it.writeByte(byte.toInt())
          }
        }
        val actualEncryptedData = buffer.readByteArray()

        val expectedEncryptedData = cipherFactory.encrypt.doFinal(data)
        assertArrayEquals(expectedEncryptedData, actualEncryptedData)
      }

      /** Only relevant for algorithms which handle padding. */
      test("encryptPaddingRequired") {
        val random = Random(7515202505362968404)
        val cipherFactory = cipherAlgorithm.createCipherFactory(random)
        val blockSize = cipherFactory.blockSize
        val dataSize = blockSize * 4 + if (cipherAlgorithm.padding) blockSize / 2 else 0
        val data = random.nextBytes(dataSize)

        val buffer = Buffer()
        val cipherSink = buffer.cipherSink(cipherFactory.encrypt)
        cipherSink.buffer().use { it.write(data) }
        val actualEncryptedData = buffer.readByteArray()

        val expectedEncryptedData = cipherFactory.encrypt.doFinal(data)
        assertArrayEquals(expectedEncryptedData, actualEncryptedData)
      }

      test("decrypt") {
        val random = Random(488375923060579687)
        val cipherFactory = cipherAlgorithm.createCipherFactory(random)
        val expectedData = random.nextBytes(32)
        val encryptedData = cipherFactory.encrypt.doFinal(expectedData)

        val buffer = Buffer()
        val cipherSink = buffer.cipherSink(cipherFactory.decrypt)
        cipherSink.buffer().use { it.write(encryptedData) }
        val actualData = buffer.readByteArray()

        assertArrayEquals(expectedData, actualData)
      }

      test("decryptEmpty") {
        val random = Random(-9063010151894844496)
        val cipherFactory = cipherAlgorithm.createCipherFactory(random)
        val expectedData = ByteArray(0)
        val encryptedData = cipherFactory.encrypt.doFinal(expectedData)

        val buffer = Buffer()
        val cipherSink = buffer.cipherSink(cipherFactory.decrypt)
        cipherSink.buffer().use { it.write(encryptedData) }
        val actualData = buffer.readByteArray()

        assertArrayEquals(expectedData, actualData)
      }

      test("decryptLarge") {
        val random = Random(993064087526004362)
        val cipherFactory = cipherAlgorithm.createCipherFactory(random)
        val expectedData = random.nextBytes(Segment.SIZE * 16 + Segment.SIZE / 2)
        val encryptedData = cipherFactory.encrypt.doFinal(expectedData)

        val buffer = Buffer()
        val cipherSink = buffer.cipherSink(cipherFactory.decrypt)
        cipherSink.buffer().use { it.write(encryptedData) }
        val actualData = buffer.readByteArray()

        assertArrayEquals(expectedData, actualData)
      }

      test("decryptSingleByteWrite") {
        val random = Random(2621474675920878975)
        val cipherFactory = cipherAlgorithm.createCipherFactory(random)
        val expectedData = random.nextBytes(32)
        val encryptedData = cipherFactory.encrypt.doFinal(expectedData)

        val buffer = Buffer()
        val cipherSink = buffer.cipherSink(cipherFactory.decrypt)
        cipherSink.buffer().use {
          encryptedData.forEach { byte ->
            it.writeByte(byte.toInt())
          }
        }
        val actualData = buffer.readByteArray()

        assertArrayEquals(expectedData, actualData)
      }

      /** Only relevant for algorithms which handle padding. */
      test("decryptPaddingRequired") {
        val random = Random(7689061926945836562)
        val cipherFactory = cipherAlgorithm.createCipherFactory(random)
        val blockSize = cipherFactory.blockSize
        val dataSize = blockSize * 4 + if (cipherAlgorithm.padding) blockSize / 2 else 0
        val expectedData = random.nextBytes(dataSize)
        val encryptedData = cipherFactory.encrypt.doFinal(expectedData)

        val buffer = Buffer()
        val cipherSink = buffer.cipherSink(cipherFactory.decrypt)
        cipherSink.buffer().use { it.write(encryptedData) }
        val actualData = buffer.readByteArray()

        assertArrayEquals(expectedData, actualData)
      }
    }
  }
}
