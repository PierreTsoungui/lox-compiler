# 0_lox – Ausgangsbasis (Refactoring)

## Ziel der Aufgabe 0
Der Lox‑Interpreter ist auf moderne Java‑Sprachkonzepte angepasst, klar in Dateien
strukturiert und ohne Build‑Werkzeug ausführbar. Diese Phase bildet die stabile Basis für
alle weiteren Projektabschnitte.

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
`var`, NIO‑Dateizugriff, Stream‑Pipelines, Switch‑Expressions).

## Ausführung (ohne Build‑Tool)
- Start über [0_lox/Lox.java](0_lox/Lox.java)
- Tests über [0_lox/InterpreterTest.java](0_lox/InterpreterTest.java)

## Klassenübersicht mit Beispielen und Modernisierungen

### Lox.java – Einstiegspunkt und Pipeline
Datei: [0_lox/Lox.java](0_lox/Lox.java)

Aufgabe: CLI‑Einstieg, Dateiausführung/REPL und Orchestrierung der Pipeline
Scanner → Parser → Resolver → Interpreter.

**Modernisierung:**
- Nutzung der NIO‑API (`Files`, `Path`) statt klassischer `FileInputStream`/`Reader`.
- Lokale Typinferenz mit `var`.
- Klare Trennung der Pipeline als eigene Objekte (Scanner/Parser/Resolver/Interpreter).
- Zentrale Fehlerbehandlung mit Token‑Bezug und Runtime‑Fehlern.

**Unterschiede zum alten Interpreter:**
- Resolver‑Phase ist explizit vorgeschaltet.
- Fehlerausgabe nutzt Tokeninformationen.

Beispiel:
```java
var source = """
var a = 10;
var b = 20;
print a + b;
""";
Lox.run(source);
```


### Scanner – Modernisierung (Aufgabe 0)
Datei: [0_lox/Scanner.java](0_lox/Scanner.java)

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
# Parser.java – Syntaxanalyse

**Datei:** `0_lox/Parser.java`  

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
Datei: [0_lox/Interpreter.java](0_lox/Interpreter.java)

Aufgabe: Auswertung der AST‑Knoten, Laufzeitumgebung, native Funktionen (`clock`).

**Modernisierung:**
- Pattern Matching in `switch` über `Expr`/`Stmt` (kein Visitor‑Pattern).
- `IdentityHashMap<Expr, Integer>` für Resolver‑Bindings (Referenz‑Identität).
- `streams` für Argumentauswertung (`arguments.stream().map(...)`).
- `switch`‑Expressions für Operatoren.
- Sealed/Record‑basierte Werte in `LoxValue`.

**Unterschiede zum alten Interpreter:**
- Kein `Expr.Visitor`/`Stmt.Visitor`; direkte `switch`‑Auswertung.
- `LoxValue` kapselt Typen als Records statt klassischer Klassenhierarchie.

Beispiel:
```java
var interpreter = new Interpreter();
interpreter.interpret(statements);
```

### Environment.java – Variablen‑Scopes
Datei: [0_lox/Environment.java](0_lox/Environment.java)

Aufgabe: Verschachtelte Umgebungen, `define`, `get`, `assign`.

**Modernisierung:**
- Lokale Typinferenz (`var` in `ancestor`).
- Klare, fokussierte API mit `getAt`/`assignAt` für Resolver‑Offsets.

**Unterschiede zum alten Interpreter:**
- Direkter Support für Resolver‑Tiefe (Offsets) statt dynamischem Lookup.

Beispiel:
```java
var env = new Environment();
env.define("x", 42);
var x = env.get(new Token(TokenType.IDENTIFIER, "x", null, 1));
```

### Expr.java – Ausdrucks‑AST
Datei: [0_lox/Expr.java](0_lox/Expr.java)

Aufgabe: Sealed `Expr`‑Hierarchie mit Records für Ausdruckstypen.

**Modernisierung:**
- **sealed interface** + **records** ersetzen Visitor‑basierte Klassenstruktur.
- Jedes AST‑Element ist ein kompakter, unveränderlicher Record.

**Unterschiede zum alten Interpreter:**
- Wegfall von `Expr.Visitor<R>` und einzelnen Klassen pro Ausdruck.

Beispiel:
```java
var expr = new Expr.Binary(
	new Expr.Literal(1),
	new Token(TokenType.PLUS, "+", null, 1),
	new Expr.Literal(2)
);
```

### Stmt.java – Statement‑AST
Datei: [0_lox/Stmt.java](0_lox/Stmt.java)

Aufgabe: Sealed `Stmt`‑Hierarchie (Blöcke, Variablen, Funktionen, Klassen, etc.).

**Modernisierung:**
- **sealed interface** + **records** statt Visitor‑Pattern.
- Einheitliche, immutable Statement‑Definitionen.

