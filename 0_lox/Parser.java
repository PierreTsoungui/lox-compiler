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
            if (match(TokenType.CLASS)) return classDeclaration();
            if (match(TokenType.FUN)) return function("function");
            if (match(TokenType.VAR)) return varDeclaration();
            return statement();
        } catch (ParseError e) {
            synchronize();
            return null;
        }
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
        if (match(TokenType.FOR)) return forStatement();
        if (match(TokenType.IF)) return ifStatement();
        if (match(TokenType.PRINT)) return printStatement();
        if (match(TokenType.RETURN)) return returnStatement();
        if (match(TokenType.WHILE)) return whileStatement();
        if (match(TokenType.LEFT_BRACE)) return new Stmt.Block(block());
        return expressionStatement();
    }
private Stmt forStatement() {
    consume(TokenType.LEFT_PAREN, "Expect '(' after 'for'.");

    // Initializer
    Stmt initializer;
    if (match(TokenType.SEMICOLON)) {
        initializer = null;
    } else if (match(TokenType.VAR)) {
        initializer = varDeclaration();
    } else {
        initializer = expressionStatement();
    }

    // Condition
    Expr condition = !check(TokenType.SEMICOLON) ? expression() : null;
    consume(TokenType.SEMICOLON, "Expect ';' after loop condition.");

    // Increment
    Expr increment = !check(TokenType.RIGHT_PAREN) ? expression() : null;
    consume(TokenType.RIGHT_PAREN, "Expect ')' after for clauses.");

    // Body
    Stmt body = statement();

    // Inkrement in den Body einfügen
    if (increment != null) {
        if (body instanceof Stmt.Block b) {
            var newStmts = new ArrayList<Stmt>(b.statements());
            newStmts.add(new Stmt.Expression(increment));
            body = new Stmt.Block(newStmts);
        } else {
            body = new Stmt.Block(Arrays.asList(body, new Stmt.Expression(increment)));
        }
    }

    // While-Schleife mit Bedingung (oder true)
    Stmt whileLoop = new Stmt.While(
        condition != null ? condition : new Expr.Literal(true),
        body
    );

    // Initializer ausführen, aber **kein neues Environment**, nur als Block mit While
    if (initializer != null) {
        return new Stmt.Block(Arrays.asList(initializer, whileLoop));
    }

    return whileLoop;
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
    
        Expr value = expression();
        consume(TokenType.SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(value);
    }

    private Stmt returnStatement() {
        Token keyword = previous(); 
        Expr value = !check(TokenType.SEMICOLON) ? expression() : null;
        consume(TokenType.SEMICOLON, "Expect ';' after return value.");
        return new Stmt.Return(keyword, value);
    }

    private Stmt varDeclaration() {
        // 'var' wurde bereits von declaration() gematcht
        var name = consume(TokenType.IDENTIFIER, "Expect variable name.");
        Expr initializer = match(TokenType.EQUAL) ? expression() : null;
        consume(TokenType.SEMICOLON, "Expect ';' after variable declaration.");
        return new Stmt.Var(name, initializer);
    }

    private Stmt whileStatement() {
        // 'while' wurde bereits von statement() gematcht
        consume(TokenType.LEFT_PAREN, "Expect '(' after 'while'.");
        var condition = expression();
        consume(TokenType.RIGHT_PAREN, "Expect ')' after condition.");
        var body = statement();
        return new Stmt.While(condition, body);
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
        while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
            var decl = declaration();
            if (decl != null) stmts.add(decl);
        }
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
        if (match(TokenType.FALSE)) return new Expr.Literal(false);
        if (match(TokenType.TRUE)) return new Expr.Literal(true);
        if (match(TokenType.NIL)) return new Expr.Literal(null);
        if (match(TokenType.NUMBER, TokenType.STRING)) {
            return new Expr.Literal(previous().literal());
        }
        if (match(TokenType.SUPER)) {
            var keyword = previous();
            consume(TokenType.DOT, "Expect '.' after 'super'.");
            var method = consume(TokenType.IDENTIFIER, "Expect superclass method name.");
            return new Expr.Super(keyword, method);
        }
        if (match(TokenType.THIS)) return new Expr.This(previous());
        if (match(TokenType.IDENTIFIER)) return new Expr.Variable(previous());
        if (match(TokenType.LEFT_PAREN)) {
            var expr = expression();
            consume(TokenType.RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }
        throw error(peek(), "Expect expression.");
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