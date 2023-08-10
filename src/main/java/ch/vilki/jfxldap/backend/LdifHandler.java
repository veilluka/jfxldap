package ch.vilki.jfxldap.backend;

import com.unboundid.ldap.sdk.*;
import com.unboundid.ldif.LDIFWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class LdifHandler {

    static Logger logger = LogManager.getLogger(LdifHandler.class);

    private boolean _breakOperation;
    private int _exportedEntriesCount = 0;
    private Connection _currentConnection = null;
    String[] _returningAttributes = null;

    public void setReadAttributes(UnboundidLdapSearch.READ_ATTRIBUTES readAttributes) {
        switch (readAttributes) {
            case none:
                _returningAttributes = new String[]{"cn"};
                break;
            case all:
                _returningAttributes = new String[]{"*", "+"};
                break;
            case only_user:
                _returningAttributes = new String[]{"*"};
                break;
            case only_operational:
                _returningAttributes = new String[]{"+"};
                break;
            default:
                _returningAttributes = new String[]{"*", "+"};
        }
    }

    public void exportLdif(File file, Connection connection, List<Entry> entries, List<String> exportAttribute,
                           List<String> ignoreAttributes, IProgress progress) {
        _breakOperation = false;
        _exportedEntriesCount = 0;
        _currentConnection = connection;
        List<String> missedEntries = new ArrayList<>();
        LDIFWriter writer = null;
        try {
            writer = new LDIFWriter(file);
        } catch (IOException e) {
            logger.error("Exception in export LDIF", e);
            if (progress != null) progress.signalTaskDone("file_export", "export done", e);
        }
        try {
            Set<String> attributes = new HashSet<>();
            boolean ignore = false;
            if (exportAttribute != null) {
                for (String s : exportAttribute) attributes.add(s);
            } else if (ignoreAttributes != null) {
                for (String s : ignoreAttributes) attributes.add(s);
                ignore = true;
            }
            for (Entry exportEntry : entries) {
                Entry readEntry = _currentConnection.getEntry(exportEntry.getDN(), new String[]{"*", "+"});
                Entry export = null;
                if (attributes.isEmpty()) {
                    export = readEntry;
                } else {
                    export = filterAttributes(exportEntry, attributes, ignore);
                }
                writer.writeEntry(export);
                _exportedEntriesCount++;
                if (_exportedEntriesCount % 10 == 0 && progress != null)
                    progress.setProgress("file_export", (100.0 / entries.size()) * (double) _exportedEntriesCount);
            }
            writer.close();
            if (progress != null) progress.signalTaskDone("file_export", "export done", null);

        } catch (Exception e) {
            progress.signalTaskDone("file_export", "Error", e);
        }
    }

    public void exportLdif(File file, Connection connection, List<String> exportAttribute,
                           List<String> ignoreAttributes, Filter filter, String exportDN,
                           boolean exportChildren, IProgress progress, Config config) {
        _breakOperation = false;
        _exportedEntriesCount = 0;
        _currentConnection = connection;
        LDIFWriter writer = null;
        try {
            writer = new LDIFWriter(file);
        } catch (IOException e) {
            logger.error("Exception in export LDIF", e);
            if (progress != null) progress.signalTaskDone("file_export", "export done", e);
        }
        String filterString = null;
        if (filter != null) filterString = filter.toString();
        UnboundidLdapSearch unboundidLdapSearch = new UnboundidLdapSearch(config, _currentConnection, exportDN, filterString, progress);
        if (exportChildren) unboundidLdapSearch.set_searchScope(SearchScope.SUB);
        else unboundidLdapSearch.set_searchScope(SearchScope.BASE);
        unboundidLdapSearch.setReadAttributes(UnboundidLdapSearch.READ_ATTRIBUTES.all);

        List<Entry> exportEntries = null;
        if (exportChildren) {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<?> future = executor.submit(unboundidLdapSearch);
            try {
                future.get();
            } catch (InterruptedException e) {
                progress.signalTaskDone("file_export", "Search failed for export", e);
                logger.error("error", e);
            } catch (ExecutionException e) {
                progress.signalTaskDone("file_export", "Search failed for export", e);
                logger.error("error", e);
            }
        }
        if (unboundidLdapSearch.get_children().isEmpty()) {
            if (exportChildren) {
                progress.signalTaskDone("file_export", "No entry found for export", null);
                return;
            } else {
                exportEntries = new ArrayList<>();
                try {
                    exportEntries.add(connection.getEntry(exportDN));
                } catch (LDAPException e) {
                    progress.signalTaskDone("file_export", "Error Exporting", e);
                    return;
                }
            }
        } else {
            exportEntries = unboundidLdapSearch.get_children();
        }

        int nrOfFoundEntries = exportEntries.size();
        Set<String> attributes = new HashSet<>();
        boolean ignore = false;
        if (ignoreAttributes != null && !ignoreAttributes.isEmpty()) {
            for (String s : ignoreAttributes) attributes.add(s.toLowerCase());
            ignore = true;
        } else if (exportAttribute != null && !exportAttribute.isEmpty()) {
            for (String s : exportAttribute) attributes.add(s.toLowerCase());
        }
        try {
            for (Entry entry : exportEntries) {
                Entry exportEntry = filterAttributes(entry, attributes, ignore);
                writer.writeEntry(exportEntry);
                _exportedEntriesCount++;
                if (_exportedEntriesCount % 10 == 0 && progress != null) progress.setProgress("file_export",
                        (100.0 / nrOfFoundEntries) * (double) _exportedEntriesCount);
            }
            writer.close();
            if (progress != null) progress.signalTaskDone("file_export", "export done", null);
        } catch (Exception e) {
            progress.signalTaskDone("file_export", "Error", e);
        }
    }

    private Entry filterAttributes(Entry entry, Set<String> attributes, boolean ignore) {
        if (attributes == null || attributes.isEmpty()) return entry;
        Entry retEntry = new Entry(entry.getDN());
        for (Attribute att : entry.getAttributes()) {
            if (ignore) {
                if (!attributes.contains(att.getName().toLowerCase())) retEntry.addAttribute(att);
            } else {
                if (attributes.contains(att.getName().toLowerCase())) retEntry.addAttribute(att);
            }

        }
        return retEntry;
    }
}
