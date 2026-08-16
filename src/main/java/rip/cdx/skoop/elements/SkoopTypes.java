package rip.cdx.skoop.elements;

import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.classes.Parser;
import ch.njol.skript.lang.ParseContext;
import ch.njol.skript.registrations.Classes;
import org.jetbrains.annotations.Nullable;
import rip.cdx.skoop.core.api.SkoopObject;

public class SkoopTypes {

    public static void register() {
        Classes.registerClass(
                new ClassInfo<>(SkoopObject.class, "skoopobject")
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
                            public String toString(SkoopObject o, int flags) {
                                return o.toString();
                            }

                            @Override
                            public String toVariableNameString(SkoopObject o) {
                                return o.toString();
                            }
                        })
        );
    }
}