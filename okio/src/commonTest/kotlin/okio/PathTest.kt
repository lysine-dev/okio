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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okio.Path.Companion.toPath

class PathTest {
  @Test
  fun unixRoot() {
    val path = "/".toPath()
    assertEquals(path, path.normalized())
    assertEquals(path, path.root)
    assertEquals(listOf(), path.segments)
    assertEquals("/", path.toString())
    assertNull(path.parent)
    assertNull(path.volumeLetter)
    assertEquals("", path.name)
    assertTrue(path.isAbsolute)
    assertTrue(path.isRoot)
  }

  @Test
  fun unixAbsolutePath() {
    val path = "/home/jesse/todo.txt".toPath()
    assertEquals(path, path.normalized())
    assertEquals("/".toPath(), path.root)
    assertEquals(listOf("home", "jesse", "todo.txt"), path.segments)
    assertEquals("/home/jesse/todo.txt", path.toString())
    assertEquals("/home/jesse".toPath(), path.parent)
    assertNull(path.volumeLetter)
    assertEquals("todo.txt", path.name)
    assertTrue(path.isAbsolute)
    assertFalse(path.isRoot)
  }

  @Test
  fun unixRelativePath() {
    val path = "project/todo.txt".toPath()
    assertEquals(path, path.normalized())
    assertNull(path.root)
    assertEquals(listOf("project", "todo.txt"), path.segments)
    assertEquals("project/todo.txt", path.toString())
    assertEquals("project".toPath(), path.parent)
    assertNull(path.volumeLetter)
    assertEquals("todo.txt", path.name)
    assertFalse(path.isAbsolute)
    assertFalse(path.isRoot)
  }

  @Test
  fun unixRelativePathWithDots() {
    val path = "../../project/todo.txt".toPath()
    assertEquals(path, path.normalized())
    assertNull(path.root)
    assertEquals(listOf("..", "..", "project", "todo.txt"), path.segments)
    assertEquals("../../project/todo.txt", path.toString())
    assertEquals("../../project".toPath(), path.parent)
    assertNull(path.volumeLetter)
    assertEquals("todo.txt", path.name)
    assertFalse(path.isAbsolute)
    assertFalse(path.isRoot)
  }

  @Test
  fun unixRelativeSeriesOfDotDots() {
    val path = "../../..".toPath()
    assertEquals(path, path.normalized())
    assertNull(path.root)
    assertEquals(listOf("..", "..", ".."), path.segments)
    assertEquals("../../..", path.toString())
    assertNull(path.parent)
    assertNull(path.volumeLetter)
    assertEquals("..", path.name)
    assertFalse(path.isAbsolute)
    assertFalse(path.isRoot)
  }

  @Test
  fun unixAbsoluteSeriesOfDotDots() {
    val path = "/../../..".toPath()
    assertEquals(path, path.normalized())
    assertEquals("/".toPath(), path.root)
    assertEquals(listOf(), path.segments)
    assertEquals("/", path.toString())
    assertNull(path.parent)
    assertNull(path.volumeLetter)
    assertEquals("", path.name)
    assertTrue(path.isAbsolute)
    assertTrue(path.isRoot)
  }

  @Test
  fun unixAbsoluteSingleDot() {
    val path = "/.".toPath()
    assertEquals(path, path.normalized())
    assertEquals("/".toPath(), path.root)
    assertEquals(listOf(), path.segments)
    assertEquals("/", path.toString())
    assertNull(path.parent)
    assertNull(path.volumeLetter)
    assertEquals("", path.name)
    assertTrue(path.isAbsolute)
    assertTrue(path.isRoot)
  }

  @Test
  fun unixRelativeDoubleDots() {
    val path = "..".toPath()
    assertEquals(path, path.normalized())
    assertNull(path.root)
    assertEquals(listOf(".."), path.segments)
    assertEquals("..", path.toString())
    assertNull(path.parent)
    assertNull(path.volumeLetter)
    assertEquals("..", path.name)
    assertFalse(path.isAbsolute)
    assertFalse(path.isRoot)
  }

  @Test
  fun unixRelativeSingleDot() {
    val path = ".".toPath()
    assertEquals(path, path.normalized())
    assertNull(path.root)
    assertEquals(listOf("."), path.segments)
    assertEquals(".", path.toString())
    assertNull(path.parent)
    assertNull(path.volumeLetter)
    assertEquals(".", path.name)
    assertFalse(path.isAbsolute)
    assertFalse(path.isRoot)
  }

