package rip.cdx.skoop.core.api;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.event.Event;

import java.util.HashMap;
import java.util.Map;

@Getter
@RequiredArgsConstructor
public class SkoopObject {

    private final SkoopClass skoopClass;
    private final Map<SkoopField, Object> fields = new HashMap<>();

    public void initializeDefaults(Event event) {
        for (SkoopField field : skoopClass.getFields().values()) {
            if (field.getDefaultValue() == null) {
                continue;
            }

            Object[] values = field.getDefaultValue().getArray(event);

            if (field.getType().isPlural()) {
                if (!validateValues(field, values)) {
                    continue;
                }

                fields.put(field, values.clone());
                continue;
            }

            if (values.length == 0) {
                continue;
            }

            Object value = values[0];

            if (!field.getType().accepts(value)) {
                continue;
            }

            fields.put(field, value);
        }
    }

    private boolean validateValues(SkoopField field, Object[] values) {
        for (Object value : values) {
            if (value != null && !field.getType().accepts(value)) {
                return false;
            }
        }

        return true;
    }

    public Object getField(SkoopField field) {
        return fields.get(field);
    }

    public void setField(SkoopField field, Object value) {
        if (value == null) {
            fields.remove(field);
            return;
        }

        fields.put(field, value);
    }

    @Override
    public String toString() {
        return skoopClass.getName() + " object";
    }
}