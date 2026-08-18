package rip.cdx.skoop.elements.structures;

import ch.njol.skript.ScriptLoader;
import ch.njol.skript.Skript;
import ch.njol.skript.config.Node;
import ch.njol.skript.config.SectionNode;
import ch.njol.skript.config.SimpleNode;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.Literal;
import ch.njol.skript.lang.ParseContext;
import ch.njol.skript.lang.SkriptEvent;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.Trigger;
import ch.njol.skript.lang.TriggerItem;
import ch.njol.skript.lang.parser.ParserInstance;
import ch.njol.util.Kleenean;
import com.github.shanebeee.skr.Registration;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;
import org.skriptlang.skript.lang.entry.EntryContainer;
import org.skriptlang.skript.lang.script.Script;
import org.skriptlang.skript.lang.structure.Structure;
import rip.cdx.skoop.Skoop;
import rip.cdx.skoop.api.SkoopClass;
import rip.cdx.skoop.api.SkoopConstructor;
import rip.cdx.skoop.api.SkoopField;
import rip.cdx.skoop.api.SkoopMethod;
import rip.cdx.skoop.api.SkoopParameter;
import rip.cdx.skoop.api.SkoopType;
import rip.cdx.skoop.core.SkoopParseContext;
import rip.cdx.skoop.core.events.SkoopConstructorEvent;
import rip.cdx.skoop.core.events.SkoopFieldDefaultEvent;
import rip.cdx.skoop.core.events.SkoopMethodEvent;
import rip.cdx.skoop.core.events.SkoopStaticEvent;
import rip.cdx.skoop.elements.events.EvtConstructor;
import rip.cdx.skoop.elements.events.EvtMethod;
import rip.cdx.skoop.elements.events.EvtStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The {@code class <name>} structure: declares fields, constructors and methods.
 * <p>
 * Loading happens in two passes so that member bodies can resolve the types of fields declared
 * further down the class. The first pass reads the bodyless declarations — fields and abstract
 * methods — and the second the ones that have a body.
 * <p>
 * A class may {@code extend} one already declared class. The superclass has to appear <em>before</em>
 * the subclass, for the same reason a {@code static:} body may only read statics of a class declared
 * above it: members are loaded in declaration order, so a superclass named further down has no
 * fields or methods yet when the subclass is parsed against it.
 */
public class StructClass extends Structure {

    private static final Pattern FIELD_PATTERN = Pattern.compile(
            "^(?<modifier>(?:static|abstract)\\s+)?(?<name>[A-Za-z_][A-Za-z0-9_]*):\\s*(?<type>[\\w\\[\\] ]+?)(?:\\s*=\\s*(?<default>.+))?$"
    );
    private static final Pattern CONSTRUCTOR_PATTERN = Pattern.compile(
            "^constructor\\s*\\((?<args>.*)\\)$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern METHOD_PATTERN = Pattern.compile(
            "^(?<modifier>(?:static|abstract)\\s+)?method\\s+(?<name>[A-Za-z_][A-Za-z0-9_]*)\\s*\\((?<args>.*)\\)(?:\\s+returns\\s+(?<return>[\\w\\[\\] ]+))?$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern STATIC_BODY_PATTERN = Pattern.compile(
            "^static$",
            Pattern.CASE_INSENSITIVE
    );

    private static final String STATIC_MODIFIER = "static";
    private static final String ABSTRACT_MODIFIER = "abstract";

    private static final Set<String> RESERVED_NAMES = Set.of(
            "class", "constructor", "method", "function", "static", "abstract", "extends", "super", "this"
    );

    /**
     * Classes must be fully loaded before any trigger that references them is parsed, otherwise
     * {@code Counter.total} would be looked up against a class that has no members yet. Sits just
     * after Skript's functions (400) and well before events and commands (the 1000 default).
     */
    private static final Priority PRIORITY = new Priority(450);

    private String className;
    private @Nullable String superclassName;
    private boolean isAbstract;
    private SkoopClass skoopClass;
    private EntryContainer entryContainer;

