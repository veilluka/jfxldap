package ch.vilki.jfxldap.backend;

import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.SearchScope;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.net.URLConnection;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public class ExcelFileHandler {

    static Logger logger = LogManager.getLogger(ExcelFileHandler.class);

    public List<ArrayList<HashMap<String, String>>> get_parsedEntries() {
        return _parsedEntries;
    }

    List<ArrayList<HashMap<String, String>>> _parsedEntries = null;
    XSSFWorkbook _workbook = null;
    private XSSFSheet _sheet = null;
    public String get_fileName() {
        return _fileName;
    }
    private String _fileName = null;
    public boolean readExcelFile(String fileName) {
        logger.info("Reading excel file->" + fileName);
        _parsedEntries = new ArrayList<ArrayList<HashMap<String, String>>>();
        try {
            FileInputStream file = new FileInputStream(new File(fileName));
            _fileName = fileName;
            _workbook = new XSSFWorkbook(file);
            _sheet = _workbook.getSheetAt(0);

            List<Integer[]> startMarkers = findStartMarker();

            for (Integer[] pairs : startMarkers) {
                logger.debug("Found start marker->" + pairs[0] + " and end->" + pairs[1]);
                if (!checkEntry(pairs)) {
                    logger.error("Check not passed");
                    return false;
                } else {
                    ArrayList<HashMap<String, String>> entry = parseEntries(pairs);
                    _parsedEntries.add(entry);
                }
            }
            file.close();
            logger.debug(toString());
        } catch (Exception e) {
            logger.error("Exception during reading excel file", e);
            return false;
        }
        return true;
    }

    public void exportTree(List<Entry> entries, Connection connection, List<String> exportAttribute,
                           List<String> ignoreAttributes, boolean exportDN, File file, boolean csv, IProgress progress) {
        int pos = 0;
        Map<String, Integer> attributePosition = new HashMap<>();
        List<Map<String, List<String>>> allValues = new ArrayList<>();
        if (progress != null) progress.setProgress(0.0, "Preparing entries for export");
        try {
            List<Entry> readEntriesFromConnection = new ArrayList<>();
            Set<String> foundAttributes = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (Entry entry : entries) {
                try {
                    Entry readEntry = connection.getEntry(entry.getDN(), "*", "+");
                    readEntry.getAttributes().forEach(x -> foundAttributes.add(x.getName()));
                    readEntriesFromConnection.add(readEntry);
                } catch (Exception e) {
                    logger.error("Exception in export", e);
                    if (progress != null) progress.signalTaskDone("file_export", "Error in Export", e);
                    return;
                }
            }
            Set<String> attributes = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            if (exportAttribute != null && !exportAttribute.isEmpty()) {
                for (String at : exportAttribute) {
                    if (foundAttributes.contains(at)) attributes.add(at);
                }
            } else if (ignoreAttributes != null && ignoreAttributes.size() > 0) {
                attributes.addAll(foundAttributes);
                for (String at : ignoreAttributes) {
                    if (attributes.contains(at)) attributes.remove(at);

                }
            } else {
                attributes.addAll(foundAttributes);
            }
            for (Entry entry : readEntriesFromConnection) {
                Map<String, List<String>> entryValue = getEntryValues(entry, attributes, exportDN);
                allValues.add(entryValue);
                for (String attName : entryValue.keySet()) {
                    if (!attributePosition.containsKey(attName)) {
                        attributePosition.put(attName, pos);
                        pos++;
                    }
                }
            }
            List<List<String>> exportContent = new ArrayList<>();
            String[] header = new String[pos];
            for (String attName : attributePosition.keySet()) {
                header[attributePosition.get(attName)] = attName;
            }
            exportContent.add(Arrays.asList(header));
            for (Map<String, List<String>> value : allValues) {
                String[] lineValues = new String[pos];
                for (String attName : value.keySet()) {
                    List<String> attValues = value.get(attName);
                    StringBuilder builder = new StringBuilder();
                    for (String s : attValues) {
                        if (csv) {
                            builder.append(s);
                            builder.append(",");
                        } else {
                            builder.append("\"");
                            builder.append(s);
                            builder.append("\"");
                            builder.append("\n");

                        }
                    }
                    builder.deleteCharAt(builder.length() - 1);
                    lineValues[attributePosition.get(attName)] = builder.toString();
                }
                exportContent.add(Arrays.asList(lineValues));
            }
            String fileName = file.getAbsolutePath();
            if (progress != null) progress.setProgress(0.0, "Exporting in File now");
            if (!csv) writeExcel(fileName, exportContent, progress);
            else writeCSV(fileName, exportContent, null, progress);
            if (progress != null) progress.signalTaskDone("file_export", "Export in file->" + fileName + " done", null);
        } catch (Exception e) {
            logger.error("Error during export", e);
            if (progress != null) progress.signalTaskDone("file_export", "Export in file error", e);
        }
    }

    public void exportTree(Connection connection, List<String> exportAttribute,
                           List<String> ignoreAttributes, boolean exportDN, String baseDN, File file, Filter filter,
                           boolean csv, boolean onlyselected, String groupBy, IProgress progress) {
        int pos = 0;
        Map<String, Integer> attributePosition = new HashMap<>();
        List<Map<String, List<String>>> allValues = new ArrayList<>();
        String filterString = null;
        if (filter != null) filterString = filter.toString();
        UnboundidLdapSearch unboundidLdapSearch = new UnboundidLdapSearch(null, connection, baseDN, filterString, progress);
        if (!onlyselected) unboundidLdapSearch.set_searchScope(SearchScope.SUB);
        else unboundidLdapSearch.set_searchScope(SearchScope.BASE);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        progress.setProgress(0.0, "Committ LDAP Search command, waiting....");
        Future<?> future = executor.submit(unboundidLdapSearch);

        try {
            future.get();
        } catch (InterruptedException e) {
            logger.error("error", e);
            progress.signalTaskDone("file_export", "Search failed for export", e);
        } catch (ExecutionException e) {
            logger.error("error", e);
            progress.signalTaskDone("file_export", "Search failed for export", e);
        }
        if (unboundidLdapSearch.get_children() == null || unboundidLdapSearch.get_children().isEmpty()) {
            progress.signalTaskDone("file_export", "Found no entry to export", null);
            return;
        }
        progress.setProgress(0.0, "Preparing entries for export");
        try {
            Set<String> foundAttributes = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (Entry attName : unboundidLdapSearch.get_children()) {
                foundAttributes.addAll(attName.getAttributes().stream().map(x -> x.getName()).collect(Collectors.toSet()));
            }

            Set<String> attributes = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            if (exportAttribute != null && !exportAttribute.isEmpty()) {
                for (String at : exportAttribute) {
                    if (foundAttributes.contains(at)) attributes.add(at);
                }
            } else if (ignoreAttributes != null && ignoreAttributes.size() > 0) {
                attributes.addAll(foundAttributes);
                for (String at : ignoreAttributes) {
                    if (attributes.contains(at)) attributes.remove(at);
                }
            } else {
                attributes.addAll(foundAttributes);
            }
            for (Entry entry : unboundidLdapSearch.get_children()) {
                Map<String, List<String>> entryValue = getEntryValues(entry, attributes, exportDN);
                allValues.add(entryValue);
                for (String attName : entryValue.keySet()) {
                    if (!attributePosition.containsKey(attName)) {
                        attributePosition.put(attName, pos);
                        pos++;
                    }
                }
            }
            List<List<String>> exportContent = new ArrayList<>();
            String[] header = new String[pos];
            for (String attName : attributePosition.keySet()) {
                header[attributePosition.get(attName)] = attName;
            }
            exportContent.add(Arrays.asList(header));
            for (Map<String, List<String>> value : allValues) {
                String[] lineValues = new String[pos];
                List<String> groupByValues = new ArrayList<>();
                for (String attName : value.keySet()) {
                    List<String> attValues = value.get(attName);
                    if(groupBy != null && groupBy.equalsIgnoreCase(attName) && attValues.size()>1)
                    {
                        lineValues[attributePosition.get(attName)] = attValues.get(0);
                        groupByValues.addAll(attValues);
                    }
                    else
                    {
                        StringBuilder builder = new StringBuilder();
                        for (String s : attValues) {
                            builder.append(s);
                            builder.append(",");
                        }
                        builder.deleteCharAt(builder.length() - 1);
                        lineValues[attributePosition.get(attName)] = builder.toString();
                    }
                }
                exportContent.add(Arrays.asList(lineValues));
                if(!groupByValues.isEmpty())
                {
                    for(int i=1; i< groupByValues.size(); i++)
                    {
                        String[] cloned = new String[pos];
                        for(int j=0; j< lineValues.length; j++) cloned[j] = lineValues[j];
                        cloned[attributePosition.get(groupBy.toLowerCase())] = groupByValues.get(i);
                        exportContent.add(Arrays.asList(cloned));
                    }
                }
            }
            String fileName = file.getAbsolutePath();
            if (progress != null) progress.setProgress(0.0, "Exporting in File now");
            if (!csv) writeExcel(fileName, exportContent, progress);
            else writeCSV(fileName, exportContent, null, progress);
            if (progress != null) progress.signalTaskDone("file_export", "Export in file->" + fileName + " done", null);
        } catch (Exception e) {
            logger.error("Error during export", e);
            if (progress != null) progress.signalTaskDone("file_export", "Export in file error", e);
        }
    }



    private Map<String, List<String>> getEntryValues(Entry entry, Set<String> attributes, boolean readDN) {
        Set<String> readAttributes = null;

        if (attributes == null || attributes.isEmpty()) {
            readAttributes = entry.getAttributes().stream().map(x -> x.getName().toLowerCase()).collect(Collectors.toSet());
        } else readAttributes = attributes;
        Map<String, List<String>> retValue = new HashMap<>();
        for (String attName : readAttributes) {
            Attribute attribute = entry.getAttribute(attName);
            if (attribute == null) continue;
            String[] values = attribute.getValues();
            List<String> vals = null;
            if (values != null) vals = Arrays.stream(values).collect(Collectors.toList());
            if (vals != null) {
                retValue.put(attName, vals);
            }
        }
        if (readDN) {
            List<String> dn = new ArrayList<>();
            dn.add(entry.getDN());
            retValue.put("dn", dn);
        }
        return retValue;
    }

    public void writeCSV(String fileName, List<List<String>> content, String comment, IProgress progress) {
        if (progress != null) progress.setProgress(0, "exporting in file now");
        int exportedLines = 0;
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName), "iso-8859-1"))) {
            if (comment != null && !comment.equalsIgnoreCase("")) {
                writer.write("#");
                writer.write(comment);
                writer.write(System.lineSeparator());
            }
            for (List<String> line : content) {
                StringBuilder exportLine = new StringBuilder();
                for (String v : line) {
                    exportLine.append(v);
                    exportLine.append(";");
                }
                if (exportLine.length() > 0) exportLine.deleteCharAt(exportLine.length() - 1);
                writer.write(exportLine.toString());
                writer.write(System.lineSeparator());
                exportedLines++;
                if (exportedLines % 50 == 0 && progress != null)
                    progress.setProgress((100.0 / content.size()) * (double) exportedLines,
                            "lines exported->" + String.valueOf(exportedLines));
            }
        } catch (Exception e) {
            logger.error("Exception occured during csv file export", e);
        }
    }

    public void writeExcel(String fileName, List<List<String>> content, IProgress progress) throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet spreadsheet = workbook.createSheet("Export");
        XSSFRow row;
        TreeMap<String, String> notExported = new TreeMap<>();
        for (int i = 0; i < content.size(); i++) {
            row = spreadsheet.createRow(i);
            Cell cell0 = row.createCell(0);
            if (i == 0) {
                cell0.setCellValue("START");
            }
            if (i == content.size() - 1) {
                cell0.setCellValue("END");
            }
            for (int j = 0; j < content.get(i).size(); j++) {
                try {
                    Cell cell = row.createCell(j + 1);
                    if (content.get(i).get(j) != null && content.get(i).get(j).length() > 32767) {
                        InputStream is = new BufferedInputStream(new ByteArrayInputStream(content.get(i).get(j).getBytes()));
                        String mimeType = URLConnection.guessContentTypeFromStream(is);
                        int pictureureIdx = workbook.addPicture(content.get(i).get(j).getBytes(), Workbook.PICTURE_TYPE_PNG);
                        CreationHelper helper = workbook.getCreationHelper();
                        Drawing drawing = spreadsheet.createDrawingPatriarch();
                        ClientAnchor anchor = helper.createClientAnchor();
                        anchor.setCol1(i);
                        anchor.setRow1(j);
                        drawing.createPicture(anchor, pictureureIdx);

                    } else {
                        cell.setCellValue(content.get(i).get(j));
                    }
                } catch (Exception e) {
                    String position = "CELL(" + String.valueOf(i) + "," + String.valueOf(j) + ")";
                    notExported.put(position, content.get(i).get(j));
                }
            }
        }
        spreadsheet.autoSizeColumn(0);
        spreadsheet.autoSizeColumn(1);
        spreadsheet.autoSizeColumn(2);
        spreadsheet.autoSizeColumn(3);
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(new File(fileName));
        } catch (FileNotFoundException e) {
            logger.error("error exporting excel", e);
            throw new Exception(e);
        }
        try {
            workbook.write(out);
            out.close();
        } catch (IOException e) {
            logger.error("error exporting excel", e);
            throw new Exception(e);
        }

        if (!notExported.isEmpty()) {
            writeNotExportedValues(fileName + "_not_exported.txt", notExported);
            throw new Exception("Export done, but some entries have not been exported, file with missing lines has been created ->"
                    + fileName + "_not_exported.csv");
        }

    }

    private void writeNotExportedValues(String fileName, TreeMap<String, String> notExported) throws IOException {
        Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName), "iso-8859-1"));
        for (String cell : notExported.keySet()) {
            writer.write(cell);
            writer.write(System.lineSeparator());
            writer.write(notExported.get(cell));
            writer.write(System.lineSeparator());
        }
    }

    private ArrayList<HashMap<String, String>> parseEntries(Integer[] markers) {
        XSSFRow row = _sheet.getRow(markers[0]);
        ArrayList<HashMap<String, String>> retValue = new ArrayList<HashMap<String, String>>();
        Iterator<Cell> it;
        it = row.cellIterator();
        HashMap<Integer, String> attributeMapping = new HashMap<Integer, String>();
        int i = 0;
        it.next();
        while (it.hasNext()) {
            Cell cell = it.next();
            String value = getCellValue(cell);
            if (value == null) {
                logger.error("Attribute name is not set, can not procees");
                return null;
            }
            String keyName = value.replaceAll("\\s", "").toLowerCase();
            attributeMapping.put(i, keyName);
            i++;
        }
        for (int j = markers[0] + 1; j <= markers[1]; j++) {
            row = _sheet.getRow(j);
            HashMap<String, String> line = new HashMap<String, String>();
            for (int k = 0; k < attributeMapping.size(); k++) {
                Cell cell = row.getCell(k + 1);
                String value = getCellValue(cell);
                line.put(attributeMapping.get(k), value);
            }
            retValue.add(line);
        }
        return retValue;
    }

    private List<Integer[]> findStartMarker() {
        ArrayList<Integer[]> entriesRowNumber = new ArrayList<Integer[]>();
        int firstRowNumber = _sheet.getFirstRowNum();
        int lastRowNumber = _sheet.getLastRowNum();
        boolean foundStart = false;
        Integer[] pair = null;
        for (int i = firstRowNumber; i <= lastRowNumber; i++) {
            XSSFRow row = _sheet.getRow(i);
            if (row == null) logger.debug("Row with the number=" + i + " has no content");
            else {
                XSSFCell cell0 = row.getCell(0);
                String value = getCellValue(cell0);
                if (value == null) continue;
                String v = value.replaceAll("\\s+", "");
                if (v != null && v.equalsIgnoreCase("START")) {
                    pair = new Integer[2];
                    pair[0] = i;
                    foundStart = true;
                } else if (v != null && foundStart && v.equalsIgnoreCase("END")) {
                    pair[1] = i;
                    foundStart = false;
                    entriesRowNumber.add(pair);
                    pair = null;
                }
            }
        }
        return entriesRowNumber;
    }

    private boolean checkEntry(Integer[] marker) {
        int numberOfAttributes = -1;
        for (int i = marker[0]; i < marker[1]; i++) {
            if (i == marker[0]) numberOfAttributes = _sheet.getRow(i).getLastCellNum();
            else {
                if (_sheet.getRow(i).getLastCellNum() > numberOfAttributes) {
                    logger.error("Row has more data then attributes->" + i);
                    return false;
                }
            }
        }
        return true;
    }


    public String getCellValue(Cell cell) {
        if (cell == null) {
            logger.debug("can not read cell as it is null pointer");
            return null;
        }
        String value = null;
        try {
            value = cell.getStringCellValue();
        } catch (Exception e) {
            logger.debug("Cell is not string value->");
        }
        if (value == null) {
            try {
                logger.debug("Trying to get cell numeric value");
                double numeric = cell.getNumericCellValue();
                int tmp = (int) numeric;
                value = Integer.toString(tmp);

            } catch (Exception e) {
                logger.debug("Did not work, cell is not numeric value");
            }
        }
        if (value == null) {
            try {
                boolean bo = cell.getBooleanCellValue();
                if (bo) value = "TRUE";
                else value = "FALSE";
            } catch (Exception e) {
                logger.debug("cell is not boolean");
            }
        }
        logger.debug("ROW[" + cell.getRowIndex() + "]COLUMN[" + cell.getColumnIndex() + "]->" + value);
        return value;

    }

    public String toString() {
        if (_parsedEntries == null) return "";

        StringBuilder builder = new StringBuilder();
        for (ArrayList<HashMap<String, String>> entry : _parsedEntries) {
            builder.append("\n ---------------BLOCK--------------------------\n");
            for (HashMap<String, String> line : entry) {
                builder.append("\n ---------------ENTRY--------------------------\n");
                for (String k : line.keySet()) {
                    builder.append(k + "=" + line.get(k) + "\n");
                }
            }
        }
        return builder.toString();

    }
}
