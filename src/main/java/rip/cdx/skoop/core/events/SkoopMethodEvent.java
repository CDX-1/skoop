package rip.cdx.skoop.core.events;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import rip.cdx.skoop.core.api.SkoopMethod;
import rip.cdx.skoop.core.api.SkoopObject;
import rip.cdx.skoop.core.api.SkoopParameter;

import java.util.List;

@Getter
@RequiredArgsConstructor
public class SkoopMethodEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final SkoopObject object;
    private final SkoopMethod method;
    private final Object[] arguments;

    @Setter
    private @Nullable Object returnValue;

    public @Nullable Object getArgument(String name) {
        List<SkoopParameter> parameters = method.getParameters();

        for (int i = 0; i < parameters.size(); i++) {
            if (parameters.get(i).name().equalsIgnoreCase(name)) {
                return arguments[i];
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

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}