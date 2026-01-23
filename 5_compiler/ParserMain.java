import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
}

/* ================= AstNode ================= */
interface AstNode {
    List<AstNode> children();
    String label();
}
sealed interface Expr extends AstNode
    permits Expr.Assign, Expr.Binary, Expr.Call, Expr.Get, Expr.Set, Expr.Grouping,
            Expr.Literal, Expr.Logical, Expr.Super, Expr.This, Expr.Unary, Expr.Variable {

   public record Assign(Token name, Expr value) implements Expr {
        @Override public List<AstNode> children() { return List.of(value); }
        @Override public String label() { return "Assign"; }
    }

   public record Binary(Expr left, Token operator, Expr right) implements Expr {
        @Override public List<AstNode> children() { return List.of(left,right); }
        @Override public String label() { return "Binary:"+operator.lexem(); }
    }

    record Call(Expr callee, Token paren, List<Expr> arguments) implements Expr {
        @Override public List<AstNode> children() {
            List<AstNode> children = new ArrayList<>();
            children.add(callee); children.addAll(arguments);
            return children;
        }
        @Override public String label() { return "Call"; }
    }

    record Get(Expr object, Token name) implements Expr {
        @Override public List<AstNode> children() { return List.of(object); }
        @Override public String label() { return "Get(."+name.lexem()+")"; }
    }
    record Set(Expr object, Token name, Expr value) implements Expr {
        @Override
        public List<AstNode> children() {
            return List.of(object, value); // Beide Kinder wichtig für Traversal
        }

        @Override
        public String label() {
            return "Set(." + name.lexem() + ")";
        }
    }

    record Grouping(Expr expression) implements Expr {
        @Override public List<AstNode> children() { return List.of(expression); }
        @Override public String label() { return "()"; }
    }

    record Literal(Object value) implements Expr {
        @Override public List<AstNode> children() { return List.of(); }
        @Override public String label() { 
             if (value instanceof Token t) return t.lexem();
            if (value == null) return "nil";
            return value.toString();
        }
    }

    record Logical(Expr left, Token operator, Expr right) implements Expr {
        @Override public List<AstNode> children() { return List.of(left,right); }
        @Override public String label() { return "Logical:"+operator.lexem(); }
    }

    record Super(Token keyword, Token method) implements Expr {
        @Override public List<AstNode> children() { return List.of(); }
        @Override public String label() { return "super."+method.lexem(); }
    }

    record This(Token keyword) implements Expr {
        @Override public List<AstNode> children() { return List.of(); }
        @Override public String label() { return "this"; }
    }

    record Unary(Token operator, Expr right) implements Expr {
        @Override public List<AstNode> children() { return List.of(right); }
        @Override public String label() { return "Unary:"+operator.lexem(); }
    }

    record Variable(Token name) implements Expr {
        @Override public List<AstNode> children() { return List.of(); }
        @Override public String label() { return name.lexem(); }
    }
}

