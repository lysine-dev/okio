Change Log
==========

## Unreleased

 * None yet.

## Version 3.17.0

_2026-03-11_

 * New: Adjust down the Kotlin stdlib dependency to [Kotlin 2.1.21][kotlin_2_1_21]. Okio is built
   with an up-to-date Kotlin compiler (2.2.21), but depends on an older kotlin-stdlib. We're doing
   this so you can update Okio and Kotlin independently.

 * Fix: Return the correct timestamp in `FileMetadata.createdAtMillis` on Kotlin/Native on UNIX
   platforms. We were incorrectly using the POSIX `ctime` (_change_ time) instead of the
   `birthtime`. With this fix Okio now prefers `statx()` over `stat()` on native platforms. This
   API first appeared in Linux in 4.11 (2017) and Android in API 30 (2020).


## Version 3.16.4

_2025-11-17_

* Fix: Don't delay triggering timeouts. In 3.16.0 we introduced a regression that caused timeouts
  to fire later than they were supposed to.


## Version 3.16.3

_2025-11-14_

This release is the same as 3.16.2. We forgot to cherry-pick a commit before we released!


## Version 3.16.2

_2025-10-14_

 * Fix: `okio-assetfilesystem` APIs now correctly restored as visible to Kotlin.


## Version 3.16.1

_2025-10-09_

 * Fix: Don't crash when calling `Socket.shutdownOutput()` or `shutdownInput()` on an `SSLSocket` on
   Android API 21. This method throws an `UnsupportedOperationException`, so we now catch that and
   close the underlying stream instead.


## Version 3.16.0

_2025-07-29_

 * Fix: Change `Socket.asOkioSocket()` to resolve its source `InputStream` and `OutputStream`
   eagerly. This will throw a `SocketException` immediately if the socket isn’t connected.
   This behavior is consistent with our similar APIs, `Socket.source()` and `Socket.sink()`.

 * Fix: Optimize `AsyncTimeout` on systems with a very large number of active timeouts. This class
   originally kept active timeouts in a linked list; with this update the internal data structure is
   a binary heap. The old runtime was 𝑂(𝑛²) to activate 𝑛 timeouts; with this optimization the
   runtime is 𝑂(𝑛 log 𝑛).

 * Upgrade: [Kotlin 2.2.0][kotlin_2_2_0].


## Version 3.15.0

_2025-07-01_

 * Upgrade: [kotlinx-datetime 0.7.0-0.6.x-compat][datetime_0_7_0_compat]. The previous 3.14.0
   release, `FakeFileSystem` broke binary-compatibility for calls to `FakeFileSystem()`. This
   restores that compatibility.

 * Breaking: Change `FakeFileSystem.clock` from a JVM field to a property. This avoids a crash
   running Okio in a Gradle 8.x plugin. This change is not binary-compatible.


## Version 3.14.0

_2025-06-28_

 * Upgrade: [kotlinx-datetime 0.7.0][datetime_0_7_0]. This impacts the `okio-fakefilesystem`
   artifact only. This is a binary-incompatible backwards-incompatible change that cannot co-exist
   with previous releases of `okio-fakefilesystem`! If you observe a `NoSuchMethodError` calling
   `FakeFileSystem.init`, update the calling code to this release (or newer) and recompile. We don't
   like imposing backwards-incompatible changes like this on you and do so regretfully.


## Version 3.13.0

_2025-06-13_

 * New: `okio.Socket` interface. This is a first step towards low-level streaming networking support
   for Kotlin/Multiplatform. This release provides two JVM-only implementations: `asOkioSocket()`
   adapts a `java.net.Socket` to our new interface, and `inMemorySocketPair()` returns a pair of
   mutually-connected sockets. We also anticipate that this interface will be a useful abstraction
   for proxies and tunnels.
 * New: In-development snapshots are now published to the [Central Portal Snapshots repository].


## Version 3.12.0

_2025-05-27_

 * New: Support an optional end index when searching with `BufferedSource.indexOf()`.
 * Fix: Don't do unnecessary work in `BufferedSource.rangeEquals()`.
 * Upgrade: [Kotlin 2.1.21][kotlin_2_1_21].


## Version 3.11.0

_2025-04-09_

 * Fix: Clear the deflater's byte array reference
 * New: Faster implementation of `String.decodeHex()` on Kotlin/JS.
 * New: Declare `EXACTLY_ONCE` execution for blocks like `Closeable.use {}` and `FileSystem.read {}`.
 * Upgrade: [Kotlin 2.1.20][kotlin_2_1_20].


## Version 3.10.2

_2025-01-08_

 * Fix: `okio-nodefilesystem` artifact is no longer empty.


## Version 3.10.1

_2025-01-07_

 * New: `FileSystem.close()` may prevent future access and/or clean up associated resources depending on the backing implementation. `FakeFileSystem` will prevent future operations once closed.
 * `InputStream`s created from `BufferedSource.inputStream()` now have a more efficient version of `InputStream.transferTo()` which reduces memory copies.
 * `okio-nodefilesystem` is no longer publised as a JS project, but a Kotlin multiplatform project with only a JS target. ~This change should not affect consumers in any way, and is motivated by the Kotlin Gradle plugin deprecating the JS-only plugin.~ Please use 3.10.2 to ensure this change actually does not affect your builds.


## Version 3.10.0

_2025-01-06_

This version is equivalent to the subsequent 3.10.1, but it did not fully publish to Maven Central due to infrastructure problems.


## Version 3.9.1

_2024-09-12_

 * Fix: Support paths containing a single dot (".") in `Path.relativeTo`.
 * Fix: Do not read from the upstream source when a 0-byte read is requested.
 * Fix: Update kotlinx.datetime to 0.6.0 to correct a Gradle module metadata problem with 0.5.0.
   Note: this artifact is only used in 'okio-fakefilesystem' and 'okio-nodefilesystem' and not in the Okio core.


