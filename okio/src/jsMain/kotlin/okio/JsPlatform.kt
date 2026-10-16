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
package okio

/*
 * This file exposes Node.js `os`, `path` and `worker_threads` APIs to Kotlin/JS, using reasonable default behaviors
 * if those symbols aren't available.
 *
 * This was originally implemented using a Kotlin/JS [JsModule], but that broke browser builds
 * because modules weren't available to browsers.
 *
 * https://nodejs.org/api/os.html
 * https://github.com/browserify/path-browserify/blob/master/index.js
 * https://github.com/CoderPuppy/os-browserify/blob/master/browser.js
 */

internal actual val PLATFORM_DIRECTORY_SEPARATOR: String
  get() = (path?.sep) as? String ?: "/"

private val os: dynamic
  get() {
    return try {
      js("require('os')")
    } catch (t: Throwable) {
      null
    }
  }

private val path: dynamic
  get() {
    return try {
      js("require('path')")
    } catch (t: Throwable) {
      null
    }
  }

internal val tmpdir: String
  get() = os?.tmpdir() as? String ?: "/tmp"

private val worker_threads: dynamic
  get() {
    return try {
      js("require('worker_threads')")
    } catch (t: Throwable) {
      null
    }
  }

internal actual fun getCurrentThreadId() =
  (worker_threads?.threadId as? Int)?.toLong() ?: 0L

internal actual fun getAvailableProcessors() =
  os?.availableParallelism() as? Int ?: js("navigator.hardwareConcurrency") as? Int ?: 1
