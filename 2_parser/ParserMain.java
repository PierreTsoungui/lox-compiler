
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;


/* ================= AstNode ================= */
interface AstNode {
    List<AstNode> children();
    String label();
}
sealed interface Expr extends AstNode
    permits Expr.Assign, Expr.Binary, Expr.Call, Expr.Get, Expr.Set, Expr.Grouping,
            Expr.Literal, Expr.Logical, Expr.Super, Expr.This, Expr.Unary, Expr.Variable {

    record Assign(Token name, Expr value) implements Expr {
        @Override public List<AstNode> children() { return List.of(value); }
        @Override public String label() { return "Assign"; }
    }

    record Binary(Expr left, Token operator, Expr right) implements Expr {
        @Override public List<AstNode> children() { return List.of(left,right); }
        @Override public String label() { return "Binary:"+operator.lexem(); }
    }

    record Call(Expr callee, Token paren, List<Expr> arguments) implements Expr {
        @Override public List<AstNode> children() {
            List<AstNode> children = new ArrayList<>();
            children.add(callee);
            children.addAll(arguments);
            return List.copyOf(children);
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
            return List.of(object, value); 
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
        @Override public List<AstNode> children() { return List.copyOf(statements); }
        @Override public String label() { return "block"; }
    }

    record If(Expr condition, Stmt thenBranch, Optional<Stmt> elseBranch) implements Stmt {
        @Override public List<AstNode> children() {
            List<AstNode> list = new ArrayList<>();
            list.add(condition);
            list.add(thenBranch);
            elseBranch.ifPresent(list::add);
            return List.copyOf(list);
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
        @Override public List<AstNode> children() { return List.copyOf(body); }
        @Override public String label() { return "funDecl " + name.lexem(); }
    }

    record Class(Token name, Expr.Variable superClass, List<Stmt.Function> methods) implements Stmt {
        @Override public List<AstNode> children() {
            List<AstNode> kids = new ArrayList<>();
            kids.add(new TokenNode(name));
            if (superClass != null) kids.add(superClass);
            kids.addAll(methods);
            return List.copyOf(kids);
        }
        @Override public String label() { return "ClassDecl"; }
    }

    // Optional: Program-Wrapper
    record Program(List<Stmt> declarations) implements AstNode {
        @Override public List<AstNode> children() { return List.copyOf(declarations); }
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
            return List.copyOf(nodes);
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

                List<Token> next = r.rest();

                // Guard: if the underlying parser does not consume any tokens,
                // stop to avoid an infinite loop. Emit a warning so the developer
                // can fix the parser that accepts empty input.
                if (next == current || next.size() == current.size()) {
                    System.err.println("Warning: Many parser did not consume input; stopping to avoid infinite loop.");
                    list.add(r.recognized().orElse(null));
                    break;
                }

                list.add(r.recognized().orElse(null));
                current = next;
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

    // Helper factory methods to reduce repetition
    private Parser<TokenNode> item(TokenType t) { return new Item(t); }

    private <T extends AstNode> Parser<ListAstNode> many(Parser<T> p) { return new Many<>(p); }

    private <T extends AstNode> Parser<T> maybe(Parser<T> p) { return new Maybe<>(p); }

    private <T extends AstNode> Parser<T> lazy(Supplier<Parser<T>> s) { return new Parser.Lazy<>(s); }

    // Helpers to reduce casting of parser result lists
    private TokenNode tokenNode(ListAstNode list, int idx) { return (TokenNode) list.nodes().get(idx); }
    private Token token(ListAstNode list, int idx) { return tokenNode(list, idx).token(); }
    private Expr expr(ListAstNode list, int idx) { return (Expr) list.nodes().get(idx); }
    private Stmt stmt(ListAstNode list, int idx) { return (Stmt) list.nodes().get(idx); }
    private ListAstNode listAst(ListAstNode list, int idx) { return (ListAstNode) list.nodes().get(idx); }
    
    // Helper to create simple token-based Expr parsers
    private Parser<Expr> tokenParser(TokenType type, Function<Token, Expr> f) {
        return item(type).map(t -> f.apply(t.token()));
    }

    @SuppressWarnings("unchecked")
    private <T extends AstNode> T node(ListAstNode list, int idx) { return (T) list.nodes().get(idx); }

    // Extracted mapper helpers to shorten parser methods
    private Stmt.Program mapProgram(ListAstNode parts) {
        return new Stmt.Program(extractStatements(listAst(parts, 0)));
    }

    private Stmt mapClassDecl(ListAstNode list) {
        TokenNode className = tokenNode(list, 1);
        AstNode superNode = list.nodes().get(2);
        Token superClass = superNode instanceof TokenNode t ? t.token() : null;
        ListAstNode methodsNode = listAst(list, 4);
        List<Stmt.Function> methods = methodsNode.nodes().stream().map(n -> (Stmt.Function) n).toList();
        return new Stmt.Class(className.token(), superClass == null ? null : new Expr.Variable(superClass), methods);
    }

    private Stmt.Function mapFunction(ListAstNode list) {
        TokenNode nameNode = tokenNode(list, 0);
        ListAstNode par = listAst(list, 2);
        List<Token> params = par != null ? par.nodes().stream().map(t -> ((TokenNode) t).token()).toList() : List.of();
        Stmt.Block bl = (Stmt.Block) stmt(list, 4);
        return new Stmt.Function(nameNode.token(), params, bl.statements());
    }

    private ListAstNode mapParameters(ListAstNode list) {
        TokenNode first = tokenNode(list, 0);
        ListAstNode rest = listAst(list, 1);
        List<AstNode> all = Stream.concat(Stream.of(first), rest.nodes().stream()).toList();
        return new ListAstNode(all);
    }

    private Stmt.Block mapBlock(ListAstNode list) {
        ListAstNode manyNode = listAst(list, 1);
        List<Stmt> decls = manyNode.nodes().stream().map(n -> (Stmt) n).toList();
        return new Stmt.Block(decls);
    }

    private Stmt mapVarDecl(ListAstNode list) {
        TokenNode varName = tokenNode(list, 1);
        var maybeInit = list.nodes().get(2);
        Expr init = maybeInit == null ? null : (Expr) maybeInit;
        return new Stmt.Var(varName.token(), init);
    }

    private Stmt mapForStmt(ListAstNode list) {
        Stmt body = stmt(list, 7);
        if (expr(list, 5) != null)
            body = new Stmt.Block(Arrays.asList(body, new Stmt.Expression(expr(list, 5))));
        body = new Stmt.While(expr(list, 3) != null ? expr(list, 3) : new Expr.Literal(true), body);
        if (stmt(list, 2) != null)
            body = new Stmt.Block(Arrays.asList(stmt(list, 2), body));
        return body;
    }

    private Expr mapAssignment(ListAstNode list) {
        Expr lhs = expr(list, 0);
        Expr maybeRhs = expr(list, 1);
        if (maybeRhs == null) return lhs;
        if (lhs instanceof Expr.Variable v) return new Expr.Assign(v.name(), maybeRhs);
        if (lhs instanceof Expr.Get g) return new Expr.Set(g.object(), g.name(), maybeRhs);
        return lhs;
    }

    static ParserMain fromSource(String source) {
        List<Token> tokens = new Scanner(source).tokenize();

        return new ParserMain(tokens);
    }

    // program → declaration* EOF
    Parser<Stmt.Program> program() {
        return lazy(() ->
            new And(
                many(declaration()),
                item(TokenType.EOF)
            ).map(this::mapProgram)
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
            lazy(this::classDecl),
            lazy(this::funDecl),
            lazy(this::varDecl),
            lazy(this::statement)
        );
    }
    Parser<TokenNode> superClassOpt =
        maybe(
            new And(
                listItem(TokenType.LESS, TokenType.IDENTIFIER)
            ).map(listAst -> tokenNode(listAst, 1))
        );

    Parser<Stmt> classDecl() {
        return new And(
            item(TokenType.CLASS),           
            item(TokenType.IDENTIFIER),      
            superClassOpt,                       
            item(TokenType.LEFT_BRACE),      
            many(lazy(this::function)), 
            item(TokenType.RIGHT_BRACE)      
        ).map(this::mapClassDecl);
    }
    

    Parser<Stmt.Function> function() {

        Parser<ListAstNode> parametersOpt = maybe(parameters());

        return new And(
            item(TokenType.IDENTIFIER),   // 0: Funktionsname
            item(TokenType.LEFT_PAREN),   // 1
            parametersOpt,                    // 2
            item(TokenType.RIGHT_PAREN),  // 3
            block()                           // 4
        ).map(this::mapFunction);
    }

    Parser<ListAstNode> parameters() {
        return new And(
            item(TokenType.IDENTIFIER),
            many(new And(
                item(TokenType.COMMA),
                item(TokenType.IDENTIFIER)
            ).map(list -> tokenNode(list, 1))) // nur IDENTIFIER extrahieren
        ).map(this::mapParameters);
    }

    Parser<Stmt> block() {
        return new And(
            item(TokenType.LEFT_BRACE),   // 0
            many(declaration()),        // 1
            item(TokenType.RIGHT_BRACE)   // 2
        ).map(this::mapBlock);
    }

    Parser<Stmt> funDecl() {
        return new And(
            item(TokenType.FUN),
            function()
        ).map(res -> (Stmt.Function) stmt(res, 1));
    }

    Parser<Stmt> varDecl() {
        Parser<TokenNode> varKeyword = item(TokenType.VAR);
        Parser<TokenNode> name = item(TokenType.IDENTIFIER);
        Parser<Expr> initializer = maybe(
            new And(
                item(TokenType.EQUAL),
                parseExpression()
            ).map(list -> expr(list, 1))
        );
        Parser<TokenNode> semicolon = item(TokenType.SEMICOLON);

        return new And(varKeyword, name, initializer, semicolon)
            .map(this::mapVarDecl);
    }

     Parser<Stmt> exprStmt() {
        return new And(
            parseExpression(),
            item(TokenType.SEMICOLON)
        ).map(list -> new Stmt.Expression(expr(list, 0)));
    }

    Parser<Stmt> printStmt() {
        return new And(
            item(TokenType.PRINT),
            parseExpression(),
            item(TokenType.SEMICOLON)
        ).map(list -> new Stmt.Print(expr(list, 1)));
    }

    Parser<Stmt> returnStmt() {
        return new And(
            item(TokenType.RETURN),
            maybe(parseExpression()),
            item(TokenType.SEMICOLON)
            ).map(list -> new Stmt.Return(token(list, 0), expr(list, 1)));
    }

    Parser<Stmt> ifStmt() {
        return new And(
            item(TokenType.IF),
            item(TokenType.LEFT_PAREN),
            parseExpression(),
            item(TokenType.RIGHT_PAREN),
            statement(),
            maybe(new And(item(TokenType.ELSE), statement()).map(l -> stmt(l, 1)))
        ).map(list -> new Stmt.If(
            expr(list, 2),
            stmt(list, 4),
            Optional.ofNullable(stmt(list, 5)))
        );
    }


    Parser<Stmt> whileStmt() {
        return new And(
            item(TokenType.WHILE),
            item(TokenType.LEFT_PAREN),
            parseExpression(),
            item(TokenType.RIGHT_PAREN),
            statement()
        ).map(list -> new Stmt.While(
            expr(list, 2),
            stmt(list, 4)
        ));
    }
        @SuppressWarnings("unchecked")
    Parser<Stmt> forStmt() {
            return new And(
                item(TokenType.FOR),
                item(TokenType.LEFT_PAREN),
                new Or<>(varDecl(), exprStmt(), item(TokenType.SEMICOLON).map(tok -> null)),
                maybe(parseExpression()),
                item(TokenType.SEMICOLON),
                maybe(parseExpression()),
                item(TokenType.RIGHT_PAREN),
                statement()
            ).map(this::mapForStmt);
    }


    Parser<Stmt> statement() {
        return lazy(() ->
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
        return lazy(() ->
            new And(
                logicOr(),  // 0: parse einen "normalen" Ausdruck (kann Call/Get beinhalten)
                maybe( // optional: "=" assignment
                    new And(
                        item(TokenType.EQUAL),
                        lazy(this::assignment)
                    ).map(ep -> expr(ep, 1))
                )
            ).map(this::mapAssignment)
        );
    }


    //logic_or       → logic_and ( "or" logic_and )* ; 
    Parser<Expr> logicOr() {
        return binaryLeftAssoc(logicAnd(), item(TokenType.OR), logicAnd(), false);
    }


    //logic_and      → equality ( "and" equality )* ;
    
    Parser<Expr> logicAnd() {
            return binaryLeftAssoc(equality(), item(TokenType.AND), equality(), false);
    }

 
    //comparison ( ( "!=" | "==" ) comparison )*
    Parser<Expr> equality() {
        return binaryLeftAssoc(
        comparison(),
        new Or<>(item(TokenType.BANG_EQUAL), item(TokenType.EQUAL_EQUAL)),
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
            many(new And(operator, nextOperand)
                .map(l -> new Pair<>(
                    token(l, 0),
                    expr(l, 1)
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
            item(TokenType.LESS),
            item(TokenType.LESS_EQUAL),
            item(TokenType.GREATER),
            item(TokenType.GREATER_EQUAL)
        );
         return binaryLeftAssoc(term(), op, term(),true );

    }

    //factor ( ( "-" | "+" ) factor )* ;
    Parser<Expr> term() {

        Parser<TokenNode> op = new Or<>(
            item(TokenType.MINUS),
            item(TokenType.PLUS)
        );

          return binaryLeftAssoc(factor(), op, factor(),true);
    }
    
    Parser<Expr> factor() {
        Parser<TokenNode> op = new Or<>(
            item(TokenType.SLASH),
            item(TokenType.STAR)
        );
         return binaryLeftAssoc(unary(), op, unary(),true);
    }
       


   /*  unary          → ( "!" | "-" ) unary | call ;
    call           → primary ( "(" arguments? ")" | "." IDENTIFIER )* ;*/
    Parser<Expr> unary() {
        Parser<TokenNode> op = new Or<>(item(TokenType.BANG), item(TokenType.MINUS));

        Parser<Expr> left = new And(op, lazy(this::unary)).map(l -> {
            Token operator = token(l, 0);
            Expr right = expr(l, 1);
            return new Expr.Unary(operator, right);
        });

        return new Or<>(left, call());
    }


  /* */  //call   → primary ( "(" arguments? ")" | "." IDENTIFIER )* ;   
  // call → primary ( "(" arguments? ")" | "." IDENTIFIER )* ;
 

    Parser<Expr> call() {
        return primary().flatMap(callee ->
            many(callSuffix()).map(suffixes -> {
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
            item(TokenType.LEFT_PAREN),
            maybe(arguments()),
            item(TokenType.RIGHT_PAREN)
        ).map(list -> {
            ListAstNode argsNode = listAst(list, 1);
           List<Expr> args = Optional.ofNullable(argsNode)
                        .map(node -> node.nodes().stream()
                        .map(exp -> (Expr) exp)
                        .toList())
                        .orElse(List.of());
            Token paren = token(list, 2); // ')' Token
         return new Expr.Call(null, paren, args); // callee wird später gesetzt
         });
    }


    private Parser<Expr> propertyAccessSuffix() {
        return new And(
            item(TokenType.DOT),
            item(TokenType.IDENTIFIER)
        ).map(list -> {
            Token name = token(list, 1);
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
        @SuppressWarnings("unchecked")
        Parser<Expr> simpleTokens = new Or<>(
            tokenParser(TokenType.TRUE, t -> new Expr.Literal(true)),
            tokenParser(TokenType.FALSE, t -> new Expr.Literal(false)),
            tokenParser(TokenType.NIL, t -> new Expr.Literal(null)),
            tokenParser(TokenType.THIS, Expr.This::new),
            tokenParser(TokenType.NUMBER, t -> new Expr.Literal(t.value())),
            tokenParser(TokenType.STRING, t -> new Expr.Literal(t.value())),
            tokenParser(TokenType.IDENTIFIER, Expr.Variable::new)
        );

        Parser<Expr> superExpr = new And(
            item(TokenType.SUPER),
            item(TokenType.DOT),
            item(TokenType.IDENTIFIER)
        ).map(list -> new Expr.Super(token(list, 0), token(list, 2)));

        Parser<Expr> grouping = between(
            TokenType.LEFT_PAREN, parseExpression(), TokenType.RIGHT_PAREN
        ).map(Expr.Grouping::new);
  
        return new Or<>(simpleTokens,superExpr, grouping);
    }
      @SuppressWarnings("unchecked")
    private <T extends AstNode> Parser<T>between(TokenType left, Parser<T> middle, TokenType right) {
        return new And(item(left), middle, item(right))
            .map(list -> node(list, 1));
    }
    //arguments      → expression ( "," expression )* ;
   Parser<ListAstNode> arguments() {
    return parseExpression().flatMap(firstArg -> 
        many(
            new And(item(TokenType.COMMA), parseExpression())
                .map(list -> expr(list, 1))
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
            return List.of(); 
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

        // If this is a ListAstNode, make it transparent: don't emit a labeled
        // node for it, and connect the parent directly to its children.
        if (node instanceof ListAstNode) {
            int parentId = id; // keep unique id even if transparent
            for (AstNode child : node.children()) {
                int childId = visit(child, sb, idCounter);
                sb.append("  node").append(parentId).append(" -> node").append(childId).append(";\n");
            }
            return parentId;
        }

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

    // Append-capable export convenience
    public static void exportToFile(AstNode root, String filename, boolean append) throws IOException {
        String dot = toDot(root);
        if (append) {
            Files.writeString(Path.of(filename), dot, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } else {
            Files.writeString(Path.of(filename), dot);
        }
        System.out.println("DOT saved to: " + filename + (append ? " (appended)" : ""));
    }
}
