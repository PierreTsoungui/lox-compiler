

import java.util.List;

// --- Modernisierte Stmt-Hierarchie ---
sealed interface Stmt
        permits Block, Class, Expression, Function,
                If, Print, Return, Var, While { }

// --- Block Statement ---
record Block(List<Stmt> statements) implements Stmt { }

// --- Class Statement ---
record Class(Token name, Expr.Variable superclass, List<Function> methods) implements Stmt { }

// --- Expression Statement ---
record Expression(Expr expression) implements Stmt { }

// --- Function Declaration ---
record Function(Token name, List<Token> params, List<Stmt> body) implements Stmt { }

// --- If Statement ---
record If(Expr condition, Stmt thenBranch, Stmt elseBranch) implements Stmt { }

// --- Print Statement ---
record Print(Expr expression) implements Stmt { }

// --- Return Statement ---
record Return(Token keyword, Expr value) implements Stmt { }

// --- Variable Declaration ---
record Var(Token name, Expr initializer) implements Stmt { }

// --- While Statement ---
record While(Expr condition, Stmt body) implements Stmt { }
