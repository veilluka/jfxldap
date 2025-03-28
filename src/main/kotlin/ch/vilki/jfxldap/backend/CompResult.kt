package ch.vilki.jfxldap.backend

import com.unboundid.ldap.sdk.*
import java.util.*
import java.util.regex.Pattern
import java.util.stream.Collectors

class KCompResult(entry: SearchResultEntry?) : java.io.Serializable, Comparable<KCompResult>, Cloneable {
    enum class ENTRY_TYPE {
        SOURCE, TARGET, BOTH
    }

    enum class COMPARE_RESULT {
        ENTRY_EQUAL,
        ENTRY_NOT_EQUAL,
        ONLY_IN_SOURCE,
        ONLY_IN_TARGET,
        ENTRY_EQUAL_BUT_CHILDREN_NOT,
        COMPARE_ERROR
    }

    private var _compare_result: COMPARE_RESULT? = null
    private var _dn: String? = null
    private var _parent_DN: String? = null
    private lateinit var _entry_type: ENTRY_TYPE
    private var _entryEqual = false
    private var _rdnDisplay: String? = null
    private var _modificationsToTarget: MutableList<Modification>? = null
    private var _modificationsToSource: MutableList<Modification>? = null
    private var _childrenNotEqual: Boolean? = null
    private var _allValues: HashMap<String, MutableList<String>>? = null
    
    var _sourceEntry: SearchResultEntry? = null
        private set
    
    var _targetEntry: SearchResultEntry? = null
        set(value) {
            field = value
            if (_dn == null && value != null) _dn = value.dn
        }
    
    init {
        if (entry != null) _dn = entry.dn
        _sourceEntry = entry
    }

    override fun compareTo(other: KCompResult): Int {
        return getRDN().compareTo(other.getRDN())
    }

    fun get_compare_result(): COMPARE_RESULT {
        return _compare_result ?: COMPARE_RESULT.COMPARE_ERROR
    }

    fun set_compare_result(compareResult: COMPARE_RESULT) {
        _compare_result = compareResult
    }

    fun getParent_DN(): String? {
        return _parent_DN
    }

    fun setParent_DN(parentDN: String) {
        _parent_DN = parentDN
    }

    fun set_dn(dn: String) {
        _dn = dn
    }

    fun get_dn(): String? {
        return _dn
    }

    fun get_rdnDisplay(): String? {
        return _rdnDisplay
    }

    fun set_rdnDisplay(rdnDisplay: String) {
        _rdnDisplay = rdnDisplay
    }

    fun get_childrenNotEqual(): Boolean? {
        return _childrenNotEqual
    }

    fun set_childrenNotEqual(childrenNotEqual: Boolean?) {
        _childrenNotEqual = childrenNotEqual
    }

    fun get_ModificationsToTarget(): List<Modification>? {
        return _modificationsToTarget
    }

    fun get_ModificationsToSource(): List<Modification>? {
        return _modificationsToSource
    }

    fun is_entryEqual(): Boolean {
        return _entryEqual
    }

    fun set_entryEqual(entryEqual: Boolean) {
        _entryEqual = entryEqual
    }

    fun get_entry_type(): ENTRY_TYPE {
        return _entry_type
    }

    fun set_entry_type(entryType: ENTRY_TYPE) {
        _entry_type = entryType
    }

    fun getAttributeValues(attributeName: String): List<String>? {
        return _allValues?.get(attributeName)
    }

    override fun clone(): KCompResult {
        val compResult = KCompResult(_sourceEntry)
        compResult._targetEntry = _targetEntry
        compResult._childrenNotEqual = _childrenNotEqual
        compResult.set_compare_result(get_compare_result())
        compResult._dn = _dn
        compResult._parent_DN = _parent_DN
        compResult._entry_type = _entry_type
        compResult._entryEqual = _entryEqual
        compResult._rdnDisplay = _rdnDisplay
        
        if (_modificationsToTarget != null) {
            compResult._modificationsToTarget = ArrayList()
            compResult._modificationsToTarget!!.addAll(_modificationsToTarget!!)
        }
        
        if (_modificationsToSource != null) {
            compResult._modificationsToSource = ArrayList()
            compResult._modificationsToSource!!.addAll(_modificationsToSource!!)
        }
        
        if (_allValues != null) {
            compResult._allValues = HashMap()
            compResult._allValues!!.putAll(_allValues!!)
        }
        
        return compResult
    }

