package rip.cdx.skoop.core.api;

import ch.njol.skript.lang.Expression;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Nullable;

@Getter
@RequiredArgsConstructor
public class SkoopField {
    private final String name;
    private final SkoopType type;
    private final @Nullable Expression<?> defaultValue;
}