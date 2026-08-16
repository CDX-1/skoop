package rip.cdx.skoop.api;

import ch.njol.skript.lang.Trigger;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A method of a {@link SkoopClass}. Methods are overloadable on their parameter types.
 */
@Getter
public class SkoopMethod extends SkoopExecutable {

    private final String name;
    private final @Nullable SkoopType returnType;

    public SkoopMethod(String name, List<SkoopParameter> parameters, @Nullable SkoopType returnType, Trigger trigger) {
        super(parameters, trigger);
        this.name = name;
        this.returnType = returnType;
    }

    public boolean isVoid() {
        return returnType == null;
    }

    /**
     * @return whether {@code other} would be an indistinguishable overload of this method
     */
    public boolean hasSameSignature(SkoopMethod other) {
        return name.equalsIgnoreCase(other.name) && hasSameParameters(other);
    }

    @Override
    public String toString() {
        String declaration = name + getSignature();
        return returnType == null ? declaration : declaration + " returns " + returnType.toSignatureString();
    }
}
