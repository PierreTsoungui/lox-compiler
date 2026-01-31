import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.*;
import java.util.function.BiConsumer;

// ------------------ Token ------------------
record Token(TokenType type, String lexem, Object value, int line) {
    @Override
    public String toString() {
        return String.format("TOKEN(%s, %s, %s) on line %d", type, lexem, value, line);
    }
}

// ------------------ Token Types ------------------
enum TokenType {
    // Keywords
    FUN, VAR, IF, ELSE, WHILE, RETURN, AND, OR, NOT, TRUE, FALSE,
    CLASS, SUPER, FOR, PRINT, THIS, NIL,
    // Symbols & Operators
    LEFT_PAREN, RIGHT_PAREN, LEFT_BRACE, RIGHT_BRACE, LEFT_BRACKET, RIGHT_BRACKET,
    COMMA, MINUS, PLUS, STAR, SLASH, SEMICOLON,
    EQUAL, EQUAL_EQUAL, LESS, GREATER, LESS_EQUAL, GREATER_EQUAL,
    BANG, BANG_EQUAL, DOT,
    // Identifiers & Literals
    IDENTIFIER, NUMBER, STRING,
    // EOF
    EOF;

    public static final Map<String, TokenType> keywords = new HashMap<>();
    static {
        keywords.put("and", AND); keywords.put("class", CLASS); keywords.put("else", ELSE);
        keywords.put("false", FALSE); keywords.put("for", FOR); keywords.put("fun", FUN);
        keywords.put("if", IF); keywords.put("nil", NIL); keywords.put("or", OR);
        keywords.put("print", PRINT); keywords.put("return", RETURN);
        keywords.put("super", SUPER); keywords.put("this", THIS);
        keywords.put("true", TRUE); keywords.put("var", VAR); keywords.put("while", WHILE);
    }

    public static final Map<String, TokenType> op = Map.ofEntries(
        Map.entry("+", PLUS), Map.entry("-", MINUS), Map.entry("*", STAR), Map.entry("/", SLASH),
        Map.entry("==", EQUAL_EQUAL), Map.entry("=", EQUAL), Map.entry("!=", BANG_EQUAL),
        Map.entry("<", LESS), Map.entry("<=", LESS_EQUAL), Map.entry(">", GREATER), Map.entry(">=", GREATER_EQUAL),
        Map.entry("!", BANG)
    );

    public static final Map<String, TokenType> sep = Map.ofEntries(
        Map.entry("(", LEFT_PAREN), Map.entry(")", RIGHT_PAREN), Map.entry("{", LEFT_BRACE),
        Map.entry("}", RIGHT_BRACE), Map.entry("[", LEFT_BRACKET), Map.entry("]", RIGHT_BRACKET),
        Map.entry(",", COMMA), Map.entry(";", SEMICOLON), Map.entry(".", DOT)
    );
}

public class Scanner {
    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private int line = 1;
    private boolean error = false;

    public Scanner(String source) { this.source = source; }

    // ------------------ Tokenizing ------------------
    public List<Token> tokenize() {
        int pos = 0;
        while (pos < source.length()) {
            boolean matched = false;
            for (TokenPattern tp : TokenPattern.values()) {
                Matcher m = Pattern.compile(tp.regex).matcher(source.substring(pos));
                if (m.lookingAt()) { tp.handler.accept(m, this); pos += m.end(); matched = true; break; }
            }
            if (!matched) { error("Unbekanntes Zeichen: " + source.charAt(pos)); break; }
            if (error) break;
        }
        if (!error) addToken(TokenType.EOF, "", null);
        return tokens;
    }

    private void handleSeparator(String sep, int endIndex) {
        if (sep.equals(".") && !tokens.isEmpty() && tokens.get(tokens.size()-1).type()==TokenType.NUMBER
                && endIndex < source.length() && Character.isDigit(source.charAt(endIndex))) {
            addToken(TokenType.DOT, sep, null); return;
        }
        addToken(TokenType.sep.get(sep), sep, null);
    }

    private void addToken(TokenType type, String lexem, Object value) { tokens.add(new Token(type, lexem, value, line)); }

    private void error(String msg) { System.err.println(msg + " in Zeile " + line); error = true; }

    // ------------------ Token Patterns ------------------
    private enum TokenPattern {
        NUMBER("(?<NUMBER>[0-9]+(\\.[0-9]+)?)", (m, t) -> t.addToken(TokenType.NUMBER, m.group("NUMBER"), Double.parseDouble(m.group("NUMBER")))),
        STRING("(?<STRING>\"[^\"]*\")", (m,t)->t.addToken(TokenType.STRING, m.group("STRING"), m.group("STRING").substring(1,m.group("STRING").length()-1))),
        LINE_COMMENT("//[^\\n]*", (_m,t)->{}),
        IDENTIFIER("(?<IDENTIFIER>[a-zA-Z_][a-zA-Z0-9_]*)", (m,t)->{
            String v = m.group("IDENTIFIER");
            t.addToken(TokenType.keywords.getOrDefault(v, TokenType.IDENTIFIER), v, null);
        }),
        SEPARATOR("(?<SEPARATOR>[(){}.,;\\[\\]])", (m,t)->t.handleSeparator(m.group("SEPARATOR"), m.end())),
        OPERATOR("(?<OPERATOR>==|!=|<=|>=|\\+|\\-|\\*|/|=|<|>|!)", (m,t)->t.addToken(TokenType.op.get(m.group("OPERATOR")), m.group("OPERATOR"), null)),
        NEWLINE("(?<NEWLINE>\\n)", (_m,t)->t.line++),
        WHITESPACE("(?<WHITESPACE>[ \\t\\r]+)", (_m,_t)->{}),
        UNKNOWN("(?<UNKNOWN>.)", (m,t)->t.error("Unbekanntes Zeichen: " + m.group()));

        final String regex; final BiConsumer<Matcher, Scanner> handler;
        TokenPattern(String regex, BiConsumer<Matcher, Scanner> handler){ this.regex = regex; this.handler = handler; }
    }

    // ------------------ Testmethoden ------------------
    public static void test(String input) { new Scanner(input).tokenize().forEach(System.out::println); }

    public static void testFile(String filename){
        try { String source = Files.readString(Path.of(filename)); test(source); }
        catch(IOException e){ System.err.println("Fehler beim Lesen der Datei: " + e.getMessage()); }
    }
}
