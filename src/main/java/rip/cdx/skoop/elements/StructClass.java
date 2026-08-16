package rip.cdx.skoop.elements;

import ch.njol.skript.ScriptLoader;
import ch.njol.skript.Skript;
import ch.njol.skript.config.Node;
import ch.njol.skript.config.SectionNode;
import ch.njol.skript.config.SimpleNode;
import ch.njol.skript.lang.Literal;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.Trigger;
import ch.njol.skript.lang.TriggerItem;
import ch.njol.skript.lang.parser.ParserInstance;
import ch.njol.skript.registrations.Classes;
import ch.njol.skript.util.Utils;
import com.github.shanebeee.skr.Registration;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;
import org.skriptlang.skript.lang.entry.EntryContainer;
import org.skriptlang.skript.lang.script.Script;
import org.skriptlang.skript.lang.structure.Structure;
import rip.cdx.skoop.Skoop;
import rip.cdx.skoop.core.api.SkoopClass;
import rip.cdx.skoop.core.api.SkoopConstructor;
import rip.cdx.skoop.core.api.SkoopField;
import rip.cdx.skoop.core.api.SkoopParameter;
import rip.cdx.skoop.elements.events.SkoopConstructorEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StructClass extends Structure {

    private static final Pattern FIELD_PATTERN = Pattern.compile("(?<name>[A-Za-z_][A-Za-z0-9_]*): (?<type>[\\w ]+)");
    private static final Pattern CONSTRUCTOR_PATTERN = Pattern.compile("^constructor\\s*\\((?<args>.*)\\)$", Pattern.CASE_INSENSITIVE);

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
    public boolean load() {
        if (Skoop.getInstance().getClassRegistry().contains(className)) {
            Skript.error("A class named '" + className + "' is already registered.");
            return false;
        }

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

                Skript.error("Unknown section '" + key + "' in class " + className);
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

        Skoop.getInstance()
                .getClassRegistry()
                .register(skoopClass);

        return true;
    }

    private boolean loadField(String line) {
        Matcher matcher = FIELD_PATTERN.matcher(line);

        if (!matcher.matches()) {
            Skript.error("Invalid class field '" + line + "'. Expected: <name>: <type>");
            return false;
        }

        String fieldName = matcher.group("name").toLowerCase(Locale.ENGLISH);
        String typeName = matcher.group("type").trim();

        if (isReserved(fieldName)) {
            Skript.error("'" + fieldName + "' is a reserved class keyword.");
            return false;
        }

        Utils.PluralResult plural = Utils.isPlural(typeName);
        var classInfo = Classes.getClassInfoFromUserInput(plural.updated());

        if (classInfo == null) {
            Skript.error("Unknown type '" + typeName + "' for field '" + fieldName + "'");
            return false;
        }

        SkoopField field = new SkoopField(fieldName, classInfo, plural.plural());
        skoopClass.addField(field);

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

    private SkoopParameter parseParameter(String input) {
        Matcher matcher = FIELD_PATTERN.matcher(input);

        if (!matcher.matches()) {
            Skript.error("Invalid constructor parameter '" + input + "'. Expected: <name>:<type>");
            return null;
        }

        String parameterName = matcher.group("name").toLowerCase(Locale.ENGLISH);
        String typeName = matcher.group("type").trim();

        Utils.PluralResult plural = Utils.isPlural(typeName);

        var classInfo = Classes.getClassInfoFromUserInput(plural.updated());

        if (classInfo == null) {
            Skript.error("Unknown type '" + typeName + "' for constructor parameter '" + parameterName + "'");
            return null;
        }

        return new SkoopParameter(parameterName, classInfo, plural.plural());
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
