package rip.cdx.skoop.elements.types;

import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.classes.Parser;
import ch.njol.skript.classes.Serializer;
import ch.njol.skript.lang.ParseContext;
import ch.njol.skript.registrations.Classes;
import ch.njol.yggdrasil.Fields;
import org.jetbrains.annotations.Nullable;
import rip.cdx.skoop.api.SkoopObject;
import rip.cdx.skoop.core.SkoopSerialization;

import java.io.NotSerializableException;
import java.io.StreamCorruptedException;

/**
 * Registers the Skript types Skoop exposes to scripts.
 */
public class SkoopTypes {

    public static void register() {
        Classes.registerClass(new ClassInfo<>(SkoopObject.class, "skoopobject")
                .user("skoop ?objects?")
                .name("Skoop Object")
                .description("An instance of a Skoop class. Can be stored in variables; the class "
                        + "must still be declared when the variable is loaded again.")
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
                })
                .serializer(new Serializer<>() {
                    @Override
                    public Fields serialize(SkoopObject object) throws NotSerializableException {
                        try {
                            return SkoopSerialization.serialize(object);
                        } catch (StreamCorruptedException e) {
                            throw new NotSerializableException(e.getMessage());
                        }
                    }

                    @Override
                    public void deserialize(SkoopObject object, Fields fields) throws StreamCorruptedException {
                        SkoopSerialization.deserialize(object, fields);
                    }

                    @Override
                    protected boolean canBeInstantiated() {
                        // Instantiate first, then fill: Yggdrasil registers the instance before
                        // reading its fields, which is what lets objects referencing each other in
                        // a cycle be restored.
                        return true;
                    }

                    @Override
                    public boolean mustSyncDeserialization() {
                        return false;
                    }
                }));
    }
}
