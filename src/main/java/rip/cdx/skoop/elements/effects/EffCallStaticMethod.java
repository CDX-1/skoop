package rip.cdx.skoop.elements.effects;

import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import com.github.shanebeee.skr.Registration;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import rip.cdx.skoop.core.SkoopStaticCall;

public class EffCallStaticMethod extends Effect {

    private SkoopStaticCall call;

    public static void register(Registration reg) {
        reg.newEffect(
                        EffCallStaticMethod.class,
                        "call <([A-Za-z_][A-Za-z0-9_]*)>\\.<([A-Za-z_][A-Za-z0-9_]*)>",
                        "call <([A-Za-z_][A-Za-z0-9_]*)>\\.<([A-Za-z_][A-Za-z0-9_]*)> with %objects%"
                )
                .name("Skoop Call Static Method")
                .description("Calls a static method on a class, discarding any returned value.")
                .examples("call Counter.reset")
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
    protected void execute(Event event) {
        call.run(event, this::error);
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return call.toString(event, debug);
    }
}
