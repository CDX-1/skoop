package rip.cdx.skoop.api;

/**
 * A single named parameter of a {@link SkoopExecutable}.
 */
public record SkoopParameter(String name, SkoopType type) {

    @Override
    public String toString() {
        return name + ": " + type.toSignatureString();
    }
}
