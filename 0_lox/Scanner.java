import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// --- Modernisierte Scanner-Klasse (unabhängig von Lox) ---
public class Scanner {

    // Keyword Map
    private static final Map<String, TokenType> keywords = Map.ofEntries(
        Map.entry("and", TokenType.AND),
        Map.entry("class", TokenType.CLASS),
        Map.entry("else", TokenType.ELSE),
        Map.entry("false", TokenType.FALSE),
        Map.entry("for", TokenType.FOR),
        Map.entry("fun", TokenType.FUN),
        Map.entry("if", TokenType.IF),
        Map.entry("nil", TokenType.NIL),
        Map.entry("or", TokenType.OR),
        Map.entry("print", TokenType.PRINT),
        Map.entry("return", TokenType.RETURN),
        Map.entry("super", TokenType.SUPER),
        Map.entry("this", TokenType.THIS),
        Map.entry("true", TokenType.TRUE),
        Map.entry("var", TokenType.VAR),
        Map.entry("while", TokenType.WHILE)
    );

    private final String source;
    final List<Token> tokens = new ArrayList<>();
    private boolean hadError = false;
    private int start = 0;
    private int current = 0;
    private int line = 1;

    public Scanner(String source) {
        this.source = source;
    }

    public List<Token> scanTokens() {
        while (!isAtEnd()) {
            start = current;
            scanToken();
        }
        tokens.add(new Token(TokenType.EOF, "", null, line));
        return tokens;
    }
    
    public boolean hadError() {
        return hadError;
    }

    private void error(int line, String message) {
        System.err.println("[line " + line + "] Error: " + message);
        hadError = true;
    }

    public void scanToken() {
        char c = advance();
        switch (c) {
            case '(', ')', '{', '}', ',', '.', '-', '+', ';', '*' ->
                addToken(switch (c) {
                    case '(' -> TokenType.LEFT_PAREN;
                    case ')' -> TokenType.RIGHT_PAREN;
                    case '{' -> TokenType.LEFT_BRACE;
                    case '}' -> TokenType.RIGHT_BRACE;
                    case ',' -> TokenType.COMMA;
                    case '.' -> TokenType.DOT;
                    case '-' -> TokenType.MINUS;
                    case '+' -> TokenType.PLUS;
                    case ';' -> TokenType.SEMICOLON;
                    case '*' -> TokenType.STAR;
                    default -> throw new IllegalStateException("Unexpected char: " + c);
                });
            case '!' -> addToken(match('=') ? TokenType.BANG_EQUAL : TokenType.BANG);
            case '=' -> addToken(match('=') ? TokenType.EQUAL_EQUAL : TokenType.EQUAL);
            case '<' -> addToken(match('=') ? TokenType.LESS_EQUAL : TokenType.LESS);
            case '>' -> addToken(match('=') ? TokenType.GREATER_EQUAL : TokenType.GREATER);
            case '/' -> {
                if (match('/')) while (peek() != '\n' && !isAtEnd()) advance();
                else addToken(TokenType.SLASH);
            }
            case ' ', '\r', '\t' -> {}
            case '\n' -> line++;
            case '"' -> string();
            default -> {
                if (isDigit(c)) number();
                else if (isAlpha(c)) identifier();
                else error(line, "Unexpected character."); // GEÄNDERT: Lox.error → error
            }
        }
    }

    private void identifier() {
        while (isAlphaNumeric(peek())) advance();
        String text = source.substring(start, current);

        TokenType type = keywords.getOrDefault(text, TokenType.IDENTIFIER);
        addToken(type);
    }

    private void number() {
        while (isDigit(peek())) advance();
        if (peek() == '.' && isDigit(peekNext())) {
            advance();
            while (isDigit(peek())) advance();
        }
        
        try {
            addToken(TokenType.NUMBER, Double.parseDouble(source.substring(start, current)));
        } catch (NumberFormatException e) {
            error(line, "Invalid number format.");
        }
    }

    private void string() {
        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n') line++;
            advance();
        }
        if (isAtEnd()) {
            error(line, "Unterminated string."); // GEÄNDERT: Lox.error → error
            return;
        }
        advance();
        addToken(TokenType.STRING, source.substring(start + 1, current - 1));
    }

    private boolean match(char expected) {
        if (isAtEnd()) return false;
        if (source.charAt(current) != expected) return false;
        current++;
        return true;
    }

    private char peek() { return isAtEnd() ? '\0' : source.charAt(current); }
    private char peekNext() { return current + 1 >= source.length() ? '\0' : source.charAt(current + 1); }
    private boolean isAlpha(char c) { return Character.isLetter(c) || c == '_'; }
    private boolean isAlphaNumeric(char c) { return isAlpha(c) || isDigit(c); }
    private boolean isDigit(char c) { return c >= '0' && c <= '9'; }
    private boolean isAtEnd() { return current >= source.length(); }
    private char advance() { return source.charAt(current++); }
    private void addToken(TokenType type) { addToken(type, null); }
    private void addToken(TokenType type, Object literal) {
        String text = source.substring(start, current);
        tokens.add(new Token(type, text, literal, line));
    }
}