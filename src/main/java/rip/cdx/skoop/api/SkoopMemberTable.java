package rip.cdx.skoop.api;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The fields and methods declared at one level of a {@link SkoopClass}.
 * <p>
 * A class holds two of these — one for instance members and one for static members — so that both
 * get the same case-insensitive lookup and overload resolution without duplicating it.
 */
@Getter
public class SkoopMemberTable {

    private final Map<String, SkoopField> fields = new LinkedHashMap<>();
    private final Map<String, List<SkoopMethod>> methods = new LinkedHashMap<>();

    private static String key(String name) {
        return name.toLowerCase(Locale.ENGLISH);
    }

    public void addField(SkoopField field) {
        fields.put(key(field.getName()), field);
    }

    public @Nullable SkoopField getField(String name) {
        return fields.get(key(name));
    }

    public boolean hasField(String name) {
        return fields.containsKey(key(name));
    }

    public void addMethod(SkoopMethod method) {
        methods.computeIfAbsent(key(method.getName()), ignored -> new ArrayList<>()).add(method);
    }

    /**
     * @return the overload of {@code name} accepting the supplied arguments, or null if there is none
     */
    public @Nullable SkoopMethod findMethod(String name, Object[] arguments) {
        for (SkoopMethod method : getMethods(name)) {
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
}