    fun addAttributeValue(attributeName: String, value: String) {
        if (_allValues == null) _allValues = HashMap()
        
        if (_allValues!!.containsKey(attributeName)) {
            val values = _allValues!![attributeName]
            values!!.add(value)
            _allValues!![attributeName] = values
        } else {
            val values = ArrayList<String>()
            values.add(value)
            _allValues!![attributeName] = values
        }
    }

    private fun addAttribute(attributeName: String, values: Array<String>?) {
        if (values == null || values.isEmpty()) return
        for (v in values) {
            addAttributeValue(attributeName, v)
        }
    }

    fun setAttributes(attributes: Collection<Attribute>) {
        for (at in attributes) {
            addAttribute(at.name, at.values)
        }
    }

    fun findValue(value: String): List<Modification>? {
        val modifications = ArrayList<Modification>()
        
        _allValues?.let { allValues ->
            for (attName in allValues.keys) {
                val values = allValues[attName]
                values?.let { valuesList ->
                    for (val1 in valuesList) {
                        val sameValues = ArrayList<String>()
                        if (val1.contains(value)) {
                            sameValues.add(val1)
                        }
                        if (sameValues.isNotEmpty()) {
                            val storeValues = sameValues.toTypedArray()
                            val mod = Modification(ModificationType.ADD, attName, *storeValues)
                            modifications.add(mod)
                        }
                    }
                }
            }
        }
        
        return if (modifications.isEmpty()) null else modifications
    }

    fun resolveCompareResult(childResult: COMPARE_RESULT?) {
        if (childResult == null) {
            if (is_entryEqual()) {
                set_compare_result(COMPARE_RESULT.ENTRY_EQUAL)
            } else {
                when {
                    get_entry_type() == ENTRY_TYPE.SOURCE -> set_compare_result(COMPARE_RESULT.ONLY_IN_SOURCE)
                    get_entry_type() == ENTRY_TYPE.TARGET -> set_compare_result(COMPARE_RESULT.ONLY_IN_TARGET)
                    else -> set_compare_result(COMPARE_RESULT.ENTRY_NOT_EQUAL)
                }
            }
            return
        }
        
        if (_compare_result == null) {
            if (is_entryEqual()) {
                if (childResult == COMPARE_RESULT.ENTRY_EQUAL) {
                    set_compare_result(COMPARE_RESULT.ENTRY_EQUAL)
                } else {
                    set_compare_result(COMPARE_RESULT.ENTRY_EQUAL_BUT_CHILDREN_NOT)
                }
            } else {
                when {
                    get_entry_type() == ENTRY_TYPE.SOURCE -> set_compare_result(COMPARE_RESULT.ONLY_IN_SOURCE)
                    get_entry_type() == ENTRY_TYPE.TARGET -> set_compare_result(COMPARE_RESULT.ONLY_IN_TARGET)
                    else -> set_compare_result(COMPARE_RESULT.ENTRY_NOT_EQUAL)
                }
            }
        } else {
            if (_compare_result == COMPARE_RESULT.ENTRY_EQUAL) {
                if (childResult != COMPARE_RESULT.ENTRY_EQUAL) {
                    set_compare_result(COMPARE_RESULT.ENTRY_EQUAL_BUT_CHILDREN_NOT)
                }
            }
        }
    }

