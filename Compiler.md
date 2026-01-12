# Compilerbau – Projekt Lox

## Einleitung

Ziel dieser Projektarbeit ist die schrittweise Umsetzung zentraler Konzepte des Compilerbaus
anhand der Programmiersprache Lox. Als inhaltliche und strukturelle Referenz dient das Werk
„Crafting Interpreters“ von Bob Nystrom, in dem ein Interpreter sowie ein ByteCode-Compiler
für Lox entwickelt werden.

Ausgehend von der Referenzimplementierung wurde der Lox-Interpreter zunächst an moderne
Sprachkonzepte von Java angepasst, um eine saubere und gut strukturierte Ausgangsbasis
für weitere Erweiterungen zu schaffen. Darauf aufbauend werden zentrale Komponenten der
Sprachverarbeitung schrittweise durch eigene Implementierungen ersetzt.

Im Fokus des Projekts stehen insbesondere der Austausch des Scanners durch eine
regex-basierte Tokenisierung, die Neuentwicklung des Parsers mittels Parserkombinatoren
sowie die Implementierung in Java realisierten Lox Virtual Machine zur
Ausführung von ByteCode. Ziel ist es, die einzelnen Phasen der Sprachverarbeitung klar
voneinander zu trennen und vergleichbar zu machen.

Nicht alle in „Crafting Interpreters“ beschriebenen Komponenten wurden vollständig
übernommen oder umgesetzt. Insbesondere ein Transpiler nach Java, Kotlin oder JavaScript
ist nicht Bestandteil dieser Arbeit. Der Schwerpunkt liegt stattdessen auf der ByteCode-
basierten Ausführung und der dazugehörigen Toolchain aus Compiler, Assembler und VM.


## 0. Ausgangsbasis: Refactoring des Lox-Interpreters

Als Ausgangspunkt für die Projektarbeit diente der in „Crafting Interpreters“ beschriebene
Lox-Interpreter in Java. Diese Referenzimplementierung stellt eine funktionale, jedoch
didaktisch orientierte Basis dar, die für eine weitergehende experimentelle Erweiterung
zunächst strukturell überarbeitet wurde.

Ziel dieses ersten Projektabschnitts war es, den bestehenden Interpreter an moderne
Sprachkonzepte von Java anzupassen und eine klar gegliederte Codebasis zu schaffen.
Dabei kamen unter anderem Records zur Modellierung einfacher, unveränderlicher
Datenstrukturen, sealed Interfaces bzw. Classes zur klaren Typabgrenzung sowie
Pattern Matching zur Vereinfachung von Fallunterscheidungen zum Einsatz.

Darüber hinaus wurde der Code in logisch zusammenhängende Dateien aufgeteilt,
sodass die einzelnen Komponenten wie Scanner, Parser, AST und Interpreter klar
voneinander getrennt sind. Diese Struktur erleichtert den späteren Austausch einzelner
Teile der Sprachverarbeitung, ohne die übrigen Komponenten anpassen zu müssen.

Das Refactoring dient somit nicht primär der Erweiterung des Funktionsumfangs,
sondern der Schaffung einer stabilen und gut verständlichen Ausgangsbasis für die
folgenden Projektstufen, in denen Scanner, Parser und Ausführungsmodell schrittweise
durch eigene Implementierungen ersetzt werden.

## 1. Scanner mit regulären Ausdrücken
- Motivation für den Austausch des Original-Scanners
- Tokenisierung mittels regulärer Ausdrücke
- Vergleich: Originalscanner vs. Regex-Scanner
- Integration in den bestehenden Interpreter
- Beispielhafter Scan-Durchlauf

## 2. Parser mit Parserkombinatoren
- Motivation für Parserkombinatoren
- Abbildung der Lox-Grammatik
- Aufbau des ASTLox
- Trennung von Parsing und Interpretation
- (Optional) Visualisierung des AST

## 4. Lox-VM (Java) und Assembler
- Motivation für eine ByteCode-basierte Ausführung
- Grundidee der Lox-VM
- Stack-basiertes Ausführungsmodell
- Überblick über zentrale Opcodes
- Assembler: Textformat für ByteCode
- Beispiel: Assembler-Programm und Ausführung

## 5. ByteCode-Compiler
- Einordnung in die Toolchain
- ASTLox als Eingabe
- Generierung von Assembler-Code
- Zusammenspiel von Compiler, Assembler und VM
- Beispielhafter End-to-End-Durchlauf

## Dokumentation und Code-Einbindung
- Ziel der automatisierten Dokumentation
- Konzept der code-nahen Einbindung
- Vorteile gegenüber manuellem Kopieren
- Aktueller Stand der Umsetzung

## Fazit
- Zusammenfassung der erreichten Ergebnisse
- Zentrale Erkenntnisse zum Compilerbau
- Reflexion über Designentscheidungen
- Mögliche Erweiterungen (Ausblick)
