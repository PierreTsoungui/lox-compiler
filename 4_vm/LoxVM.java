import java.util.*;
import java.util.function.*;
import java.util.function.Function;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

// Instruction Set
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
    
    // Lokale Variablen
    record GetLocal(int slot) implements Op {}
    record SetLocal(int slot) implements Op {}
    
    // Globale
    record DefGlobal(String name) implements Op {}
    record SetGlobal(String name) implements Op {}
    record GetGlobal(String name) implements Op {}
    
    // Upvalues
    record GetUpval(int index) implements Op {}
    record SetUpval(int index) implements Op {}
    record CloseUpval() implements Op {}
    
    // Properties
    record GetProp(String name) implements Op {}
    record SetProp(String name) implements Op {}
    record GetSuper(String name) implements Op {}
    
    // Flow
    record Jump(int offset) implements Op {
        public Jump { assert offset >= 0; }
    }
    record JumpIfFalse(int offset) implements Op {
        public JumpIfFalse { assert offset >= 0; }
    }
    record Loop(int offset) implements Op {
        public Loop { assert offset >= 0; }
    }
    
    // Aufrufe
    record Call(int args) implements Op {}
    record Invoke(String name, int args) implements Op {}
    record SuperInvoke(String name, int args) implements Op {}
    record Return() implements Op {}
    
    // Closures
    record Closure(CompiledFunction fn, List<UpvalueDescriptor> upvalues) implements Op {}
    
    // OOP
    record Class(String name) implements Op {}
    record Inherit() implements Op {}
    record Method(String name) implements Op {}
}

// Function representation
record CompiledFunction(String name, int arity, List<Op> code) {}

// Upvalue descriptor
record UpvalueDescriptor(boolean isLocal, int index) {}

// Value hierarchy
sealed interface Val {
    default boolean isTruthy() { return true; }
    final class Nil implements Val {

        public static final Nil INSTANCE = new Nil();

         private Nil() {}

        @Override
        public String toString() {
            return "nil";
        }

         @Override
        public boolean isTruthy() {
            return false;
        }
    }
    record Bool(boolean value) implements Val {
        @Override public String toString() { return String.valueOf(value); }
        @Override public boolean isTruthy() { return value; }
    }
    record Num(double value) implements Val {
        @Override public String toString() { 
            if (value == (long)value) return String.format("%d", (long)value);
            return String.valueOf(value); 
        }
    }
    record Str(String value) implements Val {
        @Override public String toString() { return value; }
    }
    
    sealed interface Obj extends Val {
        final class Closure implements Obj {
            public final CompiledFunction fn;
            public final Upvalue[] upvalues;
            public Closure(CompiledFunction fn, Upvalue[] upvalues) {
                this.fn = fn;
                this.upvalues = upvalues;
            }
            @Override public String toString() { return "<fn " + fn.name() + ">"; }
        }
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
        final class Klass implements Obj {
            public final String name;
            public final Map<String, Closure> methods;
            public Klass(String name) {
                this.name = name;
                this.methods = new HashMap<>();
            }
            @Override public String toString() { return "<class " + name + ">"; }
        }
        final class Instance implements Obj {
            public final Klass klass;
            public final Map<String, Val> fields;
            public Instance(Klass klass) {
                this.klass = klass;
                this.fields = new HashMap<>();
            }
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

// Upvalue for closures
class Upvalue {
    int location; Val closed; boolean isOpen = true;

    public Upvalue(int location) { this.location = location; }

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
class VM {
    private final List<Val> stack = new ArrayList<>(); // ArrayList erlaubt Random Access für Slots!
    private final List<CallFrame> frames = new ArrayList<>();
    private final Map<String, Val> globals = new HashMap<>();
    
    // Die Liste der offenen Upvalues (Wichtig für closeUpval)
    private final List<Upvalue> openUpvalues = new ArrayList<>();

    private CallFrame frame; // Aktueller Frame (Cache)

    static class CallFrame {
        Val.Obj.Closure closure;
        int ip = 0;
        int slotOffset; // Wo beginnt dieser Frame im globalen Stack?
        CallFrame(Val.Obj.Closure c, int offset) { 
            closure = c; 
            slotOffset = offset; 
        }
    }

    public void interpret(CompiledFunction script) {
        // Main Script Frame
        Val.Obj.Closure scriptClosure = new Val.Obj.Closure(script, new Upvalue[0]);
        pushFrame(new CallFrame(scriptClosure, 0));
        
        try {
            run();
        } catch(Exception e) { 
            System.err.println("VM Error: " + e.getMessage());
            e.printStackTrace(); 
        }
    }