    public static void register(Registration reg) {
        reg.newStructure(StructClass.class,
                        "class <([A-Za-z_][A-Za-z0-9_]*)>",
                        "class <([A-Za-z_][A-Za-z0-9_]*)> extends <([A-Za-z_][A-Za-z0-9_]*)>",
                        "abstract class <([A-Za-z_][A-Za-z0-9_]*)>",
                        "abstract class <([A-Za-z_][A-Za-z0-9_]*)> extends <([A-Za-z_][A-Za-z0-9_]*)>")
                .name("Skoop Class")
                .description("Declares a Skoop class with its fields, constructors and methods. "
                        + "A class may extend one class declared above it. An abstract class cannot be "
                        + "instantiated and is the only kind of class that may declare abstract members.")
                .examples(
                        "abstract class Animal:",
                        "\tname: string = \"unnamed\"",
                        "\tabstract legs: number",
                        "",
                        "\tabstract method speak() returns string",
                        "",
                        "\tmethod describe() returns string:",
                        "\t\treturn \"%this.name% says %call this.speak%\"",
                        "",
                        "class Dog extends Animal:",
                        "\tlegs: number = 4",
                        "",
                        "\tmethod speak() returns string:",
                        "\t\treturn \"woof\""
                )
                .since("1.0.0")
                .register();
    }

    @Override
    public boolean init(Literal<?>[] literals, int matchedPattern, SkriptParser.ParseResult parseResult, @UnknownNullability EntryContainer entryContainer) {
        this.className = parseResult.regexes.getFirst().group(1).trim();
        this.isAbstract = matchedPattern >= 2;
        this.superclassName = matchedPattern % 2 == 1 ? parseResult.regexes.get(1).group(1).trim() : null;
        this.entryContainer = entryContainer;
        this.skoopClass = new SkoopClass(className, superclassName, isAbstract);

        return true;
    }

    @Override
    public Priority getPriority() {
        return PRIORITY;
    }

    @Override
    public boolean preLoad() {
        if (Skoop.getInstance().getClassRegistry().contains(className)) {
            Skript.error("A class named '" + className + "' is already declared.");
            return false;
        }

        // Registered early so that other classes can reference this one as a field or parameter type.
        Skoop.getInstance().getClassRegistry().register(skoopClass);
        return true;
    }

    @Override
    public boolean load() {
        SectionNode source = entryContainer.getSource();

        if (!loadSuperclass() || !loadDeclarations(source) || !loadMembers(source) || !checkAbstractMembersImplemented()) {
            Skoop.getInstance().getClassRegistry().unregister(skoopClass);
            return false;
        }

        skoopClass.setLoaded(true);
        return true;
    }

    /**
     * Runs the static field defaults and {@code static:} bodies.
     * <p>
     * Deferred to postLoad so that every class in the batch has finished loading first — a static
     * body may reference another class's statics, or construct one of its instances.
     */
    @Override
    public boolean postLoad() {
        skoopClass.runStaticInitializers();
        return true;
    }

    @Override
    public void unload() {
        skoopClass.setLoaded(false);
        skoopClass.clearStaticState();
        Skoop.getInstance().getClassRegistry().unregister(skoopClass);
    }

    // INHERITANCE

    /**
     * Checks that the declared superclass exists and has already been loaded.
     * <p>
     * Requiring it to be loaded is what keeps inherited members resolvable at parse time: a field
     * or method inherited from a class that has not read its own declarations yet would silently
     * fall back to an untyped {@code object}, quietly breaking plural fields and overload
     * resolution instead of reporting anything.
     */
    private boolean loadSuperclass() {
        if (superclassName == null) {
            return true;
        }

        if (superclassName.equalsIgnoreCase(className)) {
            Skript.error("Class '" + className + "' cannot extend itself.");
            return false;
        }

        SkoopClass superclass = Skoop.getInstance().getClassRegistry().get(superclassName);
        if (superclass == null) {
            Skript.error("There is no Skoop class named '" + superclassName + "' to extend.");
            return false;
        }

        if (!superclass.isLoaded()) {
            Skript.error("Class '" + superclass.getName() + "' has to be declared before '" + className
                    + "' extends it. Move it above this class, or into a script that loads first.");
            return false;
        }

        return true;
    }

