package rip.cdx.skoop;

import com.github.shanebeee.skr.Registration;
import rip.cdx.skoop.elements.*;

public class SkoopLoader {

    public static void register(Registration reg) {

        SkoopTypes.register();

        ExprThis.register(reg);
        ExprField.register(reg);
        ExprConstructorArgument.register(reg);
        ExprNew.register(reg);
        StructClass.register(reg);

        reg.finalizeRegistration();

    }

}
