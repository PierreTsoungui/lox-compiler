# Compilerbau – Projekt Lox

## Einleitung

Dieses Projekt behandelt die schrittweise Umsetzung eines **Lox-Compilers** und verfolgt dabei die zentralen Konzepte des Compilerbaus. Ziel ist es, die Sprache Lox von der Quelltextanalyse über die AST-Generierung bis hin zur Ausführung auf einer virtuellen Maschine zu bearbeiten.  

Besonderer Fokus liegt auf der Nutzung moderner **Java-Sprachkonzepte** wie **Records, Pattern Matching, sealed Interfaces/Classes, Lambda-Ausdrücken** und **Streams**, um den Code klar, modular und wartbar zu gestalten.  

Das Projekt gliedert sich in sechs aufeinander aufbauende Phasen:

1. **Vorbereitung (Aufgabe 0)**  
   Der ursprüngliche Lox-Interpreter wurde strukturell überarbeitet, modernisiert und in Dateien klar strukturiert. Das Projekt ist ohne Build-Werkzeug lauffähig.  

2. **Scanner-Austausch (Aufgabe 1)**  
   Der alte Scanner wurde durch eine Implementierung auf Basis **regulärer Ausdrücke** ersetzt.  

3. **Parser-Austausch (Aufgabe 2)**  
   - **AST-Generierung:** Einsatz von **Parserkombinatoren** zur Erzeugung eines ASTLox, der die bestehende Lox-Implementierung unverändert weiter nutzen kann.  
   - **Visualisierung:** Der AST kann als **Graphviz-dot-Datei** ausgegeben werden, um die Struktur grafisch darzustellen.  

4. **Transpiler (Aufgabe 3)**  
   Der ASTLox wird in ein semantisch äquivalentes Programm in **JavaScript** übersetzt.  

5. **Lox-VM & Assembler (Aufgabe 4)**  
   - **Lox-VMJava:** Für die Ausführung von Bytecode-Programmen, die durch den Bytecode-Compiler erzeugt wurden, wird die Lox-VMJava verwendet.  
   - **Smart-Assembler:** Für die Erzeugung von Bytecode wird ein **Smart-Assembler** eingesetzt, der als **High-Level DSL in Java** implementiert ist. Er ermöglicht die programmatische Generierung von Bytecode über Methodenaufrufe, z. B. für Konstanten, Operationen, Schleifen, Bedingungen, Funktionen und Klassen. Sprung-Offsets, lokale Variablen, Upvalues und Funktionsaufrufe werden automatisch korrekt aufgelöst, sodass der erzeugte Bytecode direkt von der Lox-VMJava ausgeführt werden kann.

6. **Bytecode-Compiler (Aufgabe 5)**  
   Aufbauend auf ScannerRegEx und Parserkombinatoren erzeugt der Compiler aus dem ASTLox **Bytecode für die Lox-VMJava**. Die komplette Toolchain aus Compiler, Assembler und VM ermöglicht so die Ausführung von Lox-Programmen.  

Alle Phasen sind in eigenen Ordnern dokumentiert, inklusive detaillierter Beschreibungen, Beispielen und Nutzungshinweisen.

## Navigation
- Aufgabe 0: [0_lox/README.md](0_lox/README.md)  
- Aufgabe 1: [1_Scanner/README.md](1_Scanner/README.md)  
- Aufgabe 2: [2_parser/README.md](2_parser/README.md)  
- Aufgabe 3: [3_transpiler/README.md](3_transpiler/README.md)  
- Aufgabe 4: [4_vm/README.md](4_vm/README.md)  
- Aufgabe 5: [5_compiler/README.md](5_compiler/README.md)
