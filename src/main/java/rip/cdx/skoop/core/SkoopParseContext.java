package rip.cdx.skoop.core;

import org.jetbrains.annotations.Nullable;
import rip.cdx.skoop.api.SkoopClass;
import rip.cdx.skoop.api.SkoopParameter;
import rip.cdx.skoop.api.SkoopType;

import java.util.List;

/**
 * Carries what the member body currently being parsed belongs to — its class, its parameters, its
 * return type, and whether it is static — so that {@code this} and argument expressions inside the
 * body can resolve their declared Skoop type, or be rejected, at parse time.
 * <p>
 * Scoped to the parsing thread and cleared once the body has been loaded.
 */
public final class SkoopParseContext {

    private static final ThreadLocal<SkoopClass> CLASS = new ThreadLocal<>();
    private static final ThreadLocal<List<SkoopParameter>> PARAMETERS = new ThreadLocal<>();
    private static final ThreadLocal<SkoopType> RETURN_TYPE = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> STATIC = new ThreadLocal<>();

    private SkoopParseContext() {
    }

    /**
     * @param returnType the declared return type of the member, or null for constructors and void methods
     * @param isStatic   whether the body belongs to the class rather than to an instance
     */
    public static void enter(SkoopClass skoopClass, List<SkoopParameter> parameters,
                             @Nullable SkoopType returnType, boolean isStatic) {
        CLASS.set(skoopClass);
        PARAMETERS.set(parameters);
        RETURN_TYPE.set(returnType);
        STATIC.set(isStatic);
    }

    public static void exit() {
        CLASS.remove();
        PARAMETERS.remove();
        RETURN_TYPE.remove();
        STATIC.remove();
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
     * @return whether the body being parsed is static, and therefore has no instance
     */
    public static boolean isStatic() {
        return Boolean.TRUE.equals(STATIC.get());
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
