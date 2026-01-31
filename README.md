# Lox-Compiler – Projektstand

## Projektziel
Ziel ist die Entwicklung eines vollständigen Lox‑Stacks: Interpreter, Parser, Compiler, VM und Transpiler.

---

## Aktueller Stand (Januar 2026)

### 0_lox – Interpreter
- **Funktionalität:** Klassen, Vererbung, `super`, Closures, Kontrollfluss, Variablen, Rückgabe
- **Tests:** InterpreterTest mit Basis- und komplexen Fällen
- **Status:** funktionsfähig und getestet

---

### 1_Scanner – Scanner (separat)
- **Ziel:** Tokenisierung von Quellcode
- **Status:** implementiert (separates Modul)

---

### 2_parser – Parser
- **ParserMain:** Parser-Kombinatoren, AST‑Erzeugung, `parseProgram()`
- **Status:** funktionsfähig und in anderen Modulen genutzt

---

### 3_transpiler – Lox → JavaScript
- **JsTranspiler:** Funktions- und Klassentranspilation, `super`‑Aufrufe, Warnungen
- **Tests:** JsTranspilerTest (Basis + kritische Fälle)
- **Status:** funktionsfähig

---

### 4_vm – VM + SmartAssembler
- **VM:** Bytecode‑Ausführung
- **SmartAssembler:** DSL für Bytecode‑Erzeugung
- **Tests:** SmartAssemblerTest / SmartAssemblerVmTest
- **Status:** funktionsfähig

---

### 5_compiler – Compiler
- **Pipeline:** Parser → AST → SmartAssembler → Bytecode → VM
- **Features:** Kontrollfluss, Funktionen, Klassen, Vererbung, Closures
- **Tests:** CompilerTest
- **Status:** funktionsfähig

---

## Nächste Schritte
1. Transpiler‑Warnungen präzisieren (Closure‑/Funktionswerte)
2. JS‑Output: Instanzierung mit `new`
3. Tests weiter ausbauen (Edge‑Cases)
4. Dokumentation konsolidieren

---

*Stand: 31. Januar 2026*# Lox-Compiler – Projektstand
