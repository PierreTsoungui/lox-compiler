import java.util.*;
import java.util.function.Supplier;

public class Parser {
    private static class ParseError extends RuntimeException {}

    private final List<Token> tokens;
    private int current = 0;
    private static final int MAX_ARGS = 255;
    private static final int MAX_PARAMS = 255;

   public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    /* ---------------------------
       Declaration Type
    --------------------------- */
    private enum DeclKind { CLASS, FUN, VAR, NONE }

    /* ---------------------------
       Entry Point
    --------------------------- */
    public List<Stmt> parse() {
        var statements = new ArrayList<Stmt>();
        while (!isAtEnd()) {
            var decl = declaration();
            if (decl != null) statements.add(decl);
        }
        return statements;
    }

    /* ---------------------------
       Declarations / Statements
    --------------------------- */
    private Stmt declaration() {
        try {
            return switch (nextDeclKind()) {
                case CLASS -> classDeclaration();
                case FUN   -> function("function");
                case VAR   -> varDeclaration();
                case NONE  -> statement();
            };
        } catch (ParseError e) {
            synchronize();
            return null;
        }
    }

    private DeclKind nextDeclKind() {
        if (checkAndAdvance(TokenType.CLASS)) return DeclKind.CLASS;
        if (checkAndAdvance(TokenType.FUN)) return DeclKind.FUN;
        if (checkAndAdvance(TokenType.VAR)) return DeclKind.VAR;
        return DeclKind.NONE;
    }

    private Stmt classDeclaration() {
        var name = consume(TokenType.IDENTIFIER, "Expect class name.");
        Expr.Variable superclass = null;

        if (match(TokenType.LESS)) {
            superclass = new Expr.Variable(consume(TokenType.IDENTIFIER, "Expect superclass name."));
        }

        consume(TokenType.LEFT_BRACE, "Expect '{' before class body.");
        var methods = new ArrayList<Stmt.Function>();
        while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) methods.add(function("method"));
        consume(TokenType.RIGHT_BRACE, "Expect '}' after class body.");

