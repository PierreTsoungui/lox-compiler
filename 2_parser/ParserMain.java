
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;


/* ================= Token ================= */
record Token(TokenType type, String lexem, Object value, int line) {
    @Override
    public String toString() {
        return String.format("TOKEN(%s, %s, %s) on line %d", type, lexem, value, line);
    }
}

/* ================= TokenType ================= */
enum TokenType {
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

/* ================= AstNode ================= */
interface AstNode {
    List<AstNode> children();
    String label();
}
/* ================= Expr ================= */
sealed interface Expr extends AstNode
    permits Expr.Binary, Expr.Unary, Expr.Literal, Expr.Grouping,
            Expr.Assign, Expr.CallExpr, Expr.GetExpr, Expr.SuperExpr, Expr.ThisExpr,
            Expr.Variable {

    /* ---------------- Binary Expression ---------------- */
    record Binary(Expr left, Token op, Expr right) implements Expr {
        @Override
        public List<AstNode> children() {
            return List.of(left, right);
        }

        @Override
        public String label() {
            return op.lexem();
        }
    }

    /* ---------------- Unary Expression ---------------- */
    record Unary(Token op, Expr right) implements Expr {
        @Override
        public List<AstNode> children() {
            return List.of(right);
        }

        @Override
        public String label() {
            return op.lexem();
        }
    }

    /* ---------------- Literal Expression ---------------- */
    record Literal(Object value) implements Expr {
        @Override
        public List<AstNode> children() {
            return List.of();
        }

        @Override
        public String label() {
            return value == null ? "nil" : value.toString();
        }
    }

    /* ---------------- Grouping Expression ---------------- */
    record Grouping(Expr expression) implements Expr {
        @Override
        public List<AstNode> children() {
            return List.of(expression);
        }

        @Override
        public String label() {
            return "()";
        }
    }

    /* ---------------- Assignment Expression ---------------- */
    record Assign(Optional<Expr> obj, Token name, Expr value) implements Expr {

        @Override
        public List<AstNode> children() {
        
            return Stream.concat(
                obj.stream(),                   // optionales Objekt
                Stream.of(new TokenNode(name), value) // Name + Wert
            ).toList();
        }

        @Override
        public String label() {
            return "Assign"; 
        }
    }


    /* ---------------- AssignExpr (Variable Assignment) ---------------- */
  /*  record AssignExpr(Token name, Expr value) implements Expr {
        @Override
        public List<AstNode> children() {
            return List.of(new TokenNode(name), value);
        }

        @Override
        public String label() {
            return name.lexem();
        }
    }*/

    /* ---------------- Call Expression ---------------- */
   record CallExpr(Expr callee, List<Expr> arguments) implements Expr {
        @Override
        public List<AstNode> children() {
        // Optional: arguments als eigener Knoten „args“
        if (arguments.isEmpty()) return List.of(callee);
            List<AstNode> children = new ArrayList<>();
            children.add(callee);
            children.addAll(arguments); // oder wrap in ein ArgsNode
            return children;
        }

       @Override
        public String label() {
                         // callee schön darstellen
            if (callee instanceof Variable v)
            return "CallExpr(" + v.name().lexem() + ")";

            if (callee instanceof GetExpr g)
            return "CallExpr(" + g.name().lexem() + ")";

            return "CallExpr";
        }



    }

    /* ---------------- Get Expression ---------------- */
    record GetExpr(Expr object, Token name) implements Expr {
        @Override
        public List<AstNode> children() {
            return List.of(object);
        }

        @Override
        public String label() {
            // Rekursiv: object.label() liefert komplette Kette
            return "GetExpr(." + name.lexem() + ")";
        }
    }

    /* ---------------- Super Expression ---------------- */
    record SuperExpr(Token name) implements Expr {
        @Override
        public List<AstNode> children() {
            return List.of(new TokenNode(name));
        }

        @Override
        public String label() {
            return "super." + name.lexem();
        }
    }

    /* ---------------- This Expression ---------------- */
    record ThisExpr(Token keyword) implements Expr {
        @Override
        public List<AstNode> children() {
            return List.of();
        }

        @Override
        public String label() {
            return "this";
        }
    }

    /* ---------------- Variable Expression ---------------- */
    record Variable(Token name) implements Expr {
        @Override
        public List<AstNode> children() {
            return List.of();
        }

        @Override
        public String label() {
            return name.lexem();
        }
    }
}


/* ================= Parser Interfaces ================= */
sealed interface Parser<T>
    permits Success, Fail, Item, Or, And, Many, Parser.Lazy, MapParser, FlatMap,Maybe {

    Result<T> parse(List<Token> tokens);

    default <R> Parser<R> map(Function<T, R> f) {
        return new Lazy<>(() -> new MapParser<>(this, f));
    }

    default <R> Parser<R> flatMap(Function<T, Parser<R>> f) {
        return new Lazy<>(() -> new FlatMap<>(this, f));
    }

    final class Lazy<T> implements Parser<T> {
        private final Supplier<Parser<T>> supplier;
        private Parser<T> cached = null;
        public Lazy(Supplier<Parser<T>> supplier) { this.supplier = supplier; }
        @Override public Result<T> parse(List<Token> tokens) {
            if (cached == null) cached = supplier.get();
            return cached.parse(tokens);
        }
    }
}

/* ================= Result, Success, Fail ================= */
record Result<T>(Optional<T> recognized, List<Token> rest, boolean failed) {

