# 0_lox – Ausgangsbasis (Refactoring)

## Ziel der Aufgabe 0
Der Lox‑Interpreter ist auf moderne Java‑Sprachkonzepte angepasst, klar in Dateien
strukturiert und ohne Build‑Werkzeug ausführbar.

## Umsetzung (Kurzfassung)
- Einsatz moderner Java‑Features (Records, Pattern Matching, sealed Interfaces/Classes,
  Switch‑Expressions, Map.ofEntries, var, Streams).
- Trennung der Komponenten in eigene Dateien für Wartbarkeit und Austauschbarkeit.
- Ausführung ohne Build‑System möglich.

## Moderne Architektur – Überblick
**Wesentlicher Unterschied zum ursprünglichen Interpreter:**
Der Original‑Ansatz aus *Crafting Interpreters* nutzt das **Visitor‑Pattern** mit
einer `Expr`/`Stmt`‑Klassenhierarchie und Besuchern wie `Expr.Visitor<R>`.
In dieser Modernisierung wurde das durch **sealed Interfaces** + **Records** ersetzt.
Die Auswertung erfolgt über **Pattern Matching in switch** statt Visitor‑Dispatch.
Das macht die AST‑Struktur kompakter, typsicherer und leichter erweiterbar.

Zusätzlich wurden moderne Java‑APIs und Sprachfeatures genutzt (z. B. `Map.ofEntries`,
`var`, Stream‑Pipelines, Switch‑Expressions).

## Ausführung (ohne Build‑Tool)
- Start über [0_lox/Lox.java](Lox.java)
- Tests über [0_lox/InterpreterTest.java](InterpreterTest.java)

## Klassenübersicht mit Beispielen und Modernisierungen

### Lox.java – Einstiegspunkt und Pipeline
Datei: [0_lox/Lox.java](Lox.java)

Aufgabe: CLI‑Einstieg, Dateiausführung/REPL und Orchestrierung der Pipeline
Scanner → Parser → Resolver → Interpreter.

**Modernisierung:**
-  Keine strukturellen Änderungen notwendig. 

Beispiel:
```java
var source = """
var a = 10;
var b = 20;
print a + b;
""";
Lox.run(source);
```
### TokenType.java – Token-Typen
Datei: [0_lox/TokenType.java](TokenType.java)

**Aufgabe:**  
Enum aller Token-Typen der Lox-Sprache (Operatoren, Schlüsselwörter, Literale, EOF).

**Modernisierung:**  
- Keine strukturellen Änderungen notwendig.  

### Token.java – Token-Datensatz
Datei: [0_lox/Token.java](Token.java)

**Aufgabe:**  
Repräsentiert ein Token aus dem Scanner mit Typ, Lexem, Literalwert und Zeilennummer.

**Modernisierung:**  
- Umstellung auf ein **`record`** anstelle einer klassischen Klasse.  

**Unterschiede zur ursprünglichen Version:**  

- Deutlich kompakterer und besser lesbarer Code.  
- Semantisch unverändert gegenüber der Originalimplementierung.

**Beispiel:**  
```java
var tok = new Token(TokenType.NUMBER, "123", 123.0, 1);
```
### AST – Statements und Ausdrücke
Dateien: [0_lox/Stmt.java](Stmt.java), [0_lox/Expr.java](Expr.java)

**Aufgabe:**  
Repräsentation der Syntaxbäume des Lox-Programms: Statements (Blöcke, Variablen, Funktionen, Klassen, Bedingungen, Schleifen) und Ausdrücke (arithmetisch, logisch, Variablen, Funktionsaufrufe, Property-Zugriffe, etc.).

**Modernisierung:**
- **Sealed Interfaces** (`Stmt`, `Expr`) mit **Records** für konkrete Typen.
- Verzicht auf klassische Visitor-Pattern, stattdessen **Pattern Matching per `switch`** im Interpreter.
- AST-Knoten sind **immutable**, kompakt und gut lesbar.

**Unterschiede zum alten Interpreter:**
- Keine `Stmt.Visitor<R>` / `Expr.Visitor<R>`-Schnittstellen mehr.
- Direkte Auswertung der Knoten über `switch (stmt)` bzw. `switch (expr)` im Interpreter.

**Beispiele:**
```java
// Statement
var stmt = new Stmt.Print(new Expr.Literal("hello"));

// Ausdruck
var expr = new Expr.Binary(
    new Expr.Literal(1),
    new Token(TokenType.PLUS, "+", null, 1),
    new Expr.Literal(2)
);
```
### Scanner – Modernisierung (Aufgabe 0)
Datei: [0_lox/Scanner.java](Scanner.java)

Der ursprüngliche Scanner basiert auf der Referenzimplementierung aus
*Crafting Interpreters* und wurde im Rahmen der Vorbereitung strukturell
und sprachlich modernisiert.

