import java.util.*;
import java.util.function.*;
import java.util.function.Function;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
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
    
    // Aufrufe
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
        CallFrame(Val.Obj.Closure c, int offset) { closure = c; slotOffset = offset; }
    }

    public void interpret(CompiledFunction script) {
        // Main Script Frame
        Val.Obj.Closure scriptClosure = new Val.Obj.Closure(script, new Upvalue[0]);
        pushFrame(new CallFrame(scriptClosure, 0));
        
        try {
            run();
        } catch(Exception e) { e.printStackTrace(); }
    }

    private void run() {
        // Pair as a helper class
        record Pair(Val left, Val right) {}
        while (frame.ip < frame.closure.fn.code().size()) {
            Op op = frame.closure.fn.code().get(frame.ip++);
            
            // System.out.println("Stack: " + stack + " | Op: " + op); // Debug

            switch (op) {
                case Op.Const(Val v) -> stack.add(v);
                case Op.Nil() -> stack.add(new Val.Nil()); // ToDo: Singleton?
                case Op.True() -> stack.add(new Val.Bool(true));
                case Op.False() -> stack.add(new Val.Bool(false));
                case Op.Pop() -> stack.removeLast();
                case Op.Print() -> System.out.println(stack.removeLast()); // remove korrekt?

                // case Op.Nil -> stack.add(null);
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
                case Op.Neg() -> {
                    stack.add(switch(stack.removeLast()) {
                        case Val.Num n -> new Val.Num(-n.value());
                        default -> throw new RuntimeException("Operand must be a number.");
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
                        return;
                    } 
                    // Für alle anderen Typen (Strings, Objekte, Bools, Nil) 
                    // ist die Java-Standard-Gleichheit (equals) korrekt.
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
                // TODO: Muss ich auf dieser Ebene tatsächlich mit Indizes arbeiten? Ist auch das zu bitnah gedacht?
                //       Könnte man also Locals vom Stack unterscheiden?
                case Op.GetLocal(int slot) -> stack.add(stack.get(frame.slotOffset + slot)); // ToDo: Locals und Stack unterscheiden?
                case Op.SetLocal(int slot) -> stack.set(frame.slotOffset + slot, stack.getLast());
                
                case Op.DefGlobal(var n) -> globals.put(n, stack.removeLast());
                case Op.SetGlobal(String name) -> {
                    // Wir entfernen den Wert nicht, da das Ergebnis der Zuweisung
                    // der zugewiesene Wert selbst ist (für Ausdrücke wie a = b = 1).
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
                    closeUpvalues(stack.size() - 1); // Top of Stack wird geschlossen
                    // Hinweis: In echter VM wird stack pointer genutzt.
                    // Wir tun so, als ob die Variable, die gepoppt werden soll, geschlossen wird.
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
                        if (desc.isLocal()) { // Capture Variable from CURRENT Stack Frame
                            captured[i] = captureUpvalue(frame.slotOffset + desc.index());
                        } else { // Capture Upvalue from CURRENT Closure (Pass-through)
                            captured[i] = frame.closure.upvalues[desc.index()];
                        }
                    }
                    stack.add(new Val.Obj.Closure(fn, captured));
                }
                
                case Op.Call(int argc) -> {
                    Val callee = stack.get(stack.size() - 1 - argc);
                    callValue(callee, argc);
                }
                
                case Op.Return() -> {
                    Val result = stack.removeLast();
                    closeUpvalues(frame.slotOffset); // Upvalues schließen
                    // Merke dir, wo der Frame begann (dort liegt der Callee/Funktion)
                    int frameStart = frame.slotOffset;
                    frames.removeLast();
                    if (frames.isEmpty()) return;
                    frame = frames.getLast();
                    // Stack bereinigen: Alles ab frameStart (Argumente + Callee) entfernen
                    while (stack.size() > frameStart) {
                        stack.removeLast();
                    }
                    stack.add(result); // Ergebnis auf den Stack des Aufrufers legen
                }
                
                // --- OOP ---
                case Op.Class(var name) -> stack.add(new Val.Obj.Klass(name));
                case Op.Inherit() -> {
                    // Erwarteter Stack: [ ..., SuperKlasse, SubKlasse ]
                    // 1. Subklasse entfernen (sie wird nicht mehr gebraucht)
                    Val subVal = stack.removeLast(); 
                    // 2. Superklasse ansehen (Muss für 'super'-Scope liegen bleiben!)
                    Val supVal = stack.getLast(); 
                    // 3. Typ-Prüfung und Kopiervorgang                    
                    if (subVal instanceof Val.Obj.Klass sub && 
                        supVal instanceof Val.Obj.Klass sup) {
                        // Methoden kopieren
                        sub.methods.putAll(sup.methods);
                        // KEIN Push(Nil). Wir hinterlassen den Stack sauber: [ ..., SuperKlasse ]
                        // Der Compiler/Assembler ist dafür verantwortlich, später ein POP zu senden.
                    } else {
                        throw new RuntimeException("Inherit must take two classes.");
                    }
                }
                case Op.Method(var name) -> {
                    Val.Obj.Closure method = (Val.Obj.Closure) stack.removeLast();
                    Val.Obj.Klass klass = (Val.Obj.Klass) stack.getLast(); // Peek
                    klass.methods.put(name, method);
                }

                case Op.Invoke(String name, int args) -> {
                    // 1. Den Receiver (das 'this') finden.
                    // Der Stack sieht so aus: [ Receiver, Arg1, Arg2, ... ArgN ]
                    // Der Receiver liegt also genau 'args' Plätze unter der Spitze.
                    int receiverIdx = stack.size() - 1 - args;
                    Val receiver = stack.get(receiverIdx);

                    // 2. Typprüfung: Nur Instanzen haben Methoden.
                    if (receiver instanceof Val.Obj.Instance instance) {
                        // A. FELD-CHECK (Fields shadow Methods!)
                        // Lox-Semantik: Wenn die Instanz ein Feld mit dem Namen hat, 
                        // hat dieses Vorrang vor einer gleichnamigen Methode der Klasse.
                        // Das passiert z.B., wenn man eine Funktion in einer Eigenschaft speichert:
                        // dog.callback = someFunc; dog.callback();
                        if (instance.fields.containsKey(name)) {
                            Val value = instance.fields.get(name);
                            // Wir ersetzen den Receiver im Stack durch den Wert des Feldes.
                            // Stack vorher: [ Receiver, Args... ]
                            // Stack nachher: [ FieldValue, Args... ]
                            stack.set(receiverIdx, value);
                            // Jetzt führen wir einen ganz normalen Call aus (wie bei Op.Call)
                            callValue(value, args);
                        }
                        // B. METHODEN-LOOKUP (Der optimierte Pfad)
                        else if (instance.klass.methods.containsKey(name)) {
                            Val.Obj.Closure method = instance.klass.methods.get(name);
                            // Arity Check
                            if (args != method.fn.arity()) {
                                throw new RuntimeException("Expected " + method.fn.arity() + 
                                                        " arguments but got " + args + ".");
                            }
                            // Hier ist der Trick: Wir müssen nichts am Stack ändern!
                            // Der Receiver (die Instanz) liegt bereits an 'stack[receiverIdx]'.
                            // Wenn wir jetzt einen neuen CallFrame für die Methode pushen,
                            // wird Slot 0 dieses neuen Frames genau auf diesen Index zeigen.
                            // Damit ist 'this' automatisch korrekt gebunden.
                            callClosure(method, args);
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
                    // 1. Die Superklasse vom Stack nehmen (liegt oben)
                    Val superVal = stack.removeLast();
                    // 2. Typprüfung
                    if (superVal instanceof Val.Obj.Klass superKlass) {
                        // 3. Methode in der Superklasse suchen
                        // Der Witz bei 'super': Wir suchen die Methode in der Klasse des PARENTs,
                        // führen sie aber auf der Instanz des CHILDs aus.
                        Val.Obj.Closure method = superKlass.methods.get(name);
                        if (method == null) {
                            throw new RuntimeException("Undefined property '" + name + "' in superclass.");
                        }
                        // 4. Aufruf durchführen
                        // Der Stack sieht jetzt so aus: [ Receiver, Arg1, ... ArgN ]
                        // (Weil wir SuperClass gepoppt haben).
                        // Das ist exakt das Layout, das 'callClosure' erwartet!
                        // Der Receiver liegt an 'stack.size() - 1 - args'.
                        // Der neue CallFrame wird Slot 0 auf diesen Receiver binden.
                        // Somit ist 'this' innerhalb der Super-Methode korrekt die ursprüngliche Instanz.
                        callClosure(method, args);
                    } else {
                        throw new RuntimeException("Super operand must be a class.");
                    }
                }

                case Op.GetProp(var name) -> {
                    Object obj = stack.removeLast();
                    if (obj instanceof Val.Obj.Instance inst) {
                        if (inst.fields.containsKey(name)) {
                            stack.add(inst.fields.get(name));
                            return;
                        } else if (inst.klass.methods.containsKey(name)) {
                            // Bind Method
                            Val.Obj.Closure meth = inst.klass.methods.get(name);
                            // Simples Binding: Wir packen 'this' (inst) in Slot 0? 
                            // Nein, clox bindet "this" als UPVALUE oder Slot 0 im neuen Frame.
                            // Hier nutzen wir den Lox-Weg: BoundMethod ist ein neues Objekt.
                            // Vereinfachung für Hybrid: Wir tricksen.
                            // Wir rufen Methode auf und setzen 'this' manuell in Slot 0 beim Call?
                            // Besser: Bound Methods fehlen in meiner Obj-Struktur oben, 
                            // aber wir können es simulieren, indem wir die Methode pushen
                            // und beim Call merken: "Oh, das ist eine Methode".
                            stack.add(new Val.Obj.BoundMethod(inst, meth));
                            return;
                        } else throw new RuntimeException("Property not found: " + name);
                    } else throw new RuntimeException("There is no instance.");
                }
                case Op.SetProp(var name) -> {
                    // 1. Zuzuweisenden Wert vom Stack nehmen
                    Val value = stack.removeLast(); 
                    // 2. Empfänger-Objekt (Instance) vom Stack nehmen
                    Val receiver = stack.removeLast(); 
                    // 3. Typprüfung und Zuweisung
                    if (receiver instanceof Val.Obj.Instance instance) {
                        // Die LoxValue.Obj.Instance Klasse muss eine 'set(String name, LoxValue value)'
                        // Methode implementieren, um das Feld zu speichern.
                        instance.set(name, value); 
                        // 4. Den zugewiesenen Wert zurück auf den Stack legen
                        // Wichtig: In Lox evaluieren Zuweisungen zum zugewiesenen Wert (z.B. a = b = 5)
                        stack.add(value);
                        return;
                    }
                    throw new RuntimeException("Only instances have fields that can be set.");
                }
                case Op.GetSuper(var name) -> {
                    // 1. Die Superklasse vom Stack nehmen (liegt oben).
                    // Sie wurde meist über einen Upvalue-Lookup ('super') geladen.
                    Val superVal = stack.removeLast();
                    // 2. Den Receiver ('this') vom Stack nehmen (liegt darunter).
                    Val receiverVal = stack.removeLast();
                    // 3. Typprüfung
                    if (superVal instanceof Val.Obj.Klass superKlass && 
                        receiverVal instanceof Val.Obj.Instance receiver) {
                        // 4. Methode in der Superklasse suchen
                        Val.Obj.Closure method = superKlass.methods.get(name);
                        if (method == null) {
                            throw new RuntimeException("Undefined property '" + name + "' in superclass.");
                        }
                        // 5. Binden (Binding)
                        // Wir erstellen ein 'BoundMethod'-Objekt.
                        // Trick: Wir nehmen die Methode aus der SUPER-Klasse,
                        // binden sie aber an die Instanz der SUB-Klasse (receiver).
                        stack.add(new Val.Obj.BoundMethod(receiver, method));
                    } else {
                        throw new RuntimeException("Super operand must be a class and receiver an instance.");
                    }
                }
            }
        }
    }
    
    // --- Helper ---
    
    private void callValue(Val callee, int argc) {
        switch (callee) {
            case Val.Obj.BoundMethod bm -> {
                // Stack korrigieren: Das 'this' muss in Slot 0 des neuen Frames
                // Im aktuellen Stack liegen [Arg0, Arg1].
                // Slot 0 des neuen Frames ist bm.receiver.
                stack.set(stack.size() - 1 - argc, bm.receiver); // Ersetze "BoundMethod" Platzhalter mit "this"
                callClosure(bm.method, argc);
            }
            case Val.Obj.Closure cl -> callClosure(cl, argc);
            case Val.Obj.Klass k -> {
                // 1. Instanz erzeugen
                Val.Obj.Instance instance = new Val.Obj.Instance(k);
            
                // 2. Stack-Manipulation: [Klasse, Arg1, Arg2] -> [Instanz, Arg1, Arg2]
                // Wir ersetzen die Klasse (die als "Callee" fungierte) durch die neue Instanz.
                // Diese Instanz wird automatisch zu 'this' im folgenden init-Aufruf.
                int instanceSlot = stack.size() - 1 - argc;
                stack.set(instanceSlot, instance);
                
                // 3. Initializer ('init') suchen und aufrufen
                if (k.methods.containsKey("init")) {
                    Val.Obj.Closure init = k.methods.get("init");
                    // Führt den Aufruf aus. Da die Instanz in Slot 0 liegt, 
                    // funktioniert der Zugriff auf 'this' im init-Body korrekt.
                    callClosure(init, argc);
                } else if (argc != 0) {
                    // Wenn kein 'init' da ist, darf man keine Argumente übergeben.
                    throw new RuntimeException("Expected 0 arguments but got " + argc + ".");
                }
            }
            case Val.Obj.Native nat -> {
                if (argc != nat.arity) throw new RuntimeException("Wrong arity.");
                List<Val> args = new ArrayList<>();
                for (int i = 0; i < argc; i++) {
                    // Stack: [Func, Arg1, Arg2] -> wir poppen von hinten
                    args.add(0, stack.removeLast());
                }
                // Funktion poppen (Native Functions brauchen keinen Stackframe)
                stack.removeLast();      
                Val result = nat.logic.apply(args);
                stack.add(result);
            }
            default -> throw new RuntimeException(callee + " is not a callable object.");
        }
    }

    private void callClosure(Val.Obj.Closure cl, int argc) {
        if (argc != cl.fn.arity()) throw new RuntimeException("Wrong arity");
        pushFrame(new CallFrame(cl, stack.size() - 1 - argc)); // -1 für Slot 0 (this/func)
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
/**
 * Assembler für Lox-Bytecode (Op-basierte VM)
 *
 * Wandelt eine Textdarstellung von Opcodes mit Labels
 * in eine CompiledFunction (List<Op>) um.
 */
class Assembler {

    //Hauptmethode: assemble ---
    public CompiledFunction assemble(String source) {
        List<String> lines = preprocess(source);          
        Map<String, Integer> labels = collectLabels(lines); 
        List<Op> code = generateOps(lines, labels);  
        return new CompiledFunction("<script>", 0, code);
    }

    // --- Schritt 1: Lines vorbereiten ---
    private List<String> preprocess(String source) {
        return source.lines()
                .map(String::trim)
                .filter(l -> !l.isEmpty() && !l.startsWith("//"))
                .collect(Collectors.toList());
    }

    // --- Schritt 2: Labels sammeln ---
    private Map<String, Integer> collectLabels(List<String> lines) {
        Map<String, Integer> labels = new HashMap<>();
        int ip = 0;
        for (String line : lines) {
            if (line.endsWith(":")) {
                String label = line.substring(0, line.length() - 1);
                labels.put(label, ip);
            } else {
                ip++;
            }
        }
        return labels;
    }

    // --- Schritt 3: Ops erzeugen ---
    private List<Op> generateOps(List<String> lines, Map<String, Integer> labels) {
        List<Op> code = new ArrayList<>();
        int pc = 0;

        for (String line : lines) {
            if (line.endsWith(":")) continue;

            String[] parts = line.split("\\s+");
            String op = parts[0];

            switch (op) {
                // Konstanten & Literale
                case "OP_CONSTANT" -> code.add(new Op.Const(new Val.Num(Double.parseDouble(parts[1]))));
                case "OP_NIL" -> code.add(new Op.Nil());
                case "OP_TRUE" -> code.add(new Op.True());
                case "OP_FALSE" -> code.add(new Op.False());

                // Arithmetik
                case "OP_ADD" -> code.add(new Op.Add());
                case "OP_SUBTRACT" -> code.add(new Op.Sub());
                case "OP_MULTIPLY" -> code.add(new Op.Mul());
                case "OP_DIVIDE" -> code.add(new Op.Div());
                case "OP_NEGATE" -> code.add(new Op.Neg());

                // Vergleich & Logik
                case "OP_EQUAL" -> code.add(new Op.Equal());
                case "OP_GREATER" -> code.add(new Op.Greater());
                case "OP_LESS" -> code.add(new Op.Less());
                case "OP_NOT" -> code.add(new Op.Not());

                // Stack & Kontrolle
                case "OP_POP" -> code.add(new Op.Pop());
                case "OP_PRINT" -> code.add(new Op.Print());
                case "OP_RETURN" -> code.add(new Op.Return());

                // Variablen
                case "OP_DEFINE_GLOBAL" -> code.add(new Op.DefGlobal(parts[1]));
                case "OP_GET_GLOBAL" -> code.add(new Op.GetGlobal(parts[1]));
                case "OP_SET_GLOBAL" -> code.add(new Op.SetGlobal(parts[1]));
                case "OP_GET_LOCAL" -> code.add(new Op.GetLocal(Integer.parseInt(parts[1])));
                case "OP_SET_LOCAL" -> code.add(new Op.SetLocal(Integer.parseInt(parts[1])));

                // Flow
                case "OP_JUMP" -> code.add(new Op.Jump(labels.get(parts[1]) - pc - 1));
                case "OP_JUMP_IF_FALSE" -> code.add(new Op.JumpIfFalse(labels.get(parts[1]) - pc - 1));
                case "OP_LOOP" -> code.add(new Op.Loop(pc - labels.get(parts[1]) + 1));

                // OOP
                case "OP_CLASS" -> code.add(new Op.Class(parts[1]));
                case "OP_METHOD" -> code.add(new Op.Method(parts[1]));
                case "OP_INHERIT" -> code.add(new Op.Inherit());
                case "OP_GET_PROPERTY" -> code.add(new Op.GetProp(parts[1]));
                case "OP_SET_PROPERTY" -> code.add(new Op.SetProp(parts[1]));
                case "OP_INVOKE" -> code.add(new Op.Invoke(parts[1], Integer.parseInt(parts[2])));
                case "OP_SUPER_INVOKE" -> code.add(new Op.SuperInvoke(parts[1], Integer.parseInt(parts[2])));
                case "OP_GET_SUPER" -> code.add(new Op.GetSuper(parts[1]));

                default -> throw new RuntimeException("Unknown opcode: " + op);
            }
            pc++;
        }

        return code;
    }



    /**
     * Mini-Test für JShell / Debug
     */
    // --- Testmethode ---
    public void testAssembler() {
        String program = """
            OP_CONSTANT 1
            OP_CONSTANT 2
            OP_ADD
            OP_PRINT
        """;

        CompiledFunction fn = assemble(program);
        new VM().interpret(fn); // Erwartet: 3
    }

    // Run a file with text code
    void runAssembler( String file) throws IOException {
        String program = Files.readString(Path.of(file));
         CompiledFunction fn = assemble(program);
         new VM().interpret(fn);

    }


}