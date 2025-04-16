package ch.vilki.jfxldap.gui;

import ch.vilki.jfxldap.Main;
import ch.vilki.jfxldap.backend.*;
import com.unboundid.ldap.sdk.*;
import com.unboundid.ldif.LDIFException;
import com.unboundid.ldif.LDIFReader;
import com.unboundid.util.ssl.SSLUtil;
import com.unboundid.util.ssl.TrustAllTrustManager;
import javax.net.ssl.SSLSocketFactory;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPConnectionOptions;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPResult;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.BindResult;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.MenuItem;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.*;
import javafx.util.Callback;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LdapExploreController implements IProgress, ILoader {

    static Logger logger = LogManager.getLogger(LdapExploreController.class);

    /***************** GUI ELEMENTS *****************************************/

    @FXML
    TreeView<CustomEntryItem> _treeView;
    @FXML
    TreeView<CollectionEntry> _collectionTree;

    @FXML ChoiceBox<Connection> _choiceBoxEnviroment;
    private ChoiceBox<String> _choiceBoxTag;
    @FXML Button _buttonConnect;
    @FXML Button _buttonDisconnect;
    @FXML Button _buttonOpenFile;
    @FXML Button _buttonUploadFile;
    @FXML Button _buttonCloseFile;
    @FXML Button _buttonRunLdapSearch;
    @FXML Button _buttonRemoveFilter;
    @FXML Button _buttonReload; 
    @FXML HBox _hboxFilter;
    private TextFieldLdapFilter _textFieldLdapFilter = new TextFieldLdapFilter();


    Scene _scene;
    Stage _stage;
    VBox _exploreWindow;

    final ContextMenu _contextMenu = new ContextMenu();
    static String TAB = "     ";
    MenuItem _compareItem = new MenuItem(TAB + "Compare", Icons.get_iconInstance().getIcon(Icons.ICON_NAME.COMPARE_SMALL));
    MenuItem _search = new MenuItem(TAB + "Search", Icons.get_iconInstance().getIcon(Icons.ICON_NAME.SEARCH_SMALL));
    MenuItem _setDisplayAttribute = new MenuItem(TAB + "Set display attribute", Icons.get_iconInstance().getIcon(Icons.ICON_NAME.SET_ATTRIBUTE_SMALL));
    MenuItem _export = new MenuItem(TAB + "Export", Icons.get_iconInstance().getIcon(Icons.ICON_NAME.EXPORT_SMALL));
    MenuItem _clipBoardLDIF = new MenuItem(TAB + "Clipboard LDIF", Icons.get_iconInstance().getIcon(Icons.ICON_NAME.COPY_PASTE_SMALL));
    MenuItem _deleteEntry = new MenuItem(TAB + "Delete", Icons.get_iconInstance().getIcon(Icons.ICON_NAME.REMOVE));
    MenuItem _setPassword = new MenuItem(TAB + "Set Password", Icons.get_iconInstance().getIcon(Icons.ICON_NAME.PASSWORD));
    MenuItem _verifyPassword = new MenuItem(TAB + "Verify Password", Icons.get_iconInstance().getIcon(Icons.ICON_NAME.PASSWORD));
    MenuItem _exportAttribute = new MenuItem(TAB + "Export Attribute", Icons.get_iconInstance().getIcon(Icons.ICON_NAME.EXPORT_SMALL));
    MenuItem _ldifEditor = new MenuItem(TAB + "Batch Modify", Icons.get_iconInstance().getIcon(Icons.ICON_NAME.REFRESH));

    /*------------- PROGRESS PANE ----------------------- */
    @FXML
    VBox _progressPane;
    Stage _progressStage;
    Scene _progressScene;

    /************************************************************************/


    public void set_collectionTree(TreeView<CollectionEntry> _collectionTree) {
        this._collectionTree = _collectionTree;
    }

    private boolean _ldapExploreMode = true;
    private boolean _fileMode = false;
    ChangeListener _expandedListenerOnline = null;
    ChangeListener _expandedListenerFile = null;
    private LdapExploreController _this;
    Main _main = null;
    Connection _currConnection = null;
    UnboundidLdapSearch _currentReader;
    ExecutorService _executor = Executors.newFixedThreadPool(5);
    String _lastSelectedDirectory;
    String _selectedDN;
    TreeItem<CustomEntryItem> _observedEntry;
    private static LDIFReader _ldifReader = null;
    private boolean _breakFileLoad = false;
    private static String _openedLDIFFile = null;
    private boolean _ldapExplorerTargetAction = false;
    private ProgressWindowController _progressController = null;
    private boolean _iAmTargetExplorer;
    Event _connectionEstablishedEvent = new LdapExplorerEvent(LdapExplorerEvent.CONNECTION_ESTABLISHED);

    public String get_selectedDN() {
        return _selectedDN;
    }
    public Connection get_currentConnection() {
        return _currConnection;
    }
    public void set_currentConnection(Connection _currentConnection) {
        _currConnection = _currentConnection;
    }
    public boolean is_ldapExplorerTargetAction() {
        return _ldapExplorerTargetAction;
    }
    public void set_ldapExplorerTargetAction(boolean _ldapExplorerTargetAction) {
        this._ldapExplorerTargetAction = _ldapExplorerTargetAction;
    }

    public VBox get_window() {
        return _exploreWindow;
    }

    Comparator<TreeItem<CustomEntryItem>> _treeItemCustomComparator =
            Comparator.comparing((TreeItem<CustomEntryItem> one) -> one.getValue().getDn());

    Map<String, TreeItem<CustomEntryItem>> _allTreeEntries = new HashMap<>();


    @Override
    public void setWindow(Parent parent) {

        _exploreWindow = (VBox) parent;
        _scene = new Scene(_exploreWindow);
        _stage = new Stage();
        _stage.setScene(_scene);
        _exploreWindow.getChildren().add(_treeView);
        HBox hBox = (HBox) _exploreWindow.getChildren().get(1);
        hBox.getChildren().add(1, _textFieldLdapFilter);
        HBox.setHgrow(_textFieldLdapFilter, Priority.ALWAYS);
        _textFieldLdapFilter.alignmentProperty().setValue(Pos.BOTTOM_LEFT);
        HBox.setMargin(_textFieldLdapFilter, new Insets(5, 5, 1, 2));
        
        // Create tag ChoiceBox if it's null (not defined in FXML)
        if (_choiceBoxTag == null) {
            _choiceBoxTag = new ChoiceBox<>();
            _choiceBoxTag.setTooltip(new Tooltip("Filter connections by tag"));
        }
        
        // Find the HBox that contains the environment ChoiceBox
        for (int i = 0; i < _exploreWindow.getChildren().size(); i++) {
            if (_exploreWindow.getChildren().get(i) instanceof HBox) {
                HBox controlsHBox = (HBox) _exploreWindow.getChildren().get(i);
                // Look for the environment ChoiceBox in this HBox
                for (int j = 0; j < controlsHBox.getChildren().size(); j++) {
                    if (controlsHBox.getChildren().get(j) == _choiceBoxEnviroment) {
                        // Insert the tag ChoiceBox just before the environment ChoiceBox
                        controlsHBox.getChildren().add(j, _choiceBoxTag);
                        HBox.setMargin(_choiceBoxTag, new Insets(0, 5, 0, 0));
                        break;
                    }
                }
            }
        }

        // Add key event handler for F5 refresh
        _treeView.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.F5) {
                refreshSelectedEntry();
            }
        });

        _treeView.setCellFactory(new Callback<>() {
            @Override
            public TreeCell<CustomEntryItem> call(TreeView<CustomEntryItem> param) {
                return new TreeCell<>() {
                    @Override
                    protected void updateItem(CustomEntryItem item, boolean empty) {
                        super.updateItem(item,empty);
                        textProperty().unbind();
                        styleProperty().unbind();
                        graphicProperty().unbind();
                        if (empty || item == null) {
                            setGraphic(null);
                            textProperty().set(null);
                            styleProperty().set(null);
                            return;
                        }
                        textProperty().bind(item._rdn);
                        if (!item.is_dummy()) {
                            setGraphic(Icons.get_iconInstance().getObjectType(item.get_objectClass()));
                        }
                        else  styleProperty().set(item.get_styleProperty().getValue());
                    }
                };
            }
        });

    }

    @Override
    public void setOwner(Stage stage) {
    }

    void initProgressWindow(Main main) {
        FXMLLoader settingsLoader = new FXMLLoader();
        settingsLoader.setLocation(SettingsController.class.getResource(ControllerManager.Companion.fxmlDir("ProgressWindow.fxml")));
        try {
            _progressPane = (VBox) settingsLoader.load();
        } catch (IOException e) {
            GuiHelper.EXCEPTION("Initialization error", "Exception loading progress window", e);
            e.printStackTrace();
        }
        _progressController = settingsLoader.getController();
        _progressScene = new Scene(_progressPane);
        _progressStage = new Stage();
        _progressStage.setTitle("PROGRESS");
        _progressStage.setScene(_progressScene);
        _progressStage.initStyle(StageStyle.DECORATED);
        _progressStage.initModality(Modality.APPLICATION_MODAL);
        _progressStage.initOwner(main.get_primaryStage());

        _progressController._buttonCancel.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                _progressStage.hide();
                _breakFileLoad = true;
                _progressController.clearProgressWindow();
            }
        });
        _progressStage.setOnCloseRequest(new EventHandler<WindowEvent>() {
            @Override
            public void handle(WindowEvent event) {
                _breakFileLoad = true;
            }
        });
        _hboxFilter.setDisable(true);
    }

    private TreeItem<CustomEntryItem> findChild(TreeItem<CustomEntryItem> entry, String rdn) {
        if (entry.getChildren() == null || entry.getChildren().isEmpty()) return null;
        for (TreeItem<CustomEntryItem> e : entry.getChildren()) {
            String comapareRdn = e.getValue().getRdn();
            if (comapareRdn != null && comapareRdn.equalsIgnoreCase(rdn)) return e;
        }
        return null;
    }

    private void setFileMode(boolean fileMode) {
        _fileMode = fileMode;
        _buttonCloseFile.setDisable(!_fileMode);
        _buttonConnect.setDisable(_fileMode);
        _buttonDisconnect.setDisable(_fileMode);
    }

    public void setTargetExplorerMode() {
        _contextMenu.getItems().remove(_search);
        _iAmTargetExplorer = true;
    }

    private void setIcons(TreeItem<CustomEntryItem> entryTreeItem) {
        if (entryTreeItem == null) return;
        for (TreeItem<CustomEntryItem> child : entryTreeItem.getChildren()) {
            if (child.getValue().is_dummy())
                child.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.SUBFOLDER_NOT_EQUAL));
            else child.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.ENTRY_EQUAL));
            setIcons(child);
        }
    }

    private void runLdapSearch() {
        if (_observedEntry == null) return;
        try {
            if (_iAmTargetExplorer) _main._ctManager._startSearchController .set_ldapExplorerTargetAction(true);
            else _main._ctManager._startSearchController .set_ldapExplorerTargetAction(false);
            if (!_textFieldLdapFilter.is_filterOK()) return;
            Filter f = _textFieldLdapFilter.get_filter();
            _main._ctManager._startSearchController .runLdapSearch(_observedEntry.getValue().getDn(), f);
        } catch (Exception exc) {
            return;
        }
    }

    private void setNewDisplayAttribute(TreeItem<CustomEntryItem> treeItem, String attributeName) {
        if (treeItem == null) return;
        if (treeItem.getValue() == null || treeItem.getValue() == null || treeItem.getValue().getEntry() == null)
            return;
        try {
            Entry entry = get_currentConnection().getEntry(treeItem.getValue().getEntry().getDN(), new String[]{attributeName});
            Platform.runLater(() -> {
                String dn = "";
                if(entry!=null) dn = entry.getDN();
                _progressController.setProgress(0, dn);
            });
            if (treeItem.getValue() != null && entry != null) {
                treeItem.getValue().setEntry(entry);
                treeItem.getValue().setDisplayAttribute(attributeName);
            }
            _treeView.refresh();
        } catch (LDAPException e) {
            logger.error(e);
        }
        for (TreeItem<CustomEntryItem> item : treeItem.getChildren()) setNewDisplayAttribute(item, attributeName);
    }

    @FXML
    private void initialize() {
        _buttonUploadFile.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.UPLOAD_FILE));
        _buttonCloseFile.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.CLOSE_FILE));
        setFileMode(false);
        _setDisplayAttribute.setOnAction(x -> {
            String enterValue = GuiHelper.enterValue("Input required", "Enter display attribute name", "Display attribute",
                    get_currentConnection().getSchemaAttributes());
            if (enterValue == null) return;

            logger.info("Selected attribute is->" + enterValue);
            _progressStage.show();
            setNewDisplayAttribute(_treeView.getSelectionModel().getSelectedItem(), enterValue);
            _currConnection.setDisplayAttribute(enterValue);
            _progressStage.close();
        });

        _export.setOnAction(x -> {
            TreeItem<CustomEntryItem> selectedItem = _treeView.getSelectionModel().getSelectedItem();
            if (selectedItem == null) return;
            _main._ctManager._exportWindowController.showExportWindow(get_currentConnection(), selectedItem.getValue().getDn());

        });

        _clipBoardLDIF.setOnAction(x -> {
            if (_observedEntry == null) return;
            if (_observedEntry.getValue() == null || _observedEntry.getValue().getEntry() == null) return;
            String[] ldif = _observedEntry.getValue().getEntry().toLDIF(1000);
            StringBuilder stringBuilder = new StringBuilder();
            for (String s : ldif) {
                stringBuilder.append(s);
                stringBuilder.append(System.lineSeparator());
            }
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                    new StringSelection(stringBuilder.toString()), null
            );
        });

        _deleteEntry.setOnAction(x -> {
            deleteEntry();
        });

        _compareItem.setOnAction(e -> {
            if (_iAmTargetExplorer) {
                set_ldapExplorerTargetAction(true);
                if (_main._ctManager._ldapSourceExploreCtrl.get_currentConnection() != null &&
                        _main._ctManager._ldapSourceExploreCtrl._treeView.getSelectionModel().getSelectedItem() != null) {
                    _main._ctManager._startLdapCompareController._textFieldSelectedSourceDN.setText(
                            _main._ctManager._ldapSourceExploreCtrl._treeView.getSelectionModel().getSelectedItem().getValue().getDn());
                    _main._ctManager._startLdapCompareController.setTargetSelectedView(
                            _treeView.getSelectionModel().getSelectedItem().getValue().getDn(), _choiceBoxEnviroment.getSelectionModel().getSelectedIndex());
                } else {
                    GuiHelper.ERROR("Compare Error", "Select source first");
                    return;
                }
            } else {
                set_ldapExplorerTargetAction(false);
                _main._ctManager._startLdapCompareController._textFieldSelectedSourceDN.setText(
                        _treeView.getSelectionModel().getSelectedItem().getValue().getDn());
                _main._ctManager._startLdapCompareController.setTargetSelectedView(
                        _treeView.getSelectionModel().getSelectedItem().getValue().getDn(), -1);
            }
            _main._ctManager._startLdapCompareController.getEmbeddedFilterViewController().setSchemaAttributes(get_currentConnection().SchemaAttributes);
            _main._ctManager._startLdapCompareController._stage.showAndWait();
        });


        _search.setOnAction(e -> {
            if (_iAmTargetExplorer) {
                _main._ctManager._startSearchController .set_ldapExplorerTargetAction(true);
            } else {
                _main._ctManager._startSearchController .set_ldapExplorerTargetAction(false);
            }
            _main._ctManager._startSearchController ._textFieldSearchDN.setText(_treeView.getSelectionModel().getSelectedItem().getValue().getDn());
            _main._ctManager._startSearchController .getEmbeddedFilterViewController().setSchemaAttributes(get_currentConnection().SchemaAttributes);
            _main._ctManager._startSearchController .windowOpened();
            _main._ctManager._startSearchController ._stage.showAndWait();
        });
        _setPassword.setOnAction(e -> setUserPassword());
        _verifyPassword.setOnAction(e -> verifyPassword());
        _exportAttribute.setOnAction(e -> _main._ctManager._exportAttributeController.showExportAttributeWindow(get_currentConnection(), _observedEntry.getValue().getDn()));
        _ldifEditor.setOnAction(e -> openLdifEditor());
        _contextMenu.getItems().addAll(_search, _compareItem, _setDisplayAttribute, _export, _clipBoardLDIF, 
                _deleteEntry, _setPassword, _verifyPassword, _exportAttribute, _ldifEditor);
        _contextMenu.getItems().forEach(x -> x.setStyle(Styling.SMALL_MENU_TEXT_BOLD));
        _buttonConnect.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.CONNECT_ICON));
        _buttonDisconnect.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.DISCONNECT_ICON));
        _buttonOpenFile.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.OPEN_FILE));
        _buttonRunLdapSearch.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.FILTER_ADD));
        _buttonRemoveFilter.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.FILTER_REMOVE));
        _buttonRunLdapSearch.setOnAction(x -> runLdapSearch());
        _textFieldLdapFilter.setOnKeyPressed(e -> {
            if (e.getCode().equals(KeyCode.ENTER)) {
                runLdapSearch();
            }
        });
        _buttonRemoveFilter.setOnAction(e -> _textFieldLdapFilter.clear());
        _buttonOpenFile.setOnAction(x -> loadFile());
        _buttonCloseFile.setOnAction(e -> {
            setFileMode(false);
            disconnect();
        });

        _buttonUploadFile.setOnAction(e -> uploadFile());
        _buttonDisconnect.setOnAction(e -> disconnect());
        _choiceBoxEnviroment.setTooltip(new Tooltip("Select connection from the configuration"));
        _buttonConnect.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                
                if (_ldapExploreMode) connect(true);
                else  _main._ctManager._collectionsController.connect(_choiceBoxEnviroment.getSelectionModel().getSelectedItem());
            }
        });

        _buttonReload.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.REFRESH));
        _buttonReload.setOnAction(e -> refreshSelectedEntry());

        _expandedListenerOnline = (ChangeListener<Boolean>) (observable, oldValue, newValue) -> {
            BooleanProperty bb = (BooleanProperty) observable;

            TreeItem t = (TreeItem<CustomEntryItem>) bb.getBean();
            _observedEntry = t;
            _treeView.fireEvent(new LdapExplorerEvent(LdapExplorerEvent.ELEMENT_SELECTED, _observedEntry));
            _main._ctManager._progressWindowController.clearProgressWindow();
            if (_observedEntry.isExpanded()) {
                if (_observedEntry.getChildren().size() == 1 && _observedEntry.getChildren().get(0).getValue().is_dummy()) {
                    _observedEntry.getChildren().get(0).expandedProperty().removeListener(_expandedListenerOnline);
                    _observedEntry.getChildren().clear();
                    _progressStage.show();
                    _currentReader = new UnboundidLdapSearch(_main._configuration, get_currentConnection(),
                            _observedEntry.getValue().getEntry().getDN(), null, _this);
                    if (get_currentConnection().getDisplayAttribute() != null) {
                        _currentReader.setDisplayAttribute(get_currentConnection().getDisplayAttribute());
                    }
                    _currentReader.setReadAttributes(UnboundidLdapSearch.READ_ATTRIBUTES.none);
                    ExecutorService executor = Executors.newSingleThreadExecutor();
                    executor.submit(() -> _executor.execute(_currentReader));
                }
            }
            else{
                _treeView.autosize();
                _treeView.refresh();

            }
            /*
            else {
                _treeView.getRoot().getChildren().forEach(x->{
                    _treeView.refresh();
                });
            }

             */
        };

        _expandedListenerFile = (ChangeListener<Boolean>) (observable, oldValue, newValue) -> {
            BooleanProperty bb = (BooleanProperty) observable;
            TreeItem t = (TreeItem<CustomEntryItem>) bb.getBean();
            _observedEntry = t;
            _treeView.fireEvent(new LdapExplorerEvent(LdapExplorerEvent.ELEMENT_SELECTED, _observedEntry));
            if (_observedEntry.isExpanded()) {
                if (_observedEntry.getChildren().size() > 10) {
                    _observedEntry.getValue().setRdn(_observedEntry.getValue().getRdn() + " [" + _observedEntry.getChildren().size() + "]");
                }
                _observedEntry.getChildren().sort(_treeItemCustomComparator);
            }
        };
    }

    public void refreshTree() {
        if (_currConnection == null || _treeView.getRoot() == null) return;
        refreshTree_checkMissingEntries(_treeView.getRoot());
        _allTreeEntries.clear();
        getAllEntries(_treeView.getRoot());
        for (String k : _allTreeEntries.keySet()) {
            if (_allTreeEntries.get(k).isExpanded()) refreshTree_checkAddedEntries(_allTreeEntries.get(k));
        }
    }


    private void getAllEntries(TreeItem<CustomEntryItem> item) {
        _allTreeEntries.put(item.getValue().getDn(), item);
        for (TreeItem<CustomEntryItem> child : item.getChildren()) {
            _allTreeEntries.put(child.getValue().getDn(), child);
            getAllEntries(child);
        }
    }

    public void deleteEntry() {
        if (_observedEntry == null) return;
        
        boolean hasChildren = false;
        
        // First check if numSubOrdinates attribute is available
        Entry entry = _observedEntry.getValue().getEntry();
        if (entry != null) {
            Attribute numSubOrdinatesAttr = entry.getAttribute("numSubOrdinates");
            if (numSubOrdinatesAttr != null) {
                try {
                    int numSubOrdinates = Integer.parseInt(numSubOrdinatesAttr.getValue());
                    hasChildren = numSubOrdinates > 0;
                } catch (NumberFormatException e) {
                    // Fall back to search if parsing fails
                    hasChildren = _currentReader.getOneChild(_observedEntry.getValue().getDn()) != null;
                }
            } else {
                // Fall back to old method when numSubOrdinates is not available
                hasChildren = _currentReader.getOneChild(_observedEntry.getValue().getDn()) != null;
            }
        }
        
        if (!hasChildren) {
            logger.debug("Deleting only one child {}", _observedEntry.getValue().getDn());
            try {
                logger.info("DELETING NOW-> {}" + _observedEntry.getValue().getDn());
                if(!GuiHelper.confirm("Delete","Confirm deletion",_observedEntry.getValue().getDn())) return;

                _currConnection.delete(_observedEntry.getValue().getDn());
                _observedEntry.getParent().getChildren().remove(_observedEntry);
                _observedEntry = null;
            } catch (Exception e) {
                GuiHelper.EXCEPTION("Error deleting entry", e.getMessage(), e);
            }
        } else {
            _main._ctManager._deleteEntriesController.show(_observedEntry, _currConnection, _selectedDN);
        }
    }
    private void uploadFile() {
        if (_choiceBoxEnviroment.getSelectionModel().isEmpty()) {
            GuiHelper.ERROR("Enviroment selection error ", "Select enviroment first");
            return;
        }
        if (!GuiHelper.confirm("Import LDIF",
                "Import LDIF file in->" + _choiceBoxEnviroment.getSelectionModel().getSelectedItem().getName(),
                "Import all entries from the file in selected LDAP enviroment?")) return;

        try {
            connect(false);
        } catch (Exception exc) {
            GuiHelper.EXCEPTION("Connection error",
                    "Could not connect to enviroment->" + _choiceBoxEnviroment.getSelectionModel().getSelectedItem().getName(), exc);
            return;
        }
        if (_openedLDIFFile == null) {
            GuiHelper.ERROR("File error", "File not loaded");
            return;
        }
        _progressStage.show();
        _executor.submit(() -> {
            CollectionImport collectionImport = new CollectionImport();
            collectionImport.loadFile(new File(_openedLDIFFile), _main._ctManager._ldapSourceExploreCtrl, null);
            try {
                collectionImport.importInEnviroment(false, get_currentConnection(), CollectionImport.IMPORT_OPTIONS.ADD_OR_MODIFY);
                Platform.runLater(() -> {
                    _progressStage.hide();
                    GuiHelper.INFO("File loaded", "LDIF File has been loaded");
                });
            } catch (Exception exc) {
                Platform.runLater(() -> {
                    _progressStage.hide();
                    GuiHelper.EXCEPTION("Error Loading file", exc.getMessage(), exc);
                });
            }
        });
    }

    private void loadFile() {

        FileChooser chooser = new FileChooser();
        if (_lastSelectedDirectory == null) _lastSelectedDirectory = System.getProperty("user.home");
        chooser.setTitle("Open LDIF File");
        File defaultDirectory = new File(_main._configuration.get_lastUsedDirectory());
        chooser.setInitialDirectory(defaultDirectory);
        FileChooser.ExtensionFilter ldifFilter = new FileChooser.ExtensionFilter("LDIF files (*.ldif)", "*.ldif");
        chooser.getExtensionFilters().add(ldifFilter);
        File selectedFile = chooser.showOpenDialog(_main.get_primaryStage());
        if (selectedFile == null) return;
        _lastSelectedDirectory = selectedFile.getParent();
        _main._configuration.set_lastUsedDirectory(_lastSelectedDirectory);
        
        if (selectedFile.getName().toLowerCase().endsWith(".ldif")) {
            _openedLDIFFile = selectedFile.getAbsolutePath();
            try {
                uploadLDIFFile(selectedFile);
            } catch (IOException | LDIFException e) {
                GuiHelper.EXCEPTION("Error loading file", e.getMessage(), e);
                return;
            }
        }
        _choiceBoxEnviroment.getSelectionModel().clearSelection();
    }


    public void uploadLDIFFile(File selectedFile) throws IOException, LDIFException {
        _progressStage.show();
        _progressController.setProgress(0.0,"LOADING FILE NOW");
        TreeSet<String> foundAttributes = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        try {
            _ldifReader = new LDIFReader(selectedFile);
        } catch (IOException e) {
            GuiHelper.EXCEPTION("Open LDIF File", "File->" + selectedFile + " open failed, exception occured", e);
            _ldifReader = null;
            _progressStage.hide();
            return;
        }
        _breakFileLoad = false;
        _treeView.setRoot(null);
         Connection cc = new Connection(selectedFile.getAbsolutePath(),this,false);
         cc.set_fileMode(true);
         set_currentConnection(cc);
        CustomEntryItem CustomEntryItem = new CustomEntryItem("cn=" + selectedFile.getName());
        try {
            List<Attribute> newAttributes = new ArrayList<>();
            newAttributes.add(new Attribute("TYPE", "DUMMY"));
            newAttributes.add(new Attribute("description", "This is dummy entry for the gui only"));

            Path path = Paths.get(selectedFile.getAbsolutePath());
            List<String> pathElements = new ArrayList<>();
            Paths.get(path.toString()).forEach(p -> pathElements.add(p.toString()));
            StringBuilder builder = new StringBuilder();
            for (String s : pathElements) {
                builder.append("path=");
                builder.append(s);
                builder.append(",");
            }
            builder.deleteCharAt(builder.length() - 1);
            Entry entry1 = new Entry(builder.toString(), newAttributes);
            CustomEntryItem.setEntry(entry1);
            CustomEntryItem.setDummy();
            CustomEntryItem.setRdn(selectedFile.getName());
            cc.refreshEntry(entry1);
        } catch (Exception e) {
            _progressStage.hide();
            GuiHelper.EXCEPTION("Exception opening file", e.getMessage(), e);
            logger.error("Exception during opening file", e);
        }
        _treeView.setRoot(new TreeItem<>(CustomEntryItem));
        _treeView.getRoot().setExpanded(true);

        _executor.submit(() -> {
            int nrOfEntriesDone = 0;
            AtomicInteger atomicInteger = new AtomicInteger();
            while (true) {
                Entry entry = null;
                try {
                    entry = _ldifReader.readEntry();
                    if (entry == null)
                    {
                        _progressController.setProgress(1.0,"READ DONE, BUILD TREE NOW...");
                        break;
                    }
                    cc.refreshEntry(entry);
                    addEntriesFromLdif(_treeView.getRoot(), entry, entry.getDN());
                    entry.getAttributes().forEach(x -> foundAttributes.add(x.getName()));
                    nrOfEntriesDone++;
                    if (nrOfEntriesDone % 100 == 0) {
                        if (_breakFileLoad)
                        {
                            Platform.runLater(()->{
                                _treeView.setRoot(null);
                                _progressStage.hide();
                            });
                            _ldifReader = null;
                            return;
                        }
                        String dn = entry.getDN();
                        final String entriesDone = String.valueOf(nrOfEntriesDone);
                        Platform.runLater(() -> _progressController.setProgress((double) (atomicInteger.incrementAndGet() %100)  / 100.0, "Entries read->" + entriesDone));
                    }
                } catch (Exception e) {
                    _progressStage.hide();
                    String dn = entry.getDN();
                    Platform.runLater(() -> GuiHelper.EXCEPTION("Error Loading Entry", dn, e));
                    try {
                        _ldifReader.close();
                    } catch (Exception e1) {
                        _ldifReader = null;
                    }
                    _ldifReader = null;
                    logger.error(e);
                }
            }
            Platform.runLater(() -> {
                setIcons(_treeView.getRoot());
                _progressStage.hide();
                setFileMode(true);
                expand(_treeView.getRoot());
                _treeView.refresh();
            });

        });
        cc.setSchemaAttributes(foundAttributes.stream().collect(Collectors.toList()));
    }

    private void expand(TreeItem<CustomEntryItem> item) {
        if (item != null && !item.isLeaf()) {
            if (item.getValue().is_dummy()) item.setExpanded(true);
            for (TreeItem<CustomEntryItem> child : item.getChildren()) {
                expand(child);
            }
        }
    }

    private void connect(Connection connection) throws LDAPException, GeneralSecurityException {
        connection.connect();
        RootDSE root = connection.getRootDSE();
        System.out.println("SUPPORTS->" + root.supportsControl("1.2.840.113556.1.4.319"));
        _currConnection = connection;
        _buttonConnect.setDisable(true);
        _buttonDisconnect.setDisable(false);
        _buttonUploadFile.setDisable(false);
        _hboxFilter.setDisable(false);
    }

    @Override
    public void setProgress(double progress, String description) {
        Platform.runLater(() -> {
            if (_progressController != null) _progressController.setProgress(progress, description);
        });
    }

    @Override
    public void setProgress(String taskName, double progress) {
        Platform.runLater(() -> {
            if (_progressController != null) _progressController.setProgress(progress, taskName);
        });
    }

    public void switch2CollectionTree() {
        _ldapExploreMode = false;
        _exploreWindow.getChildren().remove(_treeView);
        if (!_exploreWindow.getChildren().contains(_collectionTree)){
            _exploreWindow.getChildren().add(_collectionTree);
        }
        _buttonOpenFile.setDisable(true);
    }

    public void switch2LdapTree() {
        _ldapExploreMode = true;
        if (_exploreWindow.getChildren().contains(_collectionTree))
            _exploreWindow.getChildren().remove(_collectionTree);
        if (!_exploreWindow.getChildren().contains(_treeView)) _exploreWindow.getChildren().add(_treeView);
        _buttonOpenFile.setDisable(false);
    }

    public void refreshTree_checkMissingEntries(TreeItem<CustomEntryItem> entryTreeItem) {
        // check missing first   //entryTreeItem.getValue().getEntry()
        boolean missing = false;
        try {
            Entry entry = _currConnection.getEntry(entryTreeItem.getValue().getDn());
            if (entry == null) missing = true;
        } catch (Exception e) {
            missing = true;
        }
        if (missing) {
            if (entryTreeItem.getParent() != null) {
                Platform.runLater(() -> {
                    if (entryTreeItem.getParent() != null) {
                        entryTreeItem.getParent().getChildren().remove(entryTreeItem);
                    }
                });
            }
        } else {
            // Create a safe copy of children to iterate over
            List<TreeItem<CustomEntryItem>> childrenToCheck = new ArrayList<>(entryTreeItem.getChildren());
            for (TreeItem<CustomEntryItem> entry : childrenToCheck) {
                refreshTree_checkMissingEntries(entry);
            }
        }
        Platform.runLater(() -> _treeView.refresh());
    }

    public void refreshTree_checkAddedEntries(TreeItem<CustomEntryItem> entryTreeItem) {
        String searchDN = entryTreeItem.getValue().getDn();
        Set<String> childrenDN = new HashSet<>();
        for (TreeItem<CustomEntryItem> c : entryTreeItem.getChildren()) {
            childrenDN.add(c.getValue().getDn());
        }
        _currentReader = new UnboundidLdapSearch(_main._configuration, get_currentConnection(),
                searchDN, null, null);
        if (get_currentConnection().getDisplayAttribute() != null) {
            _currentReader.setDisplayAttribute(get_currentConnection().getDisplayAttribute());
        }
        _currentReader.setReadAttributes(UnboundidLdapSearch.READ_ATTRIBUTES.none);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(_currentReader);
        try {
            future.get();
            for (Entry c : _currentReader.get_children()) {
                if (!childrenDN.contains(c.getDN())) {
                    CustomEntryItem customEntryItem = new CustomEntryItem(c);
                    TreeItem<CustomEntryItem> item = new TreeItem<>(customEntryItem);
                    Entry child = null;
                    try {
                        child = _currentReader.getOneChild(customEntryItem.getEntry().getDN());
                    } catch (Exception e) { }
                    if (child != null) {
                        CustomEntryItem customEntryItemDummy = new CustomEntryItem(child);
                        customEntryItemDummy.setDummy();
                        item.expandedProperty().addListener(_expandedListenerOnline);
                        item.getChildren().add(new TreeItem<>(customEntryItemDummy));
                    }
                    entryTreeItem.getChildren().add(item);
                    _treeView.refresh();
                }
            }
        } catch (Exception e) { }
    }

    public void signalTaskDone(String taskName, String description, Exception e) {
        Platform.runLater(()->{
            List<CustomEntryItem> items = new ArrayList();
            if(_currentReader != null &&  _currentReader.get_children() != null)
            {
                for (Entry entry : _currentReader.get_children()) {
                    CustomEntryItem customEntryItem = new CustomEntryItem(entry);
                    if (get_currentConnection().getDisplayAttribute() != null) {
                        customEntryItem.setDisplayAttribute(get_currentConnection().getDisplayAttribute());
                    }
                    //Entry child = _currentReader.getOneChild(customEntryItem.getDn());
                    //if(child!=null) customEntryItem.set_hasChildren(true);
                    items.add(customEntryItem);
                }
            }
            List<CustomEntryItem> sorted_items = items.stream().sorted().toList();
           _progressController.setProgress(1.0, "LDAP Read done, sort and build tree now ");
            if (_currentReader == null) {
                _progressStage.hide();
                if(e !=null) GuiHelper.EXCEPTION("Exception occured",description,e);
                return;
            }

            //-----------------------------------------------------


                if (_treeView.getRoot() == null) {
                    _treeView.setRoot(new TreeItem<>(new CustomEntryItem(_currentReader.get_mainEntry())));
                    for (CustomEntryItem custom : sorted_items) {
                        TreeItem<CustomEntryItem> item = new TreeItem<>(custom);
                        _treeView.getRoot().getChildren().add(item);
                        
                        // Check for numSubOrdinates attribute first before doing an expensive search
                        Entry entry = custom.getEntry();
                        boolean hasChildren = false;
                        
                        if (entry != null) {
                            // Check if entry has numSubOrdinates attribute
                            Attribute numSubOrdinatesAttr = entry.getAttribute("numSubOrdinates");
                            if (numSubOrdinatesAttr != null) {
                                // Use the attribute value directly
                                try {
                                    int numSubOrdinates = Integer.parseInt(numSubOrdinatesAttr.getValue());
                                    hasChildren = numSubOrdinates > 0;
                                } catch (NumberFormatException e1) {
                                    // If parsing fails, assume there might be children
                                    hasChildren = true;
                                }
                            } else {
                                // Fall back to old method when numSubOrdinates is not available
                                try {
                                    Entry child = _currentReader.getOneChild(custom.getEntry().getDN());
                                    hasChildren = (child != null);
                                } catch (Exception e1) {
                                    GuiHelper.EXCEPTION("Connection Error", e1.getLocalizedMessage(), e1);
                                }
                            }
                        }
                        
                        if (hasChildren) {
                            // Add a dummy child to enable expansion
                            CustomEntryItem CustomEntryItemDummy = new CustomEntryItem();
                            CustomEntryItemDummy.setDummy();
                            item.expandedProperty().addListener(_expandedListenerOnline);
                            item.getChildren().add(new TreeItem<>(CustomEntryItemDummy));
                            _treeView.getRoot().setExpanded(true);
                        }
                    }
                } else {
                    if (_observedEntry != null) {
                        Set<String> observedEntryChildren = _observedEntry.getChildren().stream().map(x -> x.getValue().getDn()).collect(Collectors.toSet());
                        for (CustomEntryItem CustomEntryItem : sorted_items) {
                            if (observedEntryChildren.contains(CustomEntryItem.getDn())) continue;
                            TreeItem<CustomEntryItem> item = new TreeItem<>(CustomEntryItem);
                            Entry child = null;
                            try {
                                if (sorted_items.size() > 100)
                                {
                                    child = CustomEntryItem.getEntry();
                                }
                                else
                                {
                                    child = _currentReader.getOneChild(CustomEntryItem.getEntry().getDN());
                                }
                            } catch (Exception e1) {
                                e1.printStackTrace();
                            }
                            if (child != null && sorted_items.size() < 5000) {
                                item.getValue().set_hasChildren(true);
                                item.expandedProperty().addListener(_expandedListenerOnline);
                                CustomEntryItem CustomEntryItemDummy = new CustomEntryItem(child);
                                CustomEntryItemDummy.setDummy();
                                item.getChildren().add(new TreeItem<>(CustomEntryItemDummy));
                            }
                            _observedEntry.getChildren().add(item);
                        }

                        if (_observedEntry.getChildren().size() > 10) {
                            _observedEntry.getValue().setRdn
                                    (_observedEntry.getValue().getRdn() + " [" + _observedEntry.getChildren().size() + "]");
                        }
                        _observedEntry.setExpanded(true);
                        int row = _treeView.getRow(_observedEntry);
                        _treeView.scrollTo(row);
                         _treeView.refresh();
                        _treeView.getSelectionModel().select(_observedEntry);
                    }
                }
                _progressStage.hide();
                if(e !=null) GuiHelper.EXCEPTION("Exception occured",description,e);

        });


    }

    public void disconnect() {
        _buttonConnect.setDisable(false);
        _buttonDisconnect.setDisable(true);
        _buttonUploadFile.setDisable(true);
        _breakFileLoad = true;

        _choiceBoxEnviroment.getSelectionModel().clearSelection();
        if (_ldifReader != null) {
            try {
                _ldifReader.close();
                _ldifReader = null;
            } catch (Exception e) {
                // Ignore exceptions during closing
            }
        }
        if (_openedLDIFFile != null) _openedLDIFFile = null;

        removeListeners(_treeView.getRoot());
        removeListeners(_collectionTree.getRoot());
        _treeView.setRoot(null);
        _collectionTree.setRoot(null);

        _observedEntry = null;
        if (get_currentConnection() != null) get_currentConnection().disconect();
        set_currentConnection(null);
        if(_main.get_entryDiffView()!=null) _main.get_entryDiffView().updateValues(null);
    }

    private void removeListeners(TreeItem<?> item) {
        if(item==null || item.getChildren() == null) return;
        for(TreeItem childItem : item.getChildren()){
            childItem.expandedProperty().removeListener(_expandedListenerOnline);
            childItem.expandedProperty().unbind();
            removeListeners(childItem);
        }
    }


    public void connect(boolean initTree) {
        if (_ldifReader != null) {
            try {
                _ldifReader.close();
                _ldifReader = null;
            } catch (Exception e) {
            }
            if (initTree) _treeView.setRoot(null);
        }
        _fileMode = false;
        _observedEntry = null;
        if (_choiceBoxEnviroment.getSelectionModel().isEmpty()) {
            GuiHelper.ERROR("Connection not selected", "Select connection first");
            return;
        }
        Connection connection;
        if (get_currentConnection() != null) get_currentConnection().disconect();
        set_currentConnection(null);
        _textFieldLdapFilter.set_currConnection(null);
        if (_main._configuration.getConnectionPassword(_choiceBoxEnviroment.getValue()) == null) {
            String loginPassword = GuiHelper.enterPassword("Source Connection Password", "Enter login password");
            try {
                connection = _choiceBoxEnviroment.getValue().copy();
                connection.setPassword(loginPassword);
                connect(connection);
            } catch (LDAPException | GeneralSecurityException e) {
                e.printStackTrace();
                GuiHelper.EXCEPTION("Connection Error", e.toString(), e);
                return;
            }
        } else {
            try {
                connection = _choiceBoxEnviroment.getValue().copy();
                connection.setPassword(_main._configuration.getConnectionPassword(connection).toString());
                connect(connection);
                set_currentConnection(connection);
                _buttonConnect.fireEvent(_connectionEstablishedEvent);
                if (initTree) {
                    _currentReader = new UnboundidLdapSearch(_main._configuration, get_currentConnection(),
                            get_currentConnection().getBaseDN(), null, _this);
                    if (get_currentConnection().getDisplayAttribute() != null)
                        _currentReader.setDisplayAttribute(get_currentConnection().getDisplayAttribute());
                    _currentReader.setReadAttributes(UnboundidLdapSearch.READ_ATTRIBUTES.none);
                    _treeView.setRoot(null);
                    _executor.execute(_currentReader);
                }

            } catch (LDAPException | GeneralSecurityException e) {
                e.printStackTrace();
                GuiHelper.EXCEPTION("Connection Error", e.toString(), e);
                return;
            }
        }
        _textFieldLdapFilter.set_currConnection(_currConnection);
    }

    public void setMain(Main main) {
        _main = main;
        _choiceBoxEnviroment.setItems(_main._ctManager._settingsController._connectionObservableList);
        
        // Create tag ChoiceBox programmatically
        _choiceBoxTag = new ChoiceBox<>();
        _choiceBoxTag.setTooltip(new Tooltip("Filter connections by tag"));
        
        // Initialize tag filtering
        initializeTagChoiceBox();
        
        _this = this;
        initProgressWindow(_main);
        _treeView = new TreeView<>();
        _treeView.getStyleClass().add("code-font-tree");
        VBox.setVgrow(_treeView, Priority.ALWAYS);
        _treeView.setMaxHeight(Double.MAX_VALUE);

        _treeView.setCellFactory(new Callback<TreeView<CustomEntryItem>, TreeCell<CustomEntryItem>>() {
            @Override
            public TreeCell<CustomEntryItem> call(TreeView<CustomEntryItem> param) {
                return new TreeCell<CustomEntryItem>() {
                    protected void updateItem(CustomEntryItem item, boolean empty) {
                        textProperty().unbind();
                        styleProperty().unbind();
                        graphicProperty().unbind();
                        if (empty || item == null) {
                            setGraphic(null);
                            textProperty().set(null);
                            styleProperty().set(null);
                            return;
                        }
                        styleProperty().bind(item.get_styleProperty());
                        textProperty().bind(item._rdn);
                        super.updateItem(item, empty);
                    }
                };
            }
        });

        _treeView.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
            if (e.getButton() == MouseButton.SECONDARY) {
                TreeItem item = _treeView.getSelectionModel().getSelectedItem();
                if (item != null) {
                    _contextMenu.show(_main.get_primaryStage(), e.getScreenX(), e.getScreenY());
                }
            }
        });
        _treeView.getSelectionModel().selectedItemProperty()
                .addListener(new ChangeListener<TreeItem<CustomEntryItem>>() {
                    @Override
                    public void changed(ObservableValue<? extends TreeItem<CustomEntryItem>> observable, TreeItem<CustomEntryItem> oldValue, TreeItem<CustomEntryItem> newValue) {
                        if (newValue != null) {
                            _observedEntry = newValue;
                            _selectedDN = _observedEntry.getValue().getDn();
                            //_main.get_entryDiffView().updateValues(_observedEntry.getValue());
                            _treeView.fireEvent(new LdapExplorerEvent(LdapExplorerEvent.ELEMENT_SELECTED, _observedEntry));
                            _main._ctManager._progressWindowController.clearProgressWindow();
                            if (_observedEntry.isExpanded()) {
                                if (_observedEntry.getChildren().size() == 1 && _observedEntry.getChildren().get(0).getValue().is_dummy()) {
                                    _observedEntry.getChildren().get(0).expandedProperty().removeListener(_expandedListenerOnline);
                                    _observedEntry.getChildren().clear();
                                    _progressStage.show();
                                    _currentReader = new UnboundidLdapSearch(_main._configuration, get_currentConnection(),
                                            _observedEntry.getValue().getEntry().getDN(), null, _this);
                                    if (get_currentConnection().getDisplayAttribute() != null) {
                                        _currentReader.setDisplayAttribute(get_currentConnection().getDisplayAttribute());
                                    }
                                    _currentReader.setReadAttributes(UnboundidLdapSearch.READ_ATTRIBUTES.none);
                                    ExecutorService executor = Executors.newSingleThreadExecutor();
                                    executor.submit(() -> _executor.execute(_currentReader));
                                }
                            }
                            else{
                                _treeView.autosize();
                                _treeView.refresh();

                            }
                            /*
                            else {
                                _treeView.getRoot().getChildren().forEach(x->{
                                    _treeView.refresh();
                                });
                            }

                             */
                        }
                        else{
                            _treeView.getSelectionModel().select(_observedEntry);
                        }
                    }
                });
    }

    private void initializeTagChoiceBox() {
        // Set tooltip for the tag choice box
        _choiceBoxTag.setTooltip(new Tooltip("Filter connections by tag"));
        
        // Create ObservableList for tags that will be used in the ChoiceBox
        ObservableList<String> tags = FXCollections.observableArrayList();
        tags.add("All"); // Default option to show all connections
        
        // Collect unique tags from all connections
        Set<String> uniqueTags = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Connection conn : _main._ctManager._settingsController._connectionObservableList) {
            String tag = conn.getTag();
            if (tag != null && !tag.trim().isEmpty()) {
                uniqueTags.add(tag.trim());
            }
        }
        
        // Add all unique tags to the ObservableList
        tags.addAll(uniqueTags);
        
        // Set items and select the "All" option by default
        _choiceBoxTag.setItems(tags);
        _choiceBoxTag.getSelectionModel().select(0); // Select "All" option
        
        // Create filtered list for connections
        FilteredList<Connection> filteredConnections = new FilteredList<>(
            _main._ctManager._settingsController._connectionObservableList, 
            p -> true // Initially show all connections
        );
        
        // Set up listener for tag selection changes
        _choiceBoxTag.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                String selectedTag = newValue;
                logger.info("Selected tag: " + selectedTag);
                
                // Apply filter based on selected tag
                if (selectedTag.equals("All")) {
                    // Show all connections
                    filteredConnections.setPredicate(p -> true);
                    _choiceBoxEnviroment.setItems(_main._ctManager._settingsController._connectionObservableList);
                } else {
                    // Filter connections by selected tag
                    filteredConnections.setPredicate(conn -> {
                        String connTag = conn.getTag();
                        return connTag != null && connTag.trim().equalsIgnoreCase(selectedTag);
                    });
                    _choiceBoxEnviroment.setItems(filteredConnections);
                }
                
                // Select first connection if available
                if (!_choiceBoxEnviroment.getItems().isEmpty()) {
                    _choiceBoxEnviroment.getSelectionModel().select(0);
                }
            }
        });
        
        // Add listener to connection list to update tags when connections change
        _main._ctManager._settingsController._connectionObservableList.addListener(
            (ListChangeListener<Connection>) change -> {
                // Recompute unique tags
                uniqueTags.clear();
                for (Connection conn : _main._ctManager._settingsController._connectionObservableList) {
                    String tag = conn.getTag();
                    if (tag != null && !tag.trim().isEmpty()) {
                        uniqueTags.add(tag.trim());
                    }
                }
                
                // Update tag list while preserving selection
                String selectedTag = _choiceBoxTag.getSelectionModel().getSelectedItem();
                tags.clear();
                tags.add("All");
                tags.addAll(uniqueTags);
                
                // Restore selection or default to "All"
                if (tags.contains(selectedTag)) {
                    _choiceBoxTag.getSelectionModel().select(selectedTag);
                } else {
                    _choiceBoxTag.getSelectionModel().select(0);
                }
                
                // Reapply filter
                if (selectedTag.equals("All")) {
                    filteredConnections.setPredicate(p -> true);
                    _choiceBoxEnviroment.setItems(_main._ctManager._settingsController._connectionObservableList);
                } else {
                    filteredConnections.setPredicate(conn -> {
                        String connTag = conn.getTag();
                        return connTag != null && connTag.trim().equalsIgnoreCase(selectedTag);
                    });
                    _choiceBoxEnviroment.setItems(filteredConnections);
                }
            });
    }

    /**
     * Opens the LDIF Editor with the current connection
     */
    private void openLdifEditor() {
        if (_currConnection == null) {
            GuiHelper.ERROR("Connection Required", "You must have an active LDAP connection to use the LDIF Editor");
            return;
        }
        _main._ctManager._ldifEditorController.setLdapExploreController(_main._ctManager._ldapSourceExploreCtrl);
        if (_observedEntry != null && _observedEntry.getValue() != null) {
            String selectedDN = _observedEntry.getValue().getDn();
            _main._ctManager._ldifEditorController.setSelectedDN(selectedDN);
        }
        _main._ctManager._ldifEditorController.show();
    }

    /**
     * Refreshes the selected entry and all expanded entries below it.
     * This method is triggered when F5 is pressed on a selected entry.
     */
    public void refreshSelectedEntry() {
        if (_currConnection == null || _observedEntry == null) return;
        
        logger.info("Refreshing selected entry: {}", _observedEntry.getValue().getDn());
        
        // Create a progress indicator
        Platform.runLater(() -> {
            _progressController.setProgress(0, "Refreshing entry: " + _observedEntry.getValue().getDn());
            _progressStage.show();
        });
        
        // Use a background thread for the refresh
        Task<Void> refreshTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                try {
                    // Check if selected entry still exists and update it
                    checkAndRefreshEntry(_observedEntry);
                    
                    Platform.runLater(() -> {
                        // Refresh the tree view UI
                        _treeView.refresh();
                        // Hide progress window
                        _progressStage.hide();
                    });
                } catch (Exception e) {
                    logger.error("Error refreshing entry: " + e.getMessage(), e);
                    Platform.runLater(() -> {
                        GuiHelper.EXCEPTION("Refresh Error", "Error refreshing entry", e);
                        _progressStage.hide();
                    });
                }
                return null;
            }
        };
        
        // Execute the refresh task
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(refreshTask);
        executor.shutdown();
    }
    
    /**
     * Recursively checks and refreshes an entry and its expanded children.
     *
     * @param entryTreeItem The entry to refresh
     */
    private void checkAndRefreshEntry(TreeItem<CustomEntryItem> entryTreeItem) {
        if (entryTreeItem == null) return;
        
        try {
            // Check if entry exists and update it
            Entry entry = _currConnection.getEntry(entryTreeItem.getValue().getDn());
            if (entry != null) {
                // Update the entry with fresh data
                entryTreeItem.getValue().setEntry(entry);
                
                // Only process expanded children
                if (entryTreeItem.isExpanded()) {
                    // Create a copy of children to avoid concurrent modification
                    List<TreeItem<CustomEntryItem>> childrenToCheck = new ArrayList<>(entryTreeItem.getChildren());
                    
                    // Check for new children that might have been added
                    refreshTree_checkAddedEntries(entryTreeItem);
                    
                    // Recursively process each expanded child
                    for (TreeItem<CustomEntryItem> childItem : childrenToCheck) {
                        if (childItem.isExpanded()) {
                            checkAndRefreshEntry(childItem);
                        }
                    }
                }
            } else {
                // Entry no longer exists, remove it if it has a parent
                if (entryTreeItem.getParent() != null) {
                    Platform.runLater(() -> {
                        entryTreeItem.getParent().getChildren().remove(entryTreeItem);
                    });
                }
            }
        } catch (Exception e) {
            logger.error("Error checking entry " + entryTreeItem.getValue().getDn() + ": " + e.getMessage(), e);
        }
    }
    
    /**
     * Sets the password for the selected LDAP entry.
     */
    private void setUserPassword() {
        if (_observedEntry == null || _observedEntry.getValue() == null || _observedEntry.getValue().getEntry() == null) {
            GuiHelper.ERROR("Error", "No entry selected");
            return;
        }
    
        // Ask for the new password using the improved dialog with confirmation
        String password = GuiHelper.enterPasswordWithConfirmation(
            "Set Password", 
            "Set new password for user", 
            _observedEntry.getValue().getDn()
        );
        
        // If password is null or empty, the user canceled the dialog
        if (password == null || password.isEmpty()) {
            return;
        }
    
        try {
            // Try different password formats depending on the LDAP server
            Modification mod;
            
            // Check if this is Active Directory
            boolean isActiveDirectory = false;
            if (_currConnection.getVendor() != null && 
                _currConnection.getVendor().toLowerCase().contains("microsoft")) {
                isActiveDirectory = true;
            } else if (_currConnection.get_rootDSE() != null && 
                     _currConnection.get_rootDSE().hasAttribute("forestFunctionality")) {
                isActiveDirectory = true;
            }
            
            if (isActiveDirectory) {
                // For Active Directory, use unicodePwd with special formatting
                String quotedPassword = "\"" + password + "\"";
                byte[] unicodePassword = quotedPassword.getBytes("UTF-16LE");
                mod = new Modification(ModificationType.REPLACE, "unicodePwd", unicodePassword);
                logger.info("Setting password using Active Directory format (unicodePwd attribute)");
            } else {
                // For standard LDAP servers like OpenLDAP
                mod = new Modification(ModificationType.REPLACE, "userPassword", password);
                logger.info("Setting password using standard LDAP format (userPassword attribute)");
            }
            
            ModifyRequest modifyRequest = new ModifyRequest(_observedEntry.getValue().getDn(), mod);
            
            // Execute the modify operation
            LDAPResult result = _currConnection.modify(modifyRequest);
            
            if (result.getResultCode().equals(ResultCode.SUCCESS)) {
                GuiHelper.INFO("Success", "Password updated successfully");
            } else {
                GuiHelper.ERROR("Error", "Failed to update password: " + result.getDiagnosticMessage());
            }
        } catch (Exception e) {
            GuiHelper.EXCEPTION("Error setting password", "An error occurred while setting the password", e);
            logger.error("Error setting password", e);
        }
    }

    /**
     * Verifies if a password is valid for the selected LDAP entry.
     * This method tests the password by attempting to bind to the LDAP server
     * using the entry's DN and the provided password.
     */
    private void verifyPassword() {
        if (_observedEntry == null || _observedEntry.getValue() == null || _observedEntry.getValue().getEntry() == null) {
            GuiHelper.ERROR("Error", "No entry selected");
            return;
        }
    
        // Get the DN of the selected entry
        String entryDN = _observedEntry.getValue().getDn();
        
        // Ask for the password to verify
        String password = GuiHelper.enterPassword(
            "Verify Password", 
            "Enter password to verify for: " + entryDN
        );
        
        // If password is null or empty, the user canceled the dialog
        if (password == null || password.isEmpty()) {
            return;
        }
    
        LDAPConnection verifyConn = null;
        try {
            logger.info("Attempting to verify password for entry: {}", entryDN);
            
            // Get the current connection's server and port
            String host = _currConnection.getServer();
            int port = _currConnection.getPortNumber();
            boolean useSSL = _currConnection.isSSL();
            
            // Configure connection options for better timeout handling
            LDAPConnectionOptions options = new LDAPConnectionOptions();
            options.setConnectTimeoutMillis(10000);  // 10 seconds
            options.setResponseTimeoutMillis(20000); // 20 seconds
            
            // Create a new connection with the entry's DN and provided password
            if (useSSL) {
                // Use the SSL connection with expanded options
                try {
                    SSLUtil sslUtil = new SSLUtil(new TrustAllTrustManager());
                    SSLSocketFactory socketFactory = sslUtil.createSSLSocketFactory();
                    
                    // First establish connection without binding
                    verifyConn = new LDAPConnection(socketFactory, options, host, port);
                    
                    // Then perform a separate bind operation
                    BindResult bindResult = verifyConn.bind(entryDN, password);
                    if (bindResult.getResultCode() == ResultCode.SUCCESS) {
                        GuiHelper.INFO("Success", "Password is valid for entry: " + entryDN);
                    }
                } catch (Exception sslEx) {
                    logger.error("SSL error during password verification", sslEx);
                    throw new LDAPException(ResultCode.CONNECT_ERROR, 
                        "SSL connection error: " + sslEx.getMessage());
                }
            } else {
                // Standard non-SSL connection
                // First establish connection without binding
                verifyConn = new LDAPConnection(options, host, port);
                
                // Then perform a separate bind operation
                BindResult bindResult = verifyConn.bind(entryDN, password);
                if (bindResult.getResultCode() == ResultCode.SUCCESS) {
                    GuiHelper.INFO("Success", "Password is valid for entry: " + entryDN);
                }
            }
        } catch (LDAPException e) {
            // Check the result code to provide a more specific error message
            if (e.getResultCode().equals(ResultCode.INVALID_CREDENTIALS)) {
                GuiHelper.ERROR("Authentication Failed", "The password is incorrect for entry: " + entryDN);
            } else {
                GuiHelper.ERROR("Error", "Failed to verify password: " + e.getMessage());
            }
            logger.error("Error verifying password", e);
        } catch (Exception e) {
            GuiHelper.EXCEPTION("Error", "An unexpected error occurred while verifying the password", e);
            logger.error("Unexpected error during password verification", e);
        } finally {
            // Always close the connection
            if (verifyConn != null) {
                try {
                    verifyConn.close();
                } catch (Exception ex) {
                    logger.warn("Error closing verification connection", ex);
                }
            }
        }
    }

    private void addEntriesFromLdif(TreeItem<CustomEntryItem> treeEntry, Entry entry, String dn) {
        if (dn.length() == 0) return;
        String[] split = dn.split(",");
        if (split == null || split.length == 1) {
            TreeItem<CustomEntryItem> insert = findChild(treeEntry, dn);
            ObservableList<TreeItem<CustomEntryItem>> backup = null;
            if (insert != null) {
                backup = insert.getChildren();
                treeEntry.getChildren().remove(insert);
            }
            CustomEntryItem customEntryItem = new CustomEntryItem(entry);
            TreeItem<CustomEntryItem> ti = new TreeItem<>(customEntryItem);
            ti.expandedProperty().addListener(_expandedListenerFile);
            treeEntry.getChildren().add(ti);
            if (backup != null && !backup.isEmpty()) {
                ti.getChildren().addAll(backup);
            }
            return;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < split.length - 1; i++) {
            builder.append(split[i]);
            builder.append(",");
        }
        builder.deleteCharAt(builder.length() - 1);

        TreeItem<CustomEntryItem> child = findChild(treeEntry, split[split.length - 1]);
        if (child == null) {
            String dummyDN = entry.getDN().replace(builder.toString() + ",", "");
            CustomEntryItem customEntryItem = new CustomEntryItem(dummyDN);
            customEntryItem.setDummy();

            customEntryItem.set_dn(new SimpleStringProperty(dummyDN));
            List<Attribute> newAttributes = new ArrayList<>();
            Attribute attribute = new Attribute("description", "this entry is not in file");
            newAttributes.add(attribute);
            Entry entry1 = new Entry(dummyDN, newAttributes);
            entry1.setDN(dummyDN);
            customEntryItem.setEntry(entry1);
            customEntryItem.setRdn(split[split.length - 1]);
            child = new TreeItem<>(customEntryItem);

            child.expandedProperty().addListener(_expandedListenerFile);
            treeEntry.getChildren().add(child);

        }
        addEntriesFromLdif(child, entry, builder.toString());
    }
}
