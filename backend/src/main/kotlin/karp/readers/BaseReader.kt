package karp.readers

import java.nio.file.Path

interface BaseReader {
    val extensions: List<String>

    fun read(path: Path): ReadResult
}