    /**
     * Checks that a concrete class leaves nothing abstract behind. Only inherited members can get
     * this far: declaring an abstract member in a concrete class is rejected where it is declared.
     */
    private boolean checkAbstractMembersImplemented() {
        if (isAbstract) {
            return true;
        }

        List<SkoopField> fields = skoopClass.getUnimplementedFields();
        if (!fields.isEmpty()) {
            Skript.error("Class '" + className + "' does not declare a value for the inherited abstract "
                    + (fields.size() == 1 ? "field " : "fields ") + describeFields(fields)
                    + ". Redeclare " + (fields.size() == 1 ? "it" : "them")
                    + " here, or make this class abstract.");
            return false;
        }

        List<SkoopMethod> methods = skoopClass.getUnimplementedMethods();
        if (!methods.isEmpty()) {
            Skript.error("Class '" + className + "' does not implement the inherited abstract "
                    + (methods.size() == 1 ? "method " : "methods ") + describeMethods(methods)
                    + ". Give " + (methods.size() == 1 ? "it a body" : "them bodies")
                    + " here, or make this class abstract.");
            return false;
        }

        return true;
    }

    private static String describeFields(List<SkoopField> fields) {
        return fields.stream()
                .map(field -> "'" + field.getName() + ": " + field.getType().toSignatureString() + "'")
                .collect(Collectors.joining(", "));
    }

    private static String describeMethods(List<SkoopMethod> methods) {
        return methods.stream()
                .map(method -> "'" + method.getName() + method.getSignature() + "'")
                .collect(Collectors.joining(", "));
    }

    // FIRST PASS: BODYLESS DECLARATIONS (FIELDS AND ABSTRACT METHODS)

    private boolean loadDeclarations(SectionNode source) {
        for (Node child : source) {
            if (!(child instanceof SimpleNode simpleNode)) {
                continue;
            }

            String line = ScriptLoader.replaceOptions(simpleNode.getKey());
            if (line == null || line.isBlank()) {
                continue;
            }

            Matcher methodMatcher = METHOD_PATTERN.matcher(line);
            if (methodMatcher.matches()) {
                if (!loadAbstractMethod(methodMatcher)) {
                    return false;
                }

                continue;
            }

            if (!loadField(line)) {
                return false;
            }
        }

        return true;
    }

    private boolean loadField(String line) {
        Matcher matcher = FIELD_PATTERN.matcher(line);
        if (!matcher.matches()) {
            Skript.error("Invalid field '" + line + "'. Expected: [static|abstract] <name>: <type> [= <default>]");
            return false;
        }

        String modifier = readModifier(matcher);
        boolean isStaticField = STATIC_MODIFIER.equals(modifier);
        boolean isAbstractField = ABSTRACT_MODIFIER.equals(modifier);
        String fieldName = matcher.group("name");
        String typeName = matcher.group("type").trim();
        String defaultInput = matcher.group("default");

        if (isReserved(fieldName)) {
            Skript.error("'" + fieldName + "' is a reserved keyword and cannot be used as a field name.");
            return false;
        }

        if (isAbstractField && !isAbstract) {
            Skript.error("Field '" + fieldName + "' cannot be abstract: class '" + className
                    + "' is not abstract. Only an abstract class may leave a field for its subclasses to declare.");
            return false;
        }

        if (isAbstractField && defaultInput != null && !defaultInput.isBlank()) {
            Skript.error("Abstract field '" + fieldName + "' cannot have a default value: "
                    + "it exists to make each subclass supply one.");
            return false;
        }

        // Static and instance fields share a namespace, so that 'Foo.bar' and '{_foo}.bar' can
        // never mean two different fields in the same class.
        if (skoopClass.getDeclaredField(fieldName) != null || skoopClass.getDeclaredStaticField(fieldName) != null) {
            Skript.error("Duplicate field '" + fieldName + "' in class '" + className + "'.");
            return false;
        }

        SkoopType type = SkoopType.resolveType(typeName);
        if (type == null) {
            Skript.error("Unknown type '" + typeName + "' for field '" + fieldName + "'.");
            return false;
        }

        if (!checkFieldAgainstSuperclass(fieldName, type, isStaticField, isAbstractField)) {
            return false;
        }

        Expression<?> defaultValue = null;

        if (defaultInput != null && !defaultInput.isBlank()) {
            defaultValue = parseDefaultValue(defaultInput, fieldName, type);

            if (defaultValue == null) {
                return false;
            }
        }

        SkoopField field = new SkoopField(fieldName, type, defaultValue, isAbstractField);

        if (isStaticField) {
            skoopClass.addStaticField(field);
        } else {
            skoopClass.addField(field);
        }

        return true;
    }

