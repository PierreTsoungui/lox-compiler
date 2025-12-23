
import java.util.*;


/**
 * VM und SmatAssembler 
 * 
 */

import java.util.*;
import java.util.function.*;
import java.util.function.Function;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.HashSet;

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

// === Die Virtual Machine ===
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

// === 3. Der Smart Assembler (Compiler) ===
class SmartAssembler {
    // Compiler State Stack (für verschachtelte Funktionen)
    private final Deque<CompilerState> compilers = new ArrayDeque<>();
    private final Set<String> globals = new HashSet<>();

    public SmartAssembler() {
        // Start mit global/script Compiler
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
        int jump = emitJumpIfFalse(); // short-circuit
        right.accept(this);
        patchJump(jump);
        return this;
    }

        // Logical OR
    public SmartAssembler or(Consumer<SmartAssembler> left, Consumer<SmartAssembler> right) {
        left.accept(this);                  // linken Ausdruck auswerten
        int jumpIfTrue = emitJumpIfFalse(); // invertiert: "wenn false, springe weiter"
    
        int jumpOverRight = emitJump();    // über rechten Ausdruck springen

        patchJump(jumpIfTrue);             // linke false springt hier zum rechten Ausdruck

        right.accept(this);                // rechten Ausdruck auswerten

         patchJump(jumpOverRight);          // danach über den rechten Ausdruck springen

         return this;
    }

      // --- Jump / Branch Helpers ---
    public int emitJumpIfFalse() {
        emit(new Op.JumpIfFalse(0)); // Platzhalter
        return compilers.peek().code.size() - 1; // Position merken
    }

