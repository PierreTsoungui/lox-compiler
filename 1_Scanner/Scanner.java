// --------------------------------------------
    //  TokenType (inneres Enum)
    // --------------------------------------------
    public static enum TokenType {
        // Keywords
        FUN, VAR, IF, ELSE, WHILE, RETURN, AND, OR, NOT, TRUE, FALSE,
        CLASS, SUPER, FOR, PRINT, THIS, NIL,

        // Symbols & Operators
        LEFT_PAREN, RIGHT_PAREN, LEFT_BRACE, RIGHT_BRACE,
        LEFT_BRACKET, RIGHT_BRACKET,
        COMMA, MINUS, PLUS, STAR, SLASH, SEMICOLON,
        EQUAL, EQUAL_EQUAL, LESS, GREATER, LESS_EQUAL, GREATER_EQUAL,
        BANG, BANG_EQUAL, DOT,

        // Identifiers & Literals
        IDENTIFIER, NUMBER, STRING,

        // EOF
        EOF;

        public static final Map<String, TokenType> keywords = new HashMap<>();
        static {
            keywords.put("and", AND);
            keywords.put("class", CLASS);
            keywords.put("else", ELSE);
            keywords.put("false", FALSE);
            keywords.put("for", FOR);
            keywords.put("fun", FUN);
            keywords.put("if", IF);
            keywords.put("nil", NIL);
            keywords.put("or", OR);
            keywords.put("print", PRINT);
            keywords.put("return", RETURN);
            keywords.put("super", SUPER);
            keywords.put("this", THIS);
            keywords.put("true", TRUE);
            keywords.put("var", VAR);
            keywords.put("while", WHILE);
        }

        public static final Map<String, TokenType> op = Map.ofEntries(
                Map.entry("+", PLUS),
                Map.entry("-", MINUS),
                Map.entry("*", STAR),
                Map.entry("/", SLASH),
                Map.entry("==", EQUAL_EQUAL),
                Map.entry("=", EQUAL),
                Map.entry("!=", BANG_EQUAL),
                Map.entry("<", LESS),
                Map.entry("<=", LESS_EQUAL),
                Map.entry(">", GREATER),
                Map.entry(">=", GREATER_EQUAL),
                Map.entry("!", BANG)
        );

        public static final Map<String, TokenType> sep = Map.ofEntries(
                Map.entry("(", LEFT_PAREN),
                Map.entry(")", RIGHT_PAREN),
                Map.entry("{", LEFT_BRACE),
                Map.entry("}", RIGHT_BRACE),
                Map.entry("[", LEFT_BRACKET),
                Map.entry("]", RIGHT_BRACKET),
                Map.entry(",", COMMA),
                Map.entry(";", SEMICOLON),
                Map.entry(".", DOT)
        );
    }

      // --------------------------------------------
    //  TestMehode
    // --------------------------------------------

    public static void test(String input) {
        Scanner scanner = new Scanner(input);
        List<Token> tokens = scanner.tokenize();
        tokens.forEach(System.out::println);
    }


     // --------------------------------------------
    //  TestMehode with file 
    // --------------------------------------------
    public static void testFile(String filename) {
        try
        {
            String source = Files.readString(Path.of(filename));
            Scanner scanner = new Scanner(source);
            List<Token> tokens = scanner.tokenize();
            tokens.forEach(System.out::println);
        } catch (IOException e) {
            System.err.println("Fehler beim Lesen der Datei: " + e.getMessage());
        }
    }
