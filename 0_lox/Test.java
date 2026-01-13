public class Test {

    // JShell-Einstieg
    public static void run() {
        runAll();
    }

    // Optional: klassischer Einstieg
    public static void main(String[] args) {
        runAll();
    }

    // ================= Test Runner =================

    private static void runAll() {
        Runnable[] tests = {
            Test::test,
            Test::testAdvancedInterpreter,
            Test::testSimpleFor,
            Test::testWhileScope,
            Test::testNestedBlocks
        };

        int passed = 0;

        for (int i = 0; i < tests.length; i++) {
            try {
                tests[i].run();
                passed++;
            } catch (Throwable ex) {
                System.err.println("Test " + (i + 1) + " failed:");
                ex.printStackTrace();
            }
        }

        System.out.println("Tests passed: " + passed + "/" + tests.length);
        if (passed == tests.length) {
            System.out.println("ALL TESTS PASSED");
        } else {
            System.out.println("SOME TESTS FAILED");
        }
    }

    // ================= TEST METHODS =================

    public static void test() {
        String source = """
            var a = 10;
            var b = 20;
            print a + b;

            fun greet(name) {
                print "Hello, " + name + "!";
            }

            greet("Alice");

            class Point {
                init(x, y) {
                    this.x = x;
                    this.y = y;
                }

                distance() {
                    return this.x * this.x + this.y * this.y;
                }
            }

            var p = Point(3, 4);
            print p.distance();

            print clock();
            """;

        System.out.println("=== Running Lox Test ===");
        Lox.run(source);
        System.out.println("=== Test Finished ===");
    }

    public static void testAdvancedInterpreter() {
        String source = """
            var x = 5;
            var y = 2;
            var z = x * y + 10;
            print z;

            if (z > 15) {
                print "z is greater than 15";
            } else {
                print "z is 15 or less";
            }

            var sum = 0;
            for (var i = 1; i <= 5; i = i + 1) {
                sum = sum + i;
            }
            print sum;

            fun outer(a) {
                fun inner(b) {
                    return a + b;
                }
                return inner(10);
            }
            print outer(5);

            class Rectangle {
                init(width, height) {
                    this.width = width;
                    this.height = height;
                }
                area() {
                    return this.width * this.height;
                }
            }

            var r = Rectangle(4, 6);
            print r.area() + 10;

            var flag = true and false or true;
            print flag;
            """;

        System.out.println("=== Running Advanced Interpreter Test ===");
        Lox.run(source);
        System.out.println("=== Advanced Interpreter Test Finished ===");
    }

    public static void testSimpleFor() {
        String source = """
            for (var i = 0; i < 3; i = i + 1) {
                print "Loop";
            }
            print "Done";
            """;

        System.out.println("=== Testing Simple For Loop ===");
        Lox.run(source);
    }

    public static void testWhileScope() {
        String source = """
            {
                var i = 0;
                while (i < 3) {
                    print i;
                    i = i + 1;
                }
            }
            """;

        System.out.println("=== Testing While Scope ===");
        Lox.run(source);
    }

    public static void testNestedBlocks() {
        String source = """
            {
                var i = 0;
                while (i < 3) {
                    {
                        print "Loop";
                    }
                    i = i + 1;
                }
            }
            """;

        System.out.println("=== Testing Nested Blocks ===");
        Lox.run(source);
    }
}
