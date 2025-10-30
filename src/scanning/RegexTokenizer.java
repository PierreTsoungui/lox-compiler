package scanning;

import java.util.*;
import java.util.regex.*;
public class RegexTokenizer {
    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    int line = 1;
    private static final Pattern masterPattern = buildMasterPattern();
    boolean error = false;


    public RegexTokenizer(String source) {
        this.source = source;
    }
  
    private static Pattern buildMasterPattern() {
        String joined = String.join("|", Arrays.stream(TokenPattern.values())
            .map(tp -> tp.regex)
            .toList());
        return Pattern.compile(joined);
    }

    public List<Token> tokenize() {
         Matcher matcher = masterPattern.matcher(source);

        while (matcher.find()) {
            TokenPattern t = Arrays.stream(TokenPattern.values())
                                    .filter(tp -> matcher.group(tp.name()) != null)
                                    .findFirst()
                                    .get();

            t.handler.accept(matcher, this);
            if ( error || t == TokenPattern.UNKNOWN) break; 
        }

        if (!error) {
            addToken(TokenType.EOF, "", null);
         return tokens;   
        } else {
            return Collections.emptyList();  
        }
    }


    void handleSeparator(String sepr, int endIndex) {
        if (sepr.equals(".")) {
            int nextIndex = endIndex;

            // Fall 1: Punkt vor Zahl (z. B. .5) → ungültig
            if (nextIndex < source.length() && Character.isDigit(source.charAt(nextIndex))) {
               System.err.printf("Ungültige Zahl: .%s in line: %d%n", source.charAt(nextIndex), line);
                error = true;
                return;
            }

            // Fall 2: Punkt allein oder an falscher Stelle → Fehler
            if (nextIndex == source.length() ||
                 (!Character.isLetterOrDigit(source.charAt(nextIndex)) && source.charAt(nextIndex) != '_')) {
                 System.err.printf("Unerwartetes '.' in line: %d%n" , line);
                  error = true;
                 return;
            }
        }
        if(!error){
            TokenType type = TokenType.sep.get(sepr);
            addToken(type, sepr, null);
        }
    }
   
    
    void addToken(TokenType type , String lexem, Object value){
        tokens.add( new Token(type, lexem, value, line));
    }
 
}