    static <T> Result<T> of(T recognized, List<Token> rest) {
        return new Result<>(Optional.ofNullable(recognized), rest, false);
    }

    static <T> Result<T> fail(List<Token> rest) {
        return new Result<>(Optional.empty(), rest, true);
    }

    boolean hasFailed() { 
        return failed; 
    }

   
}


record Success<T>(T value) implements Parser<T> {
    @Override public Result<T> parse(List<Token> lst) { return Result.of(value, lst); }
}
record Fail<T>() implements Parser<T> {
   @Override public Result<T> parse(List<Token> tokens) { return Result.of(null, tokens); }

}
/* ================= Map & FlatMap ================= */
record MapParser<T, R>(Parser<T> parser, Function<T, R> f) implements Parser<R> {
    @Override public Result<R> parse(List<Token> tokens) {
        Result<T> res = parser.parse(tokens);
        if (res.hasFailed() ||res.recognized().isEmpty() ) return new Fail<R>().parse(tokens);
        return Result.of(f.apply(res.recognized().get()), res.rest());
    }
}
record FlatMap<T, R>(Parser<T> parser, Function<T, Parser<R>> f) implements Parser<R> {
    @Override
    public Result<R> parse(List<Token> tokens) {
        Result<T> res = parser.parse(tokens);

        if (res.hasFailed() || res.recognized().isEmpty()) {
            return Result.fail(tokens); // Optional leer → treat as fail oder wie benötigt
        }

        return f.apply(res.recognized().get()).parse(res.rest());
    }
}


/* ================= Item, Or, And, Many ================= */
record Item(TokenType expectedType) implements Parser<Token> {
    @Override
    public Result<Token> parse(List<Token> tokens) {
        if (tokens.isEmpty()) return new Fail<Token>().parse(tokens);

        Token first = tokens.get(0);

        if (first.type() == expectedType) {
            // Rest-Liste MUSS KOPIERT werden!
            List<Token> rest = new ArrayList<>(tokens.subList(1, tokens.size()));
            return Result.of(first, rest);
        }

        return Result.fail(tokens);
    }
}
record Or<T>(Parser<T>... parsers) implements Parser<T> {
    @SafeVarargs
    public Or {}

    @Override
    public Result<T> parse(List<Token> tokens) {
        return Arrays.stream(parsers)
                     .map(p -> p.parse(tokens))
                     .filter(r -> !r.hasFailed())         // nur erfolgreiche Ergebnisse
                     .findFirst()
                     .map(r -> Result.of(r.recognized().orElse(null), r.rest()))
                     .orElse(Result.fail(tokens));        // wenn keiner passt → Fail
    }
}

record And(Parser<?>... parsers) implements Parser<List<?>> {
    @SafeVarargs
    public And {}

