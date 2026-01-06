import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Lox {
    private static final Interpreter interpreter = new Interpreter();
    static boolean hadError = false;
    static boolean hadRuntimeError = false;
    
    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            System.out.println("Usage: jlox [script]");
            System.exit(64);
        } else if (args.length == 1) {
            runFile(args[0]);
        } else {
            runPrompt();
        }
    }
    
    private static void runFile(String path) throws IOException {
        var bytes = Files.readAllBytes(Path.of(path));
        run(new String(bytes, Charset.defaultCharset()));
        
        if (hadError) System.exit(65);
        if (hadRuntimeError) System.exit(70);
    }
    
    private static void runPrompt() throws IOException {
        var input = new InputStreamReader(System.in);
        var reader = new BufferedReader(input);
        
        while (true) {
            System.out.print("> ");
            var line = reader.readLine();
            if (line == null) break;
            run(line);
            hadError = false;
            hadRuntimeError = false;
        }
    }
    
    private static void run(String source) {
        Scanner scanner = new Scanner(source);
        List<Token> tokens = scanner.scanTokens();
        var parser = new Parser(tokens);
        List<Stmt> statements = parser.parse();
        
        if (hadError) return;
        
        var resolver = new Resolver(interpreter);
        resolver.resolve(statements);
        
        if (hadError) return;
        
        interpreter.interpret(statements);
    }
    
    // ========== ERROR HANDLING METHODS ==========
    
    public static void error(int line, String message) {
        report(line, "", message);
    }
    
    public static void error(Token token, String message) {
        if (token.type() == TokenType.EOF) {
            report(token.line(), " at end", message);
        } else {
            report(token.line(), " at '" + token.lexeme() + "'", message);
        }
    }
    
    public static void runtimeError(RuntimeError error) {
        System.err.println("[line " + error.token.line() + "] " + 
                          error.getMessage());
        hadRuntimeError = true;
    }
    
    private static void report(int line, String where, String message) {
        System.err.println("[line " + line + "] Error" + where + ": " + message);
        hadError = true;
    }

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

        print clock();  // native function test
        """;

    System.out.println("=== Running Lox Test ===");
    run(source);
    System.out.println("=== Test Finished ===");
    }

    
}