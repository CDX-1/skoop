package rip.cdx.skoop.elements;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.TriggerItem;
import ch.njol.skript.lang.parser.ParserInstance;
import ch.njol.util.Kleenean;
import com.github.shanebeee.skr.Registration;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import rip.cdx.skoop.core.events.SkoopMethodEvent;

public class EffReturnMethod extends Effect {

    private Expression<?> value;

    public static void register(Registration reg) {
        reg.newEffect(
                        EffReturnMethod.class,
                        "return %objects%"
                )
                .name("Skoop Method Return")
                .description("Returns a value from a Skoop method")
                .since("1.0.0")
                .register();
    }

    @Override
    public boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
        if (!ParserInstance.get().isCurrentEvent(SkoopMethodEvent.class)) {
            Skript.error("This return effect can only be used inside a Skoop method.");
            return false;
        }

        this.value = expressions[0].getConvertedExpression(Object.class);

        if (this.value == null) {
            Skript.error("Could not parse Skoop method return value.");
            return false;
        }

        return true;
    }

    @Override
    protected void execute(Event event) {
        // handled in walk()
    }

    @Override
    protected @Nullable TriggerItem walk(Event event) {
        if (!(event instanceof SkoopMethodEvent methodEvent)) {
            return null;
        }

        Object[] values = value.getArray(event);

        if (values.length == 0) {
            methodEvent.setReturnValue(null);
        } else if (values.length == 1) {
            methodEvent.setReturnValue(values[0]);
        } else {
            methodEvent.setReturnValue(values);
        }

        // null stops the method trigger here.
        return null;
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "return " + value.toString(event, debug);
    }
}