    /**
     * A field may only reuse an inherited name when it is filling in an inherited <em>abstract</em>
     * field. Shadowing a concrete one is rejected: two fields of the same name on one object, one
     * reachable from the subclass and one from the superclass's own methods, is never what the
     * script meant.
     */
    private boolean checkFieldAgainstSuperclass(String fieldName, SkoopType type, boolean isStaticField, boolean isAbstractField) {
        SkoopClass superclass = skoopClass.getSuperclass();
        if (superclass == null) {
            return true;
        }

        SkoopField inheritedStatic = superclass.getStaticField(fieldName);
        if (inheritedStatic != null) {
            Skript.error("'" + fieldName + "' is already a static field of superclass '"
                    + superclass.getName() + "'.");
            return false;
        }

        SkoopField inherited = superclass.getField(fieldName);
        if (inherited == null) {
            return true;
        }

        if (isStaticField) {
            Skript.error("Field '" + fieldName + "' cannot be static: it is already an instance field of "
                    + "superclass '" + superclass.getName() + "'.");
            return false;
        }

        if (!inherited.isAbstract()) {
            Skript.error("Field '" + fieldName + "' is already declared by superclass '" + superclass.getName()
                    + "'. Skoop does not allow a subclass to shadow an inherited field.");
            return false;
        }

        if (!inherited.getType().isSameAs(type)) {
            Skript.error("Field '" + fieldName + "' is declared abstract as "
                    + inherited.getType().toSignatureString() + " by superclass '" + superclass.getName()
                    + "', so it has to keep that type here, not " + type.toSignatureString() + ".");
            return false;
        }

        if (isAbstractField) {
            Skript.error("Field '" + fieldName + "' is already abstract in superclass '"
                    + superclass.getName() + "'; redeclaring it as abstract changes nothing.");
            return false;
        }

        return true;
    }

    /**
     * Loads an {@code abstract method <name>(...)} declaration, which has no body and is therefore
     * written as a plain line rather than a section.
     */
    private boolean loadAbstractMethod(Matcher matcher) {
        String modifier = readModifier(matcher);
        String methodName = matcher.group("name");

        if (!ABSTRACT_MODIFIER.equals(modifier)) {
            Skript.error("Method '" + methodName + "' has no body. End the line with a colon and indent the "
                    + "body below it, or declare it as an abstract method.");
            return false;
        }

        if (!isAbstract) {
            Skript.error("Method '" + methodName + "' cannot be abstract: class '" + className
                    + "' is not abstract. Only an abstract class may leave a method for its subclasses to implement.");
            return false;
        }

        String label = "abstract method '" + methodName + "'";

        List<SkoopParameter> parameters = parseParameters(matcher.group("args"), label);
        if (parameters == null) {
            return false;
        }

        String returnTypeName = matcher.group("return");
        SkoopType returnType = null;

        if (returnTypeName != null && !returnTypeName.isBlank()) {
            returnType = SkoopType.resolveType(returnTypeName.trim());

            if (returnType == null) {
                Skript.error("Unknown return type '" + returnTypeName.trim() + "' for " + label + ".");
                return false;
            }
        }

        SkoopMethod method = new SkoopMethod(methodName, parameters, returnType, null, className);
        return addMethod(method, methodName, "abstract method");
    }

