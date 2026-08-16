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
import rip.cdx.skoop.core.events.SkoopMethodEvent;
import rip.cdx.skoop.elements.events.EvtConstructor;
import rip.cdx.skoop.elements.events.EvtMethod;

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
            "^(?<name>[A-Za-z_][A-Za-z0-9_]*):\\s*(?<type>[\\w\\[\\] ]+?)(?:\\s*=\\s*(?<default>.+))?$"
    );
    private static final Pattern CONSTRUCTOR_PATTERN = Pattern.compile(
            "^constructor\\s*\\((?<args>.*)\\)$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern METHOD_PATTERN = Pattern.compile(
            "^method\\s+(?<name>[A-Za-z_][A-Za-z0-9_]*)\\s*\\((?<args>.*)\\)(?:\\s+returns\\s+(?<return>[\\w\\[\\] ]+))?$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Set<String> RESERVED_NAMES = Set.of(
            "class", "constructor", "method", "function", "static", "extends", "this"
    );

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

    @Override
    public void unload() {
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

        String fieldName = matcher.group("name");
        String typeName = matcher.group("type").trim();
        String defaultInput = matcher.group("default");

        if (isReserved(fieldName)) {
            Skript.error("'" + fieldName + "' is a reserved keyword and cannot be used as a field name.");
            return false;
        }

        if (skoopClass.hasField(fieldName)) {
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
            defaultValue = parseDefaultValue(defaultInput, fieldName);

            if (defaultValue == null) {
                return false;
            }
        }

        skoopClass.addField(new SkoopField(fieldName, type, defaultValue));
        return true;
    }

    private @Nullable Expression<?> parseDefaultValue(String input, String fieldName) {
        Expression<?> parsed = new SkriptParser(input, SkriptParser.ALL_FLAGS, ParseContext.DEFAULT)
                .parseExpression(Object.class);

        if (parsed == null) {
            Skript.error("Could not parse the default value '" + input + "' of field '" + fieldName + "'.");
            return null;
        }

        Expression<?> converted = parsed.getConvertedExpression(Object.class);
        if (converted == null) {
            Skript.error("Could not convert the default value of field '" + fieldName + "'.");
            return null;
        }

        return converted;
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

        Trigger trigger = loadBody(node, parameters, null,
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
        String methodName = matcher.group("name");
        String returnTypeName = matcher.group("return");

        List<SkoopParameter> parameters = parseParameters(matcher.group("args"), "method '" + methodName + "'");
        if (parameters == null) {
            return false;
        }

        SkoopType returnType = null;

        if (returnTypeName != null && !returnTypeName.isBlank()) {
            returnType = SkoopType.resolveType(returnTypeName.trim());

            if (returnType == null) {
                Skript.error("Unknown return type '" + returnTypeName.trim() + "' for method '" + methodName + "'.");
                return false;
            }
        }

        Trigger trigger = loadBody(node, parameters, returnType,
                "skoop method", SkoopMethodEvent.class, new EvtMethod(),
                "method " + className + "." + methodName);
        if (trigger == null) {
            return false;
        }

        SkoopMethod method = new SkoopMethod(methodName, parameters, returnType, trigger);
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
    private @Nullable Trigger loadBody(SectionNode node, List<SkoopParameter> parameters, @Nullable SkoopType returnType,
                                       String eventName, Class<? extends Event> eventType,
                                       SkriptEvent skriptEvent, String triggerName) {
        ParserInstance parser = ParserInstance.get();
        ParserInstance.Backup backup = parser.backup();

        Script script = parser.getCurrentScript();
        ArrayList<TriggerItem> items;
        boolean delayed;

        try {
            parser.setCurrentEvent(eventName, eventType);
            SkoopParseContext.enter(skoopClass, parameters, returnType);

            items = ScriptLoader.loadItems(node);
            delayed = parser.getHasDelayBefore() != Kleenean.FALSE;
        } finally {
            SkoopParseContext.exit();
            parser.restoreBackup(backup);
        }

        if (delayed) {
            Skript.error("A " + triggerName + " body cannot be delayed: the caller runs it synchronously "
                    + "and would never see the result.");
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

        for (String rawArg : args.split(",")) {
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
