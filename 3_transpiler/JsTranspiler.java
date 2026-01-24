import java.util.List;

public class JsTranspiler {
    private final StringBuilder out = new StringBuilder();
    private int indentLevel = 0;

    public String transpile(List<Stmt> statements) {
        for (Stmt stmt : statements) {
            emitStmt(stmt);
        }
        return out.toString();
    }

    private void emitStmt(Stmt stmt) {
        switch (stmt) {
            // expression;
            case Stmt.Expression e -> {
                emitIndent();
                emitExpr(e.expr());
                out.append(";\n");
            }

            // print expr;
            case Stmt.Print p -> {
                emitIndent();
                out.append("console.log(");
                emitExpr(p.expr());
                out.append(");\n");
            }

            // var name = initializer;
            case Stmt.Var v -> {
                emitIndent();
                out.append("let ").append(v.name().lexem());
                if (v.initializer() != null) {
                    out.append(" = ");
                    emitExpr(v.initializer());
                }
                out.append(";\n");
            }
            
            // { stmt* }
            case Stmt.Block b -> {
                emitIndent();
                out.append("{\n");
                indentLevel++;
                for (Stmt s : b.statements()) {
                    emitStmt(s);
                }
                indentLevel--;
                emitIndent();
                out.append("}\n");
            }
            
            // if (condition) thenBranch else elseBranch
            case Stmt.If i -> {
                emitIndent();
                out.append("if (");
                emitExpr(i.condition());
                out.append(") ");
                
                // Prüfen ob thenBranch ein Block ist
                if (i.thenBranch() instanceof Stmt.Block) {
                    emitStmt(i.thenBranch());
                } else {
                    out.append("{\n");
                    indentLevel++;
                    emitStmt(i.thenBranch());
                    indentLevel--;
                    emitIndent();
                    out.append("}\n");
                }

                i.elseBranch().ifPresent(elseBranch -> {
                    emitIndent();
                    out.append("else ");
                    
                    // Prüfen ob elseBranch ein Block ist
                    if (elseBranch instanceof Stmt.Block) {
                        emitStmt(elseBranch);
                    } else {
                        out.append("{\n");
                        indentLevel++;
                        emitStmt(elseBranch);
                        indentLevel--;
                        emitIndent();
                        out.append("}\n");
                    }
                });
            }

            // while (condition) body
            case Stmt.While w -> {
                emitIndent();
                out.append("while (");
                emitExpr(w.condition());
                out.append(") ");
                
                if (w.body() instanceof Stmt.Block) {
                    emitStmt(w.body());
                } else {
                    out.append("{\n");
                    indentLevel++;
                    emitStmt(w.body());
                    indentLevel--;
                    emitIndent();
                    out.append("}\n");
                }
            }
            
            // return value;
            case Stmt.Return r -> {
                emitIndent();
                out.append("return");
                if (r.value() != null) {
                    out.append(" ");
                    emitExpr(r.value());
                }
                out.append(";\n");
            }
            
            // fun name(params) { body }
            case Stmt.Function f -> {
                emitIndent();
                out.append("function ")
                   .append(f.name().lexem())
                   .append("(");

                for (int i = 0; i < f.params().size(); i++) {
                    if (i > 0) out.append(", ");
                    out.append(f.params().get(i).lexem());
                }

                out.append(") {\n");
                indentLevel++;
                for (Stmt s : f.body()) {
                    emitStmt(s);
                }
                indentLevel--;
                emitIndent();
                out.append("}\n");
            }

            // class Name { methods }
            case Stmt.Class c -> {
                emitIndent();
                out.append("class ")
                   .append(c.name().lexem());
                
                // Superklasse hinzufügen
                if (c.superClass() != null) {
                    out.append(" extends ").append(c.superClass().name().lexem());
                }
                
                out.append(" {\n");
                
                indentLevel++;
                for (Stmt.Function method : c.methods()) {
                    emitIndent();
                    out.append(method.name().lexem()).append("(");
                    for (int i = 0; i < method.params().size(); i++) {
                        if (i > 0) out.append(", ");
                        out.append(method.params().get(i).lexem());
                    }

                    out.append(") {\n");
                    indentLevel++;
                    for (Stmt s : method.body()) {
                        emitStmt(s);
                    }
                    indentLevel--;
                    emitIndent();
                    out.append("}\n");
                }
                indentLevel--;
                emitIndent();
                out.append("}\n");
            }
            
            default -> throw new IllegalArgumentException("Unknown statement type: " + stmt.getClass().getName());
        }
    }

