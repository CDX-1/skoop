package rip.cdx.skoop.core.events;

import lombok.Getter;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * The context a field's default value is parsed and evaluated under.
 * <p>
 * Defaults deliberately do <b>not</b> see the event that created the object. Evaluating them
 * against the caller's event would make {@code name: text = "%player%"} mean "whoever happened to
 * trigger the construction", which is neither predictable nor what a field default reads as.
 * Because this event carries no event-values, such an expression fails at parse time instead.
 */
public class SkoopFieldDefaultEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    @Getter
    private final String className;

    public SkoopFieldDefaultEvent(String className) {
        this.className = className;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }
}
