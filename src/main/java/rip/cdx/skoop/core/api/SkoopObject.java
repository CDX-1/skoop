package rip.cdx.skoop.core.api;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

public class SkoopObject {
    @Getter
    private final SkoopClass skoopClass;
    private final Map<SkoopField, Object> values = new HashMap<>();

    public SkoopObject(SkoopClass skoopClass) {
        this.skoopClass = skoopClass;

        for (SkoopField field : skoopClass.getFields().values()) {
            values.put(field, null);
        }
    }

    public Object getField(SkoopField field) {
        return values.get(field);
    }

    public void setField(SkoopField field, Object value) {
        values.put(field, value);
    }

    @Override
    public String toString() {
        return skoopClass.getName() + " instance";
    }
}
