import java.util.*;

public class CompilerTest {

	static void runCompileTest(String testName, String source, boolean runVm) {
		System.out.println("=== Test: " + testName + " ===");
		Compiler compiler = new Compiler();
		CompiledFunction fn = compiler.compile(source);

		if (fn.code() == null || fn.code().isEmpty()) {
			throw new RuntimeException("No bytecode generated.");
		}

		System.out.println("Bytecode:");
		for (Op op : fn.code()) {
			System.out.println(op);
		}

		if (runVm) {
			System.out.println("--- VM Output ---");
			new VM().interpret(fn);
		}
		System.out.println();
	}

	static void testLiteral() {
		runCompileTest("Literals", """
			1; true; false; nil; "hello";
			""", false);
	}

	static void testBinary() {
		runCompileTest("Binary", "1 + 2; 5 - 3; 4 * 2; 8 / 2; 3 > 1; 2 < 4; 5 == 5; 6 != 7;", false);
	}

	static void testLogical() {
		runCompileTest("Logical", "true and false; false or true;", false);
	}

	static void testVariable() {
		runCompileTest("Variable", "var a = 10; a = 20; print a;", true);
	}

	static void testUnary() {
		runCompileTest("Unary", "-5; !true;", false);
	}

	static void testIfElse() {
		runCompileTest("IfElse", """
			if (true) {
				print "yes";
			} else {
				print "no";
			}
			""", true);
	}

	static void testWhile() {
		runCompileTest("While", """
			var i = 0;
			while (i < 3) {
				print i;
				i = i + 1;
			}
			""", true);
	}

	static void testFunction() {
		runCompileTest("Function", """
			fun add(a, b) {
				return a + b;
			}
			print add(1, 2);
			""", true);
	}

	static void testFunctionSimple() {
		runCompileTest("FunctionSimple", """
			fun add(a, b) {
				return a + b;
			}
			add(1, 2);
			""", true);
	}

	static void testFunctionReturn() {
		runCompileTest("FunctionReturn", """
			fun test() {
				return 42;
			}
			print test();
			""", true);
	}

	static void testScope() {
		runCompileTest("Scope", """
			var x = "global";
			{
				var x = "local";
				print x;
			}
			print x;
			""", true);
	}

	static void testClass() {
		runCompileTest("Class", """
			class Animal {
				fun init(name) {
					this.name = name;
				}

				fun speak() {
					print "Animal speaks";
				}
			}

			class Dog < Animal {
				fun init(name, age) {
					super.init(name);
					this.age = age;
				}

				fun bark() {
					print this.name + " says woof!";
				}

				fun getAge() {
					return this.age;
				}
			}
			""", false);
	}

	static void testHelloWorld() {
		runCompileTest("HelloWorld", """
			print "Hello, World!";
			var x = 10 + 5;
			print "10 + 5 = ";
			print x;
			""", true);
	}

	public static void runAll() {
		System.out.println("=== Running Compiler tests ===");

		List<Runnable> tests = Arrays.asList(
			CompilerTest::testLiteral,
			CompilerTest::testBinary,
			CompilerTest::testLogical,
			CompilerTest::testVariable,
			CompilerTest::testUnary,
			CompilerTest::testIfElse,
			CompilerTest::testWhile,
			CompilerTest::testFunction,
			CompilerTest::testFunctionSimple,
			CompilerTest::testFunctionReturn,
			CompilerTest::testScope,
			CompilerTest::testClass,
			CompilerTest::testHelloWorld
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
		System.out.println("=== Finished Compiler tests ===");
	}

	public static void main(String[] args) {
		runAll();
	}
}
