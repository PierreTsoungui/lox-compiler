import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
public class TestParser {

    public static void testFile(String filename) throws Exception {
    String source = Files.readString(Path.of(filename));
    ParserMain p = ParserMain.fromSource(source);

    List<Token> tokens = p.tokens;

    Result<Stmt> result = p.classDecl().parse(tokens);

    if (result.hasFailed()) {
        throw new RuntimeException(
            "Parsing-Fehler bei Token: " + tokens
        );
    }

    // AST ausgeben
    List<Stmt> stm = result.recognized().get();
    System.out.println(AstDot.toDot(program));

    System.out.println("OK → Datei vollständig geparst.");
}

}