**Wesentliche Änderungen gegenüber der Originalversion:**
- Ersetzung klassischer Klassen durch `record`-Typen für Tokens
- Ansatz moderne Switch-Expressions mit gruppierten Case-Labels
 Nutzung moderner Switch-Expressions mit gruppierten Case-Labels
- Verwendung einer unveränderlichen Keyword-Tabelle mittels `Map.ofEntries(...)`
  statt eines mutablem `HashMap`-Initialisierungsblocks
- Nutzung von `Character.isDigit`, `Character.isLetter` etc. statt
  manueller ASCII-Vergleiche
- Reduzierung von mutablem Zustand und klarere Trennung von Scan-Logik
  und Zustandsverwaltung
- Verbesserung der Lesbarkeit und Wartbarkeit durch funktionalere
  Kontrollstrukturen

Die Semantik des Scanners bleibt unverändert, die Tokenisierung entspricht
weiterhin exakt der Lox-Spezifikation.

Beispiel:
```java
var scanner = new Scanner("print 1 + 2;");
var tokens = scanner.scanTokens();
```
### Parser.java – Syntaxanalyse

**Datei:** [0_lox/Parser.java](Parser.java) 

**Aufgabe:**  
Rekursiver Abstieg, Erzeugung von `Stmt`‑ und `Expr`‑Strukturen (AST).

---

## Modernisierung

- Verwendung von `var` für lokale Typinferenz, um den Code kompakter zu machen.
- **Switch-Patterning** in `assignment()` für Typprüfungen von AST-Knoten (`Expr.Variable` / `Expr.Get`) statt klassischer `instanceof`-Ketten.
- Hilfsfunktion `parseSeparated(...)` zur generischen, wiederverwendbaren Verarbeitung von Listen wie Funktionsparametern oder Argumenten.
- Ausdrucks- und Statement-Knoten (`Expr` / `Stmt`) als **Records**, wodurch Visitor-Klassen überflüssig werden.

---

## Unterschiede zum alten Interpreter

| Bereich             | Alt                                             | Modern                                                                 |
|-------------------- |-------------------------------------------------|-----------------------------------------------------------------------|
| AST-Strukturen      | Klassische Klassen (`Expr.Binary`, `Stmt.Var`) | `record`-Typen                                                        |
| Zuweisungen         | `if (expr instanceof ...) {...}`               | Switch-Patterning (`switch(expr) { case Expr.Variable v -> ... }`)   |
| Parsing-Helper      | Redundante Loops für Argumente/Parameter       | `parseSeparated(...)` als generische Hilfsmethode                     |
| Lokale Variablen    | Explizite Typen                                 | `var` für Typinferenz                                                 |

---
## Beispiel

```java
var parser = new Parser(tokens);
var statements = parser.parse();
```
### Interpreter.java – Ausführung
Datei: [0_lox/Interpreter.java](Interpreter.java)

**Aufgabe:**  
Auswertung der AST-Knoten, Verwaltung der Laufzeitumgebung, native Funktionen (`clock`).

**Modernisierung:**
- Pattern Matching in `switch` über `Expr`/`Stmt` (kein Visitor-Pattern mehr).
- `IdentityHashMap<Expr, Integer>` für Resolver-Bindings (Referenz-Identität statt `equals()`).
- Streams für Argumentauswertung (`arguments.stream().map(...)`).
- `switch`-Expressions für Operatoren.
- Sealed/Record-basierte Werte in `LoxValue`.

**Unterschiede zum alten Interpreter:**
- Kein `Expr.Visitor`/`Stmt.Visitor`; direkte `switch`-Auswertung.
- `LoxValue` kapselt Typen als Records statt klassische Klassenhierarchie.

**Beispiel:**
```java
var interpreter = new Interpreter();
interpreter.interpret(statements);
```
### Environment.java – Laufzeitumgebung
Datei: [0_lox/Environment.java](Environment.java)

**Aufgabe:**  
Verwaltung von Variablenbindungen und verschachtelten Gültigkeitsbereichen
(Scopes) während der Programmausführung.

**Modernisierung:**
- Vereinfachte Konstruktoren durch Delegation (`this(null)`).
- Verwendung von `var` für lokale Variablen mit klar erkennbarem Typ.
- Beibehaltung der rekursiven Scope-Auflösung über `enclosing`.

**Unterschiede zur Originalversion:**
- Keine funktionalen Änderungen am Scope- oder Lookup-Verhalten.

**Beispiel:**
```java
var global = new Environment();
global.define("x", 42);
var local = new Environment(global);
```
### Resolver.java – Scope- und Namensauflösung
Datei: [0_lox/Resolver.java](Resolver.java)

