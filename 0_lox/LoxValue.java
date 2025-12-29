

import java.util.List;
import java.util.Map;

public sealed interface LoxValue 
        permits LoxValue.Nil, LoxValue.Bool, LoxValue.Num, 
                LoxValue.Str, LoxValue.Fn, LoxValue.Klass, 
                LoxValue.Instance, LoxValue.NativeFn {
    
    // === Primitive Werte ===
    
    record Nil() implements LoxValue {
        static final Nil INSTANCE = new Nil();
        public String stringify() { return "nil"; }
        public boolean isTruthy() { return false; }
    }
    
    record Bool(boolean value) implements LoxValue {
        public String stringify() { return String.valueOf(value); }
        public boolean isTruthy() { return value; }
    }
    
    record Num(double value) implements LoxValue {
        public String stringify() {
            String text = String.valueOf(value);
            return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
        }
        public boolean isTruthy() { return true; }
    }
    
    record Str(String value) implements LoxValue {
        public String stringify() { return value; }
        public boolean isTruthy() { return true; }
    }
    
    // === Funktionen ===
    
    record Fn(Token name, List<Token> params, List<Stmt> body, 
              Environment closure, boolean isInitializer) implements LoxValue {
        public String stringify() { return "<fn " + name.lexeme() + ">"; }
        public boolean isTruthy() { return true; }
        
        public Fn bind(Instance instance) {
            var env = new Environment(closure);
            env.define("this", instance);
            return new Fn(name, params, body, env, isInitializer);
        }
    }
    
    // === Native Funktionen ===
    
    non-sealed interface NativeFn extends LoxValue {
        int arity();
        LoxValue call(Interpreter interpreter, List<LoxValue> arguments);
        default String stringify() { return "<native fn>"; }
        default boolean isTruthy() { return true; }
    }
    
    // === Klassen ===
    
    record Klass(String name, Klass superclass, 
                 Map<String, Fn> methods) implements LoxValue {
        public String stringify() { return "<class " + name + ">"; }
        public boolean isTruthy() { return true; }
        
        public Fn findMethod(String name) {
            if (methods.containsKey(name)) {
                return methods.get(name);
            }
            if (superclass != null) {
                return superclass.findMethod(name);
            }
            return null;
        }
    }
    
    // === Instanzen ===
    
    record Instance(Klass klass, Map<String, LoxValue> fields) implements LoxValue {
        public String stringify() { return "<instance " + klass.name() + ">"; }
        public boolean isTruthy() { return true; }
        
        public LoxValue get(Token name) {
            if (fields.containsKey(name.lexeme())) {
                return fields.get(name.lexeme());
            }
            
            var method = klass.findMethod(name.lexeme());
            if (method != null) {
                return method.bind(this);
            }
            
            throw new Interpreter.RuntimeError(name, 
                "Undefined property '" + name.lexeme() + "'.");
        }
        
        public void set(Token name, LoxValue value) {
            fields.put(name.lexeme(), value);
        }
    }
    
    // === Default Methoden ===
    
    default String stringify() {
        return toString();
    }
    
    default boolean isTruthy() {
        return true;
    }
}