    @Override
    public Result<List<?>> parse(List<Token> tokens) {
        List<Object> results = new ArrayList<>();
        List<Token> remaining = tokens;

        for (Parser<?> p : parsers) {
            Result<?> r = p.parse(remaining);

            if (r.hasFailed()) return Result.fail(tokens); // echter Fehler → abbrechen

            // Nur echte Werte aufnehmen
             results.add(r.recognized().orElse(null));
;

            remaining = r.rest();
        }

        return Result.of(results, remaining); // leere Liste möglich
    }
}
record Many<T>(Parser<T> parser) implements Parser<List<T>> {
    @Override
    public Result<List<T>> parse(List<Token> tokens) {
        List<T> results = new ArrayList<>();
        List<Token> current = tokens;

        while (true) {
            Result<T> res = parser.parse(current);
            if (res.hasFailed()) break;

            // wichtig: Tokens müssen weitergehen
            if (res.rest() == current) break;

            results.add(res.recognized().orElse(null));
            current = res.rest();
        }

        return Result.of(results, current);
    }
}



record Maybe<T>(Parser<T> parser) implements Parser<T> {
    @Override
    public Result<T> parse(List<Token> tokens) {
        Result<T> res = parser.parse(tokens);

        if (res.hasFailed()) {
            // Innerer Parser fehlgeschlagen → Maybe schlägt nicht fehl
            return Result.of(null, tokens);
        }

        // Erfolgreich → recognized und rest übernehmen
        return Result.of(res.recognized().orElse(null), res.rest());
    }
}



/* ================= Parser-Klasse (Grammatik) ================= */
public class ParserMain {

    final List<Token> tokens;

    public ParserMain(List<Token> tokens) {
        this.tokens = tokens;
    }

   


    static TokenType mapType(Scanner.TokenType st) {
      return TokenType.valueOf(st.name());
   }

     record Pair<A, B>(A first, B second) {}

    public static ParserMain fromSource(String source) {
        List<Token> tokens = new Scanner(source).tokenize()
                .stream()
            .map(t -> new Token(
            mapType(t.type()),    
            t.lexem(),
            t.value(),
            t.line()
        ))
        .toList();

        return new ParserMain(tokens);
    }


// program → declaration* EOF
Parser<Program> program() {
    return new Parser.Lazy<>(() ->
        new And(
            new Many<>(declaration()),
            new Item(TokenType.EOF)
        ).map(parts -> {
            @SuppressWarnings("unchecked")
            List<Stmt> decls = (List<Stmt>) parts.get(0);
            return new Program(decls);
        })
    );
}

Parser<Stmt> declaration() {
    return new Or<>(
        new Parser.Lazy<>(() -> classDecl())
       // new Parser. Lazy<>(() -> funDecl()),
        //new Parser.Lazy<>(() -> varDecl()),
       // new Parser.Lazy<>(() -> statement())
    );
}

     
    Parser<Token> superClassOpt =
        new Or<>(
         new And(
            new Item(TokenType.LESS),
            new Item(TokenType.IDENTIFIER)
            ).map(list -> (Token) list.get(1)),  
            new Success<>(null)                   
    );
    @SuppressWarnings("unchecked")
    Parser<Stmt> classDecl() {
        return new And(
            new Item(TokenType.CLASS),
            new Item(TokenType.IDENTIFIER),
            superClassOpt,
            new Item(TokenType.LEFT_BRACE),
            new Many<>(new Parser.Lazy<>(() -> function())), 
            new Item(TokenType.RIGHT_BRACE)
            ).map(list -> new ClassDecl(
            (Token) list.get(1),               // Name
            (Token) list.get(2),               // Superklasse oder null
            (List<Func>) list.get(4)           // Methoden
        ));
    }


    Parser<Func> function() {

    Parser<List<Token>> parametersOpt =
    new Or<>(
        parameters(),                          // richtiger Treffer
        new Success<>(List.<Token>of())        // optional: leere Parameterliste
    );

    return new And(
        new Item(TokenType.IDENTIFIER),
        new Item(TokenType.LEFT_PAREN),
        parametersOpt,
        new Item(TokenType.RIGHT_PAREN),
        block()
    ).map(list -> {
        Token name = (Token) list.get(0);
        @SuppressWarnings("unchecked")
        List<Token> params = (List<Token>) list.get(2);
        BlockStmt body = (BlockStmt) list.get(4);
        return new Func(name, params, body);
    });
}

Parser<List<Token>> parameters() {
    return new Item(TokenType.IDENTIFIER)                       
        .flatMap(first ->                                       
            new Many<>(                                        
                new And(new Item(TokenType.COMMA),           
                             new Item(TokenType.IDENTIFIER)) 
                .map(list -> (Token) list.get(1))           
            )
            .map(rest -> {                                     
                rest.add(0, first);                           
                return rest;                                  
            })
        );
}
   
    Parser<Stmt> block() {
        return new And(
        new Item(TokenType.LEFT_BRACE),
        new Many<>(declaration()),
        new Item(TokenType.RIGHT_BRACE)
        ).map(list -> {
            @SuppressWarnings("unchecked")
            List<Stmt> decls = (List<Stmt>) list.get(1);
            return new BlockStmt(decls);
        });
    }


