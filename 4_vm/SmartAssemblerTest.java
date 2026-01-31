import java.util.*;

public class SmartAssemblerTest {


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

    

    static void testOrShortCircuit() {
        SmartAssembler.runTest("testOrShortCircuit", a -> {
            a.or(
                s -> s.true_(),
                s -> s.const_(new Val.Str("BAD")).print()
            );
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
            SmartAssemblerTest::testIfElse,
            SmartAssemblerTest::testWhile,
            SmartAssemblerTest::testOrShortCircuit,
            SmartAssemblerTest::testClosureCapture,
            SmartAssemblerTest::testFunctionReturn,
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
                System.out.println("PASSED");
                passed++;
            } catch (Throwable e) {
                System.out.println(" FAILED: " + e.getMessage());
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
