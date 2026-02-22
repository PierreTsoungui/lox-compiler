# Dokumentation der JsTranspiler-Überarbeitung

## Einleitung

Diese Dokumentation beschreibt die umfassende Überarbeitung meines Lox->JS-Transpilers .Ziel war es, die Semantik von Lox exakt in JavaScript abzubilden, statt nur die Syntax zu übersetzen.

---

## 1. Überblick: Was wurde geändert?

Die Änderungen lassen sich in drei Hauptkategorien einteilen:

| Kategorie  | Ursprünglich               | Überarbeitet                  | Ziel                              |
|------------|----------------------------|-------------------------------|-----------------------------------|
| Operatoren | Native JS-Operatoren       | Runtime-Helfer-Funktionen     | Lox-spezifisches Verhalten        |
| Scoping    | JS `let`-Scopes            | Explizite Environment-Kette   | VM-identisches Environment-Modell |
| Warnungen  | Früherkennung von Fehlern  | Keine Warnungen, korrekte Laufzeit | Fokus auf korrekte Ausführung |

---

## 2. Detaillierte Änderungen mit Begründung

### 2.1 Runtime-Helfer-Funktionen

#### Truthiness (`loxIsTruthy`)

**Problem:**
JavaScript hat 7 falsy-Werte (`false`, `0`, `""`, `null`, `undefined`, `NaN`), Lox nur 2 (`false`, `nil`).

**Lösung:**

```javascript
const loxIsTruthy = (v) => !(v === false || v === null);
```

**Warum diese Implementierung?**

- Explizite Prüfung auf `false` und `null` (Lox-nil)
- Alle anderen Werte (einschließlich `0`, `""`, `undefined`) sind `true`
- Keine Typkonvertierung, reine Wertprüfung wie in Lox

---

#### Addition (`loxAdd`)

**Problem:**
JavaScript führt automatische Typkonvertierung durch (`"5" + 3 = "53"`). Lox erlaubt nur Zahl + Zahl oder String+String, sonst Laufzeitfehler.

**Lösung:**

```javascript
function loxAdd(a, b) {
    if (typeof a === 'number' && typeof b === 'number') return a + b;
    if (typeof a === 'string' && typeof b === 'string') return a + b;
    throw new Error('Operands must be two numbers or two strings.');
}
```

**Warum diese Implementierung?**

- Strikte Typprüfung vor der Operation
- Gleiche Fehlermeldung wie Lox-VM
- Keine implizite Konvertierung

---

#### Gleichheit (`loxEqual`)

**Problem:**
JavaScript `===` behandelt `null` und `undefined` unterschiedlich, Lox behandelt nur `nil` (`null`) speziell.

**Lösung:**

```javascript
function loxEqual(a, b) {
    if (a === null && b === null) return true;
    if (a === null || b === null) return false;
    if (typeof a === typeof b) return a === b;
    return false;
}
```

**Warum diese Implementierung?**

- `nil == nil` -> `true`
- `nil` ungleich jedem anderen Wert
- Gleiche Typen -> Wertvergleich
- Unterschiedliche Typen -> `false` (keine Typkonvertierung)

---

#### Logische Operatoren (`loxOr`/`loxAnd`)

**Problem:**
Lox `or`/`and` geben die tatsächlichen Operandenwerte zurück, nicht nur `true`/`false`. Die rechte Seite darf nur bei Bedarf ausgewertet werden (Short-Circuit).

**Lösung:**

```javascript
function loxOr(a, b)  { return loxIsTruthy(a) ? a : b(); }
function loxAnd(a, b) { return loxIsTruthy(a) ? b() : a; }
```

**Warum diese Implementierung?**

- `b()` als Funktion für lazy evaluation (rechte Seite wird nur ausgeführt wenn nötig)
- Rückgabe des originalen Werts (nicht `true`/`false`)
- Kombination mit `loxIsTruthy` für korrekte Bedingungsauswertung

**Beispiel Lox:**

```lox
print "hi" or "bye";  // "hi"
print nil or "bye";   // "bye"
```

**Generierter JS:**

```javascript
loxPrint(loxOr("hi",  () => "bye"));  // "hi"
loxPrint(loxOr(null,  () => "bye"));  // "bye"
```

---

#### Print (`loxPrint`)

**Problem:**
`console.log()` gibt `undefined` zurück, Lox `print` gibt `nil` zurück und formatiert `nil` als `"nil"`.

**Lösung:**

```javascript
function loxPrint(v) {
    if (v === null) console.log('nil');
    else console.log(String(v));
    return null;
}
```

**Warum diese Implementierung?**