    // SECOND PASS: CONSTRUCTORS AND METHODS

    private boolean loadMembers(SectionNode source) {
        for (Node child : source) {
            if (!(child instanceof SectionNode sectionNode)) {
                continue;
            }

            String key = ScriptLoader.replaceOptions(sectionNode.getKey());
            if (key == null) {
                continue;
            }

            if (STATIC_BODY_PATTERN.matcher(key).matches()) {
                if (!loadStaticBody(sectionNode)) {
                    return false;
                }

                continue;
            }

            Matcher constructorMatcher = CONSTRUCTOR_PATTERN.matcher(key);
            if (constructorMatcher.matches()) {
                if (!loadConstructor(constructorMatcher.group("args"), sectionNode)) {
                    return false;
                }

                continue;
            }

            Matcher methodMatcher = METHOD_PATTERN.matcher(key);
            if (methodMatcher.matches()) {
                if (!loadMethod(methodMatcher, sectionNode)) {
                    return false;
                }

                continue;
            }

            Skript.error("Unknown section '" + key + "' in class '" + className + "'.");
            return false;
        }

        return true;
    }

    private boolean loadConstructor(String args, SectionNode node) {
        List<SkoopParameter> parameters = parseParameters(args, "constructor");
        if (parameters == null) {
            return false;
        }

        Trigger trigger = loadBody(node, parameters, null, false,
                "skoop constructor", SkoopConstructorEvent.class, new EvtConstructor(),
                "constructor " + className);
        if (trigger == null) {
            return false;
        }

        SkoopConstructor constructor = new SkoopConstructor(parameters, trigger);
        if (skoopClass.hasConstructor(constructor)) {
            Skript.error("Duplicate constructor " + constructor.getSignature() + " in class '" + className + "'.");
            return false;
        }

        skoopClass.addConstructor(constructor);
        return true;
    }

    private boolean loadMethod(Matcher matcher, SectionNode node) {
        String modifier = readModifier(matcher);
        boolean isStaticMethod = STATIC_MODIFIER.equals(modifier);
        String methodName = matcher.group("name");

        if (ABSTRACT_MODIFIER.equals(modifier)) {
            Skript.error("Abstract method '" + methodName + "' cannot have a body: it exists to make each "
                    + "subclass write one. Drop the colon and the indented lines below it.");
            return false;
        }

        String label = (isStaticMethod ? "static method '" : "method '") + methodName + "'";

        List<SkoopParameter> parameters = parseParameters(matcher.group("args"), label);
        if (parameters == null) {
            return false;
        }

        String returnTypeName = matcher.group("return");
        SkoopType returnType = null;

        if (returnTypeName != null && !returnTypeName.isBlank()) {
            returnType = SkoopType.resolveType(returnTypeName.trim());

            if (returnType == null) {
                Skript.error("Unknown return type '" + returnTypeName.trim() + "' for " + label + ".");
                return false;
            }
        }

        Trigger trigger = loadBody(node, parameters, returnType, isStaticMethod,
                "skoop method", SkoopMethodEvent.class, new EvtMethod(),
                (isStaticMethod ? "static method " : "method ") + className + "." + methodName);
        if (trigger == null) {
            return false;
        }

        SkoopMethod method = new SkoopMethod(methodName, parameters, returnType, trigger, className);

        if (isStaticMethod) {
            if (skoopClass.hasStaticMethod(method)) {
                Skript.error("Duplicate static method '" + methodName + method.getSignature()
                        + "' in class '" + className + "'.");
                return false;
            }

            skoopClass.addStaticMethod(method);
            return true;
        }

        return addMethod(method, methodName, "method");
    }

