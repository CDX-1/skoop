package rip.cdx.skoop.core;

import org.jetbrains.annotations.Nullable;
import rip.cdx.skoop.api.SkoopClass;

import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds every class currently declared by a loaded script, keyed case-insensitively by name.
 */
public final class SkoopClassRegistry {

    private final Map<String, SkoopClass> classes = new ConcurrentHashMap<>();

    private static String key(String name) {
        return name.toLowerCase(Locale.ENGLISH);
    }

    public void register(SkoopClass skoopClass) {
        classes.put(key(skoopClass.getName()), skoopClass);
    }

    public void unregister(SkoopClass skoopClass) {
        classes.remove(key(skoopClass.getName()), skoopClass);
    }

    public @Nullable SkoopClass get(String name) {
        return classes.get(key(name));
    }

    public boolean contains(String name) {
        return classes.containsKey(key(name));
    }

    public Collection<SkoopClass> getClasses() {
        return Collections.unmodifiableCollection(classes.values());
    }

    public void clear() {
        classes.clear();
    }
}
