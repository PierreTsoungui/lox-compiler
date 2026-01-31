# Lox-Compiler – Projektstand

## Projektziel
Ziel dieses Projekts ist die Entwicklung eines **Compilers für die Programmiersprache Lox**.  
Der Compiler wird schrittweise von der **lexikalischen Analyse** über einen **Parser** bis hin zur **Bytecode-Generierung für eine virtuelle Maschine (VM)** aufgebaut.

---

## Aktueller Stand

### 1. Scanner (Lexikalische Analyse)
- **Dateien & Strukturen:**  
  - `TokenType.java` – Definition aller Token-Typen  
  - `Token.java` – Token-Record  
  - `TokenPattern.java` – Reguläre Ausdrücke für Token-Erkennung  

- **Unterstützte Token-Typen:**  
  - Zahlen (`NUMBER`)  
  - Zeichenketten (`STRING`)  
  - Identifikatoren & Keywords (`IDENTIFIER`)  
  - Operatoren (`OPERATOR`)  
  - Separatoren (`SEPARATOR`)  
  - Whitespace & Zeilenumbrüche (`WHITESPACE`, `NEWLINE`)  
  - Unbekannte Zeichen (`UNKNOWN`)  

- **Funktionen:**  
  - `tokenize()` – erzeugt Liste von Tokens aus Quellcode  
  - `handleSeparator()` – spezielle Behandlung von Punkt-Separatoren  
  - `addToken()` – fügt Tokens der Liste hinzu  

- **Besonderheiten & Features:**  
  - Keywords werden automatisch erkannt  
  - Kommentare (`// ...`) werden ignoriert  
  - Fehlerhafte oder unerwartete Zeichen führen zur sofortigen Fehlerausgabe; bei Fehlern wird keine Token-Liste erzeugt  
  - EOF-Token wird nur bei fehlerfreiem Scannen erzeugt  

- **Testbeispiel:**
```lox
var a = 5;
var b={ true};
print("Hello");
var b= test;
var i= 3+3;
test[2]


---

### 1. Scanner – Ausgabe

**Ausgabe:**  
Korrekte Token-Liste für Variablen, Zahlen, Operatoren, Strings, Keywords und EOF.

> **Status:** Scanner vollständig implementiert und getestet.

---

### 2. Parser

**Aktueller Stand:**  
- Parser-Kombinator erfolgreich implementiert  
- Verwendung eines gemeinsamen Interfaces zur Lösung von Cast-Problemen  
- Unterstützung für generische Parsing-Strukturen  
- Parser vorbereitet für die AST-Erzeugung

> **Status:** Implementierung läuft; grundlegende Strukturen fertig, Arbeit an spezifischen Parser-Regeln.

---

### 3. Virtuelle Maschine & Assembler

**VM/Assembler-Funktionen:**  
- Virtuelle Maschine funktionsfähig  
- Assembler erzeugt Logs und Bytecode  
- Log-Funktionen bereits durch Dozenten teilweise implementiert  
- Compiler erzeugt Assembler-Code, der in Bytecode umgewandelt wird

> **Status:** Grundlegende VM- und Assembler-Funktionalität abgeschlossen.

---

## Nächste Schritte
1. Fertigstellung des Parsers und Integration mit Scanner  
2. AST-Generierung und Validierung  
3. Verbindung Compiler → Assembler → VM  
4. Erweiterte Fehlerbehandlung im Compiler  
5. Testen der kompletten Pipeline: Lox-Quellcode → Bytecode → Ausführung in VM

---

*Stand: 8. November 2025*  
Der Compiler befindet sich aktuell in der **Compiler-Bauphase**. Scanner und VM/Assembler sind einsatzbereit; der Fokus liegt derzeit auf der **Parser-Implementierung** und der Integration aller Komponenten.


Beschreibung
- Die Implementierung in `2_parser/Scanner.java` verwendet eine Menge von regulären Ausdrücken (Enum `TokenPattern`), um Token im Quelltext zu erkennen. Jeder Pattern-Eintrag besitzt einen Handler, der beim Treffer ein `Token` erzeugt oder Seitenwirkungen (z. B. Zeilenanzahl erhöhen, Kommentar überspringen) ausführt.

Wesentliche Features
- Erkennung von Keywords, Identifiers, Zahlen (inkl. optionaler Dezimalstellen), Strings, Operatoren und Separatoren.
- Zeilenorientierte Fehlerausgabe bei unbekannten Zeichen und ein lokales Error-Flag, das die Tokenisierung bei schwerwiegenden Fehlern abbricht.
- Trennung von Pattern-Definition (`TokenPattern`) und Token-Handling (Handler als `BiConsumer`).

Integration
- Die Scanner-Ausgabe ist eine Liste von `Token`-Records (`type`, `lexem`, `value`, `line`), die direkt an den Parser übergeben werden kann. Die `TokenType`-Maps (`keywords`, `op`, `sep`) stellen die Zuordnung von Lexemen zu Token-Typen bereit.

Selektoren (Dokumentations-Builder)
- `file:2_parser/Scanner.java`
- `method:2_parser/Scanner.java::Scanner.tokenize()`

Beispiel: Lokaler Testlauf
- Ich habe einen kleinen Test-Runner (`TestScannerRunner.java`) angelegt, der `Scanner.test(...)` mit einer kurzen Eingabe aufruft. Zum Kompilieren und Ausführen in `2_parser`:

```powershell
cd 2_parser
javac Scanner.java TestScannerRunner.java
java TestScannerRunner
```

Beispielausgabe (für Eingabe: `var x = 42; print x;\nfun greet() { print "hello"; }\n// a comment`)

```text
TOKEN(VAR, var, null) on line 1
TOKEN(IDENTIFIER, x, null) on line 1
TOKEN(EQUAL, =, null) on line 1
TOKEN(NUMBER, 42, 42.0) on line 1
TOKEN(SEMICOLON, ;, null) on line 1
TOKEN(PRINT, print, null) on line 1
TOKEN(IDENTIFIER, x, null) on line 1
TOKEN(SEMICOLON, ;, null) on line 1
TOKEN(FUN, fun, null) on line 2
TOKEN(IDENTIFIER, greet, null) on line 2
TOKEN(LEFT_PAREN, (, null) on line 2
TOKEN(RIGHT_PAREN, ), null) on line 2
TOKEN(LEFT_BRACE, {, null) on line 2
TOKEN(PRINT, print, null) on line 2
TOKEN(STRING, "hello", hello) on line 2
TOKEN(SEMICOLON, ;, null) on line 2
TOKEN(RIGHT_BRACE, }, null) on line 2
TOKEN(EOF, , null) on line 4
```

Hinweis
- Auf Wunsch kann ich den Scanner‑Abschnitt weiter ausbauen: z. B. detaillierte Erklärung der wichtigsten `TokenPattern`-Regexes, Tests für Float-Erkennung, oder die automatische Einbettung der Token-Ausgabe in die Dokumentation als echtes Snippet aus dem Build‑Schritt.
