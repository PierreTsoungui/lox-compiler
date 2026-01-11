

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

class Resolver {
    private final Interpreter interpreter;
    private final Stack<Map<String, Boolean>> scopes = new Stack<>();
    private FunctionType currentFunction = FunctionType.NONE;
    private ClassType currentClass = ClassType.NONE;
    
    Resolver(Interpreter interpreter) {
        this.interpreter = interpreter;
    }
    
    private enum FunctionType {
        NONE, FUNCTION, INITIALIZER, METHOD
    }
    
    private enum ClassType {
        NONE, CLASS, SUBCLASS
    }
    
    void resolve(List<Stmt> statements) {
        for (var statement : statements) {
            resolve(statement);
        }
    }
    
    // ========== Statement Resolution ==========
    
    private void resolve(Stmt stmt) {
        switch (stmt) {
            case Stmt.Block(var statements) -> {
                beginScope();
                resolve(statements);
                endScope();
            }
            
            case Stmt.Class(var name, var superclass, var methods) -> {
                var enclosingClass = currentClass;
                currentClass = ClassType.CLASS;
                
                declare(name);
                define(name);
                
                // Superklasse
                if (superclass != null) {
                    if (name.lexeme().equals(superclass.name().lexeme())) {
                        Lox.error(superclass.name(),
                            "A class can't inherit from itself.");
                    }
                    
                    currentClass = ClassType.SUBCLASS;
                    resolve(superclass);
                    
                    beginScope();
                    scopes.peek().put("super", true);
                }
                
                // this scope
                beginScope();
                scopes.peek().put("this", true);
                
                // Methoden
                for (var method : methods) {
                    var declaration = method.name().lexeme().equals("init")
                        ? FunctionType.INITIALIZER 
                        : FunctionType.METHOD;
                    resolveFunction(method, declaration);
                }
                
                endScope(); // this
                
                if (superclass != null) {
                    endScope(); // super
                }
                
                currentClass = enclosingClass;
            }
            
            case Stmt.Expression(var expr) -> 
                resolve(expr);
                
            case Stmt.Function(var name, _, _) -> {
                declare(name);
                define(name);
                resolveFunction(stmt, FunctionType.FUNCTION);
            }
                
            case Stmt.If(var condition, var thenBranch, var elseBranch) -> {
                resolve(condition);
                resolve(thenBranch);
                if(elseBranch!=null) resolve(elseBranch);
              
            }
                
            case Stmt.Print(var expr) -> 
                resolve(expr);
                
            case Stmt.Return(var keyword, var value) -> {
                if (currentFunction == FunctionType.NONE) {
                    Lox.error(keyword, "Can't return from top-level code.");
                }
                
                if (value != null) {
                    if (currentFunction == FunctionType.INITIALIZER) {
                        Lox.error(keyword,
                            "Can't return a value from an initializer.");
                    }
                    resolve(value);
                }
            }
                
            case Stmt.Var(var name, var initializer) -> {
                declare(name);
                if (initializer != null) {
                    resolve(initializer);
                }
                define(name);
            }
                
            case Stmt.While(var condition, var body) -> {
                resolve(condition);
                resolve(body);
            }
        };
    }
    
    // ========== Expression Resolution ==========
    
    private void resolve(Expr expr) {
        switch (expr) {
            case Expr.Assign(var name, var value) -> {
                resolve(value);
                resolveLocal(expr, name);
            }
                
            case Expr.Binary(var left, _, var right) -> {
                resolve(left);
                resolve(right);
            }
                
            case Expr.Call(var callee, _, var arguments) -> {
                resolve(callee);
                for (var argument : arguments) {
                    resolve(argument);
                }
            }
                
            case Expr.Get(var object, _) -> 
                resolve(object);
                
            case Expr.Grouping(var expression) -> 
                resolve(expression);
                
            case Expr.Literal(_) -> 
                {} // Nothing to resolve
                
            case Expr.Logical(var left, _, var right) -> {
                resolve(left);
                resolve(right);
            }
                
            case Expr.Set(var object, _, var value) -> {
                resolve(value);
                resolve(object);
            }
                
            case Expr.Super(var keyword, _) -> {
                if (currentClass == ClassType.NONE) {
                    Lox.error(keyword,
                        "Can't use 'super' outside of a class.");
                } else if (currentClass != ClassType.SUBCLASS) {
                    Lox.error(keyword,
                        "Can't use 'super' in a class with no superclass.");
                }
                resolveLocal(expr, keyword);
            }
                
            case Expr.This(var keyword) -> {
                if (currentClass == ClassType.NONE) {
                    Lox.error(keyword,
                        "Can't use 'this' outside of a class.");
                } else {
                    resolveLocal(expr, keyword);
                }
            }
                
            case Expr.Unary(_, var right) -> 
                resolve(right);
                
            case Expr.Variable(var name) -> {
                if (!scopes.isEmpty() &&
                    Boolean.FALSE.equals(scopes.peek().get(name.lexeme()))) {
                    Lox.error(name,
                        "Can't read local variable in its own initializer.");
                }
                resolveLocal(expr, name);
            }
        };
    }
    
    // ========== Function Resolution ==========
    
    private void resolveFunction(Stmt function, FunctionType type) {
        if (!(function instanceof Stmt.Function func)) {
            return;
        }
        
        var enclosingFunction = currentFunction;
        currentFunction = type;
        
        beginScope();
        for (var param : func.params()) {
            declare(param);
            define(param);
        }
        resolve(func.body());
        endScope();
        
        currentFunction = enclosingFunction;
    }
    
    // ========== Scope Management ==========
    
    private void beginScope() {
        scopes.push(new HashMap<>());
    }
    
    private void endScope() {
        scopes.pop();
    }
    
    private void declare(Token name) {
        if (scopes.isEmpty()) return;
        
        var scope = scopes.peek();
        if (scope.containsKey(name.lexeme())) {
            Lox.error(name,
                "Already a variable with this name in this scope.");
        }
        scope.put(name.lexeme(), false);
    }
    
    private void define(Token name) {
        if (scopes.isEmpty()) return;
        scopes.peek().put(name.lexeme(), true);
    }
    
    private void resolveLocal(Expr expr, Token name) {
        for (int i = scopes.size() - 1; i >= 0; i--) {
            if (scopes.get(i).containsKey(name.lexeme())) {
                int depth = scopes.size() - 1 - i;
                interpreter.resolve(expr, depth);
                return;
            }
        }
    }
}