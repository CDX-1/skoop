package rip.cdx.skoop.api;

import ch.njol.skript.lang.Expression;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Nullable;

/**
 * A declared field of a {@link SkoopClass}.
 */
@Getter
@RequiredArgsConstructor
public class SkoopField {

    private final String name;
    private final SkoopType type;
    private final @Nullable Expression<?> defaultValue;

    @Override
    public String toString() {
        return name + ": " + type.toSignatureString();
    }
}
