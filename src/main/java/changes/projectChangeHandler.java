package changes;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class projectChangeHandler {
    public static List<changeHandler> getProjectChanges(String project){
        String basedir = changeHandler.getDataDir();
        StringBuilder builder = new StringBuilder(basedir);
        builder.append(project);
        File projectDir = new File(builder.toString());
        List<changeHandler> bugChanges = new ArrayList<>();
        for(File bugDir: projectDir.listFiles()){
            String name = bugDir.getName();
            bugChanges.add(new changeHandler(project, name));
        }
        return bugChanges;
    }

    public static void main(String[] args){
        List<changeHandler> bugChanges = getProjectChanges("DERBY");
        System.out.println();
    }
}
