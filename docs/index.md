Okio
====

Okio is a library that complements `java.io` and `java.nio` to make it much
easier to access, store, and process your data. It started as a component of
[OkHttp][1], the capable HTTP client included in Android. It's well-exercised
and ready to solve new problems.

ByteStrings and Buffers
-----------------------

Okio is built around two types that pack a lot of capability into a
straightforward API:

 * [**ByteString**][3] is an immutable sequence of bytes. For character data, `String`
   is fundamental. `ByteString` is String's long-lost brother, making it easy to
   treat binary data as a value. This class is ergonomic: it knows how to encode
   and decode itself as hex, base64, and UTF-8.

 * [**Buffer**][4] is a mutable sequence of bytes. Like `ArrayList`, you don't need
   to size your buffer in advance. You read and write buffers as a queue: write
   data to the end and read it from the front. There's no obligation to manage
   positions, limits, or capacities.

Internally, `ByteString` and `Buffer` do some clever things to save CPU and
memory. If you encode a UTF-8 string as a `ByteString`, it caches a reference to
that string so that if you decode it later, there's no work to do.

`Buffer` is implemented as a linked list of segments. When you move data from
one buffer to another, it _reassigns ownership_ of the segments rather than
copying the data across. This approach is particularly helpful for multithreaded
programs: a thread that talks to the network can exchange data with a worker
thread without any copying or ceremony.

Sources and Sinks
-----------------

An elegant part of the `java.io` design is how streams can be layered for
transformations like encryption and compression. Okio includes its own stream
types called [`Source`][5] and [`Sink`][6] that work like `InputStream` and
`OutputStream`, but with some key differences:

 * **Timeouts.** The streams provide access to the timeouts of the underlying
   I/O mechanism. Unlike the `java.io` socket streams, both `read()` and
   `write()` calls honor timeouts.

 * **Easy to implement.** `Source` declares three methods: `read()`, `close()`,
   and `timeout()`. There are no hazards like `available()` or single-byte reads
   that cause correctness and performance surprises.

 * **Easy to use.** Although _implementations_ of `Source` and `Sink` have only
   three methods to write, _callers_ are given a rich API with the
   [`BufferedSource`][7] and [`BufferedSink`][8] interfaces. These interfaces give you
   everything you need in one place.

 * **No artificial distinction between byte streams and char streams.** It's all
   data. Read and write it as bytes, UTF-8 strings, big-endian 32-bit integers,
   little-endian shorts; whatever you want. No more `InputStreamReader`!

 * **Easy to test.** The `Buffer` class implements both `BufferedSource` and
   `BufferedSink` so your test code is simple and clear.

Sources and sinks interoperate with `InputStream` and `OutputStream`. You can
view any `Source` as an `InputStream`, and you can view any `InputStream` as a
`Source`. Similarly for `Sink` and `OutputStream`.


Presentations
-------------

[A Few “Ok” Libraries][ok_libraries_talk] ([slides][ok_libraries_slides]): An introduction to Okio
and three libraries written with it.

[Decoding the Secrets of Binary Data][encoding_talk] ([slides][encoding_slides]): How data encoding
works and how Okio does it.

[Ok Multiplatform!][ok_multiplatform_talk] ([slides][ok_multiplatform_slides]): How we changed
Okio’s implementation language from Java to Kotlin.

[Nerding Out On Okio][apis_talk]: The story of the Okio APIs, their design and tradeoffs, as well
as implementation notes with animated marbles diagrams.


Requirements
------------

Okio 2.x supports Android 4.0.3+ (API level 15+) and Java 7+.

Okio 3.x supports Android 4.0.3+ (API level 15+) and Java 8+.

Okio depends on the [Kotlin standard library][kotlin]. It is a small library with strong
backward-compatibility.


Releases
--------

Our [change log][changelog] has release history.

```kotlin
implementation("com.squareup.okio:okio:3.17.0")
```

<details>
   <summary>Snapshot builds are also available</summary>

```kotlin
repositories {
  maven("https://central.sonatype.com/repository/maven-snapshots/")
}

dependencies {
  implementation("com.squareup.okio:okio:3.18.0-SNAPSHOT")
}
```

</details>


License
--------

    Copyright 2013 Square, Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

 [1]: https://github.com/lysine-dev/okhttp
 [3]: https://lysine.dev/okio/3.x/okio/okio/okio/-byte-string/index.html
 [4]: https://lysine.dev/okio/3.x/okio/okio/okio/-buffer/index.html
 [5]: https://lysine.dev/okio/3.x/okio/okio/okio/-source/index.html
 [6]: https://lysine.dev/okio/3.x/okio/okio/okio/-sink/index.html
 [7]: https://lysine.dev/okio/3.x/okio/okio/okio/-buffered-source/index.html
 [8]: https://lysine.dev/okio/3.x/okio/okio/okio/-buffered-sink/index.html
 [changelog]: https://lysine.dev/okio/changelog/
 [javadoc]: https://lysine.dev/okio/2.x/okio/okio/index.html
 [kotlin]: https://kotlinlang.org/
 [ok_libraries_talk]: https://www.youtube.com/watch?v=WvyScM_S88c
 [ok_libraries_slides]: https://speakerdeck.com/jakewharton/a-few-ok-libraries-droidcon-mtl-2015
 [encoding_talk]: https://www.youtube.com/watch?v=T_p22jMZSrk
 [encoding_slides]: https://speakerdeck.com/swankjesse/decoding-the-secrets-of-binary-data-droidcon-nyc-2016
 [ok_multiplatform_talk]: https://www.youtube.com/watch?v=Q8B4eDirgk0
 [ok_multiplatform_slides]: https://speakerdeck.com/swankjesse/ok-multiplatform
 [apis_talk]: https://www.youtube.com/watch?v=Du7YXPAV1M8