    private void run() {
        // Pair as a helper class
        record Pair(Val left, Val right) {}
        
        while (frame.ip < frame.closure.fn.code().size()) {
            Op op = frame.closure.fn.code().get(frame.ip++);
            
            // Debug-Ausgabe für problematische Ops
            if (op instanceof Op.Print || op instanceof Op.Call || 
                op instanceof Op.Invoke || op instanceof Op.SuperInvoke ||
                op instanceof Op.GetSuper || op instanceof Op.GetProp) {
                // System.err.println("[DEBUG] IP: " + (frame.ip-1) + " | Op: " + op + " | Stack: " + stack);
            }

            switch (op) {
                case Op.Const(Val v) -> stack.add(v);
                case Op.Nil() -> stack.add(Val.Nil.INSTANCE);
                case Op.True() -> stack.add(new Val.Bool(true));
                case Op.False() -> stack.add(new Val.Bool(false));
                case Op.Pop() -> stack.removeLast();
                case Op.Print() -> {
                    Val value = stack.getLast(); // Nur lesen, nicht entfernen!
                    System.out.println(value);
                }

                case Op.Add() -> {
                    Val b = stack.removeLast();
                    Val a = stack.removeLast();
                    stack.add(switch(new Pair(a, b)) {
                        case Pair(Val.Num x, Val.Num y) -> new Val.Num(x.value() + y.value());
                        case Pair(Val.Str x, Val.Str y) -> new Val.Str(x.value() + y.value());
                        default -> throw new RuntimeException("Operands must be two numbers or strings.");
                    });
                }
                case Op.Sub() -> {
                    Val b = stack.removeLast();
                    Val a = stack.removeLast();
                    stack.add(switch(new Pair(a, b)) {
                        case Pair(Val.Num x, Val.Num y) -> new Val.Num(x.value() - y.value());
                        default -> throw new RuntimeException("Operands must be two numbers.");
                    });
                }
                case Op.Mul() -> {
                    Val b = stack.removeLast();
                    Val a = stack.removeLast();
                    stack.add(switch(new Pair(a, b)) {
                        case Pair(Val.Num x, Val.Num y) -> new Val.Num(x.value() * y.value());
                        default -> throw new RuntimeException("Operands must be two numbers.");
                    });
                }
                case Op.Div() -> {
                    Val b = stack.removeLast();
                    Val a = stack.removeLast();
                    stack.add(switch(new Pair(a, b)) {
                        case Pair(Val.Num x, Val.Num y) -> new Val.Num(x.value() / y.value());
                        default -> throw new RuntimeException("Operands must be two numbers.");
                    });
                }
                case Op.Not() -> {
                    stack.add(new Val.Bool(!stack.removeLast().isTruthy()));
                }
                case Op.Equal() -> {
                    Val snd = stack.removeLast();
                    Val fst = stack.removeLast();
                    // Spezialbehandlung für Zahlen: Wir wollen IEEE 754 Semantik, 
                    // nicht Java-Objekt-Semantik (Double.equals):
                    // new Num(Double.NaN).equals(new Num(Double.NaN)) ==> true
                    if (fst instanceof Val.Num n1 && snd instanceof Val.Num n2) {
                        // In Java ist (NaN == NaN) false. Das ist genau das, was wir wollen.
                        // Auch (-0.0 == 0.0) ist true. Auch das wollen wir.
                        stack.add(new Val.Bool(n1.value() == n2.value()));
                        //return wurde entfernt, da wir den Vergleichsoperator nicht verlassen wollen, sondern nur das Ergebnis auf den Stack legen wollen.
                    } else {
                        // Für alle anderen Typen (Strings, Objekte, Bools, Nil) 
                        // ist die Java-Standard-Gleichheit (equals) korrekt.
                        stack.add(new Val.Bool(fst.equals(snd)));
                    }
                   
                }

                case Op.Greater() -> {
                    if (stack.removeLast() instanceof Val.Num y &&
                        stack.removeLast() instanceof Val.Num x)
                        stack.add(new Val.Bool(x.value() > y.value()));
                    else throw new RuntimeException("Operands must be two numbers.");                    
                }
                case Op.Less() -> {
                    if (stack.removeLast() instanceof Val.Num y &&
                        stack.removeLast() instanceof Val.Num x)
                        stack.add(new Val.Bool(x.value() < y.value()));
                    else throw new RuntimeException("Operands must be two numbers.");
                }
                
                // --- Variablen: Extrem schnell (Array Access) ---
                case Op.GetLocal(int slot) -> stack.add(stack.get(frame.slotOffset + slot));
                case Op.SetLocal(int slot) -> stack.set(frame.slotOffset + slot, stack.getLast());
                
                case Op.DefGlobal(var n) -> globals.put(n, stack.removeLast());
                case Op.SetGlobal(String name) -> {
                    Val value = stack.getLast(); 
                    if (globals.containsKey(name)) {
                        globals.put(name, value);
                    } else {
                        throw new RuntimeException("Undefined variable '" + name + "'.");
                    }
                }
                case Op.GetGlobal(var n) -> {
                    if(!globals.containsKey(n)) throw new RuntimeException("Undefined global: " + n);
                    stack.add(globals.get(n));
                }

                // --- Upvalues (Der Closure-Zugriff) ---
                case Op.GetUpval(int idx) -> stack.add(frame.closure.upvalues[idx].get(stack));
                case Op.SetUpval(int idx) -> frame.closure.upvalues[idx].set(stack, stack.getLast());
                case Op.CloseUpval() -> {
                    closeUpvalues(stack.size() - 1);
                }

                // --- Flow ---
                case Op.Jump(int off) -> frame.ip += off;
                case Op.JumpIfFalse(int off) -> { if (!stack.getLast().isTruthy()) frame.ip += off; } 
                case Op.Loop(int off) -> frame.ip -= off;
                
                // --- Funktionen --- Hier entsteht das Closure-Objekt zur LAUFZEIT
                case Op.Closure(CompiledFunction fn, List<UpvalueDescriptor> ups) -> {
                    Upvalue[] captured = new Upvalue[ups.size()];
                    for (int i = 0; i < ups.size(); i++) {
                        UpvalueDescriptor desc = ups.get(i);
                        if (desc.isLocal()) {
                            captured[i] = captureUpvalue(frame.slotOffset + desc.index());
                        } else {
                            captured[i] = frame.closure.upvalues[desc.index()];
                        }
                    }
                    stack.add(new Val.Obj.Closure(fn, captured));
                }
                
                case Op.Call(int argc) -> {
                    Val callee = stack.get(stack.size() - 1 - argc);
                    
                    // Spezialbehandlung für Klassen-Konstruktor
                    if (callee instanceof Val.Obj.Klass klass) {
                        // 1. Instanz erzeugen
                        Val.Obj.Instance instance = new Val.Obj.Instance(klass);
                        
                        // 2. Ersetze Klasse durch Instanz auf dem Stack
                        stack.set(stack.size() - 1 - argc, instance);
                        
                        // 3. Initializer aufrufen (falls vorhanden)
                        if (klass.methods.containsKey("init")) {
                            Val.Obj.Closure init = klass.methods.get("init");
                            callClosure(init, argc);
                        } else if (argc != 0) {
                            throw new RuntimeException("Expected 0 arguments but got " + argc + ".");
                        }
                    } else {
                        callValue(callee, argc);
                    }
                }
                
                case Op.Return() -> {
                    boolean isInitializer = "init".equals(frame.closure.fn.name());
                    Val result = isInitializer ? stack.get(frame.slotOffset) : stack.removeLast();
                    closeUpvalues(frame.slotOffset);
                    int frameStart = frame.slotOffset;
                    frames.removeLast();
                    if (frames.isEmpty()) return;
                    frame = frames.getLast();
                    while (stack.size() > frameStart) {
                        stack.removeLast();
                    }
                    stack.add(result);
                }
                
                // --- OOP ---
                case Op.Class(var name) -> stack.add(new Val.Obj.Klass(name));
                case Op.Inherit() -> {
                    Val subVal = stack.removeLast();
                    Val supVal = stack.getLast();
                    if (subVal instanceof Val.Obj.Klass sub && 
                        supVal instanceof Val.Obj.Klass sup) {
                        sub.methods.putAll(sup.methods);
                    } else {
                        throw new RuntimeException("Inherit must take two classes.");
                    }
                }
                case Op.Method(var name) -> {
                    Val.Obj.Closure method = (Val.Obj.Closure) stack.removeLast();
                    Val.Obj.Klass klass = (Val.Obj.Klass) stack.getLast();
                    klass.methods.put(name, method);
                }

                case Op.Invoke(String name, int args) -> {
                    int receiverIdx = stack.size() - 1 - args;
                    Val receiver = stack.get(receiverIdx);
                    
                    if (receiver instanceof Val.Obj.Instance instance) {
                        // A. FELD-CHECK (Fields shadow Methods!)
                        if (instance.fields.containsKey(name)) {
                            Val value = instance.fields.get(name);
                            stack.set(receiverIdx, value);
                            callValue(value, args);
                            //return;
                        }
                        // B. METHODEN-LOOKUP
                        else if (instance.klass.methods.containsKey(name)) {
                            Val.Obj.Closure method = instance.klass.methods.get(name);
                            if (args != method.fn.arity()) {
                                throw new RuntimeException("Expected " + method.fn.arity() + 
                                                        " arguments but got " + args + ".");
                            }
                            callClosure(method, args);
                            //return;
                        } 
                        // C. FEHLER
                        else {
                            throw new RuntimeException("Undefined property '" + name + "'.");
                        }
                    } else {
                        throw new RuntimeException("Only instances have methods.");
                    }
                }
                
                case Op.SuperInvoke(String name, int args) -> {
                    // Stack: [..., superClass, receiver, arg1, arg2, ...]
                    // SmartAssembler pusht superClass als letztes
                    Val superVal = stack.removeLast();
                    
                    if (superVal instanceof Val.Obj.Klass superKlass) {
                        Val.Obj.Closure method = superKlass.methods.get(name);
                        if (method == null) {
                            throw new RuntimeException("Undefined property '" + name + "' in superclass.");
                        }
                        callClosure(method, args);
                    } else {
                        throw new RuntimeException("Super operand must be a class.");
                    }
                }

                case Op.GetProp(var name) -> {
                    Val obj = stack.removeLast();
                    if (obj instanceof Val.Obj.Instance inst) {
                        stack.add(inst.get(name));
                    } else {
                        throw new RuntimeException("Only instances have properties.");
                    }
                }
                
                case Op.SetProp(var name) -> {
                    Val value = stack.removeLast();
                    Val obj = stack.removeLast();
                    if (obj instanceof Val.Obj.Instance inst) {
                        inst.set(name, value);
                        stack.add(value); // Assignment returns the value
                    } else {
                        throw new RuntimeException("Only instances have properties.");
                    }
                }
                
                case Op.GetSuper(var name) -> {
                    // Stack: [instance, superClass] → GetSuper → [boundMethod]
                    Val superVal = stack.removeLast();
                    Val receiverVal = stack.removeLast();
                    
                    if (superVal instanceof Val.Obj.Klass superKlass && 
                        receiverVal instanceof Val.Obj.Instance receiver) {
                        Val.Obj.Closure method = superKlass.methods.get(name);
                        if (method == null) {
                            throw new RuntimeException("Undefined property '" + name + "' in superclass.");
                        }
                        stack.add(new Val.Obj.BoundMethod(receiver, method));
                    } else {
                        throw new RuntimeException("GetSuper requires instance and superclass.");
                    }
                }
                
                default -> throw new RuntimeException("Unimplemented opcode: " + op.getClass().getSimpleName());
            }
        }
    }
    