## Version 3.9.0

_2024-03-12_

 * New: `FileSystem.SYSTEM` can be used in source sets that target both Kotlin/Native and
   Kotlin/JVM. Previously, we had this symbol in each source set but it wasn't available to
   common source sets.
 * New: `COpaquePointer.readByteString(...)` creates a ByteString from a memory address.
 * New: Support `InflaterSource`, `DeflaterSink`, `GzipSink`, and `GzipSource` in Kotlin/Native.
 * New: Support openZip() on Kotlin/Native. One known bug in this implementation is that
   `FileMetadata.lastModifiedAtMillis()` is interpreted as UTC and not the host machine's time zone.
 * New: Prefer NTFS timestamps in ZIP file systems' metadata. This avoids the time zone problems
   of ZIP's built-in DOS timestamps, and the 2038 time bombs of ZIP's extended timestamps.
 * Fix: Don't leak file handles to opened JAR files open in `FileSystem.RESOURCES`.
 * Fix: Don't throw a `NullPointerException` if `Closeable.use { ... }` returns null.


## Version 3.8.0

_2024-02-09_

 * New: `TypedOptions` works like `Options`, but it returns a `T` rather than an index.
 * Fix: Don't leave sinks open when there's a race in `Pipe.fold()`.


## Version 3.7.0

_2023-12-16_

 * New: `Timeout.cancel()` prevents a timeout from firing.
 * Breaking: Drop the `watchosX86` Kotlin/Native target. From [the Kotlin blog][watchosX86],
   _‘This is an obsolete simulator for Intel Macs. Use the watchosX64 target instead.’_
 * New: Add the `watchosDeviceArm64` Kotlin/Native target.
 * New: `Timeout` APIs that accept `kotlin.time.Duration`.
 * Upgrade: [Kotlin 1.9.21][kotlin_1_9_21].


## Version 3.6.0

_2023-10-01_

 * Fix: Don't leak file handles when using `metadata` functions on `ZipFileSystem`. We had a bug
   where we were closing the `.zip` file, but not a stream inside of it. We would have prevented
   this bug if only we’d used `FakeFileSystem.checkNoOpenFiles()` in our tests!
 * Fix: Don't build an index of a class loader's resources in `ResourceFileSystem.read()`. This
   operation doesn't need this index, and building it is potentially expensive.
 * New: Experimentally support Linux on ARM64 for Kotlin/Native targets (`linuxArm64`). Note that
   we haven't yet added CI test coverage for this platform.
 * Upgrade: [Kotlin 1.9.10][kotlin_1_9_10].


## Version 1.17.6

_2023-10-01_

 * Fix: Don't crash decoding GZIP files when the optional extra data (`XLEN`) is 32 KiB or larger.


## Version 3.5.0

_2023-08-02_

 * New: Support the WebAssembly (WASM) platform. Okio's support for WASM is experimental, but
   improving, just like Kotlin's own support for WASM.
 * New: Adapt WebAssembly System Interface (WASI) API's as an Okio FileSystem using
   `WasiFileSystem`. This is in the new `okio-wasifilesystem` module. It requires the [preview1]
   WASI API. We’ll make backwards-incompatible upgrades to new WASI API versions as they become
   available.
 * Fix: Return relative paths in the NIO adapter FileSystem when required. `FileSystem.list()`
   had always returned absolute paths, even when the target directory was supplied as a relative
   path.
 * Fix: Don't crash when reading into an empty array using `FileHandle` on Kotlin/Native.
 * Upgrade: [Kotlin 1.9.0][kotlin_1_9_0].


## Version 3.4.0

_2023-07-07_

 * New: Adapt a Java NIO FileSystem (`java.nio.file.FileSystem`) as an Okio FileSystem using
   `fileSystem.asOkioFileSystem()`.
 * New: Adapt Android’s `AssetManager` as an Okio FileSystem using `AssetFileSystem`. This is in the
   new `okio-assetfilesystem` module. Android applications should prefer this over
   `FileSystem.RESOURCES` as it’s faster to load.
 * Fix: Don't crash decoding GZIP files when the optional extra data (`XLEN`) is 32 KiB or larger.
 * Fix: Resolve symlinks in `FakeFileSystem.canonicalize()`.
 * Fix: Report the correct `createdAtMillis` in `NodeJsFileSystem` file metadata. We were
   incorrectly using `ctimeMs`, where `c` means _changed_, not _created_.
 * Fix: `UnsafeCursor` is now `Closeable`.


## Version 3.3.0

_2023-01-07_

 * Fix: Don't leak resources when `use {}` is used with a non-local return. We introduced this
   performance and stability bug by not considering that non-local returns execute neither the
   `return` nor `catch` control flows.
 * Fix: Use a sealed interface for `BufferedSink` and `BufferedSource`. These were never intended
   for end-users to implement, and we're happy that Kotlin now allows us to express that in our API.
 * New: Change internal locks from `synchronized` to `ReentrantLock` and `Condition`. We expect this
   to improve help when using Okio with Java virtual threads ([Project Loom][loom]).
 * Upgrade: [Kotlin 1.8.0][kotlin_1_8_0].


## Version 3.2.0

_2022-06-26_

 * Fix: Configure the multiplatform artifact (`com.squareup.okio:okio:3.x.x`) to depend on the
   JVM artifact (`com.squareup.okio:okio-jvm:3.x.x`) for Maven builds. This should work-around an
   issue where Maven doesn't interpret Gradle metadata.
 * Fix: Change `CipherSource` and `CipherSink` to recover if the cipher doesn't support streaming.
   This should work around a crash with AES/GCM ciphers on Android.
 * New: Enable compatibility with non-hierarchical projects.


