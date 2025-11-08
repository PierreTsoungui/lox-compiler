package parsing;

import scanning.Token;

sealed interface Expr
    permits Expr.Binary, Expr.Unary, Expr.Literal, Expr.Grouping {

    record Binary(Expr left, Token op, Expr right) implements Expr {}
    record Unary(Token op, Expr right) implements Expr {}
    record Literal(Object value) implements Expr {}
    record Grouping(Expr expression) implements Expr {}
}

