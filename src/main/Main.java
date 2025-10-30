package main;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import scanning.*;
public class Main {
    public static void main(String [] args) throws Exception {

        if (args.length < 2) {
            System.out.println("Usage: java Main [--tokens] <filename>");
            return;
        }
        String option = args[0];
        String filename = args[1];
        String source = Files.readString(Paths.get(filename));
        if (option.equals("--tokens")) {
            RegexTokenizer scanner = new RegexTokenizer(source);
            List<Token> tokens = scanner.tokenize();
            System.out.println("=== Tokens ===");
            for (Token token : tokens) {
                System.out.println(token);
            }
        } else {
            System.out.println("Unknown option: " + option);
        }
    }

}
