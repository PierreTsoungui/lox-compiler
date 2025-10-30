# Lox-Compiler – Projektstand

## Projektziel
Ziel dieses Projekts ist es, einen **Compiler für die Programmiersprache Lox** zu entwickeln.  
Der Compiler wird schrittweise von der **Lexikalischen Analyse (Scanner)** über einen **Parser** bis hin zur **Bytecode-Generierung für eine VM** umgesetzt.

---

## Aktueller Stand

### Scanner (Lexikalische Analyse)
- **Token-Typen** definiert in `src/scanning/TokenType.java`
- **Token-Record** in `src/scanning/Token.java`
- **TokenPattern** in `src/scanning/TokenPattern.java` mit regulären Ausdrücken für:
  - Zahlen (`NUMBER`)  
  - Zeichenketten (`STRING`)  
  - Identifikatoren und Keywords (`IDENTIFIER`)  
  - Operatoren (`OPERATOR`)  
  - Separatoren (`SEPARATOR`)  
  - Whitespace (`WHITESPACE`) und Zeilenumbrüche (`NEWLINE`)  
  - Unbekannte Zeichen (`UNKNOWN`)  

- **Funktionen implementiert:**  
  - `tokenize()` – führt die lexikalische Analyse durch und erzeugt eine Liste von Tokens  
  - `handleSeparator()` – spezielle Behandlung von Punkt-Separatoren und Fehlerprüfung  
  - `addToken()` – fügt neue Tokens hinzu  

- **Features:**  
  - Keywords werden automatisch erkannt  
  - Kommentare (`// ...`) werden übersprungen  
  - Fehlerbehandlung für unerwartete oder ungültige Zeichen ist implementiert  
  - EOF-Token wird nur bei fehlerfreiem Scannen erzeugt  

- **Besonderheiten:**  
  - Die regulären Ausdrücke für die Token-Erkennung wurden sorgfältig definiert und optimiert (dabei wurde mir bei der Formulierung der Regex-Ausdrücke geholfen)  
  - Tokenizer stoppt sofort bei einem Fehler, um die Ausgabe ungültiger Tokens zu verhindern  
  - Fehler werden auf der Konsole ausgegeben, und im Falle eines Fehlers wird keine Token-Liste zurückgegeben  

> **Hinweis:** Die Scanner-Funktionalität ist vollständig implementiert.
# Tests

### 1. Einfache Tokenisierung
**Eingabe:**
```lox
var a = 5;
var b={ true};
print("Hello");
var b= test;
var i= 3+3;
test[2]
```
**Ausgabe:**
```text
== Tokens ===
TOKEN(VAR, var, null) on line 1
TOKEN(IDENTIFIER, a, null) on line 1
TOKEN(EQUAL, =, null) on line 1
TOKEN(NUMBER, 5, 5.0) on line 1
TOKEN(SEMICOLON, ;, null) on line 1
TOKEN(VAR, var, null) on line 2
TOKEN(IDENTIFIER, b, null) on line 2
TOKEN(EQUAL, =, null) on line 2
TOKEN(LEFT_BRACE, {, null) on line 2
TOKEN(TRUE, true, null) on line 2
TOKEN(RIGHT_BRACE, }, null) on line 2
TOKEN(SEMICOLON, ;, null) on line 2
TOKEN(PRINT, print, null) on line 3
TOKEN(LEFT_PAREN, (, null) on line 3
TOKEN(STRING, "Hello", Hello) on line 3
TOKEN(RIGHT_PAREN, ), null) on line 3
TOKEN(SEMICOLON, ;, null) on line 3
TOKEN(VAR, var, null) on line 4
TOKEN(IDENTIFIER, b, null) on line 4
TOKEN(EQUAL, =, null) on line 4
TOKEN(IDENTIFIER, test, null) on line 4
TOKEN(SEMICOLON, ;, null) on line 4
TOKEN(VAR, var, null) on line 5
TOKEN(IDENTIFIER, i, null) on line 5
TOKEN(EQUAL, =, null) on line 5
TOKEN(NUMBER, 3, 3.0) on line 5
TOKEN(PLUS, +, null) on line 5
TOKEN(NUMBER, 3, 3.0) on line 5
TOKEN(SEMICOLON, ;, null) on line 5
TOKEN(IDENTIFIER, test, null) on line 6
TOKEN(LEFT_BRACKET, [, null) on line 6
TOKEN(NUMBER, 2, 2.0) on line 6
TOKEN(RIGHT_BRACKET, ], null) on line 6
TOKEN(EOF, , null) on line 6
```
## Aktuelle Arbeit & Nächste Schritte

Der **Scanner ist abgeschlossen und voll funktionsfähig**.  
Aktuell beschäftige ich mich mit der **Vorbereitung auf die Parser-Implementierung** und vertiefe mein Verständnis der zugrunde liegenden Prinzipien.

---

### Aktuell 
- Ich frische meine **Kenntnisse über endliche Automaten und Grammatiken** auf, um den Übergang vom Scanner zum Parser besser vorzubereiten.  
- Ich recherchiere außerdem, **wie man Code-Ausgaben automatisch in README-Dateien einfügen kann**, um später die Testergebnisse direkt dokumentieren zu können.  
---

*Stand: 30. Oktober 2025*  
