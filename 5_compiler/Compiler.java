
import java.util.*;
import java.util.function.Function;


// === Der Instruction Set (High-Level Bytecode) ===
// Die VM führt nur diese Records aus. Sie sind "dumm".
sealed interface Op {
    // Stack & Konstanten
    record Const(Val value) implements Op {}
    record Nil() implements Op {}
    record True() implements Op {}
    record False() implements Op {}
    record Pop() implements Op {}
    record Print() implements Op {}

    // Arithmetik
    record Add() implements Op {}
    record Sub() implements Op {}
    record Mul() implements Op {}
    record Div() implements Op {}
    record Neg() implements Op {}
    record Not() implements Op {}

    // Vergleiche
    record Equal() implements Op {}
    record Greater() implements Op {}
    record Less() implements Op {}
    
    // Lokale Variablen (Nur Slot-Indizes!)
    record GetLocal(int slot) implements Op {}
    record SetLocal(int slot) implements Op {}
    
    // Globale (Hier brauchen wir Namen, da Late-Binding)
    record DefGlobal(String name) implements Op {}
    record SetGlobal(String name) implements Op {}
    record GetGlobal(String name) implements Op {}
    
    // Upvalues (Closures)
    record GetUpval(int index) implements Op {}
    record SetUpval(int index) implements Op {}
    record CloseUpval() implements Op {} // Schließt Upvalues beim Verlassen des Scopes

    // Properties von Klassen und Instanzen
    record GetProp(String name) implements Op {}
    record SetProp(String name) implements Op {}

    // Superklasse
    record GetSuper(String name) implements Op {}
    
    // Flow
    record Jump(int offset) implements Op { // Vorwärtssprung
        public Jump { assert offset >= 0; }
    }
    record JumpIfFalse(int offset) implements Op { // Vorwärtssprung
        public JumpIfFalse { assert offset >= 0; }
    }
    record Loop(int offset) implements Op { // Rückwärtssprung
        public Loop { assert offset >= 0; }
    }
    
    // Au
    // rufe
    record Call(int args) implements Op {}
    record Invoke(String name, int args) implements Op {}
    record SuperInvoke(String name, int args) implements Op {}
    record Return() implements Op {}

    // Erzeugt ein Closure aus dem Funktions-Template
    record Closure(CompiledFunction fn, List<UpvalueDescriptor> upvalues) implements Op {}

    // Objektorientierung
    record Class(String name) implements Op {}
    record Inherit() implements Op {}
    record Method(String name) implements Op {} // Bindet Methode an Klasse
}

// === Hilfsklassen (Internals) ===

// Der Code des Funktionsrumpfes (OpCodes)
record CompiledFunction(String name, int arity, List<Op> code) {}

// Beschreibt, woher ein Upvalue kommt (für den CLOSURE Befehl)
record UpvalueDescriptor(boolean isLocal, int index) {}

// Ein Zeiger auf eine Variable (für Closures).
// Kann entweder auf den Stack zeigen (isOpen) oder einen geschlossenen Wert halten.
class Upvalue {
    int location;      // Index im Stack (wenn offen)
    Val closed;   // Der gespeicherte Wert (wenn geschlossen)
    boolean isOpen = true;

    public Upvalue(int location) { this.location = location; }

    // Zugriff abstrahiert: Egal ob Stack oder Closed
    Val get(List<Val> stack) {
        return isOpen ? stack.get(location) : closed;
    }

    void set(List<Val> stack, Val value) {
        if (isOpen) stack.set(location, value);
        else closed = value;
    }

    void close(List<Val> stack) {
        closed = stack.get(location);
        isOpen = false;
    }
}

// === Die Wurzel aller Werte ===
sealed interface Val {
    // Helper: Ist der Wert "truthy" im Sinne von Lox? (Nur false und nil sind falsch)
    default boolean isTruthy() { return true; }

    // --- Primitive Werte (Immutable Records) ---
    record Nil() implements Val {
        @Override public String toString() { return "nil"; }
        @Override public boolean isTruthy() { return false; }
    }
    record Bool(boolean value) implements Val {
        @Override public String toString() { return String.valueOf(value); }
        @Override public boolean isTruthy() { return value; }
    }

    record Num(double value) implements Val {
        @Override public String toString() { 
            // Lox-Style: Keine ".0" bei Ganzzahlen anzeigen
            if (value == (long)value) return String.format("%d", (long)value);
            return String.valueOf(value); 
        }
    }
    
