import java.util.List;

/**
 * Interface für aufrufbare Lox-Werte (Funktionen, Klassen, native Funktionen).
 * Diese Datei wurde extrahiert, um Zyklen zwischen Interpreter und LoxValue zu vermeiden.
 * 
 * Interpreter wird als Object übergeben und muss dann gecastet werden.
 * Dies vermeidet den Zyklus beim Laden in JShell.
 */
public interface LoxCallable {
    int arity();
     LoxValue call(Object interpreter, List<LoxValue> arguments);
}

