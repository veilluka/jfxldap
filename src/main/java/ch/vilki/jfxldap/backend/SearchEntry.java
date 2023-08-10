package ch.vilki.jfxldap.backend;

import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.Entry;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SearchEntry extends CustomEntry {

    SimpleBooleanProperty ValueFound;
    public boolean getValueFound() {return ValueFound.get();}
    public SimpleBooleanProperty ValueFoundProperty() {return ValueFound;}
    public void setValueFound(boolean found) {this.ValueFound.set(found);}

    SimpleBooleanProperty ChildrenFound;
    public boolean getChildrenFound() {return ChildrenFound.get();}
    public SimpleBooleanProperty ChildrenFoundProperty() {return ChildrenFound;}
    public void setChildrenFound(boolean found) {
        if(this.getChildrenFound()) return;
         this.ChildrenFound.set(found);

    }

    SimpleListProperty<String> FoundInAttributes;
    public ObservableList<String> getFoundInAttributes() {
        return FoundInAttributes.get();
    }
    public SimpleListProperty<String> FoundInAttributesProperty() {
        return FoundInAttributes;
    }
    public void setFoundInAttributes(ObservableList<String> attributes) {
        this.FoundInAttributes.set(attributes);
    }

    public SearchEntry(Entry entry,
                       String searchString,
                       boolean ignoreCase,
                       List<String> searchAttributes,
                       boolean ignoreSearchAttributes,
                       boolean regex,
                       boolean notContains,
                       boolean exactMatch)
    {
        super(entry);
        ValueFound = new SimpleBooleanProperty(false);
         Set<String> foundInAttributes = new HashSet<>();
        ChildrenFound = new SimpleBooleanProperty(false);
        Collection<Attribute> attributes = entry.getAttributes();
        Set<String> searchAtts = new HashSet<>();
        if(searchAttributes != null) for(String s: searchAttributes) searchAtts.add(s.toLowerCase());
        for(Attribute at: attributes)
        {
            boolean runSearch = false;
            if(searchAttributes != null && !searchAttributes.isEmpty())
            {
                if(ignoreSearchAttributes)
                {
                    if(!searchAttributes.contains(at.getName().toLowerCase())) runSearch = true;
                }
                else
                {
                    if(searchAttributes.contains(at.getName().toLowerCase())) runSearch = true;
                }
            }
            else
            {
                runSearch = true;
            }
            if(runSearch)
            {
                String values[] = at.getValues();
                if(values != null && values.length > 0)
                {
                    for(String v: values) {
                        if (match(v, searchString, ignoreCase, regex, exactMatch)) {
                            setValueFound(true);
                            foundInAttributes.add(at.getName());
                        }
                    }
               }
            }
        }
        if(notContains) // we are looking for entries which do not have value searched for
        {
            if(!foundInAttributes.isEmpty()) // value found in some attributes, we are not intressted for this entry
            {
                setValueFound(false); // set to false so that entry is filtered out later on
            }
            else // value not found, we are looking for this entry, but it has been flagged as false. flag it as true
            {
                setValueFound(true);
            }
            FoundInAttributes = new SimpleListProperty<String>();
        }
        else
        {
            if(!foundInAttributes.isEmpty())
            {
                ObservableList<String> l = FXCollections.observableArrayList();
                l.addAll(foundInAttributes);
                FoundInAttributes = new SimpleListProperty<String>(l);

            }
            else FoundInAttributes = new SimpleListProperty<String>();
        }
    }

    public SearchEntry(Entry entry, List<String> searchAttributes, boolean ignoreSearchAttributes, String baseDN, Connection connection)
    {
        super(entry);
        ValueFound = new SimpleBooleanProperty(false);
        Set<String> foundInAttributes = new HashSet<>();
        ChildrenFound = new SimpleBooleanProperty(false);
        Collection<Attribute> attributes = entry.getAttributes();
        Set<String> searchAtts = new HashSet<>();
        if(searchAttributes != null) for(String s: searchAttributes) searchAtts.add(s.toLowerCase());
        for(Attribute at: attributes)
        {
            boolean runSearch = false;

            if(searchAttributes != null && !searchAttributes.isEmpty())
            {
                if(ignoreSearchAttributes)
                {
                    if(!searchAttributes.contains(at.getName().toLowerCase())) runSearch = true;
                }
                else
                {
                    if(searchAttributes.contains(at.getName().toLowerCase())) runSearch = true;
                }
            }
            else
            {
                runSearch = true;
            }
            if(runSearch)
            {
                String values[] = at.getValues();
                if(values != null && values.length > 0)
                {
                    for(String v: values)
                    {
                        if(hasDeadLink(v,baseDN,connection))
                        {
                            setValueFound(true);
                            foundInAttributes.add(at.getName());
                        }
                    }
                }
            }
        }
        if(!foundInAttributes.isEmpty())
        {
            ObservableList<String> l = FXCollections.observableArrayList();
            l.addAll(foundInAttributes);
            FoundInAttributes = new SimpleListProperty<String>(l);
        }
        else FoundInAttributes = new SimpleListProperty<String>();
    }

    private boolean hasDeadLink(String value, String baseDN, Connection connection)
    {
        if(value.contains(baseDN))
        {
            try {
                Entry e = connection.getEntry(value);
                if(e== null) return true;
            }
            catch (Exception e)
            {
                return false;
            }
        }
        return false;
    }


    public boolean match(String attValue, String searchValue,boolean ignoreCase, boolean regex, boolean exactMatch)
    {
        if(searchValue == null || searchValue.equalsIgnoreCase("")) return true;
        String att = null;
        String search = null;
        if(ignoreCase)
        {
            att = attValue.toLowerCase();
            search = searchValue.toLowerCase();
        }
        else
        {
            att = attValue;
            search = searchValue;
        }
        if(exactMatch)
        {
            if(att.equals(search)) return true;
            return false;
        }
        else
        {
            if(att.contains(search)) return true;
            return false;
        }
    }

    public String toString()
    {
        return getRdn();
    }



}
