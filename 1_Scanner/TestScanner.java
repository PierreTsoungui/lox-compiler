public class TestScanner {
    public static void main(String[] args) {
        System.out.println("=== Test 1: Einfache Variablen & Zahlen ===");
        String code1 = """
            var x = 42;
            var y = 3.14;
            print x + y;
        """;
        Scanner.test(code1);

        System.out.println("\n=== Test 2: Strings & Operatoren ===");
        String code2 = """
            var greeting = "Hello, World!";
            print greeting;
            if (x > 10) { x = x - 1; }
        """;
        Scanner.test(code2);

        System.out.println("\n=== Test 3: Kommentare & Whitespaces ===");
        String code3 = """
            // Dies ist ein Kommentar
            var a = 5;   // Inline-Kommentar
            var b = a * 2;
            print b;
        """;
        Scanner.test(code3);

        System.out.println("\n=== Test 4: Klassen & Funktionen ===");
        String code4 = """
            class Shape {
                init(color) { this.color = color; }
                describe() { print this.color + " shape"; }
            }
            fun add(a, b) { return a + b; }
            var circle = Shape();
            print add(1, 2);
        """;
        Scanner.test(code4);

        System.out.println("\n=== Test 5: Ungültige Zeichen (Fehler) ===");
        String code5 = """
            var x = 10;
            var y = @;   // ungültiges Zeichen
        """;
        Scanner.test(code5);
    }
}