    // --- Helper ---
    
    private void callValue(Val callee, int argc) {
        switch (callee) {
            case Val.Obj.BoundMethod bm -> {
                // Ersetze BoundMethod durch receiver auf dem Stack
                int receiverIdx = stack.size() - 1 - argc;
                stack.set(receiverIdx, bm.receiver);
                callClosure(bm.method, argc);
            }
            case Val.Obj.Closure cl -> callClosure(cl, argc);
            case Val.Obj.Klass k -> {
                // Sollte normalerweise im Op.Call Fall behandelt werden
                // Falls hierher gekommen, Instanz erzeugen
                Val.Obj.Instance instance = new Val.Obj.Instance(k);
                int instanceSlot = stack.size() - 1 - argc;
                stack.set(instanceSlot, instance);
                
                if (k.methods.containsKey("init")) {
                    Val.Obj.Closure init = k.methods.get("init");
                    callClosure(init, argc);
                } else if (argc != 0) {
                    throw new RuntimeException("Expected 0 arguments but got " + argc + ".");
                }
            }
            case Val.Obj.Native nat -> {
                if (argc != nat.arity) throw new RuntimeException("Wrong arity.");
                List<Val> args = new ArrayList<>();
                for (int i = 0; i < argc; i++) {
                    args.add(0, stack.removeLast());
                }
                stack.removeLast(); // Native Funktion
                Val result = nat.logic.apply(args);
                stack.add(result);
            }
            default -> throw new RuntimeException(callee + " is not a callable object.");
        }
    }