    private void emitIndent() {
        out.append("  ".repeat(indentLevel));
    }

    private void emitExpr(Expr expr) {
        switch (expr) {
            case Expr.Literal l -> {
                Object v = l.value();
                
                if (v == null) {
                    out.append("null");
                } else if (v instanceof String s) {
                    out.append('"').append(escapeString(s)).append('"');
                } else if (v instanceof Double d) {
                    // Bessere Number-Handling
                    if (Double.isNaN(d)) {
                        out.append("NaN");
                    } else if (Double.isInfinite(d)) {
                        out.append(d > 0 ? "Infinity" : "-Infinity");
                    } else if (d == Math.floor(d) && Math.abs(d) <= 1e15) {
                        // Ganze Zahl
                        out.append(d.longValue());
                    } else {
                        // Gleitkommazahl
                        out.append(d);
                    }
                } else if (v instanceof Boolean b) {
                    out.append(b ? "true" : "false");
                } else {
                    out.append(v);
                }
            }       

            case Expr.Variable v -> {
                out.append(v.name().lexem());
            }
            case Expr.Assign a -> {
                out.append(a.name().lexem())
                   .append(" = ");
                emitExpr(a.value());
            }
            case Expr.Binary b -> {
                out.append("(");
                emitExpr(b.left());
                out.append(" ").append(b.operator().lexem()).append(" ");
                emitExpr(b.right());
                out.append(")");
            }
            case Expr.Grouping g -> {
                out.append("(");
                emitExpr(g.expression());
                out.append(")");
            }
            case Expr.Unary u -> {
                out.append(u.operator().lexem());
                emitExpr(u.right());
            }
            case Expr.Call c -> {
                emitExpr(c.callee());
                out.append("(");
                for (int i = 0; i < c.arguments().size(); i++) {
                    if (i > 0) out.append(", ");
                    emitExpr(c.arguments().get(i));
                }
                out.append(")");
            }
            case Expr.Logical l -> {
                out.append("(");
                emitExpr(l.left());
                out.append(" ").append(l.operator().lexem()).append(" ");
                emitExpr(l.right());
                out.append(")");
            }
            case Expr.This t -> {
                out.append("this");
            }
            case Expr.Super s -> {
                out.append("super");
            }
            case Expr.Get g -> {
                emitExpr(g.object());
                out.append(".").append(g.name().lexem());
            }
            case Expr.Set s -> {
                emitExpr(s.object());
                out.append(".").append(s.name().lexem());
                out.append(" = ");
                emitExpr(s.value());
            }
            default -> throw new IllegalArgumentException("Unknown expression type: " + expr.getClass().getName());
        }
    }

    private String escapeString(String str) {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\t", "\\t")
                  .replace("\r", "\\r");
    }

    public static void run(String source) {
        try {
            JsTranspiler transpiler = new JsTranspiler();
            List<Stmt> statements = ParserMain.parseProgram(source);
            String jsCode = transpiler.transpile(statements);
            
            System.out.println("=== Transpiler Output ===");
            System.out.println(jsCode);
            
        } catch (Exception e) {
            System.err.println("Transpiler error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        String sourceCode = """
            var x = 5;
            print x + 10;
            if (x > 0) print "positive";
            
            fun add(a, b) {
                return a + b;
            }
            
            print add(3, 4);
            
            class Person {
                init(name) {
                    this.name = name;
                }
                
                greet() {
                    print "Hello, " + this.name;
                }
            }
            
            var p = Person("Alice");
            p.greet();
            """;
        
        run(sourceCode);
    }
}