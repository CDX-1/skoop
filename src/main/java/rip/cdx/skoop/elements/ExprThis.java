package rip.cdx.skoop.elements;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.parser.ParserInstance;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import com.github.shanebeee.skr.Registration;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import rip.cdx.skoop.core.api.SkoopObject;
import rip.cdx.skoop.core.events.SkoopConstructorEvent;
import rip.cdx.skoop.core.events.SkoopMethodEvent;

public class ExprThis extends SimpleExpression<SkoopObject> {

    public static void register(Registration reg) {
        reg.newSimpleExpression(ExprThis.class, SkoopObject.class, "this")
                .name("Skoop This")
                .description("The current Skoop object")
                .since("1.0.0")
                .register();
    }

    @Override
    public boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
        ParserInstance parser = ParserInstance.get();

        if (!parser.isCurrentEvent(SkoopConstructorEvent.class) && !parser.isCurrentEvent(SkoopMethodEvent.class)) {
            Skript.error("'this' can only be used inside a Skoop constructor or method.");
            return false;
        }

        return true;
    }

    @Override
    protected SkoopObject @Nullable [] get(Event event) {
        if (event instanceof SkoopConstructorEvent constructorEvent) {
            return new SkoopObject[]{constructorEvent.getObject()};
        }

        if (event instanceof SkoopMethodEvent methodEvent) {
            return new SkoopObject[]{methodEvent.getObject()};
        }

        return null;
    }

    @Override
    public boolean isSingle() {
        return true;
    }

    @Override
    public Class<? extends SkoopObject> getReturnType() {
        return SkoopObject.class;
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "this";
    }
}