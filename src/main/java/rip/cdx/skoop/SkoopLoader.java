package rip.cdx.skoop;

import com.github.shanebeee.skr.Registration;
import rip.cdx.skoop.elements.effects.EffCallMethod;
import rip.cdx.skoop.elements.effects.EffReturnMethod;
import rip.cdx.skoop.elements.expressions.ExprCallMethod;
import rip.cdx.skoop.elements.expressions.ExprConstructorArgument;
import rip.cdx.skoop.elements.expressions.ExprField;
import rip.cdx.skoop.elements.expressions.ExprMethodArgument;
import rip.cdx.skoop.elements.expressions.ExprNew;
import rip.cdx.skoop.elements.expressions.ExprThis;
import rip.cdx.skoop.elements.structures.StructClass;
import rip.cdx.skoop.elements.types.SkoopTypes;

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
        ExprConstructorArgument.register(reg);
        ExprMethodArgument.register(reg);
        ExprCallMethod.register(reg);

        // Effects
        EffCallMethod.register(reg);
        EffReturnMethod.register(reg);

        reg.finalizeRegistration();
    }
}
