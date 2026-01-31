import java.util.List;

public class ParserTest {

    public static void main(String[] args) {
        testParser();
    }

    public static void testParser() {
        System.out.println("=== Parser Test: fib, Shape, Circle ===");

        String code = """
            fun fib(n) {
                if (n <= 1) return n;
                return fib(n - 1) + fib(n - 2);
            }
            print fib(5);

            class Shape {
                init(color) {
                    this.color = color;
                }
                describe() {
                    print this.color + " shape";
                }
            }

            class Circle < Shape {
                init(color, radius) {
                    super.init(color);
                    this.radius = radius;
                }
                area {
                    return 3.14 * this.radius * this.radius;
                }
            }

            var circle = Circle("red", 5);
            circle.describe();
            print circle.area;
            """;

        List<Stmt> stmts = ParserMain.parseProgram(code);

        // 1. Prüfe die Anzahl Top-Level Statements (fib + print + Shape + Circle + var circle + 2 prints)
        assertEqual(7, stmts.size());

        // 2. Prüfe erste Funktion fib
        Stmt first = stmts.get(0);
        assertTrue(first instanceof Stmt.Function, "First stmt should be a Function");
        Stmt.Function fib = (Stmt.Function) first;
        assertEqual("fib", fib.name().lexem());
        assertEqual(1, fib.params().size());
        assertEqual("n", fib.params().get(0).lexem());

        // 3. Prüfe Klasse Shape
        Stmt shapeClassStmt = stmts.get(2);
        assertTrue(shapeClassStmt instanceof Stmt.Class, "Third stmt should be a Class");
        Stmt.Class shapeClass = (Stmt.Class) shapeClassStmt;
        assertEqual("Shape", shapeClass.name().lexem());
        assertEqual(null, shapeClass.superClass());
        assertEqual(2, shapeClass.methods().size());
        assertEqual("init", shapeClass.methods().get(0).name().lexem());
        assertEqual("describe", shapeClass.methods().get(1).name().lexem());

        // 4. Prüfe Klasse Circle
        Stmt circleClassStmt = stmts.get(3);
        assertTrue(circleClassStmt instanceof Stmt.Class, "Fourth stmt should be a Class");
        Stmt.Class circleClass = (Stmt.Class) circleClassStmt;
        assertEqual("Circle", circleClass.name().lexem());
        assertTrue(circleClass.superClass() instanceof Expr.Variable);
        assertEqual("Shape", ((Expr.Variable) circleClass.superClass()).name().lexem());
        assertEqual(2, circleClass.methods().size());
        assertEqual("init", circleClass.methods().get(0).name().lexem());
        assertEqual("area", circleClass.methods().get(1).name().lexem());

        // 5. Prüfe Variable circle
        Stmt varCircleStmt = stmts.get(4);
        assertTrue(varCircleStmt instanceof Stmt.Var);
        Stmt.Var circleVar = (Stmt.Var) varCircleStmt;
        assertEqual("circle", circleVar.name().lexem());
        assertTrue(circleVar.initializer() instanceof Expr.Call);

        System.out.println("=== Alle Tests erfolgreich! ===");
    }

    /* =================== Hilfsmethoden =================== */

    public static void assertEqual(Object expected, Object actual) {
        if (expected == null && actual == null) return;
        if (expected != null && expected.equals(actual)) return;
        throw new AssertionError(
            "Assertion failed! Expected: " + expected + ", Actual: " + actual
        );
    }

    public static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError("Assertion failed: " + message);
    }
    public static void assertTrue(boolean condition) {
    assertTrue(condition, "Assertion failed");
}

}
