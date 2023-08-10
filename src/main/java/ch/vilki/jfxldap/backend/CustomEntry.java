package ch.vilki.jfxldap.backend;

import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.RDN;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class CustomEntry implements  Comparable<CustomEntry> {

    static Logger logger = LogManager.getLogger(CustomEntry.class);
    private boolean _dummy;
    public Set<String> get_objectClass() {
        return _objectClass;
    }
    private Set<String> _objectClass = new HashSet<>();
    public boolean is_dummy() {
        return _dummy;
    }
    public void set_dummy(boolean _dummy) {
        this._dummy = _dummy;
        setStyleProperty(ViewStyle.GREYED_OUT);
    }

    SimpleStringProperty Rdn;
    public String getRdn() {return Rdn.get();}
    public SimpleStringProperty rdnProperty() {return Rdn;}
    public void setRdn(String rdn) {
        if(Rdn == null) Rdn = new SimpleStringProperty();
        this.Rdn.set(rdn);
    }

    SimpleObjectProperty Entry;
    public com.unboundid.ldap.sdk.Entry getEntry() {
        if(Entry == null) return null;
           return (com.unboundid.ldap.sdk.Entry) Entry.get();
    }
    public SimpleObjectProperty entryProperty() {return Entry;}
    public void setEntry(Object entry) {this.Entry.set(entry);}

    SimpleStringProperty Dn;
    public String getDn() { return Dn.get();}
    public SimpleStringProperty dnProperty() {return Dn;}
    public void setDn(String dn) {this.Dn.set(dn);}

    public void set_nrOfChildren(int _nrOfChildren) {
        this._nrOfChildren = _nrOfChildren;
    }

    private int _nrOfChildren = 0;

    public enum ViewStyle {

        NEW(" -fx-text-fill: #222222;"),
        GREYED_OUT(" -fx-text-fill: #666666;"),
        ERROR("-fx-text-fill: red;"),
        PERSON("-fx-text-fill: #666666;"),
        PARENT(" -fx-text-fill: #d60e0e;");


        public String style;
        ViewStyle(String style){
            this.style = style;
        }

        public String getStyle() {
            return style;
        }
    }

    SimpleStringProperty StyleProperty;
    public String getStyleProperty() {return StyleProperty.get();}
    public SimpleStringProperty StyleProperty() {return StyleProperty;}
    public void setStyleProperty(ViewStyle viewStyle)
    {
        StyleProperty = new SimpleStringProperty(viewStyle.style);
    }

    public void readAllAttributes(Connection connection)
    {
        if(Entry.get() != null)
        {
            try {
                 Entry refreshed = connection.getEntry(
                            (( com.unboundid.ldap.sdk.Entry) Entry.get()).getDN(),new String[] {"*", "+"});
                    Entry.set(refreshed);
            }
            catch (Exception e){
                logger.error("Error reading entry->" + (( com.unboundid.ldap.sdk.Entry) Entry.get()).getDN(),e);
            }
        }
    }

    public CustomEntry(String dn)
    {
        _objectClass.add("top");
        Entry = new SimpleObjectProperty();
        Dn = new SimpleStringProperty(dn);
        Rdn = new SimpleStringProperty("");
        StyleProperty = new SimpleStringProperty(ViewStyle.NEW.style);
    }

    public CustomEntry() {
        _objectClass.add("top");
        Entry = new SimpleObjectProperty();
        Rdn = new SimpleStringProperty("");
        StyleProperty = new SimpleStringProperty(ViewStyle.NEW.style);
    }

    @Override
    public int compareTo(CustomEntry o) {
        try {
            return Rdn.get().toLowerCase().compareTo(o.Rdn.get().toLowerCase());
        }
        catch (Exception e)
        {
            logger.error("Exception occured during compareOperation, this rdn->" + Rdn.get() + " other->" + o.Rdn.get(),e);
        }
        return 0;
    }

    public CustomEntry(com.unboundid.ldap.sdk.Entry entry)
    {
        String dn= "ENTRY_NULL";
        String rdn = "ENTRY_NULL";
        if(entry != null)
        {
            Attribute oClass = entry.getObjectClassAttribute();
            if(oClass != null)    for(String cl: oClass.getValues()) _objectClass.add(cl.toLowerCase());
            dn  = entry.getDN();
            try {
                rdn = entry.getRDN().toString();
            }
            catch (Exception e)
            {
                logger.error("Exception",e);
            }
        }

        Dn = new SimpleStringProperty(dn);
        Entry = new SimpleObjectProperty(entry);
        Rdn = new SimpleStringProperty(rdn);
        if(entry != null) StyleProperty = new SimpleStringProperty(ViewStyle.NEW.style);
        else StyleProperty = new SimpleStringProperty(ViewStyle.ERROR.style);
    }

    public CustomEntry(String dn, String rdn, List<Attribute> attributes)
    {
        for(Attribute attribute: attributes)
        {
            if(attribute.getName().equalsIgnoreCase("objectclass"))
            {
                _objectClass = (Set<String>) Arrays.stream(attribute.getValues()).collect(Collectors.toSet());
            }
        }
        Dn = new SimpleStringProperty(dn);
        Entry entry = new Entry(dn,attributes);
        Entry = new SimpleObjectProperty(entry);
        Rdn = new SimpleStringProperty(rdn);
        StyleProperty = new SimpleStringProperty(ViewStyle.NEW.style);
    }

    public void setDisplayAttribute(String attributeName)
    {
        if(attributeName == null || is_dummy()) return;
        Attribute value = getEntry().getAttribute(attributeName);
        if(value != null)
        {
            Rdn.setValue(value.getValue());
        }
        else
        {
            try {
                RDN rdn = getEntry().getRDN();
                if(rdn != null)
                    Rdn.setValue(rdn.toString());
            }
            catch (Exception e){
                logger.error("Exception occured",e);
            }
        }
    }

    @Override
    public String toString()
    {
        if(Rdn.get() != null)
        {
            if(_nrOfChildren > 0)  return Rdn.get() + "["+_nrOfChildren+"]"  ;
            else return Rdn.get();
        }
        if(Dn.get() != null) return Dn.get() ;
        if(Entry.get() != null)
        {
            com.unboundid.ldap.sdk.Entry entry = (com.unboundid.ldap.sdk.Entry) Entry.get();
            try {
                if(entry.getRDN() != null)
                {
                    if(_nrOfChildren > 0)   return entry.getRDN().toString() + "["+_nrOfChildren+"]";
                    else return entry.getRDN().toString();
                }
            }
            catch (Exception e){}
            if(entry.getDN() != null) return entry.getDN() ;
        }
        return this.toString() ;
    }
    public String[] splitDN()
    {
        return Dn.get().split(",");
    }
}
