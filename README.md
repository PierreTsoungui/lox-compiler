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
