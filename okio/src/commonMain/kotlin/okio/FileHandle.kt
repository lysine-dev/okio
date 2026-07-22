/*
 * Copyright (C) 2021 Square, Inc.
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

/**
 * An open file for reading and writing; using either streaming and random access.
 *
 * Use [read] and [write] to perform one-off random-access reads and writes. Use [source], [sink],
 * and [appendingSink] for streaming reads and writes.
 *
 * File handles must be closed when they are no longer needed. It is an error to read, write, or
 * create streams after a file handle is closed. The operating system resources held by a file
 * handle will be released once the file handle **and** all of its streams are closed.
 *
 * Although this class offers both reading and writing APIs, file handle instances may be
 * read-only or write-only. For example, a handle to a file on a read-only file system will throw an
 * exception if a write is attempted.
 *
 * File handles may be used by multiple threads concurrently. But the individual sources and sinks
 * produced by a file handle are not safe for concurrent use.
 */
abstract class FileHandle(
  /**
   * True if this handle supports both reading and writing. If this is false all write operations
   * including [write], [sink], [resize], and [flush] will all throw [IllegalStateException] if
   * called.
   */
  val readWrite: Boolean,
) : Closeable {
  /**
   * True once the file handle is closed. Resources should be released with [protectedClose] once
   * this is true and [openStreamCount] is 0.
   */
  private var closed = false

  /**
   * Reference count of the number of open sources and sinks on this file handle. Resources should
   * be released with [protectedClose] once this is 0 and [closed] is true.
   */
  private var openStreamCount = 0

  val lock: Lock = newLock()

  /**
   * Reads at least 1, and up to [byteCount] bytes from this starting at [fileOffset] and copies
   * them to [array] at [arrayOffset]. Returns the number of bytes read, or -1 if [fileOffset]
   * equals [size].
   */
  @Throws(IOException::class)
  fun read(
    fileOffset: Long,
    array: ByteArray,
    arrayOffset: Int,
    byteCount: Int,
  ): Int {
    lock.withLock {
      check(!closed) { "closed" }
    }
    return protectedRead(fileOffset, array, arrayOffset, byteCount)
  }

  /**
   * Reads at least 1, and up to [byteCount] bytes from this starting at [fileOffset] and appends
   * them to [sink]. Returns the number of bytes read, or -1 if [fileOffset] equals [size].
   */
  @Throws(IOException::class)
  fun read(fileOffset: Long, sink: Buffer, byteCount: Long): Long {
    lock.withLock {
      check(!closed) { "closed" }
    }
    return readNoCloseCheck(fileOffset, sink, byteCount)
  }

  /**
   * Returns the total number of bytes in the file. This will change if the file size changes.
   */
  @Throws(IOException::class)
  fun size(): Long {
    lock.withLock {
      check(!closed) { "closed" }
    }
    return protectedSize()
  }

  /**
   * Changes the number of bytes in this file to [size]. This will remove bytes from the end if the
   * new size is smaller. It will add `0` bytes to the end if it is larger.
   */
  @Throws(IOException::class)
  fun resize(size: Long) {
    check(readWrite) { "file handle is read-only" }
    lock.withLock {
      check(!closed) { "closed" }
    }
    return protectedResize(size)
  }

  /** Reads [byteCount] bytes from [array] and writes them to this at [fileOffset]. */
  fun write(
    fileOffset: Long,
    array: ByteArray,
    arrayOffset: Int,
    byteCount: Int,
  ) {
    check(readWrite) { "file handle is read-only" }
    lock.withLock {
      check(!closed) { "closed" }
    }
    return protectedWrite(fileOffset, array, arrayOffset, byteCount)
  }

  /** Removes [byteCount] bytes from [source] and writes them to this at [fileOffset]. */
  @Throws(IOException::class)
  fun write(fileOffset: Long, source: Buffer, byteCount: Long) {
    check(readWrite) { "file handle is read-only" }
    lock.withLock {
      check(!closed) { "closed" }
    }
    writeNoCloseCheck(fileOffset, source, byteCount)
  }

  /** Pushes all buffered bytes to their final destination. */
  @Throws(IOException::class)
  fun flush() {
    check(readWrite) { "file handle is read-only" }
    lock.withLock {
      check(!closed) { "closed" }
    }
    return protectedFlush()
  }

  /**
   * Returns a source that reads from this starting at [fileOffset]. The returned source must be
   * closed when it is no longer needed.
   */
  @Throws(IOException::class)
  fun source(fileOffset: Long = 0L): Source {
    lock.withLock {
      check(!closed) { "closed" }
      openStreamCount++
    }
    return FileHandleSource(this, fileOffset)
  }

  /**
   * Returns the position of [source] in the file. The argument [source] must be either a source
   * produced by this file handle, or a [BufferedSource] that directly wraps such a source. If the
   * parameter is a [BufferedSource], it adjusts for buffered bytes.
   */
  @Throws(IOException::class)
  fun position(source: Source): Long {
    var source = source
    var bufferSize = 0L

    if (source is RealBufferedSource) {
      bufferSize = source.buffer.size
      source = source.source
    }

    require(source is FileHandleSource && source.fileHandle === this) {
      "source was not created by this FileHandle"
    }
    check(!source.closed) { "closed" }

    return source.position - bufferSize
  }

  /**
   * Change the position of [source] in the file to [position]. The argument [source] must be either
   * a source produced by this file handle, or a [BufferedSource] that directly wraps such a source.
   * If the parameter is a [BufferedSource], it will skip or clear buffered bytes.
   */
  @Throws(IOException::class)
  fun reposition(source: Source, position: Long) {
    if (source is RealBufferedSource) {
      val fileHandleSource = source.source
      require(fileHandleSource is FileHandleSource && fileHandleSource.fileHandle === this) {
        "source was not created by this FileHandle"
      }
      check(!fileHandleSource.closed) { "closed" }

      val bufferSize = source.buffer.size
      val toSkip = position - (fileHandleSource.position - bufferSize)
      if (toSkip in 0L until bufferSize) {
        // The new position requires only a buffer change.
        source.skip(toSkip)
      } else {
        // The new position doesn't share data with the current buffer.
        source.buffer.clear()
        fileHandleSource.position = position
      }
    } else {
      require(source is FileHandleSource && source.fileHandle === this) {
        "source was not created by this FileHandle"
      }
      check(!source.closed) { "closed" }
      source.position = position
    }
  }

  /**
   * Returns a sink that writes to this starting at [fileOffset]. The returned sink must be closed
   * when it is no longer needed.
   */
  @Throws(IOException::class)
  fun sink(fileOffset: Long = 0L): Sink {
    check(readWrite) { "file handle is read-only" }
    lock.withLock {
      check(!closed) { "closed" }
      openStreamCount++
    }
    return FileHandleSink(this, fileOffset)
  }

  /**
   * Returns a sink that writes to this starting at the end. The returned sink must be closed when
   * it is no longer needed.
   */
  @Throws(IOException::class)
  fun appendingSink(): Sink {
    return sink(size())
  }

  /**
   * Returns the position of [sink] in the file. The argument [sink] must be either a sink produced
   * by this file handle, or a [BufferedSink] that directly wraps such a sink. If the parameter is a
   * [BufferedSink], it adjusts for buffered bytes.
   */
  @Throws(IOException::class)
  fun position(sink: Sink): Long {
    var sink = sink
    var bufferSize = 0L

    if (sink is RealBufferedSink) {
      bufferSize = sink.buffer.size
      sink = sink.sink
    }

    require(sink is FileHandleSink && sink.fileHandle === this) {
      "sink was not created by this FileHandle"
    }
    check(!sink.closed) { "closed" }

    return sink.position + bufferSize
  }

  /**
   * Change the position of [sink] in the file to [position]. The argument [sink] must be either a
   * sink produced by this file handle, or a [BufferedSink] that directly wraps such a sink. If the
   * parameter is a [BufferedSink], it emits for buffered bytes.
   */
  @Throws(IOException::class)
  fun reposition(sink: Sink, position: Long) {
    if (sink is RealBufferedSink) {
      val fileHandleSink = sink.sink
      require(fileHandleSink is FileHandleSink && fileHandleSink.fileHandle === this) {
        "sink was not created by this FileHandle"
      }
      check(!fileHandleSink.closed) { "closed" }

      sink.emit()
      fileHandleSink.position = position
    } else {
      require(sink is FileHandleSink && sink.fileHandle === this) {
        "sink was not created by this FileHandle"
      }
      check(!sink.closed) { "closed" }
      sink.position = position
    }
  }

  @Throws(IOException::class)
  final override fun close() {
    lock.withLock {
      if (closed) return
      closed = true
      if (openStreamCount != 0) return
    }
    lock.destroy()
    protectedClose()
  }

  /** Like [read] but not performing any close check. */
  @Throws(IOException::class)
  protected abstract fun protectedRead(
    fileOffset: Long,
    array: ByteArray,
    arrayOffset: Int,
    byteCount: Int,
  ): Int

  /** Like [write] but not performing any close check. */
  @Throws(IOException::class)
  protected abstract fun protectedWrite(
    fileOffset: Long,
    array: ByteArray,
    arrayOffset: Int,
    byteCount: Int,
  )

  /** Like [flush] but not performing any close check. */
  @Throws(IOException::class)
  protected abstract fun protectedFlush()

  /** Like [resize] but not performing any close check. */
  @Throws(IOException::class)
  protected abstract fun protectedResize(size: Long)

  /** Like [size] but not performing any close check. */
  @Throws(IOException::class)
  protected abstract fun protectedSize(): Long

  /**
   * Subclasses should implement this to release resources held by this file handle. It is invoked
   * once both the file handle is closed, and also all sources and sinks produced by it are also
   * closed.
   */
  @Throws(IOException::class)
  protected abstract fun protectedClose()

  private fun readNoCloseCheck(fileOffset: Long, sink: Buffer, byteCount: Long): Long {
    require(byteCount >= 0L) { "byteCount < 0: $byteCount" }

    var currentOffset = fileOffset
    val targetOffset = fileOffset + byteCount

    while (currentOffset < targetOffset) {
      val tail = sink.writableSegment(1)
      val readByteCount = protectedRead(
        fileOffset = currentOffset,
        array = tail.data,
        arrayOffset = tail.limit,
        byteCount = minOf(targetOffset - currentOffset, Segment.SIZE - tail.limit).toInt(),
      )

      if (readByteCount == -1) {
        if (tail.pos == tail.limit) {
          // We allocated a tail segment, but didn't end up needing it. Recycle!
          sink.head = tail.pop()
          SegmentPool.recycle(tail)
        }
        if (fileOffset == currentOffset) return -1L // We wanted bytes but didn't return any.
        break
      }

      tail.limit += readByteCount
      currentOffset += readByteCount
      sink.size += readByteCount
    }

    return currentOffset - fileOffset
  }

  private fun writeNoCloseCheck(fileOffset: Long, source: Buffer, byteCount: Long) {
    checkOffsetAndCount(source.size, 0L, byteCount)

    var currentOffset = fileOffset
    val targetOffset = fileOffset + byteCount

    while (currentOffset < targetOffset) {
      val head = source.head!!
      val toCopy = minOf(targetOffset - currentOffset, head.limit - head.pos).toInt()
      protectedWrite(currentOffset, head.data, head.pos, toCopy)

      head.pos += toCopy
      currentOffset += toCopy
      source.size -= toCopy

      if (head.pos == head.limit) {
        source.head = head.pop()
        SegmentPool.recycle(head)
      }
    }
  }

  private class FileHandleSink(
    val fileHandle: FileHandle,
    var position: Long,
  ) : Sink {
    var closed = false

    override fun write(source: Buffer, byteCount: Long) {
      check(!closed) { "closed" }
      fileHandle.writeNoCloseCheck(position, source, byteCount)
      position += byteCount
    }

    override fun flush() {
      check(!closed) { "closed" }
      fileHandle.protectedFlush()
    }

    override fun timeout() = Timeout.NONE

    override fun close() {
      if (closed) return
      closed = true
      fileHandle.lock.withLock {
        fileHandle.openStreamCount--
        if (fileHandle.openStreamCount != 0 || !fileHandle.closed) return@close
      }
      fileHandle.protectedClose()
    }
  }

  private class FileHandleSource(
    val fileHandle: FileHandle,
    var position: Long,
  ) : Source {
    var closed = false

    override fun read(sink: Buffer, byteCount: Long): Long {
      check(!closed) { "closed" }
      val result = fileHandle.readNoCloseCheck(position, sink, byteCount)
      if (result != -1L) position += result
      return result
    }

    override fun timeout() = Timeout.NONE

    override fun close() {
      if (closed) return
      closed = true
      fileHandle.lock.withLock {
        fileHandle.openStreamCount--
        if (fileHandle.openStreamCount != 0 || !fileHandle.closed) return@close
      }
      fileHandle.protectedClose()
    }
  }
}