  @Test
  fun windowsVolumeLetter() {
    val path = "C:\\".toPath()
    assertEquals(path, path.normalized())
    assertEquals("C:\\".toPath(), path.root)
    assertEquals(listOf(), path.segments)
    assertEquals("C:\\", path.toString())
    assertNull(path.parent)
    assertEquals('C', path.volumeLetter)
    assertEquals("", path.name)
    assertTrue(path.isAbsolute)
    assertTrue(path.isRoot)
  }

  @Test
  fun windowsAbsolutePathWithVolumeLetter() {
    val path = "C:\\Windows\\notepad.exe".toPath()
    assertEquals(path, path.normalized())
    assertEquals("C:\\".toPath(), path.root)
    assertEquals(listOf("Windows", "notepad.exe"), path.segments)
    assertEquals("C:\\Windows\\notepad.exe", path.toString())
    assertEquals("C:\\Windows".toPath(), path.parent)
    assertEquals('C', path.volumeLetter)
    assertEquals("notepad.exe", path.name)
    assertTrue(path.isAbsolute)
    assertFalse(path.isRoot)
  }

  @Test
  fun windowsVolumeLetterWithForwardSlash() {
    val path = "C:/".toPath()
    assertEquals(path, path.normalized())
    assertEquals("C:/".toPath(), path.root)
    assertEquals(listOf(), path.segments)
    assertEquals("C:/", path.toString())
    assertNull(path.parent)
    assertEquals('C', path.volumeLetter)
    assertEquals("", path.name)
    assertTrue(path.isAbsolute)
    assertTrue(path.isRoot)
  }

  @Test
  fun windowsAbsolutePathWithVolumeLetterAndForwardSlash() {
    val path = "C:/Windows/notepad.exe".toPath()
    assertEquals(path, path.normalized())
    assertEquals("C:/".toPath(), path.root)
    assertEquals(listOf("Windows", "notepad.exe"), path.segments)
    assertEquals("C:/Windows/notepad.exe", path.toString())
    assertEquals("C:/Windows".toPath(), path.parent)
    assertEquals('C', path.volumeLetter)
    assertEquals("notepad.exe", path.name)
    assertTrue(path.isAbsolute)
    assertFalse(path.isRoot)
  }

  @Test
  fun windowsAbsolutePath() {
    val path = "\\".toPath()
    assertEquals(path, path.normalized())
    assertEquals("\\".toPath(), path.root)
    assertEquals(listOf(), path.segments)
    assertEquals("\\", path.toString())
    assertEquals(null, path.parent)
    assertNull(path.volumeLetter)
    assertEquals("", path.name)
    assertTrue(path.isAbsolute)
    assertTrue(path.isRoot)
  }

  @Test
  fun windowsAbsolutePathWithoutVolumeLetter() {
    val path = "\\Windows\\notepad.exe".toPath()
    assertEquals(path, path.normalized())
    assertEquals("\\".toPath(), path.root)
    assertEquals(listOf("Windows", "notepad.exe"), path.segments)
    assertEquals("\\Windows\\notepad.exe", path.toString())
    assertEquals("\\Windows".toPath(), path.parent)
    assertNull(path.volumeLetter)
    assertEquals("notepad.exe", path.name)
    assertTrue(path.isAbsolute)
    assertFalse(path.isRoot)
  }

  @Test
  fun windowsRelativePathWithVolumeLetter() {
    val path = "C:Windows\\notepad.exe".toPath()
    assertEquals(path, path.normalized())
    assertNull(path.root)
    assertEquals(listOf("C:Windows", "notepad.exe"), path.segments)
    assertEquals("C:Windows\\notepad.exe", path.toString())
    assertEquals("C:Windows".toPath(), path.parent)
    assertEquals('C', path.volumeLetter)
    assertEquals("notepad.exe", path.name)
    assertFalse(path.isAbsolute)
    assertFalse(path.isRoot)
  }

  @Test
  fun windowsVolumeLetterRelative() {
    val path = "C:".toPath()
    assertEquals(path, path.normalized())
    assertNull(path.root)
    assertEquals(listOf("C:"), path.segments)
    assertEquals("C:", path.toString())
    assertNull(path.parent)
    assertEquals('C', path.volumeLetter)
    assertEquals("", path.name)
    assertFalse(path.isAbsolute)
    assertFalse(path.isRoot)
  }

