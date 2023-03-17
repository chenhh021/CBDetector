package parseTest;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import parsers.java.JavaParser;
import parsers.java.JavaLexer;
import parsers.java.JavaParserBaseVisitor;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import java.io.IOException;

public class javaTest {
    public static void main(String[] args) throws IOException {
        CharStream input = CharStreams.fromFileName("D:\\chh\\SE\\projects\\CLCDSA\\CLCDSA\\CLCDSA_Code\\src\\test.java");
        JavaLexer lexer = new JavaLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        JavaParser parser = new JavaParser(tokens); // Parser Created
        ParseTree tree = parser.compilationUnit();

        ParseTreeWalker walker = new ParseTreeWalker();
        Listener listener = new Listener(parser);
        walker.walk(listener, tree);

        System.out.println("/n");
        System.out.println(listener.getMethodName());
        System.out.println(listener.getVariableno()); // Number of Variables
        System.out.println(listener.getNoofarguments()); // Number of Arguments
        System.out.println(listener.getNoofoperators()); // No of Operators
        System.out.println(listener.getNoofexpression()); // No of Expressions
        System.out.println(listener.getNoofloops()); // No of Loops
        System.out.println(listener.getNoofoperands()); // No of Operands
        System.out.println(listener.getNoofexceptions()); // No of Exceptions
        System.out.println(listener.getNoofexceptionclause()); // No of Exception Handled
        System.out.println(listener.getMccablecomplex());
    }
}
