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
import rip.cdx.skoop.core.SkoopMethodContext;
import rip.cdx.skoop.core.api.SkoopParameter;
import rip.cdx.skoop.core.api.SkoopType;
import rip.cdx.skoop.core.SkoopTypeProvider;
import rip.cdx.skoop.core.events.SkoopMethodEvent;

public class ExprMethodArgument extends SimpleExpression<Object> implements SkoopTypeProvider {

    private String argumentName;
    private SkoopType type;

    public static void register(Registration reg) {
        reg.newSimpleExpression(
                        ExprMethodArgument.class,
                        Object.class,
                        "method arg[ument] <([A-Za-z_][A-Za-z0-9_]*)>"
                )
                .name("Skoop Method Argument")
                .description("Gets an argument passed to the current Skoop method")
                .since("1.0.0")
                .register();
    }

    @Override
    public boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
        if (!ParserInstance.get().isCurrentEvent(SkoopMethodEvent.class)) {
            Skript.error("Method arguments can only be used inside a Skoop method.");
            return false;
        }

        this.argumentName = parseResult.regexes.getFirst().group(1);

        SkoopParameter parameter = SkoopMethodContext.getParameter(argumentName);

        if (parameter == null) {
            Skript.error("Unknown method argument '" + argumentName + "'");
            return false;
        }

        this.type = parameter.type();

        return true;
    }

    @Override
    protected Object @Nullable [] get(Event event) {
        if (!(event instanceof SkoopMethodEvent methodEvent)) {
            return null;
        }

        Object value = methodEvent.getArgument(argumentName);

        if (value == null) {
            return null;
        }

        return new Object[]{value};
    }

    @Override
    public boolean isSingle() {
        return !type.isPlural();
    }

    @Override
    public Class<?> getReturnType() {
        if (type.isSkoopType()) {
            return rip.cdx.skoop.core.api.SkoopObject.class;
        }

        return type.getSkriptType().getC();
    }

    @Override
    public @Nullable SkoopType getSkoopType() {
        return type;
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "method argument " + argumentName;
    }
}