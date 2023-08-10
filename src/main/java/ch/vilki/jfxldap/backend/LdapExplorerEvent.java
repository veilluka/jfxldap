package ch.vilki.jfxldap.backend;

import javafx.event.Event;
import javafx.event.EventType;
import javafx.scene.control.TreeItem;

public class LdapExplorerEvent extends Event {

    public TreeItem<CustomEntry> treeEntry;

    public static EventType<LdapExplorerEvent> CONNECTION_ESTABLISHED = new EventType<>("CONNECTION_ESTABLISHED");
    public static EventType<LdapExplorerEvent> ELEMENT_SELECTED = new EventType<>("ELEMENT_SELECTED");


    public LdapExplorerEvent(EventType<? extends Event> eventType) {
        super(eventType);
    }
    public LdapExplorerEvent(EventType<? extends Event> eventType, TreeItem<CustomEntry> treeEntry) {
        super(eventType);
        this.treeEntry = treeEntry;
    }

}