    Parser<Stmt> funDecl() {
        return new And(
        new Item(TokenType.FUN),
        function()
        ).map(res -> new FunDecl((Func) res.get(1)));
    }


    Parser<Stmt> varDecl() {
        return new And(
        new Item(TokenType.VAR),                  // "var"
        new Item(TokenType.IDENTIFIER),          // Name
        new Maybe<>(                              // optional: "=" expression
            new And(
                new Item(TokenType.EQUAL),
                parseExpression()
            ).map(list -> (Expr) list.get(1))
        ),
        new Item(TokenType.SEMICOLON)            // ";"
        ).map(list -> {
        Token name = (Token) list.get(1);
        @SuppressWarnings("unchecked")
        Expr initializer = (Expr) list.get(2);
        return new VarDecl(name, initializer);
     });
    }

    Parser<Stmt> exprStmt() {
        return new And(
        parseExpression(),
        new Item(TokenType.SEMICOLON)
        ).map(list -> new ExprStmt((Expr) list.get(0)));
    }

Parser<Stmt> printStmt() {
    return new And(
        new Item(TokenType.PRINT),
        parseExpression(),
        new Item(TokenType.SEMICOLON)
    ).map(list -> new PrintStmt((Expr) list.get(1)));
}

Parser<Stmt> returnStmt() {
    return new And(
        new Item(TokenType.RETURN),
        new Maybe<>(parseExpression()),
        new Item(TokenType.SEMICOLON)
    ).map(list -> new ReturnStmt((Expr) list.get(1)));
}

Parser<Stmt> ifStmt() {
    return new And(
        new Item(TokenType.IF),
        new Item(TokenType.LEFT_PAREN),
        parseExpression(),
        new Item(TokenType.RIGHT_PAREN),
        statement(),
        new Maybe<>(new And(new Item(TokenType.ELSE), statement()).map(l -> (Stmt) l.get(1)))
    ).map(list -> new IfStmt(
        (Expr) list.get(2),
        (Stmt) list.get(4),
        Optional.ofNullable((Stmt) list.get(5))
    ));
}


Parser<Stmt> whileStmt() {
    return new And(
        new Item(TokenType.WHILE),
        new Item(TokenType.LEFT_PAREN),
        parseExpression(),
        new Item(TokenType.RIGHT_PAREN),
        statement()
    ).map(list -> new WhileStmt(
        (Expr) list.get(2),
        (Stmt) list.get(4)
    ));
}

Parser<Stmt> forStmt() {
    return new And(
        new Item(TokenType.FOR),
        new Item(TokenType.LEFT_PAREN),
        new Or<>(varDecl(), exprStmt() ,new Item(TokenType.SEMICOLON).map(tok->null)),
        new Maybe<>(parseExpression()),
        new Item(TokenType.SEMICOLON),
        new Maybe<>(parseExpression()),
        new Item(TokenType.RIGHT_PAREN),
        statement()
    ).map(list -> new ForStmt(
        (Stmt) list.get(2),
         (Expr) list.get(3),
        (Expr) list.get(5),
        (Stmt) list.get(7)
    ));
}

Parser<Stmt> statement() {
    return new Parser.Lazy<>(() -> 
        new Or<>(
            //exprStmt(),
           // forStmt(),
            //ifStmt(),
            printStmt(),
           // returnStmt(),
            //whileStmt(),
            block()
        )
    );
}



