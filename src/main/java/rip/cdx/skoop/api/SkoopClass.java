package rip.cdx.skoop.api;

import ch.njol.skript.lang.Trigger;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;
import rip.cdx.skoop.Skoop;
import rip.cdx.skoop.core.events.SkoopFieldDefaultEvent;
import rip.cdx.skoop.core.events.SkoopStaticEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
 * <p>
 * A class may extend one superclass, referenced <b>by name</b> for the same reason {@link SkoopType}
 * references classes by name: reloading the superclass's script replaces its instance, and holding
 * the old one would leave the subclass inheriting from a dead declaration. Member lookups therefore
 * walk the chain through the registry on every call.
 */
@Getter
public class SkoopClass {

    /**
     * Upper bound on how far a lookup follows the superclass chain.
     * <p>
     * Declaration order rules out a cycle being <em>declared</em>, but a partially reloaded script
     * can leave the registry holding one for a moment, and a lookup must not hang if it does.
     */
    private static final int MAX_HIERARCHY_DEPTH = 32;

    private final String name;
    private final @Nullable String superclassName;
    private final boolean isAbstract;

    private final SkoopMemberTable instanceMembers = new SkoopMemberTable();
    private final SkoopMemberTable staticMembers = new SkoopMemberTable();
    private final List<SkoopConstructor> constructors = new ArrayList<>();

    /** Values of the static fields declared by <em>this</em> class. Concurrent because triggers may run off the main thread. */
    private final Map<String, Object> staticValues = new ConcurrentHashMap<>();

    /** Bodies of the static sections, run once when the class finishes loading. */
    private final List<Trigger> staticBodies = new ArrayList<>();

    /**
     * Set once the declaring structure has finished loading its members. A subclass must not be
     * loaded before its superclass, and this is what makes that detectable.
     */
    @Setter
    private boolean loaded;

    /**
     * The superclass as it was last resolved from the registry. Read only when the registry no
     * longer holds it; see {@link #getSuperclass()}.
     */
    @Getter(AccessLevel.NONE)
    private @Nullable SkoopClass lastKnownSuperclass;

    public SkoopClass(String name) {
        this(name, null, false);
    }

