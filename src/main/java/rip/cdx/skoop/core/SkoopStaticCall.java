package rip.cdx.skoop.core;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Expression;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import rip.cdx.skoop.Skoop;
import rip.cdx.skoop.api.SkoopClass;
import rip.cdx.skoop.api.SkoopMethod;
import rip.cdx.skoop.api.SkoopType;

import java.util.List;
import java.util.function.Consumer;

/**
 * The parsed form of {@code call <Class>.<method> [with %objects%]}, shared by the effect and the
 * expression variant of a static method call.
 * <p>
 * The class is referenced by name and looked up per call, so a reload of the declaring script is
 * picked up rather than leaving the call bound to a dead class.
 */
public final class SkoopStaticCall {

    private final String className;
    private final String methodName;
    private final @Nullable Expression<?> arguments;
    private final @Nullable SkoopType returnType;

    private SkoopStaticCall(String className, String methodName,
                            @Nullable Expression<?> arguments, @Nullable SkoopType returnType) {
        this.className = className;
        this.methodName = methodName;
        this.arguments = arguments;
        this.returnType = returnType;
    }

    /**
     * Parses a static call, reporting parse errors itself.
     *
     * @return the parsed call, or null if the class or method does not exist
     */
    public static @Nullable SkoopStaticCall parse(String className, String methodName, @Nullable Expression<?> rawArguments) {
        SkoopClass skoopClass = Skoop.getInstance().getClassRegistry().get(className);
        if (skoopClass == null) {
            Skript.error("There is no Skoop class named '" + className + "'.");
            return null;
        }

        List<SkoopMethod> overloads = skoopClass.getStaticMethods(methodName);
        if (overloads.isEmpty()) {
            Skript.error("Class '" + skoopClass.getName() + "' has no static method named '" + methodName + "'.");
            return null;
        }

        Expression<?> arguments = null;

        if (rawArguments != null) {
            arguments = rawArguments.getConvertedExpression(Object.class);

            if (arguments == null) {
                Skript.error("Could not parse the arguments for static method '" + methodName + "'.");
                return null;
            }
        }

        return new SkoopStaticCall(className, methodName, arguments, resolveReturnType(overloads));
    }

    /**
     * @return the shared return type of every overload, or null if they disagree or return nothing
     */
    private static @Nullable SkoopType resolveReturnType(List<SkoopMethod> overloads) {
        SkoopType returnType = overloads.getFirst().getReturnType();
        if (returnType == null) {
            return null;
        }

        for (SkoopMethod overload : overloads) {
            if (overload.getReturnType() == null || !overload.getReturnType().isSameAs(returnType)) {
                return null;
            }
        }

        return returnType;
    }

    /**
     * @param onError receives a human readable message when the class or an overload is missing
     * @return the method's return value, flattened to an array, or null if nothing was returned
     */
    public Object @Nullable [] run(Event event, Consumer<String> onError) {
        SkoopClass skoopClass = Skoop.getInstance().getClassRegistry().get(className);
        if (skoopClass == null) {
            onError.accept("Class '" + className + "' is not currently declared by any loaded script.");
            return null;
        }

        Object[] values = arguments == null ? new Object[0] : arguments.getArray(event);

        SkoopMethod method = skoopClass.findStaticMethod(methodName, values);
        if (method == null) {
            onError.accept("No static method '" + methodName + "' in class '" + skoopClass.getName()
                    + "' accepts the given arguments.");
            return null;
        }

        Object result = SkoopMethodExecutor.execute(null, method, values, onError);
        if (result == null) {
            return null;
        }

        // Plural returns are stored as the raw array the method returned.
        return result instanceof Object[] array ? array : new Object[]{result};
    }

    public boolean isSingle() {
        return returnType == null || !returnType.isPlural();
    }

    public Class<?> getValueClass() {
        return returnType == null ? Object.class : returnType.getValueClass();
    }

    public @Nullable SkoopType getReturnType() {
        return returnType;
    }

    public String toString(@Nullable Event event, boolean debug) {
        String call = "call " + className + "." + methodName;
        return arguments == null ? call : call + " with " + arguments.toString(event, debug);
    }
}
