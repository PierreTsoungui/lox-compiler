package scanning;

/**
 * A Token represents a lexical unit in Lox source code.
 *
 * @param type Type of the token (from TokenType)
 * @param lexeme Actual text read from source
 * @param value Literal value if applicable, otherwise null
 * @param line Line number in source code
 */
public record Token (TokenType type,   String lexem, Object value , int line){


    /**
     * representation of the token for debugging purposes.
     * 
     * @return A formatted string representing the token.
     */
   @Override
    public String toString() {
     return String.format("TOKEN(%s, %s, %s) on line %d", type, lexem, value, line);
    }

}
