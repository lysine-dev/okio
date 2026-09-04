// ktlint-disable filename
/*
 * Copyright (C) 2018 Square, Inc.
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
package okio

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock as jvmWithLock
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

internal actual fun ByteArray.toUtf8String(): String = String(this, Charsets.UTF_8)

internal actual fun String.asUtf8ToByteArray(): ByteArray = toByteArray(Charsets.UTF_8)

// TODO remove if https://youtrack.jetbrains.com/issue/KT-20641 provides a better solution
actual typealias ArrayIndexOutOfBoundsException = java.lang.ArrayIndexOutOfBoundsException

actual typealias Lock = ReentrantLock

internal actual fun newLock(): Lock = ReentrantLock()
internal actual inline fun Lock.destroy() = Unit

actual inline fun <T> Lock.withLock(action: () -> T): T {
  contract {
    callsInPlace(action, InvocationKind.EXACTLY_ONCE)
  }

  return jvmWithLock(action)
}

actual typealias IOException = java.io.IOException

actual typealias ProtocolException = java.net.ProtocolException

actual typealias EOFException = java.io.EOFException

actual typealias FileNotFoundException = java.io.FileNotFoundException

actual typealias Closeable = java.io.Closeable

actual typealias Deflater = java.util.zip.Deflater

actual typealias Inflater = java.util.zip.Inflater