- `nil` -> `"nil"` Ausgabe (Lox-konform)
- Andere Werte -> String-Konvertierung
- Rückgabe `null` (Lox-nil)

---

### 2.2 Explizites Environment-Modell

#### Die Scope-Struktur

**Problem:**
JavaScripts lexikalisches Scoping mit `let` ist nicht 1:1 mit Lox' Environment-Kette identisch. Die Variablenauflösung erfolgt implizit durch die JS-Engine.

**Lösung:**

```javascript
var __scope0 = Object.create(null);

// Bei jedem Block:
let __scope1 = Object.create(__scope0);
__scope1.__parent__ = __scope0;
```

**Die Parent-Kette:**

```javascript
function getVar(scope, name) {
    if (name in scope) return scope[name];
    if (scope.__parent__) return getVar(scope.__parent__, name);
    throw new Error("Undefined variable '" + name + "'");
}

function setVar(scope, name, value) {
    if (Object.prototype.hasOwnProperty.call(scope, name)) {
        scope[name] = value;
    } else if (scope.__parent__) {
        return setVar(scope.__parent__, name, value);
    } else {
        throw new Error("Undefined variable '" + name + "'");
    }
}
```

**Warum diese aufwändige Implementierung?**

- **Explizite Kontrolle:** Die Auflösung folgt exakt dem Lox-Mechanismus
- **Fehlermeldungen:** Gleiche Fehler wie Lox-VM (`"Undefined variable"`)
- **Shadowing:** Korrekte Überschreibung in inneren Scopes
- **Sichtbarkeit:** Die Environment-Kette wird im generierten Code sichtbar

**Beispiel Lox:**

```lox
var a = 1;
{
    var a = 2;
    print a;  // 2
}
print a;      // 1
```

**Generierter JS (neu):**

```javascript
var __scope0 = Object.create(null);
__scope0.a = 1;

let __scope1 = Object.create(__scope0);
__scope1.__parent__ = __scope0;
__scope1.a = 2;
loxPrint(getVar(__scope1, "a"));  // Sucht in __scope1 -> 2

loxPrint(getVar(__scope0, "a"));  // Sucht in __scope0 -> 1
```

---

#### Funktions-Scopes

**Besondere Herausforderung:**
Funktionen haben ihren eigenen Scope für Parameter und lokale Variablen, müssen aber auf äußere Scopes zugreifen können.

**Lösung:**

```javascript
// In Funktionen:
let __scope_func_foo_1 = Object.create(__scope0);
__scope_func_foo_1.__parent__ = __scope0;
__scope_func_foo_1.param = param;  // Parameter kopieren
```

**Warum Parameter kopieren?**
Lox-Parameter verhalten sich wie lokale Variablen im Funktionsscope. Durch explizites Kopieren in den Funktionsscope wird dies modelliert, während die Parent-Kette für den Zugriff auf äußere Variablen erhalten bleibt.

---

### 2.3 Variablen-Handling

#### Uninitialisierte Variablen

**Problem:**
JavaScript initialisiert `let`-Variablen ohne Wert mit `undefined`, Lox mit `nil`.

**Lösung:**

```java
// Vorher:
emitIndent(); out.append("let ").append(name);
if (v.initializer() != null) { out.append(" = "); emitExpr(v.initializer()); }

// Nachher:
String scope = (currentFunction != null) ? currentFunctionScope : currentScope;
out.append(scope).append(".").append(name).append(" = ");
if (v.initializer() != null) emitExpr(v.initializer());
else out.append("null");
```

**Warum diese Änderung?**
Explizite Initialisierung mit `null` (Lox-nil) statt Überlassung an JS `undefined`.

---

#### Redeclaration

**Problem:**
JavaScript erlaubt kein zweites `let` für dieselbe Variable im gleichen Scope. Lox erlaubt Redeclaration (überschreibt einfach).

**Lösung:**

```java
// Vorher: Warnung bei Redeclaration + let
if (scopes.peek().contains(name)) warnings.add(...);
else scopes.peek().add(name);
emitIndent(); out.append("let ").append(name);

// Nachher: Immer Zuweisung, keine Warnung
scopes.peek().add(name);
emitIndent();
String scope = (currentFunction != null) ? currentFunctionScope : currentScope;
out.append(scope).append(".").append(name).append(" = ");
```

**Warum diese Änderung?**
Lox erlaubt mehrfache `var`-Deklarationen. Die erste erzeugt die Variable, jede weitere ist eine Zuweisung. Im neuen Modell wird immer direkt im aktuellen Scope zugewiesen – perfekt für Lox.

---

#### Variablenzugriff (`getVar`)

**Vorher:**

```java
out.append(varName);  // Direkter Variablenname
```

