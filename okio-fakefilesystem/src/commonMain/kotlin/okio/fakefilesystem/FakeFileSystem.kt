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
package okio.fakefilesystem

import kotlin.jvm.JvmName
import kotlin.reflect.KClass
import kotlin.time.Instant
import kotlinx.datetime.Clock
import okio.ArrayIndexOutOfBoundsException
import okio.Buffer
import okio.ByteString
import okio.FileHandle
import okio.FileLock
import okio.FileMetadata
import okio.FileNotFoundException
import okio.FileSystem
import okio.IOException
import okio.LockMode
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source
import okio.fakefilesystem.FakeFileSystem.Element.Directory
import okio.fakefilesystem.FakeFileSystem.Element.File
import okio.fakefilesystem.FakeFileSystem.Element.Symlink
import okio.fakefilesystem.FakeFileSystem.Operation.READ
import okio.fakefilesystem.FakeFileSystem.Operation.WRITE

/**
 * A fully in-memory file system useful for testing. It includes features to support writing
 * better tests.
 *
 * Use [openPaths] to see which paths have been opened for read or write, but not yet closed. Tests
 * should call [checkNoOpenFiles] in `tearDown()` to confirm that no file streams were leaked.
 *
 * Strict By Default
 * -----------------
 *
 * These actions are not allowed and throw an [IOException] if attempted:
 *
 *  * Moving a file that is currently open for reading or writing.
 *  * Deleting a file that is currently open for reading or writing.
 *  * Moving a file to a path that currently resolves to an empty directory.
 *  * Reading and writing the same file at the same time.
 *  * Opening a file for writing that is already open for writing.
 *
 * Programs that do not attempt any of the above operations should work fine on both UNIX and
 * Windows systems. Relax these constraints individually or call [emulateWindows] or [emulateUnix];
 * to apply the constraints of a particular operating system.
 *
 * Closeable
 * ---------
 *
 * This file system cannot be used after it is closed. Closing it does not close any of its open
 * streams; those must be closed directly.
 */
