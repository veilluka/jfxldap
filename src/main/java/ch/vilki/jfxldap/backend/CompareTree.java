package ch.vilki.jfxldap.backend;

import ch.vilki.jfxldap.gui.Icons;
import com.unboundid.asn1.ASN1OctetString;
import com.unboundid.ldap.sdk.*;
import com.unboundid.ldif.LDIFException;
import javafx.application.Platform;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;

public class CompareTree extends TreeView<CompResult> implements Cloneable {
    static Logger logger = LogManager.getLogger(CompareTree.class);
    private IProgress _progress = null;
    private static int _nrOfEntries;
    private static int _entriesDone;

    public Connection get_sourceConnection() {
        return _sourceConnection;
    }

    private Connection _sourceConnection;

    public Connection get_targetConnection() {
        return _targetConnection;
    }

    private Connection _targetConnection;
    SearchResultEntry _rootSourceEntry = null;
    SearchResultEntry _rootTargetEntry = null;

    private boolean _initialized = false;
    private Config _config = null;
    private boolean _ignoreMissingEntries = false;
    private boolean _ignoreOperationalAttributes = true;
    private boolean _showAllEntries = false;
    private boolean _ignoreWhiteSpaces = false;
    private Filter _sourceFilter = null;
    private Filter _targetFilter = null;
    private HashSet<String> _foundDifferentAttributes = new HashSet<>();
    private HashSet<String> _ignoreAttributes = new HashSet<String>();
    private HashSet<String> _compareOnlyAttributes = new HashSet<String>() {{}};
    private String _displayAttribute = "";
    private static HashMap<String, TreeItem<CompResult>> _allSourceObjectsFoundDN = new HashMap<>();
    private boolean _breakOperation = false;
    private boolean _subtree = true;
    private static boolean _rerunEvaluation = false;
    static Filter STANDARD_FILTER = null;


    public boolean is_initialized() {
        return _initialized;
    }
    public void set_initialized(boolean _initialized) {
        this._initialized = _initialized;
    }
    public void set_ignoreMissingEntries(boolean _ignoreMissingEntries) {
        this._ignoreMissingEntries = _ignoreMissingEntries;
    }
    public void set_ignoreOperationalAttributes(boolean _ignoreOperationalAttributes) {
        this._ignoreOperationalAttributes = _ignoreOperationalAttributes;
    }
    public HashSet<String> get_foundDifferentAttributes() {
        return _foundDifferentAttributes;
    }
    public void set_breakOperation(boolean _breakOperation) {
        this._breakOperation = _breakOperation;
    }

    public void resetTree() {
        setRoot(null);
        _allSourceObjectsFoundDN.clear();
        _breakOperation = false;
    }

    public void registerProgress(IProgress progress) {
        _progress = progress;
    }

    private void breakOperation() {
        System.out.println("BREAK");
        Platform.runLater(() -> setRoot(null));
        _breakOperation = false;
        _nrOfEntries = 0;
        _entriesDone = 0;
        if (_progress != null) _progress.signalTaskDone("searchTree", null, null);
    }

    public void setIgnoreAttributes(List<String> ignoreAttributes) {
        _ignoreAttributes.clear();
        _compareOnlyAttributes.clear();
        if (ignoreAttributes != null) {
            for (String s : ignoreAttributes) _ignoreAttributes.add(s.toLowerCase());
        }
    }

    public void set_CompareOnlyAttributes(List<String> compareAttributes) {
        _compareOnlyAttributes.clear();
        _ignoreAttributes.clear();
        if (compareAttributes != null) for (String s : compareAttributes) _compareOnlyAttributes.add(s.toLowerCase());
    }

    public void disableCompareFilter() {
        _compareOnlyAttributes.clear();
        _ignoreAttributes.clear();
    }

    public List<TreeItem<CompResult>> getAllChildren(TreeItem<CompResult> startNode, List<TreeItem<CompResult>> result) {
        if (result == null) result = new ArrayList<>();
        for (TreeItem<CompResult> child : startNode.getChildren()) {
            result.add(child);
            result = getAllChildren(child, result);
        }
        return result;
    }

