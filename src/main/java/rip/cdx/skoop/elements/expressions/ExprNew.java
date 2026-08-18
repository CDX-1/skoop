package rip.cdx.skoop.elements.expressions;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import com.github.shanebeee.skr.Registration;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import rip.cdx.skoop.Skoop;
import rip.cdx.skoop.api.SkoopClass;
import rip.cdx.skoop.api.SkoopConstructor;
import rip.cdx.skoop.api.SkoopObject;
import rip.cdx.skoop.api.SkoopType;
import rip.cdx.skoop.core.SkoopTypeProvider;
import rip.cdx.skoop.core.events.SkoopConstructorEvent;

public class ExprNew extends SimpleExpression<SkoopObject> implements SkoopTypeProvider {

    private String className;
    private @Nullable Expression<?> arguments;
    private @Nullable SkoopType type;

    public static void register(Registration reg) {
        reg.newSimpleExpression(
                        ExprNew.class,
                        SkoopObject.class,
                        "new <([A-Za-z_][A-Za-z0-9_]*)>",
                        "new <([A-Za-z_][A-Za-z0-9_]*)> with %objects%"
                )
                .name("Skoop New Object")
                .description("Creates a new instance of a Skoop class, running its matching constructor.")
                .examples("set {_dog} to new Dog with \"Rex\" and 3")
                .since("1.0.0")
                .register();
    }

    @Override
    public boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
        this.className = parseResult.regexes.getFirst().group(1);

        SkoopClass skoopClass = Skoop.getInstance().getClassRegistry().get(className);
        if (skoopClass == null) {
            Skript.error("There is no Skoop class named '" + className + "'.");
            return false;
        }

        if (skoopClass.isAbstract()) {
            Skript.error("Class '" + skoopClass.getName() + "' is abstract and cannot be instantiated. "
                    + "Create an instance of a class that extends it instead.");
            return false;
        }

        this.type = SkoopType.ofSkoopClass(skoopClass.getName(), false);

        if (matchedPattern == 1) {
            this.arguments = expressions[0].getConvertedExpression(Object.class);

            if (this.arguments == null) {
                Skript.error("Could not parse constructor arguments for class '" + className + "'.");
                return false;
            }
        }

        return true;
    }

    @Override
    protected SkoopObject @Nullable [] get(Event event) {
        SkoopClass skoopClass = Skoop.getInstance().getClassRegistry().get(className);
        if (skoopClass == null) {
            error("There is no Skoop class named '" + className + "'.");
            return null;
        }

        // Re-checked at runtime: a reload may have turned the class abstract since parsing.
        if (skoopClass.isAbstract()) {
            error("Class '" + skoopClass.getName() + "' is abstract and cannot be instantiated.");
            return null;
        }

        Object[] values = arguments == null ? new Object[0] : arguments.getArray(event);

        SkoopObject object = new SkoopObject(skoopClass);
        object.initializeDefaults();

        SkoopConstructor constructor = skoopClass.findConstructor(values);
        if (constructor == null) {
            // A class without declared constructors gets an implicit no-argument one.
            if (values.length == 0 && skoopClass.getConstructors().isEmpty()) {
                return new SkoopObject[]{object};
            }

            error("No constructor of class '" + className + "' accepts the given arguments.");
            return null;
        }

        constructor.getTrigger().execute(new SkoopConstructorEvent(object, constructor, values));

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
    public @Nullable SkoopType getSkoopType() {
        return type;
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        if (arguments == null) {
            return "new " + className;
        }

        return "new " + className + " with " + arguments.toString(event, debug);
    }
}
