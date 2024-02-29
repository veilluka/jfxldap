package ch.vilki.jfxldap.backend;

import ch.vilki.secured.Password;
import ch.vilki.secured.SecureString;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.SearchResultEntry;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class Helper {

    public static  ArrayList<String> longestCommonSubsequence(String text1, String text2) {
        String[] text1Words = text1.split(" ");
        String[] text2Words = text2.split(" ");
        int text1WordCount = text1Words.length;
        int text2WordCount = text2Words.length;
        int[][] solutionMatrix = new int[text1WordCount + 1][text2WordCount + 1];
        for (int i = text1WordCount - 1; i >= 0; i--) {
            for (int j = text2WordCount - 1; j >= 0; j--) {
                if (text1Words[i].equals(text2Words[j])) {
                    solutionMatrix[i][j] = solutionMatrix[i + 1][j + 1] + 1;
                } else {
                    solutionMatrix[i][j] = Math.max(solutionMatrix[i + 1][j],
                            solutionMatrix[i][j + 1]);
                }
            }
        }
        int i = 0, j = 0;
        ArrayList<String> lcsResultList = new ArrayList<String>();
        while (i < text1WordCount && j < text2WordCount) {
            if (text1Words[i].equals(text2Words[j])) {
                lcsResultList.add(text2Words[j]);
                i++;
                j++;
            } else if (solutionMatrix[i + 1][j] >= solutionMatrix[i][j + 1]) {
                i++;
            } else {
                j++;
            }
        }
        return lcsResultList;
    }

    public static  String markTextDifferences(String text1, String text2,ArrayList<String> lcsList, String insertColor, String deleteColor) {
        StringBuffer stringBuffer = new StringBuffer();
        if (text1 != null && lcsList != null) {
            String[] text1Words = text1.split(" ");
            String[] text2Words = text2.split(" ");
            int i = 0, j = 0, word1LastIndex = 0, word2LastIndex = 0;
            for (int k = 0; k < lcsList.size(); k++) {
                for (i = word1LastIndex, j = word2LastIndex;
                     i < text1Words.length && j < text2Words.length; ) {
                    if (text1Words[i].equals(lcsList.get(k)) &&
                            text2Words[j].equals(lcsList.get(k))) {
                        stringBuffer.append("<SPAN>" + lcsList.get(k) + " </SPAN>");
                        word1LastIndex = i + 1;
                        word2LastIndex = j + 1;
                        i = text1Words.length;
                        j = text2Words.length;
                    } else if (!text1Words[i].equals(lcsList.get(k))) {
                        for (; i < text1Words.length &&
                                !text1Words[i].equals(lcsList.get(k)); i++) {
                            stringBuffer.append("<SPAN style='BACKGROUND-COLOR:" +
                                    deleteColor + "'>" + text1Words[i] + " </SPAN>");
                        }
                    } else if (!text2Words[j].equals(lcsList.get(k))) {
                        for (; j < text2Words.length &&
                                !text2Words[j].equals(lcsList.get(k));j++){
                            stringBuffer.append("<SPAN style='BACKGROUND-COLOR:" +
                                    insertColor + "'>" + text2Words[j] + " </SPAN>");
                        }
                    }
                }
            }
            for (; word1LastIndex < text1Words.length; word1LastIndex++) {
                stringBuffer.append("<SPAN style='BACKGROUND-COLOR:" +
                        deleteColor + "'>" + text1Words[word1LastIndex] + " </SPAN>");
            }
            for (; word2LastIndex < text2Words.length; word2LastIndex++) {
                stringBuffer.append("<SPAN style='BACKGROUND-COLOR:" +
                        insertColor + "'>" + text2Words[word2LastIndex] + " </SPAN>");
            }
        }
        return stringBuffer.toString();
    }

    public static List<Integer> findAllPositionsInString(String sourceString, String search)
    {
        List<Integer> found = new ArrayList<>();
        int i=0;
        int start = 0;
        int end = 0;
        while(true)
        {
            int startPosition = sourceString.indexOf(search,start);
            if(startPosition != -1)
            {
                int endingPosition = startPosition + search.length();
                found.add(startPosition);
                found.add(endingPosition);
                start = endingPosition;
            }
            else
            {
                return found;
            }
        }
    }

    public static String getPrettyXml(String xml) {
        if (xml == null || xml.trim().length() == 0) return "";

        int stack = 0;
        StringBuilder pretty = new StringBuilder();
        String[] rows = xml.trim().replaceAll(">", ">\n").replaceAll("<", "\n<").split("\n");

        for (int i = 0; i < rows.length; i++) {
            if (rows[i] == null || rows[i].trim().length() == 0) continue;

            String row = rows[i].trim();
            if (row.startsWith("<?")) {
                pretty.append(row + "\n");
            } else if (row.startsWith("</")) {
                String indent = repeatString(--stack);
                pretty.append(indent + row + "\n");
            } else if (row.startsWith("<") && row.endsWith("/>") == false) {
                String indent = repeatString(stack++);
                pretty.append(indent + row + "\n");
                if (row.endsWith("]]>")) stack--;
            } else {
                String indent = repeatString(stack);
                pretty.append(indent + row + "\n");
            }
        }

        return pretty.toString().trim();
    }

    public  String format(String unformattedXml) {
        return unformattedXml;
    }

    private Document parseXmlFile(String in) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            InputSource is = new InputSource(new StringReader(in));
            return db.parse(is);
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        } catch (SAXException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String repeatString(int stack) {
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < stack; i++) {
            indent.append(" ");
        }
        return indent.toString();
    }

    public static String encrypt(String value, String key){
        if(key != null && !key.equalsIgnoreCase(""))
            try {
                return Password.encrypt(new SecureString(value),new SecureString(key));
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        else return null;
    }
    public static String decrypt(String value,String key )  {
        if(key != null && !key.equalsIgnoreCase(""))
            try {
                return Password.decrypt(value,new SecureString(key)).toString();
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        else return null;
    }

    public static String getTimeStampForFileName()
    {
        Date newDate = new Date();
        String printableDateFormat = "ddMMyy-HHmmss";
        SimpleDateFormat printableSdf = new SimpleDateFormat(printableDateFormat);
        return printableSdf.format(newDate.getTime());
    }

    public static String replace(String source, String target, String replacement)
    {
        StringBuilder sbSource = new StringBuilder(source);
        StringBuilder sbSourceLower = new StringBuilder(source.toLowerCase());
        String searchString = target.toLowerCase();

        int idx = 0;
        while((idx = sbSourceLower.indexOf(searchString, idx)) != -1) {
            sbSource.replace(idx, idx + searchString.length(), replacement);
            sbSourceLower.replace(idx, idx + searchString.length(), replacement);
            idx+= replacement.length();
        }
        sbSourceLower.setLength(0);
        sbSourceLower.trimToSize();
        return sbSource.toString();
    }


    public static Comparator<Entry> EntryComparator = (Entry one, Entry two)->{
        try
        {
            return one.getRDN().getAttributeValues()[0].compareTo(two.getRDN().getAttributeValues()[0]);
        }
        catch (Exception e)
        {
            return 0;
        }
    };

    public static Comparator<Entry> DN_LengthComparator = (Entry one, Entry two)->{
        try
        {
            if(one.getParsedDN().getRDNStrings().length > two.getParsedDN().getRDNStrings().length) return  -1;
            else  if(one.getParsedDN().getRDNStrings().length < two.getParsedDN().getRDNStrings().length) return  1;
            else return 0;
        }
        catch (Exception e)
        {
            return 0;
        }
    };

    public static Comparator<SearchResultEntry> SearchResultComparator = (SearchResultEntry one, SearchResultEntry two)->{
        try
        {
            return one.getRDN().getAttributeValues()[0].compareTo(two.getRDN().getAttributeValues()[0]);
        }
        catch (Exception e)
        {
            return 0;
        }
    };

    public static String parseTime(String time)
    {
        String[] formats = new String[]{"yyyyMMddhhmmssZ","yyyyMMddhhmmss'Z'","yyyyMMddhhmmss'.000Z'","yyyyMMddhhmmss'.Z'"};
        Date parsed = null;
        for(String f: formats)
        {
            try {
                DateFormat df = new SimpleDateFormat(f);
                df.setTimeZone(TimeZone.getTimeZone("UTC"));
                parsed = df.parse( time);
                break;

            }
            catch (ParseException e) {}
        }
        if(parsed != null)
        {
            DateFormat df = new SimpleDateFormat("dd.MMM yyyy HH:mm:ss.SSS");
            df.setTimeZone(TimeZone.getTimeZone("UTC"));
            return df.format(parsed);

        }
        return null;
    }



}
