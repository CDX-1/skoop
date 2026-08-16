package rip.cdx.skoop.core;

import rip.cdx.skoop.core.api.SkoopClass;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class SkoopClassRegistry {

    private final Map<String, SkoopClass> classes = new HashMap<>();

    public void register(SkoopClass skoopClass) {
        classes.put(
                skoopClass.getName().toLowerCase(Locale.ENGLISH),
                skoopClass
        );
    }

    public SkoopClass get(String name) {
        return classes.get(name.toLowerCase(Locale.ENGLISH));
    }

    public boolean contains(String name) {
        return classes.containsKey(name.toLowerCase(Locale.ENGLISH));
    }

    public void clear() {
        classes.clear();
    }
}