    public void initCompare(String rootSourceDN,
                            String rootTargetDN,
                            String displayAttribute,
                            Connection source,
                            Connection target,
                            boolean showAllEntries,
                            boolean ignoreWhiteSpace,
                            Filter sourceFilter,
                            Config config,
                            List<String> attributes,
                            boolean ignore,
                            boolean includeOnly,
                            boolean subtree) throws LDAPException {
        STANDARD_FILTER = Filter.create("(objectclass=*)");
        _config = config;
        resetTree();
        logger.debug("Setting root to start compare->" + rootSourceDN);
        _sourceConnection = source;
        _targetConnection = target;
        _breakOperation = false;
        _subtree = subtree;
        _rootSourceEntry = _sourceConnection.getEntry(rootSourceDN);
        _rootTargetEntry = _targetConnection.getEntry(rootTargetDN);

        CompResult rootCompareResult = getCompareResult(_rootSourceEntry, displayAttribute);
        TreeItem<CompResult> rootItem = new TreeItem<>(rootCompareResult);
        setRoot(rootItem);
        if (sourceFilter == null) {
            _sourceFilter = STANDARD_FILTER;
            _targetFilter = STANDARD_FILTER;
        } else {
            _sourceFilter = sourceFilter;
            _targetFilter = sourceFilter;
        }
        _nrOfEntries = 0;
        _entriesDone = 0;
        _displayAttribute = displayAttribute;
        _showAllEntries = showAllEntries;
        _ignoreWhiteSpaces = ignoreWhiteSpace;
        _foundDifferentAttributes.clear();
        _ignoreAttributes.clear();
        _compareOnlyAttributes.clear();
        if (ignore && attributes != null && !attributes.isEmpty())
            _ignoreAttributes.addAll(attributes.stream().map(String::toLowerCase).collect(Collectors.toCollection(ArrayList::new)));
        if (_ignoreOperationalAttributes) {
            _ignoreAttributes.addAll(_sourceConnection.OperationalAttributes);
        }
        if (includeOnly && attributes != null && !attributes.isEmpty())
            _compareOnlyAttributes.addAll(attributes.stream().map(String::toLowerCase).collect(Collectors.toCollection(ArrayList::new)));
        setStyle("-fx-fill: #ff0000;-fx-font-weight:bold;");
    }


    public boolean runCompare() throws Exception {
        _breakOperation = false;
        if (getRoot() == null) {
            throw new Exception("Root object not set");
        }
        if (!_subtree) {
            return reEvaluateTree();
        }
        sendProgress(0, "Running LDAP Read Operation on source tree now");
        getAllChildrenFromSource(getRoot(), _displayAttribute);
        if (_breakOperation) {
            breakOperation();
            return false;
        }
        try {
            sendProgress(0, "Running LDAP Read Operation on target Tree now");
            readTargetTree(getRoot().getValue().get_dn(), _displayAttribute);
        } catch (Exception e) {
            logger.error("Exception during compare", e);
        }
        return reEvaluateTree();
    }

    public boolean reEvaluateTree() {
        if (_breakOperation) {
            breakOperation();
            return false;
        }
        sendProgress(0, "Evaluate node status ");
        evaluateChildrenStatus(getRoot());
        sendProgress(0, "Setting compare result ");
        setCompareResult(getRoot());
        if (!_showAllEntries) {
            sendProgress(0, "Removing all entries which are same from the result");
            _rerunEvaluation = false;
            for (int i = 0; i < 100; i++) {
                showOnlyDifferentEntries(getRoot());
                refresh();
                if (_rerunEvaluation) {
                    _rerunEvaluation = false;
                } else break;
            }
        }
        if (_breakOperation) {
            breakOperation();
            return false;
        }
        
        CompResult.COMPARE_RESULT compResult = getRoot().getValue().get_compare_result();
        Platform.runLater(() -> setEntryIcon(getRoot(), compResult));
        sendProgress(0, "Building compare tree now ");
        Platform.runLater(() -> buildCompareTreeView(getRoot()));

        if (_breakOperation) {
            breakOperation();
            return false;
        }

        if (_progress != null) {
            logger.debug("Signaling progress done now for createMergeTred");
            _progress.signalTaskDone("compare", null, null);
        }
        return true;
    }

