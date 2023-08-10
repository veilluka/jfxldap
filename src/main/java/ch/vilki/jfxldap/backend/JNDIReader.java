package ch.vilki.jfxldap.backend;

import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.SearchScope;
import com.unboundid.util.ssl.SSLUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.*;
import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

public class JNDIReader extends SocketFactory{

    private String _filter = null;
    private String _searchDN;
    private IProgress _progress;
    private Connection _connection = null;
    private String[] _returningAttributes;
    private LdapContext  _ldapContext = null;
    int _scope = SearchControls.ONELEVEL_SCOPE;
    private String[] _displayAttribute = null;
    private Entry _mainEntry;
    private static Config _config=null;
    private List<Entry> _children;

    public int get_pageSize() {
        return _pageSize;
    }
    public Entry get_mainEntry() {return _mainEntry;}
    public List<Entry> get_children() {return _children;}
    public void setDisplayAttribute(String[] displayAttribute) {
        this._displayAttribute = displayAttribute;
    }
    public void set_pageSize(int _pageSize) {
        this._pageSize = _pageSize;
    }
    private int _pageSize;
    public void set_filter(String _filter) {
        this._filter = _filter;
    }
    public void set_scope(int _scope) {
        this._scope = _scope;
    }
      public void set_returningAttributes(String[] _returningAttributes) {
        this._returningAttributes = _returningAttributes;
    }

    static Logger logger = LogManager.getLogger(JNDIReader.class);

    public JNDIReader(Config config, Connection connection, String searchDN,
                      String filterString, IProgress progress )
    {
        _connection = connection;
        _searchDN = searchDN;
        _filter = filterString;
        _progress = progress;
        _pageSize = 500;
        if(config != null)  _config = config;
        _scope = SearchControls.ONELEVEL_SCOPE;
    }

    public void set_scope(SearchScope scope) {
        if(scope.equals(SearchScope.SUB)) _scope = SearchControls.SUBTREE_SCOPE;
        else _scope=SearchControls.ONELEVEL_SCOPE;
    }
    private void initLdapContext(int pagesize) throws IOException, NamingException {
        String providerURL = "ldap://"+_connection.getServer()+":"+_connection.getPort();
        boolean initContext = false;
        if(_connection == null) return;
        if(_ldapContext == null)initContext =true;
        if(_ldapContext != null) {
            try {
                String url = (String) _ldapContext.getEnvironment().get(providerURL);
                if(!url.equalsIgnoreCase(providerURL)) initContext = true;
            } catch (NamingException e1) {
                e1.printStackTrace();
            }
        }
        if(initContext) {
            Hashtable<String, Object > env = new Hashtable<String, Object>(11);
            env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
            env.put(Context.PROVIDER_URL, providerURL);
            env.put(Context.SECURITY_AUTHENTICATION, "simple");
            env.put(Context.SECURITY_PRINCIPAL, _connection.getUser());
            env.put(Context.SECURITY_CREDENTIALS,_config.getConnectionPassword(_connection).toString());
            if(_connection.UseSSL.get())
            {
                env.put("java.naming.ldap.factory.socket", "ch.vilki.backend.MySSLSocketFactory");
                env.put(Context.SECURITY_PROTOCOL, "ssl");
                MySSLSocketFactory.set_config(_config);
            }
            _ldapContext = new InitialLdapContext(env,new Control[] { new PagedResultsControl(pagesize, Control.NONCRITICAL) });
            _ldapContext.setRequestControls(null);
            _ldapContext.setRequestControls(new Control[]{
                    new PagedResultsControl(_pageSize, Control.NONCRITICAL) });
        }
    }

    public Entry getOneChild(String searchDN) throws NamingException, IOException {
        initLdapContext(10);
        SearchControls sc = new SearchControls();
        sc.setSearchScope(SearchControls.ONELEVEL_SCOPE);
        if(_returningAttributes == null) _returningAttributes = new String[]{"*","+"};
        sc.setReturningAttributes(_returningAttributes);
        NamingEnumeration results = null;
        try {
            results = _ldapContext.search(searchDN, "(objectClass=*)", sc);
        }
        catch (Exception e)
        {
            results = _ldapContext.search(searchDN, "(objectClass=*)", sc);
        }

        while (results.hasMoreElements()) {
            SearchResult result = (SearchResult) results.nextElement();
            return convertToUnboundidEntry(result);
        }
        return  null;
    }