    /**
     * Adds an instance method after checking it against this class's own declarations and against
     * anything it overrides.
     */
    private boolean addMethod(SkoopMethod method, String methodName, String label) {
        if (skoopClass.hasMethod(method)) {
            Skript.error("Duplicate " + label + " '" + methodName + method.getSignature()
                    + "' in class '" + className + "'.");
            return false;
        }

        SkoopMethod overridden = skoopClass.findOverriddenMethod(method);
        if (overridden != null && !method.hasSameReturnType(overridden)) {
            Skript.error("Method '" + methodName + method.getSignature() + "' overrides '"
                    + overridden.getDeclaringClassName() + "." + methodName + overridden.getSignature()
                    + "', so it has to return " + describeReturnType(overridden) + ", not "
                    + describeReturnType(method) + ".");
            return false;
        }

        skoopClass.addMethod(method);
        return true;
    }

    private static String describeReturnType(SkoopMethod method) {
        SkoopType returnType = method.getReturnType();
        return returnType == null ? "nothing" : returnType.toSignatureString();
    }

    /**
     * Parses a member body under the given event, exposing its parameters to the parse context.
     *
     * @return the loaded trigger, or null if the body is invalid
     */
    private boolean loadStaticBody(SectionNode node) {
        Trigger trigger = loadBody(node, List.of(), null, true,
                "skoop static body", SkoopStaticEvent.class, new EvtStatic(),
                "static body of " + className);
        if (trigger == null) {
            return false;
        }

        skoopClass.addStaticBody(trigger);
        return true;
    }

    private @Nullable Trigger loadBody(SectionNode node, List<SkoopParameter> parameters, @Nullable SkoopType returnType,
                                       boolean isStatic, String eventName, Class<? extends Event> eventType,
                                       SkriptEvent skriptEvent, String triggerName) {
        ParserInstance parser = ParserInstance.get();
        ParserInstance.Backup backup = parser.backup();

        Script script = parser.getCurrentScript();
        ArrayList<TriggerItem> items;
        boolean delayed;

        try {
            parser.setCurrentEvent(eventName, eventType);
            SkoopParseContext.enter(skoopClass, parameters, returnType, isStatic);

            items = ScriptLoader.loadItems(node);
            delayed = parser.getHasDelayBefore() != Kleenean.FALSE;
        } finally {
            SkoopParseContext.exit();
            parser.restoreBackup(backup);
        }

        // Only a declared return value is actually broken by a delay: the caller reads it the
        // moment the call returns, so anything returned after the delay is silently dropped.
        // Void methods and constructors are free to delay -- timed effects are a normal reason to.
        if (delayed && returnType != null) {
            Skript.error("A method that returns " + returnType.toSignatureString() + " cannot be delayed: "
                    + "the caller reads the result as soon as the call returns, so a value returned "
                    + "after the delay would be lost. Drop the return type to make it a void method.");
            return null;
        }

        return new Trigger(script, triggerName, skriptEvent, items);
    }

    // SHARED

    /**
     * @return the lower-cased modifier the declaration was written with, or null if it had none
     */
    private static @Nullable String readModifier(Matcher matcher) {
        String modifier = matcher.group("modifier");
        return modifier == null ? null : modifier.trim().toLowerCase(Locale.ENGLISH);
    }

    /**
     * Parses a field's default value against the field's declared type, so a mismatch is an error
     * here rather than a value silently dropped when the object is constructed.
     * <p>
     * Parsed under {@link SkoopFieldDefaultEvent}, which carries no event-values: a default has to
     * mean the same thing no matter who constructs the object.
     */
    private @Nullable Expression<?> parseDefaultValue(String input, String fieldName, SkoopType type) {
        ParserInstance parser = ParserInstance.get();
        ParserInstance.Backup backup = parser.backup();

        Expression<?> parsed;

        try {
            parser.setCurrentEvent("skoop field default", SkoopFieldDefaultEvent.class);
            parsed = new SkriptParser(input, SkriptParser.ALL_FLAGS, ParseContext.DEFAULT)
                    .parseExpression(type.getValueClass());
        } finally {
            parser.restoreBackup(backup);
        }

        if (parsed == null) {
            Skript.error("Could not parse '" + input + "' as a " + type.toSignatureString()
                    + " default for field '" + fieldName + "'.");
            return null;
        }

        if (!type.isPlural() && !parsed.isSingle()) {
            Skript.error("Field '" + fieldName + "' holds a single " + type.getName()
                    + ", but its default value is a list.");
            return null;
        }

        return parsed;
    }

