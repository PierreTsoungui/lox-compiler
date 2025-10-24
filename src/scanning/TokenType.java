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
    // Identifiers and Literals
    IDENTIFIER,     // Variable or function name
    NUMBER,         // Numeric literal
    STRING,         // String literal


    // End of file marker
    EOF ;           // Signals the end of the source input


public static final Map<String, TokenType>KEYWORDS= new HashMap <>();
    static{
        KEYWORDS.put("and", AND);
        KEYWORDS.put("class", CLASS);
        KEYWORDS.put("else", ELSE);
        KEYWORDS.put("false", FALSE);
        KEYWORDS.put("fun", FUN);
        KEYWORDS.put("for", FOR);
        KEYWORDS.put("if", IF);
        KEYWORDS.put("nil", NIL);
        KEYWORDS.put("or", OR);
        KEYWORDS.put("print", PRINT);
        KEYWORDS.put("return", RETURN);
        KEYWORDS.put("super", SUPER);
        KEYWORDS.put("this", THIS);
        KEYWORDS.put("true", TRUE);
        KEYWORDS.put("var", VAR);
        KEYWORDS.put("while", WHILE);
    }

}