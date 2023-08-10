package ch.vilki.jfxldap.backend;

import com.unboundid.ldap.sdk.Entry;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class CollectionEntry extends CustomEntry {
    static Logger logger = LogManager.getLogger(CollectionEntry.class);

    public static String IGNORE = "IGNORE";
    public static String EXPORT_ONLY = "EXPORT_ONLY";
    public static String NONE = "NONE";

    SimpleBooleanProperty Subtree;

    public boolean isSubtree() {
        return Subtree.get();
    }

    public SimpleBooleanProperty subtreeProperty() {
        return Subtree;
    }

    public void setSubtree(boolean subtree) {
        this.Subtree.set(subtree);
    }

    SimpleBooleanProperty OverwriteEntry;

    public boolean getOverwriteEntry() {
        return OverwriteEntry.get();
    }

    public SimpleBooleanProperty overwriteEntryProperty() {
        return OverwriteEntry;
    }

    public void setOverwriteEntry(boolean overwriteEntry) {
        this.OverwriteEntry.set(overwriteEntry);
    }

    SimpleBooleanProperty ExportRoot;

    public boolean getExportRoot() {
        return ExportRoot.get();
    }

    public SimpleBooleanProperty exportRootEntryProperty() {
        return ExportRoot;
    }

    public void setExportRoot(boolean exportRoot) {
        this.ExportRoot.set(exportRoot);
    }

    SimpleStringProperty AttributesAction;

    public String getAttributesAction() {
        return AttributesAction.get();
    }

    public SimpleStringProperty attributesActionProperty() {
        return AttributesAction;
    }

    public void setAttributesAction(Boolean ignore) {
        if (ignore == null) this.AttributesAction.set(NONE);
        else {
            if (ignore) this.AttributesAction.set(IGNORE);
            else this.AttributesAction.set(EXPORT_ONLY);
        }
    }

    public void setAttributesAction(String action) {
        AttributesAction.set(action);
    }

    SimpleListProperty<String> Attributes;

    public String getAttributes() {
        if (Attributes.get() == null) return "";
        StringBuilder builder = new StringBuilder();

        Attributes.get().stream().forEach(c -> {
            builder.append(c);
            builder.append(",");
        });
        if (builder.length() > 0) builder.deleteCharAt(builder.length() - 1);
        return builder.toString();
    }

    public Set<String> getAttributesAsSet() {
        return Attributes.stream().map(String::toLowerCase).collect(Collectors.toSet());
    }

    public SimpleListProperty<String> attributesProperty() {
        return Attributes;
    }

    public void setAttributes(ObservableList<String> attributes) {
        if (attributes == null) this.Attributes.clear();
        this.Attributes.set(attributes);
    }

    public void setAttributes(List<String> attributes) {
        this.Attributes.clear();
        this.Attributes.addAll(attributes);

    }

    public String getLdapFilter() {
        return LdapFilter.get();
    }

    public SimpleStringProperty ldapFilterProperty() {
        return LdapFilter;
    }

    public void setLdapFilter(String ldapFilter) {
        this.LdapFilter.set(ldapFilter);
    }

    SimpleStringProperty LdapFilter;


    SimpleBooleanProperty Selected;

    public boolean isSelected() {
        return Selected.get();
    }

    public SimpleBooleanProperty selectedProperty() {
        return Selected;
    }

    public void setSelected(boolean Selected) {
        this.Selected.set(Selected);
    }

    SimpleBooleanProperty ParentSelected;

    public boolean isParentSelected() {
        return ParentSelected.get();
    }

    public SimpleBooleanProperty parentSelectedProperty() {
        return ParentSelected;
    }

    public void setParentSelected(boolean ParentSelected) {
        this.ParentSelected.set(ParentSelected);
    }

    SimpleStringProperty DisplayDN;

    public String getDisplayDN() {
        if (DisplayDN == null || DisplayDN.get() == null) return "";
        return DisplayDN.get();
    }

    public SimpleStringProperty displayDNProperty() {
        return DisplayDN;
    }

    public void setDisplayDN(String DisplayDN) {
        this.DisplayDN.set(DisplayDN);
    }

    public CollectionEntry(String dn, boolean subtree, boolean deleteTarget,
                           List<String> attributes, String filterAction, String ldapFilter, String displayDN) {
        Dn = new SimpleStringProperty(dn);
        Subtree = new SimpleBooleanProperty(subtree);
        OverwriteEntry = new SimpleBooleanProperty(deleteTarget);
        ObservableList<String> l = FXCollections.observableArrayList();
        l.addAll(attributes);
        Attributes = new SimpleListProperty<>(l);
        Attributes.set(l);
        AttributesAction = new SimpleStringProperty(filterAction);
        LdapFilter = new SimpleStringProperty(ldapFilter);
        Selected = new SimpleBooleanProperty(false);
        DisplayDN = new SimpleStringProperty(displayDN);
        ParentSelected = new SimpleBooleanProperty(false);
        ExportRoot = new SimpleBooleanProperty(false);
    }

    public CollectionEntry(Entry entry) {
        super(entry);
        Subtree = new SimpleBooleanProperty(false);
        OverwriteEntry = new SimpleBooleanProperty(false);
        ObservableList<String> l = FXCollections.observableArrayList();
        Attributes = new SimpleListProperty<>(l);
        Attributes.set(l);
        AttributesAction = new SimpleStringProperty(NONE);
        Attributes.set(FXCollections.observableArrayList());
        LdapFilter = new SimpleStringProperty();
        Selected = new SimpleBooleanProperty(false);
        DisplayDN = new SimpleStringProperty();
        ParentSelected = new SimpleBooleanProperty(false);
        ExportRoot = new SimpleBooleanProperty(false);
    }

    public void update(CollectionEntry collectionEntry) {
        Subtree.set(collectionEntry.isSubtree());
        OverwriteEntry.set(collectionEntry.getOverwriteEntry());
        Attributes.set(collectionEntry.Attributes);
        AttributesAction.set(collectionEntry.getAttributesAction());
        LdapFilter.set(collectionEntry.getLdapFilter());
        Selected.set(collectionEntry.isSelected());
        ParentSelected.set(collectionEntry.isParentSelected());
    }

    public String getDescription() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("[DN=");
        stringBuilder.append(getDn());
        stringBuilder.append("]");

        stringBuilder.append("[OverwriteEntry=");
        stringBuilder.append(this.OverwriteEntry);
        stringBuilder.append("]");
        return stringBuilder.toString();

    }

    public String toString() {
        return Rdn.get();
    }

}
