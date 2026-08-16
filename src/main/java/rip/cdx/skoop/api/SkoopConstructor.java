package rip.cdx.skoop.api;

import ch.njol.skript.lang.Trigger;

import java.util.List;

/**
 * A constructor of a {@link SkoopClass}, run when a new instance is created.
 */
public class SkoopConstructor extends SkoopExecutable {

    public SkoopConstructor(List<SkoopParameter> parameters, Trigger trigger) {
        super(parameters, trigger);
    }

    @Override
    public String toString() {
        return "constructor" + getSignature();
    }
}
