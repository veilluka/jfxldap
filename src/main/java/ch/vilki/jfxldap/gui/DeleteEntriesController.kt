package ch.vilki.jfxldap.gui

import ch.vilki.jfxldap.Main
import ch.vilki.jfxldap.backend.*
import com.unboundid.ldap.sdk.Filter
import com.unboundid.ldap.sdk.SearchScope
import javafx.application.Platform
import javafx.concurrent.Task
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.stage.Stage
import org.apache.logging.log4j.LogManager
import java.util.concurrent.Executors
import java.util.stream.Collectors

class DeleteEntriesController : ILoader, IProgress {
    @FXML lateinit var window: VBox
    @FXML lateinit var _radioButtonSelectedWithChildren: RadioButton
    @FXML lateinit var _radioButtonOnlyChildren: RadioButton
    @FXML lateinit var _buttonDelete: Button
    @FXML lateinit var _buttonCancel: Button
    @FXML lateinit var _hboxFilter: HBox
    @FXML lateinit var _listViewResults: ListView<TextField>

    var _textFieldLdapFilter: TextFieldLdapFilter = TextFieldLdapFilter()
    var _currentConnection: Connection? = null
    var _selectedDN: String? = null
    var _deleteEntryToggleGroup = ToggleGroup()
    var _unboundidLdapSearch: UnboundidLdapSearch? = null
    var _selectedEntry: TreeItem<CustomEntryItem>? = null
    var _scene: Scene? = null
    lateinit var _stage: Stage
    private var _main: Main? = null
    private var _itemsToDelete: List<String>? = null
    @FXML
    private fun initialize() {
        _radioButtonOnlyChildren.toggleGroup = _deleteEntryToggleGroup
        _radioButtonSelectedWithChildren.toggleGroup = _deleteEntryToggleGroup
        _hboxFilter.children.add(_textFieldLdapFilter)
        _listViewResults.isDisable = true
        _textFieldLdapFilter.isDisable = true
        _radioButtonSelectedWithChildren.isSelected = true
        _radioButtonOnlyChildren.onAction = EventHandler { x: ActionEvent? ->
            _listViewResults.isDisable = false
            _textFieldLdapFilter.isDisable = false
        }
        _radioButtonSelectedWithChildren.onAction = EventHandler { x: ActionEvent? ->
            _listViewResults.isDisable = true
            _textFieldLdapFilter.isDisable = true
        }
        _buttonDelete.onAction = EventHandler { x: ActionEvent? -> delete() }
        _buttonCancel.onAction = EventHandler { x: ActionEvent? -> _stage.close() }
        _buttonDelete.style = st1
    }

    override fun setWindow(parent: Parent) {
        window = parent as VBox
        _scene = Scene(window)
        _stage = Stage()
        _stage.scene = _scene
    }

    override fun setMain(main: Main) {
        _main = main
    }

    fun show(selectedEntry: TreeItem<CustomEntryItem>?, connection: Connection?, dn: String?) {
        if (selectedEntry == null || connection == null || dn == null) return
        _buttonDelete.style = st1
        _buttonDelete.isDisable = false
        _buttonDelete.text = "Get entries"
        realDeleteDone = false
        _selectedDN = dn
        _currentConnection = connection
        _textFieldLdapFilter._currConnection = connection
        _listViewResults.items.clear()
        _textFieldLdapFilter.text = ""
        _selectedEntry = selectedEntry
        if(!_stage.isShowing)  _stage.showAndWait()
    }

    private fun delete() {
        if (_currentConnection == null) {
            GuiHelper.ERROR("Connection error", "Found no connection to delete")
            return
        }
        if (_selectedDN == null) return
        if (!realDeleteDone && !_listViewResults.items.isEmpty()) {
            if (!GuiHelper.confirm(
                    "Confirm delete", "Delete found entries?", "This will delete all found entries, " +
                            "in case LDAP Filter has been used under children only, only leafs will be deleted!"
                )
            ) return
            _stage.close()
            Main._ctManager._progressWindowController._stage.show()
            _itemsToDelete = _listViewResults.items.stream().map { x: TextField -> x.text }
                .toList()
            Thread(deleteEntries).start()
            return
        }
        try {
            _unboundidLdapSearch = UnboundidLdapSearch(
                Main._configuration, _currentConnection, this
            )
            _unboundidLdapSearch?._searchDN = _selectedDN
            _unboundidLdapSearch?.setReadAttributes(UnboundidLdapSearch.READ_ATTRIBUTES.none)
            var filter = Filter.create("objectclass=*")
            if (_textFieldLdapFilter.is_filterOK) filter = _textFieldLdapFilter._filter
            _unboundidLdapSearch?._filter = filter
            _unboundidLdapSearch?.set_searchScope(SearchScope.SUB)
            _listViewResults.items.clear()
            _listViewResults.isDisable = false
            Main._ctManager._progressWindowController._stage.show()
            val executorService = Executors.newSingleThreadExecutor()
            executorService.execute(_unboundidLdapSearch)
        } catch (e: Exception) {
            GuiHelper.EXCEPTION("Error deleting", e.message, e)
        }
    }

    override fun setOwner(stage: Stage) {
        _stage.initOwner(stage)
    }

    override fun setProgress(progress: Double, description: String?) {
        Platform.runLater { Main._ctManager._progressWindowController.setProgress(progress, description) }
    }

    override fun signalTaskDone(taskName: String?, description: String?, e: Exception?) {
        Platform.runLater {
            Main._ctManager._progressWindowController._stage.close()
            _buttonDelete.style = st2
            _buttonDelete.text = "DELETE"
        }
        val sorted =
            _unboundidLdapSearch?._children?.stream()?.sorted(Helper.DN_LengthComparator)?.collect(Collectors.toList())
        if (sorted != null) {
            for (s in sorted) {
                if (_radioButtonOnlyChildren.isSelected && s.dn.equals(_selectedDN, ignoreCase = true)) continue
                val textField = TextField(s.dn)
                Platform.runLater { _listViewResults.items.add(textField) }
            }
        }
    }

    override fun setProgress(taskName: String?, progress: Double) {
        Platform.runLater { Main._ctManager._progressWindowController.setProgress(progress, taskName) }
    }

    var deleteEntries: Task<Void> = object : Task<Void>() {
        @Throws(Exception::class)
        override fun call(): Void? {
            var done = 0
            val nrOfEntries = _itemsToDelete?.size?.toDouble() ?: 1.0
            for (dn in _itemsToDelete!!) {
                done++
                _currentConnection?.delete(dn)
                if (done % 10 == 0) {
                    val prog = done.toDouble() / nrOfEntries
                    setProgress(prog, dn)
                }
            }
            realDeleteDone = true
            Platform.runLater {
                Main._ctManager._progressWindowController._stage.close()
                _stage.show()
                _buttonDelete.isDisable = true
                _listViewResults.items.clear()
            }
            return null
        }
    }

    companion object {
        var logger = LogManager.getLogger(
            DeleteEntriesController::class.java
        )
        var st1 = "-fx-background-color: #14563f;-fx-text-fill: #ffffff "
        var st2 = "-fx-background-color:#c62f21 ;-fx-text-fill: #ffffff "
        private var realDeleteDone = false
    }
}
