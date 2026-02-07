# 1_Scanner – Regex-Scanner

## Ziel der Aufgabe 1
Der Lox-Scanner wurde modernisiert und durch eine **regex-basierte Implementierung** ersetzt, die Tokens systematisch aus dem Quelltext extrahiert.  
Dies erlaubt:
- **saubere Trennung von Tokenisierung und Parsing**,  
- **leichte Erweiterbarkeit** bei neuen Token-Typen,  
- volle Kompatibilität mit dem Interpreter aus Aufgabe 0.

---

## Umsetzung (Kurzfassung)
- **Regex-basierte Tokenisierung:** Jede Kategorie von Token (Zahlen, Strings, Identifier, Schlüsselwörter, Operatoren, Separatoren) wird durch einen regulären Ausdruck erkannt.
- **Automatisches Klassifizieren:** Jedes Token erhält Typ, Lexem, Wert und Zeilenangabe.
- **Zentrale Fehlerbehandlung:** Unbekannte Zeichen werden direkt gemeldet.
- **Pipeline-kompatibel:** Liefert eine Liste von `Token`, die direkt vom Parser weiterverarbeitet werden kann.

---

## Aufbau von `Scanner.java`

### 1. Felder und Konstruktor
```java
private final String source;        // Quelltext
private final List<Token> tokens;   // generierte Tokenliste
private int line;                   // aktuelle Zeilennummer
private boolean error;              // Flag bei Scanner-Fehlern

public Scanner(String source) { ... }  // Initialisierung
```
**Erklärung**

**1.source** :enthält den kompletten Lox-Quelltext.

**2.tokens**: Liste der erzeugten Tokens, die der Parser verarbeiten kann.

**3.line**: aktuelle Zeile im Quelltext (für Fehlerberichte).

**4.error**:Flag, falls ein unbekanntes Zeichen gefunden wird.

### 2. Token-Definition
```java
public record Token(TokenType type, String lexem, Object value, int line)
```
**Erklärung**
**1.type**: Kategorie des Tokens (NUMBER, IDENTIFIER, PLUS, etc.).

**2.lexem** :exakte Textdarstellung im Quellcode.

**3.value**: ggf. konvertierter Wert (Double, String etc.).

**4.line** : Zeilennummer im Quelltext.
**Beispiele:**
```java
TOKEN(NUMBER, 42, 42.0) on line 3
TOKEN(VAR, var, null) on line 1
```

### 3. Token Patterns

Jeder Token-Typ wird durch die enum TokenPattern definiert:

**regex**: Regulärer Ausdruck zur Erkennung.

**handler** : Funktion, die den Token erzeugt und in die Liste einfügt.

***Beispiel**
```Java

NUMBER("(?<NUMBER>[0-9]+(\\.[0-9]+)?)", (m, t) -> t.addToken(TokenType.NUMBER, m.group("NUMBER"), Double.parseDouble(m.group("NUMBER")))),

STRING("(?<STRING>\"[^\"]*\")", (m,t) -> t.addToken(TokenType.STRING, m.group("STRING"), m.group("STRING").substring(1,m.group("STRING").length()-1))),

```
### 4. Tokenizing-Logik

Durchlauf des gesamten Quelltextes (pos = 0 ... source.length()).

Für jede Position wird geprüft, ob ein Pattern lookingAt() liefert.

Passender handler erzeugt Token und fügt es zur Liste hinzu.

Fehlerhafte Zeichen lösen eine Meldung aus:
```Java
error("Unbekanntes Zeichen: " + source.charAt(pos));
```
Am Ende wird ein EOF-Token angehängt.

### 6. Methoden im Scanner
tokenize()

Führt die eigentliche Tokenisierung durch.

Gibt eine List<Token> zurück.

addToken(TokenType type, String lexem, Object value)

Fügt ein Token der Liste hinzu.

handleSeparator(String sep, int endIndex)

Spezielle Behandlung von Punkten bei Zahlen (3.14) vs. Trenner (.).

error(String msg)

Meldet Scanner-Fehler mit Zeilenangabe und setzt error = true.
### 7. Beispiel für Tokenisierung
```Java
String source = """
    var a = 10;
    print a + 5;
""";
Scanner scanner = new Scanner(source);
List<Scanner.Token> tokens = scanner.tokenize();
tokens.forEach(System.out::println);
```
**Auagabe**
```Java
TOKEN(VAR, var, null) on line 1
TOKEN(IDENTIFIER, a, null) on line 1
TOKEN(EQUAL, =, null) on line 1
TOKEN(NUMBER, 10, 10.0) on line 1
TOKEN(SEMICOLON, ;, null) on line 1
TOKEN(PRINT, print, null) on line 2
TOKEN(IDENTIFIER, a, null) on line 2
TOKEN(PLUS, +, null) on line 2
TOKEN(NUMBER, 5, 5.0) on line 2
TOKEN(SEMICOLON, ;, null) on line 2
TOKEN(EOF, , null) on line 3
```

### 8. Testmethoden

Scanner.test(String input) – Testet einen String und gibt Tokens aus.

Scanner.testFile(String filename) – Liest eine Datei ein und tokenisiert sie.