package rip.cdx.skoop.elements.expressions;

import ch.njol.skript.classes.Changer;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import com.github.shanebeee.skr.Registration;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import rip.cdx.skoop.Skoop;
import rip.cdx.skoop.api.SkoopClass;
import rip.cdx.skoop.api.SkoopField;
import rip.cdx.skoop.api.SkoopType;
import rip.cdx.skoop.core.SkoopFieldMutator;
import rip.cdx.skoop.core.SkoopTypeProvider;

/**
 * Reads or changes a static field, e.g. {@code Counter.total}.
 * <p>
 * The pattern deliberately matches any bare {@code Identifier.Identifier}; anything whose left side
 * is not a declared class is rejected in {@link #init}, which lets Skript fall through to other
 * syntaxes rather than claiming the expression.
 */
public class ExprStaticField extends SimpleExpression<Object> implements SkoopTypeProvider {

    private String className;
    private String fieldName;
    private SkoopType fieldType;

    public static void register(Registration reg) {
        reg.newSimpleExpression(
                        ExprStaticField.class,
                        Object.class,
                        "<([A-Za-z_][A-Za-z0-9_]*)\\.([A-Za-z_][A-Za-z0-9_]*)>"
                )
                .name("Skoop Static Field")
                .description("Gets or changes a static field, which belongs to the class itself "
                        + "rather than to any one instance.")
                .examples("set Counter.total to 0", "add 1 to Counter.total", "send \"%Counter.total%\"")
                .since("1.0.0")
                .register();
    }

    @Override
    public boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
        this.className = parseResult.regexes.getFirst().group(1);
        this.fieldName = parseResult.regexes.getFirst().group(2);

        // No error is reported here: this pattern matches any dotted identifier pair, so a
        // non-match simply means the expression belongs to some other syntax.
        SkoopClass skoopClass = Skoop.getInstance().getClassRegistry().get(className);
        if (skoopClass == null) {
            return false;
        }

        SkoopField field = skoopClass.getStaticField(fieldName);
        if (field == null) {
            return false;
        }

        this.fieldType = field.getType();
        return true;
    }

    /**
     * @return the declaring class, or null if its script is no longer loaded
     */
    private @Nullable SkoopClass findClass() {
        return Skoop.getInstance().getClassRegistry().get(className);
    }

    @Override
    protected Object @Nullable [] get(Event event) {
        SkoopClass skoopClass = findClass();
        if (skoopClass == null) {
            return null;
        }

        Object value = skoopClass.getStaticValue(fieldName);
        if (value == null) {
            return null;
        }

        if (fieldType.isPlural() && value instanceof Object[] values) {
            return values.clone();
        }

        return new Object[]{value};
    }

    @Override
    public Class<?> @Nullable [] acceptChange(Changer.ChangeMode mode) {
        return SkoopFieldMutator.acceptChange(mode, fieldType);
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, Changer.ChangeMode mode) {
        SkoopClass skoopClass = findClass();
        if (skoopClass == null) {
            error("Class '" + className + "' is not currently declared by any loaded script.");
            return;
        }

        SkoopFieldMutator.Result result = SkoopFieldMutator.apply(
                skoopClass.getStaticValue(fieldName), fieldType, fieldName, delta, mode, this::error);

        if (result.changed()) {
            skoopClass.setStaticValue(fieldName, result.value());
        }
    }

    @Override
    public boolean isSingle() {
        return !fieldType.isPlural();
    }

    @Override
    public Class<?> getReturnType() {
        return fieldType.getValueClass();
    }

    @Override
    public @Nullable SkoopType getSkoopType() {
        return fieldType;
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return className + "." + fieldName;
    }
}