**Unterschiede zum alten Interpreter:**
- Kein `Stmt.Visitor<R>` nötig; Auswertung direkt per Pattern‑Switch.

Beispiel:
```java
var stmt = new Stmt.Print(new Expr.Literal("hello"));
```

### Token.java – Token‑Datensatz
Datei: [0_lox/Token.java](0_lox/Token.java)

Aufgabe: Repräsentiert ein Token aus dem Scanner mit Typ, Lexem, Literal und Zeile.

**Modernisierung:**
- `record` reduziert Boilerplate (Konstruktor, `equals`, `hashCode`).
- `toString()` überschrieben für Debug‑Ausgabe.

Beispiel:
```java
var tok = new Token(TokenType.NUMBER, "123", 123.0, 1);
```

### TokenType.java – Token‑Typen
Datei: [0_lox/TokenType.java](0_lox/TokenType.java)

Aufgabe: Enum aller Lox‑Tokens (Operatoren, Keywords, Literale, EOF).

**Modernisierung:**
- Klare Enum‑Liste als zentrale Quelle der Token‑Typen.

Beispiel:
```java
var type = TokenType.STRING;
```

### Resolver.java – Namensauflösung
Datei: [0_lox/Resolver.java](0_lox/Resolver.java)

Aufgabe: Statische Auflösung von Variablen‑Scopes, `this`/`super`, Funktions‑Kontexte.

**Modernisierung:**
- Pattern Matching in `switch` über `Expr`/`Stmt`.
- `Stack<Map<String, Boolean>>` als klare Scope‑Struktur.
- `enum` für Funktions‑/Klassen‑Kontext.

**Unterschiede zum alten Interpreter:**
- Saubere Trennung zwischen Parser und Interpreter durch Resolver‑Zwischenschritt.

Beispiel:
```java
var resolver = new Resolver(interpreter);
resolver.resolve(statements);
```

### RuntimeError.java – Laufzeitfehler
Datei: [0_lox/RuntimeError.java](0_lox/RuntimeError.java)

Aufgabe: Exception‑Typ für Interpreter‑Fehler mit Token‑Bezug.

**Modernisierung:**
- Schlanke Fehlerklasse mit Token‑Referenz für bessere Diagnose.

Beispiel:
```java
throw new RuntimeError(tok, "Undefined variable.");
```

### LoxCallable.java – Aufrufbare Werte
Datei: [0_lox/LoxCallable.java](0_lox/LoxCallable.java)

Aufgabe: Schnittstelle für Funktionen, Klassen und native Funktionen.

**Modernisierung:**
- Extrahiert, um Ladezyklen (z. B. in JShell) zu vermeiden.
- Interpreter wird als `Object` übergeben (entkoppelte Signatur).

Beispiel:
```java
LoxCallable fn = new LoxCallable() {
	public int arity() { return 0; }
	public LoxValue call(Object i, java.util.List<LoxValue> a) {
		return new LoxValue.Num(0);
	}
};
```

### LoxValue.java – Laufzeitwerte
Datei: [0_lox/LoxValue.java](0_lox/LoxValue.java)

Aufgabe: Sealed‑Interface für Werte (Zahlen, Strings, Funktionen, Klassen, Instanzen).

**Modernisierung:**
- **sealed interface** + **records** für alle Laufzeitwerte.
- `NativeFn` wrapper für native Funktionen.
- Methoden‑Binding (`bind`) in `Fn` als Record‑Methode.

**Unterschiede zum alten Interpreter:**
- Kein klassisches `LoxFunction`/`LoxClass`‑Klassengerüst nötig.

Beispiel:
```java
LoxValue value = new LoxValue.Str("lox");
```

### InterpreterTest.java – Testfälle
Datei: [0_lox/InterpreterTest.java](0_lox/InterpreterTest.java)

Aufgabe: In‑Process‑Tests für Spracheigenschaften (Control Flow, Funktionen, Klassen).

**Modernisierung:**
- Nutzung von Java‑Textblöcken (`"""`), um Lox‑Programme lesbar zu definieren.
- Tests als `Runnable`‑Array statt externes Test‑Framework.

Beispiel:
```java
InterpreterTest.runAll();
```

### lox.jsh – JShell‑Startskript
Datei: [0_lox/lox.jsh](0_lox/lox.jsh)

Aufgabe: Zyklusfreie Lade‑Reihenfolge für JShell.

**Modernisierung:**
- Praktisches REPL‑Setup ohne Build‑Tool.

Beispiel:
```text
/open TokenType.java
/open Token.java
...
```

## Navigation
- Zurück zum Einstieg: [Compiler.md](Compiler.md)
- Weiter zu Aufgabe 1: [1_Scanner/README.md](1_Scanner/README.md)
