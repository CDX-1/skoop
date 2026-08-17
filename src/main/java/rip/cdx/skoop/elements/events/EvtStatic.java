package rip.cdx.skoop.elements.events;

import ch.njol.skript.lang.Literal;
import ch.njol.skript.lang.SkriptEvent;
import ch.njol.skript.lang.SkriptParser;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import rip.cdx.skoop.core.events.SkoopStaticEvent;

/**
 * The {@link SkriptEvent} attached to a {@code static:} body's {@link ch.njol.skript.lang.Trigger}.
 * Never registered as syntax; see {@link EvtSkoopMember}.
 */
public class EvtStatic extends SkriptEvent {

    @Override
    public boolean init(Literal<?>[] literals, int matchedPattern, SkriptParser.ParseResult parseResult) {
        return true;
    }

    @Override
    public boolean check(Event event) {
        return event instanceof SkoopStaticEvent;
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "skoop static body";
    }
}
