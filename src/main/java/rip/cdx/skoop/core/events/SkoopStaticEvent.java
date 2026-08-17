package rip.cdx.skoop.core.events;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import rip.cdx.skoop.api.SkoopClass;

/**
 * The context a {@code static:} body runs under.
 * <p>
 * Deliberately not a {@link SkoopInvocationEvent}: there is no instance in a static context, which
 * is what makes {@code this} fail to parse inside one.
 */
@Getter
@RequiredArgsConstructor
public class SkoopStaticEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final SkoopClass skoopClass;

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }
}
