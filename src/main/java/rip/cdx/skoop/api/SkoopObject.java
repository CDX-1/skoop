package rip.cdx.skoop.api;

import org.jetbrains.annotations.Nullable;
import rip.cdx.skoop.Skoop;
import rip.cdx.skoop.core.events.SkoopFieldDefaultEvent;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An instance of a {@link SkoopClass}.
 * <p>
 * Field values are keyed by the lower-cased field name rather than by {@link SkoopField} identity,
 * so that reloading the declaring script does not orphan the values of already existing instances.
 * The backing map is concurrent because Skript may run triggers off the main thread.
 * <p>
 * A deserialized instance starts out <em>unresolved</em>: Skript loads stored variables before it
 * loads scripts, so the declaring class does not exist yet at that point. Such an instance holds
 * the class name and the raw stored values, and binds to the real class on first use — at which
 * point the stored values are validated against the declared fields. Until then it is inert.
 */
public class SkoopObject {

    private final Map<String, Object> fields = new ConcurrentHashMap<>();

    private @Nullable SkoopClass skoopClass;
    private UUID uniqueId;

    /** Set only on a deserialized instance that has not been bound to its class yet. */
    private @Nullable String pendingClassName;
    private @Nullable Map<String, Object> pendingFields;

    public SkoopObject(SkoopClass skoopClass) {
        this.skoopClass = skoopClass;
        this.uniqueId = UUID.randomUUID();
    }

    /**
     * Used reflectively by the serializer, which has to register the instance before reading its
     * fields so that objects referencing each other in a cycle can be restored.
     */
    @SuppressWarnings("unused")
    private SkoopObject() {
    }

    /**
     * Stores a deserialized payload without validating it yet. Validation happens in
     * {@link #findSkoopClass()}, once the declaring class is actually available.
     */
    public void restore(String className, UUID uniqueId, Map<String, Object> values) {
        if (skoopClass != null) {
            throw new IllegalStateException("Skoop object is already bound to class " + skoopClass.getName());
        }

        this.pendingClassName = className;
        this.pendingFields = values;
        this.uniqueId = uniqueId;
    }

    /**
     * @return the declaring class, or null if it is not (or no longer) declared by a loaded script
     */
    public @Nullable SkoopClass findSkoopClass() {
        if (skoopClass != null) {
            return skoopClass;
        }

        if (pendingClassName == null) {
            return null;
        }

        SkoopClass resolved = Skoop.getInstance().getClassRegistry().get(pendingClassName);
        if (resolved == null) {
            return null;
        }

        bind(resolved);
        return resolved;
    }

    /**
     * @throws IllegalStateException if the declaring class is not available; call
     *         {@link #findSkoopClass()} instead wherever that is a recoverable condition
     */
    public SkoopClass getSkoopClass() {
        SkoopClass resolved = findSkoopClass();
        if (resolved == null) {
            throw new IllegalStateException("Skoop class '" + getClassName() + "' is not currently declared");
        }

        return resolved;
    }

    /**
     * @return the declaring class's name, available even while the instance is still unresolved
     */
    public String getClassName() {
        if (skoopClass != null) {
            return skoopClass.getName();
        }

        return pendingClassName == null ? "?" : pendingClassName;
    }

    public UUID getUniqueId() {
        if (uniqueId == null) {
            uniqueId = UUID.randomUUID();
        }

        return uniqueId;
    }

    /**
     * Adopts the class and applies any stored values that are still valid for it.
     * <p>
     * Driven by the class's declared fields, never by the keys in the payload, and every value is
     * checked against its declared type — a stored file that no longer matches the class, whether
     * through an edit or tampering, can only lose values here, never introduce them.
     */
    private void bind(SkoopClass resolved) {
        this.skoopClass = resolved;

        Map<String, Object> stored = pendingFields;
        this.pendingClassName = null;
        this.pendingFields = null;

        if (stored == null) {
            return;
        }

        for (SkoopField field : resolved.getFields().values()) {
            Object value = stored.get(key(field.getName()));
            if (value == null) {
                continue;
            }

            if (matches(field.getType(), value)) {
                setField(field, value);
            }
        }
    }

    /**
     * Checks a value against a declared type, handling the plural case elementwise.
     */
    public static boolean matches(SkoopType type, Object value) {
        if (!type.isPlural()) {
            return type.accepts(value);
        }

        if (!(value instanceof Object[] values)) {
            return false;
        }

        for (Object element : values) {
            if (!type.accepts(element)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Evaluates and applies the declared field defaults. Run before the constructor body.
     *
     * @see SkoopFieldDefaultEvent for why the caller's event is deliberately not used
     */
    public void initializeDefaults() {
        SkoopClass resolved = getSkoopClass();
        SkoopFieldDefaultEvent event = new SkoopFieldDefaultEvent(resolved.getName());

        for (SkoopField field : resolved.getFields().values()) {
            initializeDefault(field, event);
        }
    }

    private void initializeDefault(SkoopField field, SkoopFieldDefaultEvent event) {
        if (field.getDefaultValue() == null) {
            return;
        }

        Object[] values = field.getDefaultValue().getArray(event);

        if (field.getType().isPlural()) {
            if (matches(field.getType(), values)) {
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
        return fields.get(key(field.getName()));
    }

    public void setField(SkoopField field, @Nullable Object value) {
        if (value == null) {
            fields.remove(key(field.getName()));
            return;
        }

        fields.put(key(field.getName()), value);
    }

    /**
     * @return a snapshot of the set field values, keyed by lower-cased field name
     */
    public Map<String, Object> getFields() {
        return new LinkedHashMap<>(fields);
    }

    private static String key(String fieldName) {
        return fieldName.toLowerCase(Locale.ENGLISH);
    }

    @Override
    public String toString() {
        return getClassName() + " object";
    }
}