    public void run() throws com.unboundid.ldap.sdk.LDAPException, IOException, NamingException, GeneralSecurityException {
        if(_connection == null) return;
        if(!_connection.isConnected()) _connection.connect();
        _mainEntry = _connection.getEntry(_searchDN);
        initLdapContext(_pageSize);
       int total = 0;
       int pagesTillNow = 0;
        _children = new ArrayList<>();
        try {
            byte[] cookie = null;
            do {
                SearchControls sc = new SearchControls();
                sc.setSearchScope(_scope);
                if(_returningAttributes == null) _returningAttributes = new String[]{"*","+"};
                sc.setReturningAttributes(_returningAttributes);
                NamingEnumeration results = _ldapContext.search(_searchDN, _filter, sc);
                while (results.hasMoreElements()) {
                    total++;
                    SearchResult result = (SearchResult) results.nextElement();
                    _children.add(convertToUnboundidEntry(result));
                }
                pagesTillNow++;
                Control[] controls = _ldapContext.getResponseControls();
                if (controls != null) {
                    for (int i = 0; i < controls.length; i++) {
                        if (controls[i] instanceof PagedResultsResponseControl) {
                            PagedResultsResponseControl prrc = (PagedResultsResponseControl) controls[i];
                            cookie = prrc.getCookie();
                        }
                    }
                } else {
                    if(_progress!= null) _progress.setProgress(0,"No controls were sent from the server");
                }
                if(pagesTillNow==10) pagesTillNow=1;
                if(_progress!= null) _progress.setProgress((double) pagesTillNow/10.0, String.valueOf(total) + " entries");
                _ldapContext.setRequestControls(new Control[] { new PagedResultsControl(_pageSize, cookie, Control.CRITICAL) });
            } while (cookie != null);
            _ldapContext.close();
        } catch (Exception e) {
            // no exception, if search can not perform as there are no children, it lands here, even if it is no error.
        }
     }

    private Entry convertToUnboundidEntry(SearchResult result)
    {
        List<com.unboundid.ldap.sdk.Attribute> unboundidAttributes = new ArrayList<>();
        Attributes allAttributes = result.getAttributes();
        try {
            NamingEnumeration<? extends Attribute> all = allAttributes.getAll();
            while(all.hasMore())
            {
                Attribute att = all.next();
                NamingEnumeration<?> allValues = att.getAll();
                List<String> copiedValues = new ArrayList<>();

                while(allValues.hasMore())
                {
                    Object val = allValues.next();
                    copiedValues.add(val.toString());
                }
                com.unboundid.ldap.sdk.Attribute unboundidAttribute = new com.unboundid.ldap.sdk.Attribute(att.getID(), copiedValues);
                unboundidAttributes.add(unboundidAttribute);
            }
            Entry unboundidEntry = new Entry(result.getNameInNamespace(),unboundidAttributes);
            return unboundidEntry;
        } catch (NamingException e) {
            e.printStackTrace();
        }
        return  null;
    }

    @Override
    public Socket createSocket(String s, int i) throws IOException, UnknownHostException {
        return null;
    }

    @Override
    public Socket createSocket(String s, int i, InetAddress inetAddress, int i1) throws IOException, UnknownHostException {
        return null;
    }

    @Override
    public Socket createSocket(InetAddress inetAddress, int i) throws IOException {
        return null;
    }

    @Override
    public Socket createSocket(InetAddress inetAddress, int i, InetAddress inetAddress1, int i1) throws IOException {
        return null;
    }
    public static SocketFactory getDefault() {

        logger.info("[acquiring the default socket factory]");
        TrustManagerFactory tmf = null;
        try {
            tmf = TrustManagerFactory.getInstance("X509");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        try {
            tmf.init(_config.get_keyStore());
        } catch (KeyStoreException e) {
            e.printStackTrace();
        }
        SSLUtil sslUtil = new SSLUtil(tmf.getTrustManagers());
        SSLSocketFactory socketFactory = null;
        try {
            socketFactory = sslUtil.createSSLSocketFactory();
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
        }
        return socketFactory;

    }
}
