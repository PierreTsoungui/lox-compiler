
import java.util.*;
public class Compiler {

    // Map: Name der lokalen Variable -> Slot-Index
    private final Map<String, Integer> locals = new HashMap<>();
    private int localCount = 0;

    public String compile(String source) {
        List<Stmt> statements = ParserMain.parseProgram(source);

        if (statements.isEmpty()) return "";

        List<String> byteCodeLines = new ArrayList<>();
        for (Stmt stmt : statements) {
            byteCodeLines.addAll(stmtToByteCode(stmt));
        }

        return String.join("\n", byteCodeLines);
    }

    // --- Statements ---
    private List<String> stmtToByteCode(Stmt stmt) {
        List<String> code = new ArrayList<>();

        if (stmt instanceof Stmt.Expression expr) {
            code.addAll(exprToByteCode(expr.expr()));
        } else if (stmt instanceof Stmt.Print print) {
            code.addAll(exprToByteCode(print.expr()));
            code.add("OP_PRINT");
        } else if (stmt instanceof Stmt.Var var) {
            String name = var.name().lexem();
            if (var.initializer() != null) {
                code.addAll(exprToByteCode(var.initializer()));
            } else {
                code.add("OP_NIL");
            }

            locals.put(name, localCount++);
            code.add("OP_DEFINE_GLOBAL " + name);
        }else if (stmt instanceof Stmt.Return ret) {
            if (ret.value() != null) code.addAll(exprToByteCode(ret.value()));
            code.add("OP_RETURN");
        }
        else {
            throw new RuntimeException("Unknown statement type: " + stmt);
        }

        return code;
    }

    // --- Expressions ---
    private List<String> exprToByteCode(Expr expr) {
        List<String> code = new ArrayList<>();

        switch (expr) {
            case Expr.Literal lit -> {
                Object v = lit.value();
                if (v instanceof Double d) code.add("OP_CONSTANT " + d);
                else if (v instanceof Boolean b) code.add(b ? "OP_TRUE" : "OP_FALSE");
                else if (v instanceof String s) code.add("OP_CONSTANT \"" + s + "\"");
                else if (v == null) code.add("OP_NIL");
                else throw new RuntimeException("Unknown literal: " + v);
            }
            case Expr.Variable var -> {
                String name = var.name().lexem();
                if (locals.containsKey(name)) code.add("OP_GET_LOCAL " + locals.get(name));
                else code.add("OP_GET_GLOBAL " + name);
            }
            case Expr.Unary un -> {
                code.addAll(exprToByteCode(un.right()));
                switch (un.operator().type()) {
                    case MINUS -> code.add("OP_NEGATE");
                    case BANG -> code.add("OP_NOT");
                    default -> throw new RuntimeException("Unknown unary: " + un.operator());
                }
            }
            case Expr.Binary bin -> {
                code.addAll(exprToByteCode(bin.left()));
                code.addAll(exprToByteCode(bin.right()));

                switch (bin.operator().type()) {
                    case PLUS -> code.add("OP_ADD");
                    case MINUS -> code.add("OP_SUBTRACT");
                    case STAR -> code.add("OP_MULTIPLY");
                    case SLASH -> code.add("OP_DIVIDE");
                    case GREATER -> code.add("OP_GREATER");
                    case LESS -> code.add("OP_LESS");
                    case EQUAL_EQUAL -> code.add("OP_EQUAL");
                    default -> throw new RuntimeException("Unknown binary operator: " + bin.operator());
                }
            }
            case Expr.Logical log -> {
                if (log.operator().type() == TokenType.AND) {
                    emitAnd(log, code);
                } else {
                    emitOr(log, code);
                 }
            }
            case Expr.Grouping gp->{
                code.addAll(exprToByteCode(gp.expression()));
            }
            case Expr.Assign ass -> {
                String name = ass.name().lexem();

                // Zuerst den Wert des rechten Ausdrucks auswerten
                code.addAll(exprToByteCode(ass.value()));

                // Dann den Wert in die Variable schreiben
                 if (locals.containsKey(name)) {
                 code.add("OP_SET_LOCAL " + locals.get(name));
                } else {
                 code.add("OP_SET_GLOBAL " + name);
                }
    
            }


            default -> throw new RuntimeException("Unknown expression type: " + expr);
        }

        return code;
    }


    void emitAnd(Expr.Logical log, List<String> code) {
        code.addAll(exprToByteCode(log.left()));

        int jumpToEnd = code.size();
        code.add("OP_JUMP_IF_FALSE ???");

        code.add("OP_POP");
        code.addAll(exprToByteCode(log.right()));

        patchJump(code, jumpToEnd, code.size());
    }
    void emitOr(Expr.Logical log, List<String> code) {
        code.addAll(exprToByteCode(log.left()));

        int jumpToEvalB = code.size();
        code.add("OP_JUMP_IF_FALSE ???");

        int jumpToEnd = code.size();
        code.add("OP_JUMP ???");

        int evalB = code.size();
        code.add("OP_POP");
        code.addAll(exprToByteCode(log.right()));

        patchJump(code, jumpToEvalB, evalB);
        patchJump(code, jumpToEnd, code.size());
    }

    void patchJump(List<String> code, int jumpIndex, int targetIndex) {
         int offset = targetIndex - (jumpIndex + 1);
        String instr = code.get(jumpIndex).replace("???", String.valueOf(offset));
        code.set(jumpIndex, instr);
    }


    //TestMethode:
     static void testLiteral(Compiler compiler) {
        String source = "1; true; false; nil; \"hello\";";
        String bytecode = compiler.compile(source);
        System.out.println("--- Literals ---");
        System.out.println(bytecode);
        System.out.println();
    }
    static void testBinary(Compiler compiler) {
        String source = "1 + 2; 5 - 3; 4 * 2; 8 / 2;";
        String bytecode = compiler.compile(source);
        System.out.println("--- Binary ---");
        System.out.println(bytecode);
        System.out.println();
    }

    static void testLogical(Compiler compiler) {
        String source = "true and false; false or true;";
        String bytecode = compiler.compile(source);
        System.out.println("--- Logical ---");
        System.out.println(bytecode);
        System.out.println();
    }

    static void testVariable(Compiler compiler) {
        String source = "var a = 10; a;";
        String bytecode = compiler.compile(source);
        System.out.println("--- Variable ---");
        System.out.println(bytecode);
        System.out.println();
    }

    static void testUnary(Compiler compiler) {
        String source = "-5; !true;";
        String bytecode = compiler.compile(source);
        System.out.println("--- Unary ---");
        System.out.println(bytecode);
        System.out.println();
    }

}