  @Test
  fun windowsRelativePath() {
    val path = "Windows\\notepad.exe".toPath()
    assertEquals(path, path.normalized())
    assertNull(path.root)
    assertEquals(listOf("Windows", "notepad.exe"), path.segments)
    assertEquals("Windows\\notepad.exe", path.toString())
    assertEquals("Windows".toPath(), path.parent)
    assertNull(path.volumeLetter)
    assertEquals("notepad.exe", path.name)
    assertFalse(path.isAbsolute)
    assertFalse(path.isRoot)
  }

  @Test
  fun windowsUncServer() {
    val path = "\\\\server".toPath()
    assertEquals(path, path.normalized())
    assertEquals("\\\\server".toPath(), path.root)
    assertEquals(listOf(), path.segments)
    assertEquals("\\\\server", path.toString())
    assertNull(path.parent)
    assertNull(path.volumeLetter)
    assertEquals("server", path.name)
    assertTrue(path.isAbsolute)
    assertTrue(path.isRoot)
  }

  @Test
  fun windowsUncAbsolutePath() {
    val path = "\\\\server\\project\\notes.txt".toPath()
    assertEquals(path, path.normalized())
    assertEquals("\\\\server".toPath(), path.root)
    assertEquals(listOf("project", "notes.txt"), path.segments)
    assertEquals("\\\\server\\project\\notes.txt", path.toString())
    assertEquals("\\\\server\\project".toPath(), path.parent)
    assertNull(path.volumeLetter)
    assertEquals("notes.txt", path.name)
    assertTrue(path.isAbsolute)
    assertFalse(path.isRoot)
  }

  @Test
  fun absolutePathTraversalWithDivOperator() {
    val root = "/".toPath()
    assertEquals("/home".toPath(), root / "home")
    assertEquals("/home/jesse".toPath(), root / "home" / "jesse")
    assertEquals("/home/jesse/..".toPath(), root / "home" / "jesse" / "..")
    assertEquals("/home/jesse/../jake".toPath(), root / "home" / "jesse" / ".." / "jake")
  }

  @Test
  fun relativePathTraversalWithDivOperator() {
    val slash = Path.DIRECTORY_SEPARATOR
    val cwd = ".".toPath()
    assertEquals("home".toPath(), cwd / "home")
    assertEquals("home${slash}jesse".toPath(), cwd / "home" / "jesse")
    assertEquals("home${slash}jesse$slash..".toPath(), cwd / "home" / "jesse" / "..")
    assertEquals(
      "home${slash}jesse$slash..${slash}jake".toPath(),
      cwd / "home" / "jesse" / ".." / "jake",
    )
  }

  @Test
  fun relativePathTraversalWithDots() {
    val slash = Path.DIRECTORY_SEPARATOR
    val cwd = ".".toPath()
    assertEquals("..".toPath(), cwd / "..")
    assertEquals("..$slash..".toPath(), cwd / ".." / "..")
    assertEquals("..$slash..${slash}etc".toPath(), cwd / ".." / ".." / "etc")
    assertEquals(
      "..$slash..${slash}etc${slash}passwd".toPath(),
      cwd / ".." / ".." / "etc" / "passwd",
    )
  }

  @Test
  fun pathTraversalBaseIgnoredIfChildIsAnAbsolutePath() {
    assertEquals("/home".toPath(), "".toPath() / "/home")
    assertEquals("/home".toPath(), "relative".toPath() / "/home")
    assertEquals("/home".toPath(), "/base".toPath() / "/home")
    assertEquals("/home".toPath(), "/".toPath() / "/home")
  }

  @Test
  fun stringToAbsolutePath() {
    assertEquals("/", "/".toPath().toString())
    assertEquals("/a", "/a".toPath().toString())
    assertEquals("/a", "/a/".toPath().toString())
    assertEquals("/a/b/c", "/a/b/c".toPath().toString())
    assertEquals("/a/b/c", "/a/b/c/".toPath().toString())
    assertEquals("/", "/".toPath(normalize = true).toString())
    assertEquals("/a", "/a".toPath(normalize = true).toString())
    assertEquals("/a", "/a/".toPath(normalize = true).toString())
    assertEquals("/a/b/c", "/a/b/c".toPath(normalize = true).toString())
    assertEquals("/a/b/c", "/a/b/c/".toPath(normalize = true).toString())
  }

  @Test
  fun stringToAbsolutePathWithTraversal() {
    assertEquals("/", "/..".toPath().toString())
    assertEquals("/", "/../".toPath().toString())
    assertEquals("/", "/../..".toPath().toString())
    assertEquals("/", "/../../".toPath().toString())
    assertEquals("/", "/..".toPath(normalize = true).toString())
    assertEquals("/", "/../".toPath(normalize = true).toString())
    assertEquals("/", "/../..".toPath(normalize = true).toString())
    assertEquals("/", "/../../".toPath(normalize = true).toString())
  }