    private void getAllChildrenFromSource(TreeItem<CompResult> parent, String displayAttribute) {
        if (parent == null) return;
        try {
            UnboundidLdapSearch reader = new UnboundidLdapSearch(_config, _sourceConnection, parent.getValue().get_dn(), _sourceFilter.toString(), null);
            if(_sourceConnection.is_fileMode()) reader.fileSearchWithDummies = true;
            reader.run();
            _nrOfEntries = reader.get_children().size();
            Comparator<Entry> comparator = new Comparator<Entry>() {
                @Override
                public int compare(Entry o1, Entry o2) {
                    return o1.getDN().toLowerCase().compareTo(o2.getDN().toLowerCase());
                }
            };
            reader.get_children().sort(comparator);
            for (Entry entry : reader.get_children()) {
                CompResult unit = getCompareResult(new SearchResultEntry(entry), displayAttribute);
                TreeItem<CompResult> treeNode = new TreeItem(unit);
                _allSourceObjectsFoundDN.put(entry.getDN().toLowerCase(), treeNode);
                parent.getChildren().add(treeNode);
                if (_progress != null && _entriesDone % 5 == 0)
                    _progress.setProgress(calculateProgress(), treeNode.getValue().get_dn());
                if (_breakOperation) return;
                getAllChildrenFromSource(treeNode, displayAttribute);
            }

        } catch (Exception e) {
            logger.error("Exception in getAllChildrenFromSource", e);
        }
    }

    public void reEvaluateCompareResult(TreeItem<CompResult> treeItem, String displayAttribute) throws LDAPException {
        CompResult compareResult = treeItem.getValue();
        SearchResultEntry entry = _sourceConnection.getEntry(compareResult.get_dn());
        CompResult newResult = getCompareResult(entry, displayAttribute);
        treeItem.setValue(newResult);
        reEvaluateTree();
    }

    public void reEvaluateCompareResultSubtree(TreeItem<CompResult> treeItem, String displayAttribute) throws LDAPException {
        if (treeItem == null || !treeItem.getValue().get_entry_type().equals(CompResult.ENTRY_TYPE.BOTH)) return;
        CompResult compareResult = treeItem.getValue();
        SearchResultEntry entry = _sourceConnection.getEntry(compareResult.get_dn());
        CompResult newResult = getCompareResult(entry, displayAttribute);
        treeItem.setValue(newResult);
        for (TreeItem<CompResult> child : treeItem.getChildren()) {
            reEvaluateCompareResultSubtree(child, displayAttribute);
        }
    }

