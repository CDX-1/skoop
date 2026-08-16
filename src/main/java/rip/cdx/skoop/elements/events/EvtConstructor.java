package rip.cdx.skoop.elements.events;

import rip.cdx.skoop.core.events.SkoopConstructorEvent;

public class EvtConstructor extends EvtSkoopMember {

    public EvtConstructor() {
        super(SkoopConstructorEvent.class, "skoop constructor");
    }
}