    Parser<Expr> parseExpression() {
      return assignment();
    }



/*Parser<Expr> assignment() {
    return new Parser.Lazy<>(() -> {
        // Parser für die linke Seite (LHS)
        Parser<Expr> lhsParser = new Parser.Lazy<>(() ->
            primary().flatMap(callee ->
                new Many<>(new Or<>(
                    // Funktionsaufruf
                    new And(
                        new Item(TokenType.LEFT_PAREN),
                        new Maybe<>(arguments()),
                        new Item(TokenType.RIGHT_PAREN)
                    ).map(list -> {
                        @SuppressWarnings("unchecked")
                        List<Expr> args = list.get(1) == null ? List.of() : (List<Expr>) list.get(1);
                        return new Expr.CallExpr(callee, args);
                    }),

                    // Property-Zugriff
                    new And(
                        new Item(TokenType.DOT),
                        new Item(TokenType.IDENTIFIER)
                    ).map(list ->
                        
                        new Expr.GetExpr(callee, (Token) list.get(1)))
                )).map(suffixes -> {
                    Expr expr = callee;
                    for (Expr s : suffixes) {
                        if (s instanceof Expr.CallExpr call) {
                            expr = new Expr.CallExpr(expr, call.arguments());
                        } else if (s instanceof Expr.GetExpr get) {
                            expr = new Expr.GetExpr(expr, get.name());
                        }
                    }
                    return expr;
                })
            )
        );

        // Parser für Assignment
        Parser<Expr> assignParser = new And(
            lhsParser,
            new Item(TokenType.EQUAL),
            new Parser.Lazy<>(() -> assignment())
        ).map(list -> {
            Expr lhs = (Expr) list.get(0);
            Token name;

            if (lhs instanceof Expr.Variable var) {
                // Variable direkt → Name nehmen
                name = var.name();
                return new Expr.AssignExpr( name, (Expr) list.get(2)); // Null, weil Variable
            } else if (lhs instanceof Expr.GetExpr get) {
                // Memberzugriff → SetExpr mit Objekt
                return new Expr.SetExpr(get.object(), get.name(), (Expr) list.get(2));
            } else {
                // Ungültiges LHS (z. B. CallExpr allein) → kann hier als Fehler behandelt werden
                // Da wir keine Exceptions wollen, einfach links zurückgeben
                return lhs; 
            }
        });

        // Entweder Assignment oder fallback auf logicOr
        return new Or<>(assignParser, logicOr());
    });
}
*/



/*Helper Methode For Assignement 
 */

private Parser<Expr> lhs() {
    return primary().flatMap(callee ->
        new Many<>(
            new Or<>(
                parseCall(callee),
                parseGet(callee)
            )
        ).map(suffixes -> {
            Expr expr = callee;
            for (Expr s : suffixes) {
                if (s instanceof Expr.CallExpr call) expr = new Expr.CallExpr(expr, call.arguments());
                else if (s instanceof Expr.GetExpr get) expr = new Expr.GetExpr(expr, get.name());
            }
            return expr;
        })
    );
}
private Parser<Expr> parseCall(Expr callee) {
    return new And(
        new Item(TokenType.LEFT_PAREN),
        new Maybe<>(arguments()),
        new Item(TokenType.RIGHT_PAREN)
    ).map(list -> {
        @SuppressWarnings("unchecked")
        List<Expr> args = list.get(1) == null ? List.of() : (List<Expr>) list.get(1);
        return new Expr.CallExpr(callee, args);
    });
}

private Parser<Expr> parseGet(Expr callee) {
    return new And(
        new Item(TokenType.DOT),
        new Item(TokenType.IDENTIFIER)
    ).map(list -> new Expr.GetExpr(callee, (Token) list.get(1)));
}// Hilfsklasse für Parser
// ==================== Parser für Assignment ====================
Parser<Expr> assignment() {
    return new Parser.Lazy<>(() -> {
        Parser<Expr> assignParser = new And(
            lhs(),
            new Item(TokenType.EQUAL),
            new Parser.Lazy<>(this::assignment)
        ).map(list -> {
            Expr lhs = (Expr) list.get(0);
            Expr value = (Expr) list.get(2);

            // Variable direkt
            if (lhs instanceof Expr.Variable v) {
                return new Expr.Assign(Optional.empty(), v.name(), value);
            }

            // Äußerste GetExpr-Stufe → target links, name = äußerste Property
            if (lhs instanceof Expr.GetExpr g) {
                Expr target = g.object();
                Token name = g.name();
                return new Expr.Assign(Optional.of(target), name, value);
            }

            // Fallback, komplexer Ausdruck, z.B. Funktionsaufruf allein
            return lhs;
        });

        // Entweder Assignment oder fallback auf logische Ausdrücke
        return new Or<>(assignParser, logicOr());
    });
}



    //logic_or       → logic_and ( "or" logic_and )* ; 
   Parser<Expr> logicOr() {
    Parser<Pair<Token, Expr>> rightParser =
        new And(new Item(TokenType.OR), logicAnd())
        .map(obj -> {
            List<?> list = (List<?>) obj;
            return new Pair<>((Token) list.get(0), (Expr) list.get(1));
        });

    return logicAnd().flatMap(left ->
        new Many<>(rightParser).map(list -> {
            Expr expr = left;
            for (Pair<Token, Expr> part : list) {
                expr = new Expr.Binary(expr, part.first(), part.second());
            }
            return expr;
        })
    );
}

