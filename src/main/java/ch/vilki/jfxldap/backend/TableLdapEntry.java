package ch.vilki.jfxldap.backend;

import javafx.beans.property.SimpleStringProperty;

public class TableLdapEntry implements Comparable<TableLdapEntry> {
    public TableLdapEntry(String attributeName, String attributeValue) {
        AttributeName = new SimpleStringProperty(attributeName);
        AttributeValue = new SimpleStringProperty(attributeValue);
    }

    @Override
    public int compareTo(TableLdapEntry o) {
        return AttributeName.get().compareTo(o.AttributeName.get());
    }
    public String getAttributeName() {
        return AttributeName.get();
    }
    public SimpleStringProperty attributeNameProperty() {
        return AttributeName;
    }
    public void setAttributeName(String attributeName) {
        this.AttributeName.set(attributeName);
    }
    private final SimpleStringProperty AttributeName;
    public String getAttributeValue() {
        return AttributeValue.get();
    }
    public SimpleStringProperty attributeValueProperty() {
        return AttributeValue;
    }
    public void setAttributeValue(String attributeValue) {
        this.AttributeValue.set(attributeValue);
    }
    private final SimpleStringProperty AttributeValue;
}
