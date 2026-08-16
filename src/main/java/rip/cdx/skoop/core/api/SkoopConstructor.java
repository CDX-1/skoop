package rip.cdx.skoop.core.api;

import ch.njol.skript.lang.Trigger;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@RequiredArgsConstructor
public class SkoopConstructor {

    private final List<SkoopParameter> parameters;
    private final Trigger trigger;

    public boolean hasSameSignature(SkoopConstructor other) {
        if (parameters.size() != other.parameters.size()) {
            return false;
        }

        for (int i = 0; i < parameters.size(); i++) {
            SkoopParameter a = parameters.get(i);
            SkoopParameter b = other.parameters.get(i);

            if (a.type() != b.type()) {
                return false;
            }

            if (a.plural() != b.plural()) {
                return false;
            }
        }

        return true;
    }

    public String getSignature() {
        String args = parameters.stream()
                .map(parameter -> {
                    String type = parameter.type().getCodeName();
                    return parameter.plural() ? type + "[]" : type;
                })
                .collect(Collectors.joining(", "));

        return "(" + args + ")";
    }

    public boolean matches(Object[] arguments) {
        if (parameters.size() != arguments.length) {
            return false;
        }

        for (int i = 0; i < parameters.size(); i++) {
            SkoopParameter parameter = parameters.get(i);
            Object argument = arguments[i];

            if (argument == null) {
                return false;
            }

            Class<?> expectedType = parameter.type().getC();

            if (!expectedType.isAssignableFrom(argument.getClass())) {
                return false;
            }
        }

        return true;
    }

}