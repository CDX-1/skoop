package rip.cdx.skoop.core;

import org.jetbrains.annotations.Nullable;
import rip.cdx.skoop.api.SkoopMethod;
import rip.cdx.skoop.api.SkoopObject;
import rip.cdx.skoop.core.events.SkoopMethodEvent;

import java.util.function.Consumer;

/**
 * Resolves and runs methods on {@link SkoopObject}s.
 */
public final class SkoopMethodExecutor {

    private SkoopMethodExecutor() {
    }

    /**
     * Resolves the overload of {@code methodName} matching {@code arguments} and runs it.
     *
     * @param onError receives a human readable message when no overload matches
     * @return the value returned by the method, or null if it is void or could not be resolved
     */
    public static @Nullable Object call(SkoopObject object, String methodName, Object[] arguments, Consumer<String> onError) {
        SkoopMethod method = object.getSkoopClass().findMethod(methodName, arguments);
        if (method == null) {
            onError.accept("No method '" + methodName + "' in class '" + object.getSkoopClass().getName()
                    + "' accepts the given arguments.");
            return null;
        }

        return execute(object, method, arguments);
    }

    /**
     * Runs an already resolved method against {@code object}.
     *
     * @return the value returned by the method, or null if it is void
     */
    public static @Nullable Object execute(SkoopObject object, SkoopMethod method, Object[] arguments) {
        SkoopMethodEvent event = new SkoopMethodEvent(object, method, arguments);
        method.getTrigger().execute(event);

        return event.getReturnValue();
    }
}
