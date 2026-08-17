package rip.cdx.skoop.core.events;

import lombok.Getter;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import rip.cdx.skoop.api.SkoopExecutable;
import rip.cdx.skoop.api.SkoopObject;
import rip.cdx.skoop.api.SkoopParameter;

import java.util.List;

/**
 * Base of the events used to run a constructor or method body against an instance.
 * <p>
 * These are never posted to the Bukkit event bus; they exist purely as the parse-time context
 * and runtime state carrier for the body's {@link ch.njol.skript.lang.Trigger}.
 */
@Getter
public abstract class SkoopInvocationEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    /** Null while running a static method, which has no instance to act on. */
    private final @Nullable SkoopObject object;
    private final SkoopExecutable executable;
    private final Object[] arguments;

    protected SkoopInvocationEvent(@Nullable SkoopObject object, SkoopExecutable executable, Object[] arguments) {
        this.object = object;
        this.executable = executable;
        this.arguments = arguments;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }

    public @Nullable Object getArgument(String name) {
        List<SkoopParameter> parameters = executable.getParameters();

        for (int i = 0; i < parameters.size(); i++) {
            if (parameters.get(i).name().equalsIgnoreCase(name)) {
                return getArgument(i);
            }
        }

        return null;
    }

    public @Nullable Object getArgument(int index) {
        if (index < 0 || index >= arguments.length) {
            return null;
        }

        return arguments[index];
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }
}