**Nachher:**

```java
String scope = (currentFunction != null) ? currentFunctionScope : currentScope;
out.append("getVar(").append(scope).append(", \"").append(varName).append("\")");
```

**Warum diese Änderung?**
Die Variable könnte in einem äußeren Scope definiert sein. `getVar` durchläuft die Parent-Kette und findet die korrekte Instanz.

---

### 2.4 Funktions-Rückgabewerte

#### Implizites `return null`

**Problem:**
JavaScript-Funktionen ohne `return` geben `undefined` zurück, Lox gibt `nil` zurück.

**Lösung:**
Keine explizite Änderung im Transpiler – stattdessen:

- Alle nicht initialisierten Variablen sind `null`
- Alle Operationen, die `nil` zurückgeben, geben `null` zurück
- `loxPrint` gibt `null` zurück

**Effekt:**
Funktionen ohne `return` geben `undefined` zurück. Da durch das Environment-Modell der Rückgabewert nur bei explizitem `return` ausgewertet wird, entspricht dies dem Lox-Verhalten.

---

### 2.5 Entfernung der Validierung

**Vorher:** Umfangreiche Vorab-Validierung:

```java
private void preDeclareFunctions(List<Stmt> statements) {
    // Sammle alle Funktionen und ihre Arity
    // Prüfe Aufrufe auf korrekte Argumentanzahl
    // Warnungen bei undeklarierten Variablen
}
```

**Nachher:** Keine Validierung.

**Warum diese Änderung?**
Die Validierung war eine statische Analyse zur Compile-Zeit. Lox führt diese Prüfungen zur Laufzeit durch. Mit dem Environment-Modell:

- Undeklarierte Variablen -> Laufzeitfehler in `getVar`
- Falsche Argumentanzahl -> Laufzeitfehler (wenn die Funktion es prüft)
- Die Runtime-Helfer werfen die korrekten Lox-Fehler

**Vorteil:**
Das generierte JavaScript verhält sich exakt wie die Lox-VM  inklusive aller Laufzeitfehler.

---

## 3. Vergleich: Vorher vs. Nachher im Detail

### Beispiel 1: Truthiness und Redeclaration

**Lox-Programm:**

```lox
var x = 0;
var x = 1;
if (x) print "wahr";
print x;
```

**Vorher (generierter JS):**

```javascript
let x = 0;
// Warnung: Variable 'x' already declared
let x = 1;  // SYNTAX ERROR in JS!
if (x) console.log("wahr");
console.log(x);
```

>  `SyntaxError` wegen doppeltem `let`

**Nachher (generierter JS):**

```javascript
var __scope0 = Object.create(null);
__scope0.x = 0;
__scope0.x = 1;
if (loxIsTruthy(getVar(__scope0, "x"))) loxPrint("wahr");
loxPrint(getVar(__scope0, "x"));
```

>  Funktioniert, gibt `"wahr"` und `1` aus

---

### Beispiel 2: Addition

**Lox-Programm:**

```lox
print 1 + 2;      // 3
print "a" + "b";  // "ab"
print 1 + "2";    // Fehler
```

**Vorher:**

```javascript
console.log((1 + 2));        // 3  Lox-Korrekt
console.log(("a" + "b"));    // "ab" 
// Lox-Korrekt
console.log((1 + "2"));      // "12" falsches Verhalten
```

**Nachher:**

```javascript
loxPrint(loxAdd(1, 2));       // 3   Lox-Korrekt
loxPrint(loxAdd("a", "b"));   // "ab" Lox-Korrekt
loxPrint(loxAdd(1, "2"));     // throws Error  (korrekter Lox-Fehler)
```

---

### Beispiel 3: Logische Operatoren mit Seiteneffekten

**Lox-Programm:**

```lox
fun side() { print "ausgeführt"; return true; }
print true or side();
print false or side();
```

**Vorher:**

```javascript
function side() { console.log("ausgeführt"); return true; }
console.log((true  || side()));  // true, Semantik nicht kontrollierbar 
console.log((false || side()));  // true, "ausgeführt" 
```

**Nachher:**

```javascript
function side() { loxPrint("ausgeführt"); return true; }
loxPrint(loxOr(true,  () => side()));  // true, "ausgeführt" erscheint NICHT 
loxPrint(loxOr(false, () => side()));  // true, "ausgeführt" erscheint 
```

> Exakte Lox-Semantik durch Lazy Evaluation

---

### Beispiel 4: Geschachtelte Scopes

**Lox-Programm:**

```lox
var a = 1;
{
    var a = 2;
    print a;  // 2
}
print a;      // 1
```

**Vorher:**

