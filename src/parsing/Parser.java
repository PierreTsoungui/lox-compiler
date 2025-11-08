package parsing;

import scanning.Token;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import scanning.*;

public sealed interface Parser<T>
        permits Success, Fail, Item, Or, And, Many, Parser.Lazy,MapParser,FlatMap {

    // ---------------- Parse-Methode ----------------
    Result<T> parse(List<Token> tokens);

    
   // ---------------- map-Methode ----------------
    default <R> Parser<R> map(Function<T, R> f) {
        return new Lazy<>(() -> new MapParser<>(this, f));
    }

    // ---------------- flatMap-Methode ----------------
    default <R> Parser<R> flatMap(Function<T, Parser<R>> f) {
         return new Lazy<>(() -> new FlatMap<>(this, f));
    };




    // ---------------- Lazy-Klasse für rekursive Parser ----------------
    final class Lazy<T> implements Parser<T> {
        private final Supplier<Parser<T>> supplier;
        private Parser<T> cached = null;

        public Lazy(Supplier<Parser<T>> supplier) {
            this.supplier = supplier;
        }

        @Override
        public Result<T> parse(List<Token> tokens) {
            if (cached == null) cached = supplier.get();
            return cached.parse(tokens);
        }
    }
}


// ---------------- Result-Klasse ----------------
record Result<T>(Optional<T> recognized, List<Token> rest) {

    static <T> Result<T> of(T recognized, List<Token> rest) {
        if (recognized == null) return new Result<>(Optional.empty(), rest);
        return new Result<>(Optional.of(recognized), rest);
    }

    boolean hasFailed() { return recognized.isEmpty(); }
    boolean hasNotFailed() { return !hasFailed(); }

   
}

// ---------------- Success & Fail ----------------
record Success<T>(T value) implements Parser<T> {
    @Override
    public Result<T> parse(List<Token> tokens) {
        return Result.of(value, tokens);
    }
}

record Fail<T>() implements Parser<T> {
    @Override
    public Result<T> parse(List<Token> tokens) {
        return Result.of(null, tokens);
    }
}

//----- Map& FlatMap
record MapParser<T, R>(Parser<T> parser, Function<T, R> f) implements Parser<R> {

    @Override
    public Result<R> parse(List<Token> tokens) {
        Result<T> res = parser.parse(tokens);
        if (res.hasFailed()) return new Fail<R>().parse(tokens);
        return Result.of(f.apply(res.recognized().get()), res.rest());
    }
}
record FlatMap<T, R>(Parser<T> parser, Function<T,Parser<R>>f) implements Parser<R> {

    @Override
    public Result<R> parse(List<Token> tokens) {
        Result<T> res = parser.parse(tokens);
        if (res.hasFailed()) return new Fail<R>().parse(tokens);
        T value= res.recognized().get() ;
        Parser<R> nextParser= f.apply(value);
        return nextParser.parse(res.rest());
    }
}

record Item(TokenType expectedType) implements Parser<Token> {

    @Override
    public Result<Token> parse(List<Token> tokens) {
        if (tokens.isEmpty()) {
            return new Fail<Token>().parse(tokens);
        }

        Token first = tokens.get(0);
        List<Token> rest = tokens.subList(1, tokens.size());

        if (first.type() == expectedType) {
            return Result.of(first, rest);
        } else {
            return new Fail<Token>().parse(tokens);
        }
    }
}


record Or<T>(Parser<T>... parsers) implements Parser<T> {
 public @SafeVarargs Or{}
    @Override
    public Result<T> parse(List<Token> tokens) {
        return Arrays.stream(parsers)
                     .map(p -> p.parse(tokens))
                     .filter(Result::hasNotFailed)
                     .findFirst()
                     .orElse(new Result<>(Optional.empty(), tokens));
    }
}


