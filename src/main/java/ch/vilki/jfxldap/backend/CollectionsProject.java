package ch.vilki.jfxldap.backend;

import com.unboundid.ldap.sdk.*;
import com.unboundid.ldif.LDIFWriter;
import javafx.collections.ObservableList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class CollectionsProject {

    static Logger logger = LogManager.getLogger(CollectionsProject.class);

    public static String FIELD_DN = "dn";
    public static String FIELD_SUBTREE = "export_subtree";
    public static String FIELD_OVERWRITE_TARGET = "overwrite_target_entry";
    public static String FIELD_ATTRIBUTES = "attributes";
    public static String FIELD_ATTRIBUTES_ACTION = "attributes_action";
    public static String FIELD_LDAP_FILTER = "ldap_filter";
    public static String FIELD_DISPLAY_DN = "display_dn";
    public static String FIELD_EXPORT_ROOT = "export_root";

    public Map<String, CollectionEntry> get_collectionEntries() {
        return _collectionEntries;
    }
    Map<String, CollectionEntry> _collectionEntries;
    String _fileName;
    String _projectName;
    static int _exportedEntriesCount = 0;
    private boolean _breakOperation;
    private Connection _currentConnection = null;
    Config _config = null;
    private TreeSet<String> _ignoreAttributes = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    public void set_ignoreAttributes(TreeSet<String> _ignoreAttributes) {
        this._ignoreAttributes = _ignoreAttributes;
    }
    public void set_breakOperation(boolean _breakOperation) {
        this._breakOperation = _breakOperation;
    }
    public String get_projectName() {
        return _projectName;
    }
    public String get_fileName() {
        return _fileName;
    }
    public void set_fileName(String _fileName) {
        this._fileName = _fileName;
    }

    public CollectionsProject(Config config, String name) {
        _projectName = name;
        _config = config;
    }

    public CollectionsProject(Config config, String name, Connection connection) {
        _projectName = name;
        _currentConnection = connection;
        _config = config;
    }

    public void addCollectionEntry(CollectionEntry entry) throws Exception {
        if (_collectionEntries == null) _collectionEntries = new HashMap<>();
        if (entry == null) throw new Exception("Entry null pointer");
        _collectionEntries.put(entry.getDn(), entry);
    }

    public void addCollectionEntries(List<CollectionEntry> entries) throws Exception {
        for (CollectionEntry entry : entries) {
            addCollectionEntry(entry);
        }
    }

    public void setCollectionEntries(ObservableList<CollectionEntry> values) {
        if (_collectionEntries == null) _collectionEntries = new HashMap<>();
        _collectionEntries.clear();
        for (CollectionEntry c : values) {
            _collectionEntries.put(c.getDn(), c);
        }
    }

    public void readProject(String fileName) throws Exception {

        logger.info("Reading project->" + fileName);
        ExcelFileHandler fileHandler = new ExcelFileHandler();
        set_fileName(fileName);

        if (!fileHandler.readExcelFile(fileName)) {
            throw new Exception("Reading excel failed ");
        }
        for (int i = 0; i < fileHandler.get_parsedEntries().size(); i++) {
            ArrayList<HashMap<String, String>> entry = fileHandler.get_parsedEntries().get(i);
            for (HashMap<String, String> row : entry) {
                String dn = row.get(FIELD_DN);
                String sub = row.get(FIELD_SUBTREE);
                String del = row.get(FIELD_OVERWRITE_TARGET);
                String attributes = row.get(FIELD_ATTRIBUTES);
                String attributes_action = row.get(FIELD_ATTRIBUTES_ACTION);
                String ldapFilter = row.get(FIELD_LDAP_FILTER);
                String displayDN = row.get(FIELD_DISPLAY_DN);
                String exportRoot = row.get(FIELD_EXPORT_ROOT);
                if (dn == null) throw new Exception("Entry has no DN set->" + row);
                List<String> attributesList = new ArrayList<>();
                if (attributes != null) {
                    String[] atts = attributes.split(",");
                    for (String at : atts) attributesList.add(at);
                }
                boolean subtree = false;
                boolean deleteTarget = false;
                if (sub.equalsIgnoreCase("yes")) subtree = true;
                if (del.equalsIgnoreCase("yes")) deleteTarget = true;
                CollectionEntry collectionEntry =
                        new CollectionEntry(dn, subtree, deleteTarget, attributesList, attributes_action, ldapFilter, displayDN);
                addCollectionEntry(collectionEntry);

            }
        }
    }

    public void writeProject(String targetDirectory, boolean isFileName) throws Exception {

        String fileName = null;
        if (isFileName) fileName = targetDirectory;
        else fileName = targetDirectory + "\\" + _projectName + ".project.xlsx";

        ExcelFileHandler fileHandler = new ExcelFileHandler();
        List<List<String>> writeValues = new ArrayList<>();
        ArrayList<String> firstRow = new ArrayList<String>();
        firstRow.add(FIELD_DISPLAY_DN);
        firstRow.add(FIELD_DN);
        firstRow.add(FIELD_SUBTREE);
        firstRow.add(FIELD_OVERWRITE_TARGET);
        firstRow.add(FIELD_ATTRIBUTES);
        firstRow.add(FIELD_ATTRIBUTES_ACTION);
        firstRow.add(FIELD_LDAP_FILTER);

        writeValues.add(firstRow);
        for (String collectionEntryKey : _collectionEntries.keySet().stream().sorted().toList()) {
            CollectionEntry entry = _collectionEntries.get(collectionEntryKey);
            if(entry.get_displayDN().get()==null || entry.get_displayDN().get().isBlank()) {
                entry.setDisplayDN(entry.get_dn().get());
            }
            ArrayList<String> row = new ArrayList<String>();
            row.add(entry.getDisplayDN());
            row.add(collectionEntryKey);
            if (entry.isSubtree()) row.add("yes");
            else row.add("no");
            if (entry.getOverwriteEntry()) row.add("yes");
            else row.add("no");
            row.add(entry.getAttributes());
            row.add(entry.AttributesAction.get());
            row.add(entry.LdapFilter.get());
            writeValues.add(row);
        }
        Files.deleteIfExists(Paths.get(fileName));
        fileHandler.writeExcel(fileName, writeValues, null);
        set_fileName(fileName);
    }


    public List exportLdif(String directory, String fileName, Connection connection, IProgress progress) throws IOException {
        _breakOperation = false;
        _exportedEntriesCount = 0;
        _currentConnection = connection;
        List<String> missedEntries = new ArrayList<>();
        String exportFileName = null;
        if (fileName != null) exportFileName = directory + "\\" + fileName;
        else exportFileName = directory + "\\" + get_projectName() + ".ldif";
        LDIFWriter writer = new LDIFWriter(exportFileName);
        HashMap<Integer, List<CollectionEntry>> depthMap = new HashMap<>();
        for (String dn : get_collectionEntries().keySet()) {
            int depth = dn.split(",").length;
            List<CollectionEntry> entries = null;
            if (depthMap.containsKey(depth)) entries = depthMap.get(depth);
            else {
                entries = new ArrayList<>();
            }
            entries.add(get_collectionEntries().get(dn));
            depthMap.put(depth, entries);
        }

        List keys = new ArrayList(depthMap.keySet());
        ArrayList<Integer> sortedKeys = (ArrayList) keys.stream().sorted().collect(Collectors.toList());
        for (Integer depth : sortedKeys) {
            List<CollectionEntry> colEntry = depthMap.get(depth);
            for (CollectionEntry c : colEntry) {
                String dn = c.getDn();
                try {
                    if (_breakOperation) return missedEntries;
                    Entry entry = _currentConnection.getEntry(dn);
                    if(entry == null)
                    {
                        progress.setProgress(0,"WARNING: Configured DN for export not found in Target Enviroment" + dn + " , skip");
                        logger.warn("Configured DN for export not found in Target Enviroment" + dn + " , skip");
                        continue;
                    }
                    if (c.getAttributesAction().equalsIgnoreCase(CollectionEntry.EXPORT_ONLY)) {
                        List<Attribute> newAttributes = new ArrayList<>();
                        for (String a : c.getAttributesAsSet()) {
                            if (entry.getAttribute(a) != null) {
                                if (!_currentConnection.OperationalAttributes.contains(a.toLowerCase())) {
                                    newAttributes.add(entry.getAttribute(a));
                                }
                            }
                        }
                        if (newAttributes.isEmpty()) newAttributes.add(entry.getAttribute("objectclass"));
                        Entry newEntry = new Entry(entry.getDN(), newAttributes);
                        entry = newEntry;
                    } else if (c.getAttributesAction().equalsIgnoreCase(CollectionEntry.IGNORE)) {
                        List<Attribute> newAttributes = new ArrayList<>();
                        for (Attribute at : entry.getAttributes()) {
                            if (!c.getAttributesAsSet().contains(at.getName().toLowerCase())) {
                                if (!_currentConnection.OperationalAttributes.contains(at.getName().toLowerCase()))
                                    newAttributes.add(at);
                            }
                        }
                        if (newAttributes.isEmpty()) newAttributes.add(entry.getAttribute("objectclass"));
                        Entry newEntry = new Entry(entry.getDN(), newAttributes);
                        entry = newEntry;
                    } else {
                        List<Attribute> newAttributes = new ArrayList<>();
                        for (Attribute at : entry.getAttributes()) {
                            if (!_currentConnection.OperationalAttributes.contains(at.getName().toLowerCase()))
                                newAttributes.add(at);
                        }
                        if (newAttributes.isEmpty()) newAttributes.add(entry.getAttribute("objectclass"));
                        Entry newEntry = new Entry(entry.getDN(), newAttributes);
                        entry = newEntry;
                    }
                    writer.writeComment(c.getDescription(), true, true);
                    writer.writeEntry(entry);
                    _exportedEntriesCount++;
                    if (c.isSubtree()) {
                        writeAllChildren(c, dn, writer, null, missedEntries, progress);
                    }
                } catch (LDAPException e) {
                    missedEntries.add(dn);
                } catch (IOException e) {
                    missedEntries.add(dn);
                } catch (Exception e) {
                    if (progress != null) progress.signalTaskDone("ldifexport", e.getMessage(), e);
                }
            }
        }
        writer.close();
        if (progress != null) progress.signalTaskDone("ldifexport", "export done", null);
        return missedEntries;
    }

    public void deleteAllCollectionEntriesFromEnviromentWithoutParent(IProgress progress) {
        for (String entryDN : _collectionEntries.keySet()) {
            Filter filter = Filter.createPresenceFilter("objectClass");
            UnboundidLdapSearch unboundidLdapSearch = new UnboundidLdapSearch(_config, _currentConnection, progress);
            unboundidLdapSearch.set_searchDN(entryDN);
            unboundidLdapSearch.set_filter(filter);
            unboundidLdapSearch.set_searchScope(SearchScope.ONE);
            unboundidLdapSearch.run();
            if (unboundidLdapSearch.get_children().isEmpty()) continue;
            else {
                for (Entry entry : unboundidLdapSearch.get_children()) {
                    if (_breakOperation) return;
                    deleteAllChildren(entry.getDN(), progress);
                }
            }
        }
    }

    public void deleteAllChildren(String parentDN, IProgress progress) {
        Filter filter = Filter.createPresenceFilter("objectClass");
        UnboundidLdapSearch unboundidLdapSearch = new UnboundidLdapSearch(_config, _currentConnection, progress);
        unboundidLdapSearch.set_searchDN(parentDN);
        unboundidLdapSearch.set_filter(filter);
        unboundidLdapSearch.set_searchScope(SearchScope.ONE);
        unboundidLdapSearch.run();
        if (unboundidLdapSearch.get_children().isEmpty()) {
            if (_breakOperation) return;
            if (progress != null) progress.setProgress(0, "deleting entry now->" + parentDN);
            try {
                LDAPResult result = _currentConnection.delete(parentDN);
                if (result == null || result.getResultCode() == null || !result.getResultCode().equals(ResultCode.SUCCESS)) {
                    if (progress != null) progress.setProgress(0, "delete failed for->" + parentDN);
                }
            } catch (Exception e) {
                logger.error("Exception deleting entry->" + parentDN, e);
                if (progress != null) progress.setProgress(0, "Error deleting entry" + e.getMessage() + parentDN);
            }
            return;
        } else {
            for (Entry entry : unboundidLdapSearch.get_children()) {
                if (_breakOperation) return;
                deleteAllChildren(entry.getDN(), progress);
            }
            try {
                _currentConnection.delete(parentDN);
            } catch (Exception e) {
                logger.error("Exception deleting entry->" + parentDN, e);
                if (progress != null) progress.setProgress(0, "Error deleting entry" + e.getMessage() + parentDN);
            }
        }
    }

    private void writeAllChildren(CollectionEntry c, String parentDN, LDIFWriter writer, UnboundidLdapSearch reader, List<String> missed, IProgress progress) {
        Filter filter = null;
        boolean filterSet = false;
        if (c.getLdapFilter() != null && !c.getLdapFilter().equalsIgnoreCase("")) {
            try {
                filter = Filter.create(c.getLdapFilter());
                filterSet = true;
            } catch (LDAPException e) {
                return;
            }
        } else {
            filter = Filter.createPresenceFilter("objectClass");
        }
        logger.debug("exporting all children now with " + parentDN + " and entries->" + _exportedEntriesCount);
        UnboundidLdapSearch unboundidLdapSearch = null;
        if (reader == null) unboundidLdapSearch = new UnboundidLdapSearch(_config, _currentConnection, null);
        else unboundidLdapSearch = reader;
        unboundidLdapSearch.set_searchDN(parentDN);
        unboundidLdapSearch.set_filter(filter);
        if (filterSet) unboundidLdapSearch.set_searchScope(SearchScope.SUB);
        unboundidLdapSearch.run();
        if (unboundidLdapSearch.get_children().isEmpty()) return;
        for (Entry entry : unboundidLdapSearch.get_children()) {
            if (_breakOperation) return;
            try {
                if (c.getAttributesAction().equalsIgnoreCase(CollectionEntry.EXPORT_ONLY)) {
                    List<Attribute> newAttributes = new ArrayList<>();
                    for (String a : c.getAttributesAsSet()) {
                        if (!_currentConnection.OperationalAttributes.contains(a.toLowerCase())) {
                            newAttributes.add(entry.getAttribute(a));
                        }
                    }
                    if (newAttributes.isEmpty()) newAttributes.add(entry.getAttribute("objectclass"));
                    Entry newEntry = new Entry(entry.getDN(), newAttributes);
                    entry = newEntry;
                } else if (c.getAttributesAction().equalsIgnoreCase(CollectionEntry.IGNORE)) {
                    List<Attribute> newAttributes = new ArrayList<>();
                    for (Attribute at : entry.getAttributes()) {

                        if (!c.getAttributesAsSet().contains(at.getBaseName().toLowerCase()) &&
                                !_currentConnection.OperationalAttributes.contains(at.getBaseName().toLowerCase())) {
                            newAttributes.add(at);
                        }
                    }
                    if (newAttributes.isEmpty()) newAttributes.add(entry.getAttribute("objectclass"));
                    Entry newEntry = new Entry(entry.getDN(), newAttributes);
                    entry = newEntry;
                } else {
                    List<Attribute> newAttributes = new ArrayList<>();
                    for (Attribute at : entry.getAttributes()) {
                        if (!_currentConnection.OperationalAttributes.contains(at.getBaseName().toLowerCase()))
                            newAttributes.add(at);
                    }
                    if (newAttributes.isEmpty()) newAttributes.add(entry.getAttribute("objectclass"));
                    Entry newEntry = new Entry(entry.getDN(), newAttributes);
                    entry = newEntry;
                }
                writer.writeEntry(entry);
                if (progress != null && _exportedEntriesCount % 10 == 0)
                    progress.setProgress((double) (_exportedEntriesCount % 100) / (double) 100, "Exporting->" + parentDN);
                _exportedEntriesCount++;
                if (!filterSet) writeAllChildren(c, entry.getDN(), writer, unboundidLdapSearch, missed, progress);
            } catch (IOException e) {
                missed.add(entry.getDN());
            } catch (Exception e) {
                logger.error("Exception in writeAllChildren", e);
            }
        }
    }


}