    record Str(String value) implements Val { // ToDo: Gehört das nicht unter Obj?
        @Override public String toString() { return value; }
    }

    // --- Heap-Objekte ---

    sealed interface Obj extends Val {
        // CLOSURE: Die Laufzeit-Instanz einer Funktion (Funktions-Code + gefangene Variablen)
        final class Closure implements Obj {
            public final CompiledFunction fn;
            public final Upvalue[] upvalues;
            public Closure(CompiledFunction fn, Upvalue[] upvalues) {
                this.fn = fn;
                this.upvalues = upvalues;
            }
            @Override public String toString() { return "<fn " + fn.name() + ">"; }
        }

        // NATIVE FUNCTION: Ein Wrapper um Java-Code (Lambda)
        final class Native implements Obj {
            public final String name;
            public final int arity;
            public final Function<List<Val>, Val> logic;
            public Native(String name, int arity, Function<List<Val>, Val> logic) {
                this.name = name;
                this.arity = arity;
                this.logic = logic;
            }
            @Override public String toString() { return "<native fn " + name + ">"; }
        }

        // KLASS: Mutabel (Methoden werden nach und nach hinzugefügt)
        final class Klass implements Obj {
            public final String name;
            public final Map<String, Closure> methods;
            public Klass(String name) {
                this.name = name;
                this.methods = new HashMap<>();
            }
            @Override public String toString() { return "<class " + name + ">"; }
        }

        // INSTANCE: Mutabel (Felder ändern sich zur Laufzeit)
        final class Instance implements Obj {
            public final Klass klass;
            public final Map<String, Val> fields;
            public Instance(Klass klass) {
                this.klass = klass;
                this.fields = new HashMap<>();
            }
            // Helper für Property Access
            Val get(String name) {
                if (fields.containsKey(name)) return fields.get(name);
                Closure method = klass.methods.get(name);
                if (method != null) return new BoundMethod(this, method);
                throw new RuntimeException("Undefined property '" + name + "'.");
            }
            void set(String name, Val value) {
                fields.put(name, value);
            }
            @Override public String toString() { return "<inst " + klass.name + ">"; }
        }

        // BOUND METHOD: Verbindet eine Instanz fest mit einer Methode
        final class BoundMethod implements Obj {
            public final Instance receiver;
            public final Closure method;
            public BoundMethod(Instance receiver, Closure method) {
                this.receiver = receiver;
                this.method = method;
            }
            @Override public String toString() { return "<fn " + method.fn.name() + ">"; }
        }
    }
}


/**
 * Compiler Implementation
 */
public class Compiler {
    private SmartAssembler asm = new SmartAssembler();
    
    public CompiledFunction compile(String source) {
        List<Stmt> statements = ParserMain.parseProgram(source);
        if (statements.isEmpty()) return asm.compile();

        for (Stmt stmt : statements) {
            stmtToAsm(stmt);
        }
         
        // Füge implizites return nil am Ende hinzu
        asm.nil();
        asm.ret();
        return asm.compile();
    }

    // --- Statements ---
    private void stmtToAsm(Stmt stmt) {
        switch (stmt) {
            case Stmt.Expression expr -> {
                exprToAsm(expr.expr());
                
            }
            
            case Stmt.Print print -> {
                exprToAsm(print.expr());
                asm.print();
            }
            
            case Stmt.Var var -> {
                if (var.initializer() != null) 
                    exprToAsm(var.initializer());
                else 
                    asm.nil();
                asm.var(var.name().lexem());
            }
            
            case Stmt.Return ret -> {
                if (ret.value() != null) 
                    exprToAsm(ret.value());
                else 
                    asm.nil();
                asm.ret();
            }
            
            case Stmt.Block block -> {
                // Verwende die öffentliche scope() Methode
                asm.scope(a -> {
                    for (Stmt s : block.statements()) {
                        stmtToAsm(s);
                    }
                });
            }
            
            case Stmt.If ifStmt -> {
                // Verwende die ifThenElse-Methode von SmartAssembler
                asm.ifThenElse(
                    a -> exprToAsm(ifStmt.condition()),
                    a -> stmtToAsm(ifStmt.thenBranch()),
                    ifStmt.elseBranch().isPresent() 
                        ? a -> stmtToAsm(ifStmt.elseBranch().get()) 
                        : null
                );
            }
            case Stmt.While whileStmt -> {
                // while (condition) body
                asm.whileLoop(
                    a->exprToAsm(whileStmt.condition()),
                    a->stmtToAsm(whileStmt.body())
                );
               
            }
            
           // In Compiler.java:
            case Stmt.Function func -> {
                // Parameternamen extrahieren
                List<String> paramNames = new ArrayList<>();
                for (Token param : func.params()) {
                    paramNames.add(param.lexem());
                }
    
                asm.fun(func.name().lexem(), paramNames, a -> {
                    // Funktionskörper
                    for (Stmt s : func.body()) {
                        stmtToAsm(s);
                    }

                 });
            }
            
            case Stmt.Class classDecl -> {
                     String superClassName = null;
                if (classDecl.superClass() != null) {
                    superClassName = classDecl.superClass().name().lexem();
                }

                asm.classDecl(classDecl.name().lexem(), superClassName, a -> {
                    for (Stmt.Function method : classDecl.methods()) {
                            List<String> params = method.params()
                            .stream()
                            .map(Token::lexem)
                            .toList();
                            a.method(
                                    method.name().lexem(),
                                    params,
                                    ma -> {
                                        for (Stmt st : method.body()) {
                                            stmtToAsm(st);
                                        }
                                     }
                            );
                    }
                });
            }

            
            default -> 
                throw new RuntimeException("Unknown statement type: " + stmt);
        }
    }

