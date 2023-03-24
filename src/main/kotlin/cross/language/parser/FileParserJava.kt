package cross.language.parser

import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.ParseTreeWalker
import parsers.java.JavaLexer
import parsers.java.JavaParser
import parsers.java.JavaParser.*
import parsers.java.JavaParserBaseListener

class FileParserJava(path: String) : FileParser(path) {
    override val methods: MutableList<Method> = mutableListOf()

    init {
//        println("parse java code")
        val lexer = JavaLexer(this.charStream)
        val tokens = CommonTokenStream(lexer)
        val parser = JavaParser(tokens)
        val tree: ParseTree = parser.compilationUnit()
        val walker = ParseTreeWalker()
        val listener = Listener(parser)
        walker.walk(listener, tree)
    }


    inner class Listener(
        /** Observable.  */
        var parser: JavaParser
    ) : JavaParserBaseListener() {
        /** The clazzName of the imported file.  */
        var className: String? = null
        var methodName: String? = null
        var variableno = 0
            private set
        var noofarguments = 0
            private set
        var noofexpression = 0
            private set
        var noofoperators = 0
            private set
        var noofloops = 0
            private set
        var noofoperands = 0
            private set
        var noofexceptions = 0
            private set
        var noofexceptionclause = 0
            private set
        var mccablecomplex = 0
            private set

        /** The count of methdos of the imported file.  */
        var methodCount = 0

        /** Listen to matches of classDeclaration  */
        override fun enterClassDeclaration(ctx: ClassDeclarationContext) {
            className = ctx.identifier().toString()
        }

        /** Listen to matches of methodDeclaration  */
        override fun enterMethodDeclaration(ctx: MethodDeclarationContext) {
            methodargument(ctx.formalParameters().text)
            methodCount++
            mccablecomplex++
            val identifier = ctx.identifier()
            if (identifier != null) {
                methodName = identifier.text
                methods.add(Method(identifier.text))
            }
            if (ctx.THROWS() != null) noofexceptions++
        }

        override fun enterCatchClause(ctx: CatchClauseContext) {
            noofexceptionclause++
        }

        override fun enterVariableInitializer(ctx: VariableInitializerContext) {
            variableno++
        }

        /*
    @Override
    public void enterArguments(JavaParser.ArgumentsContext ctx) {
        System.out.println(ctx.getText());
        noofarguments++;
    }
    */
        fun methodargument(parameters: String) {
            var parameters = parameters
            parameters = parameters.replace("(", "")
            parameters = parameters.replace(")", "")
            if (!parameters.isEmpty()) {
                val count = parameters.chars().filter { ch: Int -> ch == ','.code }.count().toInt()
                noofarguments = noofarguments + count + 1
            }
        }

        override fun enterTypeArgument(ctx: TypeArgumentContext) {
            noofoperands++
        }

        override fun enterExpression(ctx: ExpressionContext) {
            if (ctx.bop != null) {
                val temp = ctx.bop.text
                if (temp != ".") {
                    if (temp == "+" || temp == "-" || temp == "*" || temp == "/" || temp == "%" || temp == "=" || temp == "+=" || temp == "-=" || temp == "*=" || temp == "/=" || temp == "&=" || temp == "|=" || temp == "^=" || temp == ">>=" || temp == ">>>=" || temp == "<<=" || temp == "%=") {
                        noofoperators++
                        noofexpression++
                    } else if (temp == "&&" || temp == "||" || temp == "!" || temp == "==" || temp == "!=" || temp == ">" || temp == "<" || temp == ">=" || temp == "<=") {
                        noofoperators++
                        noofexpression++
                        mccablecomplex++
                    }
                }
            }
            if (ctx.prefix != null) {
                val temp = ctx.prefix.text
                if (temp == "++" || temp == "--") {
                    noofoperators++
                    noofexpression++
                }
            }
            if (ctx.postfix != null) {
                val temp = ctx.postfix.text
                if (temp == "++" || temp == "--") {
                    noofoperators++
                    noofexpression++
                }
            }
        }

        override fun enterArrayInitializer(ctx: ArrayInitializerContext) {
            variableno++
        }

        override fun enterConstantDeclarator(ctx: ConstantDeclaratorContext) {
            noofoperands++
        }

        override fun enterTypeType(ctx: TypeTypeContext) {
            noofoperands++
        }

        override fun enterStatement(ctx: StatementContext) {
            if (ctx.FOR() != null || ctx.WHILE() != null || ctx.DO() != null) {
                noofloops++
                mccablecomplex++
            }
            if (ctx.IF() != null) {
                mccablecomplex++
            }
        }

        override fun enterSwitchBlockStatementGroup(ctx: SwitchBlockStatementGroupContext) {
            mccablecomplex++
        }
    }
}