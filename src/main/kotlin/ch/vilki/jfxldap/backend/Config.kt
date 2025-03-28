package ch.vilki.jfxldap.backend

import ch.vilki.secured.GuiHelper
import ch.vilki.secured.SecStorage
import ch.vilki.secured.SecureStorageException
import ch.vilki.secured.SecureString
import org.apache.logging.log4j.LogManager
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.Security
import java.security.cert.CertificateException
import java.security.spec.InvalidKeySpecException

class Config {
    @JvmField
    var _allAttributes: Set<String> = HashSet()
    var _ignoreAttributes: Set<String> = HashSet()
    var _secStorage: SecStorage? = null
    @JvmField
    var _connections: MutableMap<String, Connection> = HashMap()
    private var _tempDir: String? = null
    private var _beyondCompareExe: String? = null
    private var _visualCodeExe: String? = null
    private var _lastUsedDirectory: String? = null
    private var _configSecured = false
    private var _keyStoreFile: String? = null
    private var _keyStore: KeyStore? = null
    private var _keyStorePassword: SecureString? = null

    fun is_configSecured(): Boolean {
        return _configSecured
    }

    fun get_keyStore(): KeyStore? {
        return _keyStore
    }

    fun get_tempDir(): String? {
        if (_tempDir == null) _tempDir = _secStorage!!.getPropStringValue("general@@_tempDir")
        return _tempDir
    }

    fun set_tempDir(_tempDir: String?) {
        this._tempDir = _tempDir
        try {
            _secStorage!!.addUnsecuredProperty("general@@_tempDir", _tempDir)
        } catch (e: Exception) {
            logger.error("Error adding property", e)
        }
    }

    fun get_beyondCompareExe(): String? {
        if (_beyondCompareExe == null) _beyondCompareExe =
            _secStorage!!.getPropStringValue("general@@_beyondCompareExe")
        return _beyondCompareExe
    }

    fun set_beyondCompareExe(_beyondCompareExe: String?) {
        this._beyondCompareExe = _beyondCompareExe
        try {
            _secStorage!!.addUnsecuredProperty("general@@_beyondCompareExe", _beyondCompareExe)
        } catch (e: Exception) {
            logger.error("Error adding property", e)
        }
    }

    fun get_visualCodeExe(): String? {
        if (_visualCodeExe == null) _visualCodeExe = _secStorage!!.getPropStringValue("general@@_visualCodeExe")
        return _visualCodeExe
    }

    fun set_visualCodeExe(visualCodeExe: String?) {
        _visualCodeExe = visualCodeExe
        try {
            _secStorage!!.addUnsecuredProperty("general@@_visualCodeExe", _visualCodeExe)
        } catch (e: Exception) {
            logger.error("Error adding property", e)
        }
    }

    fun get_lastUsedDirectory(): String? {
        if (_lastUsedDirectory == null) {
            _lastUsedDirectory = _secStorage!!.getPropStringValue("general@@_lastUsedDirectory")
            if (_lastUsedDirectory != null) {
                if (!Files.exists(Paths.get(_lastUsedDirectory))) {
                    _lastUsedDirectory = System.getProperty("user.home")
                }
            } else {
                _lastUsedDirectory = System.getProperty("user.home")
            }
        }
        return _lastUsedDirectory
    }

    fun set_lastUsedDirectory(_lastUsedDirectory: String?) {
        this._lastUsedDirectory = _lastUsedDirectory
        try {
            _secStorage!!.addUnsecuredProperty("general@@_lastUsedDirectory", _lastUsedDirectory)
        } catch (e: Exception) {
            logger.error("Error adding property", e)
        }
    }

    fun get_keyStoreFile(): String? {
        return _keyStoreFile
    }

    fun set_keyStorePassword(keyStorePassword: SecureString?) {
        _keyStorePassword = keyStorePassword
        try {
            _secStorage!!.addSecuredProperty("general@@_keyStorePassword", _keyStorePassword)
        } catch (e: Exception) {
            logger.error("Error adding property", e)
        }
    }

    fun get_keyStorePassword(): SecureString {
        return _secStorage!!.getPropValue("general@@_keyStorePassword")
    }

    fun set_keyStoreFile(_keyStoreFile: String?) {
        this._keyStoreFile = _keyStoreFile
        if (_keyStoreFile == null) try {
            _secStorage!!.deleteProperty("general@@_keystorefile")
        } catch (e: Exception) {
            logger.error("Error deleting property", e)
        } else try {
            _secStorage!!.addUnsecuredProperty("general@@_keystorefile", _keyStoreFile)
        } catch (e: Exception) {
            logger.error("Error adding property", e)
        }
    }

