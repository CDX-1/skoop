package rip.cdx.skoop.core.api;

import lombok.Getter;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
public class SkoopClass {
    private final String name;
    private final Map<String, SkoopField> fields = new LinkedHashMap<>();
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