class FakeFileSystem private constructor(
  private val clockNowMillis: () -> Long,
) : FileSystem() {

  constructor() : this(clockNowMillis = defaultClockNowMillis)

  constructor(clock: kotlin.time.Clock = kotlin.time.Clock.System) : this(
    clockNowMillis = { clock.now().toEpochMilliseconds() },
  )

  // Avoid calling kotlinx.datetime.Clock.System.now() because it crashes at runtime if the Kotlin
  // stdlib isn't 2.1.20+. (That'll be the case when running in Gradle 8.x.)
  @Deprecated(
    "Use the constructor that accepts a kotlin.time.Clock, or the no-args constructor",
    level = DeprecationLevel.HIDDEN,
  )
  constructor(clock: Clock = Clock.System) : this(
    when {
      clock === Clock.System -> defaultClockNowMillis
      else -> {
        { clock.now().toEpochMilliseconds() }
      }
    },
  )

  /** Returns the clock used to timestamp files. */
  // We construct this on-demand to avoid a NoClassDefFoundError if there's no kotlin.time.Clock.
  val clock: kotlin.time.Clock
    get() = object : kotlin.time.Clock {
      override fun now() = Instant.fromEpochMilliseconds(clockNowMillis())
    }

  /** File system roots. Each element is a Directory and is created on-demand. */
  private val roots = mutableMapOf<Path, Directory>()

  /** Files that are currently open and need to be closed to avoid resource leaks. */
  private val openFiles = mutableListOf<OpenFile>()

  /** Lock coordination points. */
  private val locks = mutableMapOf<Path, FakeFileSystemLock>()

  /** Forbid all access after [close]. */
  private var closed = false

  /**
   * An absolute path with this file system's current working directory. Relative paths will be
   * resolved against this directory when they are used.
   */
  var workingDirectory: Path = "/".toPath()
    set(value) {
      require(value.isAbsolute) {
        "expected an absolute path but was $value"
      }
      field = value
    }

  /**
   * True to allow files to be moved even if they're currently open for read or write. UNIX file
   * systems typically allow open files to be moved; Windows file systems do not.
   */
  var allowMovingOpenFiles = false

  /**
   * True to allow files to be deleted even if they're currently open for read or write. UNIX file
   * systems typically allow open files to be deleted; Windows file systems do not.
   */
  var allowDeletingOpenFiles = false

  /**
   * True to allow the target of an [atomicMove] operation to be an empty directory. Windows file
   * systems typically allow files to replace empty directories; UNIX file systems do not.
   */
  var allowClobberingEmptyDirectories = false

  /**
   * True to permit a file to have multiple [sinks][sink] open at the same time. Both Windows and
   * UNIX file systems permit this but the result may be undefined.
   */
  var allowWritesWhileWriting = false

  /**
   * True to permit a file to have a [source] and [sink] open at the same time. Both Windows and
   * UNIX file systems permit this but the result may be undefined.
   */
  var allowReadsWhileWriting = false

  /**
   * True to allow symlinks to be created. UNIX file systems typically allow symlinks; Windows file
   * systems do not. Setting this to false after creating a symlink does not prevent that symlink
   * from being returned or used.
   */
  var allowSymlinks = false

  /**
   * Canonical paths for every file and directory in this file system. This omits file system roots
   * like `C:\` and `/`.
   */
  @get:JvmName("allPaths")
  val allPaths: Set<Path>
    get() {
      val result = mutableListOf<Path>()
      for (path in roots.keys) {
        result += listRecursively(path)
      }
      result.sort()
      return result.toSet()
    }

  /**
   * Canonical paths currently opened for reading or writing in the order they were opened. This may
   * contain duplicates if a single path is open by multiple readers.
   *
   * Note that this may contain paths not present in [allPaths]. This occurs if a file is deleted
   * while it is still open.
   *
   * The returned list is ordered by the order that the paths were opened.
   */
  @get:JvmName("openPaths")
  val openPaths: List<Path>
    get() = openFiles.map { it.canonicalPath }

  /**
   * Confirm that all files that have been opened on this file system (with [source], [sink], and
   * [appendingSink]) have since been closed. Call this in your test's `tearDown()` function to
   * confirm that your program hasn't leaked any open files.
   *
   * Forgetting to close a file on a real file system is a severe error that may lead to a program
   * crash. The operating system enforces a limit on how many files may be open simultaneously. On
   * Linux this is [getrlimit] and is commonly adjusted with the `ulimit` command.
   *
   * [getrlimit]: https://man7.org/linux/man-pages/man2/getrlimit.2.html
   *
   * @throws IllegalStateException if any files are open when this function is called.
   */
  fun checkNoOpenFiles() {
    val firstOpenFile = openFiles.firstOrNull() ?: return
    throw IllegalStateException(
      """
      |expected 0 open files, but found:
      |    ${openFiles.joinToString(separator = "\n    ") { it.canonicalPath.toString() }}
      """.trimMargin(),
      firstOpenFile.backtrace,
    )
  }

  /**
   * Configure this file system to use a Windows-like working directory (`F:\`, unless the working
   * directory is already Windows-like) and to follow a Windows-like policy on what operations
   * are permitted.
   */
  fun emulateWindows() {
    if ("\\" !in workingDirectory.toString()) {
      workingDirectory = "F:\\".toPath()
    }
    allowMovingOpenFiles = false
    allowDeletingOpenFiles = false
    allowClobberingEmptyDirectories = true
    allowWritesWhileWriting = true
    allowReadsWhileWriting = true
  }

  /**
   * Configure this file system to use a UNIX-like working directory (`/`, unless the working
   * directory is already UNIX-like) and to follow a UNIX-like policy on what operations are
   * permitted.
   */
  fun emulateUnix() {
    if ("/" !in workingDirectory.toString()) {
      workingDirectory = "/".toPath()
    }
    allowMovingOpenFiles = true
    allowDeletingOpenFiles = true
    allowClobberingEmptyDirectories = false
    allowWritesWhileWriting = true
    allowReadsWhileWriting = true
    allowSymlinks = true
  }

  override fun canonicalize(path: Path): Path {
    val canonicalPath = canonicalizeInternal(path)

    val lookupResult = lookupPath(canonicalPath)
    if (lookupResult?.element == null) {
      throw FileNotFoundException("no such file: $path")
    }

    return lookupResult.path
  }

  /** Don't throw [FileNotFoundException] if the path doesn't identify a file. */
  private fun canonicalizeInternal(path: Path): Path {
    check(!closed) { "closed" }
    return workingDirectory.resolve(path, normalize = true)
  }

  /**
   * Sets the metadata of type [type] on [path] to [value]. If [value] is null this clears that
   * metadata.
   *
   * Extras are not copied by [copy] but they are moved with [atomicMove].
   *
   * @throws IOException if [path] does not exist.
   */
  @Throws(IOException::class)
  fun <T : Any> setExtra(path: Path, type: KClass<out T>, value: T?) {
    val canonicalPath = canonicalizeInternal(path)
    val lookupResult = lookupPath(
      canonicalPath = canonicalPath,
      createRootOnDemand = canonicalPath.isRoot,
      resolveLastSymlink = false,
    )
    val element = lookupResult?.element ?: throw FileNotFoundException("no such file: $path")
    if (value == null) {
      element.extras.remove(type)
    } else {
      element.extras[type] = value
    }
  }

  override fun metadataOrNull(path: Path): FileMetadata? {
    val canonicalPath = canonicalizeInternal(path)
    val lookupResult = lookupPath(
      canonicalPath = canonicalPath,
      createRootOnDemand = canonicalPath.isRoot,
      resolveLastSymlink = false,
    )
    return lookupResult?.element?.metadata
  }

  override fun list(dir: Path): List<Path> = list(dir, throwOnFailure = true)!!

  override fun listOrNull(dir: Path): List<Path>? = list(dir, throwOnFailure = false)

  private fun list(dir: Path, throwOnFailure: Boolean): List<Path>? {
    val canonicalPath = canonicalizeInternal(dir)
    val lookupResult = lookupPath(canonicalPath)
    if (lookupResult?.element == null) {
      if (throwOnFailure) throw FileNotFoundException("no such directory: $dir") else return null
    }
    val element = lookupResult.element as? Directory
      ?: if (throwOnFailure) throw IOException("not a directory: $dir") else return null

    element.access(nowMillis = clockNowMillis())
    return element.children.keys.map { dir / it }.sorted()
  }

  override fun source(file: Path): Source {
    val fileHandle = openReadOnly(file)
    return fileHandle.source()
      .also { fileHandle.close() }
  }

  override fun sink(file: Path, mustCreate: Boolean): Sink {
    val fileHandle = open(file, readWrite = true, mustCreate = mustCreate)
    fileHandle.resize(0L) // If the file already has data, get rid of it.
    return fileHandle.sink()
      .also { fileHandle.close() }
  }

  override fun appendingSink(file: Path, mustExist: Boolean): Sink {
    val fileHandle = open(file, readWrite = true, mustExist = mustExist)
    return fileHandle.appendingSink()
      .also { fileHandle.close() }
  }

  override fun openReadOnly(file: Path): FileHandle {
    return open(file, readWrite = false)
  }

  override fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean): FileHandle {
    return open(file, readWrite = true, mustCreate = mustCreate, mustExist = mustExist)
  }

  private fun open(
    file: Path,
    readWrite: Boolean,
    mustCreate: Boolean = false,
    mustExist: Boolean = false,
  ): FileHandle {
    require(!mustCreate || !mustExist) {
      "Cannot require mustCreate and mustExist at the same time."
    }

    val canonicalPath = canonicalizeInternal(file)
    val lookupResult = lookupPath(canonicalPath, createRootOnDemand = readWrite)
    val nowMillis = clockNowMillis()
    val element: File
    val operation: Operation

    if (lookupResult?.element == null && mustExist) {
      throw IOException("$file doesn't exist.")
    }
    if (lookupResult?.element != null && mustCreate) {
      throw IOException("$file already exists.")
    }

    if (readWrite) {
      // Note that this case is used for both write and read/write.
      if (lookupResult?.element is Directory) {
        throw IOException("destination is a directory: $file")
      }
      if (!allowWritesWhileWriting) {
        findOpenFile(canonicalPath, operation = WRITE)?.let {
          throw IOException("file is already open for writing $file", it.backtrace)
        }
      }
      if (!allowReadsWhileWriting) {
        findOpenFile(canonicalPath, operation = READ)?.let {
          throw IOException("file is already open for reading $file", it.backtrace)
        }
      }

      val parent = lookupResult?.parent
        ?: throw FileNotFoundException("parent directory does not exist")
      parent.access(nowMillis, true)

      val existing = lookupResult.element
      element = File(createdAtMillis = existing?.createdAtMillis ?: nowMillis)
      parent.children[lookupResult.segment!!] = element
      operation = WRITE

      if (existing is File) {
        element.data = existing.data
      }
    } else {
      val existing = lookupResult?.element
        ?: throw FileNotFoundException("no such file: $file")
      element = existing as? File ?: throw IOException("not a file: $file")
      operation = READ

      if (!allowReadsWhileWriting) {
        findOpenFile(canonicalPath, operation = WRITE)?.let {
          throw IOException("file is already open for writing $file", it.backtrace)
        }
      }
    }

    element.access(nowMillis = clockNowMillis(), modified = readWrite)

    val openFile = OpenFile(canonicalPath, operation, Exception("file opened for $operation here"))
    openFiles += openFile

    return FakeFileHandle(
      readWrite = readWrite,
      openFile = openFile,
      file = element,
    )
  }

  override fun createDirectory(dir: Path, mustCreate: Boolean) {
    val canonicalPath = canonicalizeInternal(dir)

    val lookupResult = lookupPath(canonicalPath, createRootOnDemand = true)

    if (canonicalPath.isRoot) {
      // Looking it up was sufficient. Don't crash when creating roots that already exist.
      return
    }

    if (mustCreate && lookupResult?.element != null) {
      throw IOException("already exists: $dir")
    }

    val parentDirectory = lookupResult.requireParent()
    parentDirectory.children[canonicalPath.nameBytes] =
      Directory(createdAtMillis = clockNowMillis())
  }

  override fun atomicMove(
    source: Path,
    target: Path,
  ) {
    val canonicalSource = canonicalizeInternal(source)
    val canonicalTarget = canonicalizeInternal(target)

    val targetLookupResult = lookupPath(canonicalTarget, createRootOnDemand = true)
    val sourceLookupResult = lookupPath(canonicalSource, resolveLastSymlink = false)

    // Universal constraints.
    if (targetLookupResult?.element is Directory) {
      throw IOException("target is a directory: $target")
    }
    val targetParent = targetLookupResult.requireParent()
    if (!allowMovingOpenFiles) {
      findOpenFile(canonicalSource)?.let {
        throw IOException("source is open $source", it.backtrace)
      }
      findOpenFile(canonicalTarget)?.let {
        throw IOException("target is open $target", it.backtrace)
      }
    }
    if (!allowClobberingEmptyDirectories) {
      if (sourceLookupResult?.element is Directory && targetLookupResult?.element is File) {
        throw IOException("source is a directory and target is a file")
      }
    }

    val sourceParent = sourceLookupResult.requireParent()
    val removed = sourceParent.children.remove(canonicalSource.nameBytes)
      ?: throw FileNotFoundException("source doesn't exist: $source")
    targetParent.children[canonicalTarget.nameBytes] = removed
  }

  override fun delete(path: Path, mustExist: Boolean) {
    val canonicalPath = canonicalizeInternal(path)

    val lookupResult = lookupPath(
      canonicalPath = canonicalPath,
      createRootOnDemand = true,
      resolveLastSymlink = false,
    )

    if (lookupResult?.element == null) {
      if (mustExist) {
        throw FileNotFoundException("no such file: $path")
      } else {
        return
      }
    }

    if (lookupResult.element is Directory && lookupResult.element.children.isNotEmpty()) {
      throw IOException("non-empty directory")
    }

    if (!allowDeletingOpenFiles) {
      findOpenFile(canonicalPath)?.let {
        throw IOException("file is open $path", it.backtrace)
      }
    }

    val directory = lookupResult.requireParent()
    directory.children.remove(canonicalPath.nameBytes)
  }

  override fun createSymlink(
    source: Path,
    target: Path,
  ) {
    val canonicalSource = canonicalizeInternal(source)

    val existingLookupResult = lookupPath(canonicalSource, createRootOnDemand = true)
    if (existingLookupResult?.element != null) {
      throw IOException("already exists: $source")
    }
    val parent = existingLookupResult.requireParent()

    if (!allowSymlinks) {
      throw IOException("symlinks are not supported")
    }

    parent.children[canonicalSource.nameBytes] = Symlink(createdAtMillis = clockNowMillis(), target)
  }

  /**
   * Walks the file system looking for [canonicalPath], following symlinks encountered along the
   * way. This function is designed to be used both when looking up existing files and when creating
   * new files into an existing directory.
   *
   * It returns either:
   *
   *  * a path lookup result with an element if that file or directory or symlink exists. This is
   *    useful when reading or writing an existing fie.
   *
   *  * a path lookup result that only got as far as the canonical path's parent, if the parent
   *    exists but the child file does not. This is useful when creating a new file.
   *
   *  * null, if not even the parent directory exists. A file cannot yet be created with this path
   *    because there is no parent to attach it to.
   *
   * This will create the root of the returned path if it does not exist.
   *
   * @param canonicalPath a normalized path, typically the result of [FakeFileSystem.canonicalizeInternal].
   * @param recurseCount used internally to detect cycles.
   * @param resolveLastSymlink true if the result's element must not itself be a symlink. Use this
   *     for looking up metadata, or operations that apply to the path like delete and move. We
   *     always follow symlinks for enclosing directories.
   * @param createRootOnDemand true to create a root directory like `C:\` or `/` if it doesn't
   *     exist. Pass true for mutating operations.
   */
  private fun lookupPath(
    canonicalPath: Path,
    recurseCount: Int = 0,
    resolveLastSymlink: Boolean = true,
    createRootOnDemand: Boolean = false,
  ): PathLookupResult? {
    // 40 is chosen for consistency with the Linux kernel (which previously used 8).
    if (recurseCount > 40) {
      throw IOException("symlink cycle?")
    }

    val rootPath = canonicalPath.root!!
    var root = roots[rootPath]

    // If the path is a root, create it on demand.
    if (root == null) {
      if (!createRootOnDemand) return null
      root = Directory(createdAtMillis = clockNowMillis())
      roots[rootPath] = root
    }

    var parent: Directory? = null
    var lastSegment: ByteString? = null
    var current: Element = root
    var currentPath: Path = rootPath

    var segmentsTraversed = 0
    val segments = canonicalPath.segmentsBytes
    for (segment in segments) {
      lastSegment = segment

      // Push the newest segment.
      if (current !is Directory) {
        throw IOException("not a directory: $currentPath")
      }
      parent = current
      current = current.children[segment] ?: break
      currentPath /= segment
      segmentsTraversed++

      // If it's a symlink, recurse to follow it.
      val isLastSegment = segmentsTraversed == segments.size
      val followSymlinks = !isLastSegment || resolveLastSymlink
      if (current is Symlink && followSymlinks) {
        current.access(nowMillis = clockNowMillis())
        // We wanna normalize it in case the target is relative and starts with `..`.
        currentPath = currentPath.parent!!.resolve(current.target, normalize = true)
        val symlinkLookupResult = lookupPath(
          canonicalPath = currentPath,
          recurseCount = recurseCount + 1,
          createRootOnDemand = createRootOnDemand,
        ) ?: break
        parent = symlinkLookupResult.parent
        lastSegment = symlinkLookupResult.segment
        current = symlinkLookupResult.element ?: break
        currentPath = symlinkLookupResult.path
      }
    }

    return when (segmentsTraversed) {
      segments.size -> {
        PathLookupResult(currentPath, parent, lastSegment, current) // The file.
      }
      segments.size - 1 -> {
        PathLookupResult(currentPath, parent, lastSegment, null) // The enclosing directory.
      }
      else -> null // We found nothing.
    }
  }

  private class PathLookupResult(
    /** The canonical path for the looked up path or its enclosing directory. */
    val path: Path,
    /** Only null if the looked up path is a root. */
    val parent: Directory?,
    /** Only null if the looked up path is a root. */
    val segment: ByteString?,
    /** Non-null if this is a root. Also not null if this file exists. */
    val element: Element?,
  )

  private fun PathLookupResult?.requireParent(): Directory {
    return this?.parent ?: throw IOException("parent directory does not exist")
  }

  private sealed class Element(
    val createdAtMillis: Long,
  ) {
    var lastModifiedAtMillis: Long = createdAtMillis
    var lastAccessedAtMillis: Long = createdAtMillis
    val extras = mutableMapOf<KClass<*>, Any>()

    class File(createdAtMillis: Long) : Element(createdAtMillis) {
      var data: ByteString = ByteString.EMPTY

      override val metadata: FileMetadata
        get() = FileMetadata(
          isRegularFile = true,
          size = data.size.toLong(),
          createdAtMillis = createdAtMillis,
          lastModifiedAtMillis = lastModifiedAtMillis,
          lastAccessedAtMillis = lastAccessedAtMillis,
          extras = extras,
        )
    }

    class Directory(createdAtMillis: Long) : Element(createdAtMillis) {
      /** Keys are path segments. */
      val children = mutableMapOf<ByteString, Element>()

      override val metadata: FileMetadata
        get() = FileMetadata(
          isDirectory = true,
          createdAtMillis = createdAtMillis,
          lastModifiedAtMillis = lastModifiedAtMillis,
          lastAccessedAtMillis = lastAccessedAtMillis,
          extras = extras,
        )
    }

    class Symlink(
      createdAtMillis: Long,
      /** This may be an absolute or relative path. */
      val target: Path,
    ) : Element(createdAtMillis) {
      override val metadata: FileMetadata
        get() = FileMetadata(
          symlinkTarget = target,
          createdAtMillis = createdAtMillis,
          lastModifiedAtMillis = lastModifiedAtMillis,
          lastAccessedAtMillis = lastAccessedAtMillis,
          extras = extras,
        )
    }

    fun access(
      nowMillis: Long,
      modified: Boolean = false,
    ) {
      lastAccessedAtMillis = nowMillis
      if (modified) {
        lastModifiedAtMillis = nowMillis
      }
    }

    abstract val metadata: FileMetadata
  }

  private fun findOpenFile(
    canonicalPath: Path,
    operation: Operation? = null,
  ): OpenFile? {
    return openFiles.firstOrNull {
      it.canonicalPath == canonicalPath && (operation == null || operation == it.operation)
    }
  }

  private fun checkOffsetAndCount(
    size: Long,
    offset: Long,
    byteCount: Long,
  ) {
    if (offset or byteCount < 0 || offset > size || size - offset < byteCount) {
      throw ArrayIndexOutOfBoundsException("size=$size offset=$offset byteCount=$byteCount")
    }
  }

  private class OpenFile(
    val canonicalPath: Path,
    val operation: Operation,
    val backtrace: Throwable,
  )

  private enum class Operation {
    READ,
    WRITE,
  }

  private inner class FakeFileHandle(
    readWrite: Boolean,
    private val openFile: OpenFile,
    private val file: File,
  ) : FileHandle(readWrite) {
    private var closed = false

    override fun protectedResize(size: Long) {
      check(!closed) { "closed" }

      val delta = size - file.data.size
      if (delta > 0) {
        file.data = Buffer()
          .write(file.data)
          .write(ByteArray(delta.toInt()))
          .readByteString()
      } else {
        file.data = file.data.substring(0, size.toInt())
      }

      file.access(nowMillis = clockNowMillis(), modified = true)
    }

    override fun protectedSize(): Long {
      check(!closed) { "closed" }
      return file.data.size.toLong()
    }

    override fun protectedRead(
      fileOffset: Long,
      array: ByteArray,
      arrayOffset: Int,
      byteCount: Int,
    ): Int {
      check(!closed) { "closed" }
      checkOffsetAndCount(array.size.toLong(), arrayOffset.toLong(), byteCount.toLong())

      val fileOffsetInt = fileOffset.toInt()
      val toCopy = minOf(file.data.size - fileOffsetInt, byteCount)
      if (toCopy <= 0) return -1
      file.data.copyInto(fileOffsetInt, array, arrayOffset, toCopy)
      return toCopy
    }

    override fun protectedWrite(
      fileOffset: Long,
      array: ByteArray,
      arrayOffset: Int,
      byteCount: Int,
    ) {
      check(!closed) { "closed" }
      checkOffsetAndCount(array.size.toLong(), arrayOffset.toLong(), byteCount.toLong())

      val buffer = Buffer()
      buffer.write(file.data, 0, minOf(fileOffset.toInt(), file.data.size))
      while (buffer.size < fileOffset) {
        buffer.writeByte(0)
      }
      buffer.write(array, arrayOffset, byteCount)
      if (buffer.size < file.data.size) {
        buffer.write(file.data, buffer.size.toInt(), file.data.size - buffer.size.toInt())
      }
      file.data = buffer.snapshot()
      file.access(nowMillis = clockNowMillis(), modified = true)
    }

    override fun protectedFlush() {
      check(!closed) { "closed" }
    }

    override fun protectedClose() {
      if (closed) return
      closed = true
      file.access(nowMillis = clockNowMillis(), modified = readWrite)
      openFiles -= openFile
    }

    override fun toString() = "FileHandler(${openFile.canonicalPath})"
  }

  override fun close() {
    closed = true
  }

  override fun toString() = "FakeFileSystem"

  /**
   * Obtain an Exclusive or Shared Lock on [path].
   *
   * @throws IOException if [path] does not exist, the FileSystem or the path does not
   * support file locking, or the lock cannot be acquired. This method does not wait for the lock
   * to become available.
   */
  @Throws(IOException::class)
  override fun lock(
    path: Path,
    mode: LockMode,
  ): FileLock {
    val canonicalPath = canonicalizeInternal(path)
    return locks.getOrPut(canonicalPath) {
      FakeFileSystemLock(canonicalPath)
    }.lock(mode)
  }
}

expect class FakeFileSystemLock constructor(path: Path) {
  fun lock(mode: LockMode): FileLock
}
