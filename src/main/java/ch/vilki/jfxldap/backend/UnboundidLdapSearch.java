package ch.vilki.jfxldap.backend;

import com.unboundid.asn1.ASN1OctetString;
import com.unboundid.ldap.sdk.*;
import com.unboundid.ldap.sdk.controls.SimplePagedResultsControl;
import com.unboundid.util.LDAPTestUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.naming.NamingException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static ch.vilki.jfxldap.backend.Helper.EntryComparator;
import static com.unboundid.ldap.sdk.controls.SimplePagedResultsControl.PAGED_RESULTS_OID;


public class UnboundidLdapSearch implements Runnable {

    public static enum READ_ATTRIBUTES{
        none,
        only_operational,
        only_user,
        all
    }

    static Logger logger = LogManager.getLogger(UnboundidLdapSearch.class);
    public Filter get_filter() {
        return _filter;
    }
    public void set_filter(Filter _filter) {
        this._filter = _filter;
    }
    private Filter _filter = null;
    public String get_searchDN() {
        return _searchDN;
    }
    public void set_searchDN(String _searchDN) {
        this._searchDN = _searchDN;
    }

    public void setDisplayAttribute(String displayAttribute) {
        this.displayAttribute = new String[1];
        this.displayAttribute[0] = displayAttribute;
    }

    private String[] displayAttribute = null;
    private String _searchDN;
    private IProgress _progress;
    private Connection _connection = null;
    public int get_pageSize() {
        return _pageSize;
    }
    public void set_pageSize(int _pageSize) {
        this._pageSize = _pageSize;
    }
    private int _pageSize;
    public SearchResult get_searchResult() {
        return _searchResult;
    }
    private SearchResult _searchResult = null;
    SearchScope _searchScope = SearchScope.ONE;
    private Entry _mainEntry;
    private Config _config;
    private String[] _returningAttributes = null;

    public List<Entry> get_children() {
       return _children.stream().sorted(EntryComparator).collect(Collectors.toList());
    }
    private List<Entry> _children = new ArrayList<>();
    public Entry get_mainEntry() {
        return _mainEntry;
    }
    private UnboundidLdapSearch(){}
    public void set_searchScope(SearchScope searchScope) {
        _searchScope = searchScope;
    }
    public static void setLogger(Logger logger) {
        UnboundidLdapSearch.logger = logger;
    }
    public void set_returningAttributes(String[] _returningAttributes) {
        this._returningAttributes = _returningAttributes;
    }

    public void setReadAttributes(READ_ATTRIBUTES readAttributes)
    {
        switch (readAttributes)
        {
            case none:
                if(displayAttribute != null) _returningAttributes = new String[]{displayAttribute[0]};
                else _returningAttributes = new String[]{"cn","objectclass"};
                logger.debug("Set read attributes none");
                break;
            case all:
                _returningAttributes = new String[]{"*", "+"};
                logger.debug("Set read attributes all");
                break;
            case only_user:
                _returningAttributes = new String[]{"*"};
                logger.debug("Set read attributes only user");
                break;
            case only_operational:
                _returningAttributes = new String[]{"+"};
                logger.debug("Set read attributes only operational");
                break;
            default:
                _returningAttributes = new String[]{"*", "+"};
                logger.debug("Set read attributes ALL");
        }
    }

    public static String[] getReadAttributes(READ_ATTRIBUTES readAttributes)
    {
        switch (readAttributes)
        {
            case none:
                return new String[]{"cn"};
             case all:
                return  new String[]{"*", "+"};
            case only_user:
                return new String[]{"*"};
            case only_operational:
                return new String[]{"+"};
            default:
                return new String[]{"*", "+"};
        }
    }

    public UnboundidLdapSearch(Config config, Connection connection, String searchDN, String filterString, IProgress progress )
    {
        _connection = connection;
        _searchDN = searchDN;
        _config = config;
        if(filterString != null && !filterString.equalsIgnoreCase(""))
        {
            try {
                _filter = Filter.create(filterString);
            } catch (LDAPException e) {
                logger.error("Error creating Filter from the string->" + filterString);
                if(progress!=null) progress.signalTaskDone("readEntries",null,null);
            }
        }
        else
        {
            _filter= Filter.createPresenceFilter("objectClass");
        }
        _progress = progress;
        _pageSize = 50;
        _searchScope = SearchScope.ONE;
    }

    public UnboundidLdapSearch(Config config, Connection connection, IProgress progress )
    {
        _config = config;
        _connection = connection;
        try {
            _filter= Filter.createPresenceFilter("objectClass");
        } catch (Exception e) {
            if(progress!=null) progress.signalTaskDone("readEntries",null,e);
        }
        _progress = progress;
        _pageSize = 100;
    }

