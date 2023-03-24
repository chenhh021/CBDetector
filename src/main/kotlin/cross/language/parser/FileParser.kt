package cross.language.parser

import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import java.nio.charset.StandardCharsets

abstract class FileParser(val path: String) {
//    val charStream: CharStream = CharStreams.fromFileName(this.path)
    val charStream: CharStream = CharStreams.fromFileName(this.path)
    init {
//        println("read source code from $path")
//        println(java.io.File(path).readText())
    }

    abstract val methods: MutableList<Method>

    class Method(val name: String) {
        override fun toString() = this.name
    }
}