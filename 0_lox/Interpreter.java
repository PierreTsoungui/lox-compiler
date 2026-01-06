import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Interpreter {

    /* ===========================
       Return Exception
    =========================== */
    public  static class Return extends RuntimeException {
        final LoxValue value;
        Return(LoxValue value) {
            super(null, null, false, false);
            this.value = value;
        }
    }

    /* ===========================
       State
    =========================== */
    final Environment globals = new Environment();
    public Environment environment = globals;
    private final Map<Expr, Integer> locals = new HashMap<>();

    /* ===========================
       Constructor
    =========================== */
    public Interpreter() {
        globals.define("clock", new LoxCallable() {
            @Override
            public int arity() { return 0; }

            @Override
            public LoxValue call(Object interpreter, List<LoxValue> arguments) {
                return new LoxValue.Num(
                    System.currentTimeMillis() / 1000.0
                );
            }

            @Override
            public String toString() {
                return "<native fn>";
            }
        });
    }

    /* ===========================
       Interpret
    =========================== */
    public void interpret(List<Stmt> statements) {
        try {
            for (Stmt stmt : statements) {
                execute(stmt);
            }
        } catch (RuntimeError error) {
            System.err.println(
                "[line " + error.token.line() + "] " + error.getMessage()
            );
        }
    }

    public void resolve(Expr expr, int depth) {
        locals.put(expr, depth);
    }

    /* ===========================
       Evaluation
    =========================== */
    public LoxValue evaluate(Expr expr) {
        return switch (expr) {
            case Expr.Assign(var name, var value) ->
                evaluateAssign(expr, name, value);
            case Expr.Binary(var left, var operator, var right) ->
                evaluateBinary(left, operator, right);
            case Expr.Call(var callee, var paren, var arguments) ->
                evaluateCall(callee, paren, arguments);
            case Expr.Get(var object, var name) ->
                evaluateGet(object, name);
            case Expr.Grouping(var expression) ->
                evaluate(expression);
            case Expr.Literal(var value) ->
                value == null ? LoxValue.Nil.INSTANCE : wrapLiteral(value);
            case Expr.Logical(var left, var operator, var right) ->
                evaluateLogical(left, operator, right);
            case Expr.Set(var object, var name, var value) ->
                evaluateSet(object, name, value);
            case Expr.Super(var keyword, var method) ->
                evaluateSuper(keyword, method);
            case Expr.This(var keyword) ->
                lookUpVariable(keyword, expr);
            case Expr.Unary(var operator, var right) ->
                evaluateUnary(operator, right);
            case Expr.Variable(var name) ->
                lookUpVariable(name, expr);
        };
    }

    /* ===========================
       Expressions
    =========================== */
    private LoxValue evaluateAssign(Expr expr, Token name, Expr value) {
        LoxValue val = evaluate(value);
        Integer distance = locals.get(expr);

        if (distance != null) {
            environment.assignAt(distance, name, val);
        } else {
            globals.assign(name, val);
        }
        return val;
    }

    private LoxValue evaluateBinary(Expr left, Token operator, Expr right) {
        LoxValue l = evaluate(left);
        LoxValue r = evaluate(right);

        return switch (operator.type()) {
            case BANG_EQUAL -> new LoxValue.Bool(!isEqual(l, r));
            case EQUAL_EQUAL -> new LoxValue.Bool(isEqual(l, r));
            case GREATER -> compare(operator, l, r, ">");
            case GREATER_EQUAL -> compare(operator, l, r, ">=");
            case LESS -> compare(operator, l, r, "<");
            case LESS_EQUAL -> compare(operator, l, r, "<=");
            case PLUS -> plus(operator, l, r);
            case MINUS -> numberOp(operator, l, r, "-");
            case STAR -> numberOp(operator, l, r, "*");
            case SLASH -> numberOp(operator, l, r, "/");
            default -> throw new IllegalStateException();
        };
    }

    private LoxValue evaluateCall(
        Expr callee,
        Token paren,
        List<Expr> arguments
    ) {
        LoxValue calleeVal = evaluate(callee);

        if (!(calleeVal instanceof LoxCallable callable)) {
            throw new RuntimeError(
                paren,
                "Can only call functions and classes."
            );
        }

        List<LoxValue> args = arguments.stream()
            .map(this::evaluate)
            .map(v ->  v)
            .toList();

        if (args.size() != callable.arity()) {
            throw new RuntimeError(
                paren,
                "Expected " + callable.arity() +
                " arguments but got " + args.size() + "."
            );
        }

        return callable.call(this, args);
    }

    private LoxValue evaluateGet(Expr object, Token name) {
        LoxValue obj = evaluate(object);
        if (obj instanceof LoxValue.Instance instance) {
            return instance.get(name);
        }
        throw new RuntimeError(name, "Only instances have properties.");
    }

    private LoxValue evaluateLogical(Expr left, Token operator, Expr right) {
        LoxValue leftVal = evaluate(left);

        if (operator.type() == TokenType.OR) {
            if (leftVal.isTruthy()) return leftVal;
        } else {
            if (!leftVal.isTruthy()) return leftVal;
        }
        return evaluate(right);
    }

    private LoxValue evaluateSet(Expr object, Token name, Expr value) {
        LoxValue obj = evaluate(object);
        if (obj instanceof LoxValue.Instance instance) {
            LoxValue val = evaluate(value);
            instance.set(name, val);
            return val;
        }
        throw new RuntimeError(name, "Only instances have fields.");
    }

    private LoxValue evaluateSuper(Token keyword, Token method) {
        int distance = locals.get(new Expr.Super(keyword, method));
        var superclass =
            (LoxValue.Klass) environment.getAt(distance, "super");
        var object =
            (LoxValue.Instance) environment.getAt(distance - 1, "this");

        var fn = superclass.findMethod(method.lexeme());
        if (fn == null) {
            throw new RuntimeError(method, "Undefined property.");
        }
        return fn.bind(object);
    }

    private LoxValue evaluateUnary(Token operator, Expr right) {
        LoxValue r = evaluate(right);

        return switch (operator.type()) {
            case BANG -> new LoxValue.Bool(!r.isTruthy());
            case MINUS -> {
                checkNumber(operator, r);
                yield new LoxValue.Num(-((LoxValue.Num) r).value());
            }
            default -> throw new IllegalStateException();
        };
    }

    /* ===========================
       Helpers
    =========================== */
    private LoxValue lookUpVariable(Token name, Expr expr) {
        Integer distance = locals.get(expr);
        Object value =
            distance != null
                ? environment.getAt(distance, name.lexeme())
                : globals.get(name);

        if (value instanceof LoxValue) {
             return (LoxValue) value;
        }
    
        // Wenn es ein LoxCallable ist, wrappe es
        if (value instanceof LoxCallable callable) {
             return new LoxValue.NativeFn(callable);
        }
    
        throw new RuntimeError(name, "Undefined variable '" + name.lexeme() + "'.");
    }

    private boolean isEqual(LoxValue a, LoxValue b) {
        if (a instanceof LoxValue.Nil) return b instanceof LoxValue.Nil;
        return a.equals(b);
    }

    private void checkNumber(Token op, LoxValue v) {
        if (!(v instanceof LoxValue.Num)) {
            throw new RuntimeError(op, "Operand must be a number.");
        }
    }

    private LoxValue compare(
        Token op,
        LoxValue l,
        LoxValue r,
        String kind
    ) {
        checkNumber(op, l);
        checkNumber(op, r);

        double a = ((LoxValue.Num) l).value();
        double b = ((LoxValue.Num) r).value();

        return switch (kind) {
            case ">" -> new LoxValue.Bool(a > b);
            case ">=" -> new LoxValue.Bool(a >= b);
            case "<" -> new LoxValue.Bool(a < b);
            case "<=" -> new LoxValue.Bool(a <= b);
            default -> throw new IllegalStateException();
        };
    }

    private LoxValue numberOp(
        Token op,
        LoxValue l,
        LoxValue r,
        String kind
    ) {
        checkNumber(op, l);
        checkNumber(op, r);

        double a = ((LoxValue.Num) l).value();
        double b = ((LoxValue.Num) r).value();

        return switch (kind) {
            case "-" -> new LoxValue.Num(a - b);
            case "*" -> new LoxValue.Num(a * b);
            case "/" -> new LoxValue.Num(a / b);
            default -> throw new IllegalStateException();
        };
    }

    private LoxValue plus(Token op, LoxValue l, LoxValue r) {
        if (l instanceof LoxValue.Num a && r instanceof LoxValue.Num b) {
            return new LoxValue.Num(a.value() + b.value());
        }
        if (l instanceof LoxValue.Str a && r instanceof LoxValue.Str b) {
            return new LoxValue.Str(a.value() + b.value());
        }
        throw new RuntimeError(
            op,
            "Operands must be two numbers or two strings."
        );
    }

    private LoxValue wrapLiteral(Object value) {
        if (value instanceof Double d) return new LoxValue.Num(d);
        if (value instanceof String s) return new LoxValue.Str(s);
        if (value instanceof Boolean b) return new LoxValue.Bool(b);
        throw new IllegalStateException();
    }

    /* ===========================
       Statements
    =========================== */
    public  void execute(Stmt stmt) {
        switch (stmt) {
            case Stmt.Block(var stmts) ->
                executeBlock(stmts, new Environment(environment));
            case Stmt.Expression(var expr) ->
                evaluate(expr);
            case Stmt.Print(var expr) ->
                System.out.println(evaluate(expr).stringify());
            case Stmt.Var(var name, var init) ->
                environment.define(
                    name.lexeme(),
                    init != null
                        ? evaluate(init)
                        : LoxValue.Nil.INSTANCE
                );
            case Stmt.Return(_, var value) ->
                throw new Return(
                    value != null
                        ? evaluate(value)
                        : LoxValue.Nil.INSTANCE
                );
            case Stmt.If(var cond, var thenB, var elseB) -> {
                if (evaluate(cond).isTruthy()) execute(thenB);
                else if (elseB != null) execute(elseB);
            }
            case Stmt.While(var cond, var body) -> {
                while (evaluate(cond).isTruthy()) execute(body);
            }
            case Stmt.Function(var name, var params, var body) -> {
                var fn = new LoxValue.Fn(
                    name, params, body, environment, false
                );
                environment.define(name.lexeme(), fn);
            }
            case Stmt.Class(var name, var superclassExpr, var methods) ->
                executeClass(
                    name,
                    (Expr.Variable) superclassExpr,
                    methods
                );
        }
    }

    private void executeBlock(
        List<Stmt> statements,
        Environment newEnv
    ) {
        Environment previous = environment;
        try {
            environment = newEnv;
            for (Stmt stmt : statements) execute(stmt);
        } finally {
            environment = previous;
        }
    }

    private void executeClass(
        Token name,
        Expr.Variable superclassExpr,
        List<Stmt.Function> methods
    ) {
        LoxValue.Klass superclass = null;

        if (superclassExpr != null) {
            LoxValue val = evaluate(superclassExpr);
            if (!(val instanceof LoxValue.Klass k)) {
                throw new RuntimeError(
                    superclassExpr.name(),
                    "Superclass must be a class."
                );
            }
            superclass = k;
        }

        environment.define(name.lexeme(), LoxValue.Nil.INSTANCE);

        if (superclass != null) {
            environment = new Environment(environment);
            environment.define("super", superclass);
        }

        var methodMap = new HashMap<String, LoxValue.Fn>();
        for (var method : methods) {
            boolean isInit =
                method.name().lexeme().equals("init");

            var fn = new LoxValue.Fn(
                method.name(),
                method.params(),
                method.body(),
                environment,
                isInit
            );
            methodMap.put(method.name().lexeme(), fn);
        }

        var klass = new LoxValue.Klass(
            name.lexeme(),
            superclass,
            methodMap
        );

        if (superclass != null) {
            environment = environment.enclosing;
        }

        environment.assign(name, klass);
    }

    public LoxValue callFunction(LoxValue.Fn function, List<LoxValue> arguments) {
    Environment previous = environment;
    
    try {
        // Neue Umgebung für den Funktionsaufruf
        Environment environment = new Environment(function.closure());
        
        // Parameter binden
        for (int i = 0; i < function.params().size(); i++) {
            environment.define(
                function.params().get(i).lexeme(), 
                arguments.get(i)
            );
        }
        
        // Funktionskörper ausführen
        try {
            executeBlock(function.body(), environment);
        } catch (Return returnValue) {
            // Bei Initializer immer "this" zurückgeben
            if (function.isInitializer()) {
                return (LoxValue) function.closure().getAt(0, "this");
            }
            return returnValue.value;
        }
        
        // Bei Initializer immer "this" zurückgeben
        if (function.isInitializer()) {
            return (LoxValue) function.closure().getAt(0, "this");
        }
        return LoxValue.Nil.INSTANCE;
        
    } finally {
        environment = previous;
    }
}
}
