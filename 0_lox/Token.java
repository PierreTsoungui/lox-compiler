
// --- Token Record ---
record Token(TokenType type, String lexeme, Object literal, int line) {
    @Override
    public String toString() {
        return String.format("TOKEN(%s, %s, %s) on line %d", type, lexeme, literal, line);
    }
}
