package rip.cdx.skoop.elements.expressions;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import com.github.shanebeee.skr.Registration;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import rip.cdx.skoop.api.SkoopType;
import rip.cdx.skoop.core.SkoopStaticCall;
import rip.cdx.skoop.core.SkoopTypeProvider;

public class ExprCallStaticMethod extends SimpleExpression<Object> implements SkoopTypeProvider {

    private SkoopStaticCall call;

    public static void register(Registration reg) {
        reg.newSimpleExpression(
                        ExprCallStaticMethod.class,
                        Object.class,
                        "call <([A-Za-z_][A-Za-z0-9_]*)>\\.<([A-Za-z_][A-Za-z0-9_]*)>",
                        "call <([A-Za-z_][A-Za-z0-9_]*)>\\.<([A-Za-z_][A-Za-z0-9_]*)> with %objects%"
                )
                .name("Skoop Static Method Call")
                .description("Calls a static method on a class and returns its result.")
                .examples("set {_id} to call Counter.nextId")
                .since("1.0.0")
                .register();
    }

    @Override
    public boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
        this.call = SkoopStaticCall.parse(
                parseResult.regexes.get(0).group(1),
                parseResult.regexes.get(1).group(1),
                matchedPattern == 1 ? expressions[0] : null
        );

        return call != null;
    }

    @Override
    protected Object @Nullable [] get(Event event) {
        return call.run(event, this::error);
    }

    @Override
    public boolean isSingle() {
        return call.isSingle();
    }

    @Override
    public Class<?> getReturnType() {
        return call.getValueClass();
    }

    @Override
    public @Nullable SkoopType getSkoopType() {
        return call.getReturnType();
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return call.toString(event, debug);
    }
}
