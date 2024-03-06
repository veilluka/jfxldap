package ch.vilki.jfxldap.backend;

import com.unboundid.ldap.sdk.Modification;
import com.unboundid.ldap.sdk.ModificationType;
import javafx.scene.control.TreeItem;

import java.util.*;


public class AttributeDifference {

    public static Comparator<AttributeDifference> attributeNameComparator = new Comparator<AttributeDifference>() {
        @Override
        public int compare(AttributeDifference o1, AttributeDifference o2) {
            return o1.getAttributeName().toLowerCase().compareTo(o2.getAttributeName().toLowerCase());
        }
    };

    private String attributeName;
    private String _dn;
    private String _sourceEntryValue;
    private String _targetEntryValue;

    public String getAttributeName() {
        return attributeName;
    }

    public void setAttributeName(String attributeName) {
        this.attributeName = attributeName;
    }

    public String get_dn() {
        return _dn;
    }

    public void set_dn(String _dn) {
        this._dn = _dn;
    }

    public String get_sourceEntryValue() {
        return _sourceEntryValue;
    }

    public void set_sourceEntryValue(String _sourceEntryValue) {
        this._sourceEntryValue = _sourceEntryValue;
    }

    public String get_targetEntryValue() {
        return _targetEntryValue;
    }

    public void set_targetEntryValue(String _targetEntryValue) {
        this._targetEntryValue = _targetEntryValue;
    }

    public List<AttributeDifference> getAttributeDifferences(String attributeName, TreeItem<CompResult> treeItem) {
        List<AttributeDifference> diffs = new ArrayList<AttributeDifference>();
        List<Modification> modifications = treeItem.getValue().get_ModificationsToTarget();
        if (modifications == null) return diffs;
        ArrayList<String> sourceValues = new ArrayList<>();
        ArrayList<String> targetValues = new ArrayList<>();
        for (Modification m : modifications) {
            if (!m.getAttributeName().equalsIgnoreCase(attributeName)) continue;
            if (m.getModificationType().equals(ModificationType.ADD)) {
                for (String val : m.getAttribute().getValues()) {
                    sourceValues.add(val);
                }
            } else if (m.getModificationType().equals(ModificationType.DELETE)) {
                for (String val : m.getAttribute().getValues()) {
                    targetValues.add(val);
                }
            }
        }
        int maxElements = 0;
        if (sourceValues.size() > targetValues.size()) {
            maxElements = sourceValues.size();
            for (int i = 0; i < maxElements; i++) {
                AttributeDifference attributeDifference = new AttributeDifference();
                attributeDifference._sourceEntryValue = sourceValues.get(i);
                if (targetValues.size() > i) attributeDifference._targetEntryValue = targetValues.get(i);
                attributeDifference.setAttributeName(attributeName);
                diffs.add(attributeDifference);
            }
        } else if (sourceValues.size() < targetValues.size()) {
            maxElements = targetValues.size();
            for (int i = 0; i < maxElements; i++) {
                AttributeDifference attributeDifference = new AttributeDifference();
                attributeDifference._targetEntryValue = targetValues.get(i);
                if (sourceValues.size() > i) attributeDifference._sourceEntryValue = sourceValues.get(i);
                attributeDifference.setAttributeName(attributeName);
                diffs.add(attributeDifference);
            }
        } else {
            for (int i = 0; i < sourceValues.size(); i++) {
                AttributeDifference attributeDifference = new AttributeDifference();
                attributeDifference._targetEntryValue = targetValues.get(i);
                attributeDifference._sourceEntryValue = sourceValues.get(i);
                attributeDifference.setAttributeName(attributeName);
                diffs.add(attributeDifference);
            }
        }
        return diffs;
    }

    public static Set<Integer> compareStringCharByChar(String source, String target) {
        Set<Integer> result = new HashSet();
        if (target == null || target.isEmpty()) {
            if (source == null) return result;
            for (int i = 0; i < source.length(); i++) result.add(i);
            return result;
        }
        if (source == null || source.isEmpty()) return result;

        char[] src = source.toCharArray();
        char[] tgt = target.toCharArray();

        for (int i = 0; i < source.length(); i++) {
            if (tgt.length <= i) result.add(i);
            else {
                if (src[i] != tgt[i]) result.add(i);
            }

        }
        return result;
    }
}
