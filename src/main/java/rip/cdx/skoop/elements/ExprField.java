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
import rip.cdx.skoop.core.api.SkoopType;
import rip.cdx.skoop.core.SkoopTypeProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ExprField extends SimpleExpression<Object> implements SkoopTypeProvider {

    private Expression<?> objects;
    private String fieldName;

    private @Nullable SkoopType fieldType;

    public static void register(Registration reg) {
        reg.newSimpleExpression(
                        ExprField.class,
                        Object.class,
                        "%skoopobjects%.<([A-Za-z_][A-Za-z0-9_]*)>"
                )
                .name("Skoop Field")
                .description("Gets or changes a field on a Skoop object")
                .since("1.0.0")
                .register();
    }

    @Override
    public boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
        this.objects = expressions[0];
        this.fieldName = parseResult.regexes.getFirst().group(1);

        if (objects instanceof SkoopTypeProvider provider) {
            SkoopType ownerType = provider.getSkoopType();

            if (ownerType != null && ownerType.isSkoopType()) {
                SkoopField field = ownerType.getSkoopClass().getField(fieldName);

                if (field != null) {
                    this.fieldType = field.getType();
                }
            }
        }

        return true;
    }

    @Override
    protected Object @Nullable [] get(Event event) {
        Object[] receivers = objects.getArray(event);

        if (receivers.length == 0) {
            return null;
        }

        List<Object> results = new ArrayList<>();

        for (Object receiver : receivers) {
            if (!(receiver instanceof SkoopObject object)) {
                continue;
            }

            SkoopField field = object.getSkoopClass().getField(fieldName);

            if (field == null) {
                continue;
            }

            Object value = object.getField(field);

            if (value == null) {
                continue;
            }

            if (field.getType().isPlural() && value instanceof Object[] values) {
                Collections.addAll(results, values);
            } else {
                results.add(value);
            }
        }

        if (results.isEmpty()) {
            return null;
        }

        return results.toArray();
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
        Object[] receivers = objects.getArray(event);

        if (receivers.length == 0) {
            return;
        }

        if (receivers.length != 1) {
            Skript.error("A Skoop field can only be changed on one object at a time.");
            return;
        }

        if (!(receivers[0] instanceof SkoopObject object)) {
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

        if (field.getType().isPlural()) {
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

        if (delta.length > 1) {
            Skript.error("Field '" + field.getName() + "' only accepts a single " + field.getType().getName() + " value.");
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

        if (!field.getType().accepts(value)) {
            Skript.error("Field '" + field.getName() + "' expects " + field.getType().getName() + ", but received " + getTypeName(value));
            return false;
        }

        return true;
    }

    private boolean validatePlural(SkoopField field, Object[] values) {
        for (Object value : values) {
            if (value != null && !field.getType().accepts(value)) {
                Skript.error("Field '" + field.getName() + "' expects " + field.getType().getName() + " values, but received " + getTypeName(value));
                return false;
            }
        }

        return true;
    }

    private String getTypeName(Object value) {
        if (value instanceof SkoopObject object) {
            return object.getSkoopClass().getName();
        }

        return value.getClass().getSimpleName();
    }

    @Override
    public boolean isSingle() {
        if (fieldType != null) {
            return !fieldType.isPlural();
        }

        return false;
    }

    @Override
    public Class<?> getReturnType() {
        if (fieldType == null) {
            return Object.class;
        }

        if (fieldType.isSkoopType()) {
            return SkoopObject.class;
        }

        return fieldType.getSkriptType().getC();
    }

    @Override
    public @Nullable SkoopType getSkoopType() {
        return fieldType;
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return objects.toString(event, debug) + "." + fieldName;
    }
}