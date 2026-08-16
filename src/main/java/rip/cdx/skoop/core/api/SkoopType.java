package rip.cdx.skoop.core.api;

import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.registrations.Classes;
import ch.njol.skript.util.Utils;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Nullable;
import rip.cdx.skoop.Skoop;

@Getter
@RequiredArgsConstructor
public class SkoopType {

    private final String name;

    private final @Nullable ClassInfo<?> skriptType;
    private final @Nullable SkoopClass skoopClass;

    private final boolean plural;

    public boolean isSkriptType() {
        return skriptType != null;
    }

    public boolean isSkoopType() {
        return skoopClass != null;
    }

    public boolean accepts(Object value) {
        if (value == null) {
            return true;
        }

        if (skoopClass != null) {
            if (!(value instanceof SkoopObject object)) {
                return false;
            }

            return object.getSkoopClass() == skoopClass;
        }

        if (skriptType != null) {
            return skriptType.getC().isInstance(value);
        }

        return false;
    }

    public static @Nullable SkoopType resolveType(String input) {
        String rawType = input.trim();

        SkoopClass skoopClass = Skoop.getInstance().getClassRegistry().get(rawType);

        if (skoopClass != null) {
            return new SkoopType(rawType, null, skoopClass, false);
        }

        Utils.PluralResult plural = Utils.isPlural(rawType);
        String typeName = plural.updated();

        ClassInfo<?> classInfo = Classes.getClassInfoFromUserInput(typeName);

        if (classInfo != null) {
            return new SkoopType(typeName, classInfo, null, plural.plural());
        }

        return null;
    }
}