    private CompResult getCompareResult(SearchResultEntry entry, String displayAttribute) {
        CompResult cr = new CompResult(entry);
        if (displayAttribute != null && !displayAttribute.equalsIgnoreCase("")) {
            Attribute a = entry.getAttribute(displayAttribute);
            if (a != null) cr.set_rdnDisplay(a.getValue());
        }
        SearchResultEntry targetEntry = null;
        String targetDN = null;
        if (_rootTargetEntry != null && !_rootSourceEntry.getDN().equalsIgnoreCase(_rootTargetEntry.getDN())) {
            targetDN = entry.getDN().replace(_rootSourceEntry.getDN(), _rootTargetEntry.getDN());
        } else {
            targetDN = entry.getDN();
        }
        try {
            targetEntry = _targetConnection.getEntry(targetDN);
        } catch (Exception e) {
        }
        if (targetEntry != null) {
            cr.set_targetEntry(targetEntry);
            cr.set_entry_type(CompResult.ENTRY_TYPE.BOTH);
            int e1 = entry.hashCode();
            int e2 = targetEntry.hashCode();
            if (e1 == e2) {
                cr.set_entryEqual(true);
            } else {
                Set<String> attributes = new HashSet();
                entry.getAttributes().stream().forEach(x -> attributes.add(x.getName()));
                targetEntry.getAttributes().forEach(x -> attributes.add(x.getName()));
                List<Modification> foundModificationsToTarget = Entry.diff(targetEntry, entry, true, attributes.toArray(new String[attributes.size()]));
                List<Modification> modificationsToTarget = new ArrayList<>();
                for (Modification mod : foundModificationsToTarget) {
                    String attributeName = mod.getAttributeName().toLowerCase();
                    if (!_compareOnlyAttributes.isEmpty()) {
                        if (_compareOnlyAttributes.contains(attributeName)) {
                            modificationsToTarget.add(mod);
                            _foundDifferentAttributes.add(attributeName);
                        }
                    } else if (!_ignoreAttributes.isEmpty()) {
                        if (!_ignoreAttributes.contains(attributeName)) {
                            modificationsToTarget.add(mod);
                            _foundDifferentAttributes.add(attributeName);
                        }
                    } else {
                        modificationsToTarget.add(mod);
                        _foundDifferentAttributes.add(attributeName);
                    }
                }
                Iterator<Modification> iterator = modificationsToTarget.iterator();
                while (iterator.hasNext()) {
                    Modification m = iterator.next();
                    String attName = m.getAttributeName();
                    Attribute sourceAttribute = entry.getAttribute(attName);
                    Attribute targetAttribute = targetEntry.getAttribute(attName);
                    StringBuilder sourceValue = new StringBuilder();
                    StringBuilder targetValue = new StringBuilder();
                    if (sourceAttribute != null && sourceAttribute.getRawValues() != null &&
                            targetAttribute != null && targetAttribute.getRawValues() != null) {
                        for (ASN1OctetString s : sourceAttribute.getRawValues()) {
                            if (_ignoreWhiteSpaces) sourceValue.append(s.stringValue().replaceAll("\\s+", ""));
                            else sourceValue.append(s.stringValue());
                        }
                        for (ASN1OctetString s : targetAttribute.getRawValues()) {
                            if (_ignoreWhiteSpaces) targetValue.append(s.stringValue().replaceAll("\\s+", ""));
                            else targetValue.append(s.stringValue());
                        }
                        if (sourceValue.toString().equalsIgnoreCase(targetValue.toString())) {
                            iterator.remove();
                        }
                    }
                }
                if (modificationsToTarget.isEmpty()) {
                    cr.set_entryEqual(true);
                } else {
                    cr.set_entryEqual(false);
                    cr.set_ModificationsToTarget(modificationsToTarget);
                }
            }
        } else {
            if (!_ignoreMissingEntries) cr.set_entry_type(CompResult.ENTRY_TYPE.SOURCE);
            else {
                cr.set_entry_type(CompResult.ENTRY_TYPE.SOURCE);
                cr.set_entryEqual(true);
            }
        }
        return cr;
    }


