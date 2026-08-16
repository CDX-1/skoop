package rip.cdx.skoop.elements;

import ch.njol.skript.Skript;
import ch.njol.skript.classes.Changer;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import com.github.shanebeee.skr.Registration;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import rip.cdx.skoop.core.api.SkoopField;
import rip.cdx.skoop.core.api.SkoopObject;

public class ExprField extends SimpleExpression<Object> {

    private Expression<SkoopObject> object;
    private String fieldName;

    public static void register(Registration reg) {
        reg.newSimpleExpression(
                        ExprField.class,
                        Object.class,
                        "%skoopobject%.<([A-Za-z_][A-Za-z0-9_]*)>"
                )
                .name("Skoop Field")
                .description("Gets or changes a field on a Skoop object")
                .since("1.0.0")
                .register();
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
        this.object = (Expression<SkoopObject>) expressions[0];
        this.fieldName = parseResult.regexes.getFirst().group(1);

        return true;
    }

    @Override
    protected Object @Nullable [] get(Event event) {
        SkoopObject object = this.object.getSingle(event);

        if (object == null) {
            return null;
        }

        SkoopField field = object.getSkoopClass().getField(fieldName);

        if (field == null) {
            return null;
        }

        Object value = object.getField(field);

        if (value == null) {
            return null;
        }

        if (field.isPlural() && value instanceof Object[] values) {
            return values;
        }

        return new Object[]{value};
    }

    @Override
    public Class<?> @Nullable [] acceptChange(Changer.ChangeMode mode) {
        return switch (mode) {
            case SET -> new Class[]{Object.class, Object[].class};
            case DELETE -> new Class[]{Object.class};
            default -> null;
        };
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, Changer.ChangeMode mode) {
        SkoopObject object = this.object.getSingle(event);

        if (object == null) {
            return;
        }

        SkoopField field = object.getSkoopClass().getField(fieldName);

        if (field == null) {
            Skript.error("Class '" + object.getSkoopClass().getName() + "' does not have a field named '" + fieldName + "'");
            return;
        }

        if (mode == Changer.ChangeMode.DELETE) {
            object.setField(field, null);
            return;
        }

        if (mode != Changer.ChangeMode.SET || delta == null) {
            return;
        }

        if (field.isPlural()) {
            if (!validatePlural(field, delta)) {
                return;
            }

            object.setField(field, delta.clone());
            return;
        }

        if (delta.length == 0) {
            object.setField(field, null);
            return;
        }

        Object value = delta[0];

        if (!validateSingle(field, value)) {
            return;
        }

        object.setField(field, value);
    }

    private boolean validateSingle(SkoopField field, Object value) {
        if (value == null) {
            return true;
        }

        Class<?> expectedType = field.getType().getC();

        if (!expectedType.isInstance(value)) {
            Skript.error("Field '" + field.getName() + "' expects " + field.getType().getCodeName() + ", but received " + value.getClass().getSimpleName());
            return false;
        }

        return true;
    }

    private boolean validatePlural(SkoopField field, Object[] values) {
        Class<?> expectedType = field.getType().getC();

        for (Object value : values) {
            if (value != null && !expectedType.isInstance(value)) {
                Skript.error("Field '" + field.getName() + "' expects " + field.getType().getCodeName() + " values, but received " + value.getClass().getSimpleName());
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean isSingle() {
        return false;
    }

    @Override
    public Class<?> getReturnType() {
        return Object.class;
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return object.toString(event, debug) + "." + fieldName;
    }
}