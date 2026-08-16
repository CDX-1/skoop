package rip.cdx.skoop.core;

import ch.njol.skript.Skript;
import org.jetbrains.annotations.Nullable;
import rip.cdx.skoop.core.api.SkoopMethod;
import rip.cdx.skoop.core.api.SkoopObject;
import rip.cdx.skoop.core.events.SkoopMethodEvent;

public final class SkoopMethodExecutor {

    public static @Nullable Object execute(SkoopObject object, String methodName, Object[] arguments) {
        SkoopMethod method = object.getSkoopClass().findMethod(methodName, arguments);

        if (method == null) {
            Skript.error("No matching method '" + methodName + "' found in class '" + object.getSkoopClass().getName() + "'");
            return null;
        }

        SkoopMethodEvent event = new SkoopMethodEvent(object, method, arguments);
        method.getTrigger().execute(event);

        return event.getReturnValue();
    }
}