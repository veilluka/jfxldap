package ch.vilki.jfxldap.backend

import com.unboundid.ldap.sdk.LDAPConnection
import com.unboundid.ldap.sdk.RootDSE
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.util.*




class LDAPReader {

    var logger: Logger = LogManager.getLogger(LDAPReader::class.java)
    enum class READ_ATTRIBUTES {
        none,
        only_operational,
        only_user,
        all
    }



    private val _ldapConnection: LDAPConnection? = null
    private val _rootDSE: RootDSE? = null
    private val _fileMode = false
    var _context: Array<String>? = null
    private val _fileName: String? = null
    private val _foundSchemaAttributes = TreeSet(java.lang.String.CASE_INSENSITIVE_ORDER)
    private val _readOnly = false
    private val displayAttribute: Array<String>? = null
    private var _returningAttributes: Array<String>? = null


    fun setReadAttributes(readAttributes: READ_ATTRIBUTES?) {
        when (readAttributes) {
            READ_ATTRIBUTES.none -> {
                if (displayAttribute != null) _returningAttributes =
                    arrayOf(displayAttribute.get(0)) else _returningAttributes =
                    arrayOf("cn", "objectclass")
                    logger.debug("Set read attributes none")
            }
            READ_ATTRIBUTES.all -> {
                _returningAttributes = arrayOf<String>("*", "+")
                logger.debug("Set read attributes all")
            }
            READ_ATTRIBUTES.only_user -> {
                _returningAttributes = arrayOf<String>("*")
                logger.debug("Set read attributes only user")
            }
            READ_ATTRIBUTES.only_operational -> {
                _returningAttributes = arrayOf<String>("+")
                logger.debug("Set read attributes only operational")
            }
            else -> {
                _returningAttributes = arrayOf<String>("*", "+")
                logger.debug("Set read attributes ALL")
            }
        }
    }

    fun is_fileMode(): Boolean {
        return _fileMode
    }

    fun isConnected(): Boolean {
        _ldapConnection?.let {
            if (!it.isConnected) {
                it.reconnect()
            }
            return it.isConnected
        }
        if(is_fileMode()) return true
        return false
    }



}