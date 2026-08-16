package rip.cdx.skoop.elements.events;

import ch.njol.skript.lang.Literal;
import ch.njol.skript.lang.SkriptEvent;
import ch.njol.skript.lang.SkriptParser;
import lombok.RequiredArgsConstructor;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import rip.cdx.skoop.core.events.SkoopInvocationEvent;

/**
 * The {@link SkriptEvent} attached to a constructor or method body's
 * {@link ch.njol.skript.lang.Trigger}.
 * <p>
 * These are never registered as syntax: class members are declared through
 * {@link rip.cdx.skoop.elements.structures.StructClass}, not as standalone events. They exist only
 * so that a Trigger has an event to describe itself with.
 */
@RequiredArgsConstructor
public abstract class EvtSkoopMember extends SkriptEvent {

    private final Class<? extends SkoopInvocationEvent> eventType;
    private final String name;

    @Override
    public boolean init(Literal<?>[] literals, int matchedPattern, SkriptParser.ParseResult parseResult) {
        return true;
    }

    @Override
    public boolean check(Event event) {
        return eventType.isInstance(event);
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return name;
    }
}
