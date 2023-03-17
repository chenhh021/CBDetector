package utils;

import java.util.regex.Pattern;

public class stringUtils {
    static Pattern noTest = Pattern.compile("(?=test)");

    public static boolean hasTest(String[] paths) {
        for (String path : paths) {
            if (noTest.matcher(path.toLowerCase()).find()) {
                return true;
            }
        }
        return false;
    }
}
