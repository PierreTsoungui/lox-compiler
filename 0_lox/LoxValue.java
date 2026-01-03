import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

public sealed interface LoxValue
        permits LoxValue.Nil, LoxValue.Bool, LoxValue.Num,
                LoxValue.Str, LoxValue.Fn, LoxValue.Klass,
                LoxValue.Instance {
        String stringify();
        boolean isTruthy();
    // === Primitive Werte ===
    record Nil() implements LoxValue {
        public static final Nil INSTANCE = new Nil();
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
   record Fn(
    Token name,
    List<Token> params,
    List<Stmt> body,
    Environment closure,
    boolean isInitializer
    ) implements LoxValue, LoxCallable {

        public String stringify() {
            return "<fn " + name.lexeme() + ">";
        }

        public boolean isTruthy() {
            return true;
        }

        @Override
        public int arity() {
            return params.size();
        }

        @Override
        public LoxValue call(Object interpreter, List<LoxValue> arguments) {
            try {
                // Verwende Reflection, um callFunction aufzurufen, ohne Interpreter direkt zu casten
                // Dies vermeidet Zyklen beim Laden in JShell
                var method = interpreter.getClass().getMethod("callFunction", LoxValue.Fn.class, List.class);
                return (LoxValue) method.invoke(interpreter, this, arguments);
            } catch (Exception e) {
                throw new RuntimeException("Error calling function: " + e.getMessage(), e);
            }
        }

        public Fn bind(Instance instance) {
            var env = new Environment(closure);
            env.define("this", instance);
            return new Fn(name, params, body, env, isInitializer);
        }
    }


    // === Klassen ===
    record Klass(String name, Klass superclass,
                 Map<String, Fn> methods) implements LoxValue, LoxCallable {
        public String stringify() { return "<class " + name + ">"; }
        public boolean isTruthy() { return true; }

        public Fn findMethod(String name) {
            if (methods.containsKey(name)) return methods.get(name);
            if (superclass != null) return superclass.findMethod(name);
            return null;
        }

        @Override
        public int arity() {
            var initializer = findMethod("init");
            return initializer != null ? initializer.arity() : 0;
        }

        @Override
        public LoxValue call(Object interpreter, List<LoxValue> arguments) {
            var instance = new Instance(this, new java.util.HashMap<>());
            
            var initializer = findMethod("init");
            if (initializer != null) {
                initializer.bind(instance).call(interpreter, arguments);
            }
            
            return instance;
        }
    }

    // === Instanzen ===
    record Instance(Klass klass, Map<String, LoxValue> fields) implements LoxValue {
        public String stringify() { return "<instance " + klass.name() + ">"; }
        public boolean isTruthy() { return true; }

        public LoxValue get(Token name) {
            if (fields.containsKey(name.lexeme())) return fields.get(name.lexeme());
            var method = klass.findMethod(name.lexeme());
            if (method != null) return method.bind(this);
            throw new RuntimeError(name, "Undefined property '" + name.lexeme() + "'.");
        }

        public void set(Token name, LoxValue value) {
            fields.put(name.lexeme(), value);
        }
    }
}
