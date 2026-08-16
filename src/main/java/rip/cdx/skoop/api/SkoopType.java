package rip.cdx.skoop.api;

import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.registrations.Classes;
import ch.njol.skript.util.Utils;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Nullable;
import rip.cdx.skoop.Skoop;

/**
 * A declared type of a field, parameter or method return value.
 * Either wraps a Skript {@link ClassInfo} or a user defined {@link SkoopClass}.
 */
@Getter
@RequiredArgsConstructor
public class SkoopType {

    private final String name;
    private final @Nullable ClassInfo<?> skriptType;
    private final @Nullable SkoopClass skoopClass;
    private final boolean plural;

    /**
     * Resolves a user written type name, e.g. {@code players} or {@code Dog}.
     *
     * @return the resolved type, or null if no Skoop class or Skript type matches
     */
    public static @Nullable SkoopType resolveType(String input) {
        String rawType = input.trim();
        Utils.PluralResult plural = Utils.isPlural(rawType);

        SkoopClass skoopClass = Skoop.getInstance().getClassRegistry().get(rawType);
        if (skoopClass != null) {
            return new SkoopType(rawType, null, skoopClass, false);
        }

        String typeName = plural.updated();
        ClassInfo<?> classInfo = Classes.getClassInfoFromUserInput(typeName);
        if (classInfo != null) {
            return new SkoopType(typeName, classInfo, null, plural.plural());
        }

        return null;
    }

    public boolean isSkriptType() {
        return skriptType != null;
    }

    public boolean isSkoopType() {
        return skoopClass != null;
    }

    /**
     * @return the Java class values of this type are represented by
     */
    public Class<?> getValueClass() {
        if (skoopClass != null) {
            return SkoopObject.class;
        }

        return skriptType != null ? skriptType.getC() : Object.class;
    }

    public boolean accepts(@Nullable Object value) {
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

    /**
     * @return whether this type is interchangeable with {@code other} for overload resolution
     */
    public boolean isSameAs(SkoopType other) {
        return plural == other.plural && name.equalsIgnoreCase(other.name);
    }

    /**
     * @return this type as it appears in a signature, e.g. {@code string[]}
     */
    public String toSignatureString() {
        return plural ? name + "[]" : name;
    }

    @Override
    public String toString() {
        return toSignatureString();
    }
}
