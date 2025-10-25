package scanning;

import java.util.*;

public class Scanner {
    private final String source;
    private final List<Token> tokens = new ArrayList<>();
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

    private void scanToken() {
        char c = advance();
        switch (c) {
            case '(': addToken(TokenType.LEFT_PAREN); break;
            case ')': addToken(TokenType.RIGHT_PAREN); break;
            case '{': addToken(TokenType.LEFT_BRACE); break;
            case '}': addToken(TokenType.RIGHT_BRACE); break;
            case '[': addToken(TokenType.LEFT_BRACKET); break;   // fix
            case ']': addToken(TokenType.RIGHT_BRACKET); break;  // fix
            case ',': addToken(TokenType.COMMA); break;
            case '.': addToken(TokenType.DOT); break;
            case '-': addToken(TokenType.MINUS); break;
            case '+': addToken(TokenType.PLUS); break;
            case ';': addToken(TokenType.SEMICOLON); break;
            case '*': addToken(TokenType.STAR); break;
            case '!':
                addToken(match('=') ? TokenType.BANG_EQUAL : TokenType.BANG);
                break;
            case '=':
                addToken(match('=') ? TokenType.EQUAL_EQUAL : TokenType.EQUAL);
                break;
            case '<':
                addToken(match('=') ? TokenType.LESS_EQUAL : TokenType.LESS);
                break;
            case '>':
                addToken(match('=') ? TokenType.GREATER_EQUAL : TokenType.GREATER);
                break;
            case '/':
                if (match('/')) {
                    while (peek() != '\n' && !isAtEnd()) advance(); // Kommentar überspringen
                } else {
                    addToken(TokenType.SLASH);
                }
                break;
            case ' ':
            case '\r':
            case '\t':
                break; // ignorieren
            case '\n':
                line++;
                break;
            case '"':
                string();
                break;
            default:
                if (isDigit(c)) {
                    number();
                } else if (isAlpha(c)) {
                    identifier();
                } else {
                    System.err.println("Unerwartetes Zeichen: " + c + " in Zeile " + line);
                }
                break;
        }
    }

    // --- Hilfsmethoden ---
    boolean isAtEnd() {
        return current >= source.length();
    }

    char advance() {
        current++;
        return source.charAt(current - 1);
    }

    void addToken(TokenType type) {
        addToken(type, null);
    }

    void addToken(TokenType type, Object value) {
        String lex = source.substring(start, current);
        tokens.add(new Token(type, lex, value, line));
    }

    boolean match(char expected) {
        if (isAtEnd() || source.charAt(current) != expected) return false;
        current++;
        return true;
    }

    char peek() {
        return isAtEnd() ? '\0' : source.charAt(current);
    }

    char peekNext() {
        return (current + 1 >= source.length()) ? '\0' : source.charAt(current + 1);
    }

    boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    boolean alphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }

    // --- Token-Erzeugung ---
    void string() {
        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n') line++;
            advance();
        }

        if (isAtEnd()) {
            System.err.println("Unterminated string at line " + line);
            return;
        }

        advance(); // schließendes "
        String text = source.substring(start + 1, current - 1);
        addToken(TokenType.STRING, text);
    }

    void number() {
        while (isDigit(peek())) advance();

        if (peek() == '.' && isDigit(peekNext())) {
            advance(); // consume '.'
            while (isDigit(peek())) advance();
        }

        String text = source.substring(start, current);
        addToken(TokenType.NUMBER, Double.parseDouble(text));
    }

    void identifier() {
        while (alphaNumeric(peek())) advance();
        String text = source.substring(start, current);
        TokenType type = TokenType.keywords.getOrDefault(text, TokenType.IDENTIFIER);
        addToken(type);
    }
}
