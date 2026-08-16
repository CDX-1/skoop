package rip.cdx.skoop.elements.expressions;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import com.github.shanebeee.skr.Registration;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import rip.cdx.skoop.api.SkoopObject;
import rip.cdx.skoop.api.SkoopType;
import rip.cdx.skoop.core.SkoopMethodCall;
import rip.cdx.skoop.core.SkoopTypeProvider;

public class ExprCallMethod extends SimpleExpression<Object> implements SkoopTypeProvider {

    private SkoopMethodCall call;

    public static void register(Registration reg) {
        reg.newSimpleExpression(
                        ExprCallMethod.class,
                        Object.class,
                        "call %skoopobject%.<([A-Za-z_][A-Za-z0-9_]*)>",
                        "call %skoopobject%.<([A-Za-z_][A-Za-z0-9_]*)> with %objects%"
                )
                .name("Skoop Method Call")
                .description("Calls a method on a Skoop object and returns its result.")
                .examples("set {_name} to call {_dog}.getName()")
                .since("1.0.0")
                .register();
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
        this.call = SkoopMethodCall.parse(
                (Expression<SkoopObject>) expressions[0],
                parseResult.regexes.getFirst().group(1),
                matchedPattern == 1 ? expressions[1] : null
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
