package rip.cdx.skoop.core;

import org.jetbrains.annotations.Nullable;
import rip.cdx.skoop.api.SkoopType;

/**
 * Implemented by syntax elements whose declared Skoop type is known at parse time, so that
 * chained member access (e.g. {@code method arg dog.owner.name}) can be resolved statically.
 */
public interface SkoopTypeProvider {

    @Nullable SkoopType getSkoopType();
}
