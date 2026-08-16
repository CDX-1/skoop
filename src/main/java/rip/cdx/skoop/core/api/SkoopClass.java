package rip.cdx.skoop.core.api;

import lombok.Getter;

import javax.annotation.Nullable;
import java.util.*;

@Getter
public class SkoopClass {
    private final String name;
    private final Map<String, SkoopField> fields = new LinkedHashMap<>();
    private final Map<String, List<SkoopMethod>> methods = new LinkedHashMap<>();
    private final List<SkoopConstructor> constructors = new ArrayList<>();

    public SkoopClass(String name) {
        this.name = name;
    }

    public void addField(SkoopField field) {
        fields.put(field.getName().toLowerCase(), field);
    }

    public SkoopField getField(String name) {
        return fields.get(name.toLowerCase());
    }

    public boolean hasField(String name) {
        return fields.containsKey(name);
    }

    public void addMethod(SkoopMethod method) {
        methods.computeIfAbsent(method.getName().toLowerCase(Locale.ENGLISH), key -> new ArrayList<>()).add(method);
    }

    public @Nullable SkoopMethod findMethod(String name, Object[] arguments) {
        List<SkoopMethod> overloads = methods.get(name.toLowerCase(Locale.ENGLISH));
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

    public boolean hasMethod(SkoopMethod method) {
        List<SkoopMethod> overloads = methods.get(method.getName().toLowerCase(Locale.ENGLISH));
        if (overloads == null) {
            return false;
        }
        return overloads.stream().anyMatch(existing -> existing.hasSameSignature(method));
    }

    public void addConstructor(SkoopConstructor constructor) {
        constructors.add(constructor);
    }

    public boolean hasConstructor(SkoopConstructor constructor) {
        return constructors.stream()
                .anyMatch(existing -> existing.hasSameSignature(constructor));
    }

    @Nullable
    public SkoopConstructor findConstructor(Object[] arguments) {
        for (SkoopConstructor constructor : constructors) {
            if (constructor.matches(arguments)) {
                return constructor;
            }
        }

        return null;
    }
}