    /**
     * @param owner how to refer to the declaring member in error messages
     * @return the parsed parameters, or null if one of them is invalid
     */
    private @Nullable List<SkoopParameter> parseParameters(String args, String owner) {
        List<SkoopParameter> parameters = new ArrayList<>();
        if (args.isBlank()) {
            return parameters;
        }

        List<String> rawArgs = splitArguments(args);
        if (rawArgs == null) {
            Skript.error("Unbalanced brackets or quotes in the parameter list of " + owner + ".");
            return null;
        }

        for (String rawArg : rawArgs) {
            SkoopParameter parameter = parseParameter(rawArg.trim(), owner);
            if (parameter == null) {
                return null;
            }

            if (parameters.stream().anyMatch(existing -> existing.name().equalsIgnoreCase(parameter.name()))) {
                Skript.error("Duplicate parameter '" + parameter.name() + "' in " + owner + ".");
                return null;
            }

            parameters.add(parameter);
        }

        return parameters;
    }

    /**
     * Splits a parameter list on commas that are not nested inside brackets or a quoted string, so
     * that a type or value containing a comma stays in one piece.
     *
     * @return the segments, or null if the brackets or quotes are unbalanced
     */
    private static @Nullable List<String> splitArguments(String args) {
        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        int depth = 0;
        boolean quoted = false;

        for (int i = 0; i < args.length(); i++) {
            char character = args.charAt(i);

            if (character == '"') {
                // "" is an escaped quote inside a Skript string, not the end of one.
                if (quoted && i + 1 < args.length() && args.charAt(i + 1) == '"') {
                    current.append(character).append(args.charAt(++i));
                    continue;
                }

                quoted = !quoted;
            } else if (!quoted) {
                switch (character) {
                    case '(', '[', '{' -> depth++;
                    case ')', ']', '}' -> depth--;
                    case ',' -> {
                        if (depth == 0) {
                            segments.add(current.toString());
                            current.setLength(0);
                            continue;
                        }
                    }
                    default -> {
                    }
                }

                if (depth < 0) {
                    return null;
                }
            }

            current.append(character);
        }

        if (depth != 0 || quoted) {
            return null;
        }

        segments.add(current.toString());
        return segments;
    }

    private @Nullable SkoopParameter parseParameter(String input, String owner) {
        Matcher matcher = FIELD_PATTERN.matcher(input);
        if (!matcher.matches()) {
            Skript.error("Invalid parameter '" + input + "' in " + owner + ". Expected: <name>: <type>");
            return null;
        }

        if (matcher.group("modifier") != null) {
            Skript.error("Parameters cannot be declared '" + readModifier(matcher) + "' ('" + input
                    + "' in " + owner + ").");
            return null;
        }

        if (matcher.group("default") != null) {
            Skript.error("Parameters cannot have default values ('" + input + "' in " + owner + ").");
            return null;
        }

        String parameterName = matcher.group("name");
        String typeName = matcher.group("type").trim();

        SkoopType type = SkoopType.resolveType(typeName);
        if (type == null) {
            Skript.error("Unknown type '" + typeName + "' for parameter '" + parameterName + "' in " + owner + ".");
            return null;
        }

        return new SkoopParameter(parameterName, type);
    }

    private static boolean isReserved(String name) {
        return RESERVED_NAMES.contains(name.toLowerCase(Locale.ENGLISH));
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return skoopClass.toString();
    }
}