  @Test
  fun stringToAbsolutePathWithEmptySegments() {
    assertEquals("/", "//".toPath().toString())
    assertEquals("/a", "//a".toPath().toString())
    assertEquals("/a", "/a//".toPath().toString())
    assertEquals("/a", "//a//".toPath().toString())
    assertEquals("/a/b", "/a/b//".toPath().toString())
    assertEquals("/", "//".toPath(normalize = true).toString())
    assertEquals("/a", "//a".toPath(normalize = true).toString())
    assertEquals("/a", "/a//".toPath(normalize = true).toString())
    assertEquals("/a", "//a//".toPath(normalize = true).toString())
    assertEquals("/a/b", "/a/b//".toPath(normalize = true).toString())
  }

  @Test
  fun stringToAbsolutePathWithDots() {
    assertEquals("/", "/./".toPath().toString())
    assertEquals("/a", "/./a".toPath().toString())
    assertEquals("/a", "/a/./".toPath().toString())
    assertEquals("/a", "/a//.".toPath().toString())
    assertEquals("/a", "/./a//".toPath().toString())
    assertEquals("/a", "/a/.".toPath().toString())
    assertEquals("/a", "//a/./".toPath().toString())
    assertEquals("/a", "//a/./.".toPath().toString())
    assertEquals("/a/b", "/a/./b/".toPath().toString())
    assertEquals("/", "/./".toPath(normalize = true).toString())
    assertEquals("/a", "/./a".toPath(normalize = true).toString())
    assertEquals("/a", "/a/./".toPath(normalize = true).toString())
    assertEquals("/a", "/a//.".toPath(normalize = true).toString())
    assertEquals("/a", "/./a//".toPath(normalize = true).toString())
    assertEquals("/a", "/a/.".toPath(normalize = true).toString())
    assertEquals("/a", "//a/./".toPath(normalize = true).toString())
    assertEquals("/a", "//a/./.".toPath(normalize = true).toString())
    assertEquals("/a/b", "/a/./b/".toPath(normalize = true).toString())
  }

  @Test
  fun stringToRelativePath() {
    assertEquals(".", "".toPath().toString())
    assertEquals(".", ".".toPath().toString())
    assertEquals("a", "a/".toPath().toString())
    assertEquals("a/b", "a/b".toPath().toString())
    assertEquals("a/b", "a/b/".toPath().toString())
    assertEquals("a/b/c/d", "a/b/c/d".toPath().toString())
    assertEquals("a/b/c/d", "a/b/c/d/".toPath().toString())
    assertEquals(".", "".toPath(normalize = true).toString())
    assertEquals(".", ".".toPath(normalize = true).toString())
    assertEquals("a", "a/".toPath(normalize = true).toString())
    assertEquals("a/b", "a/b".toPath(normalize = true).toString())
    assertEquals("a/b", "a/b/".toPath(normalize = true).toString())
    assertEquals("a/b/c/d", "a/b/c/d".toPath(normalize = true).toString())
    assertEquals("a/b/c/d", "a/b/c/d/".toPath(normalize = true).toString())
  }

  @Test
  fun stringToRelativePathWithTraversal() {
    assertEquals("..", "..".toPath().toString())
    assertEquals("..", "../".toPath().toString())
    assertEquals("a/..", "a/..".toPath().toString())
    assertEquals("a/..", "a/../".toPath().toString())
    assertEquals("a/../..", "a/../..".toPath().toString())
    assertEquals("a/../..", "a/../../".toPath().toString())
    assertEquals("a/../../..", "a/../../..".toPath().toString())
    assertEquals("../../b", "../../b".toPath().toString())
    assertEquals("a/../../../b", "a/../../../b".toPath().toString())
    assertEquals("a/../../../b/../c", "a/../../../b/../c".toPath().toString())
    assertEquals("..", "..".toPath(normalize = true).toString())
    assertEquals("..", "../".toPath(normalize = true).toString())
    assertEquals(".", "a/..".toPath(normalize = true).toString())
    assertEquals(".", "a/../".toPath(normalize = true).toString())
    assertEquals("..", "a/../..".toPath(normalize = true).toString())
    assertEquals("..", "a/../../".toPath(normalize = true).toString())
    assertEquals("../..", "a/../../..".toPath(normalize = true).toString())
    assertEquals("../../b", "../../b".toPath(normalize = true).toString())
    assertEquals("../../b", "a/../../../b".toPath(normalize = true).toString())
    assertEquals("../../c", "a/../../../b/../c".toPath(normalize = true).toString())
  }