    private void callClosure(Val.Obj.Closure cl, int argc) {
        if (argc != cl.fn.arity()) {
            throw new RuntimeException("Expected " + cl.fn.arity() + " arguments but got " + argc + ".");
        }
        pushFrame(new CallFrame(cl, stack.size() - argc - 1));
    }

    private void pushFrame(CallFrame f) {
        frames.add(f);
        frame = f;
    }

    // Upvalue Management: Find existing open upvalue or create new
    private Upvalue captureUpvalue(int localIndex) {
        for (Upvalue up : openUpvalues) {
            if (up.location == localIndex) return up;
        }
        Upvalue created = new Upvalue(localIndex);
        openUpvalues.add(created);
        return created;
    }

    private void closeUpvalues(int lastIndex) {
        // Verschiebe Werte vom Stack in den Upvalue Heap
        var it = openUpvalues.iterator();
        while (it.hasNext()) {
            Upvalue up = it.next();
            if (up.location >= lastIndex) {
                up.close(stack);
                it.remove();
            }
        }
    }

void main() {
    testSimpleMath();
    testGlobalsAndStrings();
    testControlFlow();
   
}


// --- Test 1: Einfache Mathematik (1 + 2 = 3) ---
void testSimpleMath() {
    System.out.println("=== Test 1: Math (1 + 2) ===");
        
    List<Op> ops = new ArrayList<>();
    ops.add(new Op.Const(new Val.Num(1.0)));
    ops.add(new Op.Const(new Val.Num(2.0)));
    ops.add(new Op.Add());
    ops.add(new Op.Print()); // Stack leer!
    
    // FIX: Wir müssen etwas zurückgeben (implizites return nil)
    ops.add(new Op.Nil());   // Stack: [nil]
    ops.add(new Op.Return());
    
    runScript(ops);
}

// --- Test 2: Strings und Globale Variablen ---
void testGlobalsAndStrings() {
    System.out.println("\n=== Test 2: Strings & Globals ===");

    List<Op> ops = new ArrayList<>();

    // var greeting = "Hallo ";
    ops.add(new Op.Const(new Val.Str("Hallo ")));
    ops.add(new Op.DefGlobal("greeting"));

    // print greeting + "Welt";
    ops.add(new Op.GetGlobal("greeting"));    // Stack: ["Hallo "]
    ops.add(new Op.Const(new Val.Str("Welt"))); // Stack: ["Hallo ", "Welt"]
    ops.add(new Op.Add());                    // Stack: ["Hallo Welt"]
    ops.add(new Op.Print());

    ops.add(new Op.Nil());
    ops.add(new Op.Return());

    runScript(ops);
}

// --- Test 3: Logik & Sprünge (If/Else Simulation) ---
void testControlFlow() {
    System.out.println("\n=== Test 3: Control Flow (Jump) ===");
    
    // Lox:
    // print "Start";
    // if (false) { print "Skip"; }
    // print "Ende";
        
    List<Op> ops = new ArrayList<>();
    ops.add(new Op.Const(new Val.Str("Start")));
    ops.add(new Op.Print());

    ops.add(new Op.False()); 
    ops.add(new Op.JumpIfFalse(2)); 
    // Lox Compiler würde hier eigentlich ein POP einfügen, um die Condition zu entfernen.
    // Da wir das hier weglassen, bleibt 'false' auf dem Stack.

    ops.add(new Op.Const(new Val.Str("Skip")));
    ops.add(new Op.Print());

    ops.add(new Op.Const(new Val.Str("Ende")));
    ops.add(new Op.Print());
    
    // Hier liegt noch das 'false' von oben auf dem Stack, 
    // deshalb funktionierte Return zufällig.
    // Um sicherzugehen, legen wir explizit Nil drauf (auch wenn der Stack dann [false, nil] ist)
    ops.add(new Op.Nil());
    ops.add(new Op.Return());

    runScript(ops);
}

// --- Test 4: Native Funktion (clock) ---
/* void testNativeFunction() {
    System.out.println("\n=== Test 4: Native Function (Clock) ===");

    VM vm = new VM();
    
    // Native Funktion "clock" registrieren
    vm.globals.put("clock", new Val.Obj.Native("clock", 0, args -> {
        return new Val.Num(System.currentTimeMillis() / 1000.0);
    }));

    List<Op> ops = new ArrayList<>();
    
    // Bytecode: print clock();
    ops.add(new Op.GetGlobal("clock")); // Lade Native Fn auf Stack
    ops.add(new Op.Call(0));            // Rufe sie auf
    ops.add(new Op.Print());            // Drucke Ergebnis (Zeitstempel)
    ops.add(new Op.Nil());
    ops.add(new Op.Return());

    // Manuelles Starten, da wir Globals vorab manipuliert haben
    vm.interpret(new CompiledFunction("test_native", 0, ops));
}*/

// --- Helper ---
void runScript(List<Op> code) {
    // Wrapper, um eine CompiledFunction zu bauen und die VM zu starten
    CompiledFunction func = new CompiledFunction("script", 0, code);
    new VM().interpret(func);
}


}

/**
 * Assembler für Lox-Bytecode (Op-basierte VM)
 *
 * Wandelt eine Textdarstellung von Opcodes mit Labels
 * in eine CompiledFunction (List<Op>) um.
 */

// === SmartAssembler Implementation ===
 class SmartAssembler {