    private void readTargetTree(String scope, String displayAttribute) throws Exception {
        try {
            if (_rootTargetEntry == null) return;
            List<SearchResultEntry> targetResult = null; // Operations.search(_filter, scope, SearchScope.SUB, null,_targetLdapConnection);
            String searchScope = null;
            if (!_rootSourceEntry.getDN().equalsIgnoreCase(_rootTargetEntry.getDN())) {
                searchScope = scope.replace(scope, _rootTargetEntry.getDN());
            } else {
                searchScope = scope;
            }
            if (_targetFilter.equals(STANDARD_FILTER))
                targetResult = Operations.search(_targetFilter, searchScope, SearchScope.ONE, null, _targetConnection);
            else
                targetResult = Operations.search(_targetFilter, searchScope, SearchScope.SUB, null, _targetConnection);
            if (targetResult == null || targetResult.isEmpty()) return;
            _nrOfEntries += targetResult.size();
            for (SearchResultEntry entry : targetResult) {
                CompResult cr = new CompResult(null);
                cr.set_targetEntry(entry);
                if (displayAttribute != null && !displayAttribute.equalsIgnoreCase("")) {
                    Attribute a = entry.getAttribute(displayAttribute);
                    if (a != null) cr.set_rdnDisplay(a.getValue());
                }
                TreeItem<CompResult> treeNode = new TreeItem<>(cr);
                String scannedEntry = null;
                if (!_rootSourceEntry.getDN().equalsIgnoreCase(_rootTargetEntry.getDN())) {
                    scannedEntry = entry.getDN().replace(_rootTargetEntry.getDN(), _rootSourceEntry.getDN()).toLowerCase();
                } else {
                    scannedEntry = entry.getDN().toLowerCase();
                }
                TreeItem<CompResult> parentNode = null;
                if (!_rootSourceEntry.getDN().equalsIgnoreCase(_rootTargetEntry.getDN())) {
                    scannedEntry.replace(_rootTargetEntry.getDN(), _rootSourceEntry.getDN());
                }
                if (!_allSourceObjectsFoundDN.containsKey(scannedEntry)) {
                    treeNode.getValue().set_entry_type(CompResult.ENTRY_TYPE.TARGET);
                    getRoot().getChildren().add(treeNode);
                    _entriesDone++;
                    parentNode = treeNode;
                } else {
                    parentNode = _allSourceObjectsFoundDN.get(scannedEntry);
                }
                getAllChildrenFromTarget(parentNode, displayAttribute);
            }

        } catch (LDAPException e) {
            logger.error("Exception occured in read target Tree->", e);
        }
    }

    public List<TreeItem<CompResult>> getAllDifferencesWithChildren(TreeItem<CompResult> root, List<TreeItem<CompResult>> populate) {
        if (root == null) return populate;
        CompResult crr = root.getValue();
        if (!crr.is_entryEqual()) {
            populate.add(root);
        }
        for (TreeItem<CompResult> child : root.getChildren()) {
            getAllDifferencesWithChildren(child, populate);
        }
        return populate;
    }

    private void getAllChildrenFromTarget(TreeItem<CompResult> parent, String displayAttribute) throws IOException, LDIFException {

        if (parent == null) {
            _entriesDone++;
            return;
        }
        try {
            List<SearchResultEntry> result = Operations.search(_targetFilter, parent.getValue().get_dn(), SearchScope.ONE, null, _targetConnection);
            if (result == null || result.isEmpty()) return;
            _nrOfEntries += result.size();
            for (SearchResultEntry entry : result) {
                String scannedEntry = null;
                if (!_rootSourceEntry.getDN().equalsIgnoreCase(_rootTargetEntry.getDN())) {
                    scannedEntry = entry.getDN().replace(_rootTargetEntry.getDN(), _rootSourceEntry.getDN()).toLowerCase();
                } else {
                    scannedEntry = entry.getDN().toLowerCase();
                }
                if (!_allSourceObjectsFoundDN.containsKey(scannedEntry)) {
                    CompResult unit = new CompResult(null);
                    unit.set_targetEntry(entry);
                    if (displayAttribute != null && !displayAttribute.equalsIgnoreCase("")) {
                        Attribute a = entry.getAttribute(displayAttribute);
                        if (a != null) unit.set_rdnDisplay(a.getValue());
                    }
                    TreeItem<CompResult> treeNode = new TreeItem(unit);
                    treeNode.getValue().set_entry_type(CompResult.ENTRY_TYPE.TARGET);
                    parent.getChildren().add(treeNode);
                    if (_entriesDone % 5 == 0) {
                        _progress.setProgress(calculateProgress(), "TARGET DN->" + treeNode.getValue().get_dn());
                    }
                    if (_breakOperation) return;
                    getAllChildrenFromTarget(treeNode, displayAttribute);
                } else {
                    getAllChildrenFromTarget(_allSourceObjectsFoundDN.get(scannedEntry), displayAttribute);
                }
            }

        } catch (LDAPException e) {
            logger.error("Exception in getAllChildrenFromTarget", e);
        }
    }

