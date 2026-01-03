// Zyklusfreie Ladereihenfolge für JShell
// Diese Reihenfolge vermeidet Zyklen zwischen Interpreter und LoxValue

// 1. Grundlegende Typen (keine Abhängigkeiten)
/open TokenType.java
/open Token.java

// 2. AST-Knoten (nur Token-Abhängigkeiten)
/open Expr.java
/open Stmt.java

// 3. Fehlerbehandlung
/open RuntimeError.java

// 4. Umgebung (nur Token und RuntimeError)
/open Environment.java

// 5. Callable Interface (referenziert Interpreter nur als Object, kein Zyklus mehr)
/open LoxCallable.java

// 6. Lox-Werte (abhängig von Environment, Token, Stmt, LoxCallable)
//    Verwendet Reflection, um Interpreter zu verwenden ohne direkte Referenz
/open LoxValue.java

// 7. Interpreter (abhängig von LoxValue und LoxCallable, aber kein Zyklus)
/open Interpreter.java

// 8. Scanner (unabhängig)
/open Scanner.java

// 9. Parser (abhängig von Token, Expr, Stmt)
/open Parser.java

// 10. Resolver (abhängig von Interpreter, Stmt, Expr)
/open Resolver.java

// 11. Hauptklasse (abhängig von allen)
/open Lox.java

System.out.println("=== Lox Interpreter geladen (zyklusfrei) ===");
