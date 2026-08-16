package rip.cdx.skoop.elements.types;

import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.classes.Parser;
import ch.njol.skript.lang.ParseContext;
import ch.njol.skript.registrations.Classes;
import org.jetbrains.annotations.Nullable;
import rip.cdx.skoop.api.SkoopObject;

/**
 * Registers the Skript types Skoop exposes to scripts.
 */
public class SkoopTypes {

    public static void register() {
        Classes.registerClass(new ClassInfo<>(SkoopObject.class, "skoopobject")
                .user("skoop ?objects?")
                .name("Skoop Object")
                .description("An instance of a Skoop class.")
                .since("1.0.0")
                .parser(new Parser<>() {
                    @Override
                    public @Nullable SkoopObject parse(String input, ParseContext context) {
                        return null;
                    }

                    @Override
                    public boolean canParse(ParseContext context) {
                        return false;
                    }

                    @Override
                    public String toString(SkoopObject object, int flags) {
                        return object.toString();
                    }

                    @Override
                    public String toVariableNameString(SkoopObject object) {
                        // Must be unique per instance, otherwise every instance of a class would
                        // collide onto the same variable index.
                        return "skoopobject:" + object.getUniqueId();
                    }
                }));
    }
}
