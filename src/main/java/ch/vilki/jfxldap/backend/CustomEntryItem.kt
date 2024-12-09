package ch.vilki.jfxldap.backend

import com.unboundid.ldap.sdk.Attribute
import com.unboundid.ldap.sdk.Entry
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import org.apache.logging.log4j.LogManager
import java.util.*
import javax.swing.text.View

open class CustomEntryItem : Comparable<CustomEntryItem> {

    enum class ViewStyle(var style: String) {
        NEW(" -fx-text-fill: #222222;"),
        GREYED_OUT(" -fx-text-fill: #666666;"),
        ERROR("-fx-text-fill: red;"),
        PERSON("-fx-text-fill: #666666;"),
        PARENT(" -fx-text-fill: #d60e0e;"),
        PLACE_HOLDER("-fx-text-fill: red;"),
    }

    private var _dummy = false
    private var _nrOfChildren = 0
    var _hasChildren = false

    var _objectClass: MutableSet<String> = HashSet<String>().apply { add("top") }
    fun is_dummy(): Boolean {
        return _dummy
    }
    fun setDummy(){
        _dummy=true

        setStyleProperty(ViewStyle.PLACE_HOLDER)
    }

    fun getEntry(): Entry? {
        _entry.get()?.let { return it as Entry  }
       return null
    }

    fun setEntry(entry:Entry) = _entry.set(entry)

    @JvmField
    var _rdn: SimpleStringProperty = SimpleStringProperty()
    var _entry = SimpleObjectProperty<Any>()
    var _dn: SimpleStringProperty = SimpleStringProperty()
    var _styleProperty: SimpleStringProperty = SimpleStringProperty(ViewStyle.NEW.style)
    fun setStyleProperty(viewStyle: ViewStyle) {
        _styleProperty = SimpleStringProperty(viewStyle.style)
    }

    fun getDn(): String {
        if(_dn.get() == null) {
           return   "INVALID DN"
        }
        else return _dn.get()
    }

    fun getRdn() = _rdn.get()
    fun setRdn(value:String) = _rdn.set(value)


    fun readAllAttributes(connection: Connection) {
        _entry.get()?.let { entry->
            entry as Entry
            connection.getEntry(entry.dn,*arrayOf("*", "+"))?.let { refreshed->
                _entry.set(refreshed)
            }
        }
    }

    override fun compareTo(o: CustomEntryItem): Int {
        if(_hasChildren && !o._hasChildren) return -1
        if(!_hasChildren && o._hasChildren) return 1
        try {
            return _rdn.get().lowercase(Locale.getDefault()).compareTo(o._rdn.get().lowercase(Locale.getDefault()))
        } catch (e: Exception) {
            logger.error(
                "Exception occured during compareOperation, this rdn->" + _rdn!!.get() + " other->" + o._rdn.get(),
                e
            )
        }
        return 0
    }

    constructor()

    constructor(entry: Entry?) {
        var dn: String? = "ENTRY_NULL"
        var rdn = "ENTRY_NULL"
        entry?.let { entryLdap->
            entry.objectClassAttribute?.values?.forEach {
                _objectClass.add(it.lowercase(Locale.getDefault()))
            }
            dn = entryLdap.dn
            rdn = entryLdap.rdn?.toString() ?: "ENTRY NULL"
        }
        _dn.set(dn)
        _entry.set(entry)
        _rdn.set(rdn)
        _styleProperty.set(if(entry!=null) ViewStyle.NEW.style else ViewStyle.ERROR.style)
    }

    constructor(dn: String?, rdn: String?, attributes: List<Attribute>) {

        dn?.let { _dn.set(it) }
        rdn?.let { _rdn.set(it) }
        attributes.find { it.name.equals("objectclass",true) }.let { att->
             att?.values?.let { values->
                 _objectClass.addAll(values)
             }
        }
        val entry = Entry(dn, attributes)
        _entry.set(entry)
        _styleProperty.set(ViewStyle.NEW.style)

    }

    constructor(cn:String){
        _rdn.set(cn)
    }

    fun setDisplayAttribute(attributeName: String?) {
        if (attributeName == null || is_dummy()) return

        _entry.get()?.let { entry->
            entry as Entry
            entry.getAttribute(attributeName)?.let { value->
                _rdn.set(value.value)
                return
            }
        }
        logger.error("could not set display attribute for $attributeName and $_entry")
    }

    override fun toString(): String {
        if (_rdn.get() != null) {
            return if (_nrOfChildren > 0) _rdn.get() + "[" + _nrOfChildren + "]" else _rdn.get()
        }
        if (_dn.get() != null) return _dn.get()
        if (_entry.get() != null) {
            val entry = _entry.get() as Entry
            try {
                if (entry.rdn != null) {
                    return if (_nrOfChildren > 0) entry.rdn.toString() + "[" + _nrOfChildren + "]" else entry.rdn.toString()
                }
            } catch (e: Exception) {
            }
            if (entry.dn != null) return entry.dn
        }
        return this.toString()
    }

    fun splitDN(): Array<String> {
        return _dn.get().split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    }

    companion object {
        var logger = LogManager.getLogger(CustomEntryItem::class.java)

        public fun resolveDN(dn:String): List<String> {
            return dn.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toList()
        }

        fun getAllParentsDN(dn: String): List<String> {
            val components = dn.split(",")
            return components.indices.map { i ->
                components.subList(i, components.size).joinToString(",")
            }
        }
    }
}
