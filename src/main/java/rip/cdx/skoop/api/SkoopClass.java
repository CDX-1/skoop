package rip.cdx.skoop.api;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A class declared by a script. Holds the declared members; instances are {@link SkoopObject}s.
 * <p>
 * Members are keyed case-insensitively, but keep the casing they were declared with for error messages.
 */
@Getter
public class SkoopClass {

    private final String name;
    private final Map<String, SkoopField> fields = new LinkedHashMap<>();
    private final Map<String, List<SkoopMethod>> methods = new LinkedHashMap<>();
    private final List<SkoopConstructor> constructors = new ArrayList<>();

    public SkoopClass(String name) {
        this.name = name;
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ENGLISH);
    }

    // FIELDS

    public void addField(SkoopField field) {
        fields.put(key(field.getName()), field);
    }

    public @Nullable SkoopField getField(String name) {
        return fields.get(key(name));
    }

    public boolean hasField(String name) {
        return fields.containsKey(key(name));
    }

    // METHODS

    public void addMethod(SkoopMethod method) {
        methods.computeIfAbsent(key(method.getName()), ignored -> new ArrayList<>()).add(method);
    }

    /**
     * @return the overload of {@code name} accepting the supplied arguments, or null if there is none
     */
    public @Nullable SkoopMethod findMethod(String name, Object[] arguments) {
        List<SkoopMethod> overloads = methods.get(key(name));
        if (overloads == null) {
            return null;
        }

        for (SkoopMethod method : overloads) {
            if (method.matches(arguments)) {
                return method;
            }
        }

        return null;
    }

    /**
     * @return every declared overload of {@code name}, empty if the method does not exist
     */
    public List<SkoopMethod> getMethods(String name) {
        return methods.getOrDefault(key(name), List.of());
    }

    public boolean hasMethod(SkoopMethod method) {
        return getMethods(method.getName()).stream()
                .anyMatch(existing -> existing.hasSameSignature(method));
    }

    // CONSTRUCTORS

    public void addConstructor(SkoopConstructor constructor) {
        constructors.add(constructor);
    }

    public @Nullable SkoopConstructor findConstructor(Object[] arguments) {
        for (SkoopConstructor constructor : constructors) {
            if (constructor.matches(arguments)) {
                return constructor;
            }
        }

        return null;
    }

    public boolean hasConstructor(SkoopConstructor constructor) {
        return constructors.stream()
                .anyMatch(existing -> existing.hasSameParameters(constructor));
    }

    @Override
    public String toString() {
        return "class " + name;
    }
}
