/*
 * Copyright (C) 2018 Square, Inc. and others.
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
@file:Suppress(
  "CANNOT_OVERRIDE_INVISIBLE_MEMBER",
  "INVISIBLE_MEMBER",
  "INVISIBLE_REFERENCE",
)

package com.squareup.okio.benchmarks

import okio.internal.commonAsUtf8ToByteArray
import okio.internal.commonToUtf8String

// Necessary to make an invisible functions visible to Java.
object BenchmarkUtils {
  @JvmStatic
  fun ByteArray.decodeUtf8(): String {
    return commonToUtf8String()
  }

  @JvmStatic
  fun String.encodeUtf8(): ByteArray {
    return commonAsUtf8ToByteArray()
  }
}
