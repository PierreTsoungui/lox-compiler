public class JsTranspilerTest {

	

	public static void main(String[] args) {
		runAll();
	}

	public  static void runAll() {
		Runnable[] tests = {
			JsTranspilerTest::testBasics,
			JsTranspilerTest::testControlFlow,
			JsTranspilerTest::testFunctions,
			JsTranspilerTest::testClasses,
			JsTranspilerTest::testClosuresAndReturn,
			JsTranspilerTest::testShadowingAndScopes,
			JsTranspilerTest::testInheritanceAndSuper,
			JsTranspilerTest::testLogicalShortCircuit,
			JsTranspilerTest::testWarningsAndErrors
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

	private static void runCase(String name, String source) {
		System.out.println("=== Test: " + name + " ===");
		System.out.println(JsTranspiler.transpile(source));
	}

	public static void testBasics() {
		String source = """
			var x = 10;
			var y = 20;
			print x + y;
			""";
		runCase("Basics", source);
	}

	public static void testControlFlow() {
		String source = """
			var i = 0;
			while (i < 3) {
				print i;
				i = i + 1;
			}
			
			if (i == 3) print "done"; else print "fail";
			""";
		runCase("Control Flow", source);
	}

	public static void testFunctions() {
		String source = """
			fun add(a, b) {
				return a + b;
			}
			print add(2, 3);
			""";
		runCase("Functions", source);
	}

	public static void testClasses() {
		String source = """
			class Point {
				init(x, y) {
					this.x = x;
					this.y = y;
				}
				len() {
					return this.x * this.x + this.y * this.y;
				}
			}
			var p = Point(3, 4);
			print p.len();
			""";
		runCase("Classes", source);
	}

	public static void testClosuresAndReturn() {
		String source = """
			fun makeAdder(x) {
				fun add(y) { return x + y; }
				return add;
			}
			var add5 = makeAdder(5);
			print add5(3);
			""";
		runCase("Closures & Return", source);
	}

	public static void testShadowingAndScopes() {
		String source = """
			var x = "global";
			{
				var x = "block";
				print x;
			}
			print x;
			""";
		runCase("Shadowing & Scopes", source);
	}

	public static void testInheritanceAndSuper() {
		String source = """
			class Shape {
				init(color) { this.color = color; }
				describe() { print this.color + " shape"; }
			}
			class Circle < Shape {
				init(color, r) { super.init(color); this.r = r; }
				area() { return 3.14 * this.r * this.r; }
			}
			var c = Circle("red", 2);
			c.describe();
			print c.area();
			""";
		runCase("Inheritance & Super", source);
	}

	public static void testLogicalShortCircuit() {
		String source = """
			fun right() { print "right"; return true; }
			print true or right();
			print false and right();
			""";
		runCase("Logical Short-Circuit", source);
	}

	public static void testWarningsAndErrors() {
		String source = """
			var x = 1;
			var x = 2;
			print undef;
			fun add(a,b){ return a+b; }
			add(1,2,3);
			"hello" - 5;
			""";
		runCase("Warnings & Errors", source);
	}
}
