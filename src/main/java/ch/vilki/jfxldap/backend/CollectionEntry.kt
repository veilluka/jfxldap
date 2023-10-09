package ch.vilki.jfxldap.backend

import com.unboundid.ldap.sdk.Entry
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleListProperty
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import org.apache.logging.log4j.LogManager
import java.util.*
import java.util.stream.Collectors

class CollectionEntry : CustomEntryItem {
    var Subtree: SimpleBooleanProperty
    fun isSubtree(): Boolean {
        return Subtree.get()
    }

    fun subtreeProperty(): SimpleBooleanProperty {
        return Subtree
    }

    fun setSubtree(subtree: Boolean) {
        Subtree.set(subtree)
    }

    var _overWriteEntry: SimpleBooleanProperty
    fun getOverwriteEntry(): Boolean {
        return _overWriteEntry.get()
    }

    fun overwriteEntryProperty(): SimpleBooleanProperty {
        return _overWriteEntry
    }

    fun setOverwriteEntry(overwriteEntry: Boolean) {
        _overWriteEntry.set(overwriteEntry)
    }

    var ExportRoot: SimpleBooleanProperty
    fun getExportRoot(): Boolean {
        return ExportRoot.get()
    }

    fun exportRootEntryProperty(): SimpleBooleanProperty {
        return ExportRoot
    }

    fun setExportRoot(exportRoot: Boolean) {
        ExportRoot.set(exportRoot)
    }

    @JvmField
    var AttributesAction: SimpleStringProperty
    fun getAttributesAction(): String {
        return AttributesAction.get()
    }

    fun attributesActionProperty(): SimpleStringProperty {
        return AttributesAction
    }

    fun setAttributesAction(ignore: Boolean?) {
        if (ignore == null) AttributesAction.set(NONE) else {
            if (ignore) AttributesAction.set(IGNORE) else AttributesAction.set(EXPORT_ONLY)
        }
    }

    fun setAttributesAction(action: String?) {
        AttributesAction.set(action)
    }

    var _attributes: SimpleListProperty<String>
    fun getAttributes(): String {
        if (_attributes.get() == null) return ""
        val builder = StringBuilder()
        _attributes.get().stream().forEach { c: String? ->
            builder.append(c)
            builder.append(",")
        }
        if (builder.length > 0) builder.deleteCharAt(builder.length - 1)
        return builder.toString()
    }

    val attributesAsSet: Set<String>
        get() = _attributes.stream().map { obj: String -> obj.lowercase(Locale.getDefault()) }
            .collect(Collectors.toSet())

    fun attributesProperty(): SimpleListProperty<String> {
        return _attributes
    }

    fun setAttributes(attributes: ObservableList<String>?) {
        if (attributes == null) _attributes.clear()
        _attributes.set(attributes)
    }

    fun setAttributes(attributes: List<String>?) {
        _attributes.clear()
        _attributes.addAll(attributes!!)
    }

    fun getLdapFilter(): String {
        return LdapFilter.get()
    }

    fun ldapFilterProperty(): SimpleStringProperty {
        return LdapFilter
    }

    fun setLdapFilter(ldapFilter: String?) {
        LdapFilter.set(ldapFilter)
    }

    @JvmField
    var LdapFilter: SimpleStringProperty
    var Selected: SimpleBooleanProperty
    fun isSelected(): Boolean {
        return Selected.get()
    }

    fun selectedProperty(): SimpleBooleanProperty {
        return Selected
    }

    fun setSelected(Selected: Boolean) {
        this.Selected.set(Selected)
    }

    var ParentSelected: SimpleBooleanProperty
    fun isParentSelected(): Boolean {
        return ParentSelected.get()
    }

    fun parentSelectedProperty(): SimpleBooleanProperty {
        return ParentSelected
    }

    fun setParentSelected(ParentSelected: Boolean) {
        this.ParentSelected.set(ParentSelected)
    }

    var _displayDN: SimpleStringProperty?
    fun getDisplayDN(): String {
        return if (_displayDN == null || _displayDN!!.get() == null) "" else _displayDN!!.get()
    }

    fun displayDNProperty(): SimpleStringProperty? {
        return _displayDN
    }

    fun setDisplayDN(DisplayDN: String?) {
        this._displayDN!!.set(DisplayDN)
    }

    constructor(
        dn: String?, subtree: Boolean, deleteTarget: Boolean,
        attributes: List<String>?, filterAction: String?, ldapFilter: String?, displayDN: String?
    ) {
        _dn.set(dn)
        Subtree = SimpleBooleanProperty(subtree)
        _overWriteEntry = SimpleBooleanProperty(deleteTarget)
        val l = FXCollections.observableArrayList<String>()
        l.addAll(attributes!!)
        _attributes = SimpleListProperty(l)
        _attributes.set(l)
        AttributesAction = SimpleStringProperty(filterAction)
        LdapFilter = SimpleStringProperty(ldapFilter)
        Selected = SimpleBooleanProperty(false)
        _displayDN = SimpleStringProperty(displayDN)
        ParentSelected = SimpleBooleanProperty(false)
        ExportRoot = SimpleBooleanProperty(false)
    }

    constructor(entry: Entry?) : super(entry) {
        Subtree = SimpleBooleanProperty(false)
        _overWriteEntry = SimpleBooleanProperty(false)
        val l = FXCollections.observableArrayList<String>()
        _attributes = SimpleListProperty(l)
        _attributes.set(l)
        AttributesAction = SimpleStringProperty(NONE)
        _attributes.set(FXCollections.observableArrayList())
        LdapFilter = SimpleStringProperty()
        Selected = SimpleBooleanProperty(false)
        _displayDN = SimpleStringProperty()
        ParentSelected = SimpleBooleanProperty(false)
        ExportRoot = SimpleBooleanProperty(false)
    }

    fun update(collectionEntry: CollectionEntry) {
        Subtree.set(collectionEntry.isSubtree())
        _overWriteEntry.set(collectionEntry.getOverwriteEntry())
        _attributes.set(collectionEntry._attributes)
        AttributesAction.set(collectionEntry.getAttributesAction())
        LdapFilter.set(collectionEntry.getLdapFilter())
        Selected.set(collectionEntry.isSelected())
        ParentSelected.set(collectionEntry.isParentSelected())
    }

    val description: String
        get() {
            val stringBuilder = StringBuilder()
            stringBuilder.append("[DN=")
            stringBuilder.append(_dn)
            stringBuilder.append("]")
            stringBuilder.append("[OverwriteEntry=")
            stringBuilder.append(_overWriteEntry)
            stringBuilder.append("]")
            return stringBuilder.toString()
        }

    override fun toString(): String {
        return _rdn.get()
    }

    companion object {
        var logger = LogManager.getLogger(CollectionEntry::class.java)
        @JvmField
        var IGNORE = "IGNORE"
        @JvmField
        var EXPORT_ONLY = "EXPORT_ONLY"
        var NONE = "NONE"
    }
}
