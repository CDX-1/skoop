package rip.cdx.skoop.core.api;

import ch.njol.skript.config.SectionNode;
import lombok.Data;

import java.util.List;
import java.util.stream.Collectors;

@Data
public class SkoopConstructor {
    private final List<SkoopParameter> parameters;
    private final SectionNode body;

    public String getSignature() {
        String args = parameters.stream()
                .map(parameter -> {
                    String type = parameter.type().getCodeName();

                    if (parameter.plural()) {
                        type += "[]";
                    }

                    return type;
                })
                .collect(Collectors.joining(", "));

        return "(" + args + ")";
    }

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
}
