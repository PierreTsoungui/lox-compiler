public class InterpreterTest {

    public static void run() {
        runAll();
    }

    public static void main(String[] args) {
        runAll();
    }

    public  static void runAll() {
        Runnable[] tests = {
            InterpreterTest::testArithmeticAndVars,
            InterpreterTest::testControlFlow,
            InterpreterTest::testFunctionsAndClosures,
            InterpreterTest::testClassesAndInheritance,
            InterpreterTest::testLogicalAndScope,
            InterpreterTest::testNestedClosuresState,
            InterpreterTest::testInitializerAndFields,
            InterpreterTest::testRecursionAndReturn,
            InterpreterTest::testMethodChaining
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

    public static void testArithmeticAndVars() {
        String source = """
            var a = 10;
            var b = 20;
            print a + b;
            var c = a * b - 5;
            print c;
            """;

        System.out.println("=== Test: Arithmetic & Vars ===");
        Lox.run(source);
    }

    public static void testControlFlow() {
        String source = """
            var sum = 0;
            for (var i = 1; i <= 5; i = i + 1) {
                sum = sum + i;
            }
            print sum;

            var x = 0;
            while (x < 3) {
                print x;
                x = x + 1;
            }

            if (sum > 10) {
                print "sum ok";
            } else {
                print "sum low";
            }
            """;

        System.out.println("=== Test: Control Flow ===");
        Lox.run(source);
    }

    public static void testFunctionsAndClosures() {
        String source = """
            fun makeAdder(x) {
                fun add(y) {
                    return x + y;
                }
                return add;
            }

            var add5 = makeAdder(5);
            print add5(3);

            fun fib(n) {
                if (n <= 1) return n;
                return fib(n - 1) + fib(n - 2);
            }

            print fib(6);
            """;

        System.out.println("=== Test: Functions & Closures ===");
        Lox.run(source);
    }

    public static void testClassesAndInheritance() {
        String source = """
            class Animal {
                init(name) {
                    this.name = name;
                }
                speak() {
                    print this.name + " makes noise";
                }
            }

            class Dog < Animal {
                speak() {
                    super.speak();
                    print this.name + " barks";
                }
            }

            var d = Dog("Rex");
            d.speak();
            """;

        System.out.println("=== Test: Classes & Inheritance ===");
        Lox.run(source);
    }

    public static void testLogicalAndScope() {
        String source = """
            var flag = true and false or true;
            print flag;

            {
                var x = "inner";
                print x;
            }

            var x = "outer";
            print x;
            """;

        System.out.println("=== Test: Logical & Scope ===");
        Lox.run(source);
    }

    public static void testNestedClosuresState() {
        String source = """
            fun makeCounter() {
                var count = 0;
                fun inc() {
                    count = count + 1;
                    return count;
                }
                return inc;
            }

            var c1 = makeCounter();
            print c1();
            print c1();

            var c2 = makeCounter();
            print c2();
            print c1();
            """;

        System.out.println("=== Test: Nested Closures & State ===");
        Lox.run(source);
    }

    public static void testInitializerAndFields() {
        String source = """
            class Base {
                init(v) { this.v = v; }
                get() { return this.v; }
            }

            class Child < Base {
                init(v, add) {
                    super.init(v);
                    this.add = add;
                }
                sum() { return this.get() + this.add; }
            }

            var c = Child(7, 5);
            print c.get();
            print c.sum();
            """;

        System.out.println("=== Test: Init & Fields ===");
        Lox.run(source);
    }

    public static void testRecursionAndReturn() {
        String source = """
            fun fact(n) {
                if (n <= 1) return 1;
                return n * fact(n - 1);
            }

            print fact(5);
            """;

        System.out.println("=== Test: Recursion & Return ===");
        Lox.run(source);
    }

    public static void testMethodChaining() {
        String source = """
            class Point {
                init(x, y) { this.x = x; this.y = y; }
                move(dx, dy) {
                    this.x = this.x + dx;
                    this.y = this.y + dy;
                    return this;
                }
                len() { return this.x * this.x + this.y * this.y; }
            }

            var p = Point(1, 2);
            print p.move(2, 3).len();
            """;

        System.out.println("=== Test: Method Chaining ===");
        Lox.run(source);
    }
}