    //logic_and      → equality ( "and" equality )* ;

  Parser<Expr> logicAnd() {

    Parser<Pair<Token, Expr>> rightParser =
        new And(new Item(TokenType.AND), equality())
            .map(obj -> {
            List<?> list = (List<?>) obj;
            return new Pair<>(
                (Token) list.get(0),
                (Expr) list.get(1)
            );
        });
    return equality().flatMap(left ->
        new Many<>(rightParser).map(rest -> {
            Expr expr = left;
            for (Pair<Token, Expr> p : rest) {
                expr = new Expr.Binary(expr, p.first(), p.second());
            }
            return expr;
        })
    );
}



    
    //Parser<Expr> expression() { return equality(); }
  
    //comparison ( ( "!=" | "==" ) comparison )*
    Parser<Expr> equality() {
        Parser<Token> operator = new Or<>(
            new Item(TokenType.BANG_EQUAL),
            new Item(TokenType.EQUAL_EQUAL)
        );
        Parser<Pair<Token, Expr>> opAndRight = new And(operator, comparison())
            .map(obj -> {
                List<?> list = (List<?>) obj;
                return new Pair<>((Token) list.get(0), (Expr) list.get(1));
            });

        return comparison().flatMap(left ->
            new Many<>(opAndRight).map(pairs -> {
                Expr expr = left;
                for (Pair<Token, Expr> p : pairs)
                    expr = new Expr.Binary(expr, p.first(), p.second());
                return expr;
            })
        );
    }
    
    Parser<Expr> comparison() {
        Parser<Token> op = new Or<>(
            new Item(TokenType.LESS),
            new Item(TokenType.LESS_EQUAL),
            new Item(TokenType.GREATER),
            new Item(TokenType.GREATER_EQUAL)
        );

        Parser<Pair<Token, Expr>> opAndRight = new And(op, term())
            .map(list -> new Pair<>((Token) list.get(0), (Expr) list.get(1)));

        return term().flatMap(left ->
            new Many<>(opAndRight).map(pairs -> {
                Expr expr = left;
                for (Pair<Token, Expr> p : pairs)
                    expr = new Expr.Binary(expr, p.first(), p.second());
                return expr;
            })
        );
    }
    
    Parser<Expr> term() {
        Parser<Token> op = new Or<>(
            new Item(TokenType.MINUS),
            new Item(TokenType.PLUS)
        );
        Parser<Pair<Token, Expr>> opRight = new And(op, factor())
            .map(list -> new Pair<>((Token) list.get(0), (Expr) list.get(1)));

        return factor().flatMap(left ->
            new Many<>(opRight).map(pairs -> {
                Expr expr = left;
                for (Pair<Token, Expr> p : pairs)
                    expr = new Expr.Binary(expr, p.first(), p.second());
                return expr;
            })
        );
    }
    //factor ( ( "-" | "+" ) factor )* ;
    Parser<Expr> factor() {
        Parser<Token> op = new Or<>(
            new Item(TokenType.SLASH),
            new Item(TokenType.STAR)
        );
        Parser<Pair<Token, Expr>> opRight = new And(op, unary())
            .map(list -> new Pair<>((Token) list.get(0), (Expr) list.get(1)));

        return unary().flatMap(left ->
            new Many<>(opRight).map(pairs -> {
                Expr expr = left;
                for (Pair<Token, Expr> p : pairs)
                    expr = new Expr.Binary(expr, p.first(), p.second());
                return expr;
            })
        );
    }


   /*  unary          → ( "!" | "-" ) unary | call ;
    call           → primary ( "(" arguments? ")" | "." IDENTIFIER )* ;*/
    Parser<Expr> unary() {
        Parser<Token> op = new Or<>(
            new Item(TokenType.BANG),
            new Item(TokenType.MINUS)
        );

        Parser<Expr> recursive = new Parser.Lazy<>(() -> unary());

        Parser<Pair<Token, Expr>> left = new And(op, recursive)
            .map(list -> {
                List<?> l = (List<?>) list;
                return new Pair<>((Token) l.get(0), (Expr) l.get(1));
            });

            return new Or<>(
                left.map(p -> new Expr.Unary(p.first(), p.second())),
                call()
            );
        }

