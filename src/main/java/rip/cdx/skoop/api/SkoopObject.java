package rip.cdx.skoop.api;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * An instance of a {@link SkoopClass}.
 * <p>
 * Field values are keyed by the lower-cased field name rather than by {@link SkoopField} identity,
 * so that reloading the declaring script does not orphan the values of already existing instances.
 */
@Getter
@RequiredArgsConstructor
public class SkoopObject {

    private final SkoopClass skoopClass;
    private final UUID uniqueId = UUID.randomUUID();
    private final Map<String, Object> fields = new LinkedHashMap<>();

    /**
     * Evaluates and applies the declared field defaults. Run before the constructor body.
     */
    public void initializeDefaults(Event event) {
        for (SkoopField field : skoopClass.getFields().values()) {
            initializeDefault(field, event);
        }
    }

    private void initializeDefault(SkoopField field, Event event) {
        if (field.getDefaultValue() == null) {
            return;
        }

        Object[] values = field.getDefaultValue().getArray(event);

        if (field.getType().isPlural()) {
            if (acceptsAll(field, values)) {
                setField(field, values.clone());
            }

            return;
        }

        if (values.length == 0 || !field.getType().accepts(values[0])) {
            return;
        }

        setField(field, values[0]);
    }

    public @Nullable Object getField(SkoopField field) {
        return fields.get(key(field));
    }

    public void setField(SkoopField field, @Nullable Object value) {
        if (value == null) {
            fields.remove(key(field));
            return;
        }

        fields.put(key(field), value);
    }

    private static String key(SkoopField field) {
        return field.getName().toLowerCase(Locale.ENGLISH);
    }

    private static boolean acceptsAll(SkoopField field, Object[] values) {
        for (Object value : values) {
            if (!field.getType().accepts(value)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public String toString() {
        return skoopClass.getName() + " object";
    }
}
