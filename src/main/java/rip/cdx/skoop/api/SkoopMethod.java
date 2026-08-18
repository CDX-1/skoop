package rip.cdx.skoop.api;

import ch.njol.skript.lang.Trigger;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A method of a {@link SkoopClass}. Methods are overloadable on their parameter types.
 * <p>
 * An abstract method carries no body: it declares the signature a concrete subclass has to
 * provide, and calling one is only ever possible if that subclass stopped being loaded.
 */
@Getter
public class SkoopMethod extends SkoopExecutable {

    private final String name;
    private final @Nullable SkoopType returnType;

    /** The class the method is declared in; used for the "not implemented" error messages. */
    private final String declaringClassName;

    public SkoopMethod(String name, List<SkoopParameter> parameters, @Nullable SkoopType returnType,
                       @Nullable Trigger trigger, String declaringClassName) {
        super(parameters, trigger);
        this.name = name;
        this.returnType = returnType;
        this.declaringClassName = declaringClassName;
    }

    public boolean isVoid() {
        return returnType == null;
    }

    public boolean isAbstract() {
        return getTrigger() == null;
    }

    /**
     * @return whether {@code other} would be an indistinguishable overload of this method
     */
    public boolean hasSameSignature(SkoopMethod other) {
        return name.equalsIgnoreCase(other.name) && hasSameParameters(other);
    }

    /**
     * @return whether {@code other} declares the same return type, i.e. is a legal override
     */
    public boolean hasSameReturnType(SkoopMethod other) {
        if (returnType == null || other.returnType == null) {
            return returnType == other.returnType;
        }

        return returnType.isSameAs(other.returnType);
    }

    @Override
    public String toString() {
        String declaration = (isAbstract() ? "abstract " : "") + name + getSignature();
        return returnType == null ? declaration : declaration + " returns " + returnType.toSignatureString();
    }
}
