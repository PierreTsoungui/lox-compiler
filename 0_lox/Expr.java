package 0_lox;
import java.util.List;

// --- Modernisierte Expr-Hierarchie ---
sealed interface Expr
        permits Assign, Binary, Call, Get, Grouping, Literal,
                Logical, Set, Super, This, Unary, Variable { }

// --- Assignment ---
record Assign(Token name, Expr value) implements Expr { }

// --- Binary Expression ---
record Binary(Expr left, Token operator, Expr right) implements Expr { }

// --- Function / Method Call ---
record Call(Expr callee, Token paren, List<Expr> arguments) implements Expr { }

// --- Property Access (obj.name) ---
record Get(Expr object, Token name) implements Expr { }

// --- Grouping (parentheses) ---
record Grouping(Expr expression) implements Expr { }

// --- Literal Value ---
record Literal(Object value) implements Expr { }

// --- Logical Expression (and / or) ---
record Logical(Expr left, Token operator, Expr right) implements Expr { }

// --- Property Assignment (obj.name = value) ---
record Set(Expr object, Token name, Expr value) implements Expr { }

// --- super.method ---
record Super(Token keyword, Token method) implements Expr { }

// --- this ---
record This(Token keyword) implements Expr { }

// --- Unary Expression (- / !) ---
record Unary(Token operator, Expr right) implements Expr { }

// --- Variable Reference ---
record Variable(Token name) implements Expr { }
