package parsing;

import scanning.Token;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

public sealed interface Parser<T>
        permits Success, Fail, Item, Or, And, Many, Lazy {

    Result<T> parse(List<Token> tokens);
   
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
