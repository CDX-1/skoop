package rip.cdx.skoop.core.api;

import lombok.Data;

@Data
public class SkoopField {
    private final String name;
    private final SkoopType type;
}
