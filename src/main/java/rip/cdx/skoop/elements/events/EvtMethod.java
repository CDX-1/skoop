package rip.cdx.skoop.elements.events;

import rip.cdx.skoop.core.events.SkoopMethodEvent;

public class EvtMethod extends EvtSkoopMember {

    public EvtMethod() {
        super(SkoopMethodEvent.class, "skoop method");
    }
}