    public int emitJump() {
        emit(new Op.Jump(0)); // Platzhalter
      return compilers.peek().code.size() - 1; // Position merken
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
        return compilers.peek().code.size(); // Speicher Position als Start
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
            // Lokale Variable prüfen auf Duplicate innerhalb des Blocks
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
            emit(new Op.DefGlobal(name));   // Erstmal definieren (VM merkt sich Slot)
           //emit(new Op.SetGlobal(name));   // Wert setzen
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

    // Funktionen & Closures
    public SmartAssembler fun(String name, int arity, Consumer<SmartAssembler> body) {
        // 1. Funktion im aktuellen Scope deklarieren
        var(name); 
        
        // 2. Neuen Compiler starten
        CompilerState current = compilers.peek();
        CompilerState fnCompiler = new CompilerState(current, FunctionType.FUNCTION);
        fnCompiler.functionName = name;
        fnCompiler.arity = arity;
        
        // Argumente als Locals registrieren (Slots 1..n)
        // Slot 0 ist reserviert (für closure selbst oder this)
        fnCompiler.addLocal(""); // Slot 0 reservieren
        for(int i=0; i<arity; i++) fnCompiler.addLocal("arg" + i); // Dummy names
        
        compilers.push(fnCompiler);
        
        // 3. Body kompilieren
        body.accept(this);
        emit(new Op.Const(null)); // Implizit return nil
        emit(new Op.Return());
        
        // 4. Compiler beenden & "CompiledFunction" bauen
        compilers.pop();
        CompiledFunction compiledFn = new CompiledFunction(name, arity, fnCompiler.code);
        
        // 5. CLOSURE OpCode im PARENT emitten
        // Dieser OpCode enthält Instruktionen, wie die Upvalues zu fangen sind
        current.code.add(new Op.Closure(compiledFn, fnCompiler.upvalues));
        
        return this;
    }


            // In SmartAssembler.java - NUR DIESE METHODE BEHALTEN:
    public SmartAssembler fun(String name, List<String> paramNames, Consumer<SmartAssembler> body) {
        int arity = paramNames.size();
             // 2. Neuen Compiler starten
        CompilerState current = compilers.peek();
        CompilerState fnCompiler = new CompilerState(current, FunctionType.FUNCTION);
        fnCompiler.functionName = name;
        fnCompiler.arity = arity;
    
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
    var(name);

    // 1. Klasse erzeugen
    emit(new Op.Class(name));

    // 2. Scope für `super` falls nötig
    if (superClassName != null) {
        beginScope();
        get(superClassName);
        var("super");
    }

    // 3. Methoden definieren
    body.accept(this);

    // 4. super-Scope schließen
    if (superClassName != null) {
        endScope();
    }

    // 5. Klasse in globale Variable speichern
    set(name);

    return this;
}

    public SmartAssembler getSuper(String method) {
        get("this");   // Receiver
        get("super");  // Superklasse (Upvalue!)
        emit(new Op.GetSuper(method));
        return this;
    }

    
    public SmartAssembler method(String name, int arity, Consumer<SmartAssembler> body) {
        // Ähnlich wie fun, aber FunctionType.METHOD und Slot 0 = "this"
        CompilerState current = compilers.peek();
        CompilerState methodCompiler = new CompilerState(current, FunctionType.METHOD);
        methodCompiler.functionName = name;
        methodCompiler.arity = arity;
        methodCompiler.addLocal("this"); // Slot 0 ist this!
        for(int i=0; i<arity; i++) methodCompiler.addLocal("arg"+i);
        
        compilers.push(methodCompiler);
        body.accept(this);
        if (!(methodCompiler.code.getLast() instanceof Op.Return)) {
             emit(new Op.GetLocal(0)); // this zurückgeben (wichtig für init!)
             emit(new Op.Return());
        }
        compilers.pop();
        
        CompiledFunction fn = new CompiledFunction(name, arity, methodCompiler.code);
        // Method OpCode: Binde Closure an Klasse (Stack Top)
        current.code.add(new Op.Closure(fn, methodCompiler.upvalues));
        emit(new Op.Method(name));
        return this;
    }
    
     public SmartAssembler method(
        String name,
        List<String> paramNames,
        Consumer<SmartAssembler> body
    ) 
    {
        CompilerState current = compilers.peek();
        CompilerState methodCompiler =
        new CompilerState(current, FunctionType.METHOD);

        methodCompiler.functionName = name;
        methodCompiler.arity = paramNames.size();

         // Slot 0 = this
        methodCompiler.addLocal("this");

        // Slots 1..n = echte Parameternamen
        for (String param : paramNames) {
             methodCompiler.addLocal(param);
        }

        compilers.push(methodCompiler);
        body.accept(this);

        // implizites return this für init
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
    public SmartAssembler getProp(String p) { emit(new Op.GetProp(p)); return this; }
    public SmartAssembler setProp(String p) { emit(new Op.SetProp(p)); return this; }

    // --- Internals & Resolution Logic ---

    private void beginScope() { compilers.peek().scopeDepth++; }
    
    private void endScope() {
        CompilerState c = compilers.peek();
        c.scopeDepth--;
        // Locals poppen & Upvalues schließen
        while (!c.locals.isEmpty() && c.locals.getLast().depth > c.scopeDepth) {
            emit(new Op.CloseUpval()); // Wichtig für Closures!
            emit(new Op.Pop());        // Wichtig für Stack!
            c.locals.removeLast();
        }
    }

    private void emit(Op op) { compilers.peek().code.add(op); }

    // Die Kern-Logik: Variable auflösen
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
          boolean existsGlobally = globals.contains(name); // du musst globale Variablen tracken
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

    // Das ist der kniffligste Teil von `clox` in Java nachgebaut
    private int resolveUpvalue(CompilerState c, String name) {
        if (c.enclosing == null) return -1;

        // Suche im Parent (ist es dort lokal?)
        int local = resolveLocal(c.enclosing, name);
        if (local != -1) {
            // Gefunden! Im Parent ist es lokal. Wir capturen es.
            c.enclosing.locals.get(local).isCaptured = true;
            return addUpvalue(c, local, true);
        }

        // Suche im Parent (ist es dort schon ein Upvalue?)
        int upvalue = resolveUpvalue(c.enclosing, name);
        if (upvalue != -1) {
            return addUpvalue(c, upvalue, false);
        }

        return -1;
    }

    private int addUpvalue(CompilerState c, int index, boolean isLocal) {
        // Upvalue Liste checken (Deduplication)
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
        String name; int depth; boolean isCaptured;
        Local(String n, int d) { name=n; depth=d; }
    }

    static class CompilerState {
        CompilerState enclosing;
        List<Op> code = new ArrayList<>();
        List<Local> locals = new ArrayList<>();
        List<UpvalueDescriptor> upvalues = new ArrayList<>(); // Die "Inputs" für das Closure
        int scopeDepth = 0;
        FunctionType type;
        String functionName = "";
        int arity = 0;

        CompilerState(CompilerState enc, FunctionType t) { enclosing = enc; type = t; }
        
        void addLocal(String name) {
            locals.add(new Local(name, scopeDepth));
        }
    }

// --- Hilfsmethoden für Tests ---
   static void runTest(String testName, Consumer<SmartAssembler> build) {
        System.out.println("=== Test: " + testName + " ===");
        SmartAssembler a = new SmartAssembler();
        CompiledFunction fn = buildAsm(a, build);
        for(Op op: fn.code()){
            System.out.println(op);
        }
        System.out.println();
    }

    static CompiledFunction buildAsm(SmartAssembler a, Consumer<SmartAssembler> build) {
         build.accept(a);
        return a.compile();
    }

    static void printBytecode(CompiledFunction fn) {
        for (Op op : fn.code()) {
            System.out.println(op);
        }
    }

    // ---Testfälle ---

    static void test1() {
     runTest("test1", a -> {
        a.scope(s -> {
            s.const_(new Val.Num(1)).var("x");
            s.const_(new Val.Num(2)).var("x"); // sollte Fehler auslösen
        });
    });
    }

    static void test2() {
        runTest("test2", a -> {
            a.scope(s -> {
                s.const_(new Val.Num(5)).var("y");
                s.scope(s2 -> {
                    s2.const_(new Val.Num(6)).var("y"); // erlaubt
                });
            });
        });
    }

    static void testIfElse() {
         runTest("testIfElse", a -> {
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

    static void testOr() {
        runTest("testOr", a -> {
            a.or(
                s -> s.const_(new Val.Bool(false)),
                s -> s.const_(new Val.Bool(true))
            ).print();
        });
    }

    static void testAnd() {
        runTest("testAnd", a -> {
            a.and(
                s -> s.const_(new Val.Bool(true)),
                 s -> s.const_(new Val.Bool(false))
            ).print();
        });
    }

    static void testWhile() {
        runTest("testWhile", a -> {
            a.const_(new Val.Num(0)).var("i");
            int loopStart = a.compilers.peek().code.size();
            a.get("i").const_(new Val.Num(3)).lt();
            int exitJump = a.emitJumpIfFalse();
            a.get("i").print();
            a.get("i").const_(new Val.Num(1)).add().set("i");
            a.emitLoop(loopStart);
            a.patchJump(exitJump);
        });
    }

    static void testClass() {
        runTest("testClass", a -> {
            a.classDecl("Foo", c -> {
                c.method("bar", 0, m -> {
                    m.const_(new Val.Str("Foo.bar")).print();
                });
            });
        });
    }

    static void testOrShortCircuit() {
        runTest("testOrShortCircuit", a -> {
            a.or(
                s -> s.true_(),
                s -> s.const_(new Val.Str("BAD")).print() // darf NICHT ausgeführt werden
            );
        });
    }

   static void testVarSelfAssign() {
    runTest("var a = a;", a -> {
        // Lox: var a = a; 
        // 1. Versuche RHS auszuwerten (a) → existiert noch nicht, sollte nil sein
        a.get("a");        // greift auf 'a', das noch nicht existiert → sollte global oder nil
        // 2. Deklariere Variable a und setze Wert
        a.var("a");        
    });
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
                exprToAsm(un.right());
                switch (un.operator().type()) {
                    case MINUS -> {
                        // -x wird zu 0 - x
                        asm.const_(new Val.Num(0));
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

    // --- Testmethoden ---
    static void testLiteral() {
        System.out.println("=== Test Literals ===");
        Compiler compiler = new Compiler();
        String source = "1; true; false; nil; \"hello\";";
        CompiledFunction fn = compiler.compile(source);
        for (Op op : fn.code()) {
            System.out.println(op);
        }
        System.out.println();
    }
    
    static void testBinary() {
        System.out.println("=== Test Binary ===");
        Compiler compiler = new Compiler();
        String source = "1 + 2; 5 - 3; 4 * 2; 8 / 2; 3 > 1; 2 < 4; 5 == 5; 6 != 7;";
        CompiledFunction fn = compiler.compile(source);
        for (Op op : fn.code()) {
            System.out.println(op);
        }
        System.out.println();
    }

    static void testLogical() {
        System.out.println("=== Test Logical ===");
        Compiler compiler = new Compiler();
        String source = "true and false; false or true;";
        CompiledFunction fn = compiler.compile(source);
        for (Op op : fn.code()) {
            System.out.println(op);
        }
        System.out.println();
    }

    static void testVariable() {
        System.out.println("=== Test Variable ===");
        Compiler compiler = new Compiler();
        String source = "var a = 10; a = 20; print a;";
        CompiledFunction fn = compiler.compile(source);
        for (Op op : fn.code()) {
            System.out.println(op);
        }
        System.out.println();
         System.out.println("=== Run Unary ===");
         
           VM vm = new VM();
        vm.interpret(fn);
    
    }

    static void testUnary() {
        System.out.println("=== Test Unary ===");
        Compiler compiler = new Compiler();
        String source = "-5; !true;";
        CompiledFunction fn = compiler.compile(source);
        for (Op op : fn.code()) {
            System.out.println(op);
        }
        System.out.println();

    }
    
    static void testIfElse() {
        System.out.println("=== Test If-Else ===");
        Compiler compiler = new Compiler();
        String source = """
            if (true) {
                print "yes";
            } else {
                print "no";
            }
            """;
        CompiledFunction fn = compiler.compile(source);
        for (Op op : fn.code()) {
            System.out.println(op);
        }
        System.out.println();
    }
    
    static void testWhile() {
        System.out.println("=== Test While ===");
        Compiler compiler = new Compiler();
        String source = """
            var i = 0;
            while (i < 3) {
                print i;
                i = i + 1;
            }
            """;
        CompiledFunction fn = compiler.compile(source);
        for (Op op : fn.code()) {
            System.out.println(op);
        }
        System.out.println();
    }
    
    static void testFunction() {
        System.out.println("=== Test Function ===");
        Compiler compiler = new Compiler();
        String source = """
            fun add(a, b) {
                return a + b;
            }
            print add(1, 2);
            """;
        CompiledFunction fn = compiler.compile(source);
        for (Op op : fn.code()) {
            System.out.println(op);
        }
         System.out.println("\n=== Execution ===");
        VM vm = new VM();
        vm.interpret(fn);
        System.out.println();
    }
   
    static void testParserFunctionCall() {
    System.out.println("=== Test Parser Function Call ===");
    String source = "add(1, 2);";
    List<Stmt> stmts = ParserMain.parseProgram(source);
    System.out.println("Parsed statements: " + stmts.size());
    if (!stmts.isEmpty()) {
        Stmt stmt = stmts.get(0);
        System.out.println("Statement type: " + stmt.getClass());
        if (stmt instanceof Stmt.Expression expr) {
            Expr expression = expr.expr();
            System.out.println("Expression type: " + expression.getClass());
            if (expression instanceof Expr.Call call) {
                System.out.println("Callee: " + call.callee());
                System.out.println("Arguments: " + call.arguments().size());
                for (int i = 0; i < call.arguments().size(); i++) {
                    System.out.println("  Arg " + i + ": " + call.arguments().get(i));
                }
            }
        }
    }
    System.out.println();
}
    static void     testClass() {
        System.out.println("=== Test Class ===");
        Compiler compiler = new Compiler();
        String source = """
           class Animal {
            init(name) {
                this.name = name;
            }

            speak() {
                print "Animal speaks";
            }
        }

        class Dog < Animal {
            init(name, age) {
                super.init(name);
                this.age = age;
            }

            bark() {
                print this.name + " says woof";
            }

            getAge() {
                return this.age;
            }
        }
            """;
        CompiledFunction fn = compiler.compile(source);
        for (Op op : fn.code()) {
            System.out.println(op);
        }
        System.out.println();
    }
    
    static void testScope() {
        System.out.println("=== Test Scope ===");
        Compiler compiler = new Compiler();
        String source = """
            var x = "global";
            {
                var x = "local";
                print x;
            }
            print x;
            """;
        CompiledFunction fn = compiler.compile(source);
        for (Op op : fn.code()) {
            System.out.println(op);
        }
        System.out.println();
    }

    // Haupttestmethode
    public static void main(String[] args) {
        System.out.println("========== TESTING COMPILER ==========\n");
        
        testLiteral();
        testBinary();
        testLogical();
        testVariable();
        testUnary();
        testIfElse();
        testWhile();
        testFunction();
        testClass();
        testScope();
        
        System.out.println("========== ALL TESTS COMPLETED ==========");
        
        // Beispiel: Kompiliere und führe Code aus
        System.out.println("\n=== Beispiel: Hello World ===");
        Compiler compiler = new Compiler();
        String helloWorld = """
            print "Hello, World!";
            var x = 10 + 5;
            print "10 + 5 = " + x;
            """;
        
        CompiledFunction fn = compiler.compile(helloWorld);
        System.out.println("Bytecode:");
        for (Op op : fn.code()) {
            System.out.println(op);
        }
        
        System.out.println("\n=== Ausführung mit VM ===");
        VM vm = new VM();
        vm.interpret(fn);
    }
    public static void testFunctionSimple() {
         System.out.println("=== Test Function Simple (no print) ===");
         Compiler compiler = new Compiler();
         String source = """
            fun add(a, b) {
                return a + b;
            }
             add(1, 2);  // Nur aufrufen, nicht drucken
             """;
    
        CompiledFunction fn = compiler.compile(source);
    
        System.out.println("Bytecode:");
        for (Op op : fn.code()) {
             System.out.println(op);
        }
    
        System.out.println("\n=== Execution ===");
         VM vm = new VM();
        vm.interpret(fn);
    }
    public static void testFunctionReturn() {
        System.out.println("=== Test Function Return ===");
        Compiler compiler = new Compiler();
        String source = """
            fun test() {
             return 42;
            }
             print test();
            """;
        
        CompiledFunction fn = compiler.compile(source);
    
        System.out.println("Bytecode:");
        for (Op op : fn.code()) {
         System.out.println(op);
        }
    
         System.out.println("\n=== Execution ===");
        VM vm = new VM();
        vm.interpret(fn);
    }
       static void testParser() {
    String source = """
        class Animal {
            init(name) {
                this.name = name;
            }

            speak() {
                print "Animal speaks";
            }
        }

        class Dog < Animal {
            init(name, age) {
                super.init(name);
                this.age = age;
            }

            bark() {
                print this.name + " says woof";
            }

            getAge() {
                return this.age;
            }
        }
        """;

    ParserMain parser = ParserMain.fromSource(source);

    // AST als Graphviz-DOT ausgeben
    parser.printDot();
}

}