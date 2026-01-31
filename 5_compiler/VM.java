import java.util.*;

// Uses shared Op/Val/CompiledFunction/UpvalueDescriptor/Upvalue types from Compiler.java
// === Die Virtual Machine ===
public class VM {
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
                case Op.Nil() -> stack.add(new Val.Nil());
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
                    if (fst instanceof Val.Num n1 && snd instanceof Val.Num n2) {
                        stack.add(new Val.Bool(n1.value() == n2.value()));
                        return;
                    }
                    stack.add(new Val.Bool(fst.equals(snd)));
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
                case Op.JumpIfFalse(int off) -> { 
                    Val condition = stack.removeLast(); // WICHTIG: Condition vom Stack nehmen
                    if (!condition.isTruthy()) frame.ip += off; 
                }
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
                            return;
                        }
                        // B. METHODEN-LOOKUP
                        else if (instance.klass.methods.containsKey(name)) {
                            Val.Obj.Closure method = instance.klass.methods.get(name);
                            if (args != method.fn.arity()) {
                                throw new RuntimeException("Expected " + method.fn.arity() + 
                                                        " arguments but got " + args + ".");
                            }
                            callClosure(method, args);
                            return;
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

    // --- Testmethoden ---
    void testSimpleMath() {
        System.out.println("=== Test 1: Math (1 + 2) ===");
        
        List<Op> ops = new ArrayList<>();
        ops.add(new Op.Const(new Val.Num(1.0)));
        ops.add(new Op.Const(new Val.Num(2.0)));
        ops.add(new Op.Add());
        ops.add(new Op.Print());
        ops.add(new Op.Nil());
        ops.add(new Op.Return());
        
        runScript(ops);
    }

    void testGlobalsAndStrings() {
        System.out.println("\n=== Test 2: Strings & Globals ===");

        List<Op> ops = new ArrayList<>();
        ops.add(new Op.Const(new Val.Str("Hallo ")));
        ops.add(new Op.DefGlobal("greeting"));
        ops.add(new Op.GetGlobal("greeting"));
        ops.add(new Op.Const(new Val.Str("Welt")));
        ops.add(new Op.Add());
        ops.add(new Op.Print());
        ops.add(new Op.Nil());
        ops.add(new Op.Return());

        runScript(ops);
    }

    void testControlFlow() {
        System.out.println("\n=== Test 3: Control Flow (Jump) ===");
        
        List<Op> ops = new ArrayList<>();
        ops.add(new Op.Const(new Val.Str("Start")));
        ops.add(new Op.Print());
        ops.add(new Op.False()); 
        ops.add(new Op.JumpIfFalse(2));
        ops.add(new Op.Const(new Val.Str("Skip")));
        ops.add(new Op.Print());
        ops.add(new Op.Const(new Val.Str("Ende")));
        ops.add(new Op.Print());
        ops.add(new Op.Nil());
        ops.add(new Op.Return());

        runScript(ops);
    }

    // --- Helper ---
    public void runScript(List<Op> code) {
        CompiledFunction func = new CompiledFunction("script", 0, code);
        new VM().interpret(func);
    }
    
    // Hauptmethode für Tests
    public static void main() {
        VM vm = new VM();
        vm.testSimpleMath();
        vm.testGlobalsAndStrings();
        vm.testControlFlow();
    }
}