## Version 3.1.0

_2022-04-19_

 * Upgrade: [Kotlin 1.6.20][kotlin_1_6_20].
 * New: Support [Hierarchical project structure][hierarchical_projects]. If you're using Okio in a
   multiplatform project please upgrade your project to Kotlin 1.6.20 (or newer) to take advantage
   of this. With hierarchical projects it's easier to use properties like `FileSystem.SYSTEM` that
   are available on most Okio platforms but not all of them.
 * New: `ForwardingSource` is now available on all platforms.
 * New: The `watchosX64` platform is now supported.
 * Fix: Don't crash in `NSData.toByteString()' when the input is empty.
 * Fix: Support empty ZIP files in `FileSystem.openZip()`.
 * Fix: Throw in `canonicalize()` of ZIP file systems if the path doesn't exist.
 * Fix: Don't require ZIP files start with a local file header.
 * New: `okio.ProtocolException` is a new exception type for multiplatform users. (It is aliased to
   `java.net.ProtocolException` on JVM platforms).


## Version 3.0.0

_2021-10-28_

This is the first stable release of Okio 3.x. This release is strongly backwards-compatible with
Okio 2.x, and the new major version signifies new capabilities more than it does backwards
incompatibility.

Most users should be able to upgrade from 2.x by just changing the version. If you're using Okio
in a Kotlin Multiplatform project, you'll need to drop the `-multiplatform` suffix in your Gradle
dependencies.

 * New: Remove `@ExperimentalFileSystem`. This annotation is no longer necessary as the file system
   is no longer experimental!
 * New: Path no longer aggressively normalizes `..` segments. Use `Path.normalize()` to apply these
   based on the content of the path, or `FileSystem.canonicalize()` to do it honoring any symlinks
   on a particular file system.
 * New: Publish a [bill of materials (BOM)][bom] for Okio. Depend on this from Gradle or Maven to
   keep all of your Okio artifacts on the same version, even if they're declared via transitive
   dependencies. You can even omit versions when declaring other Okio dependencies.

   ```kotlin
   dependencies {
      api(platform("com.squareup.okio:okio-bom:3.0.0"))
      api("com.squareup.okio:okio")                // No version!
      api("com.squareup.okio:okio-fakefilesystem") // No version!
   }
   ```

 * New: `FileSystem.delete()` silently succeeds when deleting a file that doesn't exist. Use
   the new `mustExist` parameter to trigger an exception instead.
 * New: `FileSystem.createDirectories()` silently succeeds when creating a directory that already
   exists. Use the new `mustCreate` parameter to trigger an exception instead.
 * New: `FileSystem` offers Java-language overloads where appropriate. Previously functions that
   had default parameters were potentially awkward to invoke from Java.
 * New: `Timeout.intersectWith()` returns a value instead of `Unit`. This is a binary-incompatible
   change. We expect that this public API is very rarely used outside of Okio itself.
 * Fix: Change `BufferedSource.readDecimalLong()` to fail if the input value is just `-`. Previously
   Okio incorrectly returned `0` for this.


## Version 3.0.0-alpha.11

