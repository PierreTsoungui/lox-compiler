# Lox Interpreter - JShell Setup

## Problem gelöst: Zyklus zwischen Interpreter und LoxValue

Das Zyklusproblem wurde gelöst, indem:
1. `LoxCallable` in eine separate Datei extrahiert wurde
2. `LoxCallable` verwendet `Object` statt `Interpreter` als Parametertyp (vermeidet Zyklus)
3. Die korrekte Ladereihenfolge wurde in `lox.jsh` definiert

## Verwendung in JShell

### Methode 1: Komplett laden (empfohlen)
```bash
jshell lox.jsh
```

### Methode 2: Einzeln laden (wenn nötig)
Lade die Dateien in dieser Reihenfolge:
1. `/open TokenType.java`
2. `/open Token.java`
3. `/open Expr.java`
4. `/open Stmt.java`
5. `/open RuntimeError.java`
6. `/open Environment.java`
7. `/open LoxCallable.java`  ⚠️ WICHTIG: Vor LoxValue!
8. `/open LoxValue.java`     ⚠️ WICHTIG: Vor Interpreter!
9. `/open Interpreter.java`
10. `/open Scanner.java`
11. `/open Parser.java`
12. `/open Resolver.java`
13. `/open Lox.java`

## Wichtige Hinweise

- **NIE** `Interpreter.java` direkt laden, ohne vorher `LoxValue.java` geladen zu haben!
- Die Reihenfolge ist kritisch wegen der Abhängigkeiten
- `LoxCallable` muss vor `LoxValue` geladen werden (da `LoxValue.Fn` und `LoxValue.Klass` es implementieren)
- `LoxValue` muss vor `Interpreter` geladen werden (da `Interpreter` `LoxValue` verwendet)

