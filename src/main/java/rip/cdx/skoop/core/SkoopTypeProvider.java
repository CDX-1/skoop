package rip.cdx.skoop.core;

import org.jetbrains.annotations.Nullable;
import rip.cdx.skoop.core.api.SkoopType;

public interface SkoopTypeProvider {
    @Nullable SkoopType getSkoopType();
}