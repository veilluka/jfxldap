package ch.vilki.jfxldap.backend;

import ch.vilki.secured.SecStorage;
import ch.vilki.secured.SecureProperty;
import ch.vilki.secured.SecureStorageException;
import ch.vilki.secured.SecureString;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.util.*;


public class Config {

    public static String PASSWORD_SET="***";
    public static String PASSWORD_NOT_SET="NO_PASSWORD";

    static Logger logger = LogManager.getLogger(Config.class);
    public Set<String> _allAttributes = new HashSet<String>();
    public Set<String> _ignoreAttributes = new HashSet<String>();
    SecStorage _secStorage = null;
    public Map<String,Connection> _connections = new HashMap<>();
    private String _tempDir;
    private String _beyondCompareExe;
    private String _visualCodeExe;
    private String _lastUsedDirectory;
    private boolean _configSecured = false;
    private String _keyStoreFile = null;
    private KeyStore _keyStore = null;
    private SecureString _keyStorePassword = null;

    public static String getConfigurationFile()
    {
        if(Files.exists(Paths.get(System.getProperty("user.home") + "//fxldap_cfg.properties")))
        {
           return  System.getProperty("user.home") + "//fxldap_cfg.properties";
        }
        else return System.getProperty("user.home") + "//fxldap_cfg.json";
    }


    public SecStorage get_secStorage() {
        return _secStorage;
    }
       public boolean is_configSecured() {
        return _configSecured;
    }
    public KeyStore get_keyStore() {
        return _keyStore;
    }

    public String get_tempDir() {
        if(_tempDir == null) _tempDir = _secStorage.getPropStringValue("general@@_tempDir");
        return _tempDir;
    }

    public void set_tempDir(String _tempDir) {
        this._tempDir = _tempDir;
        try{_secStorage.addUnsecuredProperty("general@@_tempDir",_tempDir);}catch (Exception e)
        {logger.error("Error adding property",e);}
    }

    public String get_beyondCompareExe() {
        if(_beyondCompareExe == null) _beyondCompareExe = _secStorage.getPropStringValue("general@@_beyondCompareExe");
        return _beyondCompareExe;
    }

    public void set_beyondCompareExe(String _beyondCompareExe) {
        this._beyondCompareExe = _beyondCompareExe;
        try{_secStorage.addUnsecuredProperty("general@@_beyondCompareExe",_beyondCompareExe);}
        catch (Exception e){logger.error("Error adding property",e);}
    }
    public String get_visualCodeExe() {
        if(_visualCodeExe == null) _visualCodeExe = _secStorage.getPropStringValue("general@@_visualCodeExe");
        return _visualCodeExe;
    }

    public void set_visualCodeExe(String visualCodeExe) {
        _visualCodeExe = visualCodeExe;
        try{_secStorage.addUnsecuredProperty("general@@_visualCodeExe",_visualCodeExe);}
        catch (Exception e){logger.error("Error adding property",e);}
    }


    public String get_lastUsedDirectory() {
        if(_lastUsedDirectory == null)
        {
            _lastUsedDirectory = _secStorage.getPropStringValue("general@@_lastUsedDirectory");
            if(_lastUsedDirectory != null)
            {
                if(!Files.exists(Paths.get(_lastUsedDirectory)))
                {
                    _lastUsedDirectory = System.getProperty("user.home");
                }
            }
            else
            {
                _lastUsedDirectory = System.getProperty("user.home");
            }
        }
        return _lastUsedDirectory;
    }

    public void set_lastUsedDirectory(String _lastUsedDirectory) {
        this._lastUsedDirectory = _lastUsedDirectory;
        try{_secStorage.addUnsecuredProperty("general@@_lastUsedDirectory",_lastUsedDirectory);}
        catch (Exception e){logger.error("Error adding property",e);}
    }

    public String get_keyStoreFile() {
        return _keyStoreFile;
    }

    public void set_keyStorePassword(SecureString keyStorePassword) {
        this._keyStorePassword = keyStorePassword;
        try{_secStorage.addSecuredProperty("general@@_keyStorePassword",_keyStorePassword);}
        catch (Exception e){logger.error("Error adding property",e);}
    }


    public SecureString get_keyStorePassword()
    {
        return _secStorage.getPropValue("general@@_keyStorePassword");
    }

