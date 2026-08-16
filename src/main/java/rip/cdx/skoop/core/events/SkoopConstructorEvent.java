package rip.cdx.skoop.core.events;

import rip.cdx.skoop.api.SkoopConstructor;
import rip.cdx.skoop.api.SkoopObject;

/**
 * Runs the body of a {@link SkoopConstructor} against a freshly created instance.
 */
public class SkoopConstructorEvent extends SkoopInvocationEvent {

    public SkoopConstructorEvent(SkoopObject object, SkoopConstructor constructor, Object[] arguments) {
        super(object, constructor, arguments);
    }

    public SkoopConstructor getConstructor() {
        return (SkoopConstructor) getExecutable();
    }
}