  @Test
  fun stringToRelativePathWithEmptySegments() {
    assertEquals("a", "a//".toPath().toString())
    assertEquals("a/b", "a//b".toPath().toString())
    assertEquals("a/b", "a/b//".toPath().toString())
    assertEquals("a/b", "a//b//".toPath().toString())
    assertEquals("a/b/c", "a/b/c//".toPath().toString())
    assertEquals("a", "a//".toPath(normalize = true).toString())
    assertEquals("a/b", "a//b".toPath(normalize = true).toString())
    assertEquals("a/b", "a/b//".toPath(normalize = true).toString())
    assertEquals("a/b", "a//b//".toPath(normalize = true).toString())
    assertEquals("a/b/c", "a/b/c//".toPath(normalize = true).toString())
  }

  @Test
  fun stringToRelativePathWithDots() {
    assertEquals(".", ".".toPath().toString())
    assertEquals(".", "./".toPath().toString())
    assertEquals(".", "././".toPath().toString())
    assertEquals("a/..", "././a/..".toPath().toString())
    assertEquals("a", "a/./".toPath().toString())
    assertEquals("a/b", "a/./b".toPath().toString())
    assertEquals("a/b", "a/b/./".toPath().toString())
    assertEquals("a/b", "a/b//.".toPath().toString())
    assertEquals("a/b", "a/./b//".toPath().toString())
    assertEquals("a/b", "a/b/.".toPath().toString())
    assertEquals("a/b", "a//b/./".toPath().toString())
    assertEquals("a/b", "a//b/./.".toPath().toString())
    assertEquals("a/b/c", "a/b/./c/".toPath().toString())
    assertEquals(".", ".".toPath(normalize = true).toString())
    assertEquals(".", "./".toPath(normalize = true).toString())
    assertEquals(".", "././".toPath(normalize = true).toString())
    assertEquals(".", "././a/..".toPath(normalize = true).toString())
    assertEquals("a", "a/./".toPath(normalize = true).toString())
    assertEquals("a/b", "a/./b".toPath(normalize = true).toString())
    assertEquals("a/b", "a/b/./".toPath(normalize = true).toString())
    assertEquals("a/b", "a/b//.".toPath(normalize = true).toString())
    assertEquals("a/b", "a/./b//".toPath(normalize = true).toString())
    assertEquals("a/b", "a/b/.".toPath(normalize = true).toString())
    assertEquals("a/b", "a//b/./".toPath(normalize = true).toString())
    assertEquals("a/b", "a//b/./.".toPath(normalize = true).toString())
    assertEquals("a/b/c", "a/b/./c/".toPath(normalize = true).toString())
  }

  @Test
  fun composingWindowsPath() {
    assertEquals("C:\\Windows\\notepad.exe".toPath(), "C:\\".toPath() / "Windows" / "notepad.exe")
  }

  @Test
  fun windowsResolveAbsolutePath() {
    assertEquals("\\Users".toPath(), "C:\\Windows".toPath() / "\\Users")
  }

  @Test
  fun windowsPathTraversalUp() {
    assertEquals("C:\\x\\y\\..\\..\\..\\z".toPath(), "C:\\x\\y\\..\\..\\..\\z".toPath())
    assertEquals("C:x\\y\\..\\..\\..\\z".toPath(), "C:x\\y\\..\\..\\..\\z".toPath())
    assertEquals("C:\\z".toPath(), "C:\\x\\y\\..\\..\\..\\z".toPath(normalize = true))
    assertEquals("C:..\\z".toPath(), "C:x\\y\\..\\..\\..\\z".toPath(normalize = true))
  }

  @Test
  fun samePathDifferentSlashesAreNotEqual() {
    assertNotEquals("/a".toPath(), "\\b".toPath())
    assertNotEquals("a/b".toPath(), "a\\b".toPath())
  }

  @Test
  fun samePathNoSlashesAreEqual() {
    assertEquals("a".toPath().parent!!, "a".toPath().parent!!)
    assertEquals("a/b".toPath().parent!!, "a\\b".toPath().parent!!)
  }

