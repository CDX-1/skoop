package rip.cdx.skoop.elements.effects;

import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import com.github.shanebeee.skr.Registration;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import rip.cdx.skoop.api.SkoopObject;
import rip.cdx.skoop.core.SkoopMethodCall;

public class EffCallMethod extends Effect {

    private SkoopMethodCall call;

    public static void register(Registration reg) {
        reg.newEffect(
                        EffCallMethod.class,
                        "call %skoopobject%.<([A-Za-z_][A-Za-z0-9_]*)>",
                        "call %skoopobject%.<([A-Za-z_][A-Za-z0-9_]*)> with %objects%"
                )
                .name("Skoop Call Method")
                .description("Calls a method on a Skoop object, discarding any returned value.")
                .examples("call {_dog}.bark() with 3")
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
    protected void execute(Event event) {
        call.run(event, this::error);
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return call.toString(event, debug);
    }
}