    public void set_keyStoreFile(String _keyStoreFile) {
        this._keyStoreFile = _keyStoreFile;
        if(_keyStoreFile == null)
            try{_secStorage.deleteProperty("general@@_keystorefile");}
        catch (Exception e){logger.error("Error deleting property",e);}
        else
            try{_secStorage.addUnsecuredProperty("general@@_keystorefile",_keyStoreFile);}
            catch (Exception e){logger.error("Error adding property",e);}
    }

    public String openConfiguration(String fileName) throws Exception {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        if(!Files.exists(Paths.get(fileName))) return Errors.FILE_NOT_FOUND;
        if(!SecStorage.isWindowsSecured(fileName) && SecStorage.isSecured(fileName))
        {
            return Errors.MASTER_PASSWORD_SECURED_ONLY;
        }
        if(!SecStorage.isSecured(fileName))
        {
            _secStorage = SecStorage.open_SecuredStorage(fileName,false);
            _configSecured = false;
        }
        else
        {
            if(!SecStorage.isSecuredWithCurrentUser(fileName))
            {
                return Errors.WINDOWS_NOT_SECURED_WITH_CURRENT_USER;
            }
            else
            {
                _secStorage = SecStorage.open_SecuredStorage(fileName,true);
            }
            _configSecured = true;
        }
         readConnections();
        _keyStoreFile = _secStorage.getPropStringValue("general@@_keystorefile");
        if(_keyStore == null)
        {
            if(_keyStoreFile == null || !Files.exists(Paths.get(_keyStoreFile)))
            {
                set_keyStoreFile(null);
                return "kestore file does not exist";
            }
            if(_configSecured) readKeyStoreFile(null);
            else logger.warn("Keystore file is configured but config is not secured, can not read keystore");
        }
        return null;
    }

    public void readKeyStoreFile(SecureString password) throws IOException, KeyStoreException, CertificateException,
            NoSuchAlgorithmException, SecureStorageException {
        SecureString pass = null;
        if(password != null) pass = password;
        FileInputStream fileInputStream = new FileInputStream(new File(_keyStoreFile));
        _keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        if(pass == null)
        {
            pass = get_keyStorePassword();
            if(pass == null) throw new SecureStorageException("No password provided for keystore and not found in config");
        }
        else
        {
            set_keyStorePassword(pass);
        }
        _keyStore.load(fileInputStream, pass.get_value());
    }

