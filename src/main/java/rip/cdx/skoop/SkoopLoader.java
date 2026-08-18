package rip.cdx.skoop;

import ch.njol.skript.lang.Effect;
import com.github.shanebeee.skr.Registration;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import rip.cdx.skoop.elements.effects.EffCallMethod;
import rip.cdx.skoop.elements.effects.EffCallStaticMethod;
import rip.cdx.skoop.elements.effects.EffReturnMethod;
import rip.cdx.skoop.elements.expressions.ExprCallMethod;
import rip.cdx.skoop.elements.expressions.ExprCallStaticMethod;
import rip.cdx.skoop.elements.expressions.ExprConstructorArgument;
import rip.cdx.skoop.elements.expressions.ExprField;
import rip.cdx.skoop.elements.expressions.ExprMethodArgument;
import rip.cdx.skoop.elements.expressions.ExprNew;
import rip.cdx.skoop.elements.expressions.ExprStaticField;
import rip.cdx.skoop.elements.expressions.ExprThis;
import rip.cdx.skoop.elements.structures.StructClass;
import rip.cdx.skoop.elements.types.SkoopTypes;

import java.util.List;

/**
 * Registers every syntax element Skoop provides.
 */
public final class SkoopLoader {

    private SkoopLoader() {
    }

    public static void register(Registration reg) {
        // Types have to exist before any syntax referencing %skoopobject% is registered.
        SkoopTypes.register();

        // Structures
        StructClass.register(reg);

        // Expressions
        ExprThis.register(reg);
        ExprNew.register(reg);
        ExprField.register(reg);
        ExprStaticField.register(reg);
        ExprConstructorArgument.register(reg);
        ExprMethodArgument.register(reg);
        ExprCallMethod.register(reg);
        ExprCallStaticMethod.register(reg);

        // Effects
        EffCallMethod.register(reg);
        EffCallStaticMethod.register(reg);
        EffReturnMethod.register(reg);

        reg.finalizeRegistration();
        prioritizeEffects(reg);
    }

    /**
     * Re-registers Skoop's effects at {@link SyntaxInfo#SIMPLE}.
     * <p>
     * SkriptRegistration sets an explicit priority for expressions but not for effects, so an
     * effect falls back to Skript's estimate — which loses to the catch-all effect skript-reflect
     * registers ({@code <.+>}, claiming any line shaped like {@code something.identifier}). The
     * visible symptom is that {@code call {_dog}.bark} silently does nothing while
     * {@code call {_dog}.bark with 3} works, because only the argument-less form is that shape.
     * <p>
     * SIMPLE is what SkriptRegistration already gives the expression form of the very same syntax,
     * so this only puts {@code call %skoopobject%.method} on the footing its twin always had. The
     * patterns all start with a literal keyword and reject a non-Skoop receiver in {@code init},
     * so parsing them early cannot claim another plugin's syntax.
     */
    private static void prioritizeEffects(Registration reg) {
        SyntaxRegistry registry = reg.getAddon().syntaxRegistry();

        // Copied first: registering while iterating the live collection would be a concurrent
        // modification.
        for (SyntaxInfo<? extends Effect> info : List.copyOf(registry.syntaxes(SyntaxRegistry.EFFECT))) {
            if (info.type().getPackageName().startsWith(SkoopLoader.class.getPackageName())) {
                registry.unregister(SyntaxRegistry.EFFECT, info);
                registry.register(SyntaxRegistry.EFFECT, info.toBuilder().priority(SyntaxInfo.SIMPLE).build());
            }
        }
    }
}