sealed interface Stmt extends AstNode
    permits Stmt.Expression, Stmt.Print, Stmt.Var, Stmt.Block, Stmt.If, Stmt.While, Stmt.Return, Stmt.Function, Stmt.Class {

    record Expression(Expr expr) implements Stmt {
        @Override public List<AstNode> children() { return List.of(expr); }
        @Override public String label() { return "expr-stmt"; }
    }

    record Print(Expr expr) implements Stmt {
        @Override public List<AstNode> children() { return List.of(expr); }
        @Override public String label() { return "print-stmt"; }
    }

    record Var(Token name, Expr initializer) implements Stmt {
        @Override public List<AstNode> children() {
            if (initializer != null) return List.of(new TokenNode(name), initializer);
            return List.of(new TokenNode(name));
        }
        @Override public String label() { return "Var(" + name.lexem() + ")"; }
    }

    record Block(List<Stmt> statements) implements Stmt {
        @Override public List<AstNode> children() { return new ArrayList<>(statements); }
        @Override public String label() { return "block"; }
    }

    record If(Expr condition, Stmt thenBranch, Optional<Stmt> elseBranch) implements Stmt {
        @Override public List<AstNode> children() {
            List<AstNode> list = new ArrayList<>();
            list.add(condition); list.add(thenBranch);
            elseBranch.ifPresent(list::add);
            return list;
        }
        @Override public String label() { return "if-stmt"; }
    }

    record While(Expr condition, Stmt body) implements Stmt {
        @Override public List<AstNode> children() { return List.of(condition, body); }
        @Override public String label() { return "while-stmt"; }
    }

    record Return(Token keyword, Expr value) implements Stmt {
        @Override public List<AstNode> children() {
            return value == null ? List.of() : List.of(value);
        }
        @Override public String label() { return "return"; }
    }

    record Function(Token name, List<Token> params, List<Stmt> body) implements Stmt {
        @Override public List<AstNode> children() { return new ArrayList<>(body); }
        @Override public String label() { return "funDecl " + name.lexem(); }
    }

    record Class(Token name, Expr.Variable superClass, List<Stmt.Function> methods) implements Stmt {
        @Override public List<AstNode> children() {
            List<AstNode> kids = new ArrayList<>();
            kids.add(new TokenNode(name));
            if (superClass != null) kids.add(superClass);
            kids.addAll(methods);
            return kids;
        }
        @Override public String label() { return "ClassDecl"; }
    }

    // Optional: Program-Wrapper
    record Program(List<Stmt> declarations) implements AstNode {
        @Override public List<AstNode> children() { return new ArrayList<>(declarations); }
        @Override public String label() { return "Program"; }
    }
}


    record TokenNode(Token token) implements AstNode {
        @Override public List<AstNode> children() { return List.of(); }
        @Override public String label() { return token.lexem(); }
    }

    /* ================= ListAstNode ================= */
    record ListAstNode(List<AstNode> nodes) implements AstNode {
        @Override
        public List<AstNode> children() {
            return new ArrayList<>(nodes);
        }
    
        @Override
        public String label() {
            return "List[" + nodes.size() + "]";
        }
}

