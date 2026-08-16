package rip.cdx.skoop.elements.expressions;

import ch.njol.skript.classes.Changer;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import com.github.shanebeee.skr.Registration;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import rip.cdx.skoop.api.SkoopClass;
import rip.cdx.skoop.api.SkoopField;
import rip.cdx.skoop.api.SkoopObject;
import rip.cdx.skoop.api.SkoopType;
import rip.cdx.skoop.core.SkoopTypeProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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
                .description("Gets or changes a field on a Skoop object.")
                .examples("set {_dog}.name to \"Rex\"", "send {_dog}.name")
                .since("1.0.0")
                .register();
    }

    @Override
    public boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
        this.objects = expressions[0];
        this.fieldName = parseResult.regexes.getFirst().group(1);
        this.fieldType = resolveFieldType();

        return true;
    }

    /**
     * Resolves the declared field type when the owning expression's class is known at parse time.
     * Without it the field is treated as a single {@code object}.
     */
    private @Nullable SkoopType resolveFieldType() {
        if (!(objects instanceof SkoopTypeProvider provider)) {
            return null;
        }

        SkoopType ownerType = provider.getSkoopType();
        if (ownerType == null || !ownerType.isSkoopType()) {
            return null;
        }

        SkoopClass ownerClass = ownerType.getSkoopClass();
        if (ownerClass == null) {
            return null;
        }

        SkoopField field = ownerClass.getField(fieldName);
        if (field == null) {
            return null;
        }

        return field.getType();
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

            SkoopClass skoopClass = object.findSkoopClass();
            if (skoopClass == null) {
                continue;
            }

            SkoopField field = skoopClass.getField(fieldName);
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

        return results.isEmpty() ? null : results.toArray();
    }

    @Override
    public Class<?> @Nullable [] acceptChange(Changer.ChangeMode mode) {
        return switch (mode) {
            case SET -> new Class[]{Object[].class};
            case DELETE, RESET -> new Class[0];
            // Only meaningful on plural fields. When the field type could not be resolved at parse
            // time the mode is accepted here and rejected at runtime instead.
            case ADD, REMOVE, REMOVE_ALL -> fieldType != null && !fieldType.isPlural()
                    ? null
                    : new Class[]{Object[].class};
            default -> null;
        };
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, Changer.ChangeMode mode) {
        Object[] receivers = objects.getArray(event);
        if (receivers.length == 0) {
            return;
        }

        for (Object receiver : receivers) {
            if (receiver instanceof SkoopObject object) {
                change(object, delta, mode);
            }
        }
    }

    private void change(SkoopObject object, Object @Nullable [] delta, Changer.ChangeMode mode) {
        SkoopClass skoopClass = object.findSkoopClass();
        if (skoopClass == null) {
            error("Class '" + object.getClassName() + "' is not currently declared by any loaded script.");
            return;
        }

        SkoopField field = skoopClass.getField(fieldName);
        if (field == null) {
            error("Class '" + skoopClass.getName() + "' has no field named '" + fieldName + "'.");
            return;
        }

        if (mode == Changer.ChangeMode.DELETE || mode == Changer.ChangeMode.RESET || delta == null) {
            object.setField(field, null);
            return;
        }

        SkoopType type = field.getType();

        if (mode == Changer.ChangeMode.ADD || mode == Changer.ChangeMode.REMOVE || mode == Changer.ChangeMode.REMOVE_ALL) {
            if (!type.isPlural()) {
                error("Field '" + field.getName() + "' holds a single " + type.getName()
                        + ", so values cannot be added to or removed from it.");
                return;
            }

            changeElements(object, field, delta, mode);
            return;
        }

        if (type.isPlural()) {
            if (acceptsAll(field, delta)) {
                object.setField(field, delta.clone());
            }

            return;
        }

        if (delta.length == 0) {
            object.setField(field, null);
            return;
        }

        if (delta.length > 1) {
            error("Field '" + field.getName() + "' only accepts a single " + type.getName() + " value.");
            return;
        }

        Object value = delta[0];
        if (!type.accepts(value)) {
            error("Field '" + field.getName() + "' expects " + type.getName() + ", but received " + describe(value) + ".");
            return;
        }

        object.setField(field, value);
    }

    /**
     * Applies an element-wise change to a plural field.
     * <p>
     * {@code REMOVE} drops one occurrence per given value, {@code REMOVE_ALL} drops every
     * occurrence, matching how Skript lists behave elsewhere.
     */
    private void changeElements(SkoopObject object, SkoopField field, Object[] delta, Changer.ChangeMode mode) {
        Object current = object.getField(field);
        List<Object> values = new ArrayList<>();

        if (current instanceof Object[] existing) {
            Collections.addAll(values, existing);
        } else if (current != null) {
            values.add(current);
        }

        if (mode == Changer.ChangeMode.ADD) {
            if (!acceptsAll(field, delta)) {
                return;
            }

            Collections.addAll(values, delta);
        } else {
            for (Object removed : delta) {
                if (mode == Changer.ChangeMode.REMOVE_ALL) {
                    values.removeIf(value -> Objects.equals(value, removed));
                } else {
                    values.remove(removed);
                }
            }
        }

        object.setField(field, values.isEmpty() ? null : values.toArray());
    }

    private boolean acceptsAll(SkoopField field, Object[] values) {
        for (Object value : values) {
            if (!field.getType().accepts(value)) {
                error("Field '" + field.getName() + "' expects " + field.getType().getName()
                        + " values, but received " + describe(value) + ".");
                return false;
            }
        }

        return true;
    }

    private static String describe(@Nullable Object value) {
        if (value == null) {
            return "nothing";
        }

        if (value instanceof SkoopObject object) {
            return object.getSkoopClass().getName();
        }

        return value.getClass().getSimpleName();
    }

    @Override
    public boolean isSingle() {
        if (!objects.isSingle()) {
            return false;
        }

        return fieldType == null || !fieldType.isPlural();
    }

    @Override
    public Class<?> getReturnType() {
        return fieldType == null ? Object.class : fieldType.getValueClass();
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
