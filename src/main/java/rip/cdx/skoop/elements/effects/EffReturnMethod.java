package rip.cdx.skoop.elements.effects;

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
import rip.cdx.skoop.api.SkoopType;
import rip.cdx.skoop.core.SkoopParseContext;
import rip.cdx.skoop.core.events.SkoopMethodEvent;

public class EffReturnMethod extends Effect {

    private @Nullable Expression<?> value;
    private @Nullable SkoopType returnType;

    public static void register(Registration reg) {
        reg.newEffect(
                        EffReturnMethod.class,
                        "return %objects%",
                        "return"
                )
                .name("Skoop Method Return")
                .description("Returns a value from the Skoop method currently running, and stops it.")
                .examples("return this.name")
                .since("1.0.0")
                .register();
    }

    @Override
    public boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
        if (!ParserInstance.get().isCurrentEvent(SkoopMethodEvent.class)) {
            Skript.error("'return' can only be used inside a Skoop method.");
            return false;
        }

        this.returnType = SkoopParseContext.getReturnType();

        if (matchedPattern == 1) {
            return true;
        }

        if (returnType == null) {
            Skript.error("This method does not declare a return type, so it cannot return a value.");
            return false;
        }

        this.value = expressions[0].getConvertedExpression(Object.class);
        if (value == null) {
            Skript.error("Could not parse the returned value.");
            return false;
        }

        if (!returnType.isPlural() && !value.isSingle()) {
            Skript.error("This method returns a single " + returnType.getName() + ", but multiple values were given.");
            return false;
        }

        return true;
    }

    @Override
    protected void execute(Event event) {
        // Unused: returning has to stop the trigger, which is handled in walk.
    }

    @Override
    protected @Nullable TriggerItem walk(Event event) {
        debug(event, false);

        if (event instanceof SkoopMethodEvent methodEvent) {
            methodEvent.setReturnValue(evaluate(event));
        }

        // Returning null stops the method body here.
        return null;
    }

    private @Nullable Object evaluate(Event event) {
        if (value == null || returnType == null) {
            return null;
        }

        Object[] values = value.getArray(event);

        if (returnType.isPlural()) {
            for (Object returned : values) {
                if (!returnType.accepts(returned)) {
                    error("Expected " + returnType.getName() + " values to be returned.");
                    return null;
                }
            }

            return values;
        }

        if (values.length == 0) {
            return null;
        }

        if (values.length > 1) {
            error("Expected a single " + returnType.getName() + " to be returned, but got " + values.length + " values.");
            return null;
        }

        if (!returnType.accepts(values[0])) {
            error("Expected a " + returnType.getName() + " to be returned.");
            return null;
        }

        return values[0];
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return value == null ? "return" : "return " + value.toString(event, debug);
    }
}
