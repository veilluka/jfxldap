package ch.vilki.jfxldap.backend;

import com.google.common.eventbus.EventBus;
import com.unboundid.ldap.sdk.*;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ch.vilki.jfxldap.gui.*;


import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public class SearchTree extends TreeView<SearchEntry> {
    static Logger logger = LogManager.getLogger(SearchTree.class);

    private List<String> _searchAttributes = new ArrayList<>();
    private boolean _ignoreAttributes = false;
    private boolean _searchOnly = false;
    private Filter _filter = null;
    private boolean _searchNotContains;
    private boolean _exaxtMatch;
    private boolean _regex;
    private boolean _flatSearch = false;
    private boolean _ignoreCase = false;
    private static int _foundEntries = 0;
    private static int _doneEntries = 0;
    public EventBus _searchTreeEventBus = new EventBus();
    private Config _config;
    private Connection _currentConnection = null;
    private String _searchValue = null;
    private Map<TreeItem<SearchEntry>, List<ModifyRequest>> _replaceStringModifications = null;
    Filter FILTER_NOT_SET = null;
    private boolean _breakOperation = false;

    public Map<TreeItem<SearchEntry>, List<ModifyRequest>> get_replaceStringModifications() {
        return _replaceStringModifications;
    }
    public void set_config(Config _config) {
        this._config = _config;
    }
    public String get_searchValue() {
        return _searchValue;
    }
    public Connection get_currentConnection() {
        return _currentConnection;
    }
    public SearchTree() {
        this.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        HBox.setMargin(this, new Insets(2, 2, 2, 2));
    }
    private TreeMap<String, TreeItem<SearchEntry>> _addedFlatSearch = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    public void set_breakOperation(boolean _breakOperation) {
        this._breakOperation = _breakOperation;
    }

    public void runSearch(TreeView<CustomEntry> treeView,
                          Connection connection,
                          String baseDN,
                          String searchValue,
                          boolean ignoreC,
                          boolean exactMatch,
                          boolean notContains,
                          boolean regex,
                          String displayAttribute,
                          boolean deadLink,
                          Filter filter,
                          List<String> searchAttributes,
                          boolean includeOnlyAttributes,
                          boolean ignoreAttributes,
                          IProgress progress) {

        try {
            _ignoreCase = ignoreC;
            _currentConnection = connection;
            _breakOperation = false;
            FILTER_NOT_SET = Filter.create("(objectClass=*)");
            _exaxtMatch = exactMatch;
            _searchNotContains = notContains;
            _regex = regex;
            _foundEntries = 0;
            _doneEntries = 0;
            _addedFlatSearch.clear();
            _searchAttributes.clear();
            if (searchAttributes != null) for (String s : searchAttributes) _searchAttributes.add(s.toLowerCase());
            _ignoreAttributes = ignoreAttributes;
            _searchOnly = includeOnlyAttributes;
            _flatSearch = false;
            _searchValue = searchValue;
            if (filter == null) _filter = FILTER_NOT_SET;
            else {
                _filter = filter;
                _flatSearch = true;
            }
            String[] attributes = null;
            if (!_searchAttributes.isEmpty() && !_ignoreAttributes) {
                attributes = new String[_searchAttributes.size()];
                for (int i = 0; i < _searchAttributes.size(); i++) attributes[i] = _searchAttributes.get(i);
            }
            if (connection.is_fileMode()) {
                searchFile(treeView, baseDN, searchValue, _ignoreCase, displayAttribute, progress);
                return;
            }
            if (!connection.isConnected()) connection.connect();
            SearchResultEntry rootEntry = connection.getEntry(baseDN);
            SearchEntry searchEntry = null;
            String[] split = baseDN.split(",");
            String rootDN = null;
            if (split != null && split.length > 0) rootDN = split[split.length - 1];
            else rootDN = baseDN;
            if (!deadLink) {
                searchEntry = new SearchEntry(rootEntry, searchValue, _ignoreCase, _searchAttributes, _ignoreAttributes, _regex, _searchNotContains, _exaxtMatch);
                searchEntry.setDisplayAttribute(displayAttribute);
            } else {
                searchEntry = new SearchEntry(rootEntry, _searchAttributes, _ignoreAttributes, rootDN, connection);
                searchEntry.setDisplayAttribute(displayAttribute);

            }
            TreeItem<SearchEntry> root = new TreeItem<>(searchEntry);
            Platform.runLater(() -> setRoot(root));
            _addedFlatSearch.put(root.getValue().getDn(), root);
            if (!_flatSearch) {
                runSearchRecursive(root, connection, searchValue, attributes, _ignoreCase, displayAttribute, deadLink, baseDN, progress);
            } else {
                root.getValue().setValueFound(false);
                runSearchFlat(root, connection, searchValue, _ignoreCase, displayAttribute, deadLink, baseDN, progress);
            }
            if (_breakOperation) {
                breakOperation();
                progress.signalTaskDone("searchTree", null, new Exception("Operation canceled"));
                return;
            }
            evaluateTree(root);
            if (_breakOperation) {
                breakOperation();
                progress.signalTaskDone("searchTree", null, new Exception("Operation canceled"));
                return;
            }
            showOnlyFoundEntries(root);
            if (_breakOperation) {
                breakOperation();
                progress.signalTaskDone("searchTree", null, new Exception("Operation canceled"));
                return;
            }
            setIcon(root);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("Search in->");
            stringBuilder.append(baseDN);
            stringBuilder.append(" with Filter->");
            stringBuilder.append(_filter.toString());
            if (searchValue != null) {
                stringBuilder.append(" and search text->");
                stringBuilder.append(searchValue);
            }
            progress.signalTaskDone("searchTree", stringBuilder.toString(), null);
            _breakOperation = false;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            progress.signalTaskDone("searchTree", null, e);
        }
    }

    private void searchFile(TreeView<CustomEntry> sourceTree,
                            String baseDN,
                            String searchValue,
                            boolean ignoreCase,
                            String displayAttribute,
                            IProgress progress) {
        TreeItem<CustomEntry> searchRootNode = recursiveSearch(baseDN, sourceTree.getRoot());
        if (searchRootNode == null) {
            GuiHelper.ERROR("Search error", "Could not resolve starting node");
            logger.error("did not find start node");
            return;
        }
        if (searchRootNode.getValue().getEntry() == null) {
            List<Attribute> attributes = new ArrayList<>();
            Entry dummyEntry = new Entry(searchRootNode.getValue().getDn(), attributes);
            searchRootNode.getValue().setEntry(dummyEntry);

        }
        SearchEntry searchEntry = new SearchEntry(searchRootNode.getValue().getEntry(),
                searchValue,
                ignoreCase,
                _searchAttributes,
                _ignoreAttributes,
                _regex,
                _searchNotContains,
                _exaxtMatch);
        searchEntry.setDisplayAttribute(displayAttribute);
        TreeItem<SearchEntry> root = new TreeItem<>(searchEntry);
        Platform.runLater(() -> setRoot(root));
        recursiveFileSearch(searchRootNode, root, searchValue, ignoreCase, displayAttribute, progress);
        if (_breakOperation) {
            breakOperation();
            progress.signalTaskDone("searchTree", null, null);
            return;
        }
        evaluateTree(getRoot());
        if (_breakOperation) {
            breakOperation();
            progress.signalTaskDone("searchTree", null, null);
            return;
        }
        showOnlyFoundEntries(getRoot());
        setIcon(getRoot());
        progress.signalTaskDone("searchTree", null, null);

        _breakOperation = false;
    }

    private void recursiveFileSearch(TreeItem<CustomEntry> sourceTreeItem,
                                     TreeItem<SearchEntry> targetTreeItem,
                                     String searchValue,
                                     boolean ignoreCase,
                                     String displayAttribute
            , IProgress progress) {
        try {
            for (TreeItem<CustomEntry> child : sourceTreeItem.getChildren()) {
                SearchEntry searchEntry = new SearchEntry(child.getValue().getEntry(),
                        searchValue,
                        ignoreCase,
                        _searchAttributes,
                        _ignoreAttributes,
                        _regex,
                        _searchNotContains,
                        _exaxtMatch);
                searchEntry.setDisplayAttribute(displayAttribute);
                if (!_filter.equals(FILTER_NOT_SET)) {
                    if (matchesFilter(searchEntry.getEntry())) {
                        searchEntry.setValueFound(true);
                    } else {
                        searchEntry.setValueFound(false);
                    }
                }

                TreeItem<SearchEntry> childItem = new TreeItem<>(searchEntry);
                targetTreeItem.getChildren().add(childItem);
                recursiveFileSearch(child, childItem, searchValue, ignoreCase, displayAttribute, progress);
                _doneEntries++;
                if (_doneEntries % 10 == 0) sendProgress(progress, sourceTreeItem.getValue().getDn());
            }
        } catch (Exception e) {
            logger.error("Exception occured in runSearchRecursive", e);
            return;
        }
    }

    boolean matchesFilter(Entry entry) {
        try {
            if (_filter.matchesEntry(entry)) {
                return true;
            }
        } catch (LDAPException e) {
            logger.error("Error filter matchin for->" + entry.getDN(), e);
        }
        return false;
    }

    private TreeItem<CustomEntry> recursiveSearch(String dn, TreeItem<CustomEntry> node) {

        if (node.getValue().getEntry() == null) logger.info("entry is null->" + node.getValue().getDn());
        if (node.getValue().getDn().equalsIgnoreCase(dn)) return node;
        ObservableList<TreeItem<CustomEntry>> children = node.getChildren();
        TreeItem<CustomEntry> ret = null;
        if (children != null && !children.isEmpty()) {
            for (TreeItem<CustomEntry> child : children) {
                ret = recursiveSearch(dn, child);
            }
        }
        return ret;
    }


    public void expandTree() {
        expandTreeView(getRoot());
    }

    private void expandTreeView(TreeItem<?> item) {
        if (item != null && !item.isLeaf()) {
            item.setExpanded(true);
            for (TreeItem<?> child : item.getChildren()) {
                expandTreeView(child);
            }
        }
    }

    private void breakOperation() {
        Platform.runLater(() -> setRoot(null));
        _breakOperation = false;
        _foundEntries = 0;
        _doneEntries = 0;
    }

    private void evaluateTree(TreeItem<SearchEntry> entry) {
        if (entry.getChildren().isEmpty()) {
            boolean found = false;
            found = entry.getValue().getValueFound();
            TreeItem<SearchEntry> runUp = entry.getParent();
            while (runUp != null) {
                if (found) {
                    runUp.getValue().setChildrenFound(found);
                } else {
                    found = runUp.getValue().getValueFound();
                }
                runUp = runUp.getParent();
            }
        } else {
            for (TreeItem<SearchEntry> child : entry.getChildren()) evaluateTree(child);
        }
    }

    private void showOnlyFoundEntries(TreeItem<SearchEntry> entry) {
        Iterator<TreeItem<SearchEntry>> iter = entry.getChildren().iterator();
        while (iter.hasNext()) {
            TreeItem<SearchEntry> element = iter.next();
            if (!element.getValue().getValueFound() && !element.getValue().getChildrenFound()) {
                iter.remove();
            } else {
                showOnlyFoundEntries(element);
                if (!element.getValue().getValueFound() && element.getChildren().isEmpty()) iter.remove();
            }
        }
    }

    public void replaceStringGetModifications(List<TreeItem<SearchEntry>> treeItems, String toBeReplaced, String replaceWith, IProgress progress) throws Exception {
        double done = 0;
        _replaceStringModifications = new HashMap<>();
        for (TreeItem<SearchEntry> treeItem : treeItems) {
            if (progress != null && done++ % 10 == 0) {
                progress.setProgress(done / (double) treeItems.size(), treeItem.getValue().getRdn());
            }
            if (treeItem.getValue().ValueFound.get()) {
                ArrayList<Modification> modifications = new ArrayList<Modification>();
                for (String attName : treeItem.getValue().FoundInAttributes) {
                    Collection<Attribute> attributes = treeItem.getValue().getEntry().getAttributes();
                    for (Attribute attribute : attributes) {
                        if (attribute.getName().equalsIgnoreCase(attName)) {
                            String[] values = attribute.getValues();
                            String newValues[] = new String[values.length];
                            for (int i = 0; i < values.length; i++) {
                                newValues[i] = Helper.replace(values[i], toBeReplaced, replaceWith);
                            }
                            Modification modification = new Modification(ModificationType.REPLACE, attribute.getName(), newValues);
                            modifications.add(modification);
                        }
                    }
                    if (!modifications.isEmpty()) {
                        ModifyRequest modRequest = new ModifyRequest(treeItem.getValue().getDn(), modifications);
                        //_replaceStringModifications.get(treeItem).add(modRequest);

                        if (!_replaceStringModifications.containsKey(treeItem)) {
                            List<ModifyRequest> requests = new ArrayList<>();
                            requests.add(modRequest);
                            _replaceStringModifications.put(treeItem, requests);
                        } else {
                            _replaceStringModifications.get(treeItem).clear();
                            _replaceStringModifications.get(treeItem).add(modRequest);
                        }
                        /*_currentConnection.modify(modRequest);
                        SearchEntry searchEntry = new SearchEntry(_currentConnection.getEntry(treeItem.getValue().getDn())
                                ,_searchValue,_ignoreCase,_searchAttributes, _ignoreAttributes,_regex,_searchNotContains,_exaxtMatch);

                        treeItem.setValue(searchEntry);
                        */

                    }
                }
            }
        }
        /*
        evaluateTree(getRoot());
        showOnlyFoundEntries(getRoot());
        setIcon(getRoot());
        */
        if (progress != null) progress.signalTaskDone("replaceStringGetModifications", "Replacement done", null);

    }

    public void doReplace() throws Exception {
        for (TreeItem<SearchEntry> treeItem : _replaceStringModifications.keySet()) {
            List<ModifyRequest> modifyRequests = _replaceStringModifications.get(treeItem);
            for (ModifyRequest modifyRequest : modifyRequests) {
                _currentConnection.modify(modifyRequest);
                SearchEntry searchEntry = new SearchEntry(_currentConnection.getEntry(treeItem.getValue().getDn())
                        , _searchValue, _ignoreCase, _searchAttributes, _ignoreAttributes, _regex, _searchNotContains, _exaxtMatch);

                treeItem.setValue(searchEntry);
            }
        }
        evaluateTree(getRoot());
        showOnlyFoundEntries(getRoot());
        setIcon(getRoot());
    }


    public void replaceAllStringOccurencies(TreeItem<SearchEntry> entry, String original, String replacement, Connection ldapConnection, List<ModifyRequest> modifyRequests) throws Exception {
        if (entry.getValue().ValueFound.get()) {
            ArrayList<Modification> modifications = new ArrayList<Modification>();
            for (String attName : entry.getValue().FoundInAttributes) {
                Collection<Attribute> attributes = entry.getValue().getEntry().getAttributes();
                for (Attribute attribute : attributes) {
                    if (attribute.getName().equalsIgnoreCase(attName)) {
                        String[] values = attribute.getValues();
                        String newValues[] = new String[values.length];
                        for (int i = 0; i < values.length; i++) {
                            newValues[i] = values[i].replace(original, replacement);
                        }
                        Modification modification = new Modification(ModificationType.REPLACE, attribute.getName(), newValues);
                        modifications.add(modification);
                    }
                }
                if (!modifications.isEmpty()) {
                    ModifyRequest modRequest = new ModifyRequest(entry.getValue().getDn(), modifications);
                    if (modifyRequests != null) {
                        modifyRequests.add(modRequest);
                    } else {
                        ldapConnection.modify(modRequest);
                    }
                }
            }
        }
        if (!entry.getChildren().isEmpty()) {
            for (TreeItem<SearchEntry> item : entry.getChildren()) {
                replaceAllStringOccurencies(item, original, replacement, ldapConnection, modifyRequests);
            }
        }
    }


    private void setIcon(TreeItem<SearchEntry> entry) {
        if (entry == null) return;
        if (entry.getValue().getValueFound()) {
            Platform.runLater(() -> entry.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.ENTRY_NOT_EQUAL)));
        } else {
            if (entry.getValue().getChildrenFound()) {
                Platform.runLater(() -> entry.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.SUBFOLDER_NOT_EQUAL)));
            } else {
                Platform.runLater(() -> entry.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.ENTRY_EQUAL)));
            }
        }
        for (TreeItem<SearchEntry> child : entry.getChildren()) {
            if (child.getValue().getValueFound()) {
                Platform.runLater(() -> child.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.ENTRY_NOT_EQUAL)));
            } else {
                if (child.getValue().getChildrenFound()) {
                    Platform.runLater(() -> child.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.SUBFOLDER_NOT_EQUAL)));
                } else
                    Platform.runLater(() -> child.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.ENTRY_EQUAL)));
            }
            setIcon(child);
        }
    }

    private void runSearchRecursive(TreeItem<SearchEntry> parent,
                                    Connection connection,
                                    String searchValue,
                                    String[] attributes,
                                    boolean ignoreCase,
                                    String displayAttribute,
                                    boolean deadLink
            , String baseDN, IProgress progress) {
        if (_breakOperation) return;
        try {
            UnboundidLdapSearch reader = new UnboundidLdapSearch(_config, connection, parent.getValue().getDn(), _filter.toString(), progress);
            reader.run();
            List<Entry> foundEntries = reader.get_children();
            if (foundEntries == null || foundEntries.isEmpty()) {
                return;
            } else {
                _foundEntries += foundEntries.size();
                for (Entry entry : foundEntries) {
                    SearchEntry searchEntry = null;

                    if (!deadLink) {
                        searchEntry = new SearchEntry(entry, searchValue, ignoreCase, _searchAttributes, _ignoreAttributes, _regex, _searchNotContains, _exaxtMatch);
                        searchEntry.setDisplayAttribute(displayAttribute);
                    } else {
                        searchEntry = new SearchEntry(entry, _searchAttributes, _ignoreAttributes, baseDN, connection);
                        searchEntry.setDisplayAttribute(displayAttribute);
                    }
                    if (!_filter.equals(FILTER_NOT_SET)) {
                        if (matchesFilter(searchEntry.getEntry())) {
                            searchEntry.setValueFound(true);
                        } else {
                            searchEntry.setValueFound(false);
                        }
                    }
                    TreeItem<SearchEntry> child = new TreeItem<>(searchEntry);
                    parent.getChildren().add(child);
                    _doneEntries++;
                    if (_doneEntries % 50 == 0) sendProgress(progress, "Searching for value-> " + entry.getDN());
                    runSearchRecursive(child, connection, searchValue, attributes, ignoreCase, displayAttribute, deadLink, baseDN, progress);
                }
            }
        } catch (Exception e) {
            logger.error("Exception occured in runSearchRecursive", e);
            return;
        }

    }

    private void runSearchFlat(TreeItem<SearchEntry> parent, Connection connection,
                               String searchValue, boolean ignoreCase, String displayAttribute,
                               boolean deadLink, String baseDN, IProgress progress) {
        if (_breakOperation) return;
        String[] attributes = null;
        if (!_searchAttributes.isEmpty() && !_ignoreAttributes) {
            attributes = new String[_searchAttributes.size()];
            for (int i = 0; i < _searchAttributes.size(); i++) attributes[i] = _searchAttributes.get(i);
        }
        try {
            sendProgress(progress, "LDAP SEARCH COMMAND COMITTED, WAITING");
            List<Entry> found = null;
            if (connection.is_fileMode()) {
                List<SearchResultEntry> f = connection.fileSearch(_filter);
                if (f != null && !f.isEmpty()) {
                    found = new ArrayList<>();
                    for (SearchResultEntry searchResultEntry : f) {
                        found.add(searchResultEntry);
                    }
                }
            } else {
                UnboundidLdapSearch unboundidLdapSearch = new UnboundidLdapSearch(_config, _currentConnection, progress);
                unboundidLdapSearch.set_searchDN(baseDN);
                unboundidLdapSearch.set_filter(_filter);
                unboundidLdapSearch.set_searchScope(SearchScope.SUB);
                ExecutorService executor = Executors.newSingleThreadExecutor();
                Future<?> future = executor.submit(unboundidLdapSearch);
                try {
                    future.get();
                    found = unboundidLdapSearch.get_children();
                } catch (InterruptedException e) {
                    progress.signalTaskDone("search_tree", "Search failed ", e);
                    logger.error("error", e);
                } catch (ExecutionException e) {
                    progress.signalTaskDone("search_tree", "Search failed ", e);
                    logger.error("error", e);
                }
            }

            if (found == null || found.isEmpty()) return;
            else {
                List<Entry> sorted = found.stream().sorted(Helper.EntryComparator).collect(Collectors.toList());
                _foundEntries += found.size();
                for (Entry entry : sorted) {
                    SearchEntry searchEntry = null;
                    if (!deadLink) {
                        searchEntry = new SearchEntry(entry, searchValue, ignoreCase, _searchAttributes,
                                _ignoreAttributes, _regex, _searchNotContains, _exaxtMatch);
                    } else {
                        searchEntry = new SearchEntry(entry, _searchAttributes, _ignoreAttributes, baseDN, connection);
                    }
                    searchEntry.setDisplayAttribute(displayAttribute);

                    TreeItem<SearchEntry> child = new TreeItem<>(searchEntry);
                    TreeItem<SearchEntry> foundParent = getParent(entry, connection, searchValue,
                            ignoreCase, displayAttribute, deadLink, baseDN);
                    foundParent.getValue().setChildrenFound(true);
                    foundParent.getValue().setValueFound(false);
                    if (!_addedFlatSearch.containsKey(child.getValue().getDn())) {
                        _addedFlatSearch.put(child.getValue().getDn(), child);
                        foundParent.getChildren().add(child);
                    }
                    _doneEntries++;
                    if (_doneEntries % 10 == 0) sendProgress(progress, parent.getValue().getDn());
                }
            }
        } catch (Exception e) {
            Platform.runLater(() -> GuiHelper.EXCEPTION("Exception occured during search", e.getMessage(), e));
            logger.error("Exception occured in runSearch flat ", e);
            return;
        }
    }


    private TreeItem<SearchEntry> getParent(Entry entry, Connection connection,
                                            String searchValue,
                                            boolean ignoreCase,
                                            String displayAttribute, boolean deadLink, String baseDN) throws LDAPException {
        if (entry.getParentDN() == null) return null;
        String parentDN = entry.getParentDN().toString();
        TreeItem<SearchEntry> parent = _addedFlatSearch.get(parentDN);
        if (parent != null) {
            return parent;
        } else {
            SearchResultEntry parentEntry = connection.getEntry(parentDN);
            if(parentEntry == null) return null;
            SearchEntry parentSearchEntry = null;
            if (!deadLink) {
                parentSearchEntry = new SearchEntry(parentEntry, searchValue, ignoreCase, _searchAttributes, _ignoreAttributes, _regex, _searchNotContains, _exaxtMatch);
            } else {
                parentSearchEntry = new SearchEntry(parentEntry, _searchAttributes, _ignoreAttributes, baseDN, connection);
            }
            parentSearchEntry.setDisplayAttribute(displayAttribute);
            parentSearchEntry.setValueFound(false);
            parentSearchEntry.setChildrenFound(true);
            TreeItem<SearchEntry> parentCreated = new TreeItem<>(parentSearchEntry);
            TreeItem<SearchEntry> nextParent =
                    getParent(parentEntry, connection, searchValue, ignoreCase, displayAttribute, deadLink, baseDN);
            if (!_addedFlatSearch.containsKey(parentDN)) {
                if (nextParent != null) nextParent.getChildren().add(parentCreated);
                _addedFlatSearch.put(parentDN, parentCreated);
            }
            return parentCreated;
        }
    }


    private void sendProgress(IProgress progress, String item) {
        if (progress == null) return;
        if (_foundEntries == 0) {
            progress.setProgress((double) 0, item);
        }

        if (_doneEntries % 20 == 0) {
            double pr = (double) _doneEntries / (double) _foundEntries;
            progress.setProgress((double) ((double) _doneEntries / (double) _foundEntries), item);
        }
    }
}
