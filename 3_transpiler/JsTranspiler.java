import java.util.List;

public class JsTranspiler {
    private final StringBuilder out = new StringBuilder();

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
            emitExpr(e.expression());
            out.append(";\n");
        }

        // print expr;
        case Stmt.Print p -> {
            out.append("console.log(");
            emitExpr(p.expression());
            out.append(");\n");
        }

        // var name = initializer;
        case Stmt.Var v -> {
            out.append("let ").append(v.name().lexeme());
            if (v.initializer() != null) {
                out.append(" = ");
                emitExpr(v.initializer());
            }
            out.append(";\n");
        }

        // { stmt* }
        case Stmt.Block b -> {
            out.append("{\n");
            for (Stmt s : b.statements()) {
                emitStmt(s);
            }
            out.append("}\n");
        }

        // if (condition) thenBranch else elseBranch
        case Stmt.If i -> {
            out.append("if (");
            emitExpr(i.condition());
            out.append(") ");
            emitStmt(i.thenBranch());

            if (i.elseBranch() != null) {
                out.append("else ");
                emitStmt(i.elseBranch());
            }
        }

        // while (condition) body
        case Stmt.While w -> {
            out.append("while (");
            emitExpr(w.condition());
            out.append(") ");
            emitStmt(w.body());
        }

        // return value;
        case Stmt.Return r -> {
            out.append("return");
            if (r.value() != null) {
                out.append(" ");
                emitExpr(r.value());
            }
            out.append(";\n");
        }

        // fun name(params) { body }
        case Stmt.Function f -> {
            out.append("function ")
               .append(f.name().lexeme())
               .append("(");

            for (int i = 0; i < f.params().size(); i++) {
                if (i > 0) out.append(", ");
                out.append(f.params().get(i).lexeme());
            }

            out.append(") ");
            out.append("{\n");
            for (Stmt s : f.body()) {
                emitStmt(s);
            }
            out.append("}\n");
        }

        // class Name { methods }
        case Stmt.Class c -> {
            out.append("class ")
               .append(c.name().lexeme())
               .append(" {\n");

            for (Stmt.Function method : c.methods()) {
                out.append(method.name().lexeme()).append("(");

                for (int i = 0; i < method.params().size(); i++) {
                    if (i > 0) out.append(", ");
                    out.append(method.params().get(i).lexeme());
                }

                out.append(") {\n");
                for (Stmt s : method.body()) {
                    emitStmt(s);
                }
                out.append("}\n");
            }

            out.append("}\n");
        }
    }
}

}
