import java.util.List;

public class TestScannerRunner {
    public static void main(String[] args) {
        // simple test input demonstrating tokens
        String source = "var x = 42; print x;\nfun greet() { print \"hello\"; }\n// a comment\n";
        Scanner.test(source);
    }
}