    //call   → primary ( "(" arguments? ")" | "." IDENTIFIER )* ;   
    Parser<Expr> call() {
    return primary().flatMap(callee -> 
        new Many<>(
            new Or<>(
                // Funktionsaufruf
                new And(
                    new Item(TokenType.LEFT_PAREN),
                    // Explizite Behandlung von optionalen Argumenten
                    new Or<>(
                        arguments().map(args -> args),
                        new Success<>(List.<Expr>of()) // Leere Liste als Fallback
                    ),
                    new Item(TokenType.RIGHT_PAREN)
                ).map(list -> {
                    @SuppressWarnings("unchecked")
                    List<Expr> args = (List<Expr>) list.get(1);
                    return new Expr.CallExpr(callee, args);
                }),
                
                // Property-Zugriff
                new And(
                    new Item(TokenType.DOT),
                    new Item(TokenType.IDENTIFIER)
                ).map(list -> new Expr.GetExpr(callee, (Token) list.get(1)))
            )
        ).map(suffixes -> {
            Expr expr = callee;
            for (Expr s : suffixes) {
                if (s instanceof Expr.CallExpr call) {
                    expr = new Expr.CallExpr(expr, call.arguments());
                } else if (s instanceof Expr.GetExpr get) {
                    expr = new Expr.GetExpr(expr, get.name());
                }
            }
            return expr;
        })
    );
}
Parser<Expr> primary() {

    Parser<Expr> literals = new Or<>(
        new Item(TokenType.TRUE).map(t -> (Expr) new Expr.Literal(true)),
        new Item(TokenType.FALSE).map(t -> (Expr) new Expr.Literal(false)),
        new Item(TokenType.NIL).map(t -> (Expr) new Expr.Literal(null))
    );

    Parser<Expr> thisExpr = new Item(TokenType.THIS)
        .map(t -> (Expr) new Expr.ThisExpr(t));

    Parser<Expr> number = new Item(TokenType.NUMBER)
        .map(t -> (Expr) new Expr.Literal(t.value()));

    Parser<Expr> string = new Item(TokenType.STRING)
        .map(t -> (Expr) new Expr.Literal(t.value()));

    Parser<Expr> ident = new Item(TokenType.IDENTIFIER)
        .map(t -> (Expr) new Expr.Variable(t));

    Parser<Expr> superExpr = new And(
            new Item(TokenType.SUPER),
            new And(new Item(TokenType.DOT), new Item(TokenType.IDENTIFIER))
        ).map(list -> {
        Token nameToken  = (Token) ((List<?>) list.get(1)).get(1); // IDENTIFIER aus innerem And
        return (Expr) new Expr.SuperExpr( nameToken);
    });

    Parser<Expr> grouping = new Item(TokenType.LEFT_PAREN)
        .flatMap(lp -> parseExpression()
            .flatMap(exp -> new Item(TokenType.RIGHT_PAREN)
                .map(rp -> (Expr) new Expr.Grouping(exp))
            )
        );

    // 🔹 alles flach in einem einzigen Or
    return new Or<>(
        literals,
        thisExpr,
        number,
        string,
        ident,
        superExpr,
        grouping
    );
}



    //arguments      → expression ( "," expression )* ;
    Parser<List<Expr>> arguments() {
        return parseExpression().flatMap(first ->
            new Many<>(
                new And(
                new Item(TokenType.COMMA),
                parseExpression()
                ).map(list -> (Expr) list.get(1))
            ).map(rest -> {
                List<Expr> all = new ArrayList<>();
                all.add(first);
                all.addAll(rest);
                return all;
        })
    );
}


}
 record TokenNode(Token token) implements AstNode {
    @Override public List<AstNode> children() { return List.of(); }
    @Override public String label() { return token.lexem(); }
}

record Func(Token name, List<Token> parameters, BlockStmt body) implements AstNode {

    @Override
    public List<AstNode> children() {
        // Name-Node
        AstNode nameNode = new AstNode() {
            @Override
            public List<AstNode> children() { return List.of(); }
            @Override
            public String label() { return "name:"+ name.lexem(); }
        };

        // Parameters-Node
        AstNode paramsNode = new AstNode() {
            @Override
            public List<AstNode> children() {
                return parameters.stream()
                 .map(p -> (AstNode) new TokenNode(p))
                 .toList();

            }
            @Override
            public String label() { return "parameters"; }
        };

        // Body-Node
        AstNode bodyNode = new AstNode() {
            @Override
            public List<AstNode> children() { return List.of(body); }
            @Override
            public String label() { return "body"; }
        };

        return List.of(nameNode, paramsNode, bodyNode);
    }

