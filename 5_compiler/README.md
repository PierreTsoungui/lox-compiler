# 5_compiler – Bytecode-Compiler
Diese Dokumentation beschreibt den **Compiler-Teil (Aufgabe 5)**.
Im diesem Ordner befinden sich alle Bausteine, die aus einem Lox-Quelltext **Bytecode** erzeugen und diesen anschließend auf einer **stackbasierten VM** ausführbar machen.

Hinweis: Einige Dateien im Ordner (wie `SmartAssembler.java`, `VM.java`, `ParserMain.java` und `Scanner.java`) werden für die Ausführung benötigt. Diese sind bereits in den jeweiligen Unterordnern dokumentiert und werden hier nur der Vollständigkeit halber erwähnt.

## 1. Rolle von Compiler.java in der Toolchain

`Compiler.java` ist das Bindeglied zwischen Parser und Ausführung:
- Eingabe: AST aus `ParserMain.parseProgram(...) (List<Stmt> / Expr/Stmt-Records)`.
- Verarbeitung: Traversiert das AST und ruft die SmartAssembler-DSL auf, die Op-Instruktionen erzeugt und Aufgaben wie Scopes, lokale Variablen, Upvalues und Sprungoffsets übernimmt.
- Ausgabe: `CompiledFunction `(für Top-Level-Script: name = "script", arity = 0) mit der generierten List<Op>.

## 2. Pipeline – Vom Quelltext zur Ausführung

1. Quelltext -> AST
``Compiler.compile(String source)` ruft den Parser auf und erzeugt den AST (Stmt, Expr).

2. AST -> SmartAssembler-DSL
AST-Knoten werden in SmartAssembler-Methoden umgesetzt, z. B.:
 	- Expr.Binary -> `asm.add()`, `asm.sub()`,...
 	- Stmt.If ->`asm.ifThenElse(...)`
 	- Stmt.While ->`asm.whileLoop(...)`
 	- Stmt.Function -> `asm.fun(...)`
 	- Stmt.Class -> `asm.classDecl(...)` + `asm.method(...)`

3. SmartAssembler -> Bytecode
`SmartAssembler.compile()` erzeugt eine `CompiledFunction` mit `List<Op>`.
4. Bytecode -> Ausführung
`VM.interpret(CompiledFunction)` führt den Bytecode in einer stack-basierten VM aus, unterstützt `Closures`, `Klassen`, `Methoden` und `Upvalues`.

## 3. Statement- und Expression-Kompilierung

### 3.1 Statement-Kompilierung: `stmtToAsm(Stmt stmt)`

Die Methode `stmtToAsm` ist der zentrale Statement-Dispatcher. Sie ist als `switch` über die `sealed`-Stmt-Typen implementiert.
Der Compiler ruft dabei ausschließlich DSL-Methoden des Assemblers auf.

#### 3.1.2 Expression-Statement (`Stmt.Expression`)

```lox
1 + 2;
foo();
```
Der Compiler kompiliert lediglich den enthaltenen Ausdruck über `exprToAsm(expr)`.
Es wird kein explizites Pop emittiert. Der resultierende Wert verbleibt auf dem Stack, bis der aktuelle Frame beendet wird 

### 3.1.3 Variablendeklaration (`Stmt.Var`)

```lox
var a = 10;
var b;
```
```java
case Stmt.Var var -> {
    if (var.initializer() != null)
        exprToAsm(var.initializer());
    else
        asm.nil();
    asm.var(var.name().lexem());
}
```
Mapping:
- Initializer vorhanden -> `exprToAsm(initializer)`
- sonst -> `asm.nil()`
- anschließend: `asm.var(name)`

Der Compiler entscheidet nicht, ob eine Variable global oder lokal ist.
Die Methode `asm.var(name)` übernimmt diese Entscheidung anhand der aktuellen `scopeDepth`.

### 3.1.4 If (`Stmt.If`)
```java
case Stmt.If ifStmt -> {
    asm.ifThenElse(
        a -> exprToAsm(ifStmt.condition()),
        a -> stmtToAsm(ifStmt.thenBranch()),
        ifStmt.elseBranch().isPresent()
            ? a -> stmtToAsm(ifStmt.elseBranch().get())
            : null
    );
}

