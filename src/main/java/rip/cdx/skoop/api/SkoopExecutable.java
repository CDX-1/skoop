package rip.cdx.skoop.api;

import ch.njol.skript.lang.Trigger;
import lombok.Getter;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Shared behaviour of the invokable members of a {@link SkoopClass}:
 * a parameter list plus the {@link Trigger} holding the body.
 */
@Getter
public abstract class SkoopExecutable {

    private final List<SkoopParameter> parameters;
    private final Trigger trigger;

    protected SkoopExecutable(List<SkoopParameter> parameters, Trigger trigger) {
        this.parameters = List.copyOf(parameters);
        this.trigger = trigger;
    }

    /**
     * @return whether the supplied runtime arguments can be passed to this executable
     */
    public boolean matches(Object[] arguments) {
        if (parameters.size() != arguments.length) {
            return false;
        }

        for (int i = 0; i < parameters.size(); i++) {
            Object argument = arguments[i];
            if (argument == null || !parameters.get(i).type().accepts(argument)) {
                return false;
            }
        }

        return true;
    }

    /**
     * @return whether {@code other} declares the same parameter types, i.e. would be a duplicate overload
     */
    public boolean hasSameParameters(SkoopExecutable other) {
        if (parameters.size() != other.parameters.size()) {
            return false;
        }

        for (int i = 0; i < parameters.size(); i++) {
            if (!parameters.get(i).type().isSameAs(other.parameters.get(i).type())) {
                return false;
            }
        }

        return true;
    }

    /**
     * @return the parameter types formatted for error messages, e.g. {@code (string, number[])}
     */
    public String getSignature() {
        return parameters.stream()
                .map(parameter -> parameter.type().toSignatureString())
                .collect(Collectors.joining(", ", "(", ")"));
    }
}
