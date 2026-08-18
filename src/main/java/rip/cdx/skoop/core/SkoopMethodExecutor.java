package rip.cdx.skoop.core;

import ch.njol.skript.lang.Trigger;
import org.jetbrains.annotations.Nullable;
import rip.cdx.skoop.api.SkoopClass;
import rip.cdx.skoop.api.SkoopMethod;
import rip.cdx.skoop.api.SkoopObject;
import rip.cdx.skoop.core.events.SkoopMethodEvent;

import java.util.function.Consumer;

/**
 * Resolves and runs methods on {@link SkoopObject}s.
 */
public final class SkoopMethodExecutor {

    /**
     * How deep Skoop method calls may nest before the chain is aborted.
     * <p>
     * Unbounded recursion in a script would otherwise overflow the stack of whichever thread is
     * running the trigger — usually the main thread, taking the server down over a script bug.
     */
    public static final int MAX_CALL_DEPTH = 64;

    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private SkoopMethodExecutor() {
    }

    /**
     * Resolves the overload of {@code methodName} matching {@code arguments} and runs it.
     *
     * @param onError receives a human readable message when no overload matches or the call nests too deeply
     * @return the value returned by the method, or null if it is void or could not be run
     */
    public static @Nullable Object call(SkoopObject object, String methodName, Object[] arguments, Consumer<String> onError) {
        SkoopClass skoopClass = object.findSkoopClass();
        if (skoopClass == null) {
            onError.accept("Class '" + object.getClassName() + "' is not currently declared by any loaded script.");
            return null;
        }

        SkoopMethod method = skoopClass.findMethod(methodName, arguments);
        if (method == null) {
            onError.accept("No method '" + methodName + "' in class '" + skoopClass.getName()
                    + "' accepts the given arguments.");
            return null;
        }

        return execute(object, method, arguments, onError);
    }

    /**
     * Runs an already resolved method against {@code object}.
     *
     * @return the value returned by the method, or null if it is void or the depth limit was hit
     */
    public static @Nullable Object execute(@Nullable SkoopObject object, SkoopMethod method, Object[] arguments, Consumer<String> onError) {
        Trigger body = method.getTrigger();

        // Only reachable if the class that implemented this method stopped being loaded: a
        // concrete class cannot finish loading while an abstract method is unimplemented.
        if (body == null) {
            onError.accept("Method '" + method.getName() + "' is abstract in class '"
                    + method.getDeclaringClassName() + "' and has no implementation to run.");
            return null;
        }

        int[] depth = DEPTH.get();

        if (depth[0] >= MAX_CALL_DEPTH) {
            onError.accept("Skoop method calls nested more than " + MAX_CALL_DEPTH + " deep at '"
                    + (object == null ? "" : object.getClassName() + ".") + method.getName()
                    + "'. This is almost always unterminated recursion.");
            return null;
        }

        SkoopMethodEvent event = new SkoopMethodEvent(object, method, arguments);

        depth[0]++;
        try {
            body.execute(event);
        } finally {
            depth[0]--;
        }

        return event.getReturnValue();
    }
}