    public void createKeyStoreFile(SecureString password, File file) throws KeyStoreException, CertificateException,
            NoSuchAlgorithmException, IOException {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null);
        FileOutputStream fileOutputStream = new FileOutputStream(file);
        keyStore.store(fileOutputStream,password.get_value());
        password.destroyValue();
        fileOutputStream.close();
    }

    public void saveKeyStoreFile() throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException {
        FileOutputStream fileOutputStream = new FileOutputStream(_keyStoreFile);
        SecureString pass = get_keyStorePassword();
       _keyStore.store(fileOutputStream,pass.get_value());
        pass.destroyValue();
    }

    public void createConfigurationFile(String fileName,String password) throws NoSuchAlgorithmException,
            InvalidKeySpecException, SecureStorageException, IOException {
        if(password == null)
        {
            SecStorage.createNewSecureStorage(fileName,null,false);
        }
        else
        {
            SecStorage.createNewSecureStorage(fileName,new SecureString(password),true);
        }
    }

    public String openConfiguration(String fileName, SecureString masterPassword) throws Exception {
        if(masterPassword == null || masterPassword.get_value() == null)
        {
            _secStorage = SecStorage.open_SecuredStorage(fileName,false);
            readConnections();
            return null;
        }
       _secStorage = SecStorage.open_SecuredStorage(fileName,masterPassword);
       if(masterPassword.get_value() != null)
       {
           if(!SecStorage.isPasswordCorrect(fileName,masterPassword)) return Errors.PASSWORD_ERROR;
           else
           {
               _secStorage = SecStorage.open_SecuredStorage(fileName,masterPassword);
               readConnections();
               _configSecured=true;
               return null;
           }
       }
       else
       {
           _secStorage = SecStorage.open_SecuredStorage(fileName,masterPassword);
           readConnections();
           _configSecured=true;
           return null;
       }
    }

    private void readConnections()
    {
        Connection.set_config(this);
        Set<String> allConnections = _secStorage.getAllChildLabels("connection");
        for(String connectionK: allConnections)
        {
            Map<String, String> map = _secStorage.getAllPropertiesAsMap(connectionK);
            Connection connection = new Connection(this);
            connection.setName(map.get("name"));
            connection.setServer(map.get("server"));
            connection.setPort(map.get("port"));
            connection.setUser(map.get("user"));
            connection.setBaseDN(map.get("basedn"));
            connection.setDisplayAttribute(map.get("displayattribute"));
            connection.setTag(map.get("tag"));
            if(map.containsKey("password") && map.get("password")!=null) connection.setPassword(PASSWORD_SET);
            if(map.get("usejndi") != null && map.get("usejndi").equalsIgnoreCase("true"))
                connection.setUseJNDI(true);
            else connection.setUseJNDI(false);
            if(map.get("readonly") != null && map.get("readonly").equalsIgnoreCase("true"))
                connection.set_readOnly(true);
            else connection.set_readOnly(false);
            if(map.get("ssl") != null && map.get("ssl").equalsIgnoreCase("true")) connection.setSSL(true);
            else connection.setSSL(false);
            _connections.put(connection.getName(),connection);
        }
   }

    public Connection getConnection(String connectionName)
    {
        return _connections.get(connectionName);
    }
    public void saveConnections()
    {
        try
        {
            for(String k: _connections.keySet())
            {
                Connection con = _connections.get(k);
                String label = "connection@@" + con.getName() + "@@";
                _secStorage.addUnsecuredProperty(label +"name",con.getName());
                _secStorage.addUnsecuredProperty(label + "server",con.getServer());
                _secStorage.addUnsecuredProperty(label + "port",con.getPort());
                _secStorage.addUnsecuredProperty(label + "user",con.getUser());
                _secStorage.addUnsecuredProperty(label + "basedn",con.getBaseDN());
                _secStorage.addUnsecuredProperty(label + "displayattribute",con.getDisplayAttribute());
                _secStorage.addUnsecuredProperty(label + "usejndi",String.valueOf(con.isUseJNDI()));
                _secStorage.addUnsecuredProperty(label + "readonly",String.valueOf(con.is_readOnly()));
                _secStorage.addUnsecuredProperty(label + "ssl",String.valueOf(con.isSSL()));
                _secStorage.addUnsecuredProperty(label + "tag",con.getTag());
            }
        }
        catch (Exception e)
        {
            logger.error("Save connections exception occured",e);
        }
    }

    public void deleteConnection(String connName) throws IOException {
        String propKey = "connection@@" + connName;
        List<SecureProperty> allProps = _secStorage.getAllProperties(propKey);
        for(SecureProperty secureProperty: allProps)
        {
            _secStorage.deleteProperty(secureProperty);
        }
     }

    public void updateConnection(Connection con) throws Exception {

        String label = "connection@@" + con.getName() + "@@";
        _secStorage.addUnsecuredProperty(label +"name",con.getName());
        _secStorage.addUnsecuredProperty(label + "server",con.getServer());
        _secStorage.addUnsecuredProperty(label + "port",con.getPort());
        _secStorage.addUnsecuredProperty(label + "user",con.getUser());
        _secStorage.addUnsecuredProperty(label + "basedn",con.getBaseDN());
        _secStorage.addUnsecuredProperty(label + "displayattribute",con.getDisplayAttribute());
        _secStorage.addUnsecuredProperty(label + "usejndi",String.valueOf(con.isUseJNDI()));
        _secStorage.addUnsecuredProperty(label + "readonly",String.valueOf(con.is_readOnly()));
        _secStorage.addUnsecuredProperty(label + "readonly",String.valueOf(con.isSSL()));
        _secStorage.addUnsecuredProperty(label + "tag",con.getTag());


    }
    public SecureString getConnectionPassword(Connection con)  {

        try {
            SecureString secureString = _secStorage.getPropValue("connection@@" + con.getName() + "@@password");
            return secureString;
        } catch (Exception e) {
            logger.error("error reading password",e);
            return null;
        }
    }

    public void updatePassword(Connection con,SecureString password) throws SecureStorageException {
        if(_configSecured) _secStorage.addSecuredProperty("connection@@" + con.getName() + "@@password",password);
        else _secStorage.addUnsecuredProperty("connection@@" + con.getName() + "@@password",password.toString());
    }

}
