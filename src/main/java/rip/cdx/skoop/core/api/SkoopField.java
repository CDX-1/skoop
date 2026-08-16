package rip.cdx.skoop.core.api;

import ch.njol.skript.classes.ClassInfo;
import lombok.Data;

@Data
public class SkoopField {
    private final String name;
    private final ClassInfo<?> type;
    private final boolean plural;
}
