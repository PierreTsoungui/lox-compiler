import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicInteger;

public class ParserTest {

    private static final AtomicInteger dotCounter = new AtomicInteger(1);
    private static final boolean PRINT_DOT_TO_CONSOLE = true;

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
        TestCase[] tests = {
            new TestCase("testPrint", ParserTest::testPrint),
            new TestCase("testVarDecl", ParserTest::testVarDecl),
            new TestCase("testFunDecl", ParserTest::testFunDecl),
            new TestCase("testIfElse", ParserTest::testIfElse),
            new TestCase("testWhile", ParserTest::testWhile),
            new TestCase("testForDesugared", ParserTest::testForDesugared),
            new TestCase("testAssignment", ParserTest::testAssignment),
            new TestCase("testCallAndProp", ParserTest::testCallAndProp),
            new TestCase("testClass", ParserTest::testClass),
            new TestCase("testReturn", ParserTest::testReturn),
            new TestCase("testBinaryExpression", ParserTest::testBinaryExpression),
            new TestCase("testLiteralAndVariable", ParserTest::testLiteralAndVariable),
            new TestCase("testComplexClass", ParserTest::testComplexClass),
            new TestCase("testNestedControlFlow", ParserTest::testNestedControlFlow),
            new TestCase("testMiniProgram", ParserTest::testMiniProgram)
        };

        int passed = 0;
        for (int i = 0; i < tests.length; i++) {
            TestCase t = tests[i];
            System.out.println("\n-- " + t.name + " --");
            try {
                t.run.run();
                System.out.println("PASSED");
                passed++;
            } catch (Throwable ex) {
                System.out.println("FAILED: " + ex.getMessage());
                ex.printStackTrace(System.out);
            }
        }

        System.out.println("\nTests passed: " + passed + "/" + tests.length);
    }

    public static void main(String[] args) throws Exception {
        runAll();
        System.out.println("Parser-Tests abgeschlossen.");
    }

    private record TestCase(String name, ThrowingRunnable run) {}

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
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
            if (PRINT_DOT_TO_CONSOLE) {
                System.out.println(section + dot);
            }
        } catch (IOException e) {
            System.err.println("Failed to write DOT: " + e.getMessage());
        }
    }
}
