import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicInteger;

public class ParserTest {

    private static final AtomicInteger dotCounter = new AtomicInteger(1);

    /* ===================== BASIS-TESTS ===================== */

    public static void testPrint() throws Exception {
        runCase("print 1 + 2;");
    }

    public static void testVarDecl() throws Exception {
        runCase("var x = 42;");
    }

    public static void testFunDecl() throws Exception {
        runCase("fun f() { print x; }");
    }

    public static void testIfElse() throws Exception {
        runCase("if (true) print 1; else print 2;");
    }

    public static void testWhile() throws Exception {
        runCase("while (false) print 0;");
    }

    public static void testForDesugared() throws Exception {
        runCase("for (var i = 0; i < 10; i = i + 1) print i;");
    }

    public static void testAssignment() throws Exception {
        runCase("a = b + c;");
    }

    public static void testCallAndProp() throws Exception {
        runCase("obj.method(1,2).field;");
    }

    public static void testClass() throws Exception {
        runCase("class A { fun m() {} }");
        runCase("class B < A { fun f() {} }");
    }

    public static void testReturn() throws Exception {
        runCase("fun g() { return 3; }");
    }

    public static void testBinaryExpression() throws Exception {
        runCase("1 + 2;");
        runCase("3 * (4 + 5);");
        runCase("a == b;");
        runCase("x < y;");
        runCase("x != y;");
        runCase("a and b or c;");
    }

    public static void testLiteralAndVariable() throws Exception {
        runCase("true;");
        runCase("false;");
        runCase("nil;");
        runCase("x;");
    }

    /* ===================== KOMPLEXE TESTS ===================== */

    public static void testComplexClass() throws Exception {
        runCase("""
            class Animal {
                fun speak() { print "noise"; }
            }
            class Dog < Animal {
                fun speak() {
                    super.speak();
                    print "woof";
                }
            }
        """);
    }

    public static void testNestedControlFlow() throws Exception {
        runCase("""
            var x = 0;
            while (x < 10) {
                if (x < 5 and x != 3) {
                    print x;
                } else {
                    print x + 1;
                }
                 x = x + 1;
            }
         """);
        }


    public static void testMiniProgram() throws Exception {
        runCase("""
            fun fib(n) {
                if (n <= 1) return n;
                return fib(n - 1) + fib(n - 2);
            }
            for (var i = 0; i < 5; i = i + 1) {
                print fib(i);
            }
        """);
    }

    /* ===================== RUNNER ===================== */

    public static void runAll() throws Exception {
        testPrint();
        testVarDecl();
        testFunDecl();
        testIfElse();
        testWhile();
        testForDesugared();
        testAssignment();
        testCallAndProp();
        testClass();
        testReturn();
        testBinaryExpression();
        testLiteralAndVariable();
        testComplexClass();
        testNestedControlFlow();
        testMiniProgram();
    }

    public static void main(String[] args) throws Exception {
        runAll();
        System.out.println(" Alle Parser-Tests erfolgreich!");
    }

    /* ===================== HELPER ===================== */

    private static void runCase(String src) throws Exception {
        ParserMain p = ParserMain.fromSource(src);
        var res = p.program().parse(p.tokens);

        if (res.hasNotFailed() && res.recognized().isPresent()) {
            appendDot(p.toDot());
            return;
        }

        throw new AssertionError(
            "Parse failed for source:\n" + src +
            "\nResult: " + res +
            "\nRemaining: " + res.rest()
        );
    }

    private static void appendDot(String dot) {
        try {
            Path out = Path.of("parser_all.dot");
            String section = "\n// --- case" +
                    String.format("%02d", dotCounter.getAndIncrement()) + " ---\n";
            Files.writeString(out, section + dot,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Failed to write DOT: " + e.getMessage());
        }
    }
}
