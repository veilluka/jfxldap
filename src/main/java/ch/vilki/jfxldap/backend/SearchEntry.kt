package ch.vilki.jfxldap.backend

import com.unboundid.ldap.sdk.Entry
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleListProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import java.util.*

class SearchEntry : CustomEntryItem {
    @JvmField
    public var ValueFound = SimpleBooleanProperty(false)
    public var ChildrenFound = SimpleBooleanProperty(false)

    public fun setChildrenFound(found:Boolean) = ChildrenFound.set(found)
    @JvmField
    var _foundInAttributes: SimpleListProperty<String>? = null
    fun getFoundInAttributes(): ObservableList<String> {
        return _foundInAttributes!!.get()
    }

    fun FoundInAttributesProperty(): SimpleListProperty<String>? {
        return _foundInAttributes
    }

    fun setFoundInAttributes(attributes: ObservableList<String>?) {
        _foundInAttributes!!.set(attributes)
    }

    constructor(
        entry: Entry,
        searchString: String?,
        ignoreCase: Boolean,
        searchAttributes: List<String>?,
        ignoreSearchAttributes: Boolean,
        regex: Boolean,
        notContains: Boolean,
        exactMatch: Boolean
    ) : super(entry) {
        val foundInAttributes: MutableSet<String> = HashSet()
        val attributes = entry.attributes
        val searchAtts: MutableSet<String> = HashSet()
        if (searchAttributes != null) for (s in searchAttributes) searchAtts.add(s.lowercase(Locale.getDefault()))
        for (at in attributes) {
            var runSearch = false
            if (searchAttributes != null && !searchAttributes.isEmpty()) {
                if (ignoreSearchAttributes) {
                    if (!searchAttributes.contains(at.name.lowercase(Locale.getDefault()))) runSearch = true
                } else {
                    if (searchAttributes.contains(at.name.lowercase(Locale.getDefault()))) runSearch = true
                }
            } else {
                runSearch = true
            }
            if (runSearch) {
                val values = at.values
                if (values != null && values.size > 0) {
                    for (v in values) {
                        if (match(v, searchString, ignoreCase, regex, exactMatch)) {
                            ValueFound.set(true)
                            foundInAttributes.add(at.name)
                        }
                    }
                }
            }
        }
        _foundInAttributes = if (notContains) // we are looking for entries which do not have value searched for
        {
            if (!foundInAttributes.isEmpty()) // value found in some attributes, we are not intressted for this entry
            {
                ValueFound.set(false) // set to false so that entry is filtered out later on
            } else  // value not found, we are looking for this entry, but it has been flagged as false. flag it as true
            {
                ValueFound.set(true)
            }
            SimpleListProperty()
        } else {
            if (!foundInAttributes.isEmpty()) {
                val l = FXCollections.observableArrayList<String>()
                l.addAll(foundInAttributes)
                SimpleListProperty(l)
            } else SimpleListProperty()
        }
    }

    constructor(
        entry: Entry,
        searchAttributes: List<String>?,
        ignoreSearchAttributes: Boolean,
        baseDN: String,
        connection: Connection
    ) : super(entry) {
        ValueFound = SimpleBooleanProperty(false)
        val foundInAttributes: MutableSet<String> = HashSet()
        ChildrenFound = SimpleBooleanProperty(false)
        val attributes = entry.attributes
        val searchAtts: MutableSet<String> = HashSet()
        if (searchAttributes != null) for (s in searchAttributes) searchAtts.add(s.lowercase(Locale.getDefault()))
        for (at in attributes) {
            var runSearch = false
            if (searchAttributes != null && !searchAttributes.isEmpty()) {
                if (ignoreSearchAttributes) {
                    if (!searchAttributes.contains(at.name.lowercase(Locale.getDefault()))) runSearch = true
                } else {
                    if (searchAttributes.contains(at.name.lowercase(Locale.getDefault()))) runSearch = true
                }
            } else {
                runSearch = true
            }
            if (runSearch) {
                val values = at.values
                if (values != null && values.size > 0) {
                    for (v in values) {
                        if (hasDeadLink(v, baseDN, connection)) {
                            ValueFound.set(true)
                            foundInAttributes.add(at.name)
                        }
                    }
                }
            }
        }
        _foundInAttributes = if (!foundInAttributes.isEmpty()) {
            val l = FXCollections.observableArrayList<String>()
            l.addAll(foundInAttributes)
            SimpleListProperty(l)
        } else SimpleListProperty()
    }

    private fun hasDeadLink(value: String, baseDN: String, connection: Connection): Boolean {
        if (value.contains(baseDN)) {
            try {
                val e = connection.getEntry(value) ?: return true
            } catch (e: Exception) {
                return false
            }
        }
        return false
    }

    fun match(
        attValue: String,
        searchValue: String?,
        ignoreCase: Boolean,
        regex: Boolean,
        exactMatch: Boolean
    ): Boolean {
        if (searchValue == null || searchValue.equals("", ignoreCase = true)) return true
        var att: String? = null
        var search: String? = null
        if (ignoreCase) {
            att = attValue.lowercase(Locale.getDefault())
            search = searchValue.lowercase(Locale.getDefault())
        } else {
            att = attValue
            search = searchValue
        }
        return if (exactMatch) {
            if (att == search) true else false
        } else {
            if (att.contains(search)) true else false
        }
    }

    override fun toString(): String {
        return _rdn.get()
    }
}
