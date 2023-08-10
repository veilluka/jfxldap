package ch.vilki.jfxldap.backend;

import com.unboundid.asn1.ASN1OctetString;
import com.unboundid.ldap.sdk.*;
import com.unboundid.ldif.LDIFReader;
import com.unboundid.ldif.LDIFWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CollectionImport implements IProgress {

    /**
     * to keep the order of entries from the file
     */
    private static HashMap<Integer, List<Entry>> _importedLdapEntries = null;
    private Connection _connection = null;

    public static String TASK_NAME = "collection_import";

    public static enum IMPORT_OPTIONS {
        ADD_ONLY,
        MODIFY_ONLY,
        ADD_OR_MODIFY
    }

    public static IMPORT_OPTIONS get_IMPORT_OPTIONS(String importOptions) {
        if (importOptions.equalsIgnoreCase("ADD_ONLY")) return IMPORT_OPTIONS.ADD_ONLY;
        if (importOptions.equalsIgnoreCase("ADD_OR_MODIFY")) return IMPORT_OPTIONS.ADD_OR_MODIFY;
        if (importOptions.equalsIgnoreCase("MODIFY_ONLY")) return IMPORT_OPTIONS.MODIFY_ONLY;
        return null;
    }

    static Logger logger = LogManager.getLogger(CollectionImport.class);
    private String _fileName = null;
    private IProgress _progress = null;
    private static int _nrOfEntries = 0;

    public void set_progress(IProgress progress) {
        _progress = progress;
    }

    public boolean uploadDirectory(String dir, boolean backup, Connection connection,
                                   List<Filter> filters, IProgress _progress, IMPORT_OPTIONS import_options) {
        this._progress = _progress;
        try (Stream<Path> paths = Files.walk(Paths.get(dir))) {
            paths
                    .filter(Files::isRegularFile)
                    .forEach(x ->
                    {
                        if (x.getFileName().toString().contains(".ldif")) {
                            loadFile(x.toAbsolutePath().toString(), filters);
                            try {
                                importInEnviroment(backup, connection, import_options);
                            } catch (Exception e) {
                                logger.error("Exception during import in enviroment", e);
                                signalTaskDone(TASK_NAME, "Error import file->" + x.getFileName(), e);
                                return;
                            }
                        }
                    });
        } catch (Exception e) {
            signalTaskDone(TASK_NAME, "Error reading dir", e);
        }
        return true;
    }

    public boolean loadFile(File file, IProgress progress, List<Filter> filters) {
        _progress = progress;
        return loadFile(file.getAbsolutePath(), filters);
    }

    public boolean loadFile(String fileName, List<Filter> filters) {
        _nrOfEntries = 0;
        logger.info("Loading file->" + fileName);
        _fileName = fileName;
        _importedLdapEntries = new HashMap<Integer, List<Entry>>();
        LDIFReader ldifReader = null;
        try {
            ldifReader = new LDIFReader(fileName);
        } catch (IOException e) {
            logger.error("Exception during opening of ldif file->" + fileName, e);
            signalTaskDone(TASK_NAME, "Exception during opening of ldif file->" + fileName, e);
            return false;
        }
        logger.info("Reading ldif entries now from file");
        while (true) {
            Entry entry = null;
            try {
                try {
                    entry = ldifReader.readEntry();
                } catch (IOException e) {
                    logger.error("Error reading entry, break the operation");
                    _importedLdapEntries = null;
                    _fileName = null;
                    signalTaskDone(TASK_NAME, "Error reading entry", null);
                    return false;
                }
                if (entry == null) break;
                else {
                    boolean processEntry = false;
                    if (filters != null) {
                        for (Filter f : filters) {
                            if (f.matchesEntry(entry)) processEntry = true;
                        }
                    } else {
                        processEntry = true;
                    }
                    if (processEntry) {
                        logger.debug("Adding antry now->" + entry.getDN());
                        int depth = entry.getDN().split(",").length;
                        List<Entry> entries = null;
                        if (_importedLdapEntries.containsKey(depth)) entries = _importedLdapEntries.get(depth);
                        else {
                            entries = new ArrayList<>();
                        }
                        entries.add(entry);
                        _importedLdapEntries.put(depth, entries);
                        _nrOfEntries++;
                        if (_nrOfEntries % 50 == 0) {
                            if (_progress != null)
                                _progress.setProgress((double) ((double) _nrOfEntries / (double) 100) % 100,
                                        "Nr of entries read from file->" + _nrOfEntries);
                        }
                    }
                }
            } catch (Exception le) {
                logger.error("Error reading file", le);
                signalTaskDone(TASK_NAME, "Error reading file", le);
                return false;
            }
        }
        try {
            ldifReader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }


    public void deleteEntries(Connection connection, String backupFile) {
        if (backupFile != null) createBackup(backupFile);
        List<String> keys = new ArrayList(_importedLdapEntries.keySet());
        int doneEntries = 0;

        for (int i = 50; i > 0; i--) {
            if (!_importedLdapEntries.containsKey(i)) continue;
            List<Entry> entries = _importedLdapEntries.get(i);
            for (Entry s : entries) {
                doneEntries++;
                try {
                    if (connection.getEntry(s.getDN()) != null) {
                        connection.deleteTree(s.getDN());
                        if (_progress != null)
                            _progress.setProgress((double) doneEntries / (double) _importedLdapEntries.size(),
                                    "Entry deleted ->" + s.getDN());
                    } else {
                        if (_progress != null)
                            _progress.setProgress((double) doneEntries / (double) _importedLdapEntries.size(),
                                    "Entry does not exist in target, not deleted ->" + s.getDN());
                    }

                } catch (Exception e) {
                    logger.error("Error deleting entry->" + s.getDN(), e);
                    if (_progress != null)
                        _progress.setProgress((double) doneEntries / (double) _importedLdapEntries.size(),
                                "Error deleting entry->" + s.getDN());
                }
            }
        }
    }

    private void createBackup(String fileName) {
        LDIFWriter ldifWriter = null;
        logger.info("backup flag is set, create backup now to->" + fileName);
        try {
            ldifWriter = new LDIFWriter(fileName);
        } catch (IOException e) {
            if (_progress != null) _progress.signalTaskDone(TASK_NAME, "Error", e);
            logger.error("Error creating ldif backup file", e);
            return;
        }
        List keys = new ArrayList(_importedLdapEntries.keySet());
        ArrayList<Integer> sortedKeys = (ArrayList) keys.stream().sorted().collect(Collectors.toList());
        for (Integer k : sortedKeys) {
            List<Entry> entries = _importedLdapEntries.get(k);
            for (Entry entry : entries) {
                try {
                    Entry targetEntry = _connection.getEntry(entry.getDN());
                    if (targetEntry != null) ldifWriter.writeEntry(targetEntry);
                } catch (LDAPException e) {
                    logger.debug("Could not find entry to backup->" + entry.getDN());
                } catch (IOException e) {
                   logger.error("Exception",e);
                }
            }
        }
        try {
            ldifWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void importInEnviroment(boolean backup, Connection connection, IMPORT_OPTIONS import_options) throws Exception {
        if (_progress != null) _progress.setProgress(0, "Loading Entries in LDAP NOW ");
        _connection = connection;
        logger.info("Importing in enviroment now->" + connection.getName());
        List keys = new ArrayList(_importedLdapEntries.keySet());
        if (_progress != null) _progress.setProgress(0, "Sorting entries first ");
        ArrayList<Integer> sortedKeys = (ArrayList) keys.stream().sorted().collect(Collectors.toList());
        int doneEntries = 0;
        int nrOfEntries = 0;
        for (Integer key : _importedLdapEntries.keySet()) {
            nrOfEntries += _importedLdapEntries.get(key).size();
        }

        for (Integer k : sortedKeys) {
            List<Entry> entries = _importedLdapEntries.get(k);
            for (Entry entry : entries) {
                doneEntries++;
                if (!parentExistInLDAP(entry, connection)) {
                    logger.debug("Parent does not exist for entry in LDAP, try add later->" + entry.getDN());
                    _progress.setProgress((double) doneEntries / (double) nrOfEntries,
                            "not imported ->" + entry.getDN());

                } else {
                    importEntry(entry, connection, import_options);
                    if (_progress != null && doneEntries % 10 == 0) {
                        String dn = entry.getDN();
                        _progress.setProgress((double) doneEntries / (double) nrOfEntries,
                                "Imported ->" + dn);
                    }
                }
            }
        }
        if (_progress != null) _progress.signalTaskDone(TASK_NAME, null, null);
        return;
    }

    private boolean parentExistInLDAP(Entry entry, Connection connection) {
        String parentDN = null;
        try {
            parentDN = entry.getParentDNString();
            SearchResultEntry parent = connection.getEntry(parentDN);
            if (parent != null) parentDN = parent.getDN();
        } catch (LDAPException e) {
            e.printStackTrace();
        }
        if (parentDN == null) return false;
        return true;
    }

    private void importEntry(Entry entry, Connection connection, IMPORT_OPTIONS import_options) throws Exception {
        SearchResultEntry found = connection.getEntry(entry.getDN());
        if (found != null) {
            int e1 = found.hashCode();
            int e2 = entry.hashCode();
            if (e1 == e2) {
                logger.debug("Entries have equal content ->" + found.getDN());
                return;
            } else {
                logger.debug("Entries content not equal->" + entry.getDN());
                if (import_options.equals(IMPORT_OPTIONS.ADD_OR_MODIFY) || import_options.equals(IMPORT_OPTIONS.MODIFY_ONLY))
                    modifyEntry(entry, found, connection);
            }
        } else {
            if (connection.getEntry(entry.getParentDN().toString()) == null) {
                logger.error("Can not import entry, as parent is null->" + entry.getParentDN().toString());
                return;
            }
            logger.debug("Importing entry now->" + entry.getDN());
            if (import_options.equals(IMPORT_OPTIONS.ADD_OR_MODIFY) || import_options.equals(IMPORT_OPTIONS.ADD_ONLY))
                connection.add(entry);
        }
    }


    private void modifyEntry(Entry source, Entry target, Connection connection) throws Exception {
        logger.debug("Modify LDAP Entry->" + target.getDN());
        HashSet<String> sourceAttributes = getMap(source.getAttributes());
        HashSet<String> targetAttributes = getMap(target.getAttributes());
        ArrayList<Modification> modifications = new ArrayList<Modification>();
        for (String attName : sourceAttributes) {
            if (!targetAttributes.contains(attName)) {
                logger.debug("Adding attribute to target->" + attName);
                Modification modification = new Modification(ModificationType.ADD, attName, source.getAttribute(attName).getRawValues());
                modifications.add(modification);
            } else {
                ASN1OctetString[] sourceValues = source.getAttribute(attName).getRawValues();
                ASN1OctetString[] targetValues = target.getAttribute(attName).getRawValues();
                if (!Arrays.equals(sourceValues, targetValues)) {
                    logger.debug("Modify attribute ->" + attName);
                    Modification modification = new Modification(ModificationType.REPLACE, attName, source.getAttribute(attName).getRawValues());
                    modifications.add(modification);
                } else {
                    logger.debug("Attribute same, no modification->" + attName);
                }
            }
        }
        for (String attName : targetAttributes) {
            if (!sourceAttributes.contains(attName)) {
                Modification modification = new Modification(ModificationType.DELETE, attName);
                modifications.add(modification);
            }
        }
        ModifyRequest modRequest = null;
        modRequest = new ModifyRequest(target.getDN(), modifications);

        try {
            connection.modify(modRequest);
        } catch (LDAPException e) {
            logger.error("Exception occured during modify request = " + modRequest.toLDIF(), e);
        }
    }

    private HashSet<String> getMap(Collection<Attribute> attributes) {
        HashSet<String> ret = new HashSet<String>();
        Iterator<Attribute> it = attributes.iterator();
        while (it.hasNext()) {
            Attribute at = it.next();
            ret.add(at.getName());
        }
        return ret;
    }

    public static String getFileFormatTime() {
        DateFormat df = new SimpleDateFormat("[MMddhh][mmss]");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        return df.format(new Date());
    }


    @Override
    public void setProgress(double progress, String description) {

    }

    @Override
    public void signalTaskDone(String taskName, String description, Exception e) {
        if (_progress != null) _progress.signalTaskDone(taskName, description, e);
    }

    @Override
    public void setProgress(String taskName, double progress) {

    }
}