    public SkoopClass(String name, @Nullable String superclassName, boolean isAbstract) {
        this.name = name;
        this.superclassName = superclassName;
        this.isAbstract = isAbstract;
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ENGLISH);
    }

    // HIERARCHY

    /**
     * @return the superclass, or null if this class extends nothing
     */
    public @Nullable SkoopClass getSuperclass() {
        if (superclassName == null) {
            return null;
        }

        SkoopClass registered = Skoop.getInstance().getClassRegistry().get(superclassName);
        if (registered != null) {
            lastKnownSuperclass = registered;
            return registered;
        }

        // The registry no longer holds it — the script unloaded, or the server is shutting down.
        // Skript writes its variables at exactly that point, and serializing an instance has to
        // know the inherited fields to write them, so the last resolved declaration stands in.
        // A live lookup always wins over this, so a reload is still picked up immediately.
        return lastKnownSuperclass;
    }

    /**
     * @return this class followed by each superclass, most derived first
     */
    public List<SkoopClass> getHierarchy() {
        List<SkoopClass> hierarchy = new ArrayList<>();

        SkoopClass current = this;
        for (int depth = 0; current != null && depth < MAX_HIERARCHY_DEPTH; depth++) {
            hierarchy.add(current);
            current = current.getSuperclass();
        }

        return hierarchy;
    }

    /**
     * @return whether this class is {@code other} or descends from it, compared by name so that
     *         instances created before a reload still answer correctly
     */
    public boolean isSubclassOf(String other) {
        for (SkoopClass ancestor : getHierarchy()) {
            if (ancestor.name.equalsIgnoreCase(other)) {
                return true;
            }
        }

        return false;
    }

    // INSTANCE MEMBERS

    public void addField(SkoopField field) {
        instanceMembers.addField(field);
    }

    /**
     * @return the field declared by this class itself, ignoring anything inherited
     */
    public @Nullable SkoopField getDeclaredField(String name) {
        return instanceMembers.getField(name);
    }

    /**
     * @return the field as seen by this class, following the superclass chain
     */
    public @Nullable SkoopField getField(String name) {
        for (SkoopClass current : getHierarchy()) {
            SkoopField field = current.instanceMembers.getField(name);
            if (field != null) {
                return field;
            }
        }

        return null;
    }

    public boolean hasField(String name) {
        return getField(name) != null;
    }

    /**
     * @return the fields declared by this class itself, keyed by lower-cased name
     */
    public Map<String, SkoopField> getFields() {
        return instanceMembers.getFields();
    }

    /**
     * @return every field visible on an instance of this class, superclass fields first so that
     *         their defaults are applied before a subclass overrides them
     */
    public Map<String, SkoopField> getAllFields() {
        Map<String, SkoopField> all = new LinkedHashMap<>();

        List<SkoopClass> hierarchy = getHierarchy();
        for (int i = hierarchy.size() - 1; i >= 0; i--) {
            all.putAll(hierarchy.get(i).instanceMembers.getFields());
        }

        return all;
    }

    public void addMethod(SkoopMethod method) {
        instanceMembers.addMethod(method);
    }

    public @Nullable SkoopMethod findMethod(String name, Object[] arguments) {
        for (SkoopMethod method : getMethods(name)) {
            if (method.matches(arguments)) {
                return method;
            }
        }

        return null;
    }

    /**
     * @return every overload of {@code name} visible on this class, the most derived declaration of
     *         each signature first, so that an override always shadows what it overrides
     */
    public List<SkoopMethod> getMethods(String name) {
        List<SkoopMethod> visible = new ArrayList<>();

        for (SkoopClass current : getHierarchy()) {
            for (SkoopMethod method : current.instanceMembers.getMethods(name)) {
                if (visible.stream().noneMatch(seen -> seen.hasSameParameters(method))) {
                    visible.add(method);
                }
            }
        }

        return visible;
    }

    /**
     * @return the overloads of {@code name} declared by this class itself
     */
    public List<SkoopMethod> getDeclaredMethods(String name) {
        return instanceMembers.getMethods(name);
    }

    public boolean hasMethod(SkoopMethod method) {
        return instanceMembers.hasMethod(method);
    }

    /**
     * @return the inherited method {@code method} would override, or null if it overrides nothing
     */
    public @Nullable SkoopMethod findOverriddenMethod(SkoopMethod method) {
        SkoopClass superclass = getSuperclass();
        if (superclass == null) {
            return null;
        }

        for (SkoopMethod inherited : superclass.getMethods(method.getName())) {
            if (inherited.hasSameParameters(method)) {
                return inherited;
            }
        }

        return null;
    }

    // STATIC MEMBERS

    public void addStaticField(SkoopField field) {
        staticMembers.addField(field);
    }

    public @Nullable SkoopField getDeclaredStaticField(String name) {
        return staticMembers.getField(name);
    }

    public @Nullable SkoopField getStaticField(String name) {
        SkoopClass owner = findStaticFieldOwner(name);
        return owner == null ? null : owner.staticMembers.getField(name);
    }

    public boolean hasStaticField(String name) {
        return getStaticField(name) != null;
    }

    public Map<String, SkoopField> getStaticFields() {
        return staticMembers.getFields();
    }

    public void addStaticMethod(SkoopMethod method) {
        staticMembers.addMethod(method);
    }

    public @Nullable SkoopMethod findStaticMethod(String name, Object[] arguments) {
        for (SkoopMethod method : getStaticMethods(name)) {
            if (method.matches(arguments)) {
                return method;
            }
        }

        return null;
    }

    /**
     * @return every static overload of {@code name} visible on this class, most derived first
     */
    public List<SkoopMethod> getStaticMethods(String name) {
        List<SkoopMethod> visible = new ArrayList<>();

        for (SkoopClass current : getHierarchy()) {
            for (SkoopMethod method : current.staticMembers.getMethods(name)) {
                if (visible.stream().noneMatch(seen -> seen.hasSameParameters(method))) {
                    visible.add(method);
                }
            }
        }

        return visible;
    }

    public boolean hasStaticMethod(SkoopMethod method) {
        return staticMembers.hasMethod(method);
    }

    /**
     * A static field has exactly one value no matter which subclass names it, so reads and writes
     * are routed to the class that declared it.
     *
     * @return the class holding the field's value, or null if no class in the chain declares it
     */
    public @Nullable SkoopClass findStaticFieldOwner(String fieldName) {
        for (SkoopClass current : getHierarchy()) {
            if (current.staticMembers.hasField(fieldName)) {
                return current;
            }
        }

        return null;
    }

    public @Nullable Object getStaticValue(String fieldName) {
        SkoopClass owner = findStaticFieldOwner(fieldName);
        if (owner == null) {
            return null;
        }

        return owner.staticValues.get(key(fieldName));
    }

    public void setStaticValue(String fieldName, @Nullable Object value) {
        SkoopClass owner = findStaticFieldOwner(fieldName);
        if (owner == null) {
            return;
        }

        if (value == null) {
            owner.staticValues.remove(key(fieldName));
            return;
        }

        owner.staticValues.put(key(fieldName), value);
    }

    public void addStaticBody(Trigger trigger) {
        staticBodies.add(trigger);
    }

    /**
     * Applies the static field defaults, then runs the static bodies in declaration order.
     * <p>
     * Called once the class has finished loading, so a static body can already see every static
     * field and method of its own class. Only this class's own statics are touched: a superclass
     * initializes its own exactly once, no matter how many subclasses it has.
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
                staticValues.put(key(field.getName()), values.clone());
            }

            return;
        }

        if (values.length == 0 || !field.getType().accepts(values[0])) {
            return;
        }

        staticValues.put(key(field.getName()), values[0]);
    }

    /** Drops static state, so a reload starts the class from its declared defaults again. */
    public void clearStaticState() {
        staticValues.clear();
    }

    // ABSTRACT MEMBERS

    /**
     * @return the abstract methods no class in the chain provides a body for, most derived first
     */
    public List<SkoopMethod> getUnimplementedMethods() {
        List<SkoopMethod> unimplemented = new ArrayList<>();
        List<SkoopMethod> seen = new ArrayList<>();

        for (SkoopClass current : getHierarchy()) {
            for (List<SkoopMethod> overloads : current.instanceMembers.getAllMethods().values()) {
                for (SkoopMethod method : overloads) {
                    // The most derived declaration of a signature is the one that counts; anything
                    // further up the chain has been overridden by it.
                    if (seen.stream().anyMatch(other -> other.hasSameSignature(method))) {
                        continue;
                    }

                    seen.add(method);

                    if (method.isAbstract()) {
                        unimplemented.add(method);
                    }
                }
            }
        }

        return unimplemented;
    }

    /**
     * @return the abstract fields no class in the chain redeclares concretely
     */
    public List<SkoopField> getUnimplementedFields() {
        List<SkoopField> unimplemented = new ArrayList<>();

        for (SkoopField field : getAllFields().values()) {
            if (field.isAbstract()) {
                unimplemented.add(field);
            }
        }

        return unimplemented;
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
        String declaration = (isAbstract ? "abstract class " : "class ") + name;
        return superclassName == null ? declaration : declaration + " extends " + superclassName;
    }
}
