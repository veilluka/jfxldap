package ch.vilki.jfxldap.backend;

import com.unboundid.ldap.sdk.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CompResult implements java.io.Serializable, Comparable<CompResult> {
    public static enum ENTRY_TYPE {SOURCE, TARGET, BOTH};

    public static enum COMPARE_RESULT {
        ENTRY_EQUAL,
        ENTRY_NOT_EQUAL,
        ONLY_IN_SOURCE,
        ONLY_IN_TARGET,
        ENTRY_EQUAL_BUT_CHILDREN_NOT,
        COMPARE_ERROR,

    };

    private COMPARE_RESULT _compare_result = null;
    private String _dn = null;
    private String _parent_DN = null;
    private ENTRY_TYPE _entry_type;
    private boolean _entryEqual = false;
    private String _rdnDisplay = null;
    private List<Modification> _modificationsToTarget = null;
    private List<Modification> _modificationsToSource = null;
    private Boolean _childrenNotEqual = null;
    private HashMap<String, List<String>> _allValues = null;

    public SearchResultEntry get_sourceEntry() {
        return _sourceEntry;
    }

    private SearchResultEntry _sourceEntry = null;

    public Entry get_targetEntry() {
        return _targetEntry;
    }

    public void set_targetEntry(SearchResultEntry targetEntry) {
        this._targetEntry = targetEntry;
        if (_dn == null && targetEntry != null) _dn = _targetEntry.getDN();
    }

    private SearchResultEntry _targetEntry = null;


    @Override
    public int compareTo(CompResult o) {
        return getRDN().compareTo(o.getRDN());
    }
    public COMPARE_RESULT get_compare_result() {
        if(_compare_result==null) return COMPARE_RESULT.COMPARE_ERROR;
        return _compare_result;
    }
    public void set_compare_result(COMPARE_RESULT _compare_result) {
        this._compare_result = _compare_result;
    }
    public String getParent_DN() {
        return _parent_DN;
    }
    public void setParent_DN(String parent_DN) {
        this._parent_DN = parent_DN;
    }
    public void set_dn(String _dn) {
        this._dn = _dn;
    }
    public String get_dn() {
        return _dn;
    }
    public String get_rdnDisplay() {
        return _rdnDisplay;
    }
    public void set_rdnDisplay(String _rdnDisplay) {
        this._rdnDisplay = _rdnDisplay;
    }
    public Boolean get_childrenNotEqual() {
        return _childrenNotEqual;
    }
    public void set_childrenNotEqual(Boolean _childrenNotEqual) {
        this._childrenNotEqual = _childrenNotEqual;
    }
    public List<Modification> get_ModificationsToTarget() {
        return _modificationsToTarget;
    }
    public List<Modification> get_ModificationsToSource() {
        return _modificationsToSource;
    }
    public boolean is_entryEqual() {
        return _entryEqual;
    }
    public void set_entryEqual(boolean _entryEqual) {
        this._entryEqual = _entryEqual;
    }
    public ENTRY_TYPE get_entry_type() {
        return _entry_type;
    }
    public void set_entry_type(ENTRY_TYPE _entry_type) {
        this._entry_type = _entry_type;
    }
    public CompResult(SearchResultEntry entry) {
        if (entry != null) _dn = entry.getDN();
        _sourceEntry = entry;
    }
    public List getAttributeValues(String attributeName) {
        return _allValues.get(attributeName);
    }


    public CompResult clone() {
        CompResult compResult = new CompResult(_sourceEntry);
        compResult._targetEntry = _targetEntry;
        compResult._childrenNotEqual = _childrenNotEqual;
        compResult.set_compare_result(get_compare_result());
        compResult._dn = _dn;
        compResult._parent_DN = _parent_DN;
        compResult._entry_type = _entry_type;
        compResult._entryEqual = _entryEqual;
        compResult._rdnDisplay = _rdnDisplay;
        if (_modificationsToTarget != null) {
            compResult._modificationsToTarget = new ArrayList<Modification>();
            compResult._modificationsToTarget.addAll(_modificationsToTarget);
        }
        if (_modificationsToSource != null) {
            compResult._modificationsToSource = new ArrayList<Modification>();
            compResult._modificationsToSource.addAll(_modificationsToSource);
        }
        if (_allValues != null) {
            compResult._allValues = new HashMap<>();
            compResult._allValues.putAll(_allValues);
        }
        return compResult;
    }

    public void addAttributeValue(String attributeName, String value) {
        if (_allValues == null) _allValues = new HashMap<>();
        if (_allValues.containsKey(attributeName)) {
            List values = _allValues.get(attributeName);
            values.add(value);
            _allValues.put(attributeName, values);
        } else {
            List values = new ArrayList();
            values.add(value);
            _allValues.put(attributeName, values);
        }
    }

    private void addAttribute(String attributeName, String[] values) {
        if (values == null || values.length == 0) return;
        for (String v : values) {
            addAttributeValue(attributeName, v);
        }
    }

    public void setAttributes(Collection<Attribute> attributes) {
        for (Attribute at : attributes) {
            addAttribute(at.getName(), at.getValues());
        }
    }

    public List<Modification> findValue(String value) {
        List<Modification> modifications = new ArrayList<>();
        for (String attName : _allValues.keySet()) {
            List<String> values = _allValues.get(attName);
            for (String val : values) {
                List<String> sameValues = new ArrayList<>();
                if (val.contains(value)) {
                    sameValues.add(val);
                }
                if (!sameValues.isEmpty()) {
                    String[] storeValues = new String[sameValues.size()];
                    for (int i = 0; i < sameValues.size(); i++) {
                        storeValues[i] = sameValues.get(i);
                    }
                    Modification mod = new Modification(ModificationType.ADD, attName, storeValues);
                    modifications.add(mod);
                }
            }
        }
        if (modifications.isEmpty()) return null;
        return modifications;
    }

    public void resolveCompareResult(COMPARE_RESULT childResult) {
        if (childResult == null) {
            if (is_entryEqual()) {
                set_compare_result(COMPARE_RESULT.ENTRY_EQUAL);
            } else {
                if (get_entry_type().equals(ENTRY_TYPE.SOURCE)) {
                    set_compare_result(COMPARE_RESULT.ONLY_IN_SOURCE);
                } else if (get_entry_type().equals(ENTRY_TYPE.TARGET)) {
                    set_compare_result(COMPARE_RESULT.ONLY_IN_TARGET);
                } else {
                    set_compare_result(COMPARE_RESULT.ENTRY_NOT_EQUAL);
                }
            }
            return;
        }
        if (_compare_result == null) {
            if (is_entryEqual()) {
                if (childResult.equals(COMPARE_RESULT.ENTRY_EQUAL)) {
                    set_compare_result(COMPARE_RESULT.ENTRY_EQUAL);
                } else {
                    set_compare_result(COMPARE_RESULT.ENTRY_EQUAL_BUT_CHILDREN_NOT);
                }
            } else {
                if (get_entry_type().equals(ENTRY_TYPE.SOURCE)) {
                    set_compare_result(COMPARE_RESULT.ONLY_IN_SOURCE);
                } else if (get_entry_type().equals(ENTRY_TYPE.TARGET)) {
                    set_compare_result(COMPARE_RESULT.ONLY_IN_TARGET);
                } else {
                    set_compare_result(COMPARE_RESULT.ENTRY_NOT_EQUAL);
                }
            }
        } else {
            if (_compare_result.equals(COMPARE_RESULT.ENTRY_EQUAL)) {
                if (!childResult.equals(COMPARE_RESULT.ENTRY_EQUAL)) {
                    set_compare_result(COMPARE_RESULT.ENTRY_EQUAL_BUT_CHILDREN_NOT);
                }
            }
        }
    }

    public void set_ModificationsToTarget(List<Modification> modifications) {
        this._modificationsToTarget = modifications;
        List<Modification> modToSource = new ArrayList<>();
        if (_modificationsToTarget != null && _modificationsToTarget.size() > 0) {
            for (Modification m : _modificationsToTarget) {
                if (m.getModificationType().equals(ModificationType.ADD)) {
                    Modification mNew = new Modification(ModificationType.DELETE, m.getAttributeName(), m.getValues());
                    modToSource.add(mNew);
                } else if (m.getModificationType().equals(ModificationType.DELETE)) {
                    Modification mNew = new Modification(ModificationType.ADD, m.getAttributeName(), m.getValues());
                    modToSource.add(mNew);
                }
            }
        }
        _modificationsToSource = modToSource;
    }

    public List<Modification> get_ModificationsToTarget(String attributeName) {
        List<Modification> returnValue = new ArrayList<>();
        if (_modificationsToTarget != null && _modificationsToTarget.size() > 0) {
            for (Modification m : _modificationsToTarget) {
                if (m.getAttributeName().equalsIgnoreCase(attributeName)) returnValue.add(m);
            }
        }
        return returnValue;
    }

    public List<Modification> get_ModificationsToTarget(List<String> attributes) {
        List<Modification> returnValue = new ArrayList<>();
        HashSet<String> atts = attributes.stream().map(String::toLowerCase).collect(Collectors.toCollection(HashSet::new));

        if (_modificationsToTarget != null && _modificationsToTarget.size() > 0) {
            for (Modification m : _modificationsToTarget) {
                if (atts.contains(m.getAttributeName().toLowerCase())) returnValue.add(m);
            }
        }
        return returnValue;
    }

    public List<Modification> get_ModificationsToSource(List<String> attributes) {
        List<Modification> returnValue = new ArrayList<>();
        HashSet<String> atts = attributes.stream().map(String::toLowerCase).collect(Collectors.toCollection(HashSet::new));
        if (_modificationsToSource != null && _modificationsToSource.size() > 0) {
            for (Modification m : _modificationsToSource) {
                if (atts.contains(m.getAttributeName().toLowerCase())) returnValue.add(m);
            }
        }
        return returnValue;
    }

    public String getRDN() {
        if (_rdnDisplay != null) {
            return _rdnDisplay;
        }
        if (get_dn() != null && !get_dn().equalsIgnoreCase("")) {
            String[] split = get_dn().split(",");
            if (split == null || split.length == 0) {
                return "COULD NOT PARSE RDN 1 ";
            } else {
                String rdnWithAttribute = split[0];
                return rdnWithAttribute;
                //int index = rdnWithAttribute.indexOf("=",0);
                //return rdnWithAttribute.substring(index+1);
            }
        } else {
            return "COULD NOT PARSE RDN 2";
        }
    }

    /**
     * @return Array of differences list.get(0) = attribute name, list.get(1)=ATT_MOD_OPERATION, list.get(2) = old Value, list.get(3) = new value
     */
    public TreeSet<String> getDifferentAttributes() {
        TreeSet<String> diffs = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        if (_modificationsToTarget == null) return diffs;
        for (Modification mod : _modificationsToTarget) {
            String attName = mod.getAttributeName();
            diffs.add(attName);
        }
        return diffs;
    }
    public static boolean stringContains(String sourceValue, String targetValue, boolean exact, boolean matchCase) {
        String src = null;
        String tgt = null;
        if (sourceValue == null) src = "";
        else src = sourceValue;
        if (targetValue == null) tgt = "";
        else tgt = targetValue;
        if (!matchCase) {
            src = src.toLowerCase();
            tgt = tgt.toLowerCase();
        }
        if (!exact) {
            if (src.contains(tgt)) return true;
            else return false;
        } else {
            String pattern = "\\b" + tgt + "\\b";
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(src);
            return m.find();
        }
    }


    public String toString() {
        return getRDN();
    }

    public String print() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("DN=" + _dn + "\n");
        stringBuilder.append("RDN=" + getRDN() + "\n");
        stringBuilder.append("ENTRY TYPE" + _entry_type + "\n");
        stringBuilder.append("ENTRY EQUAL=" + _entryEqual + "\n");
        stringBuilder.append("CHILDREN NOT EQUAL=" + _childrenNotEqual + "\n");
        stringBuilder.append("COMPARE RESULT=" + _compare_result + "\n");

        return stringBuilder.toString();
    }
}
