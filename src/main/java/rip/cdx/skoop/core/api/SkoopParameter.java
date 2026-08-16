package rip.cdx.skoop.core.api;

import ch.njol.skript.classes.ClassInfo;

public record SkoopParameter(
        String name,
        ClassInfo<?> type,
        boolean plural
) {}
