package ch.vilki.jfxldap.backend

import com.unboundid.ldap.sdk.ModificationType
import javafx.scene.control.TreeItem
import java.util.*

class AttributeDifference {
    var attributeName: String? = null
    private var _dn: String? = null
    private var _sourceEntryValue: String? = null
    private var _targetEntryValue: String? = null

    fun get_dn(): String? {
        return _dn
    }

    fun set_dn(_dn: String?) {
        this._dn = _dn
    }

    fun get_sourceEntryValue(): String? {
        return _sourceEntryValue
    }

    fun set_sourceEntryValue(_sourceEntryValue: String?) {
        this._sourceEntryValue = _sourceEntryValue
    }

    fun get_targetEntryValue(): String? {
        return _targetEntryValue
    }

    fun set_targetEntryValue(_targetEntryValue: String?) {
        this._targetEntryValue = _targetEntryValue
    }

    fun getAttributeDifferences(attributeName: String?, treeItem: TreeItem<CompResult>): List<AttributeDifference> {
        val diffs: MutableList<AttributeDifference> = ArrayList()
        val modifications = treeItem.value._ModificationsToTarget ?: return diffs
        val sourceValues = ArrayList<String>()
        val targetValues = ArrayList<String>()
        for (m in modifications) {
            if (!m.attributeName.equals(attributeName, ignoreCase = true)) continue
            if (m.modificationType == ModificationType.ADD) {
                for (`val` in m.attribute.values) {
                    sourceValues.add(`val`)
                }
            } else if (m.modificationType == ModificationType.DELETE) {
                for (`val` in m.attribute.values) {
                    targetValues.add(`val`)
                }
            }
        }
        var maxElements = 0
        if (sourceValues.size > targetValues.size) {
            maxElements = sourceValues.size
            for (i in 0 until maxElements) {
                val attributeDifference = AttributeDifference()
                attributeDifference._sourceEntryValue = sourceValues[i]
                if (targetValues.size > i) attributeDifference._targetEntryValue = targetValues[i]
                attributeDifference.attributeName = attributeName
                diffs.add(attributeDifference)
            }
        } else if (sourceValues.size < targetValues.size) {
            maxElements = targetValues.size
            for (i in 0 until maxElements) {
                val attributeDifference = AttributeDifference()
                attributeDifference._targetEntryValue = targetValues[i]
                if (sourceValues.size > i) attributeDifference._sourceEntryValue = sourceValues[i]
                attributeDifference.attributeName = attributeName
                diffs.add(attributeDifference)
            }
        } else {
            for (i in sourceValues.indices) {
                val attributeDifference = AttributeDifference()
                attributeDifference._targetEntryValue = targetValues[i]
                attributeDifference._sourceEntryValue = sourceValues[i]
                attributeDifference.attributeName = attributeName
                diffs.add(attributeDifference)
            }
        }
        return diffs
    }

    companion object {
        var attributeNameComparator: Comparator<AttributeDifference> = Comparator { o1, o2 ->
            o1.attributeName!!.lowercase(
                Locale.getDefault()
            ).compareTo(o2.attributeName!!.lowercase(Locale.getDefault()))
        }

        @JvmStatic
        fun compareStringCharByChar(source: String?, target: String?): Set<Int?> {
            val result: MutableSet<Int?> = HashSet<Int?>()
            if (target == null || target.isEmpty()) {
                if (source == null) return result
                for (i in 0 until source.length) result.add(i)
                return result
            }
            if (source == null || source.isEmpty()) return result

            val src = source.toCharArray()
            val tgt = target.toCharArray()

            for (i in 0 until source.length) {
                if (tgt.size <= i) result.add(i)
                else {
                    if (src[i] != tgt[i]) result.add(i)
                }
            }
            return result
        }
    }
}
