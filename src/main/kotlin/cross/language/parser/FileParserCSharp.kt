package cross.language.parser

import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.ParseTreeWalker
import parsers.csharp.CSharpLexer
import parsers.csharp.CSharpParser
import parsers.csharp.CSharpParser.*
import parsers.csharp.CSharpParserBaseListener

class FileParserCSharp(path: String) : FileParser(path) {
    override val methods: MutableList<Method> = mutableListOf()

    init {
//        println("parse java code")
        val lexer = CSharpLexer(this.charStream)
        val tokens = CommonTokenStream(lexer)
        val parser = CSharpParser(tokens)
        val tree: ParseTree = parser.compilation_unit()
        val walker = ParseTreeWalker()
        val listener = Listener(parser)
        walker.walk(listener, tree)
    }

    inner class Listener(var parser: CSharpParser) : CSharpParserBaseListener() {
        /*

        @Override
        public void enterExpression(CSharpParser.ExpressionContext ctx) {

        }

         */
        var noofvariables = 0
            private set
        var noofArguments = 0
            private set
        var noofExpressions = 0
            private set
        var noofOperators = 0
            private set
        var noofLoops = 0
            private set
        var noofExceptions = 0
            private set
        var noofHandledExceptions = 0
            private set
        var noofOperands = 0
            private set
        var mccabecomplex = 0
            private set

        override fun enterVariable_declarator(ctx: Variable_declaratorContext) {
            noofvariables++
        }

        override fun enterLocal_variable_declaration(ctx: Local_variable_declarationContext) {
            noofvariables++
        }

        override fun enterMethod_declaration(ctx: Method_declarationContext) {
            mccabecomplex++
            if (ctx.formal_parameter_list() != null) {
                var temp = ctx.formal_parameter_list().text
                temp = temp.replace("<.*?>".toRegex(), "")
                temp = temp.replace("\\[.*?\\]".toRegex(), "")
                val count = temp.chars().filter { ch: Int -> ch == ','.code }.count().toInt()
                noofArguments = noofArguments + count + 1
            }
            val identifier = ctx.method_member_name()
            if (identifier != null) {
                methods.add(Method(identifier.text))
            }
        }

        override fun enterAssignment(ctx: AssignmentContext) {
            noofExpressions++
        }

        override fun enterAssignment_operator(ctx: Assignment_operatorContext) {
            noofOperators++
        }

        override fun enterAdditive_expression(ctx: Additive_expressionContext) {
            if (ctx.PLUS().size > 0) {
                noofOperators++
                noofExpressions++
            } else if (ctx.MINUS().size > 0) {
                noofOperators++
                noofExpressions++
            }
        }

        override fun enterMultiplicative_expression(ctx: Multiplicative_expressionContext) {
            if (ctx.STAR().size > 0) {
                noofOperators++
                noofExpressions++
            } else if (ctx.DIV().size > 0) {
                noofOperators++
                noofExpressions++
            } else if (ctx.PERCENT().size > 0) {
                noofOperators++
                noofExpressions++
            }
        }

        override fun enterUnary_expression(ctx: Unary_expressionContext) {
            if (ctx.OP_INC() != null) {
                noofOperators++
                noofExpressions++
            } else if (ctx.OP_DEC() != null) {
                noofOperators++
                noofExpressions++
            }
        }

        override fun enterEquality_expression(ctx: Equality_expressionContext) {
            if (ctx.OP_EQ().size > 0) {
                noofOperators++
                noofExpressions++
            } else if (ctx.OP_NE().size > 0) {
                noofOperators++
                noofExpressions++
            }
        }

        override fun enterRelational_expression(ctx: Relational_expressionContext) {
            if (ctx.GT().size > 0) {
                noofOperators++
                noofExpressions++
            } else if (ctx.LT().size > 0) {
                noofOperators++
                noofExpressions++
            } else if (ctx.OP_GE().size > 0) {
                noofOperators++
                noofExpressions++
            } else if (ctx.OP_LE().size > 0) {
                noofOperators++
                noofExpressions++
            } else if (ctx.IS().size > 0) {
                noofOperators++
                noofExpressions++
            }
        }

        override fun enterConditional_and_expression(ctx: Conditional_and_expressionContext) {
            if (ctx.OP_AND().size > 0) {
                noofOperators++
                noofExpressions++
                mccabecomplex++
            }
        }

        override fun exitConditional_or_expression(ctx: Conditional_or_expressionContext) {
            if (ctx.OP_OR().size > 0) {
                noofOperators++
                noofExpressions++
                mccabecomplex++
            }
        }

        override fun enterShift_expression(ctx: Shift_expressionContext) {
            if (ctx.OP_LEFT_SHIFT().size > 0) {
                noofOperators++
                noofExpressions++
            } else if (ctx.right_shift().size > 0) {
                noofOperators++
                noofExpressions++
            }
        }

        override fun enterIfStatement(ctx: IfStatementContext) {
            if (ctx.IF() != null) {
                mccabecomplex++
            } else if (ctx.ELSE() != null) {
                mccabecomplex++
            }
        }

        override fun enterForStatement(ctx: ForStatementContext) {
            noofLoops++
            mccabecomplex++
        }

        override fun enterForeachStatement(ctx: ForeachStatementContext) {
            noofLoops++
            mccabecomplex++
        }

        override fun enterWhileStatement(ctx: WhileStatementContext) {
            noofLoops++
            mccabecomplex++
        }

        override fun enterDoStatement(ctx: DoStatementContext) {
            noofLoops++
            mccabecomplex++
        }

        override fun enterThrowStatement(ctx: ThrowStatementContext) {
            noofExceptions++
        }

        override fun enterCatch_clauses(ctx: Catch_clausesContext) {
            noofHandledExceptions++
        }

        override fun enterType_(ctx: Type_Context) {
            //System.out.println(ctx.getText());
            if (ctx.base_type() != null) {
                if (ctx.base_type().class_type() != null) { //Class Type
                    if (ctx.base_type().class_type().STRING() != null) noofOperands++ else if (ctx.base_type()
                            .class_type().DYNAMIC() != null
                    ) noofOperands++
                } else if (ctx.base_type().simple_type() != null) {
                    if (ctx.base_type().simple_type().BOOL() != null) {
                        noofOperands++
                    } else if (ctx.base_type().simple_type().numeric_type() != null) {
                        if (ctx.base_type().simple_type().numeric_type().integral_type() != null) { // numeric type
                            noofOperands++
                        } else if (ctx.base_type().simple_type().numeric_type()
                                .floating_point_type() != null
                        ) noofOperands++ else if (ctx.base_type().simple_type().numeric_type()
                                .DECIMAL() != null
                        ) noofOperands++
                    }
                }
            }
        }

        override fun enterSwitch_label(ctx: Switch_labelContext) {
            mccabecomplex++
        }
    }
}