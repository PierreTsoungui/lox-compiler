import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class Environment {
    private final Environment enclosing;
    private final Map<String, LoxValue> values = new HashMap<>();
    
    public Environment() {
        this(null);
    }
    
    public Environment(Environment enclosing) {
        this.enclosing = enclosing;
    }
    
    void define(String name, LoxValue value) {
        values.put(name, value);
    }
    
    LoxValue get(Token name) {
        return Optional.ofNullable(values.get(name.lexeme()))
            .or(() -> Optional.ofNullable(enclosing).map(env -> env.get(name)))
            .orElseThrow(() -> new Interpreter.RuntimeError(name,  // ← Hier
                "Undefined variable '" + name.lexeme() + "'."));
    }
    
    void assign(Token name, LoxValue value) {
        if (values.containsKey(name.lexeme())) {
            values.put(name.lexeme(), value);
            return;
        }
        
        if (enclosing != null) {
            enclosing.assign(name, value);
            return;
        }
        
        throw new Interpreter.RuntimeError(name,  // ← Hier
            "Undefined variable '" + name.lexeme() + "'.");
    }
    
    LoxValue getAt(int distance, String name) {
        return ancestor(distance).values.get(name);
    }
    
    void assignAt(int distance, Token name, LoxValue value) {
        ancestor(distance).values.put(name.lexeme(), value);
    }
    
    private Environment ancestor(int distance) {
        var env = this;
        for (int i = 0; i < distance; i++) {
            env = env.enclosing;
        }
        return env;
    }
}