```
Der Compiler nutzt die High-Level Helper-Funktion:
- `asm.ifThenElse(condition, thenBranch, elseBranchOrNull)`

Damit wird die Jump-Logik (Offsets, Patchen) vollständig im Assembler gehalten.

### 3.1.5 Funktionsdeklaration (`Stmt.Function`)

```java
case Stmt.Function func -> {
    List<String> paramNames = new ArrayList<>();
    for (Token param : func.params()) {
        paramNames.add(param.lexem());
    }

    asm.fun(func.name().lexem(), paramNames, a -> {
        for (Stmt s : func.body()) {
            stmtToAsm(s);
        }
    });
}

```
Der Compiler:
1. Extrahiert Parameternamen aus `Token`-Objekten  und erzeugt  daraus `List<String> paramNames`
2. Ruft `asm.fun(name, paramNames, bodyCompiler)` auf
3. Kompiliert den Body als Statement-Liste über `stmtToAsm`

	- `asm.fun(...)` erzeugt **ein Closure-Template** (über `Op.Closure(...)`) und speichert es im Script-Kontext als Variable `name`.
	- Damit sind Funktionen auf Top-Level direkt über den Namen aufrufbar.

### 3.1.6 While (`Stmt.While`)

Der Compiler delegiert die Schleifenlogik vollständig an asm.`asm.whileLoop(condition, body)`.
Die Erzeugung von `Loop`- und `JumpIfFalse`-Instruktionen erfolgt im Assembler


### 3.1.7 Print (`Stmt.Print`)

```lox
print a + 1;
```
Mapping:
Der Compiler übersetzt das print-Statement, indem er zuerst den Ausdruck kompiliert (Stackwert erzeugt) und anschließend `asm.print()`  aufruft. Der SmartAssembler emittiert daraufhin die Instruktion `Op.Print`.

### 3.1.8 Return (`Stmt.Return`)

```lox
return a + 1;
```

Mapping:
Der Compiler stellt sicher, dass sich vor dem Aufruf von `asm.ret()`immer ein Wert auf dem Stack befindet (entweder das Ergebnis des Ausdrucks oder nil). Der SmartAssembler erzeugt daraufhin die Instruktion `Op.Return`, die den aktuellen CallFrame beendet.

### 3.1.9 Block (`Stmt.Block`)

```lox
{
	var x = "local";
	print x;
}
```

Mapping:
- Der Compiler delegiert das Scope-Handling an `SmartAssembler.scope(...)` und kompiliert die enthaltenen Statements der Reihe nach.

### 3.1.10 Klassendeklaration (`Stmt.Class`)

Der Compiler:
1. Prüft, ob eine Superklasse existiert.
2. Ruft `asm.classDecl(className, superClassNameOrNull, classBody)` auf.
3. Für jede Methode:
	 - Parameternamen extrahieren
	 - `a.method(methodName, params, methodBodyCompiler)`

Der Compiler selbst implementiert damit keine OOP-Dispatch-Logik. Er sorgt nur dafür, dass Methoden und ggf. Superklasse korrekt an den Assembler übergeben werden.

## 4. Ausdrucks-Kompilierung: `exprToAsm(Expr expr)`

`exprToAsm` ist der zentrale Dispatcher für alle Ausdruckstypen. Die Kernidee:
- Unterausdrücke werden rekursiv in der richtigen Reihenfolge kompiliert.
- Operatoren werden als `asm.*()`-Aufrufe emittiert.

### 4.1 Literale (`Expr.Literal`)
Der Compiler unterscheidet anhand des Literal-Typs und ruft die entsprechende Assembler-Methode auf:
- `Double` -> `asm.const_(new Val.Num(d))`
- `Boolean`->`asm.true_()` / `asm.false_()`
- `String` -> `asm.const_(new Val.Str(s))`
- `null` -> `asm.nil()`

### 4.2 Variablen (`Expr.Variable`) und Zuweisung (`Expr.Assign`)

- `Expr.Variable(name)` -> `asm.get(name)`
- `Expr.Assign(name, value)`:
	1. `value` kompilieren
	2. `asm.set(name)`

Die Auflösung (local / upvalue / global) erfolgt vollständig im SmartAssembler, insbesondere innerhalb der internen Namensauflösung  `SmartAssembler.namedVariable(...)`.

### 4.3 Binäre Operatoren (`Expr.Binary`)

Die Auswertungsreihenfolge entspricht strikt dem Stack-Modell:
Zuerst wird der linke Operand ausgewertet, danach der rechte.
Der Operator verarbeitet anschließend die beiden obersten Stackwerte
Mapping (Auszug):
- `+` -> `asm.add()`
- `-` -> `asm.sub()`
- `*` -> `asm.mul()`
- `/` -> `asm.div()`
- `>` / `>=` / `<` / `<=` -> `asm.gt()` / `asm.ge()` / `asm.lt()` / `asm.le()`
- `==` / `!=` -> `asm.eq()` / `asm.ne()`

### 4.4 Logische Operatoren (`Expr.Logical`)

`and` und `or` sind **kurzschlussend**.
Der Compiler delegiert das an:
- `asm.and(left, right)` bzw. `asm.or(left, right)`

Dadurch entstehen intern Jump-Patterns, ohne dass `Compiler.java` Offsets berechnen muss.

### 4.5 Unary (`Expr.Unary`)

- `!x` → `asm.not()`
- `-x` → ursprünglich wird oft `0 - x` umgesetzt, in dieser Implementierung jedoch direkt:
  1. `exprToAsm(un.right())` -> Wert auf den Stack legen  
  2. `asm.neg()` -> negiert den obersten Stackwert
  
### 4.6 Call (`Expr.Call`)

```lox
add(1, 2)
```

Mapping:
1. Callee kompilieren
2. Argumente von links nach rechts kompilieren
3. `asm.call(argCount)`

### 4.7 Properties (`Expr.Get`, `Expr.Set`)

- `obj.name`:
	1. `obj` kompilieren
	2. `asm.getProp(name)`

- `obj.name = value`:
	1. `obj` kompilieren
	2. `value` kompilieren
	3. `asm.setProp(name)`

### 4.8 `this` und `super`

- `this` → `asm.get("this")`
- `super.method` -> `asm.getSuper(methodName)`

Wie genau `this`/`super` zur Laufzeit gebunden sind, erfolgt zur Laufzeit innerhalb der VM .Der Compiler sorgt lediglich dafür, dass die entsprechenden Lookup-Instruktionen generiert werden..

## 5. Beispiele aus `CompilerTest.java` (Compiler-Fokus)

Die folgenden Beispiele stammen direkt aus [5_compiler/CompilerTest.java](CompilerTest.java)

Hinweis: Die Bytecode-Ausgabe in `CompilerTest` druckt die `Op`-Records über `toString()`. Daher sieht man z. B. `Const[value=10]`.

### 5.1 Variablen & Zuweisung (`testVariable`)

**Quelle:**
```lox
var a = 10;
a = 20;
print a;
```

**Was der Compiler macht:**
1. `Stmt.Var` → Initializer-Literal → `asm.const_(10)` → `asm.var("a")`
2. `Expr.Assign` → RHS `20` → `asm.set("a")`
3. `Stmt.Print` → `asm.get("a")` → `asm.print()`

**Bytecode-Auszug:**
```text
Const[value=10]
DefGlobal[name=a]
Const[value=20]
SetGlobal[name=a]
GetGlobal[name=a]
Print[]
Nil[]
Return[]
```

### 5.2 If/Else (`testIfElse`)

**Quelle:**
```lox
if (true) {
	print "yes";
} else {
	print "no";
}
```

**Was der Compiler macht:**
- `Stmt.If` -> `asm.ifThenElse(condition, then, else)`
- Bedingung ist ein Literal (`true`) -> `asm.true_()`
- Then/Else sind jeweils `Stmt.Print`

**Bytecode-Auszug:**
```test
True[]
JumpIfFalse[offset=3]
Const[value=yes]
Print[]
Jump[offset=2]
Const[value=no]
Print[]
Nil[]
Return[]
```

### 5.3 Funktionen (`testFunction`)

**Quelle:**
```lox
fun add(a, b) {
	return a + b;
}
print add(1, 2);
```

**Was der Compiler macht:**
1. `Stmt.Function`:
	 - Parameterliste `a,b` extrahieren
	 - `asm.fun("add", ["a","b"], body...)`
	 - Body enthält `Stmt.Return` -> compiliert `a + b` -> `asm.ret()`
2. Top-Level `print add(1,2)`:
	 - `Expr.Call` → callee `add` + args `1,2` → `asm.call(2)`
	 - `asm.print()`

**Bytecode-Auszug:**
```text
Closure[fn=CompiledFunction[name=add, arity=2, code=[GetLocal[slot=1], GetLocal[slot=2], Add[], Return[]]], upvalues=[]]
DefGlobal[name=add]
GetGlobal[name=add]
Const[value=1]
Const[value=2]
Call[args=2]
Print[]
Nil[]
Return[]

