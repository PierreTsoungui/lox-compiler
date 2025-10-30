package scanning;
import java.util.function.BiConsumer; 
import java.util.regex.*;
 public enum TokenPattern {

        NUMBER("(?<NUMBER>[0-9]+(\\.[0-9]+)?)",
            (m, t) -> t.addToken(TokenType.NUMBER, m.group("NUMBER"), Double.parseDouble(m.group("NUMBER")))
        ),

        STRING("(?<STRING>\"[^\"]*\")",
            (m, t) -> {
                String v = m.group("STRING");
                t.addToken(TokenType.STRING, v, v.substring(1, v.length() - 1));
            }
        ),

        IDENTIFIER("(?<IDENTIFIER>[a-zA-Z_][a-zA-Z0-9_]*)",
            (m, t) -> {
                String v = m.group("IDENTIFIER");
                TokenType type = TokenType.keywords.getOrDefault(v, TokenType.IDENTIFIER);
                t.addToken(type, v, null);
            }
        ),

        SEPARATOR("(?<SEPARATOR>[(){}.,;\\[\\]])",
            (m, t) -> t.handleSeparator(m.group("SEPARATOR"), m.end())
        ),
        OPERATOR( "(?<OPERATOR>==|!=|<=|>=|\\+|\\-|\\*|/|=|<|>|!)",
        (m,t)->{
             String v = m.group("OPERATOR");
             TokenType type = TokenType.op.get(v);
             t.addToken(type,v,null);
        }
        ),
        NEWLINE("(?<NEWLINE>\\n)",
            (_m, t) -> t.line++
        ),

        WHITESPACE("(?<WHITESPACE>[ \\t\\r]+)",
            (_m, _t) -> {} // ignorieren
        ),

        UNKNOWN("(?<UNKNOWN>.)",
            (m, t) -> { t.error =true; System.err.println("Unbekanntes Zeichen: " + m.group()+"in Zeile :" + t.line); }
        );

        final String regex;
        final BiConsumer<Matcher, RegexTokenizer> handler;

      private  TokenPattern(String regex, BiConsumer<Matcher, RegexTokenizer> handler) {
            this.regex = regex;
            this.handler = handler;
        }
    }
