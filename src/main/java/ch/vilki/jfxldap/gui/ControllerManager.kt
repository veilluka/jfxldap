package ch.vilki.jfxldap.gui

import ch.vilki.jfxldap.Main
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox


class ControllerManager(main: Main) {


    var _main:Main = main
    lateinit var _ldapSourceExploreCtrl: LdapExploreController
    lateinit var _ldapTargetExploreController: LdapExploreController
    lateinit var _filterController: FilterWindowController
    lateinit var _ldapCompareController: LdapCompareController
    lateinit var _collectionsController: CollectionsController
    lateinit var _startSearchController: StartSearchController
    lateinit var _searchResultController: SearchResultController
    lateinit var _progressWindowController: ProgressWindowController
    lateinit var _settingsController: SettingsController
    lateinit var _startLdapCompareController: StartLdapCompareController
    lateinit var _exportWindowController: ExportWindowController
    lateinit var _showEntryController: ShowEntryController

    lateinit var _modificationsViewController: ModificationsViewController
    lateinit var _deleteEntriesController: DeleteEntriesController
    lateinit var _keyStoreController: KeyStoreController
    lateinit  var _entryView: EntryView

    companion object{
        fun fxmlDir(fxmlFileName:String) = "/ch/vilki/jfxldap/fxml/$fxmlFileName"
    }
    @Throws(Exception::class)
    fun initController(fxmlFile: String): ILoader {
        val url = Main::class.java.getResource(fxmlDir(fxmlFile))
            ?: throw Exception("URL could not be resolved with->$fxmlFile")
        val loader = FXMLLoader(url)
        val parent = loader.load<Parent>()
        val iLoader = loader.getController<ILoader>()
        iLoader.setMain(_main)
        iLoader.setWindow(parent)
        return iLoader
    }

    init {

    }

    fun initControllers(){
        _settingsController = initController("Settings.fxml") as SettingsController
        _ldapSourceExploreCtrl = initController("LdapExplore.fxml") as LdapExploreController;
        _ldapTargetExploreController =  initController("LdapExplore.fxml") as LdapExploreController;
        _ldapTargetExploreController.setTargetExplorerMode()

        _entryView = EntryView(_main)
        VBox.setVgrow(_entryView, Priority.ALWAYS)
        _entryView.maxHeight = Double.MAX_VALUE
        _progressWindowController = initController("ProgressWindow.fxml") as ProgressWindowController
        _collectionsController = initController("CollectionsWindow.fxml") as CollectionsController
        _collectionsController.setOwner(_main._primaryStage)
        _ldapSourceExploreCtrl.set_collectionTree(_collectionsController._treeView)
        _searchResultController = initController("SearchResult.fxml") as SearchResultController
        _startLdapCompareController = initController("StartLdapCompare.fxml") as StartLdapCompareController
        _startLdapCompareController.setOwner(_main._primaryStage)
        _ldapCompareController = initController("LdapCompareWindow.fxml") as LdapCompareController
         _startSearchController = initController("StartLdapSearch.fxml") as StartSearchController
        _showEntryController = initController("ShowEntry.fxml") as ShowEntryController
        _exportWindowController = initController("ExportWindow.fxml") as ExportWindowController
        _modificationsViewController = initController("ModificationView.fxml") as ModificationsViewController
        _deleteEntriesController = initController("DeleteEntriesWindow.fxml") as DeleteEntriesController
        _keyStoreController = initController("KeyStoreWindow.fxml") as KeyStoreController

        _settingsController.readConfiguration()

    }


}