  @Test
  fun relativeToWindowsPaths() {
    val a = "C:\\Windows\\notepad.exe".toPath()
    val b = "C:\\".toPath()
    assertRelativeTo(a, b, "..\\..".toPath(), sameAsNio = false)
    assertRelativeTo(b, a, "Windows\\notepad.exe".toPath(), sameAsNio = false)

    val c = "C:\\Windows\\".toPath()
    val d = "C:\\Windows".toPath()
    assertRelativeTo(c, d, ".".toPath())
    assertRelativeTo(d, c, ".".toPath())

    val e = "C:\\Windows\\Downloads\\".toPath()
    val f = "C:\\Windows\\Documents\\Hello.txt".toPath()
    assertRelativeTo(e, f, "..\\Documents\\Hello.txt".toPath(), sameAsNio = false)
    assertRelativeTo(f, e, "..\\..\\Downloads".toPath(), sameAsNio = false)

    val g = "C:\\Windows\\".toPath()
    val h = "D:\\Windows\\".toPath()
    assertRelativeToFails(g, h, sameAsNio = false)
    assertRelativeToFails(h, g, sameAsNio = false)
  }

  @Test
  fun relativeToWindowsUncPaths() {
    val a = "\\\\localhost\\c$\\development\\schema.proto".toPath()
    val b = "\\\\localhost\\c$\\project\\notes.txt".toPath()
    assertRelativeTo(a, b, "..\\..\\project\\notes.txt".toPath(), sameAsNio = false)
    assertRelativeTo(b, a, "..\\..\\development\\schema.proto".toPath(), sameAsNio = false)

    val c = "C:\\Windows\\".toPath()
    val d = "\\\\localhost\\c$\\project\\notes.txt".toPath()
    assertRelativeToFails(c, d, sameAsNio = false)
    assertRelativeToFails(d, c, sameAsNio = false)
  }

  @Test
  fun absoluteUnixRoot() {
    val a = "/Users/jesse/hello.txt".toPath()
    val b = "/".toPath()
    assertRelativeTo(a, b, "../../..".toPath())
    assertRelativeTo(b, a, "Users/jesse/hello.txt".toPath())

    val c = "/Users/jesse/hello.txt".toPath()
    val d = "/Admin/Secret".toPath()
    assertRelativeTo(c, d, "../../../Admin/Secret".toPath())
    assertRelativeTo(d, c, "../../Users/jesse/hello.txt".toPath())

    val e = "/Users/".toPath()
    val f = "/Users".toPath()
    assertRelativeTo(e, f, ".".toPath())
    assertRelativeTo(f, e, ".".toPath())
  }

  @Test
  fun relativeUnixDot() {
    val a = "Users/jesse/hello.txt".toPath()
    val b = ".".toPath()
    assertRelativeTo(a, b, "../../..".toPath(), sameAsNio = false)
    assertRelativeTo(b, a, "Users/jesse/hello.txt".toPath(), sameAsNio = false)

    val c = "Users/./jesse/hello.txt".toPath()
    val d = "Admin/Secret".toPath()
    assertRelativeTo(c, d, "../../../Admin/Secret".toPath())
    assertRelativeTo(d, c, "../../Users/jesse/hello.txt".toPath())

    val e = "Users/".toPath()
    val f = "Users/.".toPath()
    assertRelativeTo(e, f, ".".toPath())
    assertRelativeTo(f, e, ".".toPath())
  }

  // Note that we handle the normalized version of the paths when computing relative paths.
  @Test
  fun relativeToUnnormalizedPath() {
    val a = "Users/../a".toPath() // `a` if normalized.
    val b = "Users/b/../c".toPath() // `Users/c` if normalized.
    assertRelativeToFails(a, b, sameAsNio = false)
    assertRelativeToFails(b, a, sameAsNio = false)
    assertRelativeTo(a.normalized(), b.normalized(), "../Users/c".toPath())
    assertRelativeTo(b.normalized(), a.normalized(), "../../a".toPath())
  }

  @Test
  fun relativeToNormalizedPath() {
    val a = "Users/../a".toPath(normalize = true) // results to `a`.
    val b = "Users/b/../c".toPath(normalize = true) // results to `Users/c`.
    assertRelativeTo(a, b, "../Users/c".toPath())
    assertRelativeTo(b, a, "../../a".toPath())
  }

  @Test
  fun absoluteToRelative() {
    val a = "/Users/jesse/hello.txt".toPath()
    val b = "Desktop/goodbye.txt".toPath()

    var exception = assertRelativeToFails(a, b)
    assertEquals(
      "Paths of different roots cannot be relative to each other: " +
        "Desktop/goodbye.txt and /Users/jesse/hello.txt",
      exception.message,
    )

    exception = assertRelativeToFails(b, a)
    assertEquals(
      "Paths of different roots cannot be relative to each other: " +
        "/Users/jesse/hello.txt and Desktop/goodbye.txt",
      exception.message,
    )
  }

