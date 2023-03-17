package distillerTest;

//import Test.TestLeft;
//import Test.TestRight;
import java.io.File;
import java.util.List;

import ch.uzh.ifi.seal.changedistiller.ChangeDistiller;
import com.google.inject.Guice;
import com.google.inject.Injector;
//import ch.uzh.ifi.seal.changedistiller.ChangeDistiller.Language;
import ch.uzh.ifi.seal.changedistiller.JavaChangeDistillerModule;
import ch.uzh.ifi.seal.changedistiller.distilling.FileDistiller;
import ch.uzh.ifi.seal.changedistiller.model.entities.SourceCodeChange;
import ch.uzh.ifi.seal.changedistiller.ChangeDistiller.Language;

public class distillerTest{
    private static FileDistiller createFileDistiller (Language language) {
        switch (language) {
            case JAVA:
                Injector injector = Guice.createInjector(new JavaChangeDistillerModule());
                return injector.getInstance(FileDistiller.class);
        }
        return null;
    }

    public static void main(String args[]){
        File left = new File("D:\\chh\\SE\\projects\\detector\\data\\DERBY\\000d4bc_Bug_DERBY-4913\\from\\java_engine_org_apache_derby_catalog_types_RoutineAliasInfo.java");
        File right = new File("D:\\chh\\SE\\projects\\detector\\data\\DERBY\\000d4bc_Bug_DERBY-4913\\to\\java_engine_org_apache_derby_catalog_types_RoutineAliasInfo.java");
        FileDistiller distiller = ChangeDistiller.createFileDistiller(Language.JAVA);
        try{
            distiller.extractClassifiedSourceCodeChanges(left, right);
        } catch(Exception e) {
	    /* An exception most likely indicates a bug in ChangeDistiller. Please file a
	       bug report at https://bitbucket.org/sealuzh/tools-changedistiller/issues and
	       attach the full stack trace along with the two files that you tried to distill. */
            System.err.println("Warning: error while change distilling. " + e.getMessage());
        }

        List<SourceCodeChange> changes = distiller.getSourceCodeChanges();
        if(changes != null) {
            for(SourceCodeChange change : changes) {
                // see Javadocs for more information
                System.out.println(change.getChangeType());
                System.out.println(change.getLabel());
                System.out.println(change.toString());
                System.out.println(change.getChangedEntity());
                System.out.println(change.hashCode());
                System.out.println(change.getRootEntity());
            }
        }
    }
}
