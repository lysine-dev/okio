/*
 * Copyright (C) 2024 Square, Inc.
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
@file:JvmName("-InflaterSourceExtensions")

package okio

import kotlin.jvm.JvmName

/**
 * A source that uses [DEFLATE](https://tools.ietf.org/html/rfc1951) to decompress data read from
 * another source.
 */
expect class InflaterSource
/**
 * This internal constructor shares a buffer with its trusted caller. In general, we can't share a
 * `BufferedSource` because the inflater holds input bytes until they are inflated.
 */
internal constructor(
  source: BufferedSource,
  inflater: Inflater,
) : Source {
  constructor(source: Source, inflater: Inflater)

  override fun read(sink: Buffer, byteCount: Long): Long
  override fun timeout(): Timeout
  override fun close()
}

/**
 * Returns an [InflaterSource] that DEFLATE-decompresses this [Source] while reading.
 *
 * @see InflaterSource
 */
inline fun Source.inflate(inflater: Inflater = Inflater()): InflaterSource =
  InflaterSource(this, inflater)
