package rip.cdx.skoop.elements;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import com.github.shanebeee.skr.Registration;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import rip.cdx.skoop.Skoop;
import rip.cdx.skoop.core.api.SkoopClass;
import rip.cdx.skoop.core.api.SkoopConstructor;
import rip.cdx.skoop.core.api.SkoopObject;
import rip.cdx.skoop.elements.events.SkoopConstructorEvent;

public class ExprNew extends SimpleExpression<SkoopObject> {

    private String className;
    private Expression<?> arguments;

    public static void register(Registration reg) {
        reg.newSimpleExpression(
                        ExprNew.class,
                        SkoopObject.class,
                        "new <([A-Za-z_][A-Za-z0-9_]*)>",
                        "new <([A-Za-z_][A-Za-z0-9_]*)> with %objects%"
                )
                .name("Skoop New Object")
                .description("Creates a new Skoop object")
                .since("1.0.0")
                .register();
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(
            Expression<?>[] expressions,
            int matchedPattern,
            Kleenean isDelayed,
            SkriptParser.ParseResult parseResult
    ) {
        this.className = parseResult.regexes.getFirst().group(1);

        if (matchedPattern == 1) {
            this.arguments = expressions[0].getConvertedExpression(Object.class);

            if (this.arguments == null) {
                Skript.error("Could not parse constructor arguments for class '" + className + "'");
                return false;
            }
        }

        return true;
    }

    @Override
    protected SkoopObject @Nullable [] get(Event event) {
        SkoopClass skoopClass = Skoop.getInstance().getClassRegistry().get(className);

        if (skoopClass == null) {
            return null;
        }

        Object[] values = arguments == null ? new Object[0] : arguments.getArray(event);

        SkoopConstructor constructor = skoopClass.findConstructor(values);

        if (constructor == null) {
            return null;
        }

        SkoopObject object = new SkoopObject(skoopClass);

        SkoopConstructorEvent constructorEvent = new SkoopConstructorEvent(object, constructor, values);
        constructor.getTrigger().execute(constructorEvent);

        return new SkoopObject[]{object};
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
    public String toString(@Nullable Event event, boolean debug) {
        if (arguments == null) {
            return "new " + className;
        }

        return "new " + className + " with " +
                arguments.toString(event, debug);
    }
}