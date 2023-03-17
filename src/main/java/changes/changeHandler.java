package changes;

import ch.uzh.ifi.seal.changedistiller.ChangeDistiller;
import ch.uzh.ifi.seal.changedistiller.distilling.FileDistiller;
import ch.uzh.ifi.seal.changedistiller.model.entities.SourceCodeChange;
import ch.uzh.ifi.seal.changedistiller.model.entities.StructureEntityVersion;

import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class changeHandler {
    static private String dataDir;
    public String project;
    public String bug;
    public HashMap<String,List<SourceCodeChange>> changes;


    public changeHandler(String project, String bug){
        this.project = project;
        this.bug = bug;
        changes = new HashMap<>();
        if(dataDir == null){
            String relativelyPath=System.getProperty( "user.dir" );
            StringBuilder builder= new StringBuilder(relativelyPath);
            builder.append("\\data\\");
            int dataBaseDir = builder.length();
            dataDir = builder.toString();
        }

        StringBuilder builder = new StringBuilder(dataDir);
        builder.append(project);
        builder.append("\\");
        builder.append(bug);
        builder.append("\\");
        int baseDir = builder.length();
        builder.append("from\\");
        File oriDir = new File(builder.toString());
        File[] oriFileNames = oriDir.listFiles();

        builder.delete(baseDir, builder.length());
        builder.append("to\\");
        File desDir = new File(builder.toString());
        File[] desFileNames = desDir.listFiles();

//        System.out.println(oriFileNames[0]);
        List<StructureEntityVersion> entities = new ArrayList<StructureEntityVersion>();
        FileDistiller distiller = ChangeDistiller.createFileDistiller(ChangeDistiller.Language.JAVA);
        for(int i = 0; i < Objects.requireNonNull(oriFileNames).length && i < Objects.requireNonNull(desFileNames).length; ++i) {
            try {
                distiller.extractClassifiedSourceCodeChanges(oriFileNames[i], desFileNames[i]);
            } catch (Exception e) {
	        /* An exception most likely indicates a bug in ChangeDistiller. Please file a
	        bug report at https://bitbucket.org/sealuzh/tools-changedistiller/issues and
	        attach the full stack trace along with the two files that you tried to distill. */
                System.err.println("Warning: error while change distilling. " + e.getMessage());
                continue;
            }
            List<SourceCodeChange> allChanges = distiller.getSourceCodeChanges();
            for(SourceCodeChange change:allChanges){
                StructureEntityVersion rootEntity = change.getRootEntity();
//                entities.add(change.getRootEntity());
                String methodName = rootEntity.getUniqueName();
                //文件名的前一个位置
                String[] fullPath = methodName.split("\\.");

                if(utils.stringUtils.hasTest(fullPath)){
                    continue;
                }

                int pathSize = fullPath.length;
                switch (rootEntity.getType().name()){
                    case "METHOD":
                        methodName = fullPath[pathSize-2]+"."+fullPath[pathSize-1];
                        break;
                    case "CLASS":
                        methodName = fullPath[pathSize-1];
                        break;
                    default:
                        System.out.println(rootEntity.getType().name());
                }
//                methodName = methodName.substring(st+1);
                if(!changes.containsKey(methodName)){
                    changes.put(methodName, new ArrayList<>());
                }
                changes.get(methodName).add(change);
            }
        }
    }

    public static String getDataDir(){
        if(dataDir == null){
            String relativelyPath=System.getProperty( "user.dir" );
            StringBuilder builder= new StringBuilder(relativelyPath);
            builder.append("\\data\\");
            int dataBaseDir = builder.length();
            dataDir = builder.toString();
        }
        return dataDir;
    }

//    public changeHandler(String project){
//        if(dataDir == null){
//            String relativelyPath=System.getProperty( "user.dir" );
//            StringBuilder builder= new StringBuilder(relativelyPath);
//            builder.append("\\data\\");
//            int dataBaseDir = builder.length();
//            dataDir = builder.toString();
//        }
//
//        StringBuilder builder = new StringBuilder(dataDir);
//        builder.append(project);
//        builder.append("\\");
//        File projectDir = new File(builder.toString());
//        File[] patches = projectDir.listFiles();
////        for(File patches:patches)
//
//        builder.append("from\\");
//        File oriDir = new File(builder.toString());
//        File[] oriFileNames = oriDir.listFiles();
//
////        builder.delete(baseDir+1, builder.length());
//        File desDir = new File(builder.toString());
//        File[] desFileNames = desDir.listFiles();
//
//        System.out.println(oriFileNames[0]);
//    }

    public static void main(String args[]){
        String relativelyPath=System.getProperty( "user.dir" );
        File file = new File(relativelyPath);
        System.out.println(relativelyPath);
        System.out.println(file.listFiles());
        changeHandler test = new changeHandler("DERBY", "000d4bc_Bug_DERBY-4913");
    }
}
