package rip.cdx.skoop.elements;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import com.github.shanebeee.skr.Registration;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import rip.cdx.skoop.core.SkoopMethodExecutor;
import rip.cdx.skoop.core.api.SkoopObject;

public class EffCallMethod extends Effect {

    private Expression<SkoopObject> object;
    private Expression<?> arguments;
    private String methodName;

    public static void register(Registration reg) {
        reg.newEffect(
                        EffCallMethod.class,
                        "call %skoopobject%.<([A-Za-z_][A-Za-z0-9_]*)>",
                        "call %skoopobject%.<([A-Za-z_][A-Za-z0-9_]*)> with %objects%"
                )
                .name("Skoop Call Method")
                .description("Calls a method on a Skoop object")
                .since("1.0.0")
                .register();
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
        this.object = (Expression<SkoopObject>) expressions[0];
        this.methodName = parseResult.regexes.getFirst().group(1);

        if (matchedPattern == 1) {
            this.arguments = expressions[1].getConvertedExpression(Object.class);

            if (this.arguments == null) {
                Skript.error("Could not parse arguments for method '" + methodName + "'");
                return false;
            }
        }

        return true;
    }

    @Override
    protected void execute(Event event) {
        SkoopObject object = this.object.getSingle(event);

        if (object == null) {
            return;
        }

        Object[] arguments = this.arguments == null
                ? new Object[0]
                : this.arguments.getArray(event);

        if (object.getSkoopClass().findMethod(methodName, arguments) == null) {
            Skript.error("No matching method '" + methodName + "' found in class '" + object.getSkoopClass().getName() + "'");
            return;
        }

        SkoopMethodExecutor.execute(object, methodName, arguments);
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        if (arguments == null) {
            return "call " + object.toString(event, debug) + "." + methodName;
        }

        return "call " + object.toString(event, debug) + "." + methodName + " with " + arguments.toString(event, debug);
    }
}