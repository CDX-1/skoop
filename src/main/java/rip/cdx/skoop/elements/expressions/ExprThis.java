package rip.cdx.skoop.elements.expressions;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.parser.ParserInstance;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import com.github.shanebeee.skr.Registration;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import rip.cdx.skoop.api.SkoopClass;
import rip.cdx.skoop.api.SkoopObject;
import rip.cdx.skoop.api.SkoopType;
import rip.cdx.skoop.core.SkoopParseContext;
import rip.cdx.skoop.core.SkoopTypeProvider;
import rip.cdx.skoop.core.events.SkoopInvocationEvent;

public class ExprThis extends SimpleExpression<SkoopObject> implements SkoopTypeProvider {

    private @Nullable SkoopType type;

    public static void register(Registration reg) {
        reg.newSimpleExpression(ExprThis.class, SkoopObject.class, "this")
                .name("Skoop This")
                .description("The Skoop object the current constructor or method is running on.")
                .examples("set this.name to \"Rex\"")
                .since("1.0.0")
                .register();
    }

    @Override
    public boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
        if (!ParserInstance.get().isCurrentEvent(SkoopInvocationEvent.class)) {
            Skript.error("'this' can only be used inside a Skoop constructor or method.");
            return false;
        }

        SkoopClass currentClass = SkoopParseContext.getCurrentClass();
        if (currentClass != null) {
            this.type = new SkoopType(currentClass.getName(), null, currentClass, false);
        }

        return true;
    }

    @Override
    protected SkoopObject @Nullable [] get(Event event) {
        if (!(event instanceof SkoopInvocationEvent invocation)) {
            return null;
        }

        return new SkoopObject[]{invocation.getObject()};
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
    public @Nullable SkoopType getSkoopType() {
        return type;
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "this";
    }
}
