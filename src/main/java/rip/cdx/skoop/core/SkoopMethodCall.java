package rip.cdx.skoop.core;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Expression;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import rip.cdx.skoop.api.SkoopClass;
import rip.cdx.skoop.api.SkoopMethod;
import rip.cdx.skoop.api.SkoopObject;
import rip.cdx.skoop.api.SkoopType;

import java.util.List;
import java.util.function.Consumer;

/**
 * The parsed form of {@code call %skoopobject%.name [with %objects%]}, shared by the effect and
 * the expression variant of a method call.
 */
public final class SkoopMethodCall {

    private final Expression<SkoopObject> object;
    private final String methodName;
    private final @Nullable Expression<?> arguments;
    private final @Nullable SkoopType returnType;

    private SkoopMethodCall(Expression<SkoopObject> object, String methodName,
                            @Nullable Expression<?> arguments, @Nullable SkoopType returnType) {
        this.object = object;
        this.methodName = methodName;
        this.arguments = arguments;
        this.returnType = returnType;
    }

    /**
     * Parses a method call, reporting parse errors itself.
     *
     * @param rawArguments the {@code with %objects%} expression, or null if the call takes no arguments
     * @return the parsed call, or null if it could not be parsed
     */
    public static @Nullable SkoopMethodCall parse(Expression<SkoopObject> object, String methodName,
                                                  @Nullable Expression<?> rawArguments) {
        Expression<?> arguments = null;

        if (rawArguments != null) {
            arguments = rawArguments.getConvertedExpression(Object.class);

            if (arguments == null) {
                Skript.error("Could not parse the arguments for method '" + methodName + "'.");
                return null;
            }
        }

        return new SkoopMethodCall(object, methodName, arguments, resolveReturnType(object, methodName));
    }

    /**
     * Resolves the return type of the call when the receiver's class is known at parse time and
     * every overload of the method agrees on one. Otherwise the call is typed as a single object.
     */
    private static @Nullable SkoopType resolveReturnType(Expression<SkoopObject> object, String methodName) {
        if (!(object instanceof SkoopTypeProvider provider)) {
            return null;
        }

        SkoopType ownerType = provider.getSkoopType();
        if (ownerType == null || !ownerType.isSkoopType()) {
            return null;
        }

        SkoopClass ownerClass = ownerType.getSkoopClass();
        List<SkoopMethod> overloads = ownerClass.getMethods(methodName);
        if (overloads.isEmpty()) {
            return null;
        }

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
     * Evaluates the receiver and arguments and runs the matching overload.
     *
     * @param onError receives a human readable message when the receiver or an overload is missing
     * @return the method's return value, flattened to an array, or null if nothing was returned
     */
    public Object @Nullable [] run(Event event, Consumer<String> onError) {
        SkoopObject receiver = object.getSingle(event);
        if (receiver == null) {
            return null;
        }

        Object[] values = arguments == null ? new Object[0] : arguments.getArray(event);

        Object result = SkoopMethodExecutor.call(receiver, methodName, values, onError);
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
        String call = "call " + object.toString(event, debug) + "." + methodName;
        return arguments == null ? call : call + " with " + arguments.toString(event, debug);
    }
}
