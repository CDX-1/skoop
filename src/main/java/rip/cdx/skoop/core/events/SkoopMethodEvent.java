package rip.cdx.skoop.core.events;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;
import rip.cdx.skoop.api.SkoopMethod;
import rip.cdx.skoop.api.SkoopObject;

/**
 * Runs the body of a {@link SkoopMethod} against an instance and carries its return value back
 * to the caller.
 */
@Getter
@Setter
public class SkoopMethodEvent extends SkoopInvocationEvent {

    private @Nullable Object returnValue;

    public SkoopMethodEvent(SkoopObject object, SkoopMethod method, Object[] arguments) {
        super(object, method, arguments);
    }

    public SkoopMethod getMethod() {
        return (SkoopMethod) getExecutable();
    }
}
