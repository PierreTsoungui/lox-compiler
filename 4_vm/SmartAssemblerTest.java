import java.util.*;

public class SmartAssemblerTest {

    static void test1() {
        SmartAssembler.runTest("test1", a -> {
            a.scope(s -> {
                s.const_(new Val.Num(1)).var("x");
                s.const_(new Val.Num(2)).var("x"); // sollte Fehler auslösen
            });
        });
    }

    static void test2() {
        SmartAssembler.runTest("test2", a -> {
            a.scope(s -> {
                s.const_(new Val.Num(5)).var("y");
                s.scope(s2 -> {
                    s2.const_(new Val.Num(6)).var("y"); // erlaubt
                });
            });
        });
    }

    static void testIfElse() {
        SmartAssembler.runTest("testIfElse", a -> {
            a.const_(new Val.Bool(true))
             .scope(s -> {
                 int jumpFalsePos = s.emitJumpIfFalse();
                 s.const_(new Val.Num(1)).print();
                 int jumpEndPos = s.emitJump();
                 s.patchJump(jumpFalsePos);
                 s.const_(new Val.Num(2)).print();
                 s.patchJump(jumpEndPos);
             });
        });
    }

    static void testOr() {
        SmartAssembler.runTest("testOr", a -> {
            a.or(
                s -> s.const_(new Val.Bool(false)),
                s -> s.const_(new Val.Bool(true))
            ).print();
        });
    }

    static void testAnd() {
        SmartAssembler.runTest("testAnd", a -> {
            a.and(
                s -> s.const_(new Val.Bool(true)),
                s -> s.const_(new Val.Bool(false))
            ).print();
        });
    }

    static void testWhile() {
        SmartAssembler.runTest("testWhile", a -> {
            a.const_(new Val.Num(0)).var("i");
            int loopStart = a.emitLoopStart();
            a.get("i").const_(new Val.Num(3)).lt();
            int exitJump = a.emitJumpIfFalse();
            a.get("i").print();
            a.get("i").const_(new Val.Num(1)).add().set("i");
            a.emitLoop(loopStart);
            a.patchJump(exitJump);
        });
    }

    static void testClass() {
        SmartAssembler.runTest("testClass", a -> {
            a.classDecl("Foo", c -> {
                c.method("bar", 0, m -> {
                    m.const_(new Val.Str("Foo.bar")).print();
                });
            });
        });
    }

    static void testOrShortCircuit() {
        SmartAssembler.runTest("testOrShortCircuit", a -> {
            a.or(
                s -> s.true_(),
                s -> s.const_(new Val.Str("BAD")).print()
            );
        });
    }

    static void testVarSelfAssign() {
        SmartAssembler.runTest("var a = a;", a -> {
            a.get("a");
            a.var("a");
        });
    }

    static void testArithmetic() {
        SmartAssembler.runTest("testArithmetic", a -> {
            a.const_(new Val.Num(1))
             .const_(new Val.Num(2))
             .add()
             .print();
        });
    }

    static void testComparison() {
        SmartAssembler.runTest("testComparison", a -> {
            a.const_(new Val.Num(1))
             .const_(new Val.Num(2))
             .lt()
             .print();
            
            a.const_(new Val.Num(3))
             .const_(new Val.Num(2))
             .gt()
             .print();
        });
    }

    static void testGlobalVar() {
        SmartAssembler.runTest("testGlobalVar", a -> {
            a.const_(new Val.Num(42)).var("g");
            a.get("g").print();
        });
    }

    static void testClosureCapture() {
        SmartAssembler.runTest("testClosureCapture", a -> {
            a.scope(s -> {
                s.const_(new Val.Num(10)).var("y");
                s.fun("make", Arrays.asList(), mk -> {
                    mk.fun("inner", Arrays.asList(), inn -> {
                        inn.get("y").print();
                    });
                });
            });
        });
    }

    static void testFunctionReturn() {
        SmartAssembler.runTest("testFunctionReturn", a -> {
            a.fun("f", Arrays.asList(), f -> {
                f.const_(new Val.Num(7)).ret();
            });
        });
    }

    static void testClassPropAccess() {
        SmartAssembler.runTest("testClassPropAccess", a -> {
            a.classDecl("C", c -> {
                c.method("m", Arrays.asList(), m -> {
                    m.const_(new Val.Str("hello"))
                     .setProp("x");
                    m.getProp("x")
                     .print();
                });
            });
        });
    }

    static void testMethodWithParams() {
        SmartAssembler.runTest("testMethodWithParams", a -> {
            a.classDecl("Calculator", c -> {
                c.method("add", Arrays.asList("a", "b"), m -> {
                    m.get("a")
                     .get("b")
                     .add()
                     .ret();
                });
            });
        });
    }

    static void testInheritance() {
        SmartAssembler.runTest("testInheritance", a -> {
            a.classDecl("Parent", p -> {
                p.method("greet", Arrays.asList(), m -> {
                    m.const_(new Val.Str("Hello from parent")).print();
                });
            });
            
            a.classDecl("Child", "Parent", c -> {
                c.method("greet", Arrays.asList(), m -> {
                    m.const_(new Val.Str("Hello from child")).print();
                });
            });
        });
    }

    static void testSuperInvoke() {
        SmartAssembler.runTest("testSuperInvoke", a -> {
            a.classDecl("Parent", p -> {
                p.method("say", Arrays.asList(), m -> {
                    m.const_(new Val.Str("Parent says hi")).print();
                });
            });
            
            a.classDecl("Child", "Parent", c -> {
                c.method("say", Arrays.asList(), m -> {
                    // Rufe Super-Methode auf
                    m.const_(new Val.Str("Child says: ")).print();
                    m.const_(new Val.Str("Parent")).getSuper("say");
                    m.call(0);
                });
            });
        });
    }

    public static void runAll() {
        System.out.println("=== Running SmartAssembler tests ===");
        
        List<Runnable> tests = Arrays.asList(
            SmartAssemblerTest::testArithmetic,
            SmartAssemblerTest::testComparison,
            SmartAssemblerTest::testGlobalVar,
            SmartAssemblerTest::test1,
            SmartAssemblerTest::test2,
            SmartAssemblerTest::testIfElse,
            SmartAssemblerTest::testOr,
            SmartAssemblerTest::testAnd,
            SmartAssemblerTest::testWhile,
            SmartAssemblerTest::testOrShortCircuit,
            SmartAssemblerTest::testVarSelfAssign,
            SmartAssemblerTest::testClosureCapture,
            SmartAssemblerTest::testFunctionReturn,
            SmartAssemblerTest::testClass,
            SmartAssemblerTest::testClassPropAccess,
            SmartAssemblerTest::testMethodWithParams,
            SmartAssemblerTest::testInheritance,
            SmartAssemblerTest::testSuperInvoke
        );

        int run = 0;
        int passed = 0;
        int failed = 0;
        
        for (Runnable t : tests) {
            run++;
            System.out.println("\n-- Test #" + run + " --");
            try {
                t.run();
                System.out.println("  PASSED");
                passed++;
            } catch (Throwable e) {
                System.out.println("  FAILED: " + e.getMessage());
                e.printStackTrace(System.out);
                failed++;
            }
        }

        System.out.println("\n=== Test Summary ===");
        System.out.println("Total: " + run);
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
        System.out.println("=== Finished SmartAssembler tests ===");
    }

    public static void main(String[] args) {
        runAll();
    }
}