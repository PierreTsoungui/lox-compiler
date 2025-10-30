package scanning;

import java.util.*;
public enum TokenType {

    // Keywords
    FUN,        // Function definition keyword
    VAR,        // Variable declaration keyword
    IF,         // Conditional statement keyword
    ELSE,       // Begins an else block in conditional statements
    WHILE,      // Loop keyword
    RETURN,     // Return statement keyword
    AND,        // Logical AND operator
    OR,         // Logical OR operator
    NOT,        // Logical NOT operator
    TRUE,       // Boolean literal 'true'
    FALSE,      // Boolean literal 'false'
    CLASS,      // class declaration keyword
    SUPER,      // refers to the superclass in a class method
    FOR,        // for-loop keyword
    PRINT,      // print statement keyword
    THIS,      // refers to the current object instance
    NIL,       // null-like value in Lox

    // Symbols and Operators
    LEFT_PAREN,     // (
    RIGHT_PAREN,    // )
    LEFT_BRACE,     // {
    RIGHT_BRACE,    // }
    LEFT_BRACKET,   // [
    RIGHT_BRACKET,  // ]
    COMMA,          // ,
    MINUS,          // -
    PLUS,           // +
    STAR,           // *
    SLASH,          // /
    SEMICOLON,      // ;
    EQUAL,          // =
    EQUAL_EQUAL,    // ==
    LESS,           // <
    GREATER,          // >
    LESS_EQUAL,     // <=
    GREATER_EQUAL,  // >=
    BANG ,          // !
    BANG_EQUAL,     // !=  
    DOT,            // .
    // Identifiers and Literals
    IDENTIFIER,     // Variable or function name
    NUMBER,         // Numeric literal
    STRING,         // String literal


    // End of file marker
    EOF ;           // Signals the end of the source input


public static final Map<String, TokenType>keywords= new HashMap <>();
   
    static {
        keywords.put("and", TokenType.AND);
        keywords.put("class", TokenType.CLASS);
        keywords.put("else", TokenType.ELSE);
        keywords.put("false", TokenType.FALSE);
        keywords.put("for", TokenType.FOR);
        keywords.put("fun", TokenType.FUN);
        keywords.put("if", TokenType.IF);
        keywords.put("nil", TokenType.NIL);
        keywords.put("or", TokenType.OR);
        keywords.put("print", TokenType.PRINT);
        keywords.put("return", TokenType.RETURN);
        keywords.put("super", TokenType.SUPER);
        keywords.put("this", TokenType.THIS);
        keywords.put("true", TokenType.TRUE);
        keywords.put("var", TokenType.VAR);
        keywords.put("while", TokenType.WHILE);
}
  // Maps für Operatoren und Separatoren
    public static final Map<String, TokenType> op= Map.ofEntries(
            Map.entry("+", TokenType.PLUS),
            Map.entry("-", TokenType.MINUS),
            Map.entry("*", TokenType.STAR),
            Map.entry("/", TokenType.SLASH),
            Map.entry("==", TokenType.EQUAL_EQUAL),
            Map.entry("=", TokenType.EQUAL),
            Map.entry("!=", TokenType.BANG_EQUAL),
            Map.entry("<", TokenType.LESS),
            Map.entry("<=", TokenType.LESS_EQUAL),
            Map.entry(">", TokenType.GREATER),
            Map.entry(">=", TokenType.GREATER_EQUAL),
            Map.entry("!", TokenType.BANG)
    );

    public static final Map<String, TokenType> sep = Map.ofEntries(
            Map.entry("(", TokenType.LEFT_PAREN),
            Map.entry(")", TokenType.RIGHT_PAREN),
            Map.entry("{", TokenType.LEFT_BRACE),
            Map.entry("}", TokenType.RIGHT_BRACE),
            Map.entry("[", TokenType.LEFT_BRACKET),
            Map.entry("]", TokenType.RIGHT_BRACKET),
            Map.entry(",", TokenType.COMMA),
            Map.entry(";", TokenType.SEMICOLON),
            Map.entry(".", TokenType.DOT)
    );   

}