    private final Deque<CompilerState> compilers = new ArrayDeque<>();
    private final Set<String> globals = new HashSet<>();

    public SmartAssembler() {
        compilers.push(new CompilerState(null, FunctionType.SCRIPT));
    }

    //--- High Level DSL API ---
    public SmartAssembler const_(Val val) { emit(new Op.Const(val)); return this; }
    public SmartAssembler add() { emit(new Op.Add()); return this; }
    public SmartAssembler print() { emit(new Op.Print()); return this; }
    public SmartAssembler pop() { emit(new Op.Pop()); return this; }
    public SmartAssembler ret() { emit(new Op.Return()); return this; }
    // Literale
    public SmartAssembler nil() { emit(new Op.Nil()); return this; }
    public SmartAssembler true_() { emit(new Op.True()); return this; }
    public SmartAssembler false_() { emit(new Op.False()); return this; }
    // Logik
    public SmartAssembler eq() { emit(new Op.Equal()); return this; }
    public SmartAssembler not() { emit(new Op.Not()); return this; }
    // --- Arithmetik ---
    public SmartAssembler sub() { emit(new Op.Sub()); return this; }
    public SmartAssembler mul() { emit(new Op.Mul()); return this; }
    public SmartAssembler div() { emit(new Op.Div()); return this; }
    // --- Vergleich ---
    public SmartAssembler gt() { emit(new Op.Greater()); return this; }  // >
    public SmartAssembler lt() { emit(new Op.Less()); return this; }  // <

    // !=
    public SmartAssembler ne() {
        emit(new Op.Equal());
        emit(new Op.Not());
        return this;
    }

    // >=  → !(a < b)
    public SmartAssembler ge() {
        emit(new Op.Less());
        emit(new Op.Not());
        return this;
    }
    
    // <=  → !(a > b)
    public SmartAssembler le() {
        emit(new Op.Greater());
        emit(new Op.Not());
        return this;
    }

    // Logical AND
    public SmartAssembler and(Consumer<SmartAssembler> left,
                          Consumer<SmartAssembler> right) {
        left.accept(this);
        int jump = emitJumpIfFalse();
        right.accept(this);
        patchJump(jump);
        return this;
    }

    // Logical OR
    public SmartAssembler or(Consumer<SmartAssembler> left, Consumer<SmartAssembler> right) {
        left.accept(this);                  
        int jumpIfTrue = emitJumpIfFalse();
    
        int jumpOverRight = emitJump();    

        patchJump(jumpIfTrue);             

        right.accept(this);                

        patchJump(jumpOverRight);         

        return this;
    }

    // --- Jump / Branch Helpers ---
    public int emitJumpIfFalse() {
        emit(new Op.JumpIfFalse(0));
        return compilers.peek().code.size() - 1;
    }

    public int emitJump() {
        emit(new Op.Jump(0)); 
        return compilers.peek().code.size() - 1;
    }

    public void patchJump(int pos) {
        int offset = compilers.peek().code.size() - pos - 1;
        Op old = compilers.peek().code.get(pos);
        if (old instanceof Op.JumpIfFalse) {
            compilers.peek().code.set(pos, new Op.JumpIfFalse(offset));
        } else if (old instanceof Op.Jump) {
            compilers.peek().code.set(pos, new Op.Jump(offset));
        } else {
            throw new RuntimeException("patchJump auf falsche Op-Code-Position!");
        }
    }
    
    // Anfang einer Schleife merken
    public int emitLoopStart() {
        return compilers.peek().code.size(); 
    }

    // Sprung zurück zum Anfang der Schleife
    public void emitLoop(int loopStart) {
        int offset = compilers.peek().code.size() - loopStart + 1;
        emit(new Op.Loop(offset));
    }

