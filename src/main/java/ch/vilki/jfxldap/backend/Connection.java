package ch.vilki.jfxldap.backend;


import ch.vilki.secured.SecureString;
import com.unboundid.ldap.sdk.*;
import com.unboundid.ldap.sdk.controls.SubtreeDeleteRequestControl;
import com.unboundid.ldap.sdk.schema.AttributeTypeDefinition;
import com.unboundid.ldap.sdk.schema.Schema;
import com.unboundid.ldif.LDIFException;
import com.unboundid.ldif.LDIFReader;
import com.unboundid.ldif.LDIFWriter;
import com.unboundid.util.LDAPTestUtils;
import com.unboundid.util.ssl.SSLUtil;
import com.unboundid.util.ssl.TrustAllTrustManager;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.property.SimpleSetProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public  class Connection implements  java.io.Serializable, Comparable<Connection> {

    static Logger logger = LogManager.getLogger(Connection.class);

    private  SimpleStringProperty Name;

    public SimpleStringProperty nameProperty() {
        return Name;
    }
    public void setName(String name) {
        this.Name.set(name);
    }
    public String getName() {
        return Name.get();
    }

    private  SimpleStringProperty Server;
    public String getServer() {
        return Server.get();
    }
    public void setServer(String server) {
        this.Server.set(server);
    }
    public SimpleStringProperty serverProperty() {
        return Server;
    }

    private  SimpleStringProperty Tag;
    public String getTag() {
        return Tag.get();
    }
    public void setTag(String server) {
        this.Tag.set(server);
    }
    public SimpleStringProperty tagProperty() {
        return Tag;
    }

    private  SimpleStringProperty Port;
    public String getPort() {
        return Port.get();
    }
    public int getPortNumber()
    {
        return Integer.parseInt(portProperty().get());
    }
    public SimpleStringProperty portProperty() {
        return Port;
    }
    public void setPort(String port) {
        this.Port.set(port);
    }

    private  SimpleStringProperty User;
    public String getUser() {
        return User.get();
    }
    public SimpleStringProperty userProperty() {
        return User;
    }
    public void setUser(String user) {
        this.User.set(user);
    }

    private  SimpleStringProperty Password;
    public String getPassword()  { return Password.get(); }
    public void setPassword(String password)  {
        Password.set(password);
    }
    public SimpleStringProperty passwordProperty() {
        return Password;
    }

    private  SimpleStringProperty BaseDN;
    public SimpleStringProperty baseDNProperty() {return BaseDN;}
    public void setBaseDN(String baseDN){BaseDN.set(baseDN);}

    private  SimpleStringProperty DisplayAttribute;
    public String getDisplayAttribute(){return DisplayAttribute.get();}
    public SimpleStringProperty displayAttributeProperty() {return DisplayAttribute;}
    public void setDisplayAttribute(String displayAttribute){DisplayAttribute.set(displayAttribute);}

    SimpleBooleanProperty UseJNDI;
    public boolean isUseJNDI() {return UseJNDI.get();}
    public SimpleBooleanProperty useJNDIProperty() {return UseJNDI;}
    public void setUseJNDI(boolean UseJNDI) {this.UseJNDI.set(UseJNDI);}

    SimpleBooleanProperty UseSSL;
    public boolean isSSL() {return UseSSL.get();}
    public SimpleBooleanProperty getSSLProperty() {return UseSSL;}
    public void setSSL(boolean ssl) {this.UseSSL.set(ssl);}

    private  SimpleSetProperty<AttributeTypeDefinition> AttributeDefinition;
    public ObservableSet<AttributeTypeDefinition> getAttributeDefinition() {return AttributeDefinition.get();}
    public SimpleSetProperty<AttributeTypeDefinition> attributeDefinitionProperty() {return AttributeDefinition;}
    public void setAttributeDefinition(ObservableSet<AttributeTypeDefinition> attributeDefinition) {
        this.AttributeDefinition.set(attributeDefinition);
    }

    public void setAttributeDefinition(Set<AttributeTypeDefinition> attributeDefinition) {
        this.AttributeDefinition.clear();
        this.AttributeDefinition.addAll(attributeDefinition);
    }
    private  SimpleStringProperty Vendor;
    public String getVendor() {return Vendor.get();}
    public SimpleStringProperty vendorProperty() {return Vendor;}
    public void setVendor(String vendor) {this.Vendor.set(vendor);}

    public SimpleListProperty<String> SchemaAttributes ;
    public ObservableList<String> getSchemaAttributes() {return SchemaAttributes.get();}
    public SimpleListProperty<String> schemaAttributesProperty() {
        return SchemaAttributes;
    }
    public void setSchemaAttributes(ObservableList<String> schemaAttributes) {
        this.SchemaAttributes.set(schemaAttributes);
    }
    public void setSchemaAttributes(List<String> attributes)
    {
        if(SchemaAttributes == null) SchemaAttributes = new SimpleListProperty<String>();
        SchemaAttributes.clear();
        SchemaAttributes.addAll(attributes);
    }

    public TreeSet<String> getOperationalAttributes() {
        return OperationalAttributes;
    }

    public TreeSet<String> OperationalAttributes  = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);


    private SimpleListProperty<String> LdapContext;
    public Object getLdapContext() {return LdapContext.get();}
    public SimpleListProperty ldapContextProperty() {return LdapContext;}
    public void setLdapContext(List<String> ldapContext) {
        if(ldapContext == null) return;
        this.LdapContext.clear();
        for(String s:ldapContext) this.LdapContext.addAll(ldapContext);
    }
    public void setLdapContext(String[] ldapContext) {
        if(ldapContext == null) return;
        for(String s:ldapContext) this.LdapContext.add( s );
    }

    public TreeMap<String, Entry> get_fileEntries() {
        return _fileEntries;
    }

    private TreeMap<String,Entry> _fileEntries = null;
    private TreeMap<String,Entry> _fileDummies = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public boolean is_fileMode() {
        return _fileMode;
    }

    public void set_fileMode(boolean _fileMode) {
        this._fileMode = _fileMode;
        if(_fileEntries == null) _fileEntries = new TreeMap(String.CASE_INSENSITIVE_ORDER);
        _fileDummies.clear();
    }
    public RootDSE get_rootDSE() {
        return _rootDSE;
    }
    public String[] get_context() {
        return _context;
    }
    public LDAPConnection get_ldapConnection() {
        if(_fileMode) return null;
        return _ldapConnection;
    }
    private LDAPConnection _ldapConnection;
    private RootDSE _rootDSE;
    private boolean _fileMode;
    String[] _context = null;
    private String _fileName = null;

    public String get_fileName(){return _fileName;}
    private TreeSet<String> _foundSchemaAttributes = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private boolean _readOnly = false;
    public boolean is_readOnly() { return _readOnly;}
    public void set_readOnly(boolean _readOnly) {this._readOnly = _readOnly;}
    private static Config _config;


    public static void set_config(Config config) {
        if(config == null) return;
        Connection._config = config;
    }

    public Connection(String Name, String Server, String Port, String User, String Password, String BaseDN, String tag, String DisplayAttribute)
    {
        this.Name= new SimpleStringProperty(Name);
        this.Server= new SimpleStringProperty(Server);
        this.Port= new SimpleStringProperty(Port);
        this.User= new SimpleStringProperty(User);
        this.Password= new SimpleStringProperty(Password);
        this.BaseDN = new SimpleStringProperty(BaseDN);
        this.DisplayAttribute = new SimpleStringProperty(DisplayAttribute);
        this.LdapContext = new SimpleListProperty<>(FXCollections.observableArrayList());
        this.AttributeDefinition = new SimpleSetProperty<>(FXCollections.observableSet());
        this.Vendor = new SimpleStringProperty();
        this.UseJNDI = new SimpleBooleanProperty();
        this.UseSSL = new SimpleBooleanProperty();
        this.SchemaAttributes = new  SimpleListProperty<>(FXCollections.observableArrayList());
        this.Tag = new SimpleStringProperty(tag);
        _foundSchemaAttributes.clear();

    }
    public Connection copy() {
        Connection c = new Connection(this.getName(),
                this.getServer(),
                this.getPort(),
                this.getUser(),
                this.getPassword(),
                this.getBaseDN(),
                this.getTag(),
                this.getDisplayAttribute());
        c.setLdapContext(this.LdapContext.get());
        c.setAttributeDefinition(this.AttributeDefinition);
        c.setVendor(this.vendorProperty().get());
        c.setUseJNDI(this.UseJNDI.get());
        c._foundSchemaAttributes.addAll(this._foundSchemaAttributes);
        c.set_readOnly(this.is_readOnly());
        c.setSSL(this.isSSL());

        return c;
    }
    public Connection(Config config)
    {
       initAttributes();
    }

    public Connection(String fileName, IProgress progress, boolean loadAllEntries) throws IOException, LDIFException {
        initAttributes();
        setName(fileName);
        set_readOnly(false);
        setSSL(false);
        setUser("local");
        setPort("");
        setServer("local file");
        _fileMode = true;
        _fileName = fileName;
        _fileEntries = new TreeMap(String.CASE_INSENSITIVE_ORDER);
        _fileDummies.clear();
        if(loadAllEntries) loadFile(progress);
    }
    public Future<?> loadFile(IProgress progress) {
        ExecutorService executor
                = Executors.newSingleThreadExecutor();
        return executor.submit(() -> {
            LDIFReader ldifReader = null;
            try {
                ldifReader = new LDIFReader(_fileName);
                int readEntries = 0;
                while (true) {
                    Entry entry = ldifReader.readEntry();
                    if(entry == null) break;
                    _fileEntries.put(entry.getDN(),entry);
                    readEntries++;
                    if(progress != null && (readEntries % 100 == 0))
                    {
                        progress.setProgress(entry.getDN(), (double) (readEntries%100) / (100.0));
                    }
                }
                ldifReader.close();
            }
            catch (Exception e)
            {
               logger.error("Exception",e);
               e.printStackTrace();
            }
            _foundSchemaAttributes.clear();
        });
    }
    private void initAttributes()
    {
        this.Name= new SimpleStringProperty();
        this.Server= new SimpleStringProperty();
        this.Port= new SimpleStringProperty();
        this.User= new SimpleStringProperty();
        this.Password= new SimpleStringProperty();
        this.BaseDN = new SimpleStringProperty();
        this.DisplayAttribute = new SimpleStringProperty();
        this.LdapContext = new SimpleListProperty<>(FXCollections.observableArrayList());
        this.AttributeDefinition = new SimpleSetProperty<>(FXCollections.observableSet());
        this.Vendor = new SimpleStringProperty();
        this.UseJNDI = new SimpleBooleanProperty(false);
        this.SchemaAttributes = new SimpleListProperty<>(FXCollections.observableArrayList());
        this.UseSSL = new SimpleBooleanProperty(false);
        this.Tag = new SimpleStringProperty();
        _foundSchemaAttributes.clear();
    }
    public String getBaseDN()  {
        if(BaseDN.get() == null || BaseDN.get().equalsIgnoreCase(""))
        {
           return null;
        }
        return BaseDN.get();
    }
    public String[] getAllSchemaAttributes()
    {
        if(SchemaAttributes != null)
        {
            List<String> attribs = SchemaAttributes.stream().collect(Collectors.toList());
            return  attribs.toArray(new String[attribs.size()]);
        }
        return null;
    }

    public List<String> getAllMultivalueSchemaAttributes()
    {
        if(SchemaAttributes != null && _ldapConnection != null)
        {
            try {
                List<String> attribs = new ArrayList<>();
                for (AttributeTypeDefinition x : _ldapConnection.getSchema().getAttributeTypes()) {
                    if (!x.isSingleValued()) {
                        attribs.add(x.getNameOrOID());
                    }
                }
                return attribs;
            }
            catch (Exception e){return null;}
        }
        return null;
    }


    public boolean isConnected()
    {
        if(_ldapConnection != null)
        {
            if(!_ldapConnection.isConnected())
            {
                try {
                    _ldapConnection.reconnect();
                    if(_ldapConnection.isConnected()) return true;
                    return false;
                } catch (LDAPException e) {
                    logger.error(e);
                    return false;
                }
            }
            else return true;
        }
        else if(is_fileMode()) return true;
        return false;
    }

    @Override
    public String toString() {
        return Name.get();
    }

    @Override
    public int compareTo(Connection o) {return Name.get().compareTo(o.nameProperty().get());}
    public void disconect()
    {
        if(_ldapConnection != null)
        {
            _ldapConnection.close();
            _ldapConnection = null;
        }
    }

    public void connect() throws LDAPException, GeneralSecurityException {

        LDAPConnection c = null;
        if(UseSSL.get())
        {
           if(_config == null || _config.get_keyStore() == null)
               throw new LDAPException(ResultCode.AUTH_METHOD_NOT_SUPPORTED,"Can not connect with SSL, " +
                   "no key store provided");
           SSLSocketFactory sslSocketFactory = getSSLSocketFactory();
           c = new LDAPConnection(sslSocketFactory);
        }
        else
        {
            c = new LDAPConnection();
        }
        c.connect(Server.get(),getPortNumber());
        BindResult bindResult = c.bind(User.get(),Password.get());
        if(bindResult.getResultCode().equals(ResultCode.SUCCESS))
        {
            _rootDSE = c.getRootDSE();
            if(_rootDSE != null)
            {
                _context = _rootDSE.getNamingContextDNs();
                if(_context != null && _context.length > 0 ) setLdapContext(_context);
            }
            Schema schema = c.getSchema();
            if(schema != null)
            {
                Set<AttributeTypeDefinition> attributeTypes = schema.getAttributeTypes();
                setAttributeDefinition(attributeTypes);
            }
            _ldapConnection =  c;
            SchemaAttributes.addAll(_ldapConnection.getSchema().getAttributeTypes().stream().map(x -> x.getNameOrOID().toLowerCase()).collect(Collectors.toList()));
            OperationalAttributes.addAll(_ldapConnection.getSchema().getOperationalAttributeTypes().stream().map(x->x.getNameOrOID().toLowerCase()).collect(Collectors.toSet()));
        }
        else
        {
            c.close();
        }
    }

    private SSLSocketFactory getSSLSocketFactory() throws GeneralSecurityException {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
        tmf.init(_config.get_keyStore());
        SSLUtil sslUtil = new SSLUtil(tmf.getTrustManagers());
        SSLSocketFactory socketFactory = sslUtil.createSSLSocketFactory();
        return socketFactory;
    }

    /*
    public void useSSL() throws GeneralSecurityException, IOException, LDAPException {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
        tmf.init(trustStore);
        TrustManager[] trustManagers = tmf.getTrustManagers();
        TrustAllTrustManager trustAllTrustManager = new TrustAllTrustManager();
        SSLUtil sslUtil = new SSLUtil(trustAllTrustManager);
        SSLSocketFactory socketFactory = sslUtil.createSSLSocketFactory();
        LDAPConnection connection = new LDAPConnection(socketFactory);
        connection.connect("SERVER", 636);
        connection.bind("USER","password");
        if(connection.isConnected()) System.out.println("SSL CONNECTION OK");
        else System.out.println("CONNECTION FAILED");
        SSLSession sslSession = connection.getSSLSession();
        Certificate[] certificates =sslSession.getPeerCertificates();
        trustAllTrustManager.checkClientTrusted((X509Certificate[]) certificates,null);
        String type = certificates[0].getType();
        if(certificates!= null && certificates.length > 0)
        {

           for(int i=0; i< certificates.length; i++)
           {
              byte[] e1 = Base64.getEncoder().encode(certificates[i].getEncoded());
              String s1 = new String(e1,"utf-8");
//              PEMWriter pemWriter = new PEMWriter(new FileWriter("d:\\tmp\\p" + i +".cert"));
                    //FileWriter pemWriter = new FileWriter("d:\\tmp\\pa" + i +".cert");
 //                   pemWriter.write(s1);
   //                 pemWriter.close();
                    //pemWriter = new PEMWriter(new FileWriter("d:\\tmp\\p" + i + ".cert"));
               }
        }
    }
    *
     */

    public Set<X509Certificate> getCertificates() throws GeneralSecurityException, IOException, LDAPException {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null);
        TrustAllTrustManager trustAllTrustManager = new TrustAllTrustManager();
        SSLUtil sslUtil = new SSLUtil(trustAllTrustManager);
        SSLSocketFactory socketFactory = sslUtil.createSSLSocketFactory();
        LDAPConnection connection = new LDAPConnection(socketFactory);
        connection.connect(getServer(),getPortNumber());
        connection.bind(getUser(),Password.get());
        SSLSession sslSession = connection.getSSLSession();
        Certificate[] certs =   sslSession.getPeerCertificates();
        Set<X509Certificate> certificates = new HashSet<>();
        for(Certificate certificate: certs)
        {
            if(certificate.getType().equalsIgnoreCase("x.509"))
            {
                certificates.add((X509Certificate)certificate);
            }
        }
        return certificates;
    }

    public void connect(SecureString password) throws LDAPException {

        LDAPConnection c = new LDAPConnection();
        c.connect(Server.get(),getPortNumber());
        BindResult bindResult = c.bind(User.get(),password.toString());
        if(bindResult.getResultCode().equals(ResultCode.SUCCESS))
        {
            _rootDSE = c.getRootDSE();
            if(_rootDSE != null)
            {
                _context = _rootDSE.getNamingContextDNs();
                if(_context != null && _context.length > 0 ) setLdapContext(_context);
            }
            Schema schema = c.getSchema();
            if(schema != null)
            {
                Set<AttributeTypeDefinition> attributeTypes = schema.getAttributeTypes();
                setAttributeDefinition(attributeTypes);
            }
            _ldapConnection =  c;
            SchemaAttributes.addAll(_ldapConnection.getSchema().getAttributeTypes().stream().map(x -> x.getNameOrOID().toLowerCase()).collect(Collectors.toList()));
            OperationalAttributes.addAll(_ldapConnection.getSchema().getOperationalAttributeTypes().stream().map(x->x.getNameOrOID().toLowerCase()).collect(Collectors.toSet()));
        }
        else
        {
            c.close();
        }
    }

    public SearchResultEntry getEntry(String dn) throws LDAPException {
        if(_fileMode)
        {
            if(_fileEntries.get(dn) != null)
            {
                return new SearchResultEntry(_fileEntries.get(dn));
            }
            else
            {
                if(_fileDummies.containsKey(dn)) return new SearchResultEntry(_fileDummies.get(dn));
                return null;
            }
        }
        if(_ldapConnection != null && !_ldapConnection.isConnected()) _ldapConnection.reconnect();
        {
            if(dn == null) return null;
            return _ldapConnection.getEntry(dn);
        }

    }

    public LDAPResult modify(ModifyRequest modifyRequest) throws Exception {
        if(is_readOnly()) throw new Exception("Connection is read only");
        if(_fileMode)
        {
            List<ModifyRequest> requests = new ArrayList<>();
            requests.add(modifyRequest);
            modify(requests);
            return new LDAPResult(0,ResultCode.SUCCESS);

        }
        if(_ldapConnection != null && !_ldapConnection.isConnected()) _ldapConnection.reconnect();
        return _ldapConnection.modify(modifyRequest);

    }

    public List<LDAPResult> modify(List<ModifyRequest> modifyRequests) throws Exception {
        List<LDAPResult> ldapResults = new ArrayList<>();
        if(modifyRequests == null || modifyRequests.isEmpty()) return null;
        if(!_fileMode)
        {
            for(ModifyRequest modifyRequest: modifyRequests )
            {
                ldapResults.add(modify(modifyRequest));
            }
            return ldapResults;
        }
        for(ModifyRequest modifyRequest: modifyRequests )
        {
            Entry fileEntry = _fileEntries.get(modifyRequest.getDN());
            if(fileEntry == null) continue;
            List<Modification> modifications = modifyRequest.getModifications();
            for(Modification modification: modifications)
            {
                if(modification.getModificationType().equals(ModificationType.ADD))
                {
                    Attribute attributeToBeAdded = fileEntry.getAttribute(modification.getAttributeName());
                    if(attributeToBeAdded == null || fileEntry.getAttribute(attributeToBeAdded.getName()) == null)
                    {
                        Attribute attribute = new Attribute(modification.getAttributeName(),modification.getValues());
                         fileEntry.addAttribute(attribute);
                    }
                    else
                    {
                        TreeSet<String> currentValues = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
                        currentValues.addAll(Arrays.stream(fileEntry.getAttributeValues(attributeToBeAdded.getName())).collect(Collectors.toSet()));
                        String newValues[] = modification.getValues();
                        for(String newValue: newValues)
                        {
                            currentValues.add(newValue);
                        }
                        fileEntry.setAttribute(attributeToBeAdded.getName(), new ArrayList<>(currentValues));
                    }
                }
                if(modification.getModificationType().equals(ModificationType.DELETE))
                {
                    Attribute attributesToBeDeleted = fileEntry.getAttribute(modification.getAttributeName());
                    if(attributesToBeDeleted == null || fileEntry.getAttribute(attributesToBeDeleted.getName()) == null)
                    {
                       continue;
                    }
                    else
                    {
                        fileEntry.removeAttribute(modification.getAttributeName());
                    }
                }
                if(modification.getModificationType().equals(ModificationType.REPLACE))
                {
                    Attribute attributesToBeReplaced = fileEntry.getAttribute(modification.getAttributeName());
                    if(fileEntry.getAttribute(attributesToBeReplaced.getName()) == null)
                    {
                        fileEntry.addAttribute(attributesToBeReplaced);
                    }
                    else
                    {
                       Attribute attribute = new Attribute(modification.getAttributeName(),modification.getValues());
                       fileEntry.setAttribute(attribute);
                    }
                }
            }
        }
        updateFile();
        return ldapResults;
    }

   private void updateFile() throws IOException {
       LDIFWriter ldifWriter = new LDIFWriter(_fileName);
       for(String dn: _fileEntries.keySet())
       {
           Entry entry = _fileEntries.get(dn);
           if(entry.getAttribute("TYPE") != null && entry.getAttribute("TYPE").getValue().equals("DUMMY")) continue;
           ldifWriter.writeEntry(_fileEntries.get(dn));
       }
       ldifWriter.flush();
       ldifWriter.close();
   }

    public SearchResult search(SearchRequest searchRequest) throws LDAPSearchException
    {
        if(_fileMode)
        {
            try {
                List<SearchResultEntry> found = searchEntries(searchRequest);
                if(found != null)
                    return new SearchResult(0,ResultCode.SUCCESS,"File OK",searchRequest.getBaseDN(),null,found,null,found.size(),0,null);
                else return null;
            } catch (Exception e) {
                return null;
            }
        }
        else {
            return _ldapConnection.search(searchRequest);
        }
    }

    public List<SearchResultEntry> searchEntries(SearchRequest searchRequest) throws LDAPException, IOException, LDIFException {
       if(_fileMode)
       {
           if(_fileName == null || _fileEntries == null) return null;
           SearchResult found = search(searchRequest.getBaseDN(),searchRequest.getScope(),searchRequest.getFilter(),searchRequest.getAttributes());
           if(found != null && found.getSearchEntries() != null) return found.getSearchEntries();
           return null;
       }
       else
       {
           SearchResult result = _ldapConnection.search(searchRequest);
           if(result != null) return result.getSearchEntries();
           return null;
       }
    }

    public RootDSE getRootDSE() throws LDAPException {

        return _ldapConnection.getRootDSE();
    }

    public Schema getSchema() throws LDAPException {
        return _ldapConnection.getSchema();
    }

    public void refreshEntry(Entry entry) throws Exception {
        if(!_fileMode){throw new Exception("Supported for file mode only");}
        entry.getAttributes().forEach(x->{
            if(!_foundSchemaAttributes.contains(x.getName()))
            {
                _foundSchemaAttributes.add(x.getName());
                SchemaAttributes.add(x.getName());
            }
        });
        if(_fileEntries == null) _fileEntries = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        checkDummy(entry.getDN());
        if(_fileEntries.get(entry.getDN()) != null) return;
        _fileEntries.put(entry.getDN(),entry);
        return;
    }

    private void checkDummy(String dn)
    {
        if(_fileDummies.containsKey(dn))
        {
            _fileDummies.remove(dn);
            return;
        }
        String split[] = dn.split(",");
        for(int j=1; j< split.length; j++)
        {
            StringBuilder dummyDN = new StringBuilder();
            for(int i=j; i< split.length; i++)
            {
                dummyDN.append(split[i]);
                dummyDN.append(",");
            }
            dummyDN.deleteCharAt(dummyDN.length()-1);
            List<Attribute> newAttributes = new ArrayList<>();
            Entry dummyEntry = new Entry(dummyDN.toString(),newAttributes);
            _fileDummies.put(dummyDN.toString(),dummyEntry);
        }
    }

    public LDAPResult add(Entry entry) throws Exception {
       if(is_readOnly() && !_fileMode) throw new Exception("Connection is read only");
       if(_fileMode)
       {
          entry.getAttributes().forEach(x->{
               if(!_foundSchemaAttributes.contains(x.getName()))
               {
                   _foundSchemaAttributes.add(x.getName());
                   SchemaAttributes.add(x.getName());
               }
           });

           if(_fileEntries != null && _fileEntries.get(entry.getDN()) != null)
           {
               throw new Exception("Entry exists allready, can not add");
           }
           if(_fileEntries == null) _fileEntries = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
           _fileEntries.put(entry.getDN(),entry);
           updateFile();
           LDAPResult result = new LDAPResult(0,ResultCode.SUCCESS);
           return result;
       }
       else
       {
           if(isConnected()) return _ldapConnection.add(entry);
           else return null;
       }
    }

    public LDAPResult add(List<Entry> entries) throws Exception {
        if(is_readOnly() && !_fileMode) throw new Exception("Connection is read only");
        if(_fileMode)
        {
            for(Entry entry: entries)
            {
                entry.getAttributes().forEach(x->{
                    if(!_foundSchemaAttributes.contains(x.getName()))
                    {
                        _foundSchemaAttributes.add(x.getName());
                        SchemaAttributes.add(x.getName());
                    }
                });
                if(_fileEntries != null && _fileEntries.get(entry.getDN()) != null) continue;
                if(_fileEntries == null) _fileEntries = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                _fileEntries.put(entry.getDN(),entry);
            }
           updateFile();

            LDAPResult result = new LDAPResult(0,ResultCode.SUCCESS);
            return result;
        }
        else
        {
            for(Entry entry: entries)
            {
                if(isConnected()) add(entry);
            }
            return new LDAPResult(0,ResultCode.SUCCESS);
        }
    }

    public LDAPResult delete(String dn) throws Exception {
        if(is_readOnly() && !_fileMode) throw new Exception("Connection is read only");
        if(_fileMode)
        {
            return delete(new String[]{dn});
        }
        if(isConnected())
        {
            return _ldapConnection.delete(dn);
        }
        return null;
    }

    public LDAPResult delete(String[] DN) throws Exception {
        if(is_readOnly() && !_fileMode) throw new Exception("Connection is read only");
        if(!_fileMode)
        {
            for(String dn:DN) delete(dn);
            return new LDAPResult(0,ResultCode.SUCCESS);
        }
        for(String dn: DN)
        {
            _fileEntries.remove(dn);
        }
        updateFile();
        return new LDAPResult(0,ResultCode.SUCCESS);
    }


    public LDAPResult deleteTree(String dn) throws Exception {
        if(is_readOnly()) throw new Exception("Connection is read only");
        if(_fileMode) throw new Exception("not supported yet");
        DeleteRequest deleteRequest = new DeleteRequest(dn);

       if(!_ldapConnection.isConnected()) {
           try {
               _ldapConnection.reconnect();
           } catch (LDAPException e) {
               e.printStackTrace();
           }
       }
        LDAPResult resultWithoutControl;
        try
        {
            resultWithoutControl = _ldapConnection.delete(deleteRequest);
            if(resultWithoutControl.getResultCode().equals(ResultCode.SUCCESS)) return resultWithoutControl;
        }
        catch (LDAPException le)
        {
            resultWithoutControl = le.toLDAPResult();
            ResultCode resultCode = le.getResultCode();
            String errorMessageFromServer = le.getDiagnosticMessage();
        }
        deleteRequest.addControl(new SubtreeDeleteRequestControl());
        LDAPResult resultWithControl;
        try
        {
            resultWithControl = _ldapConnection.delete(deleteRequest);
        }
        catch (LDAPException le)
        {
            resultWithControl = le.toLDAPResult();
            ResultCode resultCode = le.getResultCode();
            String errorMessageFromServer = le.getDiagnosticMessage();
        }
        LDAPTestUtils.assertResultCodeEquals(resultWithControl, ResultCode.SUCCESS);
        return resultWithControl;
    }

    public SearchResultEntry getEntry(String dn, String... attributes) throws LDAPException {
        if(_fileMode)
        {
            if(_fileEntries.get(dn) != null)
            {
                return new SearchResultEntry(_fileEntries.get(dn));
            }
            else return null;
        }
        else
        {
          if(isConnected()) return _ldapConnection.getEntry(dn,attributes);
          else if(_ldapConnection != null)
          {
              _ldapConnection.reconnect();
              return _ldapConnection.getEntry(dn,attributes);
          }
        }
        return null;
    }

    public SearchResult search(String baseDN, SearchScope scope, Filter filter, String... attributes) throws LDAPSearchException {

        if(_fileMode) return fileSearch(baseDN,scope,filter,attributes);
        return _ldapConnection.search(baseDN,scope,filter,attributes);
    }

    public List<SearchResultEntry> fileSearch(Filter filter) {
        if(_fileMode)
        {
            List<SearchResultEntry> result = new ArrayList<>();
            try {
                for(String entry: _fileEntries.keySet())
                {
                    if(filter.matchesEntry(_fileEntries.get(entry)))
                    {
                        logger.debug("found matching entry",_fileEntries.get(entry));
                        result.add(new SearchResultEntry(_fileEntries.get(entry)));
                    }
                }
            }
            catch (Exception e){logger.error(e);}
            return result;
        }
        else
        {
            return null;
        }
    }

    public SearchResult fileSearch (String baseDN, SearchScope scope, Filter filter, String... attributes)  {
        TreeMap<String,Entry> childEntries = new TreeMap<>();
        List<SearchResultEntry> found = new ArrayList<>();
        List<SearchResultEntry> resultEntries = new ArrayList<>();
        for(String entryDN: _fileEntries.keySet())
        {
            if(entryDN.equalsIgnoreCase(baseDN)) continue;
            if(entryDN.toLowerCase().contains(baseDN.toLowerCase())) childEntries.put(entryDN,_fileEntries.get(entryDN));
        }
        if(scope.equals(SearchScope.SUB)) childEntries.keySet().forEach(x->resultEntries.add(new SearchResultEntry(childEntries.get(x))));
        if(scope.equals(SearchScope.ONE))
        {
            int baseDNLength = baseDN.split(",").length;
            List<String> remove = new ArrayList<>();
            for(String entryDN: childEntries.keySet())
            {
                int entryDNLength = entryDN.split(",").length;
                if(entryDNLength != baseDNLength+1) remove.add(entryDN);
            }
            for(String dn:remove)
            {
                childEntries.remove(dn);
            }
            if(filter != null)
            {
                for(String dn: childEntries.keySet())
                {
                    try {
                        if(filter.matchesEntry(_fileEntries.get(dn)))
                        {
                            resultEntries.add(new SearchResultEntry(childEntries.get(dn)));
                        }
                    } catch (LDAPException e) {
                        logger.error("Error matching entry",e);
                    }
                }
            }
        }
        return new SearchResult(0,ResultCode.SUCCESS,"File OK",baseDN,null,resultEntries,null,resultEntries.size(),0,null);
    }


}