_2021-10-23_

 * Upgrade: [Kotlin 1.5.31][kotlin_1_5_31].
 * Upgrade: [kotlinx-datetime 0.3.0][datetime_0_3_0]. (This is a dependency of `okio-fakefilesystem`
   only.)
 * New: Support creating and accessing symlinks. We were reluctant to include symlinks in our API
   (to keep it small!) but decided that supporting them was essential to properly implement
   recursive traversal.
 * New: `FileMetadata.extras` can track metadata for custom `FileSystem` implementations.
 * New: Support Apple Silicon Kotlin/Native targets (`macosArm64`, `iosSimulatorArm64`,
   `tvosSimulatorArm64`, and `watchosSimulatorArm64`).
 * New: `FileSystem.listRecursively()` returns a `Sequence` that includes all of a directory's
   children, and all of their children recursively. The implementation does a lazy, depth-first
   traversal.
 * New: `Path.relativeTo()` computes how to get from one path to another.
 * New: `Path.root` and `Path.segments`. These APIs decompose a path into its component parts.
 * New: `FileSystem.listOrNull()` returns a directory's children, or null if the path doesn't
   reference a readable directory.
 * New: Option to fail if the file being updated doesn't already exist: `mustExist`. Use this to
   avoid creating a new file when your intention is to update an existing file.
 * New: Option to fail if a file being created already exists: `mustCreate`. Use this to avoid
   updating an existing file when your intention is to create a new file.
 * Fix: Restore support for Kotlin/JS on browser platforms. We were relying on NodeJS-only features
   to fetch the local directory separator (`/` or `\`) and temporary directory.
 * Fix: Don't ignore the caller's specified write offset running Okio on Kotlin/Native on Linux.
   (`FileHandle.write()` was broken and always appended to the end of the file.)


## Version 3.0.0-alpha.10

_2021-09-09_

This release drops the `-multiplatform` suffix on Kotlin Multiplatform artifacts. All artifacts now
share the same name (like `com.squareup.okio:okio:3.0.0-alpha.10`) for both Kotlin/JVM and Kotlin
Multiplatform.

 * Fix: Don't crash in `ResourceFileSystem` when classpath `.jar` files have special characters in
   their paths.


## Version 3.0.0-alpha.9

_2021-08-01_

 * New: `ByteString.copyInto()` saves an allocation when extracting data from a `ByteString`.
 * Fix: Create `FileHandle.protectedSize()` to match other abstract functions.
 * Fix: Open files in binary mode on Windows. Without this, files that contain `0x1a` will be
   truncated prematurely.


## Version 3.0.0-alpha.8

_2021-07-13_

 * Fix: Don't crash on duplicate entries in a .zip file.
 * Fix: Change `FileSystem.RESOURCES` to initialize itself lazily.


## Version 3.0.0-alpha.7

_2021-07-12_

 * Fix: Change `ResourceFileSystem` to load roots eagerly. We had a bug where `list()` on the root
   returned an empty list even if resources were present.
 * New: `FileHandle.reposition()` can seek on a source or sink returned by that `FileHandle`.
 * New: Move the system resources instance to `FileSystem.RESOURCES`.
 * Upgrade: [Kotlin 1.5.20][kotlin_1_5_20].


## Version 3.0.0-alpha.6

_2021-06-01_

 * New: `FileHandle` supports random access reads, writes, and resizes on files. Create an instance
   with `FileSystem.openReadOnly()` or `FileSystem.openReadWrite()`.
 * New: Remove `Cursor` which is obsoleted by `FileHandle`. (`UnsafeCursor` is still around!)
 * New: Add support for the new intermediate representation (IR) artifacts in Kotlin/JS. We still
   support the legacy artifact format.
 * New: Support tvOS (tvosArm64, tvosX64) in multiplatform.
 * New: Change `ResourceFileSystem` to omit `.class` files when indexing `.zip` files. We expect
   this to lower the memory footprint of `ResourceFileSystem`.
 * Fix: Don't crash on background thread access in Kotlin/Native. We had to apply `@SharedImmutable`
   and run our test suite on a background thread.


## Version 3.0.0-alpha.5

_2021-04-27_

 * New: Promote the `ZipFileSystem` and `ResourceFileSystem` to the main Okio module. These are
   currently JVM-only. The `okio-zipfilesystem` module is no longer published.


## Version 3.0.0-alpha.4

_2021-04-14_

 * Fix: Rename internal classes to avoid name collisions. We were seeing problems due to having
   multiple files named `-Platform.kt`.


## Version 3.0.0-alpha.3

_2021-04-06_

 * New: Move `NodeJsFileSystem` into its own module. Having it built-in prevented Okio from working
   in a browser where there's no synchronous file system API. This is in the `okio-nodefilesystem`
   artifact.


## Version 3.0.0-alpha.2

_2021-03-24_

 * New: Require Java 8+ for Okio 3.x.
 * New: `Cursor` supports random access reads on a `Source`.
 * New: `FileSystem.openZip(path)` returns a file system backed by a `.zip` file. This is in the
   `okio-zipfilesystem` artifact.


## Version 3.0.0-alpha.1

_2021-01-07_

* New: Experimental file system API. The `Path`, `FileMetadata`, `FileSystem` and
  `ForwardingFileSystem` types are subject to API changes in a future release.
* New: Experimental `okio-fakefilesystem` artifact.


## Version 2.10.0

_2021-01-07_

* New: Support Windows (mingwX64) in multiplatform.
* New: Support watchOS (watchosArm32, watchosArm64, watchosX86) in multiplatform.
* New: Support `HashingSource`, `HashingSink`, buffer hash functions, and `UnsafeCursor` on non-JVM
  platforms. Previously these were all JVM-only.
* New: Implement `Closeable` on `Sink` and `Source` on non-JVM platforms. Okio now includes a
  multiplatform `okio.Closeable` interface and corresponding `use {}` extension. Closing resources
  when you're done with them shouldn't be JVM-only!
* New: `Sink.hashingSink` and `Source.hashingSource` functions that accept
  `java.security.MessageDigest` and `javax.crypto.Mac` instances. Use these when your hash function
  isn't built-in.
* Fix: Don't crash with a `ShortBufferException` in `CipherSink` and `CipherSource` on Android.
  (Android may throw a `ShortBufferException` even if the buffer is not too short. We now
  avoid this problem!)
* Upgrade: [Kotlin 1.4.20][kotlin_1_4_20].


## Version 2.9.0

_2020-10-04_

 * Fix: Don't corrupt the `Buffer` when writing a slice of a segmented `ByteString`. We had a severe
   bug where `ByteString` instances created with `snapshot()` and `readByteString()` incorrectly
   adjusted the buffer's size by their full length, not the length of the slice. This would have
   caused buffer reads to crash! We do not believe data was silently corrupted.
 * New: `CipherSink` and `CipherSource`. Use these with `javax.crypto.Cipher` to encrypt and decrypt
   streams of data. This is a low-level encryption API; most applications should use higher-level
   APIs like TLS when available.
 * New: Promote hash functions `md5`, `sha1()`, `sha512()`, and `sha256()` to common Kotlin. These
   are currently only available on `ByteString`, multiplatform support for `HashingSource`,
   `HashingSink`, and `Buffer` should come in a follow-up release. We wrote and optimized our own
   implementations of these hash functions in Kotlin. On JVM and Android platforms Okio still uses
   the platform's built-in hash functions.
 * New: Support OSGi metadata.
 * Upgrade: [Kotlin 1.4.10][kotlin_1_4_10].


## Version 2.8.0

_2020-08-17_

 * New: Upgrade to Kotlin 1.4.0.


## Version 2.7.0

_2020-07-07_

 * New: `Pipe.cancel()` causes in-progress and future reads and writes on the pipe to immediately
   fail with an `IOException`. The streams may still be canceled normally.

 * New: Enlarge Okio's internal segment pool from a fixed 64 KiB total to 64 KiB per processor. For
   example, on an Intel i9 8-core/16-thread machine the segment pool now uses up to 1 MiB of memory.

 * New: Migrate from `synchronized` to lock-free when accessing the segment pool. Combined with the
   change above we saw throughput increase 3x on a synthetic benchmark designed to create
   contention.


## Version 2.6.0

_2020-04-22_

 * New: `InflaterSource.readOrInflate()` is like `InflaterSource.read()`, except it will return 0 if
   consuming deflated bytes from the underlying stream did not produce new inflated bytes.


## Version 2.5.0

_2020-03-20_

 * New: Upgrade to Kotlin 1.3.70.


## Version 2.4.3

_2019-12-20_

 * New: Upgrade to Kotlin 1.3.61.


## Version 2.4.2

_2019-12-11_

 * Fix: Don't crash when an `InputStream` source is exhausted exactly at a buffer segment boundary.
   We had a bug where a sequence of reads could violate a buffer's invariants, and this could result
   in a crash when subsequent reads encountered an unexpected empty segment.


## Version 1.17.5

_2019-12-11_

 * Fix: Don't crash when an `InputStream` source is exhausted exactly at a buffer segment boundary.
   We had a bug where a sequence of reads could violate a buffer's invariants, and this could result
   in a crash when subsequent reads encountered an unexpected empty segment.


## Version 2.4.1

_2019-10-04_

 * Fix: Don't cache hash code and UTF-8 string in `ByteString` on Kotlin/Native which prevented freezing.

## Version 2.4.0

_2019-08-26_

 * New: Upgrade to Kotlin 1.3.50.


## Version 2.3.0

_2019-07-29_

**This release changes our build from Kotlin-JVM to Kotlin-multiplatform (which includes JVM).**
Both native and JavaScript platforms are unstable preview releases and subject to
backwards-incompatible changes in forthcoming releases.

To try Okio in a multiplatform project use this Maven coordinate:

```kotlin
api('com.squareup.okio:okio-multiplatform:2.3.0')
```

You’ll also need to [enable Gradle metadata][gradle_metadata] in your project's settings. The
artifact name for JVM projects has not changed.

 * New: Upgrade to Kotlin 1.3.40.
 * Fix: Use Gradle `api` instead of `implementation` for the kotlin-stdlib dependency.
 * Fix: Don't block unless strictly necessary in `BufferedSource.peek()`.

## Version 1.17.4

_2019-04-29_

 * Fix: Don't block unless strictly necessary in `BufferedSource.peek()`.


## Version 2.2.2

_2019-01-28_

 * Fix: Make `Pipe.fold()` close the underlying sink when necessary.


## Version 1.17.3

_2019-01-28_

 * Fix: Make `Pipe.fold()` close the underlying sink when necessary.


## Version 1.17.2

_2019-01-17_

 * Fix: Make `Pipe.fold()` flush the underlying sink.


## Version 2.2.1

_2019-01-17_

 * Fix: Make `Pipe.fold()` flush the underlying sink.


## Version 2.2.0

_2019-01-16_

 * New: `Throttler` limits sources and sinks to a maximum desired throughput. Multiple sources and
   sinks can be attached to the same throttler and their combined throughput will not exceed the
   desired throughput. Multiple throttlers can also be used on the same source or sink and they will
   all be honored.

 * New: `Pipe.fold()` replaces the actively-readable `Source` with a passively-writable `Sink`.
   This can be used to forward one sink to a target that is initially undetermined.

 * New: Optimize performance of ByteStrings created with `Buffer.snapshot()`.


## Version 1.17.1

_2019-01-16_

 * Fix: Make the newly-backported `Pipe.fold()` public.


## Version 1.17.0

_2019-01-16_

 * New: Backport `Pipe.fold()` to Okio 1.x.


## Version 1.16.0

_2018-10-08_

 * New: Backport `BufferedSource.peek()` and `BufferedSource.getBuffer()` to Okio 1.x.
 * Fix: Enforce timeouts when closing `AsyncTimeout` sources.


## Version 2.1.0

_2018-09-22_

 * New: `BufferedSource.peek()` returns another `BufferedSource` that reads ahead on the current
   source. Use this to process the same data multiple times.

 * New: Deprecate `BufferedSource.buffer()`, replacing it with either `BufferedSource.getBuffer()`
   (in Java) or `BufferedSource.buffer` (in Kotlin). We have done likewise for `BufferedSink`.
   When we introduced the new extension method `Source.buffer()` in Okio 2.0 we inadvertently
   collided with an existing method. This fixes that.

 * New: Improve performance of `Buffer.writeUtf8()`. This comes alongside initial implementation of
   UTF-8 encoding and decoding in JavaScript which [uses XOR masks][xor_utf8] for great performance.


## Version 2.0.0

_2018-08-27_

This release commits to a stable 2.0 API. Read the 2.0.0-RC1 changes for advice on upgrading from
1.x to 2.x.

We've also added APIs to ease migration for Kotlin users. They use Kotlin's `@Deprecated` annotation
to help you change call sites from the 1.x style to the 2.x style.


## Version 2.0.0-RC1

_2018-07-26_

Okio 2 is a major release that upgrades the library's implementation language from Java to Kotlin.

Okio 2.x is **binary-compatible** with Okio 1.x and does not change any behavior. Classes and .jar
files compiled against 1.x can be used with 2.x without recompiling.

Okio 2.x is **.java source compatible** with Okio 1.x in all but one corner case. In Okio 1.x
`Buffer` would throw an unchecked `IllegalStateException` when attempting to read more bytes than
available. Okio 2.x now throws a checked `EOFException` in this case. This is now consistent with
the behavior of its `BufferedSource` interface. Java callers that don't already catch `IOException`
will now need to.

Okio 2.x is **.kt source-incompatible** with Okio 1.x. This release adopts Kotlin idioms where they
are available.

| Java                                     |  Kotlin                              | Idiom              |
| :--------------------------------------- |  :---------------------------------- | :----------------- |
| Buffer.getByte()                         |  operator fun Buffer.get()           | operator function  |
| Buffer.size()                            |  val Buffer.size                     | val                |
| ByteString.decodeBase64(String)          |  fun String.decodeBase64()           | extension function |
| ByteString.decodeHex(String)             |  fun String.decodeHex()              | extension function |
| ByteString.encodeString(String, Charset) |  fun String.encode(Charset)          | extension function |
| ByteString.encodeUtf8(String)            |  fun String.encodeUtf8()             | extension function |
| ByteString.getByte()                     |  operator fun ByteString.get()       | operator function  |
| ByteString.of(ByteBuffer)                |  fun ByteBuffer.toByteString()       | extension function |
| ByteString.of(byte[], int, int)          |  fun ByteArray.toByteString()        | extension function |
| ByteString.read(InputStream, int)        |  fun InputStream.readByteString(Int) | extension function |
| ByteString.size()                        |  val ByteString.size                 | val                |
| DeflaterSink(Sink)                       |  fun Sink.deflater()                 | extension function |
| ForwardingSink.delegate()                |  val ForwardingSink.delegate         | val                |
| ForwardingSource.delegate()              |  val ForwardingSource.delegate       | val                |
| GzipSink(Sink, Deflater)                 |  fun Sink.gzip()                     | extension function |
| GzipSink.deflater()                      |  val GzipSink.deflater               | val                |
| GzipSource(Source)                       |  fun Source.gzip()                   | extension function |
| HashingSink.hash()                       |  val HashingSink.hash                | val                |
| HashingSource.hash()                     |  val HashingSource.hash              | val                |
| InflaterSink(Source)                     |  fun Source.inflater()               | extension function |
| Okio.appendingSink(File)                 |  fun File.appendingSink()            | extension function |
| Okio.blackhole()                         |  fun blackholeSink()                 | top level function |
| Okio.buffer(Sink)                        |  fun Sink.buffer()                   | extension function |
| Okio.buffer(Source)                      |  fun Source.buffer()                 | extension function |
| Okio.sink(File)                          |  fun File.sink()                     | extension function |
| Okio.sink(OutputStream)                  |  fun OutputStream.sink()             | extension function |
| Okio.sink(Path)                          |  fun Path.sink()                     | extension function |
| Okio.sink(Socket)                        |  fun Socket.sink()                   | extension function |
| Okio.source(File)                        |  fun File.source()                   | extension function |
| Okio.source(InputStream)                 |  fun InputStream.source()            | extension function |
| Okio.source(Path)                        |  fun Path.source()                   | extension function |
| Okio.source(Socket)                      |  fun Socket.source()                 | extension function |
| Pipe.sink()                              |  val Pipe.sink                       | val                |
| Pipe.source()                            |  val Pipe.source                     | val                |
| Utf8.size(String)                        |  fun String.utf8Size()               | extension function |

Okio 2.x has **similar performance** to Okio 1.x. We benchmarked both versions to find potential
performance regressions. We found one regression and fixed it: we were using `==` instead of `===`.

Other changes in this release:

 * New: Add a dependency on kotlin-stdlib. Okio's transitive dependencies grow from none in 1.x to
   three in 2.x. These are kotlin-stdlib (939 KiB), kotlin-stdlib-common (104 KiB), and JetBrains'
   annotations (17 KiB).

 * New: Change Okio to build with Gradle instead of Maven.


## Version 1.15.0

_2018-07-18_

 * New: Trie-based `Buffer.select()`. This improves performance when selecting
   among large lists of options.
 * Fix: Retain interrupted state when throwing `InterruptedIOException`.


## Version 1.14.0

_2018-02-11_

 * New: `Buffer.UnsafeCursor` provides direct access to Okio internals. This API
   is like Okio's version of Java reflection: it's a very powerful API that can
   be used for great things and dangerous things alike. The documentation is
   extensive and anyone using it should review it carefully before proceeding!
 * New: Change `BufferedSource` to implement `java.nio.ReadableByteChannel` and
   `BufferedSink` to implement `java.nio.WritableByteChannel`. Now it's a little
   easier to interop between Okio and NIO.
 * New: Automatic module name of `okio` for use with the Java Platform Module
   System.
 * New: Optimize `Buffer.getByte()` to search backwards when doing so will be
   more efficient.
 * Fix: Honor the requested byte count in `InflaterSource`. Previously this
   class could return more bytes than requested.
 * Fix: Improve a performance bug in `AsyncTimeout.sink().write()`.


## Version 1.13.0

_2017-05-12_

 * **Okio now uses `@Nullable` to annotate all possibly-null values.** We've
   added a compile-time dependency on the JSR 305 annotations. This is a
   [provided][maven_provided] dependency and does not need to be included in
   your build configuration, `.jar` file, or `.apk`. We use
   `@ParametersAreNonnullByDefault` and all parameters and return types are
   never null unless explicitly annotated `@Nullable`.

 * **Warning: this release is source-incompatible for Kotlin users.**
   Nullability was previously ambiguous and lenient but now the compiler will
   enforce strict null checks.


## Version 1.12.0

_2017-04-11_

 * **Fix: Change Pipe's sink.flush() to not block.** Previously closing a pipe's
   sink would block until the source had been exhausted. In practice this
   blocked the caller for no benefit.
 * **Fix: Change `writeUtf8CodePoint()` to emit `?` for partial surrogates.**
   The previous behavior was inconsistent: given a malformed string with a
   partial surrogate, `writeUtf8()` emitted `?` but `writeUtf8CodePoint()` threw
   an `IllegalArgumentException`. Most applications will never encounter partial
   surrogates, but for those that do this behavior was unexpected.
 * New: Allow length of `readUtf8LineStrict()` to be limited.
 * New: `Utf8.size()` method to get the number of bytes required to encode a
   string as UTF-8. This may be useful for length-prefixed encodings.
 * New: SHA-512 hash and HMAC APIs.


## Version 1.11.0

_2016-10-11_

 * **Fix: The four-argument overload of `Buffer.writeString()` had a major bug
   where it didn't respect offsets if the specified charset was UTF-8.** This
   was because our short-circuit optimization omitted necessary offset
   parameters.
 * New: HMAC support in `HashingSource`, `HashingSink`, `ByteString`, and
   `Buffer`. This makes it easy to create a keyed-hash message authentication
   code (HMAC) wherever your data is. Unlike the other hashes, HMAC uses a
   `ByteString` secret key for authentication.
 * New: `ByteString.of(ByteBuffer)` makes it easier to mix NIO with Okio.


## Version 1.10.0

_2016-08-28_

 * Fix: Support reading files larger than 2 GiB with `GzipSource`. Previously
   attempting to decompress such files would fail due to an overflow when
   validating the total length.
 * Fix: Exit the watchdog thread after being idle for 60 seconds. This should
   make it possible for class unloaders to fully unload Okio.
 * New: `Okio.blackhole()` returns a sink where all bytes written are discarded.
   This is Okio's equivalent of `/dev/null`.
 * New: Encode a string with any charset using `ByteString.encodeString()` and
   decode strings in any charset using `ByteString.string()`. Most applications
   should prefer `ByteString.encodeUtf8()` and `ByteString.utf8()` unless it's
   necessary to support a legacy charset.
 * New: `GzipSink.deflater()` makes it possible to configure the compression
   level.


## Version 1.9.0

_2016-07-01_

 * New: `Pipe` makes it easy to connect a producer thread to a consumer thread.
   Reads block until data is available to read. Writes block if the pipe's is
   full. Both sources and sinks support timeouts.
 * New: `BufferedSource.rangeEquals()` makes it easy to compare a range in a
   stream to an expected value. This does the right thing: it blocks to load
   the data required return a definitive result. But it won't block
   unnecessarily.
 * New: `Timeout.waitUntilNotified()` makes it possible to use nice timeout
   abstractions on Java's built-in wait/notify primitives.
 * Fix: Don't return incorrect results when `HashingSource` does large reads.
   There was a bug where it wasn't traversing through the segments of the buffer
   being hashed. This means that `HashingSource` was returning incorrect answers
   for any writes that spanned multiple segment boundaries.

## Version 1.8.0

_2016-05-02_

 * New: `BufferedSource.select(Options)` API for reading one of a set of
   expected values.
 * New: Make `ByteString.toString()` and `Buffer.toString()` friendlier.
   These methods return text if the byte string is valid UTF-8.
 * New: APIs to match byte strings: `indexOf()`, `startsWith()`, and
   `endsWith()`.

## Version 1.7.0

_2016-04-10_

 * New: Change the segment size to 8 KiB. This has been reported to dramatically
   improve performance in some applications.
 * New: `md5()`, `sha1()`, and `sha256()` methods on `Buffer`. Also add a
   `sha1()` method on `ByteString` for symmetry.
 * New: `HashingSource` and `HashingSink`. These classes are Okio’s equivalent
   to the JDK’s `DigestInputStream` and `DigestOutputStream`. They offer
   convenient `md5()`, `sha1()`, and `sha256()` factory methods to avoid an
   impossible `NoSuchAlgorithmException`.
 * New: `ByteString.asByteBuffer()`.
 * Fix: Limit snapshot byte strings to requested size.
 * Fix: Change write timeouts to have a maximum write size. Previously large
   writes could easly suffer timeouts because the entire write was subject to a
   single timeout.
 * Fix: Recover from EBADF failures, which could be triggered by asynchronously
   closing a stream on older versions of Android.
 * Fix: Don't share segments if doing so only saves a small copy. This should
   improve performance for all applications.
 * Fix: Optimize `BufferedSource.indexOfElement()` and `indexOf(ByteString)`.
   Previously this method had a bug that caused it to be very slow on large
   buffers.

## Version 1.6.0

_2015-08-25_

 * New: `BufferedSource.indexOf(ByteString)` searches a source for the next
   occurrence of a byte string.
 * Fix: Recover from unexpected `AssertionError` thrown on Android 4.2.2 and
   earlier when asynchronously closing a socket.

## Version 1.5.0

_2015-06-19_

 * Sockets streams now throw `SocketTimeoutException`. This builds on new
   extension point in `AsyncTimeout` to customize the exception when a timeout
   occurs.
 * New: `ByteString` now implements `Comparable`. The comparison sorts bytes as
   unsigned: {@code ff} sorts after {@code 00}.

## Version 1.4.0

_2015-05-16_

 * **Timeout exception changed.** Previously `Timeout.throwIfReached()` would
   throw `InterruptedIOException` on thread interruption, and `IOException` if
   the deadline was reached. Now it throws `InterruptedIOException` in both
   cases.
 * Fix: throw `EOFException` when attempting to read digits from an empty
   source. Previously this would crash with an unchecked exception.
 * New: APIs to read and write UTF-8 code points without allocating strings.
 * New: `BufferedSink` can now write substrings directly, potentially saving an
   allocation for some callers.
 * New: `ForwardingTimeout` class.

## Version 1.3.0

_2015-03-16_

 * New: Read and write signed decimal and unsigned hexadecimal values in
   `BufferedSource` and `BufferedSink`. Unlike the alternatives, these methods
   don’t do any memory allocations!
 * New: Segment sharing. This improves the runtime of operations like
   `Buffer.clone()` and `Buffer.copyTo()` by sharing underlying segments between
   buffers.
 * New: `Buffer.snapshot()` returns an immutable snapshot of a buffer as a
   `ByteString`. This builds on segment sharing so that snapshots are shallow,
   immutable copies.
 * New: `ByteString.rangeEquals()`.
 * New: `ByteString.md5()` and `ByteString.sha256()`.
 * New: `ByteString.base64Url()` returns URL-safe Base64. The existing
   decoding method has been extended to support URL-safe Base64 input.
 * New: `ByteString.substring()` returns a prefix, infix, or suffix.
 * New: `Sink` now implements `java.io.Flushable`.
 * Fix: `Buffer.write(Source, long)` now always writes fully. The previous
   behavior would return as soon as any data had been written; this was
   inconsistent with all other _write()_ methods in the API.
 * Fix: don't leak empty segments in DeflaterSink and InflaterSource. (This was
   unlikely to cause problems in practice.)

## Version 1.2.0

_2014-12-30_

 * Fix: `Okio.buffer()` _always_ buffers for better predictability.
 * Fix: Provide context when `readUtf8LineStrict()` throws.
 * Fix: Buffers do not call through the `Source` on zero-byte writes.

## Version 1.1.0

_2014-12-11_

 * Do UTF-8 encoding natively for a performance increase, particularly on Android.
 * New APIs: `BufferedSink.emit()`, `BufferedSource.request()` and `BufferedSink.indexOfElement()`.
 * Fixed a performance bug in `Buffer.indexOf()`

## Version 1.0.1

_2014-08-08_

 * Added `read(byte[])`, `read(byte[], offset, byteCount)`,  and
   `void readFully(byte[])` to `BufferedSource`.
 * Refined declared checked exceptions on `Buffer` methods.


## Version 1.0.0

_2014-05-23_

 * Bumped release version. No other changes!

## Version 0.9.0

_2014-05-03_

 * Use 0 as a sentinel for no timeout.
 * Make AsyncTimeout public.
 * Remove checked exception from Buffer.readByteArray.

## Version 0.8.0

_2014-04-24_

 * Eagerly verify preconditions on public APIs.
 * Quick return on Buffer instance equivalence.
 * Add delegate types for Sink and Source.
 * Small changes to the way deadlines are managed.
 * Add append variant of Okio.sink for File.
 * Methods to exhaust BufferedSource to byte[] and ByteString.

## Version 0.7.0

_2014-04-18_

 * Don't use getters in timeout.
 * Use the watchdog to interrupt sockets that have reached deadlines.
 * Add java.io and java.nio file source/sink helpers.

## Version 0.6.1

_2014-04-17_

 * Methods to read a buffered source fully in UTF-8 or supplied charset.
 * API to read a byte[] directly.
 * New methods to move all data from a source to a sink.
 * Fix a bug on input stream exhaustion.

## Version 0.6.0

_2014-04-15_

 * Make ByteString serializable.
 * New API: `ByteString.of(byte[] data, int offset, int byteCount)`
 * New API: stream-based copy, write, and read helpers.

## Version 0.5.0

_2014-04-08_

 * Initial public release.
 * Imported from OkHttp.


[Central Portal Snapshots repository]: https://central.sonatype.org/publish/publish-portal-snapshots/
[bom]: https://docs.gradle.org/6.2/userguide/platforms.html#sub:bom_import
[datetime_0_3_0]: https://github.com/Kotlin/kotlinx-datetime/releases/tag/v0.3.0
[datetime_0_7_0]: https://github.com/Kotlin/kotlinx-datetime/releases/tag/v0.7.0
[datetime_0_7_0_compat]: https://github.com/Kotlin/kotlinx-datetime?tab=readme-ov-file#deprecation-of-instant
[gradle_metadata]: https://blog.gradle.org/gradle-metadata-1.0
[hierarchical_projects]: https://kotlinlang.org/docs/multiplatform-hierarchy.html
[kotlin_1_4_10]: https://github.com/JetBrains/kotlin/releases/tag/v1.4.10
[kotlin_1_4_20]: https://github.com/JetBrains/kotlin/releases/tag/v1.4.20
[kotlin_1_5_20]: https://github.com/JetBrains/kotlin/releases/tag/v1.5.20
[kotlin_1_5_31]: https://github.com/JetBrains/kotlin/releases/tag/v1.5.31
[kotlin_1_6_20]: https://blog.jetbrains.com/kotlin/2022/04/kotlin-1-6-20-released/
[kotlin_1_8_0]: https://kotlinlang.org/docs/whatsnew18.html
[kotlin_1_9_0]: https://kotlinlang.org/docs/whatsnew19.html
[kotlin_1_9_10]: https://github.com/JetBrains/kotlin/releases/tag/v1.9.10
[kotlin_1_9_21]: https://github.com/JetBrains/kotlin/releases/tag/v1.9.21
[kotlin_2_1_20]: https://github.com/JetBrains/kotlin/releases/tag/v2.1.20
[kotlin_2_1_21]: https://github.com/JetBrains/kotlin/releases/tag/v2.1.21
[kotlin_2_2_0]: https://github.com/JetBrains/kotlin/releases/tag/v2.2.0
[loom]: https://wiki.openjdk.org/display/loom/Getting+started
[maven_provided]: https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html
[preview1]: https://github.com/WebAssembly/WASI/blob/main/legacy/preview1/docs.md
[watchosX86]: https://blog.jetbrains.com/kotlin/2023/02/update-regarding-kotlin-native-targets/
[xor_utf8]: https://github.com/lysine-dev/okio/blob/bbb29c459e5ccf0f286e0b17ccdcacd7ac4bc2a9/okio/src/main/kotlin/okio/Utf8.kt#L302