    // While
    public SmartAssembler whileLoop(Consumer<SmartAssembler> condition, Consumer<SmartAssembler> body) {
        int loopStart = emitLoopStart();         // Schleifenanfang merken

        condition.accept(this);                  // Bedingung auswerten
        int jumpExit = emitJumpIfFalse();       // Wenn false, Schleife verlassen

        body.accept(this);                       // Schleifen-Body
        emitLoop(loopStart);                     // Zurück zum Anfang springen

        patchJump(jumpExit);                     // Exit-Sprung patchen
        return this;
    }

    // If-Else
    public SmartAssembler ifThenElse(Consumer<SmartAssembler> condition,
                                 Consumer<SmartAssembler> thenBranch,
                                 Consumer<SmartAssembler> elseBranch) {
        // 1. Bedingung auswerten
        condition.accept(this);

        // 2. JumpIfFalse platzieren
        int jumpToElse = emitJumpIfFalse();

        // 3. Then-Branch
        thenBranch.accept(this);

        // 4. Jump über Else
        int jumpOverElse = emitJump();

        // 5. Patch JumpIfFalse zum Else
        patchJump(jumpToElse);

        // 6. Else-Branch, falls vorhanden
        if (elseBranch != null) {
            elseBranch.accept(this);
        }

        // 7. Patch Jump über Else zum Ende
        patchJump(jumpOverElse);

        return this;
    }

    public SmartAssembler var(String name) {
        CompilerState current = compilers.peek();

        if (current.scopeDepth > 0) {
            for (Local local : current.locals) {
                if (local.depth == current.scopeDepth && local.name.equals(name)) {
                    throw new RuntimeException(
                        "Lokale Variable '" + name + "' wurde im gleichen Scope schon deklariert."
                    );
                }
            }
            current.addLocal(name);
            int slot = current.locals.size() - 1;
            emit(new Op.SetLocal(slot));
        } else {
            // Global: einfach DefGlobal + SetGlobal
            globals.add(name);
            emit(new Op.DefGlobal(name));   
        }

        return this;
    }

    public SmartAssembler get(String name) {
        namedVariable(name, false);
        return this;
    }
    
    public SmartAssembler set(String name) {
        namedVariable(name, true);
        return this;
    }

    // Block Scopes
    public SmartAssembler scope(Consumer<SmartAssembler> block) {
        beginScope();
        block.accept(this);
        endScope();
        return this;
    }
        
    public SmartAssembler fun(String name, List<String> paramNames, Consumer<SmartAssembler> body) {
        int arity = paramNames.size();
        // 2. Neuen Compiler starten
        CompilerState current = compilers.peek();
        CompilerState fnCompiler = new CompilerState(current, FunctionType.FUNCTION);
        fnCompiler.functionName = name;
        fnCompiler.arity = arity;

        // Slot 0 reservieren (Callee), damit Parameter bei Slot 1 starten
        fnCompiler.addLocal("");

        // Parameter mit RICHTIGEN Namen registrieren
        for (String paramName : paramNames) {
            fnCompiler.addLocal(paramName);
        }
    
        compilers.push(fnCompiler);
    
        // 3. Body kompilieren
        body.accept(this);
    
        // 4. Implizites Return falls nötig
        if (!(compilers.peek().code.getLast() instanceof Op.Return)) {
            emit(new Op.Nil());
            emit(new Op.Return());
        }
    
        // 5. Compiler beenden
        compilers.pop();
        CompiledFunction compiledFn = new CompiledFunction(name, arity, fnCompiler.code);
    
        // 6. Closure erstellen
        current.code.add(new Op.Closure(compiledFn, fnCompiler.upvalues));
        // DANN: Als globale Variable speichern 
        var(name); 
        return this;
    }

    // OOP
    public SmartAssembler classDecl(String name, Consumer<SmartAssembler> body) {
        return classDecl(name, null, body);
    }
    
    public SmartAssembler classDecl(
        String name,
        String superClassName,
        Consumer<SmartAssembler> body
    ) {
        // 0. Reserviere globale Variable, damit die Klasse existiert
        emit(new Op.Nil());
        var(name);

        // 1. Klasse erzeugen
        emit(new Op.Class(name));

        // Klasse sofort in Variable schreiben (bleibt auf Stack)
        set(name);

        if (superClassName != null) {
            beginScope();
            get(superClassName);
            var("super");
            pop(); // Superclass-Wert vom Stack entfernen

            // Vererbung anwenden: Stack [super, class]
            get(superClassName);
            get(name);
            emit(new Op.Inherit());
            pop(); // Superclass vom Stack entfernen
        }

        body.accept(this);

        if (superClassName != null) {
            endScope();
        }

        return this;
    }

    public SmartAssembler getSuper(String method) {
        // Stack: [this, super] → GetSuper → [boundMethod]
        get("this");
        get("super");  
        emit(new Op.GetSuper(method));
        return this;
    }

    // Neu hinzugefügt für Super-Aufrufe
    public SmartAssembler superInvoke(String name, int args) { 
        emit(new Op.SuperInvoke(name, args)); 
        return this; 
    }
    
