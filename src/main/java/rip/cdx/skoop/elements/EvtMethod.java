package rip.cdx.skoop.elements;

import ch.njol.skript.lang.Literal;
import ch.njol.skript.lang.SkriptEvent;
import ch.njol.skript.lang.SkriptParser;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import rip.cdx.skoop.core.events.SkoopMethodEvent;

public class EvtMethod extends SkriptEvent {

    @Override
    public boolean init(Literal<?>[] literals, int matchedPattern, SkriptParser.ParseResult parseResult) {
        return true;
    }

    @Override
    public boolean check(Event event) {
        return event instanceof SkoopMethodEvent;
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "skoop method";
    }
}