```

### 5.4 Klassen und `super` (`testInheritanceAndSuper`)

**Quelle (relevanter Teil):**
```lox
class Circle < Shape {
	init(color, r) { super.init(color); this.r = r; }
	describe() {
		super.describe();
		print "Circle extra info";
	}
}
```

**Was der Compiler macht:**
- `Stmt.Class`:
	- erkennt `superClassName = "Shape"`
	- `asm.classDecl("Circle", "Shape", body...)`
- Jede Methode ist im AST als `Stmt.Function` repräsentiert:
	- Parameter extrahieren (`color,r`)
	- `a.method("init", ["color","r"], body...)`

**Bytecode (repräsentativer Ausschnitt):**
```text
Nil[]                     // Platzhalter für globale Variable (wird durch asm.var/set definiert)
DefGlobal[name=Shape]     
Class[name=Shape]         
SetGlobal[name=Shape]     
Closure[fn=CompiledFunction[name=init, arity=1, code=[...]], upvalues=[]]
Method[name=init]
Closure[fn=CompiledFunction[name=describe, arity=0, code=[...]], upvalues=[]]
Method[name=describe]
```
**Erläuterung**:

- `Nil[]`-> Wird als Platzhalter für die globale Variable erzeugt, damit die Klasse bereits auf dem Stack existiert, bevor Methoden oder Vererbung angewendet werden.
- `Closure[...]`-> Jede Methode wird als Closure kompiliert.
- `GetSuper[...]` / `GetLocal[slot=0]` -> Zeigt die Verwendung von `super `und this in Methoden.
- Der Compiler selbst berechnet keine Jump-Offsets; alles wird an SmartAssembler delegiert.

## 6. Ausführen der Compiler-Tests 
Die Klasse CompilerTest enthält die Methode`runAll()`, die Compiler-Regeln überprüft

## 7 Nachträgliche Änderungen am Code
## 7.1 Technischer Hinweis zur Dateistruktur

Zum Zeitpunkt der ursprünglichen Abgabe war ` SmartAssembler `als eigenständige Klasse in einer separaten Quelldatei im Compiler-Ordner implementiert; der Code kompilierte und lief fehlerfrei.

Im weiteren Verlauf der Dokumentation traten im Editor vereinzelte Typauflösungsmarkierungen im Default-Package auf, obwohl die Funktionalität weiterhin uneingeschränkt gegeben war.
Um eine konsistente statische Analyse im Editor und eine verbesserte Lesbarkeit sicherzustellen, wurde `SmartAssembler `anschließend direkt in `Compiler.java` integriert.
Diese Anpassung betrifft ausschließlich die Dateiorganisation.
 ### 7.2 Ergänzung einer Hilfsmethode für`CompilerTest`

Zur vereinfachten Überprüfung der Compiler-Funktionalität wurde im Rahmen der Nachbearbeitung eine zusätzliche Hilfsmethode in `CompilerTest` ergänzt.
Diese dient ausschließlich der strukturierten Testausführung und hat keinen Einfluss auf die Implementierungslogik des Compilers.

## Navigation
- Zurück zum Einstieg: [Compiler.md](/README.md)
