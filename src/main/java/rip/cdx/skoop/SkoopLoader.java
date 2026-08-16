package rip.cdx.skoop;

import com.github.shanebeee.skr.Registration;
import rip.cdx.skoop.elements.StructClass;

public class SkoopLoader {

    public static void register(Registration reg) {

        StructClass.register(reg);

        reg.finalizeRegistration();

    }

}
