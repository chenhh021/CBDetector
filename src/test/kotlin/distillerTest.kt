import ch.uzh.ifi.seal.changedistiller.ChangeDistiller
import ch.uzh.ifi.seal.changedistiller.JavaChangeDistillerModule
import ch.uzh.ifi.seal.changedistiller.distilling.FileDistiller
import com.google.inject.Guice
import java.io.File

object distillerTest {
    private fun createFileDistiller(language: ChangeDistiller.Language): FileDistiller? {
        return when (language) {
            ChangeDistiller.Language.JAVA -> {
                val injector = Guice.createInjector(JavaChangeDistillerModule())
                injector.getInstance(FileDistiller::class.java)
            }
        }
        return null
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val left =
            File("D:/code/java-csharp-ast/src/bugs/04fb8c0_Bug_LUCENE-10118/from/lucene_core_src_test_org_apache_lucene_index_TestConcurrentMergeScheduler.java")
        val right =
            File("D:/code/java-csharp-ast/src/bugs/04fb8c0_Bug_LUCENE-10118/to/lucene_core_src_test_org_apache_lucene_index_TestConcurrentMergeScheduler.java")
        val distiller = ChangeDistiller.createFileDistiller(ChangeDistiller.Language.JAVA)
        try {
            distiller.extractClassifiedSourceCodeChanges(left, right)
        } catch (e: Exception) {
            /* An exception most likely indicates a bug in ChangeDistiller. Please file a
	       bug report at https://bitbucket.org/sealuzh/tools-changedistiller/issues and
	       attach the full stack trace along with the two files that you tried to distill. */
            System.err.println("Warning: error while change distilling. " + e.message)
        }
        val changes = distiller.sourceCodeChanges
        if (changes != null) {
            for (change in changes) {
                // see Javadocs for more information
                println(change.changeType)
                println(change.label)
                println(change.toString())
                println(change.changedEntity)
                println(change.hashCode())
                println(change.rootEntity)
            }
        }
    }
}
