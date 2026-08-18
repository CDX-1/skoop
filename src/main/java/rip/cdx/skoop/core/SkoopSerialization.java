package rip.cdx.skoop.core;

import ch.njol.yggdrasil.Fields;
import org.jetbrains.annotations.Nullable;
import rip.cdx.skoop.api.SkoopClass;
import rip.cdx.skoop.api.SkoopField;
import rip.cdx.skoop.api.SkoopObject;

import java.io.StreamCorruptedException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Converts {@link SkoopObject}s to and from Yggdrasil {@link Fields}, which is what Skript persists
 * variables as.
 * <p>
 * Stored data is untrusted: a variables file is a plain file on disk that anyone with filesystem or
 * plugin access can edit, and it survives across script edits, so its shape may no longer match the
 * class that is loaded now. Deserialization therefore treats the payload as a claim to be validated
 * rather than state to be restored:
 * <ul>
 *   <li>the payload must name a class; the name is only ever <em>looked up</em>, never used to
 *       construct or reflect on a type, so it cannot conjure one that is not declared;</li>
 *   <li>only fields the class declares are adopted — keys that are unknown, renamed away, or
 *       injected are dropped instead of becoming ad-hoc fields;</li>
 *   <li>every value is checked against the field's <em>declared</em> type before it is applied, so
 *       a payload cannot smuggle a value of the wrong type past code that later assumes the
 *       declared one;</li>
 *   <li>the entry count is capped, bounding the work one hostile entry can cause.</li>
 * </ul>
 * Values themselves are handed to Yggdrasil, which only reconstructs classes Skript has registered
 * a serializer for. Nothing here reflects on arbitrary class names or touches Java's native
 * serialization, so a payload cannot reach a type Skript does not already trust.
 * <p>
 * The class lookup and the per-field validation are deferred to
 * {@link SkoopObject#findSkoopClass()}: Skript loads variables before it loads scripts, so no
 * Skoop class exists yet at this point. The object stays inert until it binds.
 */
public final class SkoopSerialization {

    /** Field id holding the declaring class name. Prefixed to avoid colliding with a user field. */
    private static final String CLASS_KEY = "#class";

    /** Field id holding the instance's identity, used for variable name uniqueness. */
    private static final String ID_KEY = "#id";

    /**
     * Upper bound on how many stored entries are read for one object. A class cannot realistically
     * declare more fields than this, so anything beyond it is malformed or hostile.
     */
    private static final int MAX_FIELDS = 256;

    private SkoopSerialization() {
    }

    /**
     * Writes {@code object} as its class name plus the values of the fields the class declares.
     * <p>
     * Values not matching their declared type are dropped rather than written: they can only come
     * from a class that changed shape, and writing them back would persist the mismatch.
     */
    public static Fields serialize(SkoopObject object) throws StreamCorruptedException {
        SkoopClass skoopClass = object.findSkoopClass();
        if (skoopClass == null) {
            throw new StreamCorruptedException("Skoop class '" + object.getClassName()
                    + "' is not currently declared, so its objects cannot be saved");
        }

        Fields fields = new Fields();
        fields.putObject(CLASS_KEY, skoopClass.getName());
        fields.putObject(ID_KEY, object.getUniqueId().toString());

        Map<String, Object> values = object.getFields();

        for (SkoopField field : skoopClass.getAllFields().values()) {
            Object value = values.get(key(field.getName()));
            if (value == null) {
                continue;
            }

            if (!SkoopObject.matches(field.getType(), value)) {
                continue;
            }

            fields.putObject(key(field.getName()), value);
        }

        return fields;
    }

    /**
     * Reads a stored payload into {@code object}, to be validated when it binds to its class.
     *
     * @throws StreamCorruptedException if the payload is oversized or does not name a class
     */
    public static void deserialize(SkoopObject object, Fields fields) throws StreamCorruptedException {
        if (fields.size() > MAX_FIELDS) {
            throw new StreamCorruptedException("Skoop object holds " + fields.size()
                    + " entries, more than the maximum of " + MAX_FIELDS);
        }

        String className = fields.getObject(CLASS_KEY, String.class);
        if (className == null || className.isBlank()) {
            throw new StreamCorruptedException("Skoop object is missing its class name");
        }

        Map<String, Object> values = new LinkedHashMap<>();

        for (Fields.FieldContext context : fields) {
            String id = context.getID();
            if (id.equals(CLASS_KEY) || id.equals(ID_KEY) || context.isPrimitive()) {
                continue;
            }

            Object value = context.getObject();
            if (value != null) {
                values.put(key(id), value);
            }
        }

        object.restore(className, readUniqueId(fields), values);
    }

    private static UUID readUniqueId(Fields fields) {
        try {
            String raw = fields.getObject(ID_KEY, String.class);
            return raw == null ? UUID.randomUUID() : UUID.fromString(raw);
        } catch (StreamCorruptedException | IllegalArgumentException e) {
            // A malformed id only costs variable-name uniqueness, so recover instead of refusing.
            return UUID.randomUUID();
        }
    }

    private static String key(@Nullable String fieldName) {
        return fieldName == null ? "" : fieldName.toLowerCase(Locale.ENGLISH);
    }
}
