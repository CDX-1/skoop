package rip.cdx.skoop.core;

import ch.njol.skript.classes.Changer;
import org.jetbrains.annotations.Nullable;
import rip.cdx.skoop.api.SkoopObject;
import rip.cdx.skoop.api.SkoopType;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Applies a Skript change to a field value, shared by instance and static fields so both behave
 * identically.
 * <p>
 * {@code ADD}/{@code REMOVE} mean two different things depending on the field: element-wise on a
 * plural field, arithmetic on a single number. Anything else cannot be added to.
 */
public final class SkoopFieldMutator {

    /**
     * The outcome of a change.
     *
     * @param changed false when the change was rejected and the field must be left alone
     * @param value   the new field value, null meaning "clear the field"
     */
    public record Result(boolean changed, @Nullable Object value) {

        private static final Result UNCHANGED = new Result(false, null);

        public static Result unchanged() {
            return UNCHANGED;
        }

        public static Result of(@Nullable Object value) {
            return new Result(true, value);
        }
    }

    private SkoopFieldMutator() {
    }

    /**
     * @param type the declared field type, or null when it could not be resolved at parse time
     * @return the classes Skript may pass for this mode, or null if the mode is unsupported
     */
    public static Class<?> @Nullable [] acceptChange(Changer.ChangeMode mode, @Nullable SkoopType type) {
        return switch (mode) {
            case SET -> new Class[]{Object[].class};
            case DELETE, RESET -> new Class[0];
            // Unknown type: accept and decide at runtime, when the real field is known.
            case ADD, REMOVE, REMOVE_ALL -> type == null || type.isPlural() || isNumeric(type)
                    ? new Class[]{Object[].class}
                    : null;
            default -> null;
        };
    }

    /**
     * @param current   the field's present value, null if unset
     * @param fieldName used only for error messages
     * @param onError   receives a human readable message when the change is rejected
     * @return what the field should become
     */
    public static Result apply(@Nullable Object current, SkoopType type, String fieldName,
                               Object @Nullable [] delta, Changer.ChangeMode mode, Consumer<String> onError) {
        if (mode == Changer.ChangeMode.DELETE || mode == Changer.ChangeMode.RESET || delta == null) {
            return Result.of(null);
        }

        return switch (mode) {
            case SET -> set(type, fieldName, delta, onError);
            case ADD, REMOVE, REMOVE_ALL -> modify(current, type, fieldName, delta, mode, onError);
            default -> Result.unchanged();
        };
    }

    private static Result set(SkoopType type, String fieldName, Object[] delta, Consumer<String> onError) {
        if (type.isPlural()) {
            return acceptsAll(type, fieldName, delta, onError) ? Result.of(delta.clone()) : Result.unchanged();
        }

        if (delta.length == 0) {
            return Result.of(null);
        }

        if (delta.length > 1) {
            onError.accept("Field '" + fieldName + "' only accepts a single " + type.getName() + " value.");
            return Result.unchanged();
        }

        if (!type.accepts(delta[0])) {
            onError.accept("Field '" + fieldName + "' expects " + type.getName()
                    + ", but received " + describe(delta[0]) + ".");
            return Result.unchanged();
        }

        return Result.of(delta[0]);
    }

    private static Result modify(@Nullable Object current, SkoopType type, String fieldName,
                                 Object[] delta, Changer.ChangeMode mode, Consumer<String> onError) {
        if (type.isPlural()) {
            return modifyList(current, type, fieldName, delta, mode, onError);
        }

        if (isNumeric(type)) {
            return modifyNumber(current, fieldName, delta, mode, onError);
        }

        onError.accept("Field '" + fieldName + "' holds a single " + type.getName()
                + ", so values cannot be added to or removed from it.");
        return Result.unchanged();
    }

    /**
     * {@code REMOVE} drops one occurrence per given value, {@code REMOVE_ALL} drops every
     * occurrence, matching how Skript lists behave elsewhere.
     */
    private static Result modifyList(@Nullable Object current, SkoopType type, String fieldName,
                                     Object[] delta, Changer.ChangeMode mode, Consumer<String> onError) {
        List<Object> values = new ArrayList<>();

        if (current instanceof Object[] existing) {
            Collections.addAll(values, existing);
        } else if (current != null) {
            values.add(current);
        }

        if (mode == Changer.ChangeMode.ADD) {
            if (!acceptsAll(type, fieldName, delta, onError)) {
                return Result.unchanged();
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

        return Result.of(values.isEmpty() ? null : values.toArray());
    }

    /**
     * Arithmetic on a single numeric field. An unset field counts as zero, and the result stays a
     * whole number when everything involved is one, so a counter does not drift into "3.0".
     */
    private static Result modifyNumber(@Nullable Object current, String fieldName,
                                       Object[] delta, Changer.ChangeMode mode, Consumer<String> onError) {
        if (mode == Changer.ChangeMode.REMOVE_ALL) {
            onError.accept("'remove all' cannot be used on the single number field '" + fieldName + "'.");
            return Result.unchanged();
        }

        Number base = current instanceof Number number ? number : 0L;
        boolean integral = isIntegral(base);
        double total = base.doubleValue();
        long integralTotal = base.longValue();

        for (Object value : delta) {
            if (!(value instanceof Number number)) {
                onError.accept("Field '" + fieldName + "' expects numbers, but received " + describe(value) + ".");
                return Result.unchanged();
            }

            integral &= isIntegral(number);

            if (mode == Changer.ChangeMode.ADD) {
                total += number.doubleValue();
                integralTotal += number.longValue();
            } else {
                total -= number.doubleValue();
                integralTotal -= number.longValue();
            }
        }

        return Result.of(integral ? integralTotal : total);
    }

    private static boolean acceptsAll(SkoopType type, String fieldName, Object[] values, Consumer<String> onError) {
        for (Object value : values) {
            if (!type.accepts(value)) {
                onError.accept("Field '" + fieldName + "' expects " + type.getName()
                        + " values, but received " + describe(value) + ".");
                return false;
            }
        }

        return true;
    }

    private static boolean isNumeric(SkoopType type) {
        return !type.isSkoopType() && Number.class.isAssignableFrom(wrap(type.getValueClass()));
    }

    private static Class<?> wrap(Class<?> type) {
        if (type == int.class || type == long.class || type == short.class || type == byte.class) {
            return Long.class;
        }

        if (type == double.class || type == float.class) {
            return Double.class;
        }

        return type;
    }

    private static boolean isIntegral(Number number) {
        return number instanceof Long || number instanceof Integer
                || number instanceof Short || number instanceof Byte || number instanceof BigInteger;
    }

    private static String describe(@Nullable Object value) {
        if (value == null) {
            return "nothing";
        }

        if (value instanceof SkoopObject object) {
            return object.getClassName();
        }

        return value.getClass().getSimpleName();
    }
}
