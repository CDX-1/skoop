package rip.cdx.skoop.core.api;

import ch.njol.skript.lang.Trigger;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@RequiredArgsConstructor
public class SkoopMethod {

    private final String name;
    private final List<SkoopParameter> parameters;
    private final Trigger trigger;

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

    public boolean hasSameSignature(SkoopMethod other) {
        if (!name.equalsIgnoreCase(other.name)) {
            return false;
        }

        if (parameters.size() != other.parameters.size()) {
            return false;
        }

        for (int i = 0; i < parameters.size(); i++) {
            SkoopType first = parameters.get(i).type();
            SkoopType second = other.parameters.get(i).type();

            if (!first.getName().equalsIgnoreCase(second.getName())) {
                return false;
            }

            if (first.isPlural() != second.isPlural()) {
                return false;
            }
        }

        return true;
    }

    public String getSignature() {
        String args = parameters.stream()
                .map(parameter -> {
                    SkoopType type = parameter.type();

                    return type.isPlural()
                            ? type.getName() + "[]"
                            : type.getName();
                })
                .collect(Collectors.joining(", "));

        return "(" + args + ")";
    }
}