package rip.cdx.skoop.core;

import org.jetbrains.annotations.Nullable;
import rip.cdx.skoop.api.SkoopClass;
import rip.cdx.skoop.api.SkoopParameter;
import rip.cdx.skoop.api.SkoopType;

import java.util.List;

/**
 * Carries the class and the parameter list of the constructor or method body currently being
 * parsed, so that {@code this} and argument expressions inside the body can resolve their declared
 * Skoop type at parse time.
 * <p>
 * Scoped to the parsing thread and cleared once the body has been loaded.
 */
public final class SkoopParseContext {

    private static final ThreadLocal<SkoopClass> CLASS = new ThreadLocal<>();
    private static final ThreadLocal<List<SkoopParameter>> PARAMETERS = new ThreadLocal<>();
    private static final ThreadLocal<SkoopType> RETURN_TYPE = new ThreadLocal<>();

    private SkoopParseContext() {
    }

    /**
     * @param returnType the declared return type of the member, or null for constructors and void methods
     */
    public static void enter(SkoopClass skoopClass, List<SkoopParameter> parameters, @Nullable SkoopType returnType) {
        CLASS.set(skoopClass);
        PARAMETERS.set(parameters);
        RETURN_TYPE.set(returnType);
    }

    public static void exit() {
        CLASS.remove();
        PARAMETERS.remove();
        RETURN_TYPE.remove();
    }

    /**
     * @return the class whose body is currently being parsed, or null if outside a class member
     */
    public static @Nullable SkoopClass getCurrentClass() {
        return CLASS.get();
    }

    /**
     * @return the declared return type of the member being parsed, or null if it returns nothing
     */
    public static @Nullable SkoopType getReturnType() {
        return RETURN_TYPE.get();
    }

    /**
     * @return the declared parameter named {@code name}, or null if the current member has no such parameter
     */
    public static @Nullable SkoopParameter getParameter(String name) {
        List<SkoopParameter> parameters = PARAMETERS.get();
        if (parameters == null) {
            return null;
        }

        for (SkoopParameter parameter : parameters) {
            if (parameter.name().equalsIgnoreCase(name)) {
                return parameter;
            }
        }

        return null;
    }
}
