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

/**
 * The {@code class <name>} structure: declares fields, constructors and methods.
 * <p>
 * Loading happens in two passes so that member bodies can resolve the types of fields declared
 * further down the class.
 */
public class StructClass extends Structure {

    private static final Pattern FIELD_PATTERN = Pattern.compile(
            "^(?<static>static\\s+)?(?<name>[A-Za-z_][A-Za-z0-9_]*):\\s*(?<type>[\\w\\[\\] ]+?)(?:\\s*=\\s*(?<default>.+))?$"
    );
    private static final Pattern CONSTRUCTOR_PATTERN = Pattern.compile(
            "^constructor\\s*\\((?<args>.*)\\)$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern METHOD_PATTERN = Pattern.compile(
            "^(?<static>static\\s+)?method\\s+(?<name>[A-Za-z_][A-Za-z0-9_]*)\\s*\\((?<args>.*)\\)(?:\\s+returns\\s+(?<return>[\\w\\[\\] ]+))?$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern STATIC_BODY_PATTERN = Pattern.compile(
            "^static$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Set<String> RESERVED_NAMES = Set.of(
            "class", "constructor", "method", "function", "static", "extends", "this"
    );

    /**
     * Classes must be fully loaded before any trigger that references them is parsed, otherwise
     * {@code Counter.total} would be looked up against a class that has no members yet. Sits just
     * after Skript's functions (400) and well before events and commands (the 1000 default).
     */
    private static final Priority PRIORITY = new Priority(450);

    private String className;
    private SkoopClass skoopClass;
    private EntryContainer entryContainer;

    public static void register(Registration reg) {
        reg.newStructure(StructClass.class, "class <([A-Za-z_][A-Za-z0-9_]*)>")
                .name("Skoop Class")
                .description("Declares a Skoop class with its fields, constructors and methods.")
                .examples(
                        "class Dog:",
                        "\tname: string",
                        "\tage: number = 0",
                        "",
                        "\tconstructor(name: string):",
                        "\t\tset this.name to constructor argument name",
                        "",
                        "\tmethod bark() returns string:",
                        "\t\treturn \"%this.name% barks!\""
                )
                .since("1.0.0")
                .register();
    }

    @Override
    public boolean init(Literal<?>[] literals, int matchedPattern, SkriptParser.ParseResult parseResult, @UnknownNullability EntryContainer entryContainer) {
        this.className = parseResult.regexes.getFirst().group(1).trim();
        this.entryContainer = entryContainer;
        this.skoopClass = new SkoopClass(className);

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

        if (!loadFields(source) || !loadMembers(source)) {
            Skoop.getInstance().getClassRegistry().unregister(skoopClass);
            return false;
        }

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
        skoopClass.clearStaticState();
        Skoop.getInstance().getClassRegistry().unregister(skoopClass);
    }

    // FIRST PASS: FIELDS

    private boolean loadFields(SectionNode source) {
        for (Node child : source) {
            if (!(child instanceof SimpleNode simpleNode)) {
                continue;
            }

            String line = ScriptLoader.replaceOptions(simpleNode.getKey());
            if (line == null || line.isBlank()) {
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
            Skript.error("Invalid field '" + line + "'. Expected: <name>: <type> [= <default>]");
            return false;
        }

        boolean isStatic = matcher.group("static") != null;
        String fieldName = matcher.group("name");
        String typeName = matcher.group("type").trim();
        String defaultInput = matcher.group("default");

        if (isReserved(fieldName)) {
            Skript.error("'" + fieldName + "' is a reserved keyword and cannot be used as a field name.");
            return false;
        }

        // Static and instance fields share a namespace, so that 'Foo.bar' and '{_foo}.bar' can
        // never mean two different fields in the same class.
        if (skoopClass.hasField(fieldName) || skoopClass.hasStaticField(fieldName)) {
            Skript.error("Duplicate field '" + fieldName + "' in class '" + className + "'.");
            return false;
        }

        SkoopType type = SkoopType.resolveType(typeName);
        if (type == null) {
            Skript.error("Unknown type '" + typeName + "' for field '" + fieldName + "'.");
            return false;
        }

        Expression<?> defaultValue = null;

        if (defaultInput != null && !defaultInput.isBlank()) {
            defaultValue = parseDefaultValue(defaultInput, fieldName, type);

            if (defaultValue == null) {
                return false;
            }
        }

        SkoopField field = new SkoopField(fieldName, type, defaultValue);

        if (isStatic) {
            skoopClass.addStaticField(field);
        } else {
            skoopClass.addField(field);
        }

        return true;
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
        boolean isStatic = matcher.group("static") != null;
        String methodName = matcher.group("name");
        String returnTypeName = matcher.group("return");
        String label = (isStatic ? "static method '" : "method '") + methodName + "'";

        List<SkoopParameter> parameters = parseParameters(matcher.group("args"), label);
        if (parameters == null) {
            return false;
        }

        SkoopType returnType = null;

        if (returnTypeName != null && !returnTypeName.isBlank()) {
            returnType = SkoopType.resolveType(returnTypeName.trim());

            if (returnType == null) {
                Skript.error("Unknown return type '" + returnTypeName.trim() + "' for " + label + ".");
                return false;
            }
        }

        Trigger trigger = loadBody(node, parameters, returnType, isStatic,
                "skoop method", SkoopMethodEvent.class, new EvtMethod(),
                (isStatic ? "static method " : "method ") + className + "." + methodName);
        if (trigger == null) {
            return false;
        }

        SkoopMethod method = new SkoopMethod(methodName, parameters, returnType, trigger);

        if (isStatic) {
            if (skoopClass.hasStaticMethod(method)) {
                Skript.error("Duplicate static method '" + methodName + method.getSignature()
                        + "' in class '" + className + "'.");
                return false;
            }

            skoopClass.addStaticMethod(method);
            return true;
        }

        if (skoopClass.hasMethod(method)) {
            Skript.error("Duplicate method '" + methodName + method.getSignature() + "' in class '" + className + "'.");
            return false;
        }

        skoopClass.addMethod(method);
        return true;
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
        return "class " + className;
    }
}
