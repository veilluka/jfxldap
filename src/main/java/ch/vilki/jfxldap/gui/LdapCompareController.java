package ch.vilki.jfxldap.gui;

import ch.vilki.jfxldap.Main;
import ch.vilki.jfxldap.backend.*;
import ch.vilki.secured.SecureString;
import com.unboundid.ldap.sdk.*;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public class LdapCompareController implements IProgress, ILoader {

    /***************** GUI ELEMENTS *****************************************/
    CompareTree _activeCompareTree = null;
    EntryDiffView _entryDiffView = null;
    HBox _foundAttributesHbox = new HBox();
    @FXML TextFlow _textFlowSourceValue;
    @FXML TextFlow _textFlowTargetValue;
    @FXML ScrollPane _scrollPaneSourceValue;
    @FXML ScrollPane _scrollPaneTargetValue;
    @FXML CheckBox _checkBoxShowEqual;
    @FXML VBox _vboxCompareTree;
    @FXML VBox _vBoxResult;
    @FXML Button _buttonSyncAllEntryAttributes;
    Button _buttonSyncAllEntries= new Button();


    final ContextMenu _entryContextMenu = new ContextMenu();

    MenuItem _entryMenuCopyToSource = new MenuItem("_entryMenuCopyToSource");
    MenuItem _entryMenuCopyToTarget = new MenuItem("_entryMenuCopyToTarget");
    MenuItem _entryMenuCopyTreeToSource = new MenuItem("_entryMenuCopyTreeToSource");
    MenuItem _entryMenuCopyTreeToTarget = new MenuItem("_entryMenuCopyTreeToTarget");
    MenuItem _entryMenuBeyondCompare = new MenuItem("Beyond Compare");
    MenuItem _entryMenuVisualCode = new MenuItem("VS Compare");
    MenuItem _entryMenuSyncAttributeForEntryAndAllChildrenToTarget = new MenuItem();
    final ContextMenu _syncAttributesContextMenu = new ContextMenu();


    @FXML ObservableList<String> _observableDifferentEntryAttributes = FXCollections.observableArrayList();
    @FXML ObservableList<String> _observableDifferentSearchAttributes = FXCollections.observableArrayList();

    ChoiceBox<String> _choiceBoxFoundDifferentAttributes = new ChoiceBox<>();
    ChangeListener _choiceBoxFoundDifferentAttributesListener = (observableValue, number, number2) -> {
        String selectedAttribute = _choiceBoxFoundDifferentAttributes.getSelectionModel().getSelectedItem();
        if(selectedAttribute==null) return;
        showOnlyAttribute(selectedAttribute);

    };



    private static String MAIN_TREE_ID="ALL";

    SplitPane _window;
    /*---------- PROGRESS WINDOW ****************/
    @FXML
    VBox _progressPane;
    Stage _progressStage;
    Scene _progressScene;

    /************* FILTER WINDOW **************/
    @FXML
    VBox _filterWindow;
    Stage _filterStage;
    Scene _filterScene;

    /***********************************************************************/

    Main _main;
    LdapCompareController _this;
    Scene _scene;
    Stage _stage;
    Tab _CompResultTab;
    Map<String, CompareTree> _compareTrees = new HashMap<>();

    public SplitPane get_window() {
        return _window;
    }

    private ProgressWindowController _progressController = null;
    private FilterWindowController _filterWindowController = null;
    Connection _currentTargetConnection = null;
    Connection _currentSourceConnection = null;
    TreeItem<CompResult> _observedEntry = null;
    static Logger logger = LogManager.getLogger(LdapCompareController.class);

    @Override
    public void setWindow(Parent parent) {
        _window = (SplitPane) parent;
        _scene = new Scene(_window);
        _stage = new Stage();
        _stage.setScene(_scene);
        _vboxCompareTree = new VBox();
        _vboxCompareTree.getChildren().add(_activeCompareTree);
        _window.getItems().add(0, _vboxCompareTree);
        _CompResultTab = new Tab("Compare Result", _window);
        _CompResultTab.setId("\"Compare Result");
    }

    @Override
    public void setOwner(Stage stage) {
    }

    private void initControlls() {

        _checkBoxShowEqual.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.EQUAL));
        _checkBoxShowEqual.setOnAction(x -> {
            if (_entryDiffView == null) return;
            if (_observedEntry == null) return;
            _entryDiffView.updateValue(_observedEntry.getValue(), _checkBoxShowEqual.isSelected());
        });
        _choiceBoxFoundDifferentAttributes.getSelectionModel().selectedIndexProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observableValue, Number number, Number number2) {

            }
        });

        Label diffAttributes = new Label("Show only entries with found attribute");
        diffAttributes.styleProperty().setValue(Styling.SMALL_MENU_TEXT_BOLD);
        _choiceBoxFoundDifferentAttributes.setStyle(Styling.SMALL_MENU_TEXT_BOLD);
        HBox.setMargin(diffAttributes,new Insets(0,5,0,5));
        _foundAttributesHbox.setAlignment(Pos.CENTER_LEFT);
        HBox.setMargin(_choiceBoxFoundDifferentAttributes, new Insets(0,5,0,5));
        _foundAttributesHbox.getChildren().add(diffAttributes);
        _foundAttributesHbox.getChildren().add(_choiceBoxFoundDifferentAttributes);
        _buttonSyncAllEntries.setStyle(Styling.SMALL_MENU_TEXT_BOLD);
        _buttonSyncAllEntries.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.ARROW_RIGHT));
        HBox.setMargin(_buttonSyncAllEntries,new Insets(2,5,2,5));
        _foundAttributesHbox.getChildren().add(_buttonSyncAllEntries);


        _buttonSyncAllEntryAttributes.setDisable(true);
        _buttonSyncAllEntryAttributes.setOnAction(x->{
            ObservableList<TreeItem<CompResult>> selected = _activeCompareTree.getSelectionModel().getSelectedItems();
            if(selected == null || selected.isEmpty()) return;
            if(selected.size() == 1) copyAttributeValueToTarget(selected.get(0).getValue().getDifferentAttributes().stream().collect(Collectors.toList()));
            else
            {
                GuiHelper.ERROR("Operation not allowed ","You have selected multiple objects on the tree, this works only on one " +
                        "entry hier");
                return;
            }
        });

        _buttonSyncAllEntries.setOnAction(x->{
            StringBuilder stringBuilder = new StringBuilder();
            for(String att: _choiceBoxFoundDifferentAttributes.getItems()) {
                if (att.equalsIgnoreCase(MAIN_TREE_ID)) continue;
                stringBuilder.append("--> " + att);
                stringBuilder.append(System.lineSeparator());
            }
            if(_choiceBoxFoundDifferentAttributes.getValue().equalsIgnoreCase(MAIN_TREE_ID))
            {
                if(!GuiHelper.confirm("Sync ALL Attributes", "ARE YOU SURE??",
                        "This will sync ALL Attributes on ALL entries to "
                                + _currentTargetConnection.getName() + " !!! \n" +  stringBuilder.toString()  )) return;
                for(String att: _choiceBoxFoundDifferentAttributes.getItems())
                {
                    if(att.equalsIgnoreCase(MAIN_TREE_ID)) continue;
                    syncAttributeToTarget(att,true);
                }
            }
            else
            {
                if(!GuiHelper.confirm("Sync Attribute", "ARE YOU SURE??",
                        "This will sync " + _choiceBoxFoundDifferentAttributes.getValue()  + " Attribute on ALL entries to "
                                + _currentTargetConnection.getName() + " !!! \n"   )) return;
                syncAttributeToTarget(_choiceBoxFoundDifferentAttributes.getValue(),true);
            }
        });
    }

    public void runCompare() {
        String selectedTargetDN = null;
        String selectedSourceDN = null;
       
        if (_main._ctManager._ldapTargetExploreController.is_ldapExplorerTargetAction()) {
            if (_main._ctManager._startLdapCompareController._choiceBoxTargetConnection.getSelectionModel().getSelectedItem() == null &&
                    _main._ctManager._ldapTargetExploreController.get_selectedDN() == null) {
                GuiHelper.ERROR("Connection", "Target connection not selected");
                return;
            } else {
                
                selectedTargetDN = _main._ctManager._ldapTargetExploreController.get_selectedDN();
                selectedSourceDN = _main._ctManager._ldapSourceExploreCtrl.get_selectedDN();
            }
        } else {
            selectedTargetDN = _main._ctManager._ldapSourceExploreCtrl.get_selectedDN();
            if (_main._ctManager._startLdapCompareController._choiceBoxTargetConnection.getSelectionModel().getSelectedItem() == null) {
                GuiHelper.ERROR("Target connection ERROR", "Select target connection first");
                return;
            }
        }
        try {
            if (_main._ctManager._ldapSourceExploreCtrl.get_currentConnection().is_fileMode()) {
                if (_main._ctManager._ldapSourceExploreCtrl.get_currentConnection().getEntry(selectedSourceDN) == null) {
                    GuiHelper.ERROR("Selection problem", "You have not selected real entry in LDIF source file. \n" +
                            "Some entries which are shown in explorer, are just dummy entries but are not part of the file");
                    //return;
                }
            }
        } catch (LDAPException e) {
            GuiHelper.EXCEPTION("Exception occured", e.getMessage(), e);
            logger.error("Exception in comapre", e);
        }
        String rootSourceDN = _main._ctManager._ldapSourceExploreCtrl.get_selectedDN();
        if (rootSourceDN == null || rootSourceDN.equalsIgnoreCase("") || selectedTargetDN == null || selectedTargetDN.equalsIgnoreCase("")) {
            GuiHelper.ERROR("Problem", "Select compare scope in explorer window first");
            return;
        }
        String rootTargetDN = null;
        FilterWindowController filterView = _main._ctManager._startLdapCompareController.getEmbeddedFilterViewController();

        _currentSourceConnection = _main._ctManager._ldapSourceExploreCtrl.get_currentConnection();
        if (_main._ctManager._ldapTargetExploreController.is_ldapExplorerTargetAction()) {
            _currentTargetConnection = _main._ctManager._ldapTargetExploreController.get_currentConnection();
            rootTargetDN = _main._ctManager._ldapTargetExploreController.get_selectedDN();
            try {
                if (_main._ctManager._ldapTargetExploreController.get_currentConnection().is_fileMode()) {
                    if (_main._ctManager._ldapTargetExploreController.get_currentConnection().getEntry(selectedTargetDN) == null) {
                        GuiHelper.ERROR("Selection problem", "You have not selected real entry in LDIF target file. \n" +
                                "Some entries which are shown in explorer, are just dummy entries but are not part of the file");
                        //  return;
                    }
                }
            } catch (LDAPException e) {
                GuiHelper.EXCEPTION("Exception occured", e.getMessage(), e);
                logger.error("Exception in comapre", e);
            }
        } else {
            Connection connection = null;
            rootTargetDN = rootSourceDN;
            SecureString secureString = _main._configuration.getConnectionPassword(_main._ctManager._startLdapCompareController._choiceBoxTargetConnection.getValue());
            if (secureString == null) {
                String loginPassword = GuiHelper.enterPassword("Source Connection Password", "Enter login password");

                try {
                    connection = _main._ctManager._ldapSourceExploreCtrl._choiceBoxEnviroment.getValue().copy();
                    connection.setPassword(loginPassword);
                    connection.connect();
                } catch (LDAPException | GeneralSecurityException e) {
                    e.printStackTrace();
                    GuiHelper.EXCEPTION("Connection Error", "Could not connect to target", e);
                    return;
                }
            } else {
                try {
                    connection = _main._ctManager._startLdapCompareController._choiceBoxTargetConnection.getValue().copy();
                    connection.setPassword(secureString.toString());
                    connection.connect();
                } catch (LDAPException | GeneralSecurityException e) {
                    GuiHelper.EXCEPTION("Connection Error", "Could not connect to target", e);
                    e.printStackTrace();
                    return;
                }
                _currentTargetConnection = connection;
            }
        }
        _main._ctManager._startLdapCompareController._stage.close();
        if (_main._ctManager._startLdapCompareController._checkBoxIgnoreMissingEntries.isSelected())
            _activeCompareTree.set_ignoreMissingEntries(true);
        else _activeCompareTree.set_ignoreMissingEntries(false);
        if (_main._ctManager._startLdapCompareController._checkBoxIgnoreOperationalAttributes.isSelected())
            _activeCompareTree.set_ignoreOperationalAttributes(true);
        else _activeCompareTree.set_ignoreOperationalAttributes(false);
        if (_filterWindowController._radioButtonIgnoreAttributes.isSelected())
            _activeCompareTree.setIgnoreAttributes(_filterWindowController._listFilterAttributes.getItems());
        else if (_filterWindowController._radioButtonCompareAttributes.isSelected())
            _activeCompareTree.set_CompareOnlyAttributes(_filterWindowController._listFilterAttributes.getItems());
        else _activeCompareTree.disableCompareFilter();
        _observableDifferentEntryAttributes.clear();
        _observableDifferentSearchAttributes.clear();
        _progressController._labelHeader.setText(_main._ctManager._ldapSourceExploreCtrl.get_selectedDN());
        _progressStage.show();
        _vboxCompareTree.getChildren().clear();
        Iterator<String> it = _compareTrees.keySet().iterator();
        List<String> removeTrees = new ArrayList<>();
        while (it.hasNext()) {
            String treeID = it.next();
            if (!treeID.equalsIgnoreCase(MAIN_TREE_ID))
            {
                removeTrees.add(treeID);
            }
        }
        for(String id: removeTrees)
        {
            CompareTree delTree = _compareTrees.get(id);
            delTree.setRoot(null);
        }
        for(String id: removeTrees) _compareTrees.remove(id);

        _activeCompareTree = _compareTrees.get(MAIN_TREE_ID);
        _vboxCompareTree.getChildren().add(_activeCompareTree);
        _choiceBoxFoundDifferentAttributes.getItems().clear();
        try {
            _activeCompareTree.initCompare(
                    rootSourceDN,
                    rootTargetDN,
                    _currentSourceConnection.getDisplayAttribute(),
                    _currentSourceConnection,
                    _currentTargetConnection,
                    _main._ctManager._startLdapCompareController._checkBoxShowAllEntries.isSelected(),
                    _main._ctManager._startLdapCompareController._checkBoxIgnoreWhiteSpace.isSelected(),
                    filterView.get_sourceFilter(),
                    _main._configuration,
                    filterView._listFilterAttributes.getItems(),
                    filterView._radioButtonIgnoreAttributes.isSelected(),
                    filterView._radioButtonCompareAttributes.isSelected(),
                    _main._ctManager._startLdapCompareController._checkBoxSubtree.isSelected());
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.submit(() -> _activeCompareTree.runCompare());

        } catch (Exception e) {
            GuiHelper.EXCEPTION("Compare Error", "Exception occured", e);
            e.printStackTrace();
            return;
        }
    }


    ChangeListener<TreeItem<CompResult>> listener = new ChangeListener<TreeItem<CompResult>>() {
        @Override
        public void changed(ObservableValue<? extends TreeItem<CompResult>> observable,
                            TreeItem<CompResult> oldValue, TreeItem<CompResult> newValue) {

            if (newValue != null) {
                _buttonSyncAllEntryAttributes.setDisable(true);
                _entryContextMenu.getItems().clear();
                _entryContextMenu.getItems().add(_entryMenuBeyondCompare);
                _entryContextMenu.getItems().add(_entryMenuVisualCode);
                 _observedEntry = newValue;
                _observableDifferentEntryAttributes.clear();
                if (_observedEntry != null && _observedEntry.getValue() != null && _observedEntry.getValue().getDifferentAttributes() != null)
                    _observableDifferentEntryAttributes.setAll(_observedEntry.getValue().getDifferentAttributes());
                _observableDifferentEntryAttributes.sort(Comparator.comparing(String::toString));
                _entryDiffView.updateValue(_observedEntry.getValue(), _checkBoxShowEqual.isSelected());
                if (_observedEntry.getValue().get_entry_type().equals(CompResult.ENTRY_TYPE.SOURCE)) {
                    _entryContextMenu.getItems().addAll(_entryMenuCopyToSource, _entryMenuCopyToTarget,
                            _entryMenuCopyTreeToSource, _entryMenuCopyTreeToTarget);
                    if (_observedEntry.getParent() != null) {
                        if (_observedEntry.getParent().getValue().get_entry_type().equals(CompResult.ENTRY_TYPE.BOTH)) {

                        }
                    } else if (_observedEntry.equals(_activeCompareTree.getRoot())) {
                        try {
                            Entry rootEntry =
                                    _main._ctManager._ldapSourceExploreCtrl.get_currentConnection().
                                            getEntry(_activeCompareTree.getRoot().getValue().get_dn());
                            DN parentDN = rootEntry.getParentDN();
                            if (parentDN != null) {
                                if (_currentTargetConnection.getEntry(parentDN.toString()) != null) {
                                    // TODO DELETE
                                }
                            }
                        } catch (LDAPException e1) {

                        }

                    }
                } else if (_observedEntry.getValue().get_entry_type().equals(CompResult.ENTRY_TYPE.TARGET)) {
                    _entryContextMenu.getItems().add(_entryMenuCopyToTarget);
                    _entryContextMenu.getItems().add(_entryMenuCopyTreeToTarget);
                    if (_observedEntry.getParent() != null) {
                        if (_observedEntry.getParent().getValue().get_entry_type().equals(CompResult.ENTRY_TYPE.BOTH)) {
                            //TODO DELETE
                        } else {
                            _entryContextMenu.getItems().add(_entryMenuCopyToSource);
                            _entryContextMenu.getItems().add(_entryMenuCopyTreeToSource);
                        }
                    }
                }
                else if(_observedEntry.getValue().get_entry_type().equals(CompResult.ENTRY_TYPE.BOTH))
                {
                    if(_observedEntry.getValue().get_compare_result().equals( CompResult.COMPARE_RESULT.ENTRY_NOT_EQUAL))
                    {
                        _entryContextMenu.getItems().add(_entryMenuSyncAttributeForEntryAndAllChildrenToTarget);
                        _buttonSyncAllEntryAttributes.setDisable(false);
                    }
                    else
                    {
                        _entryContextMenu.getItems().remove(_entryMenuSyncAttributeForEntryAndAllChildrenToTarget);
                        _buttonSyncAllEntryAttributes.setDisable(true);
                    }
                }
            }
            _entryContextMenu.getItems().forEach(x -> x.setStyle(Styling.SMALL_MENU_TEXT_BOLD));
        }
    };



    private void initCompareTree(CompareTree s) {
        s.registerProgress(this);
        s.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(s, Priority.ALWAYS);
        s.setMaxWidth(Double.MAX_VALUE);
        s.setMaxHeight(Double.MAX_VALUE);
        s.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        s.getSelectionModel().selectedItemProperty().addListener(listener);

        s.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
            if (e.getButton() == MouseButton.SECONDARY) {
                if (_observedEntry == null) return;
                _entryMenuCopyToTarget.setText(_currentTargetConnection.getName());
                _entryMenuCopyTreeToTarget.setText(_currentTargetConnection.getName());
                _entryMenuCopyToSource.setText(_currentSourceConnection.getName());
                _entryMenuCopyTreeToSource.setText(_currentSourceConnection.getName());
                _entryContextMenu.getItems().clear();
                _entryContextMenu.getItems().add(_entryMenuBeyondCompare);
                _entryContextMenu.getItems().add(_entryMenuVisualCode);
                if (_observedEntry.getValue().get_entry_type().equals(CompResult.ENTRY_TYPE.SOURCE)) {
                    if (_observedEntry.getParent() != null) {
                        if (_observedEntry.getParent().getValue().get_entry_type().equals(CompResult.ENTRY_TYPE.BOTH)) {
                            _entryContextMenu.getItems().add(_entryMenuCopyToTarget);
                            _entryContextMenu.getItems().add(_entryMenuCopyTreeToTarget);
                            _entryContextMenu.getItems().remove(_entryMenuSyncAttributeForEntryAndAllChildrenToTarget);
                        }
                    } else if (_observedEntry.equals(_activeCompareTree.getRoot())) {
                        try {
                            Entry rootEntry = _main._ctManager._ldapSourceExploreCtrl.get_currentConnection().getEntry(_activeCompareTree.getRoot().getValue().get_dn());
                            DN parentDN = rootEntry.getParentDN();
                            if (parentDN != null) {
                                if (_currentTargetConnection.getEntry(parentDN.toString()) != null) {
                                    _entryContextMenu.getItems().add(_entryMenuCopyToTarget);
                                    _entryContextMenu.getItems().add(_entryMenuCopyTreeToTarget);
                                    _entryContextMenu.getItems().remove(_entryMenuSyncAttributeForEntryAndAllChildrenToTarget);
                                }
                            }
                        } catch (LDAPException exc) {
                            GuiHelper.EXCEPTION("Exception thrown", exc.getMessage(), exc);
                        }

                    }
                } else if (_observedEntry.getValue().get_entry_type().equals(CompResult.ENTRY_TYPE.TARGET)) {
                    if (_observedEntry.getParent() != null) {
                        if (_observedEntry.getParent().getValue().get_entry_type().equals(CompResult.ENTRY_TYPE.BOTH)) {
                            _entryContextMenu.getItems().add(_entryMenuCopyToSource);
                            _entryContextMenu.getItems().add(_entryMenuCopyTreeToSource);
                            _entryContextMenu.getItems().remove(_entryMenuSyncAttributeForEntryAndAllChildrenToTarget);
                        }
                    }
                }
                else if(_observedEntry.getValue().get_entry_type().equals(CompResult.ENTRY_TYPE.BOTH))
                {
                    if(_observedEntry.getValue().get_compare_result().equals( CompResult.COMPARE_RESULT.ENTRY_NOT_EQUAL))
                    {
                        _entryContextMenu.getItems().add(_entryMenuSyncAttributeForEntryAndAllChildrenToTarget);
                        _buttonSyncAllEntryAttributes.setDisable(false);
                    }
                    else
                    {
                        _entryContextMenu.getItems().remove(_entryMenuSyncAttributeForEntryAndAllChildrenToTarget);
                        _buttonSyncAllEntryAttributes.setDisable(true);
                    }
                }
                if (!_entryContextMenu.getItems().isEmpty()) {
                    _entryContextMenu.getItems().forEach(x -> x.setStyle(Styling.SMALL_MENU_TEXT_BOLD));
                    _entryContextMenu.show(_main.get_primaryStage(), e.getScreenX(), e.getScreenY());
                }
            }
        });
    }

    @Override
    public void setMain(Main main) {
        _this = this;
        _main = main;
        _compareTrees.put(MAIN_TREE_ID, new CompareTree());
        _activeCompareTree = _compareTrees.get(MAIN_TREE_ID);
        initCompareTree(_activeCompareTree);
        try {
            initProgressWindow(main);
            initFilterWindow();
        } catch (IOException e) {
            e.printStackTrace();
        }
        initControlls();
        _entryContextMenu.getItems().clear();
        _entryContextMenu.getItems().add(_entryMenuBeyondCompare);
        _entryContextMenu.getItems().add(_entryMenuVisualCode);
        _entryMenuCopyToSource.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.ARROW_LEFT));
        _entryMenuCopyToTarget.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.ARROW_RIGHT));
        _entryMenuCopyTreeToSource.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.TREE_SELECTED));
        _entryMenuCopyTreeToTarget.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.TREE_SELECTED));
        _entryMenuSyncAttributeForEntryAndAllChildrenToTarget.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.SORT_SMALL));

        _buttonSyncAllEntryAttributes.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.ARROW_RIGHT));
        _buttonSyncAllEntryAttributes.setStyle(Styling.SMALL_MENU_TEXT_BOLD);
        _buttonSyncAllEntryAttributes.setText("Sync all Attributes to target entry");


        _entryDiffView = new EntryDiffView(_main);
        _vBoxResult.getChildren().add(1, _entryDiffView);
        _entryMenuVisualCode.setOnAction(event -> {
            String error = null;
            if (_main._ctManager._settingsController._textFieldTempDirectoryPath.getText() == null)
                error = "Temp directory not set in settings, set it first!";
            if (_main._ctManager._settingsController._textFieldVisualCode.getText() == null)
                error = "Visual Code executable not set in config!";
            if (error != null) {
                GuiHelper.ERROR("Error Visual Code Compare", error);
            } else {
                String sourceFileName ="";
                String targetFileName = "";

               
                if(_currentSourceConnection.is_fileMode()) {
                    sourceFileName = _main._ctManager._settingsController._textFieldTempDirectoryPath.getText() + "\\" + Paths.get(_currentSourceConnection.get_fileName()).getFileName() +
                            "_" + _observedEntry.getValue().getRDN() + ".ldif";
                }else {
                    sourceFileName = _main._ctManager._settingsController._textFieldTempDirectoryPath.getText() + "\\" +
                            _currentSourceConnection.getName() + "_" + _observedEntry.getValue().getRDN() + ".ldif";
                }
                if (_currentTargetConnection.is_fileMode()) {
                    targetFileName = _main._ctManager._settingsController._textFieldTempDirectoryPath.getText() + "\\" +
                            Paths.get(_currentTargetConnection.get_fileName()).getFileName() + "_" + _observedEntry.getValue().getRDN() + ".ldif";
                }
                else {
                    targetFileName = _main._ctManager._settingsController._textFieldTempDirectoryPath.getText() + "\\" +
                            _currentTargetConnection.getName() + "_" + _observedEntry.getValue().getRDN() + ".ldif";
                }
                List<TreeItem<CompResult>> diffNodes =
                        _activeCompareTree.getAllDifferencesWithChildren(_observedEntry, new ArrayList<>());
                try {
                    _activeCompareTree.storeDifferencesInFile(sourceFileName, targetFileName, diffNodes);
                } catch (Exception e) {
                    GuiHelper.EXCEPTION("File Error", "Error saving file", e);
                }
                try {
                    ProcessBuilder pb = new ProcessBuilder(_main._ctManager._settingsController._textFieldVisualCode.getText(),
                            "--diff",
                           targetFileName,
                            sourceFileName
                    );
                    pb.start();

                } catch (IOException e) {
                    GuiHelper.ERROR("Error starting process", e.toString());
                    e.printStackTrace();
                }
            }
        });

        _entryMenuBeyondCompare.setOnAction(event -> {
            String error = null;
            if (_main._ctManager._settingsController._textFieldTempDirectoryPath.getText() == null)
                error = "Temp directory not set in settings, set it first!";
            if (_main._ctManager._settingsController._textFieldBeyondCompareExe.getText() == null)
                error = "Beyond compare executable not set in config!";
            if (error != null) {
                GuiHelper.ERROR("Error Beyond Compare", error);
            } else {
                String sourceFileName ="";
                String targetFileName = "";

                if(_currentSourceConnection.is_fileMode()) {
                    sourceFileName = _main._ctManager._settingsController._textFieldTempDirectoryPath.getText() + "\\" + Paths.get(_currentSourceConnection.get_fileName()).getFileName() +
                            "_" + _observedEntry.getValue().getRDN() + ".ldif";
                }else {
                    sourceFileName = _main._ctManager._settingsController._textFieldTempDirectoryPath.getText() + "\\" +
                            _currentSourceConnection.getName() + "_" + _observedEntry.getValue().getRDN() + ".ldif";
                }
                if (_currentTargetConnection.is_fileMode()) {
                    targetFileName = _main._ctManager._settingsController._textFieldTempDirectoryPath.getText() + "\\" +
                            Paths.get(_currentTargetConnection.get_fileName()).getFileName() + "_" + _observedEntry.getValue().getRDN() + ".ldif";
                }
                else {
                    targetFileName = _main._ctManager._settingsController._textFieldTempDirectoryPath.getText() + "\\" +
                            _currentTargetConnection.getName() + "_" + _observedEntry.getValue().getRDN() + ".ldif";
                }
                List<TreeItem<CompResult>> diffNodes =
                        _activeCompareTree.getAllDifferencesWithChildren(_observedEntry, new ArrayList<>());
                try {
                    _activeCompareTree.storeDifferencesInFile(sourceFileName, targetFileName, diffNodes);
                } catch (Exception e) {
                    GuiHelper.EXCEPTION("File Error", "Error saving file", e);
                }
                try {
                    ProcessBuilder pb = new ProcessBuilder(
                            "cmd",
                            "\"/c\"",
                            "\"" + _main._ctManager._settingsController._textFieldBeyondCompareExe.getText() + "\"",
                            "\"" + sourceFileName + "\"",
                            "\"" + targetFileName + "\""
                    );
                    pb.start();
                } catch (IOException e) {
                    GuiHelper.ERROR("Error starting process", e.toString());
                    e.printStackTrace();
                }
            }
        });
        _entryMenuCopyToSource.setOnAction(x -> copyEntryToSource());
        _entryMenuCopyToTarget.setOnAction(x -> copyEntryToTarget());
        _entryMenuCopyTreeToTarget.setOnAction((ActionEvent x) -> {
            try {
                _progressStage.show();
                ExecutorService executor = Executors.newSingleThreadExecutor();
                executor.submit(() -> {
                    try {
                        _activeCompareTree.copyObjectToTarget(_observedEntry, true);
                    } catch (Exception e) {
                        GuiHelper.EXCEPTION("Error copy tree", "Exception occured during copy", e);
                    }
                });
            } catch (Exception e) {
                GuiHelper.EXCEPTION("Copy tree to target", "Object copy error->" + _observedEntry.getValue().get_dn(), e);
            }
        });

        _entryMenuSyncAttributeForEntryAndAllChildrenToTarget.setOnAction(x-> showAttributesToSync());

        _scrollPaneSourceValue.vvalueProperty().addListener((ov, old_val, new_val) -> {
            _scrollPaneTargetValue.setVvalue(new_val.doubleValue());
        });
    }

    private void showAttributesToSync()
    {
         _syncAttributesContextMenu.getItems().clear();
        _compareTrees.get(MAIN_TREE_ID).get_foundDifferentAttributes().forEach(x->{
            MenuItem menuItem = new MenuItem(x);
            menuItem.setStyle(Styling.SMALL_TEXT);
            menuItem.setOnAction(y->syncAttributeToTarget(x,false));
            _syncAttributesContextMenu.getItems().add(menuItem);
        });
        _syncAttributesContextMenu.getItems().forEach(x -> x.setStyle(Styling.SMALL_MENU_TEXT_BOLD));
        _syncAttributesContextMenu.show(_main.get_primaryStage(), _entryContextMenu.getX(),_entryContextMenu.getY());
        _activeCompareTree.getSelectionModel().getSelectedItem();
    }

    private void syncAttributeToTarget(String attName,boolean takeAllTreeEntries)
    {
        ObservableList<TreeItem<CompResult>> selectedEntries = null;
        List<TreeItem<CompResult>> allEntries = null;
        if(takeAllTreeEntries)
        {
           allEntries = _activeCompareTree.getAllSubEntries(_activeCompareTree.getRoot(),selectedEntries);
        }
        else
        {
            selectedEntries = _activeCompareTree.getSelectionModel().getSelectedItems();
            if(selectedEntries != null && !selectedEntries.isEmpty())
            {
                allEntries = selectedEntries.stream().collect(Collectors.toList());
            }
        }

        if(allEntries == null || allEntries.isEmpty()) return;
        List<TreeItem<CompResult>> filteredResult = new ArrayList<>();
        for(TreeItem<CompResult> resultTreeItem : allEntries)
        {
            if(resultTreeItem.getValue().get_compare_result().equals(CompResult.COMPARE_RESULT.ENTRY_NOT_EQUAL))
            {
                if(resultTreeItem.getValue().getDifferentAttributes().contains(attName)) filteredResult.add(resultTreeItem);
            }
        }
        if(filteredResult.isEmpty())
        {
            GuiHelper.INFO("Nothing to sync","Did not find any entry where the attribute->" + attName + " different to source");
            return;
        }
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Following entries shall be synced:\n \n" );
        filteredResult.stream().forEach(x->stringBuilder.append("->  " + x.getValue().getRDN() + "\n"));

        if(!GuiHelper.confirm("Attribute Synchronisation", "CONFIRM " + attName + " SYNCHRONISATION TO -> " +
                _currentTargetConnection + "!!",stringBuilder.toString() )) return;

       
        _main._ctManager._progressWindowController.clearProgressWindow();
        _main._ctManager._progressWindowController._stage.show();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = null;
        Runnable runnableTask = () -> {
            try {
                int nrOfEntries = filteredResult.size();
                int done = 0;
                for(TreeItem<CompResult> item: filteredResult)
                {
                    _activeCompareTree.overwriteEntryValue(item.getValue(),attName,true);
                    done++;
                    final double progressNr = (double)done/(double)nrOfEntries;
                    Platform.runLater(()->_main._ctManager._progressWindowController.setProgress(progressNr,item.getValue().getRDN() + " OK"));
                }
                Platform.runLater(()->
                {
                    try {
                        for(TreeItem<CompResult> item: filteredResult)
                        {

                            _activeCompareTree.reEvaluateCompareResultSubtree(item,
                                    _main._ctManager._ldapSourceExploreCtrl.get_currentConnection().getDisplayAttribute());
                            if(_activeCompareTree.equals(_compareTrees.get(attName)))
                            {
                                CompareTree mainTree = _compareTrees.get(MAIN_TREE_ID);
                                TreeItem<CompResult> copyOfSelectedEntry = mainTree.searchTreeItem(mainTree.getRoot(),item.getValue().get_dn());
                                mainTree.reEvaluateCompareResultSubtree(copyOfSelectedEntry,
                                        _main._ctManager._ldapSourceExploreCtrl.get_currentConnection().getDisplayAttribute());
                                mainTree.reEvaluateTree();

                            }
                            else
                            {
                                CompareTree attributeTree = _compareTrees.get(attName);
                                if(attributeTree != null)
                                {
                                    TreeItem<CompResult> copyOfSelectedEntry = attributeTree.searchTreeItem(attributeTree.getRoot(),item.getValue().get_dn());
                                   attributeTree.reEvaluateCompareResultSubtree(copyOfSelectedEntry,
                                            _main._ctManager._ldapSourceExploreCtrl.get_currentConnection().getDisplayAttribute());
                                    attributeTree.reEvaluateTree();

                                }
                            }
                        }
                        _activeCompareTree.reEvaluateTree();
                        _observedEntry = null;
                        _activeCompareTree.getSelectionModel().select(0);
                        _observedEntry = _activeCompareTree.getSelectionModel().getSelectedItem();
                        _activeCompareTree.autosize();
                        _activeCompareTree.refresh();
                        CompareTree tree =  _compareTrees.get(MAIN_TREE_ID);
                        HashSet<String> atts = tree.get_foundDifferentAttributes();
                        // TODO
                        // jetzt muss man den togglebutton oben entfernen wenn jetzt ein bestimmtes
                        // Attribut synchronisiert wurde
                        if(atts.isEmpty())
                        {
                            Node found = null;
                            for(Node ob: _foundAttributesHbox.getChildren())
                            {
                                if(ob instanceof ToggleButton)
                                {
                                    ToggleButton toggleButton = (ToggleButton) ob;
                                    if(toggleButton.getText().equalsIgnoreCase(attName))
                                    {
                                        found = toggleButton;
                                    }
                                }
                            }
                            if(found != null) _foundAttributesHbox.getChildren().remove(found);
                        }

                    }
                    catch (Exception e)
                    {
                        GuiHelper.EXCEPTION("Exception during refresh of the tree",e.getMessage(),e);
                        logger.error("Exception during refresh of the tree",e);
                    }

                });
            } catch (Exception e) {
                Platform.runLater(()->GuiHelper.EXCEPTION("Sync error",e.getMessage(),e));
                return;
            }
        };
        try {
           future = executor.submit(runnableTask);
        } catch (Exception e) {
            GuiHelper.EXCEPTION("Sync error",e.getMessage(),e);
        }
        try {
            future.get();
            _main._ctManager._progressWindowController._stage.hide();
            GuiHelper.INFO("SYNC Operation","Sync done");
        }
        catch (Exception e){
            GuiHelper.EXCEPTION("Sync error",e.getMessage(),e);
        }
    }


    private String normalizeText(String text) {
        if (text == null) return "";
        text = text.trim();
        text = text.replace("\n", " ");
        text = text.replace("\t", " ");
        while (text.contains("  ")) {
            text = text.replace("  ", " ");
        }
        return text;
    }

    @FXML
    private void initialize() {
    }

    private void initProgressWindow(Main main) throws IOException {
        FXMLLoader settingsLoader = new FXMLLoader();
        settingsLoader.setLocation(SettingsController.class.getResource(ControllerManager.Companion.fxmlDir("ProgressWindow.fxml")));
        _progressPane = (VBox) settingsLoader.load();
        _progressController = settingsLoader.getController();
        _progressScene = new Scene(_progressPane);
        _progressStage = new Stage();
        _progressStage.setTitle("PROGRESS");
        _progressStage.setScene(_progressScene);
        _progressStage.initStyle(StageStyle.DECORATED);
        _progressStage.initModality(Modality.APPLICATION_MODAL);
        _progressStage.initOwner(main.get_primaryStage());

        _progressController._buttonCancel.setOnAction(event -> {
            Platform.runLater(() -> _activeCompareTree.set_breakOperation(true));
            _progressStage.hide();
            _progressController.clearProgressWindow();
        });
        _progressStage.setOnCloseRequest(event -> Platform.runLater(() -> _activeCompareTree.set_breakOperation(true)));
    }

    void initFilterWindow() throws IOException {
        FXMLLoader loader_2 = new FXMLLoader();
        loader_2.setLocation(Main.class.getResource(ControllerManager.Companion.fxmlDir("FilterWindow.fxml")));
        _filterWindow = (VBox) loader_2.load();
        _filterWindowController = loader_2.getController();
        _filterWindowController.set_mainApp(_main);
        _filterWindowController.get_observableConfigAllAttributes().addAll(_main._configuration._allAttributes);
        _filterScene = new Scene(_filterWindow);
        _filterStage = new Stage();
        _filterStage.setScene(_filterScene);
        _filterStage.initStyle(StageStyle.DECORATED);
        _filterStage.initModality(Modality.APPLICATION_MODAL);
        _filterStage.initOwner(_main.get_primaryStage());
    }

    @Override
    public void setProgress(double progress, String description) {
        Platform.runLater(() -> _progressController.setProgress(progress, description));
    }

    @Override
    public void signalTaskDone(String taskName, String description, Exception e) {
        Platform.runLater(() -> {
            if (taskName.equalsIgnoreCase("compare")) {
                _observableDifferentSearchAttributes.clear();
                CompareTree compareTree = _compareTrees.get(MAIN_TREE_ID);
                if (compareTree.get_foundDifferentAttributes() != null && compareTree.get_foundDifferentAttributes().size() > 0) {
                    _entryMenuSyncAttributeForEntryAndAllChildrenToTarget.setText("Select attribute to sync to " + _currentTargetConnection.getName() );
                    _observableDifferentSearchAttributes.addAll(compareTree.get_foundDifferentAttributes());
                    if(_choiceBoxFoundDifferentAttributes.getItems().isEmpty())
                    {
                        _choiceBoxFoundDifferentAttributes.getSelectionModel().selectedItemProperty().removeListener(_choiceBoxFoundDifferentAttributesListener);
                        _choiceBoxFoundDifferentAttributesListener = (observableValue, number, number2) -> {
                            String selectedAttribute = _choiceBoxFoundDifferentAttributes.getSelectionModel().getSelectedItem();
                            if(selectedAttribute==null) return;
                            showOnlyAttribute(selectedAttribute);
                            _entryMenuSyncAttributeForEntryAndAllChildrenToTarget.setText("Sync Attributes to " +_currentTargetConnection.getName());
                        };
                        _choiceBoxFoundDifferentAttributes.getItems().add(MAIN_TREE_ID);
                        _choiceBoxFoundDifferentAttributes.getItems().addAll(compareTree.get_foundDifferentAttributes());
                        _choiceBoxFoundDifferentAttributes.getSelectionModel().select(MAIN_TREE_ID);
                        _choiceBoxFoundDifferentAttributes.getSelectionModel().selectedItemProperty().addListener(_choiceBoxFoundDifferentAttributesListener);
                    }
                }
                if (_vboxCompareTree.getChildren().contains(_foundAttributesHbox)) {
                    if (_foundAttributesHbox.getChildren().isEmpty()) {
                        _vboxCompareTree.getChildren().remove(_foundAttributesHbox);
                    }
                } else {
                    if (!_foundAttributesHbox.getChildren().isEmpty()) {
                        _vboxCompareTree.getChildren().add(0, _foundAttributesHbox);
                    }
                }
                _observableDifferentSearchAttributes.sort(Comparator.comparing(String::toString));


            }
            if (taskName.equalsIgnoreCase("copy")) {
                try {
                    _progressController.setProgress(0.9, "Reevaluating tree now stage 1 ");
                    _activeCompareTree.reEvaluateCompareResultSubtree(_observedEntry, _main._ctManager._ldapSourceExploreCtrl.get_currentConnection().getDisplayAttribute());
                    _progressController.setProgress(0.9, "Reevaluating tree now stage 2 ");
                    _activeCompareTree.reEvaluateTree();
                    _observedEntry = null;
                    _activeCompareTree.getSelectionModel().select(0);
                    _observedEntry = _activeCompareTree.getSelectionModel().getSelectedItem();
                    _activeCompareTree.autosize();
                    _activeCompareTree.refresh();
                } catch (Exception exc) {
                    GuiHelper.EXCEPTION("Error copy entry", "Exception occured", exc);
                }
            }
            _progressStage.hide();
            _progressController.clearProgressWindow();
        });
    }

    private void showOnlyAttribute(String attribute) {
        CompareTree compareTree = null;
        if (_compareTrees.get(attribute) != null) {
            compareTree = _compareTrees.get(attribute);
            _vboxCompareTree.getChildren().remove(_activeCompareTree);
            _activeCompareTree = compareTree;
            _vboxCompareTree.getChildren().add(_activeCompareTree);

        } else {
            compareTree = _compareTrees.get(MAIN_TREE_ID).clone();
            initCompareTree(compareTree);
            compareTree.setRoot(new TreeItem<>(_compareTrees.get(MAIN_TREE_ID).getRoot().getValue().clone()));
            List<String> attributes = new ArrayList<>();
            attributes.add(attribute);
            createCopyWithSearchAttributes(_compareTrees.get(MAIN_TREE_ID).getRoot(), compareTree.getRoot(), attributes);
            _compareTrees.put(attribute, compareTree);
            _vboxCompareTree.getChildren().remove(_activeCompareTree);
            _activeCompareTree = compareTree;
            _vboxCompareTree.getChildren().add(_activeCompareTree);
            _activeCompareTree.reEvaluateTree();
        }
    }

    @Override
    public void setProgress(String taskName, double progress) {   }


    private boolean confirmLdapModification(HashMap<TreeItem<CompResult>, List<String>> modifications, String header, boolean target) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Modification");
        alert.setHeaderText(header);
        alert.setResizable(true);
        Label label = new Label("LDAP-Modification:");
        label.setStyle("-fx-fill: #ff0000;-fx-font-weight:bold;");
        TextArea textArea = new TextArea();
        for (TreeItem<CompResult> cr : modifications.keySet()) {
            List<Modification> mods = cr.getValue().get_ModificationsToTarget(modifications.get(cr));
            ModifyRequest modRequest = null;
            if (!target) {
                modRequest = new ModifyRequest(cr.getValue().get_dn(), mods);
            } else {
                modRequest = new ModifyRequest(cr.getValue().get_targetEntry().getDN(), mods);
            }
            String ldif[] = modRequest.toLDIF();
            for (String s : ldif) {
                textArea.appendText(s + "\n");
            }
        }
        GridPane.setVgrow(textArea, Priority.ALWAYS);
        GridPane.setHgrow(textArea, Priority.ALWAYS);
        textArea.setMaxWidth(Double.MAX_VALUE);
        textArea.setMaxHeight(Double.MAX_VALUE);
        GridPane expContent = new GridPane();
        expContent.setMaxHeight(Double.MAX_VALUE);
        expContent.setMaxWidth(Double.MAX_VALUE);
        expContent.setMaxWidth(Double.MAX_VALUE);
        expContent.add(label, 0, 0);
        expContent.add(textArea, 0, 1);
        alert.getDialogPane().setContent(expContent);
        alert.showAndWait();
        if (alert.getResult().equals(ButtonType.OK)) return true;
        return false;
    }

    private void copyEntryToTarget() {
        try {
            _activeCompareTree.copyObjectToTarget(_observedEntry, false);
            _activeCompareTree.reEvaluateCompareResultSubtree(_observedEntry,
                    _main._ctManager._ldapSourceExploreCtrl.get_currentConnection().getDisplayAttribute());
            _activeCompareTree.reEvaluateTree();
            _observedEntry = null;
            _activeCompareTree.getSelectionModel().select(0);
            _observedEntry = _activeCompareTree.getSelectionModel().getSelectedItem();
            _activeCompareTree.autosize();
            _activeCompareTree.refresh();
        } catch (Exception e) {
            GuiHelper.EXCEPTION("Copy object to target",
                    "Object copy error->" + _observedEntry.getValue().get_dn(), e);
        }
    }

    private void copyEntryToSource() {
        try {
            _activeCompareTree.copyObjectToSource(_observedEntry.getValue().get_dn(), false);
            _activeCompareTree.reEvaluateCompareResultSubtree(_observedEntry,
                    _main._ctManager._ldapSourceExploreCtrl.get_currentConnection().getDisplayAttribute());
            _activeCompareTree.reEvaluateTree();
            _observedEntry = null;
            _activeCompareTree.getSelectionModel().select(0);
            _observedEntry = _activeCompareTree.getSelectionModel().getSelectedItem();
            _activeCompareTree.autosize();
            _activeCompareTree.refresh();
        } catch (Exception e) {
            GuiHelper.EXCEPTION("Copy object to source",
                    "Object copy error->" + _observedEntry.getValue().get_dn(), e);
        }
    }



    public void copyAttributeValueToTarget(List<String> attributeNames) {
        ObservableList<TreeItem<CompResult>> selectedEntries = _activeCompareTree.getSelectionModel().getSelectedItems();
        HashMap<TreeItem<CompResult>, List<String>> entriesToBeModified = new HashMap<>();
        List<TreeItem<CompResult>> allSelected = new ArrayList<>();
        for (TreeItem<CompResult> selectedEntry : selectedEntries) {
            allSelected.add(selectedEntry);
            List<TreeItem<CompResult>> children = null;
            children = _activeCompareTree.getAllChildren(selectedEntry, children);
            allSelected.addAll(children);
        }
        for (String attributeName : attributeNames) {
            if (_currentTargetConnection.OperationalAttributes.contains(attributeName)) continue;
            for (TreeItem<CompResult> selectedEntry : allSelected) {
                if (selectedEntry.getValue().getDifferentAttributes().contains(attributeName)) {
                    if (entriesToBeModified.containsKey(selectedEntry.getValue())) {
                        entriesToBeModified.get(selectedEntry.getValue()).add(attributeName);
                    } else {
                        List<String> attributes = null;
                        attributes = entriesToBeModified.get(selectedEntry);
                        if (attributes == null) attributes = new ArrayList<>();
                        attributes.add(attributeName);
                        entriesToBeModified.put(selectedEntry, attributes);
                    }
                }
            }
        }
        if (entriesToBeModified.isEmpty()) {
            GuiHelper.INFO("Entry Modification", "Found no modification to commit, operational attribute selected ?");
            return;
        }
        if (confirmLdapModification(entriesToBeModified, "Copy " + attributeNames + System.lineSeparator() + " from " +
                _activeCompareTree.get_sourceConnection().getName() + System.lineSeparator() +
                " to " + _activeCompareTree.get_targetConnection().getName() + " ?", true)) {
            for (TreeItem<CompResult> cr : entriesToBeModified.keySet())
                for (String attributeName : entriesToBeModified.get(cr)) {
                    try {
                        _activeCompareTree.overwriteEntryValue(cr.getValue(), attributeName, true);
                        _activeCompareTree.reEvaluateCompareResultSubtree(cr, _main._ctManager._ldapSourceExploreCtrl.get_currentConnection().getDisplayAttribute());
                    } catch (Exception e) {
                        GuiHelper.EXCEPTION("Error, operation failed", "Entry not changed->"
                                + cr.getValue().getRDN() + " and attribute->" + attributeName, e);
                        return;
                    }
                }
        }
        _activeCompareTree.autosize();
        _activeCompareTree.refresh();
        _entryDiffView.updateValue(_observedEntry.getValue(), _checkBoxShowEqual.isSelected());
    }



    private void createCopyWithSearchAttributes(TreeItem<CompResult> source, TreeItem<CompResult> target, List<String> attributes) {
        for (TreeItem<CompResult> s : source.getChildren()) {
            CompResult cloned = s.getValue().clone();
            logger.debug("Evaluate ->" + cloned.getRDN());
            if (cloned.get_compare_result().equals(CompResult.COMPARE_RESULT.ONLY_IN_SOURCE)) {
                logger.info("only in source->" + cloned.getRDN());
                continue;
            }
            if (cloned.get_compare_result().equals(CompResult.COMPARE_RESULT.ONLY_IN_TARGET)) {
                logger.debug("only in target->" + cloned.getRDN());
                continue;
            }
            if (cloned.get_compare_result().equals(CompResult.COMPARE_RESULT.ENTRY_EQUAL)) {
                logger.debug("skip equal->" + cloned.getRDN());
                continue;
            }
            if (cloned.get_compare_result().equals(CompResult.COMPARE_RESULT.ENTRY_EQUAL_BUT_CHILDREN_NOT)) {
                logger.info("children not equal for->" + cloned.getRDN());
                TreeItem<CompResult> targetEntry = new TreeItem<>(cloned);
                target.getChildren().add(targetEntry);
                createCopyWithSearchAttributes(s, targetEntry, attributes);
            } else if (cloned.get_compare_result().equals(CompResult.COMPARE_RESULT.ENTRY_NOT_EQUAL)) {
                logger.debug("check not equal ->" + cloned.getRDN());
                List<Modification> mod2Target = cloned.get_ModificationsToTarget(attributes);
                List<Modification> mod2Source = cloned.get_ModificationsToSource(attributes);
                if (mod2Source.isEmpty() && mod2Target.isEmpty()) {
                    if (s.getChildren().isEmpty()) {
                        logger.info("entry is equal and has no children->" + cloned.getRDN());
                        continue;
                    }
                    logger.info("entry is equal->" + cloned.getRDN());
                    cloned.set_compare_result(CompResult.COMPARE_RESULT.ENTRY_EQUAL);
                    cloned.set_entryEqual(true);
                    TreeItem<CompResult> targetEntry = new TreeItem<>(cloned);
                    target.getChildren().add(targetEntry);
                    createCopyWithSearchAttributes(s, targetEntry, attributes);
                } else {
                    logger.debug("entry not  equal->" + cloned.getRDN());
                    TreeItem<CompResult> targetEntry = new TreeItem<>(cloned);
                    target.getChildren().add(targetEntry);
                    createCopyWithSearchAttributes(s, targetEntry, attributes);
                }
            } else {
                logger.error("what result is this?");
            }
        }
    }


    public void set_attributeDifferences(String source, String target) {
        _textFlowSourceValue.getChildren().clear();
        _textFlowTargetValue.getChildren().clear();
        if (source == null && target == null) return;
        Set<Integer> bs = AttributeDifference.compareStringCharByChar(source, target);
        if (source != null) {
            char[] src = source.toCharArray();
            for (int i = 0; i < src.length; i++) {
                String same = "-fx-fill: #4F8A10";
                String different = "-fx-fill: #ff0000;-fx-font-weight:bold;";
                Text t1 = new Text();
                t1.setText(String.valueOf(src[i]));
                if (bs.contains(i)) t1.setStyle(different);
                else t1.setStyle(same);
                _textFlowSourceValue.getChildren().add(t1);
            }
        }
        if (target != null) {
            Text t1 = new Text();
            t1.setStyle("-fx-fill: #4F8A10");
            t1.setText(target);
            _textFlowTargetValue.getChildren().addAll(t1);
        }
    }
}