    public SmartAssembler method(String name, int arity, Consumer<SmartAssembler> body) {
        CompilerState current = compilers.peek();
        CompilerState methodCompiler = new CompilerState(current, FunctionType.METHOD);
        methodCompiler.functionName = name;
        methodCompiler.arity = arity;
        methodCompiler.addLocal("this"); // Slot 0 ist this!
        for(int i=0; i<arity; i++) methodCompiler.addLocal("arg"+i);
        
        compilers.push(methodCompiler);
        body.accept(this);
        if (!(methodCompiler.code.getLast() instanceof Op.Return)) {
            emit(new Op.GetLocal(0)); 
            emit(new Op.Return());
        }
        compilers.pop();
        
        CompiledFunction fn = new CompiledFunction(name, arity, methodCompiler.code);
        
        current.code.add(new Op.Closure(fn, methodCompiler.upvalues));
        emit(new Op.Method(name));
        return this;
    }
    
    public SmartAssembler method(
        String name,
        List<String> paramNames,
        Consumer<SmartAssembler> body
    ) {
        CompilerState current = compilers.peek();
        CompilerState methodCompiler =
        new CompilerState(current, FunctionType.METHOD);

        methodCompiler.functionName = name;
        methodCompiler.arity = paramNames.size();

        // Slot 0 = this
        methodCompiler.addLocal("this");

        for (String param : paramNames) {
            methodCompiler.addLocal(param);
        }

        compilers.push(methodCompiler);
        body.accept(this);
        if (methodCompiler.code.isEmpty() || !(methodCompiler.code.getLast() instanceof Op.Return)) {
            emit(new Op.GetLocal(0));
            emit(new Op.Return());
        }

        compilers.pop();

        CompiledFunction fn =
        new CompiledFunction(name, methodCompiler.arity, methodCompiler.code);

        current.code.add(new Op.Closure(fn, methodCompiler.upvalues));
        emit(new Op.Method(name));

        return this;
    }

    public SmartAssembler call(int args) { emit(new Op.Call(args)); return this; }
    public SmartAssembler invoke(String name, int args) { emit(new Op.Invoke(name, args)); return this; }
    public SmartAssembler getProp(String p) { emit(new Op.GetProp(p)); return this; }
    public SmartAssembler setProp(String p) { emit(new Op.SetProp(p)); return this; }

    // --- Internals & Resolution Logic ---
    private void beginScope() { compilers.peek().scopeDepth++; }
    
    private void endScope() {
        CompilerState c = compilers.peek();
        c.scopeDepth--;
        // Locals poppen & Upvalues schließen
        while (!c.locals.isEmpty() && c.locals.getLast().depth > c.scopeDepth) {
            emit(new Op.CloseUpval()); 
            emit(new Op.Pop());       
            c.locals.removeLast();
        }
    }

    private void emit(Op op) { compilers.peek().code.add(op); }

    // Variable auflösen
    private void namedVariable(String name, boolean canAssign) {
        CompilerState current = compilers.peek();
        
        // 1. Versuch: Lokal
        int arg = resolveLocal(current, name);
        if (arg != -1) {
            emit(canAssign ? new Op.SetLocal(arg) : new Op.GetLocal(arg));
            return;
        }
        
        // 2. Versuch: Upvalue (Rekursiv!)
        arg = resolveUpvalue(current, name);
        if (arg != -1) {
            emit(canAssign ? new Op.SetUpval(arg) : new Op.GetUpval(arg));
            return;
        }
        
        boolean existsGlobally = globals.contains(name);
        if (!canAssign && !existsGlobally) {
            throw new RuntimeException("Variable '" + name + "' nicht definiert!");
        }
        
        // 3. Fallback: Global
        emit(canAssign ? new Op.SetGlobal(name) : new Op.GetGlobal(name));
    }

    private int resolveLocal(CompilerState c, String name) {
        for (int i = c.locals.size() - 1; i >= 0; i--) {
            if (c.locals.get(i).name.equals(name)) return i;
        }
        return -1;
    }
    
    private int resolveUpvalue(CompilerState c, String name) {
        if (c.enclosing == null) return -1;

        // Suche im Parent 
        int local = resolveLocal(c.enclosing, name);
        if (local != -1) {
            c.enclosing.locals.get(local).isCaptured = true;
            return addUpvalue(c, local, true);
        }

        int upvalue = resolveUpvalue(c.enclosing, name);
        if (upvalue != -1) {
            return addUpvalue(c, upvalue, false);
        }

        return -1;
    }

    private int addUpvalue(CompilerState c, int index, boolean isLocal) {
        for (int i = 0; i < c.upvalues.size(); i++) {
            UpvalueDescriptor up = c.upvalues.get(i);
            if (up.index() == index && up.isLocal() == isLocal) return i;
        }
        c.upvalues.add(new UpvalueDescriptor(isLocal, index));
        return c.upvalues.size() - 1;
    }
    
    // --- Helper Classes ---
    public CompiledFunction compile() { return new CompiledFunction("script", 0, compilers.peek().code); }

    enum FunctionType { SCRIPT, FUNCTION, METHOD }
    
    static class Local {
        String name; 
        int depth; 
        boolean isCaptured;
        Local(String n, int d) { name = n; depth = d; }
    }

    static class CompilerState {
        CompilerState enclosing;
        List<Op> code = new ArrayList<>();
        List<Local> locals = new ArrayList<>();
        List<UpvalueDescriptor> upvalues = new ArrayList<>(); 
        int scopeDepth = 0;
        FunctionType type;
        String functionName = "";
        int arity = 0;

        CompilerState(CompilerState enc, FunctionType t) { 
            enclosing = enc; 
            type = t; 
        }
        
        void addLocal(String name) {
            locals.add(new Local(name, scopeDepth));
        }
    }

