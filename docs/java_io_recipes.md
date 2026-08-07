java.io Recipes
===============

These recipes use Okio with `java.io.File` instead of Okio's own `Path` and `FileSystem` types.


Read a text file line-by-line ([Java][ReadJavaIoFileLineByLine]/[Kotlin][ReadJavaIoFileLineByLineKt])
-----------------------------------------------------------------------------------------------------

This is similar to the other [line-by-line example](recipes.md#read-a-text-file-line-by-line-javakotlin), but it uses `java.io.File`
instead of `okio.Path` and `okio.FileSystem`.

=== "Java"

    ```java
    public void readLines(File file) throws IOException {
      try (Source fileSource = Okio.source(file);
           BufferedSource bufferedFileSource = Okio.buffer(fileSource)) {

        while (true) {
          String line = bufferedFileSource.readUtf8Line();
          if (line == null) break;

          if (line.contains("square")) {
            System.out.println(line);
          }
        }

      }
    }
    ```

=== "Kotlin"

    Note that static `Okio` methods become extension functions (`Okio.source(file)` =>
    `file.source()`).

    ```kotlin
    @Throws(IOException::class)
    fun readLines(file: File) {
      file.source().use { fileSource ->
        fileSource.buffer().use { bufferedFileSource ->
          while (true) {
            val line = bufferedFileSource.readUtf8Line() ?: break
            if ("square" in line) {
              println(line)
            }
          }
        }
      }
    }
    ```

Write a text file ([Java][WriteJavaIoFile]/[Kotlin][WriteJavaIoFileKt])
-----------------------------------------------------------------------------------------------------

This is similar to the other [write example](recipes.md#write-a-text-file-javakotlin), but it uses
`java.io.File` instead of `okio.Path` and `okio.FileSystem`.

=== "Java"

    ```java
    public void writeEnv(File file) throws IOException {
      try (Sink fileSink = Okio.sink(file);
           BufferedSink bufferedSink = Okio.buffer(fileSink)) {

        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
          bufferedSink.writeUtf8(entry.getKey());
          bufferedSink.writeUtf8("=");
          bufferedSink.writeUtf8(entry.getValue());
          bufferedSink.writeUtf8("\n");
        }

      }
    }
    ```

=== "Kotlin"

    ```kotlin
    @Throws(IOException::class)
    fun writeEnv(file: File) {
      file.sink().buffer().use { sink ->
        for ((key, value) in System.getenv()) {
          sink.writeUtf8(key)
          sink.writeUtf8("=")
          sink.writeUtf8(value)
          sink.writeUtf8("\n")
        }
      }
    }
    ```


[ReadJavaIoFileLineByLineKt]: https://github.com/lysine-dev/okio/blob/master/samples/src/jvmMain/kotlin/okio/samples/ReadJavaIoFileLineByLine.kt
[ReadJavaIoFileLineByLine]: https://github.com/lysine-dev/okio/blob/master/samples/src/jvmMain/java/okio/samples/ReadJavaIoFileLineByLine.java
[WriteJavaIoFileKt]: https://github.com/lysine-dev/okio/blob/master/samples/src/jvmMain/kotlin/okio/samples/WriteJavaIoFile.kt
[WriteJavaIoFile]: https://github.com/lysine-dev/okio/blob/master/samples/src/jvmMain/java/okio/samples/WriteJavaIoFile.java