        return new Stmt.Class(name, superclass, methods);
    }

    private Stmt statement() {
        return switch (peek().type()) {
            case FOR -> forStatement();
            case IF -> ifStatement();
            case PRINT -> printStatement();
            case RETURN -> returnStatement();
            case WHILE -> whileStatement();
            case LEFT_BRACE -> new Stmt.Block(block());
            default -> expressionStatement();
        };
    }

    private Stmt forStatement() {
        consume(TokenType.LEFT_PAREN, "Expect '(' after 'for'.");
        
        // Initializer
        final Stmt initializer;
        if (match(TokenType.SEMICOLON)) {
            initializer = null;
        } else if (match(TokenType.VAR)) {
            initializer = varDeclaration();
        } else {
            initializer = expressionStatement();
        }
        
        // Condition
        final Expr condition;
        if (!check(TokenType.SEMICOLON)) {
            condition = expression();
        } else {
            condition = null;
        }
        consume(TokenType.SEMICOLON, "Expect ';' after loop condition.");
        
        // Increment
        final Expr increment;
        if (!check(TokenType.RIGHT_PAREN)) {
            increment = expression();
        } else {
            increment = null;
        }
        consume(TokenType.RIGHT_PAREN, "Expect ')' after for clauses.");
        
        var body = statement();

        // Add increment if present
        if (increment != null) {
            body = new Stmt.Block(Arrays.asList(
                body,
                new Stmt.Expression(increment)
            ));
        }
        
        // Add while loop with condition
        if (condition != null) {
            body = new Stmt.While(condition, body);
        } else {
            body = new Stmt.While(new Expr.Literal(true), body);
        }
        
        // Add initializer if present
        if (initializer != null) {
            body = new Stmt.Block(Arrays.asList(initializer, body));
        }
        
        return body;
    }

    private Stmt ifStatement() {
        consume(TokenType.LEFT_PAREN, "Expect '(' after 'if'.");
        Expr condition = expression();
        consume(TokenType.RIGHT_PAREN, "Expect ')' after if condition.");
        Stmt thenBranch = statement();
        Stmt elseBranch = match(TokenType.ELSE) ? statement() : null;
        return new Stmt.If(condition, thenBranch, elseBranch);
    }
    private Stmt printStatement() {
        consume(TokenType.PRINT, "Expect 'print'.");
        Expr value = expression();
        consume(TokenType.SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(value);
    }

    private Stmt returnStatement() {
        Token keyword = consume(TokenType.RETURN, "Expect 'return'.");
        Expr value = !check(TokenType.SEMICOLON) ? expression() : null;
        consume(TokenType.SEMICOLON, "Expect ';' after return value.");
        return new Stmt.Return(keyword, value);
    }

    private Stmt varDeclaration() {
        var name = consume(TokenType.IDENTIFIER, "Expect variable name.");
        Expr initializer = match(TokenType.EQUAL) ? expression() : null;
        consume(TokenType.SEMICOLON, "Expect ';' after variable declaration.");
        return new Stmt.Var(name, initializer);
    }

    private Stmt whileStatement() {
        consume(TokenType.LEFT_PAREN, "Expect '(' after 'while'.");
        var condition = expression();
        consume(TokenType.RIGHT_PAREN, "Expect ')' after condition.");
        return new Stmt.While(condition, statement());
    }

    private Stmt expressionStatement() {
        var expr = expression();
        consume(TokenType.SEMICOLON, "Expect ';' after expression.");
        return new Stmt.Expression(expr);
    }

    private Stmt.Function function(String kind) {
        Token name = consume(TokenType.IDENTIFIER, "Expect " + kind + " name.");
         consume(TokenType.LEFT_PAREN, "Expect '(' after " + kind + " name.");

        List<Token> parameters = new ArrayList<>();
        if (!check(TokenType.RIGHT_PAREN)) {
            do {
                if (parameters.size() >= MAX_PARAMS) {
                    error(peek(), "Too many parameters.");
                }
                parameters.add(consume(TokenType.IDENTIFIER, "Expect parameter name."));
            } while (match(TokenType.COMMA));
        }

        consume(TokenType.RIGHT_PAREN, "Expect ')' after parameters.");
         consume(TokenType.LEFT_BRACE, "Expect '{' before " + kind + " body.");
        List<Stmt> body = block();
        return new Stmt.Function(name, parameters, body);
    }


    private List<Stmt> block() {
        var stmts = new ArrayList<Stmt>();
        while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) stmts.add(declaration());
        consume(TokenType.RIGHT_BRACE, "Expect '}' after block.");
        return stmts;
    }

    /* ---------------------------
       Expressions
    --------------------------- */
    private Expr expression() { return assignment(); }

    private Expr assignment() {
        var expr = or();
        if (match(TokenType.EQUAL)) {
            var equals = previous();
            var value = assignment();
            return switch (expr) {
                case Expr.Variable v -> new Expr.Assign(v.name(), value);
                case Expr.Get g -> new Expr.Set(g.object(), g.name(), value);
                default -> throw error(equals, "Invalid assignment target.");
            };
        }
        return expr;
    }

    private Expr or() { return logical(this::and, TokenType.OR); }
    private Expr and() { return logical(this::equality, TokenType.AND); }

    private Expr logical(Supplier<Expr> next, TokenType op) {
        var expr = next.get();
        while (match(op)) expr = new Expr.Logical(expr, previous(), next.get());
        return expr;
    }

    private Expr equality() { return binary(this::comparison, TokenType.BANG_EQUAL, TokenType.EQUAL_EQUAL); }
    private Expr comparison() { return binary(this::term, TokenType.GREATER, TokenType.GREATER_EQUAL, TokenType.LESS, TokenType.LESS_EQUAL); }
    private Expr term() { return binary(this::factor, TokenType.MINUS, TokenType.PLUS); }
    private Expr factor() { return binary(this::unary, TokenType.SLASH, TokenType.STAR); }

    private Expr binary(Supplier<Expr> next, TokenType... operators) {
        var expr = next.get();
        while (match(operators)) expr = new Expr.Binary(expr, previous(), next.get());
        return expr;
    }

    private Expr unary() {
        if (match(TokenType.BANG, TokenType.MINUS)) return new Expr.Unary(previous(), unary());
        return call();
    }

    private Expr call() {
        var expr = primary();
        while (true) {
            if (match(TokenType.LEFT_PAREN)) expr = finishCall(expr);
            else if (match(TokenType.DOT)) expr = new Expr.Get(expr, consume(TokenType.IDENTIFIER, "Expect property name after '.'"));
            else break;
        }
        return expr;
    }

    private Expr finishCall(Expr callee) {
        var args = parseSeparated(this::expression, TokenType.RIGHT_PAREN, TokenType.COMMA, MAX_ARGS);
        var paren = consume(TokenType.RIGHT_PAREN, "Expect ')' after arguments.");
        return new Expr.Call(callee, paren, args);
    }

    private Expr primary() {
        return switch (peek().type()) {
            case FALSE -> { advance(); yield new Expr.Literal(false); }
            case TRUE -> { advance(); yield new Expr.Literal(true); }
            case NIL -> { advance(); yield new Expr.Literal(null); }
            case NUMBER, STRING -> { advance(); yield new Expr.Literal(previous().literal()); } // Geändert: value() -> literal()
            case SUPER -> { 
                advance(); 
                var kw = previous(); 
                consume(TokenType.DOT, "Expect '.' after 'super'."); 
                yield new Expr.Super(kw, consume(TokenType.IDENTIFIER, "Expect superclass method name.")); 
            }
            case THIS -> { advance(); yield new Expr.This(previous()); }
            case IDENTIFIER -> { advance(); yield new Expr.Variable(previous()); }
            case LEFT_PAREN -> { 
                advance(); 
                var expr = expression(); 
                consume(TokenType.RIGHT_PAREN, "Expect ')' after expression."); 
                yield new Expr.Grouping(expr); 
            }
            default -> throw error(peek(), "Expect expression.");
        };
    }

    /* ---------------------------
       parseSeparated Helper
    --------------------------- */
    private <T> List<T> parseSeparated(
        Supplier<T> elementSupplier,
        TokenType end,
        TokenType sep,
        int max
    ) {
        List<T> elements = new ArrayList<>();
        if (check(end)) return elements;

        do {
            if (elements.size() >= max) {
            throw error(peek(), "Too many elements.");
        }
        elements.add(elementSupplier.get());
        } while (match(sep));

        return elements;
    }

    /* ---------------------------
       Token Helpers
    --------------------------- */
    private boolean match(TokenType... types) { 
        for (var t : types) {
            if (check(t)) { 
                advance(); 
                return true; 
            }
        }
        return false; 
    }
    
    private boolean checkAndAdvance(TokenType type) { 
        if (check(type)) { 
            advance(); 
            return true; 
        }
        return false; 
    }
    
    private Token consume(TokenType type, String message) { 
        if (check(type)) return advance(); 
        throw error(peek(), message); 
    }
    
    private boolean check(TokenType type) { 
        return !isAtEnd() && peek().type() == type; 
    }
    
    private Token advance() { 
        if (!isAtEnd()) current++; 
        return previous(); 
    }
    
    private boolean isAtEnd() { 
        return peek().type() == TokenType.EOF; 
    }
    
    private Token peek() { 
        return tokens.get(current); 
    }
    
    private Token previous() { 
        return tokens.get(current - 1); 
    }

    private ParseError error(Token token, String message) { 
        System.err.println("Error at line " + token.line() + ": " + message);
        return new ParseError(); 
    }

    private void synchronize() {
        advance();
        while (!isAtEnd()) {
            if (previous().type() == TokenType.SEMICOLON) return;
            switch (peek().type()) {
                case CLASS, FUN, VAR, FOR, IF, WHILE, PRINT, RETURN -> { return; }
                default -> advance();
            }
        }
    }
}