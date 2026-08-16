package rip.cdx.skoop.core;

import rip.cdx.skoop.core.api.SkoopParameter;

import java.util.List;

public final class SkoopMethodContext {

    private static final ThreadLocal<List<SkoopParameter>> PARAMETERS = new ThreadLocal<>();

    private SkoopMethodContext() {
    }

    public static void setParameters(List<SkoopParameter> parameters) {
        PARAMETERS.set(parameters);
    }

    public static void clear() {
        PARAMETERS.remove();
    }

    public static SkoopParameter getParameter(String name) {
        List<SkoopParameter> parameters = PARAMETERS.get();

        if (parameters == null) {
            return null;
        }

        for (SkoopParameter parameter : parameters) {
            if (parameter.name().equalsIgnoreCase(name)) {
                return parameter;
            }
        }

        return null;
    }
}