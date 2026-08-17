package rip.cdx.skoop.api;

import ch.njol.skript.lang.Trigger;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import rip.cdx.skoop.core.events.SkoopFieldDefaultEvent;
import rip.cdx.skoop.core.events.SkoopStaticEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A class declared by a script. Holds the declared members; instances are {@link SkoopObject}s.
 * <p>
 * Members are keyed case-insensitively, but keep the casing they were declared with for error messages.
 * <p>
 * Static members belong to the class rather than to any instance, so their values live here and are
 * discarded when the declaring script unloads.
 */
@Getter
public class SkoopClass {

    private final String name;

    private final SkoopMemberTable instanceMembers = new SkoopMemberTable();
    private final SkoopMemberTable staticMembers = new SkoopMemberTable();
    private final List<SkoopConstructor> constructors = new ArrayList<>();

    /** Values of the static fields. Concurrent because triggers may run off the main thread. */
    private final Map<String, Object> staticValues = new ConcurrentHashMap<>();

    /** Bodies of the {@code static:} sections, run once when the class finishes loading. */
    private final List<Trigger> staticBodies = new ArrayList<>();

    public SkoopClass(String name) {
        this.name = name;
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ENGLISH);
    }

    // INSTANCE MEMBERS

    public void addField(SkoopField field) {
        instanceMembers.addField(field);
    }

    public @Nullable SkoopField getField(String name) {
        return instanceMembers.getField(name);
    }

    public boolean hasField(String name) {
        return instanceMembers.hasField(name);
    }

    public Map<String, SkoopField> getFields() {
        return instanceMembers.getFields();
    }

    public void addMethod(SkoopMethod method) {
        instanceMembers.addMethod(method);
    }

    public @Nullable SkoopMethod findMethod(String name, Object[] arguments) {
        return instanceMembers.findMethod(name, arguments);
    }

    public List<SkoopMethod> getMethods(String name) {
        return instanceMembers.getMethods(name);
    }

    public boolean hasMethod(SkoopMethod method) {
        return instanceMembers.hasMethod(method);
    }

    // STATIC MEMBERS

    public void addStaticField(SkoopField field) {
        staticMembers.addField(field);
    }

    public @Nullable SkoopField getStaticField(String name) {
        return staticMembers.getField(name);
    }

    public boolean hasStaticField(String name) {
        return staticMembers.hasField(name);
    }

    public Map<String, SkoopField> getStaticFields() {
        return staticMembers.getFields();
    }

    public void addStaticMethod(SkoopMethod method) {
        staticMembers.addMethod(method);
    }

    public @Nullable SkoopMethod findStaticMethod(String name, Object[] arguments) {
        return staticMembers.findMethod(name, arguments);
    }

    public List<SkoopMethod> getStaticMethods(String name) {
        return staticMembers.getMethods(name);
    }

    public boolean hasStaticMethod(SkoopMethod method) {
        return staticMembers.hasMethod(method);
    }

    public @Nullable Object getStaticValue(String fieldName) {
        return staticValues.get(key(fieldName));
    }

    public void setStaticValue(String fieldName, @Nullable Object value) {
        if (value == null) {
            staticValues.remove(key(fieldName));
            return;
        }

        staticValues.put(key(fieldName), value);
    }

    public void addStaticBody(Trigger trigger) {
        staticBodies.add(trigger);
    }

    /**
     * Applies the static field defaults, then runs the {@code static:} bodies in declaration order.
     * <p>
     * Called once the class has finished loading, so a static body can already see every static
     * field and method of its own class.
     */
    public void runStaticInitializers() {
        SkoopFieldDefaultEvent defaultEvent = new SkoopFieldDefaultEvent(name);

        for (SkoopField field : staticMembers.getFields().values()) {
            initializeStaticDefault(field, defaultEvent);
        }

        for (Trigger body : staticBodies) {
            body.execute(new SkoopStaticEvent(this));
        }
    }

    private void initializeStaticDefault(SkoopField field, SkoopFieldDefaultEvent event) {
        if (field.getDefaultValue() == null) {
            return;
        }

        Object[] values = field.getDefaultValue().getArray(event);

        if (field.getType().isPlural()) {
            if (SkoopObject.matches(field.getType(), values)) {
                setStaticValue(field.getName(), values.clone());
            }

            return;
        }

        if (values.length == 0 || !field.getType().accepts(values[0])) {
            return;
        }

        setStaticValue(field.getName(), values[0]);
    }

    /** Drops static state, so a reload starts the class from its declared defaults again. */
    public void clearStaticState() {
        staticValues.clear();
    }

    // CONSTRUCTORS

    public void addConstructor(SkoopConstructor constructor) {
        constructors.add(constructor);
    }

    public @Nullable SkoopConstructor findConstructor(Object[] arguments) {
        for (SkoopConstructor constructor : constructors) {
            if (constructor.matches(arguments)) {
                return constructor;
            }
        }

        return null;
    }

    public boolean hasConstructor(SkoopConstructor constructor) {
        return constructors.stream()
                .anyMatch(existing -> existing.hasSameParameters(constructor));
    }

    @Override
    public String toString() {
        return "class " + name;
    }
}
