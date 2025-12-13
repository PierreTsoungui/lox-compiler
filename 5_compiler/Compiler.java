public class Compiler {

    private final Scanner scanner ; 
    private final ParserMain parser ;  
    // Map: Name der lokalen Variable -> Slot-Index
    private final Map<String, Integer> locals = new HashMap<>();
    private int localCount = 0;

    public CompiledFunction compile(String source){
       
        // 2. Token erzeugen
        List<Token> tokens = scanner.scan(source);

        // 3. AST erzeugen
        parser= New ParserMain(tokens);
        List<Stmt> statements = parser.program().declaration();

        // 4. ByteCode-Text generieren
        String byteCodeText = generateByteCode(statements);

        // 5. Assembler aufrufen: Text -> Op-Liste
        Assembler assembler = new Assembler();
        return assembler.assemble(byteCodeText);
    }

    private String generateByteCode(List<Stmt> statements) {
        StringBuilder sb = new StringBuilder();
        for (Stmt stmt : statements) {
            sb.append(stmtToByteCode(stmt)).append("\n");
        }
        return sb.toString();
    }

   private String stmtToByteCode(Stmt stmt) {
    if (stmt instanceof Stmt.Expression expr) return ExprTobyteCode(expr);
        
   }


    private String exprToByteCode(Expr expr) {
        return switch (expr) {
            case Expr.Literal lit -> switch (lit.value()) {
                case Double d -> "OP_CONSTANT " + d;
                case Boolean b -> b ? "OP_TRUE" : "OP_FALSE";
                case String s -> "OP_CONSTANT \"" + s + "\"";
                case null -> "OP_NIL";
                default -> throw new RuntimeException("Unknown literal type: " + lit.value());
            };
            case Expr.Variable var -> {
                String name = var.name().lexeme();
                yield locals.containsKey(name)
                ? "OP_GET_LOCAL " + locals.get(name)
                : "OP_GET_GLOBAL " + name;
b           }

            case Expr.Unary un -> {
                String operand = exprToByteCode(un.right());
                yield switch (un.operator().type()) {
                    case MINUS -> operand + "\nOP_NEGATE";
                    case BANG  -> operand + "\nOP_NOT";
                    default -> throw new RuntimeException("Unknown unary operator: " + un.operator());
                };
            }
            case Expr.Logical b -> {
                String left = exprToByteCode(b.left());
                String  right = exprToByteCode(b.right());
                String opCode = switch (b.operator().type()) {
                    case PLUS -> "OP_ADD";
                    case MINUS -> "OP_SUBTRACT";
                    case STAR -> "OP_MULTIPLY";
                    case SLASH -> "OP_DIVIDE";
                    default -> throw new RuntimeException("Unknown operator: " + b.operator());
                };
                yield left + "\n" + right + "\n" + opCode;
            }
            case Expr.THIS
            // hier kannst du weitere Expr-Fälle ergänzen, z.B. Binary, Call, Grouping
            default -> throw new RuntimeException("Unknown expression type: " + expr);
        };
    }


}