/* ================= Parser Interfaces ================= */
sealed interface Parser<T extends AstNode> 
    permits Success, Item, Or, And, Many, Parser.Lazy, MapParser, FlatMap, Maybe, Drop {

    Result<T> parse(List<Token> tokens);

    default <R extends AstNode> Parser<R> map(Function<T, R> f) {
        return new Lazy<>(() -> new MapParser<>(this, f));
    }

    default <R extends AstNode> Parser<R> flatMap(Function<T, Parser<R>> f) {
        return new Lazy<>(() -> new FlatMap<>(this, f));
    }

    final class Lazy<T extends AstNode> implements Parser<T> {
        private final Supplier<Parser<T>> supplier;
        private Parser<T> cached = null;
        
        public Lazy(Supplier<Parser<T>> supplier) { 
            this.supplier = supplier; 
        }
        
        @Override 
        public Result<T> parse(List<Token> tokens) {
            if (cached == null) cached = supplier.get();
            return cached.parse(tokens);
        }
    }
}

    /* ================= Result ================= */
    record Result<T extends AstNode>(boolean success, Optional<T> recognized, List<Token> rest) {

        static <T extends AstNode> Result<T> of(T value, List<Token> rest) {
            return new Result<>(true, Optional.ofNullable(value), rest);
        }
    
        static <T extends AstNode> Result<T> fail(List<Token> rest) {
            return new Result<>(false, Optional.empty(), rest);
        }

        boolean hasFailed() { return !success; }
        boolean hasNotFailed() { return success; }
    }

    /* ================= Success ================= */
    record Success<T extends AstNode>() implements Parser<T> {
        @Override
        public Result<T> parse(List<Token> lst) {
            return Result.of(null , lst); 
        }
    }

    /* ================= Map & FlatMap ================= */
    record MapParser<T extends AstNode, R extends AstNode>
    (
       Parser<T> parser, 
       Function<T, R> f
    ) implements Parser<R> 
    {
        @Override
        public Result<R> parse(List<Token> tokens) {
            Result<T> res = parser.parse(tokens);
            if (res.hasFailed()) return Result.fail(tokens);
        
            R mappedNode = f.apply(res.recognized().orElse(null));
            return Result.of(mappedNode, res.rest());
        }
    }

    record FlatMap<T extends AstNode, R extends AstNode>
    (
        Parser<T> parser, 
        Function<T, Parser<R>> f
    ) implements Parser<R>
    {
        @Override
        public Result<R> parse(List<Token> tokens) {
            Result<T> res = parser.parse(tokens);
            if (res.hasFailed()) return Result.fail(tokens);
        
            return f.apply(res.recognized().orElse(null)).parse(res.rest());
        }
    }

    /* ================= Item, Or, And, Many ================= */
    record Item(TokenType itm) implements Parser<TokenNode> {
      @Override
        public Result<TokenNode> parse(List<Token> tokens) {
            if (tokens.isEmpty() || tokens.get(0).type() != itm)
            return Result.fail(tokens); // Fail
            return Result.of(
            new TokenNode(tokens.get(0)),
            Collections.unmodifiableList(tokens.subList(1, tokens.size()))
          );
        }
    }
   
    record Or<T extends AstNode>(Parser<T>... parsers) implements Parser<T> {
        public @SafeVarargs Or { }
    
        @Override
        public Result<T> parse(List<Token> lst) {
            return Arrays.stream(parsers)
            .map(p -> p.parse(lst))
            .filter(Result::hasNotFailed)
            .findFirst()
            .orElseGet(() -> Result.fail(lst));
        }
    }
   

    record And(Parser<? extends AstNode>... parsers) implements Parser<ListAstNode> {
        public @SafeVarargs And { }
    
        @Override
        public Result<ListAstNode> parse(List<Token> tokens) {
            List<AstNode> results = new ArrayList<>();
            List<Token> remaining = tokens;

            for (Parser<? extends AstNode> p : parsers) {
                 Result<? extends AstNode> r = p.parse(remaining);
                if (r.hasFailed()) {
                    return Result.fail(tokens);
                }
            
                results.add(r.recognized().orElse(null));
                remaining = r.rest();
            }

            return Result.of(new ListAstNode(results), remaining);
        }
    }



    record Many<T extends AstNode>(Parser<T> parser) implements Parser<ListAstNode> {
        @Override
        public Result<ListAstNode> parse(List<Token> tokens) {
            List<AstNode> list = new ArrayList<>();
            List<Token> current = tokens;

            while (true) {
                var r = parser.parse(current);
                if (r.hasFailed()) break;
           
                list.add(r.recognized().orElse(null));
                current = r.rest();
            }

            return Result.of(new ListAstNode(list), current);
        }
    }

    record Maybe<T extends AstNode>(Parser<T> parser) implements Parser<T> {
        @Override
        public Result<T> parse(List<Token> lst) {
            return new Or<>(parser, new Success<>()).parse(lst);
        }
    }

   
    record Drop<T extends AstNode>(Parser<T> parser) implements Parser<T> {
        @Override
        public Result<T> parse(List<Token> lst) {
            Result<T> res = parser.parse(lst);
            if (res.hasNotFailed()) {
                return new Success<T>().parse(res.rest());
            }
            return res;
        }
        }

    record Pair<A, B>(A first, B second) implements AstNode {
        @Override public List<AstNode> children() { return List.of(); }
        @Override public String label() { return "Pair"; }
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

    static ParserMain fromSource(String source) {
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
    Parser<Stmt.Program> program() {
        return new Parser.Lazy<>(() ->
            new And(
            new Many<>(declaration()),
            new Item(TokenType.EOF)
            ).map(parts -> 
                new Stmt.Program(extractStatements((ListAstNode) parts.nodes().get(0)))
            )
        );
    }
    // Hilfsmethode
    private List<Stmt> extractStatements(ListAstNode node) {
        return node.nodes().stream()
        .map(n -> (Stmt) n)
        .toList();
    }
    Parser<Stmt> declaration() {
        return new Or<>(
            new Parser.Lazy<>(() -> classDecl()),
            new Parser. Lazy<>(() -> funDecl()),
            new Parser.Lazy<>(() -> varDecl()),
            new Parser.Lazy<>(() -> statement())
        );
    }
    Parser<TokenNode> superClassOpt =
        new Maybe<>(
            new And(
            listItem(TokenType.LESS,TokenType.IDENTIFIER)
        ).map(listAst -> (TokenNode) listAst.nodes().get(1))
    );

    Parser<Stmt> classDecl() {
        return new And(
            new Item(TokenType.CLASS),           
            new Item(TokenType.IDENTIFIER),      
            superClassOpt,                       
            new Item(TokenType.LEFT_BRACE),      
            new Many<>(new Parser.Lazy<>(() -> function())), 
            new Item(TokenType.RIGHT_BRACE)      
        ).map(list -> {
        List<AstNode> nodes = list.nodes();

        TokenNode className = (TokenNode) nodes.get(1);

        AstNode superNode = nodes.get(2);
        Token superClass = superNode instanceof TokenNode t? (Token) t.token() : null;
        
        ListAstNode methodsNode = (ListAstNode) nodes.get(4);
        List<Stmt.Function> methods = methodsNode.nodes().stream()
                                    .map(n -> (Stmt.Function) n)
                                    .toList();

        return new Stmt.Class(className.token(), superClass == null ? null : new Expr.Variable(superClass), methods);
    });
    }
    
    Parser<Stmt.Function> function() {

        Parser<ListAstNode> parametersOpt = new Maybe<>(parameters());

        return new And(
            new Item(TokenType.IDENTIFIER),   // 0: Funktionsname
            new Item(TokenType.LEFT_PAREN),   // 1
            parametersOpt,                    // 2
            new Item(TokenType.RIGHT_PAREN),  // 3
            block()                           // 4
        ).map(list -> {
            TokenNode nameNode = (TokenNode) list.nodes().get(0);

            // Optional parameters: kann null sein
            ListAstNode par = (ListAstNode) list.nodes().get(2);
            List<Token> params = par != null
            ? par.nodes().stream().map(t -> ((TokenNode) t).token()).toList()
            : List.of();

            Stmt.Block bl= (Stmt.Block) list.nodes().get(4);

            return new Stmt.Function(nameNode.token(), params, bl.statements());
        });
    }

    Parser<ListAstNode> parameters() {
        return new And(
            new Item(TokenType.IDENTIFIER),
            new Many<>(new And(
            new Item(TokenType.COMMA),
            new Item(TokenType.IDENTIFIER)
            ).map(list -> (TokenNode) list.nodes().get(1))) // nur IDENTIFIER extrahieren
        ).map(list -> {
            TokenNode first = (TokenNode) list.nodes().get(0);
            ListAstNode rest = (ListAstNode) list.nodes().get(1);

            List<AstNode> all = Stream.concat(
            Stream.of(first),
            rest.nodes().stream()
            ).toList();

         return new ListAstNode(all);
     });
    }

    Parser<Stmt> block() {
         return new And(
        new Item(TokenType.LEFT_BRACE),   // 0
        new Many<>(declaration()),        // 1
        new Item(TokenType.RIGHT_BRACE)   // 2
     ).map(list -> {
        ListAstNode manyNode = (ListAstNode) list.nodes().get(1); 
        List<Stmt> decls = manyNode.nodes().stream()
                                     .map(n -> (Stmt) n)
                                     .toList();
        return new Stmt.Block(decls);
        });
    }

    Parser<Stmt> funDecl() {
        return new And(
            new Item(TokenType.FUN),
            function()
        ).map(res -> ((Stmt.Function)(res.nodes().get(1))));
    }

    Parser<Stmt> varDecl() {
        Parser<TokenNode> varKeyword = new Item(TokenType.VAR);
        Parser<TokenNode> name = new Item(TokenType.IDENTIFIER);
        Parser<Expr> initializer = new Maybe<>(
            new And(
            new Item(TokenType.EQUAL),
            parseExpression()
            ).map(list -> (Expr) list.nodes().get(1)) 
    )   ;
        Parser<TokenNode> semicolon = new Item(TokenType.SEMICOLON);

        return new And(varKeyword, name, initializer, semicolon)
            .map(list -> {
              TokenNode varName = (TokenNode) list.nodes().get(1);
              var maybeInit=list.nodes().get(2);
             
            Expr init = maybeInit ==null? null : (Expr) maybeInit;

            return new Stmt.Var(varName.token(), init);
        });
    }

     Parser<Stmt> exprStmt() {
        return new And(
        parseExpression(),
        new Item(TokenType.SEMICOLON)
        ).map(list ->new Stmt.Expression((Expr) list.nodes().get(0)));
    }

    Parser<Stmt> printStmt() {
        return new And(
            new Item(TokenType.PRINT),
            parseExpression(),
            new Item(TokenType.SEMICOLON)
        ).map(list -> new  Stmt.Print((Expr) list.nodes().get(1)));
    }

    Parser<Stmt> returnStmt() {
        return new And(
            new Item(TokenType.RETURN),
            new Maybe<>(parseExpression()),
            new Item(TokenType.SEMICOLON)
        ).map(list ->new Stmt.Return((Token)((TokenNode) list.nodes().get(0)).token(),(Expr) list.nodes().get(1)));
    }

    Parser<Stmt> ifStmt() {
        return new And(
            new Item(TokenType.IF),
            new Item(TokenType.LEFT_PAREN),
            parseExpression(),
            new Item(TokenType.RIGHT_PAREN),
            statement(),
            new Maybe<>(new And(new Item(TokenType.ELSE), statement()).map(l -> (Stmt) l.nodes().get(1)))
        ).map(list ->new Stmt.If(
        (Expr) list.nodes().get(2),
        (Stmt) list.nodes().get(4),
        Optional.ofNullable((Stmt) list.nodes().get(5)))
        );
    }


    Parser<Stmt> whileStmt() {
        return new And(
            new Item(TokenType.WHILE),
            new Item(TokenType.LEFT_PAREN),
            parseExpression(),
            new Item(TokenType.RIGHT_PAREN),
            statement()
        ).map(list -> new Stmt.While(
            (Expr) list.nodes().get(2),
            (Stmt) list.nodes().get(4)
        ));
    }
        @SuppressWarnings("unchecked")
      Parser<Stmt> forStmt() {
        return new And(
            new Item(TokenType.FOR),
            new Item(TokenType.LEFT_PAREN),
            new Or<>(varDecl(), exprStmt(), new Item(TokenType.SEMICOLON).map(tok -> null)),
            new Maybe<>(parseExpression()),
            new Item(TokenType.SEMICOLON),
            new Maybe<>(parseExpression()),
             new Item(TokenType.RIGHT_PAREN),
             statement()
            ).map(list -> {
            Stmt body = (Stmt) list.nodes().get(7);
            if (list.nodes().get(5) != null)
                 body = new Stmt.Block(Arrays.asList(body, new Stmt.Expression((Expr) list.nodes().get(5))));
            body = new Stmt.While(
                list.nodes().get(3) != null ? (Expr) list.nodes().get(3) : new Expr.Literal(true),
                body
            );
            if (list.nodes().get(2) != null)
                body = new Stmt.Block(Arrays.asList((Stmt) list.nodes().get(2), body));
            return body;
        });
    }


    Parser<Stmt> statement() {
        return new Parser.Lazy<>(() -> 
            new Or<>(
            exprStmt(),
            forStmt(),
            ifStmt(),
            printStmt(),
            returnStmt(),
            whileStmt(),
            block()
            )
        );
    }

    Parser<Expr> parseExpression() {
      return assignment();
    }


    // ==================== Parser für Assignment ====================
    Parser<Expr> assignment() {
        return new Parser.Lazy<Expr>(() ->
            new And(
                logicOr(),  // 0: parse einen "normalen" Ausdruck (kann Call/Get beinhalten)
                new Maybe<>( // optional: "=" assignment
                new And(
                    new Item(TokenType.EQUAL),
                    new Parser.Lazy<>(this::assignment)
                ).map(ep -> (Expr) ep.nodes().get(1))
            )
            ).map(list -> {
                Expr lhs = (Expr) list.nodes().get(0);
                Expr maybeRhs = (Expr) list.nodes().get(1); // null wenn kein '='

                if (maybeRhs == null) {
                
                    return lhs;
                }
                // Es gab ein '=' — jetzt muss lhs assignable sein
                if (lhs instanceof Expr.Variable v) {
                    return new Expr.Assign(v.name(), maybeRhs);
                }
                if (lhs instanceof Expr.Get g) {
                    Expr obj= g.object();
                    Token name = g.name();
                    return new Expr.Set(obj,name, maybeRhs);
                }
            
                return lhs; 
            })
        );
    }


    //logic_or       → logic_and ( "or" logic_and )* ; 
    Parser<Expr> logicOr() {
        return binaryLeftAssoc(logicAnd(), new Item(TokenType.OR), logicAnd(),false);
    }


    //logic_and      → equality ( "and" equality )* ;
    
    Parser<Expr> logicAnd() {
         return binaryLeftAssoc(equality(), new Item(TokenType.AND), equality(),false);
    }

 
    //comparison ( ( "!=" | "==" ) comparison )*
    Parser<Expr> equality() {
        return binaryLeftAssoc(
        comparison(),
        new Or<>(new Item(TokenType.BANG_EQUAL), new Item(TokenType.EQUAL_EQUAL)),
        comparison(),true
    );
    }


    private Parser<Expr> binaryLeftAssoc(
        Parser<Expr> operand,
        Parser<TokenNode> operator,
         Parser<Expr> nextOperand,
         boolean isBinary
    )
     {
        return operand.flatMap(first ->
            new Many<>(new And(operator, nextOperand)
            .map(l -> new Pair<>(
                    ((TokenNode) l.nodes().get(0)).token(),
                    (Expr) l.nodes().get(1)
            ))).map(pairs -> {
                Expr expr = first;
                for (AstNode node : pairs.nodes()) {
                    @SuppressWarnings("unchecked")
                    Pair<Token, Expr> p = (Pair<Token, Expr>) node;
                     expr = isBinary
                    ? new Expr.Binary(expr, p.first(), p.second())
                    : new Expr.Logical(expr, p.first(), p.second());
                }
                return expr;
            })
        );
    }

    Parser<Expr> comparison() {
        Parser<TokenNode> op = new Or<>(
            new Item(TokenType.LESS),
            new Item(TokenType.LESS_EQUAL),
            new Item(TokenType.GREATER),
            new Item(TokenType.GREATER_EQUAL)
        );
         return binaryLeftAssoc(term(), op, term(),true );

    }

    //factor ( ( "-" | "+" ) factor )* ;
    Parser<Expr> term() {

        Parser<TokenNode> op = new Or<>(
            new Item(TokenType.MINUS),
            new Item(TokenType.PLUS)
        );

          return binaryLeftAssoc(factor(), op, factor(),true);
    }
    
    Parser<Expr> factor() {
        Parser<TokenNode> op = new Or<>(
            new Item(TokenType.SLASH),
            new Item(TokenType.STAR)
        );
         return binaryLeftAssoc(unary(), op, unary(),true);
    }
       


   /*  unary          → ( "!" | "-" ) unary | call ;
    call           → primary ( "(" arguments? ")" | "." IDENTIFIER )* ;*/
     Parser<Expr> unary() {
        Parser<TokenNode> op = new Or<>(
            new Item(TokenType.BANG),
            new Item(TokenType.MINUS)
        );
        
         Parser<Expr> recursive = new Parser.Lazy<>(() -> unary());

    // Parser für einen Unary-Operator + Operand
        Parser<Expr> left = new And(op, recursive)
        .map(l-> {
                          // And liefert List<Object>
                Token operator = (Token) ((TokenNode) l.nodes().get(0)).token();
                 Expr right = (Expr) l.nodes().get(1);
                return new Expr.Unary(operator, right);
         });
            
        return new Or<>(left, call());
    }


  /* */  //call   → primary ( "(" arguments? ")" | "." IDENTIFIER )* ;   
  // call → primary ( "(" arguments? ")" | "." IDENTIFIER )* ;
 

    Parser<Expr> call() {
        return primary().flatMap(callee ->
            new Many<>(callSuffix()).map(suffixes -> {
                Expr expr = callee;
                for (AstNode n: suffixes.nodes()) {
                     Expr suffix= (Expr) n;
                 expr = applySuffix(expr, suffix);
                }
                return expr;
            })
        );
    }

    private Parser<Expr> callSuffix() {
        return new Or<>(
            functionCallSuffix(),
            propertyAccessSuffix()
        );
    }

    private Parser<Expr> functionCallSuffix() {
        return new And(
            new Item(TokenType.LEFT_PAREN),
            new Maybe<>(arguments()),
            new Item(TokenType.RIGHT_PAREN)
        ).map(list -> {
         ListAstNode argsNode = (ListAstNode) list.nodes().get(1);
        List<Expr> args = Optional.ofNullable(argsNode)
                        .map(node -> node.nodes().stream()
                        .map(exp -> (Expr) exp)
                        .toList())
                         .orElse(List.of());
         Token paren = ((TokenNode) list.nodes().get(2)).token(); // ')' Token
         return new Expr.Call(null, paren, args); // callee wird später gesetzt
         });
    }


    private Parser<Expr> propertyAccessSuffix() {
        return new And(
            new Item(TokenType.DOT),
            new Item(TokenType.IDENTIFIER)
        ).map(list -> {
            Token name = ((TokenNode) list.nodes().get(1)).token();
            return (Expr) new Expr.Get(null, name); // callee wird später gesetzt
        });
    }

    private Expr applySuffix(Expr callee, Expr suffix) {
        if (suffix instanceof Expr.Call callExpr) {
            return new Expr.Call(callee, callExpr.paren(), callExpr.arguments());
        } else if (suffix instanceof Expr.Get getExpr) {
            return new Expr.Get(callee, getExpr.name());
        }
        return callee;
    }

   Parser<Expr> primary() {
        Map<TokenType, Function<Token, Expr>> tokenMappings = Map.of(
         TokenType.TRUE, t -> new Expr.Literal(true),
         TokenType.FALSE, t -> new Expr.Literal(false),
         TokenType.NIL, t -> new Expr.Literal(null),
         TokenType.THIS, Expr.This::new,
         TokenType.NUMBER, t -> new Expr.Literal(t.value()),
         TokenType.STRING, t -> new Expr.Literal(t.value()),
         TokenType.IDENTIFIER, Expr.Variable::new 
        );

        @SuppressWarnings("unchecked")
        Parser<Expr> simpleTokens = new Or<>(
            tokenMappings.entrySet().stream()
            .map(e -> new Item(e.getKey()).map(t -> e.getValue().apply(t.token())))
            .toArray(Parser[]::new)
        );

        Parser<Expr> superExpr = new And(
            new Item(TokenType.SUPER),
            new Item(TokenType.DOT),
            new Item(TokenType.IDENTIFIER)
        ).map(list -> new Expr.Super(((TokenNode) list.nodes().get(0)).token(),((TokenNode) list.nodes().get(2)).token()));

        Parser<Expr> grouping = between(
            TokenType.LEFT_PAREN, parseExpression(), TokenType.RIGHT_PAREN
        ).map(Expr.Grouping::new);
  
        return new Or<>(simpleTokens,superExpr, grouping);
    }
      @SuppressWarnings("unchecked")
    private <T extends AstNode> Parser<T>between(TokenType left, Parser<T> middle, TokenType right) {
            return new And(new Item(left), middle, new Item(right))
            .map(list -> (T) list.nodes().get(1));
    }
    //arguments      → expression ( "," expression )* ;
   Parser<ListAstNode> arguments() {
    return parseExpression().flatMap(firstArg -> 
        new Many<>(
            new And(new Item(TokenType.COMMA), parseExpression())
                .map(list -> (Expr) list.nodes().get(1))
        ).map(restArgs -> {
            // Kombiniere firstArg mit restArgs
            List<AstNode> allArgs = new ArrayList<>();
            allArgs.add(firstArg);
            allArgs.addAll(restArgs.nodes());
            return new ListAstNode(allArgs);
        })
    );
}
        
    /*Hilfemethode */
     // Kurze DOT-Methoden:
    public String toDot() {
        Stmt.Program p = program().parse(tokens).recognized().orElseThrow();
        return AstDot.toDot(p);
    }
    
    public void printDot(){
        System.out.println(toDot());
    }
    @SuppressWarnings("unchecked")
    Parser<TokenNode>[] listItem(TokenType... types) {
        return Arrays.stream(types)
            .map(Item::new)
            .toArray(Parser[]::new);
    }
    //Methode gibt eine liste for stmt nach dem Parsing
    public static List<Stmt> parseProgram(String source) {
        ParserMain parser = ParserMain.fromSource(source);
        Result<Stmt.Program> result = parser.program().parse(parser.tokens);

        if (result.hasNotFailed()) { 
            Stmt.Program prog = result.recognized().get(); 
            return prog.declarations();
        } else {
            System.err.println("Parsing Error: " );
            return List.of(); // leere Liste statt null
    }
}



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

        // Prüfen, ob der Knoten „sichtbar“ sein soll
        //boolean isTransparent = node instanceof ListAstNode;
            sb.append("  node").append(id)
              .append(" [label=\"").append(node.label().replace("\"", "\\\"")).append("\"];\n");
        

        for (AstNode child : node.children()) {
            int childId = visit(child, sb, idCounter);
            
                sb.append("  node").append(id).append(" -> node").append(childId).append(";\n");
            
            
        }

        return id;
    }

    // Einfache Export-Methode
    public static void exportToFile(AstNode root, String filename) throws IOException {
        String dot = toDot(root);
        Files.writeString(Path.of(filename), dot);
        System.out.println("DOT saved to: " + filename);
    }
}

