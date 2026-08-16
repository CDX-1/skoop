package rip.cdx.skoop.api;

import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.registrations.Classes;
import ch.njol.skript.util.Utils;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import rip.cdx.skoop.Skoop;

/**
 * A declared type of a field, parameter or method return value.
 * Either wraps a Skript {@link ClassInfo} or a user declared {@link SkoopClass}.
 * <p>
 * Skoop classes are referenced <b>by name</b> rather than by instance: a type outlives the class
 * declaration it was resolved from, and reloading the declaring script replaces that instance.
 * Holding the instance would leave every other class checking against a stale definition and
 * rejecting values with a "expects Dog, received Dog" style error.
 */
@Getter
public class SkoopType {

    private final String name;
    private final @Nullable ClassInfo<?> skriptType;
    private final @Nullable String skoopClassName;
    private final boolean plural;

    private SkoopType(String name, @Nullable ClassInfo<?> skriptType, @Nullable String skoopClassName, boolean plural) {
        this.name = name;
        this.skriptType = skriptType;
        this.skoopClassName = skoopClassName;
        this.plural = plural;
    }

    public static SkoopType ofSkoopClass(String className, boolean plural) {
        return new SkoopType(className, null, className, plural);
    }

    public static SkoopType ofSkriptType(ClassInfo<?> classInfo, boolean plural) {
        return new SkoopType(classInfo.getCodeName(), classInfo, null, plural);
    }

    /**
     * Resolves a user written type name, e.g. {@code players}, {@code Dog} or {@code Dogs}.
     * <p>
     * The raw name is tried against the class registry first so that a class whose name happens to
     * end in {@code s} stays reachable; only then is it depluralised and tried again.
     *
     * @return the resolved type, or null if no Skoop class or Skript type matches
     */
    public static @Nullable SkoopType resolveType(String input) {
        String rawType = input.trim();

        if (Skoop.getInstance().getClassRegistry().contains(rawType)) {
            return ofSkoopClass(rawType, false);
        }

        Utils.PluralResult plural = Utils.isPlural(rawType);
        String singular = plural.updated();

        if (plural.plural() && Skoop.getInstance().getClassRegistry().contains(singular)) {
            return ofSkoopClass(singular, true);
        }

        ClassInfo<?> classInfo = Classes.getClassInfoFromUserInput(singular);
        if (classInfo != null) {
            return ofSkriptType(classInfo, plural.plural());
        }

        return null;
    }

    public boolean isSkriptType() {
        return skriptType != null;
    }

    public boolean isSkoopType() {
        return skoopClassName != null;
    }

    /**
     * Looks the declared Skoop class up in the registry.
     *
     * @return the class, or null if it is a Skript type or its script is no longer loaded
     */
    public @Nullable SkoopClass getSkoopClass() {
        if (skoopClassName == null) {
            return null;
        }

        return Skoop.getInstance().getClassRegistry().get(skoopClassName);
    }

    /**
     * @return the Java class values of this type are represented by
     */
    public Class<?> getValueClass() {
        if (skoopClassName != null) {
            return SkoopObject.class;
        }

        return skriptType != null ? skriptType.getC() : Object.class;
    }

    public boolean accepts(@Nullable Object value) {
        if (value == null) {
            return true;
        }

        if (skoopClassName != null) {
            // Compared by name so that instances created before a reload still satisfy the type.
            return value instanceof SkoopObject object
                    && object.getClassName().equalsIgnoreCase(skoopClassName);
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