    public void evaluateChildrenStatus(TreeItem<CompResult> entry) {
        if (_breakOperation) return;
        if (entry.getChildren().isEmpty()) {
            entry.getValue().set_childrenNotEqual(false);
            TreeItem<CompResult> runUp = entry.getParent();
            while (runUp != null && runUp.getValue() != null) {
                boolean childrenNotEqual = false;
                for (TreeItem<CompResult> child : runUp.getChildren()) {
                    if (!child.getValue().is_entryEqual()) childrenNotEqual = true;
                    if (child.getValue().get_childrenNotEqual() != null && child.getValue().get_childrenNotEqual())
                        childrenNotEqual = true;
                }
                runUp.getValue().set_childrenNotEqual(childrenNotEqual);
                runUp = runUp.getParent();
            }
        } else {
            for (TreeItem<CompResult> child : entry.getChildren()) {
                evaluateChildrenStatus(child);
            }
        }
    }

    public void setCompareResult(TreeItem<CompResult> entry) {
        if (_breakOperation) return;
        if (entry.getChildren().isEmpty()) {
            entry.getValue().resolveCompareResult(null);
            TreeItem<CompResult> runUp = entry.getParent();
            while (runUp != null && runUp.getValue() != null) {
                runUp.getValue().resolveCompareResult(entry.getValue().get_compare_result());
                entry = runUp;
                runUp = runUp.getParent();
            }
        } else {
            for (TreeItem<CompResult> child : entry.getChildren()) {
                setCompareResult(child);
            }
        }
    }

    private void showOnlyDifferentEntries(TreeItem<CompResult> entry) {

        Iterator<TreeItem<CompResult>> iter = entry.getChildren().iterator();
        while (iter.hasNext()) {
            TreeItem<CompResult> element = iter.next();
            if (element.getValue().get_compare_result().equals(CompResult.COMPARE_RESULT.ENTRY_EQUAL)) {
                iter.remove();
                if (entry.getChildren().isEmpty() && entry.getValue().get_compare_result().equals(CompResult.COMPARE_RESULT.ENTRY_EQUAL_BUT_CHILDREN_NOT)) {
                    entry.getValue().set_compare_result(CompResult.COMPARE_RESULT.ENTRY_EQUAL);
                    _rerunEvaluation = true;
                }
            } else {
                showOnlyDifferentEntries(element);
            }
        }
    }


    private void buildCompareTreeView(TreeItem<CompResult> entryTreeItem) {
        if (_breakOperation) return;
        if (entryTreeItem.getChildren().isEmpty()) {
            entryTreeItem.setExpanded(false);
            return;
        }
        for (TreeItem<CompResult> child : entryTreeItem.getChildren()) {
            CompResult.COMPARE_RESULT compareResult = child.getValue().get_compare_result();
            setEntryIcon(child, compareResult);
            buildCompareTreeView(child);
        }
    }