**Aufgabe:**  
Der Resolver überprüft Variablen- und Funktionszugriffe vor der Laufzeit, verwaltet **Scopes** und bindet jede Variable an ihre Deklaration.  
Er prüft außerdem Klassenhierarchien, Initializer und die korrekte Verwendung von `this` und `super`.

---

**Modernisierung:**
- Wegfall des klassischen Visitor-Patterns (`Expr.Visitor` / `Stmt.Visitor`) zugunsten von **Pattern Matching per `switch`**.
- **Stack von Scopes** (`Stack<Map<String, Boolean>>`) zur Verfolgung von Variablen im aktuellen Kontext.
- Unterstützung von **sealed Records** in AST (`Stmt` / `Expr`) für klare, immutable Syntaxbäume.
- Verwaltung von **Funktions- und Klassentypen** (`FunctionType`, `ClassType`) für Rückgabeprüfungen und Vererbung.
- Direkte Auflösung von Variablen (`resolveLocal`) mit Tiefeninformation an den Interpreter.
- Modularisierung von Funktions-, Klassen- und Statement-Auflösungen in private Hilfsmethoden (`resolveFunction`, `beginScope`, `endScope`, `declare`, `define`).
---

**Unterschiede zum alten Resolver:**
- Kein Implementieren von `Expr.Visitor<Void>` und `Stmt.Visitor<Void>` mehr nötig.
- Statt `accept(this)` wird der AST direkt per `switch (stmt)` bzw. `switch (expr)` verarbeitet.
- Kompaktere Scope-Verwaltung ohne viele Boilerplate-Methoden.
- Bessere Lesbarkeit und Wartbarkeit durch pattern matching und Records.
---

**Funktionsweise im Überblick:**
1. **Statements auflösen:** `resolve(List<Stmt> statements)` ruft `resolve(Stmt stmt)` für jeden Statement-Typ auf.
2. **Ausdrücke auflösen:** `resolve(Expr expr)` behandelt alle Ausdruckstypen (Variablen, Funktionsaufrufe, `this`, `super`, usw.).
3. **Scopes verwalten:**  
   - `beginScope()` – neuer lokaler Scope.  
   - `endScope()` – Scope entfernen.  
   - `declare(name)` – Variable deklarieren, noch nicht initialisiert.  
   - `define(name)` – Variable als initialisiert markieren.
4. **Funktions- und Methodenauflösung:** `resolveFunction(Stmt function, FunctionType type)` prüft Parameter, Body und Initializer.
5. **Variable zu Scope binden:** `resolveLocal(expr, name)` berechnet die Tiefe und übergibt sie an den Interpreter.

---

**Beispiele:**
```java
// Variablen-Deklaration
var stmt = new Stmt.Var(new Token(TokenType.IDENTIFIER, "x", null, 1), 
                        new Expr.Literal(42));
resolver.resolve(stmt);

// Funktions-Definition
var func = new Stmt.Function(
    new Token(TokenType.IDENTIFIER, "foo", null, 1),
    List.of(), // Parameter
    List.of(new Stmt.Print(new Expr.Literal("hello"))) // Body
);
resolver.resolve(func);

// Klassen-Auflösung
var klass = new Stmt.Class(
    new Token(TokenType.IDENTIFIER, "MyClass", null, 1),
    null, // keine Superklasse
    List.of(func) // Methoden
);
resolver.resolve(klass);
```
### RuntimeError.java – Laufzeitfehler
Datei: [0_lox/RuntimeError.java](RuntimeError.java)

Aufgabe: Exception‑Typ für Interpreter‑Fehler mit Token‑Bezug.

**Modernisierung:**
- Schlanke Fehlerklasse mit Token‑Referenz für bessere Diagnose.

Beispiel:
```java
throw new RuntimeError(tok, "Undefined variable.");
```
### LoxCallable.java – Aufrufbare Lox-Werte
Datei: [0_lox/LoxCallable.java](LoxCallable.java)

**Aufgabe:**  
Schnittstelle für alle aufrufbaren Werte in Lox, z. B. Funktionen, Klassen oder native Funktionen.  
Sie definiert, wie ein Wert vom Interpreter aufgerufen wird und wie viele Argumente erwartet werden.

---

**Modernisierung:**
- Extrahiert, um **zyklische Abhängigkeiten** zwischen Interpreter und LoxValue zu vermeiden (praktisch für JShell / modularen Aufbau).  
- `call()` erhält den Interpreter als `Object`-Parameter und verwendet **`LoxValue`** für die Argumente und Rückgabe.  
- Typensicherheit durch Verwendung von `LoxValue` statt `Object`.

---

**Beispiel:**
```java
LoxCallable fn = new LoxCallable() {
    @Override
    public int arity() { return 0; }

    @Override
    public LoxValue call(Object interpreter, java.util.List<LoxValue> arguments) {
        return new LoxValue.Num(0);
    }
};
```
### LoxValue.java – Repräsentation aller Lox-Werte
Datei: [0_lox/LoxValue.java](LoxValue.java)

