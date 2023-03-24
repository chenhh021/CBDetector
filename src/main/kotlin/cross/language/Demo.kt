package cross.language

import ch.uzh.ifi.seal.changedistiller.JavaChangeDistillerModule
import ch.uzh.ifi.seal.changedistiller.distilling.FileDistiller
import ch.uzh.ifi.seal.changedistiller.model.entities.SourceCodeChange
import com.google.inject.Guice
import cross.language.parser.FileParserCSharp
import cross.language.parser.FileParserJava
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.lang.Integer.max

object Demo {
    fun run() {
        val javaFiles: List<FileInfo> = Json.decodeFromString(File("data/javaFiles.txt").readText())
        val csharpFiles: List<FileInfo> = Json.decodeFromString(File("data/csharpFiles.txt").readText())
        println(javaFiles[111].path)

        val bugs: List<FileInfo> = Json.decodeFromString(File("data/bugs.txt").readText())

        var count = 0
        val debug = true
        for (i in bugs.indices step 2) {
            if (debug) {
//                if (i > 10) break
                println("-------------------------------------------------------------------------------------")
                println("${i / 2}: ${bugs[i].fullPath}")
            }
            val left = File(bugs[i].fullPath)
            val right = File(bugs[i + 1].fullPath)


            /* distiller */

            val changes = distillerRun(left, right)
            for (change in changes) {
                println(change.changeType)
                println(change.label)
                println(change.toString())
                println(change.changedEntity)
                println(change.hashCode())
                println(change.rootEntity)
            }

            if (changes.size > 0) {
                count++
                println(count)
            }

            /* hungarian algorithm */
//
//            val bug = bugs[i + 1]
//
////            val javaFileInfo = javaFiles.find {
////                it.path == bug.tags[3]
////            }
//            val csharpFileInfo = csharpFiles.find {
//                it.formattedFileName == bug.formattedFileName
//            }
//            if (csharpFileInfo != null) {
//
//                val fileParserJava = FileParserJava(bug.fullPath)
//                val methodsJava = fileParserJava.methods.map { it.name }.distinct()
//                val fileParserCSharp = FileParserCSharp(csharpFileInfo.fullPath)
//                val methodsCSharp = fileParserCSharp.methods.map { it.name }.distinct()
//
//                if (debug) {
//                    for (name in methodsJava) println(name)
//                    println("##########")
//                    println(csharpFileInfo.fullPath)
//                    for (name in methodsCSharp) println(name)
//
//                    val size = max(methodsJava.size, methodsCSharp.size)
//
//                }
//
//            }


        }
//        println(bugs.size)

    }

    fun distillerRun(left: File, right: File): List<SourceCodeChange> {
        val injector = Guice.createInjector(JavaChangeDistillerModule())
        val distiller = injector.getInstance(FileDistiller::class.java)
        try {
            distiller.extractClassifiedSourceCodeChanges(left, right)
        } catch (e: Exception) {
            System.err.println("Warning: error while change distilling. " + e.message)
        }
        return distiller.sourceCodeChanges
    }

    fun findOther(fileInfo: FileInfo, files: List<FileInfo>): FileInfo? =
        files.find { it.formattedFileName == fileInfo.formattedFileName }

    fun findOther(formattedFileName: String, files: List<FileInfo>): FileInfo? =
        files.find { it.formattedFileName == formattedFileName }

    @Serializable
    class FileInfo(
        val path: String,
        val root: String,
        val fileName: String,
        val formattedFileName: String,
        val tags: MutableList<String> = mutableListOf(),
        val mathods: MutableList<String> = mutableListOf()
    ) {
        override fun toString() = "${this.root}${this.path}"
        val fullPath = "${this.root}${this.path}"
    }
}