  @Test
  fun absoluteToAbsolute() {
    val a = "/Users/jesse/hello.txt".toPath()
    val b = "/Users/benoit/Desktop/goodbye.txt".toPath()
    assertRelativeTo(a, b, "../../benoit/Desktop/goodbye.txt".toPath())
    assertRelativeTo(b, a, "../../../jesse/hello.txt".toPath())
  }

  @Test
  fun absoluteToSelf() {
    val a = "/Users/jesse/hello.txt".toPath()
    assertRelativeTo(a, a, ".".toPath())

    val b = "/Users/benoit/../jesse/hello.txt".toPath()
    // NIO normalizes.
    assertRelativeTo(a, b, "../../benoit/../jesse/hello.txt".toPath(), sameAsNio = false)
    assertRelativeToFails(b, a, sameAsNio = false)
    assertRelativeTo(b.normalized(), a, ".".toPath())
    assertRelativeTo(a, b.normalized(), ".".toPath())
  }

  @Test
  fun relativeToSelf() {
    val a = "Desktop/hello.txt".toPath()
    assertRelativeTo(a, a, ".".toPath())

    val b = "Documents/../Desktop/hello.txt".toPath()
    // NIO normalizes.
    assertRelativeTo(a, b, "../../Documents/../Desktop/hello.txt".toPath(), sameAsNio = false)
    assertRelativeToFails(b, a, sameAsNio = false)
    assertRelativeTo(a, b.normalized(), ".".toPath())
    assertRelativeTo(b.normalized(), a, ".".toPath())
  }

  @Test
  fun relativeToRelative() {
    val a = "Desktop/documents/resume.txt".toPath()
    val b = "Desktop/documents/2021/taxes.txt".toPath()
    assertRelativeTo(a, b, "../2021/taxes.txt".toPath(), sameAsNio = false)
    assertRelativeTo(b, a, "../../resume.txt".toPath(), sameAsNio = false)

    val c = "documents/resume.txt".toPath()
    val d = "downloads/2021/taxes.txt".toPath()
    assertRelativeTo(c, d, "../../downloads/2021/taxes.txt".toPath(), sameAsNio = false)
    assertRelativeTo(d, c, "../../../documents/resume.txt".toPath(), sameAsNio = false)
  }

  @Test
  fun relativeToRelativeWithMiddleDots() {
    val a = "Desktop/documents/a...n".toPath()
    val b = "Desktop/documents/m...z".toPath()
    assertRelativeTo(a, b, "../m...z".toPath())
    assertRelativeTo(b, a, "../a...n".toPath())
  }

  @Test
  fun relativeToRelativeWithMiddleDotsInCommonPrefix() {
    val a = "Desktop/documents/a...n/red".toPath()
    val b = "Desktop/documents/a...m/blue".toPath()
    assertRelativeTo(a, b, "../../a...m/blue".toPath())
    assertRelativeTo(b, a, "../../a...n/red".toPath())
  }

  @Test
  fun relativeToRelativeWithUpNavigationPrefix() {
    // We can't navigate from 'taxes' to 'resumes' because we don't know the name of 'Documents'.
    //   /Users/jwilson/Documents/2021/Current
    //   /Users/jwilson/Documents/resumes
    //   /Users/jwilson/taxes
    val a = "../../resumes".toPath()
    val b = "../../../taxes".toPath()
    assertRelativeTo(a, b, "../../taxes".toPath())
    assertRelativeToFails(b, a, sameAsNio = false)
  }

  @Test
  fun relativeToRelativeDifferentSlashes() {
    val a = "Desktop/documents/resume.txt".toPath()
    val b = "Desktop\\documents\\2021\\taxes.txt".toPath()
    assertRelativeTo(a, b, "../2021/taxes.txt".toPath(), sameAsNio = false)
    assertRelativeTo(b, a, "..\\..\\resume.txt".toPath(), sameAsNio = false)

    val c = "documents/resume.txt".toPath()
    val d = "downloads\\2021\\taxes.txt".toPath()
    assertRelativeTo(c, d, "../../downloads/2021/taxes.txt".toPath(), sameAsNio = false)
    assertRelativeTo(d, c, "..\\..\\..\\documents\\resume.txt".toPath(), sameAsNio = false)
  }