    @Throws(Exception::class)
    fun openConfiguration(fileName: String?): String? {
        Security.addProvider(BouncyCastleProvider())
        if (!Files.exists(Paths.get(fileName))) return Errors.FILE_NOT_FOUND
        if (!SecStorage.isWindowsSecured(fileName) && SecStorage.isSecured(fileName)) {
            return Errors.MASTER_PASSWORD_SECURED_ONLY
        }
        if (!SecStorage.isSecured(fileName)) {
            _secStorage = SecStorage.open_SecuredStorage(fileName, false)
            _configSecured = false
        } else {
            _secStorage = if (!SecStorage.isSecuredWithCurrentUser(fileName)) {
                return Errors.WINDOWS_NOT_SECURED_WITH_CURRENT_USER
            } else {
                SecStorage.open_SecuredStorage(fileName, true)
            }
            _configSecured = true
        }
        readConnections()
        _keyStoreFile = _secStorage!!.getPropStringValue("general@@_keystorefile")
        if(_keyStoreFile==null) return "keystore file not set"
        if(!Files.exists(Paths.get(_keyStoreFile))){
            GuiHelper.ERROR("Keystore file not found","Keystore file does not exist, will be deleted from settings")
            _secStorage!!.deleteProperty("general@@_keystorefile")
             set_keyStoreFile(null)
        }
        else{
            if (_configSecured) readKeyStoreFile(null) else logger.warn("Keystore file is configured but config is not secured, can not read keystore")
        }
        return null
    }

    @Throws(
        IOException::class,
        KeyStoreException::class,
        CertificateException::class,
        NoSuchAlgorithmException::class,
        SecureStorageException::class
    )
    fun readKeyStoreFile(password: SecureString?) {
        var pass: SecureString? = null
        if (password != null) pass = password
        val fileInputStream = FileInputStream(File(_keyStoreFile))
        _keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        if (pass == null) {
            pass = get_keyStorePassword()
            if (pass == null) throw SecureStorageException("No password provided for keystore and not found in config")
        } else {
            set_keyStorePassword(pass)
        }
        _keyStore!!.load(fileInputStream, pass._value)
    }