    fun set_ModificationsToTarget(modifications: MutableList<Modification>?) {
        _modificationsToTarget = modifications
        val modToSource = ArrayList<Modification>()
        
        _modificationsToTarget?.let { mods ->
            if (mods.isNotEmpty()) {
                for (m in mods) {
                    when (m.modificationType) {
                        ModificationType.ADD -> {
                            val mNew = Modification(ModificationType.DELETE, m.attributeName, *m.values)
                            modToSource.add(mNew)
                        }
                        ModificationType.DELETE -> {
                            val mNew = Modification(ModificationType.ADD, m.attributeName, *m.values)
                            modToSource.add(mNew)
                        }
                        else -> {}
                    }
                }
            }
        }
        
        _modificationsToSource = modToSource
    }

    fun get_ModificationsToTarget(attributeName: String): List<Modification> {
        val returnValue = ArrayList<Modification>()
        
        _modificationsToTarget?.let { mods ->
            if (mods.isNotEmpty()) {
                for (m in mods) {
                    if (m.attributeName.equals(attributeName, ignoreCase = true)) returnValue.add(m)
                }
            }
        }
        
        return returnValue
    }

    fun get_ModificationsToTarget(attributes: List<String>): List<Modification> {
        val returnValue = ArrayList<Modification>()
        val atts = attributes.map { it.lowercase() }.toCollection(HashSet())
        
        _modificationsToTarget?.let { mods ->
            if (mods.isNotEmpty()) {
                for (m in mods) {
                    if (atts.contains(m.attributeName.lowercase())) returnValue.add(m)
                }
            }
        }
        
        return returnValue
    }

    fun get_ModificationsToSource(attributes: List<String>): List<Modification> {
        val returnValue = ArrayList<Modification>()
        val atts = attributes.map { it.lowercase() }.toCollection(HashSet())
        
        _modificationsToSource?.let { mods ->
            if (mods.isNotEmpty()) {
                for (m in mods) {
                    if (atts.contains(m.attributeName.lowercase())) returnValue.add(m)
                }
            }
        }
        
        return returnValue
    }

    fun getRDN(): String {
        _rdnDisplay?.let {
            return it
        }
        
        if (!get_dn().isNullOrEmpty()) {
            val split = get_dn()!!.split(",").toTypedArray()
            return if (split.isEmpty()) {
                "COULD NOT PARSE RDN 1 "
            } else {
                split[0]
            }
        } else {
            return "COULD NOT PARSE RDN 2"
        }
    }

    /**
     * @return Set of attribute names that have differences
     */
    fun getDifferentAttributes(): TreeSet<String> {
        val diffs = TreeSet<String>(String.CASE_INSENSITIVE_ORDER)
        
        _modificationsToTarget?.let { mods ->
            for (mod in mods) {
                val attName = mod.attributeName
                diffs.add(attName)
            }
        }
        
        return diffs
    }

    companion object {
        fun stringContains(sourceValue: String?, targetValue: String?, exact: Boolean, matchCase: Boolean): Boolean {
            val src = sourceValue ?: ""
            val tgt = targetValue ?: ""
            
            val srcFinal = if (!matchCase) src.lowercase() else src
            val tgtFinal = if (!matchCase) tgt.lowercase() else tgt
            
            if (!exact) {
                return srcFinal.contains(tgtFinal)
            } else {
                val pattern = "\\b$tgtFinal\\b"
                val p = Pattern.compile(pattern)
                val m = p.matcher(srcFinal)
                return m.find()
            }
        }
    }

    override fun toString(): String {
        return getRDN()
    }

    fun print(): String {
        val stringBuilder = StringBuilder()
        stringBuilder.append("DN=$_dn\n")
        stringBuilder.append("RDN=${getRDN()}\n")
        stringBuilder.append("ENTRY TYPE$_entry_type\n")
        stringBuilder.append("ENTRY EQUAL=$_entryEqual\n")
        stringBuilder.append("CHILDREN NOT EQUAL=$_childrenNotEqual\n")
        stringBuilder.append("COMPARE RESULT=$_compare_result\n")
        
        return stringBuilder.toString()
    }
}
