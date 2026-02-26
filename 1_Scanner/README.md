# 1_Scanner – Regex-Scanner

## Ziel der Aufgabe 1
Der Scanner wurde durch eine **regex‑basierte Implementierung** ersetzt, die Tokens
systematisch aus dem Quelltext extrahiert. Das ermöglicht:
- **klare Trennung** von Tokenisierung und Parsing,
- **leichte Erweiterbarkeit** bei neuen Token‑Typen,
- **Kompatibilität** mit der Pipeline aus Aufgabe 0.

---

## Umsetzung (Kurzfassung)
- **Regex‑basierte Tokenisierung:** Jede Token‑Kategorie besitzt ein eigenes Pattern.
- **Automatische Klassifizierung:** Jedes Token bekommt Typ, Lexem, Wert und Zeile.
- **Fehlerbehandlung im Scanner:** Unbekannte Zeichen werden sofort gemeldet.
- **Parser‑kompatibel:** Rückgabe ist `List<Token>` (direkt weiterverwendbar).

**Hinweis zur Erstellung:**
Bei der Definition der Regex‑Patterns und bei der Code‑Optimierung wurde KI‑Unterstützung genutzt.

---

## Aufbau von Scanner.java
Datei: [1_Scanner/Scanner.java](Scanner.java)

### 1) Felder und Konstruktor
```java
private final String source;        // Quelltext
private final List<Token> tokens;   // generierte Tokenliste
private int line;                   // aktuelle Zeilennummer
private boolean error;              // Flag bei Scanner-Fehlern

public Scanner(String source) { ... }  // Initialisierung
```
**Erklärung**
- `source`: kompletter Lox‑Quelltext.
- `tokens`: Ergebnisliste der erzeugten Tokens.
- `line`: aktuelle Zeilennummer (für Fehlerberichte).
- `error`: wird gesetzt, sobald ein unbekanntes Zeichen erkannt wird.

### 2) Token‑Definition
```java
public record Token(TokenType type, String lexem, Object value, int line)
```
**Erklärung**
- `type`: Kategorie (z. B. `NUMBER`, `IDENTIFIER`, `PLUS`).
- `lexem`: exakter Text aus dem Quellcode.
- `value`: optional konvertierter Wert (z. B. `Double`, `String`).
- `line`: Zeilennummer im Quelltext.

**Beispiele:**
```java
TOKEN(NUMBER, 42, 42.0) on line 3
TOKEN(VAR, var, null) on line 1
```

### 3) Token‑Patterns (Enum TokenPattern)
Jeder Token‑Typ wird durch einen Regex erkannt. Ein `handler` erzeugt den Token
oder führt Speziallogik aus (z. B. Zeilenzähler, Kommentare, Whitespace).

**Wichtige Patterns (Auszug):**
```java
NUMBER("(?<NUMBER>[0-9]+(\\.[0-9]+)?)", (m, t) ->
    t.addToken(TokenType.NUMBER, m.group("NUMBER"), Double.parseDouble(m.group("NUMBER"))))

STRING("(?<STRING>\"[^\"]*\")", (m, t) ->
    t.addToken(TokenType.STRING, m.group("STRING"),
        m.group("STRING").substring(1, m.group("STRING").length() - 1)))

IDENTIFIER("(?<IDENTIFIER>[a-zA-Z_][a-zA-Z0-9_]*)", (m, t) -> {
    String v = m.group("IDENTIFIER");
    t.addToken(TokenType.keywords.getOrDefault(v, TokenType.IDENTIFIER), v, null);
})
```

**Hinweise:**
- `LINE_COMMENT` überspringt `//...` bis zum Zeilenende.
- `NEWLINE` erhöht `line`.
- `WHITESPACE` wird ignoriert.
- `UNKNOWN` meldet Fehler.

### 4) Tokenizing‑Logik (Ablauf)
Die Methode `tokenize()` scannt den Quelltext von links nach rechts.
Für jede Position wird geprüft, ob **ein Pattern `lookingAt()` matched**.
Der passende `handler` erzeugt Token oder führt Speziallogik aus.

Bei unbekannten Zeichen wird sofort ein Fehler gemeldet:
```java
error("Unbekanntes Zeichen: " + source.charAt(pos));
```
Am Ende wird ein `EOF`‑Token angehängt.

### 5) Wichtige Methoden
**`tokenize()`**
- Startet das Pattern‑Matching über den gesamten Quelltext.
- Bricht bei Fehlern ab und liefert sonst die Tokenliste.

**`addToken(TokenType type, String lexem, Object value)`**
- Erzeugt ein `Token` und fügt es der Liste hinzu.

**`handleSeparator(String sep, int endIndex)`**
- Spezialfall für Separatoren (z. B. `.`).
- Verwendet die `sep`‑Mapping‑Tabelle für `(`, `)`, `{`, `}`, `,`, `;`, `.` usw.
**`error(String msg)`**
- Meldet Scanner‑Fehler inkl. Zeilennummer und setzt `error = true`.

### 6) Beispiel für Tokenisierung
```Java
String source = """
    var a = 10;
    print a + 5;
""";
Scanner scanner = new Scanner(source);
List<Scanner.Token> tokens = scanner.tokenize();
tokens.forEach(System.out::println);
```
**Ausgabe**
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

### 7) Testmethoden
- `Scanner.test(String input)` – Tokenisiert einen String und gibt Tokens aus.
- `Scanner.testFile(String filename)` – Liest eine Datei und tokenisiert sie.

**Zusätzliches Testprogramm:**
- [1_Scanner/TestScanner.java](TestScanner.java) – Eigenständige Testklasse zur Überprüfung der Scanner‑Funktionalität.

*Hinweis (nach Abgabe): Vereinfachung der Punkt‑Behandlung.*

Nach der Abgabe wurde die Methode `handleSeparator` vereinfacht: Die spezielle
Behandlung des Punktes (`.`) wurde entfernt, da Fließkommazahlen bereits durch
das NUMBER‑Pattern `[0-9]+(\.[0-9]+)?` erkannt werden. Der Punkt wird nun
als Separator‑Token behandelt, wenn er nicht Teil einer Zahl ist. Dadurch wird
die Scanner‑Logik einfacher, ohne funktionale Auswirkungen.

## Navigation
- Zurück zum Einstieg: [Compiler.md](/README.md)
- Weiter zu Aufgabe 2: [2_parser/README.md](/2_parser/README.md)