    @Override
    public void run() {

        _children = new ArrayList<>();
        try {
            _mainEntry = _connection.getEntry(_searchDN);
        } catch (LDAPException e) {
            e.printStackTrace();
            if(_progress!=null) _progress.signalTaskDone("readEntries",null,e);
            return;
        }
        if(_connection.is_fileMode())
        {
            try {
                SearchResult found = _connection.search(_searchDN,_searchScope,_filter,_returningAttributes);
                for (SearchResultEntry entry : found.getSearchEntries())
                {
                    _children.add(entry);
                }
                if(_progress!=null) _progress.signalTaskDone("readEntries",null,null);
                return;
            } catch (LDAPSearchException e) {
                logger.error("Error searching file",e);
                if(_progress!= null) _progress.signalTaskDone("readEntries","file Error occured",e);
                return;
            }
        }
        if(_connection.UseJNDI.get())
        {
            JNDIReader jndiReader = new JNDIReader(_config,_connection,_searchDN,_filter.toString(),_progress);
            jndiReader.set_returningAttributes(_returningAttributes);
            try {
                jndiReader.set_scope(_searchScope);
                jndiReader.run();
            } catch (LDAPException | NamingException | IOException | GeneralSecurityException e) {
               logger.error("Error running jndi reader",e);
                if(_progress!= null) _progress.signalTaskDone("readEntries","Error occured",e);
                return;
            }
            _mainEntry = jndiReader.get_mainEntry();
            if(_progress!= null) _progress.setProgress(0.5,"Sort entries now");
            _children = jndiReader.get_children();
            if(_progress!=null) _progress.signalTaskDone("readEntries",null,null);
            return;
        }
        logger.debug("Running unboundid search now with filter->" + _filter.toString());
        if(_returningAttributes == null) setReadAttributes(READ_ATTRIBUTES.all);
        SearchRequest searchRequest = new SearchRequest(_searchDN, _searchScope, _filter, _returningAttributes);
        ASN1OctetString resumeCookie = null;
        int counter = 0;
        int found = 0;
        int max = 10 *_pageSize;
        while (true)
        {
            counter++;
            searchRequest.setControls(new SimplePagedResultsControl(_pageSize, resumeCookie));
            SearchResult searchResult = null;
            try {
                logger.debug("run search with searchRequest -> " + searchRequest.toString());
                searchResult = _connection.search(searchRequest);
                LDAPTestUtils.assertHasControl(searchResult,PAGED_RESULTS_OID);
            } catch (Exception e) {
                logger.error(e);
                if(_progress!=null) _progress.signalTaskDone("readEntries",e.toString(),e);
                return;
            }
            found += searchResult.getSearchEntries().size();
            logger.debug("found {} entries",found);
            if(_progress != null)
            {
                if(searchResult.getSearchEntries().size() > 20)
                {
                    if(counter > max) counter = 1;
                    double pr = (double) counter / (double) (_pageSize*10) ;
                    _progress.setProgress(pr,"LDAP Search found ->" + found + " entries");
                }
            }
            for (SearchResultEntry entry : searchResult.getSearchEntries())
            {
                _children.add(entry);
            }
            SimplePagedResultsControl responseControl = null;
            try {
               responseControl = SimplePagedResultsControl.get(searchResult);
            } catch (LDAPException e) {
                e.printStackTrace();
                if(_progress!=null) _progress.signalTaskDone("readEntries",null,null);
                return;
            }
            if (responseControl.moreResultsToReturn())
            {
                resumeCookie = responseControl.getCookie();
            }
            else
            {
                break;
            }
        }
        if(_progress != null) _progress.signalTaskDone("readEntries",null,null);
    }


    public Entry getOneChild(String searchDN) {
        Filter f;
        try {
            f = Filter.create("(objectClass=*)");
        } catch (LDAPException e) {
            e.printStackTrace();
            return null;
        }

        SearchRequest searchRequest = new SearchRequest(searchDN, SearchScope.ONE, f);
        searchRequest.setControls(new SimplePagedResultsControl(10));
        List<SearchResultEntry> searchResult = null;
        try {
            searchResult = _connection.searchEntries(searchRequest);
        } catch (Exception e) {
            try {
                JNDIReader jndiReader = new JNDIReader(_config,_connection,_searchDN,_filter.toString(),_progress);
                return jndiReader.getOneChild(searchDN);
            }
            catch (Exception e1) {
                return null;
            }
        }
        if(searchResult == null || searchResult.size() == 0) return null;
            else return searchResult.get(0);
        }
  }