    // --- Expressions ---
    private void exprToAsm(Expr expr) {
        switch (expr) {
            case Expr.Literal lit -> {
                Object v = lit.value();
                if (v instanceof Double d) 
                    asm.const_(new Val.Num(d));
                else if (v instanceof Boolean b) {
                    if (b) asm.true_(); else asm.false_();
                }
                else if (v instanceof String s) 
                    asm.const_(new Val.Str(s));
                else if (v == null) 
                    asm.nil();
                else 
                    throw new RuntimeException("Unknown literal: " + v);
            }
            
             case Expr.This thisExpr -> {
                // 'this' referenziert die aktuelle Instanz
                asm.get("this");
            }
            
            case Expr.Super superExpr -> {
                // 'super.method'
                asm.getSuper(superExpr.method().lexem());
            }
            case Expr.Variable var -> {
                Token token = var.name();   // zuerst sichern
                if (token == null) {
                     throw new RuntimeException("Variable without name (this/super?)");
                }
                
                asm.get(var.name().lexem());

            }
            case Expr.Assign ass -> {
                exprToAsm(ass.value());
                asm.set(ass.name().lexem());
            }
            
            case Expr.Binary bin -> {
                exprToAsm(bin.left());
                exprToAsm(bin.right());
                switch (bin.operator().type()) {
                    case PLUS -> asm.add();
                    case MINUS -> asm.sub();
                    case STAR -> asm.mul();
                    case SLASH -> asm.div();
                    case GREATER -> asm.gt();
                    case GREATER_EQUAL -> asm.ge();
                    case LESS -> asm.lt();
                    case LESS_EQUAL -> asm.le();
                    case EQUAL_EQUAL -> asm.eq();
                    case BANG_EQUAL -> asm.ne();
                    default -> 
                        throw new RuntimeException("Unknown binary operator: " + bin.operator());
                }
            }
            
            case Expr.Logical log -> {
                if (log.operator().type() == TokenType.AND) {
                    asm.and(
                        a -> exprToAsm(log.left()), 
                        a -> exprToAsm(log.right())
                    );
                } else {
                    asm.or(
                        a -> exprToAsm(log.left()), 
                        a -> exprToAsm(log.right())
                    );
                }
            }
            
            case Expr.Unary un -> {
              
                switch (un.operator().type()) {
                    case MINUS -> {
                        // -x wird zu 0 - x
                        asm.const_(new Val.Num(0));
                        exprToAsm(un.right());
                        asm.sub();
                    }
                    case BANG -> asm.not();
                    default -> 
                        throw new RuntimeException("Unknown unary operator: " + un.operator());
                }
            }
            
            case Expr.Grouping grp -> 
                exprToAsm(grp.expression());
            
            case Expr.Call call -> {
                exprToAsm(call.callee());
                for (Expr arg : call.arguments()) {
                    exprToAsm(arg);
                }
                asm.call(call.arguments().size());
            }
            
            case Expr.Get get -> {
                exprToAsm(get.object());
                asm.getProp(get.name().lexem());
            }
            
            case Expr.Set set -> {
                exprToAsm(set.object());
                exprToAsm(set.value());
                asm.setProp(set.name().lexem());
            }
            
           
            
            default -> 
                throw new RuntimeException("Unhandled expression type: " + expr.getClass().getSimpleName());
        }
    }

}
