package rip.cdx.skoop.elements;

import ch.njol.skript.ScriptLoader;
import ch.njol.skript.Skript;
import ch.njol.skript.config.Node;
import ch.njol.skript.config.SectionNode;
import ch.njol.skript.config.SimpleNode;
import ch.njol.skript.lang.*;
import ch.njol.skript.lang.parser.ParserInstance;
import com.github.shanebeee.skr.Registration;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;
import org.skriptlang.skript.lang.entry.EntryContainer;
import org.skriptlang.skript.lang.script.Script;
import org.skriptlang.skript.lang.structure.Structure;
import rip.cdx.skoop.Skoop;
import rip.cdx.skoop.core.SkoopMethodContext;
import rip.cdx.skoop.core.api.*;
import rip.cdx.skoop.core.events.SkoopConstructorEvent;
import rip.cdx.skoop.core.events.SkoopMethodEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private SkoopClass skoopClass;
    private String className;
    private EntryContainer entryContainer;

    public static void register(Registration reg) {
        reg.newStructure(StructClass.class, "class <([A-Za-z_][A-Za-z0-9_]*)>")
                .name("Skoop Class")
                .description("Register a Skoop class")
                .since("1.0.0")
                .register();
    }

    @Override
    public boolean init(Literal<?>[] literals, int i, SkriptParser.ParseResult parseResult, @UnknownNullability EntryContainer entryContainer) {
        this.className = parseResult.regexes.getFirst().group(1).trim();
        this.entryContainer = entryContainer;
        this.skoopClass = new SkoopClass(className);

        return true;
    }

    @Override
    public boolean preLoad() {
        if (Skoop.getInstance().getClassRegistry().contains(className)) {
            Skript.error("A class named '" + className + "' is already registered.");
            return false;
        }

        Skoop.getInstance().getClassRegistry().register(skoopClass);
        return true;
    }

    @Override
    public boolean load() {
        SectionNode node = entryContainer.getSource();

        for (Node child : node) {
            if (child instanceof SectionNode sectionNode) {
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
                    if (!loadMethod(
                            methodMatcher.group("name"),
                            methodMatcher.group("args"),
                            methodMatcher.group("return"),
                            sectionNode
                    )) {
                        return false;
                    }

                    continue;
                }

                Skript.error("Unknown section '" + key + "' in class '" + className + "'");
                return false;
            }

            if (child instanceof SimpleNode simpleNode) {
                String line = ScriptLoader.replaceOptions(simpleNode.getKey());

                if (line == null) {
                    continue;
                }

                if (!loadField(line)) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean loadField(String line) {
        Matcher matcher = FIELD_PATTERN.matcher(line);

        if (!matcher.matches()) {
            Skript.error("Invalid class field '" + line + "'. Expected: <name>: <type> [= <default>]");
            return false;
        }

        String fieldName = matcher.group("name").toLowerCase(Locale.ENGLISH);
        String typeName = matcher.group("type").trim();
        String defaultInput = matcher.group("default");

        if (isReserved(fieldName)) {
            Skript.error("'" + fieldName + "' is a reserved class keyword.");
            return false;
        }

        if (skoopClass.hasField(fieldName)) {
            Skript.error("Duplicate field '" + fieldName + "' in class '" + className + "'");
            return false;
        }

        SkoopType type = SkoopType.resolveType(typeName);

        if (type == null) {
            Skript.error("Unknown type '" + typeName + "' for field '" + fieldName + "'");
            return false;
        }

        Expression<?> defaultValue = null;

        if (defaultInput != null && !defaultInput.isBlank()) {
            defaultValue = new SkriptParser(defaultInput, SkriptParser.ALL_FLAGS, ParseContext.DEFAULT)
                    .parseExpression(Object.class);

            if (defaultValue == null) {
                Skript.error("Could not parse default value '" + defaultInput + "' for field '" + fieldName + "'");
                return false;
            }

            defaultValue = defaultValue.getConvertedExpression(Object.class);

            if (defaultValue == null) {
                Skript.error("Could not convert default value for field '" + fieldName + "'");
                return false;
            }
        }

        skoopClass.addField(new SkoopField(fieldName, type, defaultValue));
        return true;
    }

    private boolean loadConstructor(String args, SectionNode node) {
        List<SkoopParameter> parameters = new ArrayList<>();

        if (!args.isBlank()) {
            String[] splitArgs = args.split(",");

            for (String rawArg : splitArgs) {
                SkoopParameter parameter = parseParameter(rawArg.trim());

                if (parameter == null) {
                    return false;
                }

                parameters.add(parameter);
            }
        }

        ParserInstance parser = ParserInstance.get();
        ParserInstance.Backup backup = parser.backup();

        ArrayList<TriggerItem> items;

        try {
            parser.setCurrentEvent("skoop constructor", SkoopConstructorEvent.class);
            items = ScriptLoader.loadItems(node);
        } finally {
            parser.restoreBackup(backup);
        }

        Script script = parser.getCurrentScript();
        Trigger trigger = new Trigger(script, "constructor " + className, new EvtConstructor(), items);

        SkoopConstructor constructor = new SkoopConstructor(parameters, trigger);

        if (skoopClass.hasConstructor(constructor)) {
            Skript.error("Duplicate constructor " + constructor.getSignature() + " in class '" + className + "'");
            return false;
        }

        skoopClass.addConstructor(constructor);
        return true;
    }

    private boolean loadMethod(String name, String args, @Nullable String returnTypeName, SectionNode node) {
        String methodName = name.toLowerCase(Locale.ENGLISH);

        List<SkoopParameter> parameters = new ArrayList<>();

        if (!args.isBlank()) {
            String[] splitArgs = args.split(",");

            for (String rawArg : splitArgs) {
                SkoopParameter parameter = parseParameter(rawArg.trim());

                if (parameter == null) {
                    return false;
                }

                parameters.add(parameter);
            }
        }

        SkoopType returnType = null;

        if (returnTypeName != null && !returnTypeName.isBlank()) {
            returnType = SkoopType.resolveType(returnTypeName.trim());

            if (returnType == null) {
                Skript.error("Unknown return type '" + returnTypeName + "' for method '" + methodName + "'");
                return false;
            }
        }

        ParserInstance parser = ParserInstance.get();
        ParserInstance.Backup backup = parser.backup();

        ArrayList<TriggerItem> items;
        Script script = parser.getCurrentScript();

        try {
            parser.setCurrentEvent("skoop method", SkoopMethodEvent.class);

            SkoopMethodContext.setParameters(parameters);

            items = ScriptLoader.loadItems(node);
        } finally {
            SkoopMethodContext.clear();
            parser.restoreBackup(backup);
        }

        Trigger trigger = new Trigger(
                script,
                "method " + className + "." + methodName,
                new EvtMethod(),
                items
        );

        SkoopMethod method = new SkoopMethod(
                methodName,
                parameters,
                returnType,
                trigger
        );

        if (skoopClass.hasMethod(method)) {
            Skript.error("Duplicate method '" + methodName + method.getSignature() + "' in class '" + className + "'");
            return false;
        }

        skoopClass.addMethod(method);
        return true;
    }

    private SkoopParameter parseParameter(String input) {
        Matcher matcher = FIELD_PATTERN.matcher(input);

        if (!matcher.matches()) {
            Skript.error("Invalid constructor parameter '" + input + "'. Expected: <name>: <type>");
            return null;
        }

        String parameterName = matcher.group("name").toLowerCase(Locale.ENGLISH);
        String typeName = matcher.group("type").trim();

        SkoopType type = SkoopType.resolveType(typeName);

        if (type == null) {
            Skript.error("Unknown type '" + typeName + "' for constructor parameter '" + parameterName + "'");
            return null;
        }

        return new SkoopParameter(parameterName, type);
    }

    private boolean isReserved(String name) {
        return switch (name.toLowerCase(Locale.ENGLISH)) {
            case "constructor", "method", "function", "static", "extends", "class" -> true;
                    default -> false;
        };
    }

    @Override
    public String toString(@Nullable Event event, boolean b) {
        return "class " + className;
    }
}