  @Test
  fun windowsUncPathsDoNotDotDot() {
    assertEquals(
      """\\localhost\c$\Windows""",
      """\\localhost\c$\Windows""".toPath().toString(),
    )
    assertEquals(
      """\\127.0.0.1\c$\Windows""",
      """\\127.0.0.1\c$\Windows""".toPath().toString(),
    )
    assertEquals(
      """\\127.0.0.1\c$\Windows\..\Windows""",
      """\\127.0.0.1\c$\Windows\..\Windows""".toPath().toString(),
    )
    assertEquals(
      """\\127.0.0.1\..\localhost\c$\Windows""",
      """\\127.0.0.1\..\localhost\c$\Windows""".toPath().toString(),
    )
    assertEquals(
      """\\127.0.0.1\c$\..\d$""",
      """\\127.0.0.1\c$\..\d$""".toPath().toString(),
    )

    assertEquals(
      """\\localhost\c$\Windows""",
      """\\localhost\c$\Windows""".toPath(normalize = true).toString(),
    )
    assertEquals(
      """\\127.0.0.1\c$\Windows""",
      """\\127.0.0.1\c$\Windows""".toPath(normalize = true).toString(),
    )
    assertEquals(
      """\\127.0.0.1\c$\Windows""",
      """\\127.0.0.1\c$\Windows\..\Windows""".toPath(normalize = true).toString(),
    )
    assertEquals(
      """\\127.0.0.1\localhost\c$\Windows""",
      """\\127.0.0.1\..\localhost\c$\Windows""".toPath(normalize = true).toString(),
    )
    assertEquals(
      """\\127.0.0.1\d$""",
      """\\127.0.0.1\c$\..\d$""".toPath(normalize = true).toString(),
    )
    assertEquals(
      """\\127.0.0.1\c$""",
      """\\..\127.0.0.1\..\c$""".toPath(normalize = true).toString(),
    )
  }

  @Test fun normalizeAbsolute() {
    assertEquals("/", "/.".toPath(normalize = true).toString())
    assertEquals("/", "/.".toPath(normalize = false).toString())
    assertEquals("/", "/..".toPath(normalize = true).toString())
    assertEquals("/", "/..".toPath(normalize = false).toString())
    assertEquals("/", "/../..".toPath(normalize = true).toString())
    assertEquals("/", "/../..".toPath(normalize = false).toString())

    assertEquals("/a/b", "/a/./b".toPath(normalize = true).toString())
    assertEquals("/a/b", "/a/./b".toPath(normalize = false).toString())
    assertEquals("/a/.../b", "/a/..././b".toPath(normalize = true).toString())
    assertEquals("/a/.../b", "/a/..././b".toPath(normalize = false).toString())
    assertEquals("/", "/a/..".toPath(normalize = true).toString())
    assertEquals("/a/..", "/a/..".toPath(normalize = false).toString())
    assertEquals("/b", "/../a/../b".toPath(normalize = true).toString())
    assertEquals("/a/../b", "/../a/../b".toPath(normalize = false).toString())
  }

  @Test fun normalizeRelative() {
    assertEquals(".", ".".toPath(normalize = true).toString())
    assertEquals(".", ".".toPath(normalize = false).toString())
    assertEquals("..", "..".toPath(normalize = true).toString())
    assertEquals("..", "..".toPath(normalize = false).toString())
    assertEquals("../..", "../..".toPath(normalize = true).toString())
    assertEquals("../..", "../..".toPath(normalize = false).toString())

    assertEquals("a/b", "a/./b".toPath(normalize = true).toString())
    assertEquals("a/b", "a/./b".toPath(normalize = false).toString())
    assertEquals("a/.../b", "a/..././b".toPath(normalize = true).toString())
    assertEquals("a/.../b", "a/..././b".toPath(normalize = false).toString())
    assertEquals(".", "a/..".toPath(normalize = true).toString())
    assertEquals("a/..", "a/..".toPath(normalize = false).toString())
    assertEquals("../b", "../a/../b".toPath(normalize = true).toString())
    assertEquals("../a/../b", "../a/../b".toPath(normalize = false).toString())
  }

  @Test fun normalized() {
    val normalizedRoot = "/".toPath()
    assertEquals(normalizedRoot, "/a/..".toPath(normalize = true))
    assertEquals(normalizedRoot, "/a/..".toPath(normalize = false).normalized())
    assertEquals(normalizedRoot, "/a/..".toPath(normalize = true).normalized())

    val normalizedRelative = "../b".toPath()
    assertEquals(normalizedRelative, "../a/../b".toPath(normalize = true))
    assertEquals(normalizedRelative, "../a/../b".toPath(normalize = false).normalized())
    assertEquals(normalizedRelative, "../a/../b".toPath(normalize = true).normalized())
  }
}
