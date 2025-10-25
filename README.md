# Lox-Compiler – Projektstand

## Projektziel
Ziel dieses Projekts ist es, einen **Compiler für die Programmiersprache Lox** zu entwickeln.  
Der Compiler soll schrittweise von der **Lexikalischen Analyse (Scanner)** über einen **Parser** bis hin zur **Bytecode-Generierung für eine VM** umgesetzt werden.

---

## Aktueller Stand

### Scanner (aktueller Entwicklungsstand)
- Token-Typen definiert in `src/scanning/TokenType.java`
- Token-Record in `src/scanning/Token.java`
- Methoden für lexikalische Analyse implementiert:
  - `scanTokens()`, `scanToken()`
  - Hilfsmethoden: `advance()`, `peek()`, `peekNext()`, `match()`, `isDigit()`, `isAlpha()`, `alphaNumeric()`
  - Token-Erzeugung: `addToken()`, `string()`, `number()`, `identifier()`
- Keywords werden automatisch erkannt
- Kommentare (`// ...`) werden übersprungen
- EOF-Token wird erzeugt
- Fehlerbehandlung für unerwartete Zeichen vorhanden
- Noch ausstehend: ausführliche Tests, Parser-Anbindung, Integration in die vollständige Compiler-Pipeline

> **Hinweis:** Dies ist der aktuelle Entwicklungsstand. Die Scanner-Funktionalität wurde implementiert, aber noch nicht getestet.
