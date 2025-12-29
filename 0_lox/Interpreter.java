

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Interpreter {
    // Exception Klassen (müssen public sein für LoxValue)
    public static class RuntimeError extends RuntimeException {
        public final Token token;
        public RuntimeError(Token token, String message) {
            super(message);
            this.token = token;
        }
    }
    
    static class Return extends RuntimeException {
        final LoxValue value;
        Return(LoxValue value) {
            this.value = value;
        }
    }
    
    // Interpreter State
    final Environment globals = new Environment();
    private Environment environment = globals;
    private final Map<Expr, Integer> locals = new HashMap<>();
    
    // Konstruktor
    Interpreter() {
        // Native Funktion "clock"
        globals.define("clock", new LoxValue.NativeFn() {
            @Override
            public int arity() { return 0; }
            
            @Override
            public LoxValue call(Interpreter interpreter, List<LoxValue> arguments) {
                return new LoxValue.Num(System.currentTimeMillis() / 1000.0);
            }
        });
    }
    
    // === Hauptmethoden ===
    
    void interpret(List<Stmt> statements) {
        try {
            for (var statement : statements) {
                execute(statement);
            }
        } catch (RuntimeError error) {
               System.err.println("[line " + error.token.line() + "] " + error.getMessage());
           
        }
    }
    
    void resolve(Expr expr, int depth) {
        locals.put(expr, depth);
    }
    
    // === Expression Evaluation ===
    
    LoxValue evaluate(Expr expr) {
        return switch (expr) {
            case Expr.Assign(var name, var value) -> {
                var val = evaluate(value);
                var distance = locals.get(expr);
                if (distance != null) {
                    environment.assignAt(distance, name, val);
                } else {
                    globals.assign(name, val);
                }
                yield val;
            }
            
            case Expr.Binary(var left, var operator, var right) -> 
                evaluateBinary(left, operator, right);
                
            case Expr.Call(var callee, var paren, var arguments) -> 
                evaluateCall(callee, paren, arguments);
                
            case Expr.Get(var object, var name) -> 
                evaluateGet(object, name);
                
            case Expr.Grouping(var expression) -> 
                evaluate(expression);
                
            case Expr.Literal(var value) -> 
                evaluateLiteral(value);
                
            case Expr.Logical(var left, var operator, var right) -> 
                evaluateLogical(left, operator, right);
                
            case Expr.Set(var object, var name, var value) -> 
                evaluateSet(object, name, value);
                
            case Expr.Super(var keyword, var method) -> 
                evaluateSuper(keyword, method);
                
            case Expr.This(var keyword) -> 
                evaluateThis(keyword);
                
            case Expr.Unary(var operator, var right) -> 
                evaluateUnary(operator, right);
                
            case Expr.Variable(var name) -> 
                evaluateVariable(name);
        };
    }
    
    // === Einzelne Evaluierungsmethoden ===
    
    private LoxValue evaluateBinary(Expr left, Token operator, Expr right) {
        var leftVal = evaluate(left);
        var rightVal = evaluate(right);
        
        return switch (operator.type()) {
            case BANG_EQUAL -> new LoxValue.Bool(!isEqual(leftVal, rightVal));
            case EQUAL_EQUAL -> new LoxValue.Bool(isEqual(leftVal, rightVal));
            
            case GREATER, GREATER_EQUAL, LESS, LESS_EQUAL -> {
                checkNumberOperands(operator, leftVal, rightVal);
                var leftNum = (LoxValue.Num) leftVal;
                var rightNum = (LoxValue.Num) rightVal;
                
                yield switch (operator.type()) {
                    case GREATER -> new LoxValue.Bool(leftNum.value() > rightNum.value());
                    case GREATER_EQUAL -> new LoxValue.Bool(leftNum.value() >= rightNum.value());
                    case LESS -> new LoxValue.Bool(leftNum.value() < rightNum.value());
                    case LESS_EQUAL -> new LoxValue.Bool(leftNum.value() <= rightNum.value());
                    default -> throw new IllegalStateException("Unexpected operator");
                };
            }
            
            case MINUS -> {
                checkNumberOperands(operator, leftVal, rightVal);
                yield new LoxValue.Num(
                    ((LoxValue.Num) leftVal).value() - ((LoxValue.Num) rightVal).value());
            }
            
            case PLUS -> {
                if (leftVal instanceof LoxValue.Num l && rightVal instanceof LoxValue.Num r) {
                    yield new LoxValue.Num(l.value() + r.value());
                }
                if (leftVal instanceof LoxValue.Str l && rightVal instanceof LoxValue.Str r) {
                    yield new LoxValue.Str(l.value() + r.value());
                }
                throw new RuntimeError(operator,
                    "Operands must be two numbers or two strings.");
            }
            
            case SLASH -> {
                checkNumberOperands(operator, leftVal, rightVal);
                yield new LoxValue.Num(
                    ((LoxValue.Num) leftVal).value() / ((LoxValue.Num) rightVal).value());
            }
            
            case STAR -> {
                checkNumberOperands(operator, leftVal, rightVal);
                yield new LoxValue.Num(
                    ((LoxValue.Num) leftVal).value() * ((LoxValue.Num) rightVal).value());
            }
            
            default -> throw new IllegalStateException("Unexpected operator: " + operator.type());
        };
    }
    
    private LoxValue evaluateCall(Expr callee, Token paren, List<Expr> arguments) {
        var calleeVal = evaluate(callee);
        var args = arguments.stream()
            .map(this::evaluate)
            .toList();
        
        return switch (calleeVal) {
            case LoxValue.Fn function -> {
                if (args.size() != function.params().size()) {
                    throw new RuntimeError(paren, 
                        "Expected " + function.params().size() + " arguments but got " + args.size() + ".");
                }
                yield callFunction(function, args);
            }
            
            case LoxValue.NativeFn nativeFn -> {
                if (args.size() != nativeFn.arity()) {
                    throw new RuntimeError(paren,
                        "Expected " + nativeFn.arity() + " arguments but got " + args.size() + ".");
                }
                yield nativeFn.call(this, args);
            }
            
            case LoxValue.Klass klass -> {
                if (args.size() != 0 && klass.findMethod("init") == null) {
                    throw new RuntimeError(paren,
                        "Expected 0 arguments for class without initializer.");
                }
                yield instantiateClass(klass, args);
            }
            
            default -> throw new RuntimeError(paren, "Can only call functions and classes.");
        };
    }
    
    private LoxValue evaluateGet(Expr object, Token name) {
        var obj = evaluate(object);
        return switch (obj) {
            case LoxValue.Instance instance -> instance.get(name);
            default -> throw new RuntimeError(name, "Only instances have properties.");
        };
    }
    
    private LoxValue evaluateLiteral(Object value) {
        return switch (value) {
            case Double d -> new LoxValue.Num(d);
            case String s -> new LoxValue.Str(s);
            case Boolean b -> new LoxValue.Bool(b);
            case null -> LoxValue.Nil.INSTANCE;
            default -> throw new IllegalStateException("Unexpected literal type: " + value);
        };
    }
    
    private LoxValue evaluateLogical(Expr left, Token operator, Expr right) {
        var leftVal = evaluate(left);
        
        if (operator.type() == TokenType.OR) {
            if (isTruthy(leftVal)) return leftVal;
        } else {
            if (!isTruthy(leftVal)) return leftVal;
        }
        
        return evaluate(right);
    }
    
    private LoxValue evaluateSet(Expr object, Token name, Expr value) {
        var obj = evaluate(object);
        return switch (obj) {
            case LoxValue.Instance instance -> {
                var val = evaluate(value);
                instance.set(name, val);
                yield val;
            }
            default -> throw new RuntimeError(name, "Only instances have fields.");
        };
    }
    
    private LoxValue evaluateSuper(Token keyword, Token method) {
        var expr = new Expr.Super(keyword, method);
        var distance = locals.get(expr);
        
        if (distance == null) {
            throw new RuntimeError(keyword, "Cannot use 'super' outside of a class.");
        }
        
        var superclass = (LoxValue.Klass) environment.getAt(distance, "super");
        var object = (LoxValue.Instance) environment.getAt(distance - 1, "this");
        
        var methodFunc = superclass.findMethod(method.lexeme());
        if (methodFunc == null) {
            throw new RuntimeError(method,
                "Undefined property '" + method.lexeme() + "'.");
        }
        
        return methodFunc.bind(object);
    }
    
    private LoxValue evaluateThis(Token keyword) {
        return lookUpVariable(keyword, new Expr.This(keyword));
    }
    
    private LoxValue evaluateUnary(Token operator, Expr right) {
        var rightVal = evaluate(right);
        
        return switch (operator.type()) {
            case BANG -> new LoxValue.Bool(!isTruthy(rightVal));
            case MINUS -> {
                checkNumberOperand(operator, rightVal);
                yield new LoxValue.Num(-((LoxValue.Num) rightVal).value());
            }
            default -> throw new IllegalStateException("Unexpected unary operator");
        };
    }
    
    private LoxValue evaluateVariable(Token name) {
        return lookUpVariable(name, new Expr.Variable(name));
    }
    
    // === Function Execution ===
    
    private LoxValue callFunction(LoxValue.Fn function, List<LoxValue> arguments) {
        var environment = new Environment(function.closure());
        
        // Parameter binden
        for (int i = 0; i < function.params().size(); i++) {
            environment.define(function.params().get(i).lexeme(), arguments.get(i));
        }
        
        try {
            executeBlock(function.body(), environment);
            
            // Für Initializer immer 'this' zurückgeben
            if (function.isInitializer()) {
                return environment.get(new Token(TokenType.THIS, "this", null, -1));
            }
            
            return LoxValue.Nil.INSTANCE;
        } catch (Return returnValue) {
            return function.isInitializer() 
                ? environment.get(new Token(TokenType.THIS, "this", null, -1))
                : returnValue.value;
        }
    }
    
    private LoxValue instantiateClass(LoxValue.Klass klass, List<LoxValue> arguments) {
        var instance = new LoxValue.Instance(klass, new HashMap<>());
        
        var initializer = klass.findMethod("init");
        if (initializer != null) {
            var boundMethod = initializer.bind(instance);
            callFunction(boundMethod, arguments);
        } else if (!arguments.isEmpty()) {
            throw new RuntimeError(null, "Expected 0 arguments for class without initializer.");
        }
        
        return instance;
    }
    
    // === Statement Execution ===
    
    private void execute(Stmt stmt) {
        switch (stmt) {
            case Stmt.Block(var statements) -> 
                executeBlock(statements, new Environment(environment));
                
            case Stmt.Class(var name, var superclass, var methods) -> 
                executeClass(name, superclass, methods);
                
            case Stmt.Expression(var expr) -> 
                evaluate(expr);
                
            case Stmt.Function(var name, var params, var body) -> {
                var function = new LoxValue.Fn(name, params, body, environment, false);
                environment.define(name.lexeme(), function);
            }
                
            case Stmt.If(var condition, var thenBranch, var elseBranch) -> {
                if (isTruthy(evaluate(condition))) {
                    execute(thenBranch);
                } else if(elseBranch!=null) {
                    execute(elseBranch);
                }
            }
                
            case Stmt.Print(var expr) -> 
                System.out.println(evaluate(expr).stringify());
                
            case Stmt.Return(var keyword, var value) -> {
                var returnValue = value != null ? evaluate(value) : LoxValue.Nil.INSTANCE;
                throw new Return(returnValue);
            }
                
            case Stmt.Var(var name, var initializer) -> {
                var value = initializer != null ? evaluate(initializer) : LoxValue.Nil.INSTANCE;
                environment.define(name.lexeme(), value);
            }
                
            case Stmt.While(var condition, var body) -> {
                while (isTruthy(evaluate(condition))) {
                    execute(body);
                }
            }
        };
    }
    
    private void executeClass(Token name, Expr.Variable superclass, List<Stmt.Function> methods) {
        // Superklasse evaluieren
        LoxValue superValue = null;
        if (superclass != null) {
            superValue = evaluate(superclass);
            if (!(superValue instanceof LoxValue.Klass)) {
                throw new RuntimeError(superclass.name(),
                    "Superclass must be a class.");
            }
        }
        
        // Klasse definieren (vorerst mit nil)
        environment.define(name.lexeme(), LoxValue.Nil.INSTANCE);
        
        // Super-Umgebung bei Vererbung
        Environment classEnvironment = environment;
        if (superclass != null) {
            classEnvironment = new Environment(environment);
            classEnvironment.define("super", superValue);
        }
        
        // Methoden sammeln
        var methodMap = new HashMap<String, LoxValue.Fn>();
        var previousEnv = environment;
        
        try {
            environment = classEnvironment;
            
            for (var method : methods) {
                var isInitializer = method.name().lexeme().equals("init");
                var function = new LoxValue.Fn(
                    method.name(), method.params(), method.body(), environment, isInitializer);
                methodMap.put(method.name().lexeme(), function);
            }
        } finally {
            environment = previousEnv;
        }
        
        // Klasse erstellen
        var klass = new LoxValue.Klass(
            name.lexeme(),
            (LoxValue.Klass) superValue, 
            methodMap
        );
        
        environment.assign(name, klass);
    }
    
    void executeBlock(List<Stmt> statements, Environment newEnvironment) {
        var previous = this.environment;
        try {
            this.environment = newEnvironment;
            for (var statement : statements) {
                execute(statement);
            }
        } finally {
            this.environment = previous;
        }
    }
    
    // === Helper Methods ===
    
    private LoxValue lookUpVariable(Token name, Expr expr) {
        var distance = locals.get(expr);
        return distance != null 
            ? environment.getAt(distance, name.lexeme())
            : globals.get(name);
    }
    
    private boolean isTruthy(LoxValue value) {
        return value.isTruthy();
    }
    
    private boolean isEqual(LoxValue a, LoxValue b) {
        return switch (a) {
            case LoxValue.Nil() -> b instanceof LoxValue.Nil;
            case LoxValue.Num(double d1) -> 
                b instanceof LoxValue.Num(double value) && d1 == value;
            case LoxValue.Str(String s1) -> 
                b instanceof LoxValue.Str(String value) && s1.equals(value);
            case LoxValue.Bool(boolean b1) -> 
                b instanceof LoxValue.Bool(boolean value) && b1 == value;
            default -> a.equals(b);
        };
    }
    
    private void checkNumberOperand(Token operator, LoxValue operand) {
        if (!(operand instanceof LoxValue.Num)) {
            throw new RuntimeError(operator, "Operand must be a number.");
        }
    }
    
    private void checkNumberOperands(Token operator, LoxValue left, LoxValue right) {
        if (!(left instanceof LoxValue.Num && right instanceof LoxValue.Num)) {
            throw new RuntimeError(operator, "Operands must be numbers.");
        }
    }
}