    @Override
    public String label() {
        return "Func";
    }
}



/* ================= Stmt =============== */

sealed interface Stmt extends AstNode
    permits ExprStmt, PrintStmt, VarDecl, BlockStmt,
            IfStmt, WhileStmt, ForStmt, ReturnStmt,
            FunDecl, ClassDecl {}


record ExprStmt(Expr expr)implements Stmt {
    @Override public List<AstNode> children() { return List.of(expr); }
    @Override public String label() { return "expr-stmt"; }
}

record PrintStmt(Expr expr) implements Stmt {
    @Override
    public List<AstNode> children() { 
        return List.of(expr); 
    }

    @Override
    public String label() { 
        return "print-stmt"; 
    }
}

record VarDecl(Token name, Expr initializer) implements Stmt {
    @Override
    public List<AstNode> children() {
        return initializer == null ? List.of() : List.of(initializer);
    }
    @Override public String label() { return "var " + name.lexem(); }
}


record BlockStmt(List<Stmt> statements) implements Stmt {
    @Override public List<AstNode> children() { return new ArrayList<>(statements); }
    @Override public String label() { return "block"; }
}


record IfStmt
    (
    Expr condition,
     Stmt thenBranch, 
     Optional<Stmt> elseBranch
    ) implements Stmt {

    @Override public List<AstNode> children() {
        List<AstNode> list = new ArrayList<>();
        list.add(condition);
        list.add(thenBranch);
        elseBranch.ifPresent(list::add);
        return list;
    }

    @Override public String label() { return "if"; }
}



record WhileStmt(Expr condition, Stmt body) implements Stmt {
    @Override
    public List<AstNode> children() { 
        return List.of(condition, body); 
    }

    @Override
    public String label() { 
        return "while-stmt"; 
    }
}
record ForStmt(Stmt initializer, Expr condition, Expr increment, Stmt body) implements Stmt {
    @Override
    public List<AstNode> children() {
        List<AstNode> list = new ArrayList<>();
        if (initializer != null) list.add(initializer);
        if (condition != null) list.add(condition);
        if (increment != null) list.add(increment);
        list.add(body);
        return list;
    }
    @Override public String label() { return "for"; }
}

record ReturnStmt(Expr expr) implements Stmt {
    @Override public List<AstNode> children() {
        return expr == null ? List.of() : List.of(expr);
    }
    @Override public String label() { return "return"; }
}

record FunDecl(Func function) implements Stmt {
    @Override public List<AstNode> children() { return List.of(function); }
    @Override public String label() { return "funDecl "; }
}

record ClassDecl(
    Token name,
    Token superClass,   // optional, kann null sein
    List<Func> methods
) implements Stmt {

    @Override
    public List<AstNode> children() {
        List<AstNode> kids = new ArrayList<>();

        // Name-Node
        AstNode nameNode = new AstNode() {
            @Override
            public List<AstNode> children() { return List.of(); }
            @Override
            public String label() { return "name: " + name.lexem(); }
        };
        kids.add(nameNode);

        // SuperClass-Node, falls vorhanden
        if (superClass != null) {
            AstNode superClassNode = new AstNode() {
                @Override
                public List<AstNode> children() { return List.of(); }
                @Override
                public String label() { return "superClass: " + superClass.lexem(); }
            };
            kids.add(superClassNode);
        }

        
        kids.addAll(methods); 

        return kids;
    }

    @Override
    public String label() {
        return "ClassDecl";
    }
}


record Program(List<Stmt> declarations) implements AstNode {
    @Override
    public List<AstNode> children() {
        return new ArrayList<>(declarations);
    }
    @Override
    public String label() { return "Program"; }
}


/* ================= AstDot ================= */
class AstDot {
    public static String toDot(AstNode root) {
        StringBuilder sb = new StringBuilder("digraph AST {\n");
        visit(root, sb, new int[]{0});
        sb.append("}\n");
        return sb.toString();
    }

    private static int visit(AstNode node, StringBuilder sb, int[] idCounter) {
        int id = idCounter[0]++;
        sb.append("  node").append(id)
          .append(" [label=\"").append(node.label().replace("\"", "\\\"")).append("\"];\n");
        for (AstNode child : node.children()) {
            int childId = visit(child, sb, idCounter);
            sb.append("  node").append(id).append(" -> node").append(childId).append(";\n");
        }
        return id;
    }
}
