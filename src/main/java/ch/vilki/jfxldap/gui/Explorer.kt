package ch.vilki.jfxldap.gui

import ch.vilki.jfxldap.Main
import ch.vilki.jfxldap.backend.IProgress
import javafx.fxml.FXML
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.ChoiceBox
import javafx.scene.control.ContextMenu
import javafx.scene.control.MenuItem
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.stage.Stage

class Explorer : IProgress, ILoader {

    @FXML private lateinit var _buttonCloseFile: Button
    @FXML private lateinit var _buttonConnect: Button
    @FXML private lateinit var _buttonDisconnect: Button
    @FXML private lateinit var _buttonOpenFile: Button
    @FXML private lateinit var _buttonRemoveFilter: Button
    @FXML private lateinit var _buttonRunLdapSearch: Button
    @FXML private lateinit var _buttonUploadFile: Button
    @FXML private lateinit var _choiceBoxEnviroment: ChoiceBox<Any>
    @FXML private lateinit var _hboxFilter: HBox
    @FXML private lateinit var _ldapExploreWindow: VBox

    private val _textFieldLdapFilter = TextFieldLdapFilter()
    lateinit var _scene: Scene
    lateinit var _stage: Stage
    lateinit var _exploreWindow: VBox

    val _contextMenu = ContextMenu()
    var TAB = "     "
    var _compareItem = MenuItem(" ${TAB}Compare", Icons.get_iconInstance().getIcon(Icons.ICON_NAME.COMPARE_SMALL))
    var _search = MenuItem("${TAB}Search", Icons.get_iconInstance().getIcon(Icons.ICON_NAME.SEARCH_SMALL))
    var _setDisplayAttribute =
        MenuItem("${TAB}Set display attribute", Icons.get_iconInstance().getIcon(Icons.ICON_NAME.SET_ATTRIBUTE_SMALL))
    var _export = MenuItem(TAB + "${TAB}Export", Icons.get_iconInstance().getIcon(Icons.ICON_NAME.EXPORT_SMALL))
    var _clipBoardLDIF =
        MenuItem("${TAB}Clipboard LDIF", Icons.get_iconInstance().getIcon(Icons.ICON_NAME.COPY_PASTE_SMALL))
    var _deleteEntry = MenuItem("${TAB}Delete", Icons.get_iconInstance().getIcon(Icons.ICON_NAME.REMOVE))

    override fun setMain(main: Main?) {
        TODO("Not yet implemented")
    }

    override fun setWindow(parent: Parent?) {
        TODO("Not yet implemented")
    }

    override fun setOwner(stage: Stage?) {
        TODO("Not yet implemented")
    }

    override fun setProgress(progress: Double, description: String?) {
        TODO("Not yet implemented")
    }

    override fun setProgress(taskName: String?, progress: Double) {
        TODO("Not yet implemented")
    }

    override fun signalTaskDone(taskName: String?, description: String?, e: Exception?) {
        TODO("Not yet implemented")
    }
}