    @Throws(KeyStoreException::class, CertificateException::class, NoSuchAlgorithmException::class, IOException::class)
    fun createKeyStoreFile(password: SecureString, file: File?) {
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null)
        val fileOutputStream = FileOutputStream(file)
        keyStore.store(fileOutputStream, password._value)
        password.destroyValue()
        fileOutputStream.close()
    }

    @Throws(IOException::class, CertificateException::class, NoSuchAlgorithmException::class, KeyStoreException::class)
    fun saveKeyStoreFile() {
        val fileOutputStream = FileOutputStream(_keyStoreFile)
        val pass = get_keyStorePassword()
        _keyStore!!.store(fileOutputStream, pass._value)
        pass.destroyValue()
    }

    @Throws(
        NoSuchAlgorithmException::class,
        InvalidKeySpecException::class,
        SecureStorageException::class,
        IOException::class
    )
    fun createConfigurationFile(fileName: String?, password: String?) {
        if (password == null) {
            SecStorage.createNewSecureStorage(fileName, null, false)
        } else {
            SecStorage.createNewSecureStorage(fileName, SecureString(password), true)
        }
    }

    @Throws(Exception::class)
    fun openConfiguration(fileName: String?, masterPassword: SecureString?): String? {
        if (masterPassword == null || masterPassword._value == null) {
            _secStorage = SecStorage.open_SecuredStorage(fileName, false)
            readConnections()
            return null
        }
        _secStorage = SecStorage.open_SecuredStorage(fileName, masterPassword)
        return if (masterPassword._value != null) {
            if (!SecStorage.isPasswordCorrect(
                    fileName,
                    masterPassword
                )
            ) Errors.PASSWORD_ERROR else {
                _secStorage = SecStorage.open_SecuredStorage(fileName, masterPassword)
                readConnections()
                _configSecured = true
                null
            }
        } else {
            _secStorage = SecStorage.open_SecuredStorage(fileName, masterPassword)
            readConnections()
            _configSecured = true
            null
        }
    }

    private fun readConnections() {
        Connection.set_config(this)
        val allConnections = _secStorage!!.getAllChildLabels("connection")
        for (connectionK in allConnections) {
            val map = _secStorage!!.getAllPropertiesAsMap(connectionK)
            val connection = Connection(this)
            connection.name = map["name"]
            connection.server = map["server"]
            connection.port = map["port"]
            connection.user = map["user"]
            connection.baseDN = map["basedn"]
            connection.displayAttribute = map["displayattribute"]
            connection.tag = map["tag"]
            if (map.containsKey("password") && map["password"] != null) connection.password = PASSWORD_SET
            if (map["usejndi"] != null && map["usejndi"].equals("true", ignoreCase = true)) connection.isUseJNDI =
                true else connection.isUseJNDI = false
            if (map["readonly"] != null && map["readonly"].equals("true", ignoreCase = true)) connection.is_readOnly =
                true else connection.is_readOnly = false
            if (map["ssl"] != null && map["ssl"].equals("true", ignoreCase = true)) connection.isSSL =
                true else connection.isSSL = false
            _connections[connection.name] = connection
        }
    }

    fun getConnection(connectionName: String): Connection? {
        return _connections[connectionName]
    }

    fun saveConnections() {
        try {
            for (k in _connections.keys) {
                val con = _connections[k]
                val label = "connection@@" + con!!.name + "@@"
                _secStorage!!.addUnsecuredProperty(label + "name", con.name)
                _secStorage!!.addUnsecuredProperty(label + "server", con.server)
                _secStorage!!.addUnsecuredProperty(label + "port", con.port)
                _secStorage!!.addUnsecuredProperty(label + "user", con.user)
                _secStorage!!.addUnsecuredProperty(label + "basedn", con.baseDN)
                _secStorage!!.addUnsecuredProperty(label + "displayattribute", con.displayAttribute)
                _secStorage!!.addUnsecuredProperty(label + "usejndi", con.isUseJNDI.toString())
                _secStorage!!.addUnsecuredProperty(label + "readonly", con.is_readOnly.toString())
                _secStorage!!.addUnsecuredProperty(label + "ssl", con.isSSL.toString())
                _secStorage!!.addUnsecuredProperty(label + "tag", con.tag)
            }
        } catch (e: Exception) {
            logger.error("Save connections exception occured", e)
        }
    }

    @Throws(IOException::class)
    fun deleteConnection(connName: String) {
        val propKey = "connection@@$connName"
        val allProps = _secStorage!!.getAllProperties(propKey)
        for (secureProperty in allProps) {
            _secStorage!!.deleteProperty(secureProperty)
        }
    }

    @Throws(Exception::class)
    fun updateConnection(con: Connection) {
        val label = "connection@@" + con.name + "@@"
        _secStorage!!.addUnsecuredProperty(label + "name", con.name)
        _secStorage!!.addUnsecuredProperty(label + "server", con.server)
        _secStorage!!.addUnsecuredProperty(label + "port", con.port)
        _secStorage!!.addUnsecuredProperty(label + "user", con.user)
        _secStorage!!.addUnsecuredProperty(label + "basedn", con.baseDN)
        _secStorage!!.addUnsecuredProperty(label + "displayattribute", con.displayAttribute)
        _secStorage!!.addUnsecuredProperty(label + "usejndi", con.isUseJNDI.toString())
        _secStorage!!.addUnsecuredProperty(label + "readonly", con.is_readOnly.toString())
        _secStorage!!.addUnsecuredProperty(label + "readonly", con.isSSL.toString())
        _secStorage!!.addUnsecuredProperty(label + "tag", con.tag)
    }

    fun getConnectionPassword(con: Connection): SecureString? {
        return try {
            _secStorage!!.getPropValue("connection@@" + con.name + "@@password")
        } catch (e: Exception) {
            logger.error("error reading password", e)
            null
        }
    }

    @Throws(SecureStorageException::class)
    fun updatePassword(con: Connection, password: SecureString) {
        if (_configSecured) _secStorage!!.addSecuredProperty(
            "connection@@" + con.name + "@@password",
            password
        ) else _secStorage!!.addUnsecuredProperty("connection@@" + con.name + "@@password", password.toString())
    }

    companion object {
        @JvmField
        var PASSWORD_SET = "***"
        @JvmField
        var PASSWORD_NOT_SET = "NO_PASSWORD"
        var logger = LogManager.getLogger(Config::class.java)
        @JvmStatic
        val configurationFile: String
            get() = if (Files.exists(Paths.get(System.getProperty("user.home") + "//fxldap_cfg.properties"))) {
                System.getProperty("user.home") + "//fxldap_cfg.properties"
            } else System.getProperty("user.home") + "//fxldap_cfg.json"
    }
}