    static CompiledFunction buildAsm(SmartAssembler a, Consumer<SmartAssembler> build) {
        build.accept(a);
        return a.compile();
    }

    // --- Hilfsmethoden für Tests ---
    static void runTest(String testName, Consumer<SmartAssembler> build) {
        System.out.println("=== Test: " + testName + " ===");
        SmartAssembler a = new SmartAssembler();
        CompiledFunction fn = buildAsm(a, build);
        for(Op op: fn.code()) {
            System.out.println(op);
        }
        System.out.println();
    
    }

    static void printBytecode(CompiledFunction fn) {
        for (Op op : fn.code()) {
            System.out.println(op);
        }
    }
}

public class LoxVM {

    
    // ==================== SmartAssemblerTest-Inhalt ====================

    static void testIfElse() {
        SmartAssembler.runTest("testIfElse", a -> {
            a.const_(new Val.Bool(true))
             .scope(s -> {
                 int jumpFalsePos = s.emitJumpIfFalse();
                 s.const_(new Val.Num(1)).print();
                 int jumpEndPos = s.emitJump();
                 s.patchJump(jumpFalsePos);
                 s.const_(new Val.Num(2)).print();
                 s.patchJump(jumpEndPos);
             });
        });
    }

    static void testWhile() {
        SmartAssembler.runTest("testWhile", a -> {
            a.const_(new Val.Num(0)).var("i");
            int loopStart = a.emitLoopStart();
            a.get("i").const_(new Val.Num(3)).lt();
            int exitJump = a.emitJumpIfFalse();
            a.get("i").print();
            a.get("i").const_(new Val.Num(1)).add().set("i");
            a.emitLoop(loopStart);
            a.patchJump(exitJump);
        });
    }

    static void testOrShortCircuit() {
        SmartAssembler.runTest("testOrShortCircuit", a -> {
            a.or(
                s -> s.true_(),
                s -> s.const_(new Val.Str("BAD")).print()
            );
        });
    }

    static void testClosureCapture() {
        SmartAssembler.runTest("testClosureCapture", a -> {
            a.scope(s -> {
                s.const_(new Val.Num(10)).var("y");
                s.fun("make", Arrays.asList(), mk -> {
                    mk.fun("inner", Arrays.asList(), inn -> {
                        inn.get("y").print();
                    });
                });
            });
        });
    }

    static void testFunctionReturn() {
        SmartAssembler.runTest("testFunctionReturn", a -> {
            a.fun("f", Arrays.asList(), f -> {
                f.const_(new Val.Num(7)).ret();
            });
        });
    }

    static void testClassPropAccess() {
        SmartAssembler.runTest("testClassPropAccess", a -> {
            a.classDecl("C", c -> {
                c.method("m", Arrays.asList(), m -> {
                    m.const_(new Val.Str("hello"))
                     .setProp("x");
                    m.getProp("x")
                     .print();
                });
            });
        });
    }

    static void testMethodWithParams() {
        SmartAssembler.runTest("testMethodWithParams", a -> {
            a.classDecl("Calculator", c -> {
                c.method("add", Arrays.asList("a", "b"), m -> {
                    m.get("a")
                     .get("b")
                     .add()
                     .ret();
                });
            });
        });
    }

    static void testInheritance() {
        SmartAssembler.runTest("testInheritance", a -> {
            a.classDecl("Parent", p -> {
                p.method("greet", Arrays.asList(), m -> {
                    m.const_(new Val.Str("Hello from parent")).print();
                });
            });

            a.classDecl("Child", "Parent", c -> {
                c.method("greet", Arrays.asList(), m -> {
                    m.const_(new Val.Str("Hello from child")).print();
                });
            });
        });
    }

    static void testSuperInvoke() {
        SmartAssembler.runTest("testSuperInvoke", a -> {
            a.classDecl("Parent", p -> {
                p.method("say", Arrays.asList(), m -> {
                    m.const_(new Val.Str("Parent says hi")).print();
                });
            });

            a.classDecl("Child", "Parent", c -> {
                c.method("say", Arrays.asList(), m -> {
                    m.const_(new Val.Str("Child says: ")).print();
                    m.const_(new Val.Str("Parent")).getSuper("say");
                    m.call(0);
                });
            });
        });
    }

    public static void runAllTests() {
        System.out.println("=== Running all SmartAssembler tests via LoxVM ===");

        List<Runnable> tests = Arrays.asList(
            LoxVM::testIfElse,
            LoxVM::testWhile,
            LoxVM::testOrShortCircuit,
            LoxVM::testClosureCapture,
            LoxVM::testFunctionReturn,
            LoxVM::testClassPropAccess,
            LoxVM::testMethodWithParams,
            LoxVM::testInheritance,
            LoxVM::testSuperInvoke
        );

        int run = 0;
        int passed = 0;
        int failed = 0;

        for (Runnable t : tests) {
            run++;
            System.out.println("\n-- Test #" + run + " --");
            try {
                t.run();
                System.out.println("PASSED");
                passed++;
            } catch (Throwable e) {
                System.out.println(" FAILED: " + e.getMessage());
                e.printStackTrace(System.out);
                failed++;
            }
        }

        System.out.println("\n=== Test Summary ===");
        System.out.println("Total: " + run);
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
        System.out.println("=== Finished all SmartAssembler tests ===");
    }

    public static void main(String[] args) {
        runAllTests();
    }
}
