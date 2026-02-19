import java.util.*;
import java.util.function.*;
// --- SmartAssembler: Hilfsklasse zum Bytecode-Generieren ---
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