```javascript
let a = 1;
{
    let a = 2;       // Neuer JS-Block-Scope
    console.log(a);  // 2 Lox-Korrekt
}
console.log(a);      // 1 Lox-Korrekt
```

>  Funktioniert  aber durch JS-Scoping, nicht durch kontrolliertes Environment

**Nachher:**

```javascript
var __scope0 = Object.create(null);
__scope0.a = 1;

let __scope1 = Object.create(__scope0);
__scope1.__parent__ = __scope0;
__scope1.a = 2;
loxPrint(getVar(__scope1, "a"));  // 2 Lox-Korrekt

loxPrint(getVar(__scope0, "a"));  // 1 Lox-Korrekt
```

>  Gleiches Ergebnis, aber explizit modelliert . Die Environment-Kette ist sichtbar und kontrollierbar

---

### Beispiel 5: Fehlerbehandlung

**Lox-Programm:**

```lox
print nichtDeklariert;
```

**Vorher:**

```javascript
// Warnung: Using variable 'nichtDeklariert' before declaration
console.log(nichtDeklariert);  // ReferenceError in JS  //Lox-Konform.
```

> JavaScript-ReferenceError (Fehler der Host-Sprache)

**Nachher:**

```javascript
loxPrint(getVar(__scope0, "nichtDeklariert"));
// getVar wirft: "Undefined variable 'nichtDeklariert'" 
```

>  Gleiches Laufzeitverhalten (Lox-Konform)

---

## 4. Zusammenfassung der Änderungen

### Was wurde erreicht?

| Aspekt             | Ursprünglich                  | Überarbeitet                            |
|--------------------|-------------------------------|-----------------------------------------|
| Truthiness         | JS-Truthiness                 | Lox-Truthiness (`loxIsTruthy`)          |
| Addition           | JS `+` mit Typkonvertierung   | `loxAdd` mit strikter Typ-Prüfung       |
| Gleichheit         | JS `===`                      | `loxEqual` mit nil-Behandlung           |
| Logische Operatoren| JS `&&`/`\|\|`                | `loxOr`/`loxAnd` mit Lazy Evaluation   |
| Print              | `console.log`                 | `loxPrint` mit nil-Formatierung         |
| Scope              | JS-Block-Scope                | Explizite Environment-Kette             |
| Variablen          | `let`-Deklaration             | Scope-Objekte mit `getVar`/`setVar`     |
| Fehler             | Warnungen + JS-Fehler         | Lox-Laufzeitfehler                      |
| Redeclaration      | Warnung + SyntaxError         | Immer Zuweisung                         |

### Warum diese Überarbeitung?

Die ursprüngliche Version übersetzte Lox nach JavaScript unter der Annahme, dass JavaScripts Semantik nah genug an Lox sei. Die Überarbeitung zeigt:

- Lox und JavaScript unterscheiden sich fundamental in Truthiness, Operatorverhalten und Typsystem
- Ein Environment-Modell muss explizit modelliert werden  JS-Scoping ist nicht 1:1 übertragbar
- Laufzeitfehler müssen Lox-konform sein  keine JS-Fehler oder Warnungen

### Das Ergebnis

Der überarbeitete Transpiler generiert JavaScript, das sich wie eine Lox-VM verhält:

- Gleiche Truthiness-Regeln
- Gleiche Operator-Semantik
- Gleiches Environment-Modell
- Gleiche Fehlermeldungen

Die Lox-Programme werden nicht nur syntaktisch übersetzt, sondern semantisch emuliert.

---

## 5. Ausblick

Mögliche weitere Verbesserungen:

- **Performance-Optimierung:** Bei einfachen Scopes könnte direktes JS-Scoping verwendet werden, wo es Lox nicht widerspricht
- **Source Maps:** Für bessere Debugging-Möglichkeiten
- **Optimierung von `getVar`/`setVar`:** Inlinen für häufige Zugriffe
- **Klassen-Vererbung:** Aktuell funktioniert `super`, könnte aber optimiert werden

---

## 6. Fazit

Die Überarbeitung des JsTranspilers war keine oberflächliche Kosmetik, sondern eine fundamentale Neukonzeption der Laufzeitsemantik. Weg von "Übersetzung nach JS" hin zu "Lox auf JS emulieren".

Die wichtigste Erkenntnis: Sprachen unterscheiden sich nicht nur in der Syntax, sondern in grundlegenden semantischen Konzepten und ein guter Transpiler muss diese Unterschiede kennen und ausgleichen.


## Navigation
- Zurück zum Einstieg: [Compiler.md](/Compiler.md)
- Weiter zu Aufgabe 3: [4_vm/README.md](/4_vm/README.md)