    private void setEntryIcon(TreeItem<CompResult> child, CompResult.COMPARE_RESULT compareResult) {
        if(!child.getChildren().isEmpty()) child.setExpanded(true);
        switch (compareResult) {
            case ENTRY_EQUAL:
                child.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.ENTRY_EQUAL));
                break;
            case ENTRY_EQUAL_BUT_CHILDREN_NOT:
                child.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.SUBFOLDER_NOT_EQUAL));
                break;
            case ONLY_IN_TARGET:
                child.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.ARROW_LEFT));
                break;
            case ENTRY_NOT_EQUAL:
                child.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.ENTRY_NOT_EQUAL));
                break;
            case ONLY_IN_SOURCE:
                child.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.ARROW_RIGHT));
                break;
        }
    }

    public void overwriteEntryValue(CompResult compareResult, String attributeName, boolean target) throws Exception {

        List<Modification> modifications = null;
        if (target) modifications = compareResult.get_ModificationsToTarget(attributeName);
        else modifications = compareResult.get_ModificationsToSource();
        ModifyRequest modRequest = null;
        if (!target) modRequest = new ModifyRequest(compareResult.get_dn(), modifications);
        else modRequest = new ModifyRequest(compareResult.get_targetEntry().getDN(), modifications);
        if (target) _targetConnection.modify(modRequest);
        else _sourceConnection.modify(modRequest);
    }

    public void overwriteSubtreeValue(TreeItem<CompResult> treeItem, String attributeName, boolean target) throws Exception {

        if (treeItem == null) return;
        List<Modification> modifications = null;
        if (target) {
            modifications = treeItem.getValue().get_ModificationsToTarget(attributeName);
        } else {
            modifications = treeItem.getValue().get_ModificationsToSource();
        }
        if (modifications != null && !modifications.isEmpty()) {
            ModifyRequest modRequest = new ModifyRequest(treeItem.getValue().get_dn(), modifications);
            if (target) _targetConnection.modify(modRequest);
            else _sourceConnection.modify(modRequest);
        }
        for (TreeItem<CompResult> child : treeItem.getChildren()) {
            overwriteSubtreeValue(child, attributeName, target);
        }
    }

    private double calculateProgress() {
        return (double) _entriesDone / _nrOfEntries;
    }

    public void copyObjectToTarget(TreeItem<CompResult> source, boolean subtree) throws Exception {
        _nrOfEntries = 0;
        if (!_sourceConnection.isConnected()) _sourceConnection.connect();
        if (!_targetConnection.isConnected()) _targetConnection.connect();
        CompareTree.copyObjectToTarget(source, subtree, _sourceConnection, _targetConnection, _progress);

        _progress.signalTaskDone("copy", "Copy done", null);
    }

    private static void copyObjectToTarget(TreeItem<CompResult> source, boolean subtree,
                                           Connection sourceLdapConnection, Connection targetLdapConnection, IProgress progress) throws Exception {
        if (source == null) {
            return;
        }
        Entry sourceEntry = sourceLdapConnection.getEntry(source.getValue().get_dn());
        if (sourceEntry != null) {
            targetLdapConnection.add(sourceEntry);
            _nrOfEntries++;
            double pr = (double) (_nrOfEntries % 100) / 100.0;
            progress.setProgress(pr, source.getValue().get_dn() + "   ");
            source.getValue().set_entry_type(CompResult.ENTRY_TYPE.BOTH);
            source.getValue().set_compare_result(CompResult.COMPARE_RESULT.ENTRY_EQUAL);
        }
        if (subtree) {
            for (TreeItem<CompResult> child : source.getChildren()) {
                copyObjectToTarget(child, subtree, sourceLdapConnection, targetLdapConnection, progress);
            }
        }
    }

    public void copyObjectToSource(String targetDN, boolean subtree) throws Exception {
        Entry targetEntry = _targetConnection.getEntry(targetDN);
        if (targetEntry != null) {
            _sourceConnection.add(targetEntry);
        }
        if (subtree) {
            Filter f = Filter.create("(objectClass=*)");
            List<SearchResultEntry> result = Operations.search(f, targetDN, SearchScope.ONE, null, _targetConnection);
            if (result != null) {
                for (SearchResultEntry e : result) {
                    copyObjectToSource(e.getDN(), subtree);
                }
            }
        }
    }

    public void storeDifferencesInFile(String sourceFileName, String targetFileName,
                                       List<TreeItem<CompResult>> differentNodes) throws Exception {
        FileWriter fwSource = new FileWriter(sourceFileName);
        BufferedWriter bwSource = new BufferedWriter(fwSource);
        FileWriter fwTarget = new FileWriter(targetFileName);
        BufferedWriter bwTarget = new BufferedWriter(fwTarget);
        for (TreeItem<CompResult> n : differentNodes) {
            CompResult cr = n.getValue();

            Entry sourceEntry = _sourceConnection.getEntry(cr.get_dn());
            Entry targetEntry = _targetConnection.getEntry(cr.get_dn());
            List<String> sortedAttributesSourceNames = sourceEntry.getAttributes().stream().map(x -> x.getName()).sorted().collect(Collectors.toList());
            List<Attribute> sortedAttributesSource = new ArrayList<>();
            for (String s : sortedAttributesSourceNames) {
                sortedAttributesSource.add(sourceEntry.getAttribute(s));
            }
            Entry sortedSourceEntry = new Entry(sourceEntry.getDN(), sortedAttributesSource);

            String[] sOut = sortedSourceEntry.toLDIF();
            for (String s : sOut) {

                if (s.contains("changetype")) continue;
                if (s.contains("::")) {
                    int f = s.indexOf(":");

                    CharSequence value = s.subSequence(f + 3, s.length());
                    CharSequence attrName = s.subSequence(0, f);

                    byte[] dec = com.unboundid.util.Base64.decode(value.toString());
                    String decoded = new String(dec, Charset.forName("UTF-8"));
                    String output = attrName + ":" + decoded;
                    bwSource.append(output);
                    bwSource.append("\r\n");

                } else {
                    bwSource.append(s);
                    bwSource.append("\r\n");
                }

            }
            bwSource.append("\r\n");
            if (targetEntry != null) {
                List<String> sortedAttributesTargetNames = targetEntry.getAttributes().stream().map(x -> x.getName()).sorted().collect(Collectors.toList());
                List<Attribute> sortedAttributesTarget = new ArrayList<>();
                for (String s : sortedAttributesTargetNames) {
                    sortedAttributesTarget.add(targetEntry.getAttribute(s));
                }
                Entry sortedTargetEntry = new Entry(targetEntry.getDN(), sortedAttributesTarget);

                String[] tOut = sortedTargetEntry.toLDIF();
                for (String s : tOut) {

                    if (s.contains("changetype")) continue;
                    if (s.contains("::")) {
                        int f = s.indexOf(":");

                        CharSequence value = s.subSequence(f + 3, s.length());
                        CharSequence attrName = s.subSequence(0, f);


                        byte[] dec = com.unboundid.util.Base64.decode(value.toString());
                        String decoded = new String(dec, Charset.forName("UTF-8"));
                        String output = attrName + ":" + decoded;
                        bwTarget.append(output);
                        bwTarget.append("\r\n");
                    } else {
                        bwTarget.append(s);
                        bwTarget.append("\r\n");
                    }
                }
                bwTarget.append("\r\n");
            }
        }
        if (bwSource != null) bwSource.close();
        if (fwSource != null) fwSource.close();
        if (bwTarget != null) bwTarget.close();
        if (fwTarget != null) fwTarget.close();
    }

    @Override
    public CompareTree clone() {
        CompareTree compareTree = new CompareTree();
        compareTree._sourceConnection = _sourceConnection;
        compareTree._targetConnection = _targetConnection;
        compareTree._config = _config;
        compareTree._showAllEntries = _showAllEntries;
        return compareTree;

    }

    private void sendProgress(double progress, String description) {
        if (_progress != null) _progress.setProgress(progress, description);
    }

    public void printTree()
    {
       printTree(getRoot(),0);
    }

    private void printTree(TreeItem<CompResult> compareResultTreeItem, Integer depth)
    {
        StringBuilder space = new StringBuilder();
        for(int i=0; i< depth; i++) space.append(" ");
        System.out.println(space + compareResultTreeItem.getValue().getRDN());
        for(TreeItem<CompResult> child: compareResultTreeItem.getChildren())
        {
            printTree(child,depth++);
        }
    }

    public TreeItem<CompResult> searchTreeItem(TreeItem<CompResult> item, String dn)
    {
        if(item.getValue().get_dn().equalsIgnoreCase(dn)) return item; // hit!
        // continue on the children:
        TreeItem<CompResult> result = null;
        for(TreeItem<CompResult> child : item.getChildren()){
            result = searchTreeItem(child, dn);
            if(result != null) return result; // hit!
        }
        //no hit:
        return null;
    }

    public List<TreeItem<CompResult>> getAllSubEntries(TreeItem<CompResult> item, List<TreeItem<CompResult>> result)
    {
        if(result == null) result = new ArrayList<>();
        for(TreeItem<CompResult> child: item.getChildren())
        {
            result.add(child);
            getAllSubEntries(child,result);
        }
        return result;
    }





}
