package rip.cdx.skoop.api;

import ch.njol.skript.lang.Expression;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

/**
 * A declared field of a {@link SkoopClass}.
 * <p>
 * An abstract field declares only a name and a type: it holds no value and has no default, and a
 * concrete subclass must redeclare it before the class can be instantiated.
 */
@Getter
public class SkoopField {

    private final String name;
    private final SkoopType type;
    private final @Nullable Expression<?> defaultValue;
    private final boolean isAbstract;

    public SkoopField(String name, SkoopType type, @Nullable Expression<?> defaultValue, boolean isAbstract) {
        this.name = name;
        this.type = type;
        this.defaultValue = defaultValue;
        this.isAbstract = isAbstract;
    }

    public SkoopField(String name, SkoopType type, @Nullable Expression<?> defaultValue) {
        this(name, type, defaultValue, false);
    }

    @Override
    public String toString() {
        return (isAbstract ? "abstract " : "") + name + ": " + type.toSignatureString();
    }
}
