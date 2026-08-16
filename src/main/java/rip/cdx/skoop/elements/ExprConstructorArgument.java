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
import rip.cdx.skoop.elements.events.SkoopConstructorEvent;

public class ExprConstructorArgument extends SimpleExpression<Object> {

    private String argumentName;

    public static void register(Registration reg) {
        reg.newSimpleExpression(
                        ExprConstructorArgument.class,
                        Object.class,
                        "constructor arg[ument] <([A-Za-z_][A-Za-z0-9_]*)>"
                )
                .name("Skoop Constructor Argument")
                .description("Gets an argument passed to the current Skoop constructor")
                .since("1.0.0")
                .register();
    }

    @Override
    public boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
        if (!ParserInstance.get().isCurrentEvent(SkoopConstructorEvent.class)) {
            Skript.error("Constructor arguments can only be used inside a Skoop constructor.");
            return false;
        }

        this.argumentName = parseResult.regexes.getFirst().group(1);
        return true;
    }

    @Override
    protected Object @Nullable [] get(Event event) {
        if (!(event instanceof SkoopConstructorEvent constructorEvent)) {
            return null;
        }

        Object value = constructorEvent.getArgument(argumentName);

        if (value == null) {
            return null;
        }

        return new Object[]{value};
    }

    @Override
    public boolean isSingle() {
        return true;
    }

    @Override
    public Class<?> getReturnType() {
        return Object.class;
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "constructor argument " + argumentName;
    }
}