Aufgabe: Einheitliche Darstellung aller Lox-Werte, inkl. primitiver Werte, Funktionen, Klassen, Instanzen und nativer Funktionen.

**Modernisierung:**
- **Sealed Interface** + **Records** ersetzen die alte Klassenhierarchie.
- Alle Werte, die früher in separaten Klassen implementiert waren (`LoxFunction`, `LoxClass`, `LoxInstance`), sind nun in `LoxValue` integriert.
- Funktionen, Klassen und native Funktionen implementieren `LoxCallable` für einheitlichen Aufruf.
- Primitive Werte (`Nil`, `Bool`, `Num`, `Str`) sind immutable Records.
- Funktionen (`Fn`) kapseln Closure, Parameter, Body, Bindung von `this` und Initializer-Status.
- Klassen (`Klass`) kapseln Methoden, Superklasse, Aufruflogik und Initializer-Handling.
- Instanzen (`Instance`) kapseln Felder, Zugriff (`get`) und Zuweisung (`set`).
- Native Funktionen (`NativeFn`) kapseln jede implementierte `LoxCallable`-Funktion.

**Unterschiede zum alten Interpreter:**
- Keine separaten Klassen (`LoxFunction.java`, `LoxClass.java`, `LoxInstance.java`) nötig.
- Alles ist typensicher durch `sealed interface` und `records`.
- `LoxCallable` wird durch `call(Object interpreter, List<LoxValue> arguments)` entkoppelt, um Ladezyklen zu vermeiden.
- Primitive Werte sind als Records direkt integriert und bieten Methoden `stringify()` und `isTruthy()`.

**Alte Klassen, die ersetzt wurden:**
| Alte Klasse                  | Entspricht in neuer `LoxValue` |
|-------------------------------|--------------------------------|
| `LoxFunction.java`            | `LoxValue.Fn`                  |
| `LoxClass.java`               | `LoxValue.Klass`               |
| `LoxInstance.java`            | `LoxValue.Instance`            |
| `Return`-Handling             | weiterhin im Interpreter, Rückgabe über `LoxValue` |

**Beispiele:**
```java
// Primitive Werte
LoxValue nilValue = LoxValue.Nil.INSTANCE;
LoxValue numValue = new LoxValue.Num(42);
LoxValue boolValue = new LoxValue.Bool(true);
LoxValue strValue = new LoxValue.Str("hello");

// Funktion
LoxValue.Fn fn = new LoxValue.Fn(
    new Token(TokenType.IDENTIFIER, "myFunc", null, 1),
    List.of(), // Parameter
    List.of(), // Body Statements
    new Environment(), // Closure
    false // isInitializer
);

// Klasse
LoxValue.Klass klass = new LoxValue.Klass(
    "MyClass",
    null, // Superklasse
    Map.of("init", fn) // Methoden
);

// Instanz
LoxValue.Instance instance = new LoxValue.Instance(
    klass,
    new HashMap<>() // Felder
);

// Aufruf einer Funktion oder Klasse
LoxValue result = fn.call(interpreter, List.of());
LoxValue newInstance = klass.call(interpreter, List.of());
```
### InterpreterTest.java – Testfälle
Datei: [0_lox/InterpreterTest.java](InterpreterTest.java)

Aufgabe: Testen der Lox-Spracheigenschaften innerhalb des Projekts, z. B. Kontrollfluss, Variablen, Funktionen, Klassen, Methodenaufrufe und Vererbung.

**Modernisierung:**
- Lox-Programme werden direkt in **Java-Textblöcken (`"""`)** definiert.
- Tests als **Runnable-Array**, kein externes Test-Framework nötig.
- Alle Tests können über `runAll()` zentral ausgeführt werden.
- Main-Methode vorhanden, um die Tests als Java-Programm auszuführen.

**Wichtiger Hinweis:**  
Der Code wird **nicht automatisch beim Laden der Klasse** ausgeführt. Das ermöglicht:

1. Kontrolle, wann Tests gestartet werden (z. B. in JShell oder beim manuellen Start).  
2. Flexibilität, nur einzelne Tests zu starten, falls nötig.

**Testausführung:**
- Alle Tests starten:

```java
InterpreterTest.runAll();
```

### lox.jsh – JShell‑Startskript
Datei: [0_lox/lox.jsh](lox.jsh)

Aufgabe: Zyklusfreie Lade‑Reihenfolge für JShell.

**Modernisierung:**
- Praktisches REPL‑Setup ohne Build‑Tool.


## Navigation
- Zurück zum Einstieg: [Compiler.md](/Compiler.md)
- Weiter zu Aufgabe 1: [1_Scanner/README.md](/1_Scanner/README.md)
