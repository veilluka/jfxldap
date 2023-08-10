package ch.vilki.jfxldap.gui;


import ch.vilki.jfxldap.Main;
import ch.vilki.jfxldap.backend.*;
import com.unboundid.ldap.sdk.*;
import com.unboundid.ldap.sdk.schema.Schema;
import com.unboundid.ldif.LDIFException;
import com.unboundid.ldif.LDIFReader;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
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

public class LdapExploreController implements IProgress, ILoader {

    static Logger logger = LogManager.getLogger(LdapExploreController.class);

    /***************** GUI ELEMENTS *****************************************/

    @FXML
    TreeView<CustomEntry> _treeView;
    @FXML
    TreeView<CollectionEntry> _collectionTree;

    @FXML ChoiceBox<Connection> _choiceBoxEnviroment;
    @FXML Button _buttonConnect;
    @FXML Button _buttonDisconnect;
    @FXML Button _buttonOpenFile;
    @FXML Button _buttonUploadFile;
    @FXML Button _buttonCloseFile;
    @FXML Button _buttonRunLdapSearch;
    @FXML Button _buttonRemoveFilter;
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
    TreeItem<CustomEntry> _observedEntry;
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

    Comparator<TreeItem<CustomEntry>> _treeItemCustomComparator =
            Comparator.comparing((TreeItem<CustomEntry> one) -> one.getValue().getDn());

    Map<String, TreeItem<CustomEntry>> _allTreeEntries = new HashMap<>();


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

        _treeView.setCellFactory(new Callback<>() {
            @Override
            public TreeCell<CustomEntry> call(TreeView<CustomEntry> param) {
                return new TreeCell<>() {
                    @Override
                    protected void updateItem(CustomEntry item, boolean empty) {
                        textProperty().unbind();
                        styleProperty().unbind();
                        if (empty || item == null) {
                            setGraphic(null);
                            textProperty().set(null);
                            styleProperty().set(null);
                            return;
                        }
                        textProperty().bind(item.rdnProperty());
                        if (!item.is_dummy()) {
                            setGraphic(Icons.get_iconInstance().getObjectType(item.get_objectClass()));
                        }
                        super.updateItem(item, empty);
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

    private TreeItem<CustomEntry> findChild(TreeItem<CustomEntry> entry, String rdn) {
        if (entry.getChildren() == null || entry.getChildren().isEmpty()) return null;
        for (TreeItem<CustomEntry> e : entry.getChildren()) {
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

    private void setIcons(TreeItem<CustomEntry> entryTreeItem) {
        if (entryTreeItem == null) return;
        for (TreeItem<CustomEntry> child : entryTreeItem.getChildren()) {
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

    private void setNewDisplayAttribute(TreeItem<CustomEntry> treeItem, String attributeName) {
        if (treeItem == null) return;
        if (treeItem.getValue() == null || treeItem.getValue() == null || treeItem.getValue().getEntry() == null)
            return;
        try {
            Entry entry = get_currentConnection().getEntry(treeItem.getValue().getEntry().getDN(), new String[]{attributeName});
            Platform.runLater(() -> _progressController.setProgress(0, entry.getDN()));
            treeItem.getValue().setEntry(entry);
            treeItem.getValue().setDisplayAttribute(attributeName);
            _treeView.refresh();
        } catch (LDAPException e) {
            logger.error(e);
        }
        for (TreeItem<CustomEntry> item : treeItem.getChildren()) setNewDisplayAttribute(item, attributeName);
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
            _progressStage.close();
        });

        _export.setOnAction(x -> {
            TreeItem<CustomEntry> selectedItem = _treeView.getSelectionModel().getSelectedItem();
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
        _contextMenu.getItems().addAll(_search, _compareItem, _setDisplayAttribute, _export, _clipBoardLDIF, _deleteEntry);
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
        _expandedListenerOnline = (ChangeListener<Boolean>) (observable, oldValue, newValue) -> {
            BooleanProperty bb = (BooleanProperty) observable;

            TreeItem t = (TreeItem<CustomEntry>) bb.getBean();
            _observedEntry = t;
            _treeView.fireEvent(new LdapExplorerEvent(LdapExplorerEvent.ELEMENT_SELECTED, _observedEntry));
            _main._ctManager._progressWindowController.clearProgressWindow();
            if (_observedEntry.isExpanded()) {
                if (_observedEntry.getChildren().size() == 1 && _observedEntry.getChildren().get(0).getValue().is_dummy()) {
                    _observedEntry.getChildren().get(0).expandedProperty().removeListener(_expandedListenerOnline);
                    _observedEntry.getChildren().remove(0);
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
            else {
                _treeView.getRoot().getChildren().forEach(x->{
                    _treeView.refresh();
                    
                    System.out.println("CR->" + x.getValue().getRdn());
                });
            }
        };

        _expandedListenerFile = (ChangeListener<Boolean>) (observable, oldValue, newValue) -> {
            BooleanProperty bb = (BooleanProperty) observable;
            TreeItem t = (TreeItem<CustomEntry>) bb.getBean();
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

    private void getAllEntries(TreeItem<CustomEntry> item) {
        _allTreeEntries.put(item.getValue().getDn(), item);
        for (TreeItem<CustomEntry> child : item.getChildren()) {
            _allTreeEntries.put(child.getValue().getDn(), child);
            getAllEntries(child);
        }
    }

    public void deleteEntry() {
        if (_observedEntry == null) return;
        if (_currentReader.getOneChild(_observedEntry.getValue().getDn()) == null) {
            logger.debug("Deleting only one child {}", _observedEntry.getValue().getDn());
            try {
                logger.info("DELETING NOW-> {}" + _observedEntry.getValue().getDn());
                _currConnection.delete(_observedEntry.getValue().getDn());
            } catch (Exception e) {
                GuiHelper.EXCEPTION("Error deleting entry", e.getMessage(), e);
                return;
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
        CustomEntry customEntry = new CustomEntry("cn=" + selectedFile.getAbsolutePath());
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
            customEntry.setEntry(entry1);
            customEntry.set_dummy(true);
            customEntry.setRdn(selectedFile.getName());
            cc.refreshEntry(entry1);
        } catch (Exception e) {
            _progressStage.hide();
            GuiHelper.EXCEPTION("Exception opening file", e.getMessage(), e);
            logger.error("Exception during opening file", e);
        }
        _treeView.setRoot(new TreeItem<>(customEntry));
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

    private void expand(TreeItem<CustomEntry> item) {
        if (item != null && !item.isLeaf()) {
            if (item.getValue().is_dummy()) item.setExpanded(true);
            for (TreeItem<CustomEntry> child : item.getChildren()) {
                expand(child);
            }
        }
    }

    private void addEntriesFromLdif(TreeItem<CustomEntry> treeEntry, Entry entry, String dn) {
        if (dn.length() == 0) return;
        String[] split = dn.split(",");
        if (split == null || split.length == 1) {
            TreeItem<CustomEntry> insert = findChild(treeEntry, dn);
            ObservableList<TreeItem<CustomEntry>> backup = null;
            if (insert != null) {
                backup = insert.getChildren();
                treeEntry.getChildren().remove(insert);
            }
            CustomEntry customEntry = new CustomEntry(entry);
            TreeItem<CustomEntry> ti = new TreeItem<>(customEntry);
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

        TreeItem<CustomEntry> child = findChild(treeEntry, split[split.length - 1]);
        if (child == null) {
            String dummyDN = entry.getDN().replace(builder.toString() + ",", "");
            CustomEntry customEntry = new CustomEntry(dummyDN);
            customEntry.set_dummy(true);
            List<Attribute> newAttributes = new ArrayList<>();
            Attribute attribute = new Attribute("description", "this is dummy entry");
            newAttributes.add(attribute);
            Entry entry1 = new Entry(dummyDN, newAttributes);
            entry1.setDN(dummyDN);
            customEntry.setEntry(entry1);
            customEntry.setRdn(split[split.length - 1]);
            child = new TreeItem<>(customEntry);
            child.expandedProperty().addListener(_expandedListenerFile);
            treeEntry.getChildren().add(child);
        }
        addEntriesFromLdif(child, entry, builder.toString());
    }


    void connect(Connection connection) throws LDAPException, GeneralSecurityException {
        connection.connect();
        RootDSE root = connection.getRootDSE();
        System.out.println("SUPPORTS->" + root.supportsControl("1.2.840.113556.1.4.319"));
        String urls[] = root.getAltServerURIs();
        String rootdn = root.getDN();
        String context[] = root.getNamingContextDNs();
        String selectedValue = connection.getBaseDN();
        if (selectedValue == null && context == null) {
            Filter f = Filter.create("(objectclass=*)");
            SearchResult found = connection.search("", SearchScope.ONE, f, null);
            if (found != null && found.getEntryCount() > 0) {
                context = new String[found.getEntryCount()];
                for (int i = 0; i < found.getEntryCount(); i++) {
                    context[i] = found.getSearchEntries().get(i).getDN();
                }
            }
        }
        if (selectedValue == null) {
            selectedValue = GuiHelper.selectValue("LDA Context", "LDAP Context not set ", "SELECT", context);
        }
        if (selectedValue == null && context != null) selectedValue = context[0];
        if (connection.getBaseDN() == null) connection.setBaseDN(selectedValue);
        String subschema = root.getSubschemaSubentryDN();
        String[] features = root.getSupportedFeatureOIDs();
        String vendor = root.getVendorName();
        Schema schema = connection.getSchema();
        System.out.println("VENDOR IS->" + vendor);
    }

    @Override
    public void setProgress(double progress, String description) {
        Platform.runLater(() -> _progressController.setProgress(progress, description));
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

    @Override
    public void signalTaskDone(String taskName, String description, Exception e) {
        Platform.runLater(()->{
            List<CustomEntry> items = new ArrayList();
            if(_currentReader != null &&  _currentReader.get_children() != null)
            {
                for (Entry entry : _currentReader.get_children()) {
                    CustomEntry collectionEntry = new CustomEntry(entry);
                    if (get_currentConnection().getDisplayAttribute() != null) {
                        collectionEntry.setDisplayAttribute(get_currentConnection().getDisplayAttribute());
                    }
                    items.add(collectionEntry);
                }
            }
            List<CustomEntry> sorted_items = items.stream().sorted().collect(Collectors.toList());
           _progressController.setProgress(1.0, "LDAP Read done, sort and build tree now ");
            if (_currentReader == null) {
                _progressStage.hide();
                if(e !=null) GuiHelper.EXCEPTION("Exception occured",description,e);
                return;
            }

            //-----------------------------------------------------


                if (_treeView.getRoot() == null) {
                    _treeView.setRoot(new TreeItem<>(new CustomEntry(_currentReader.get_mainEntry())));
                    for (CustomEntry custom : sorted_items) {
                        TreeItem<CustomEntry> item = new TreeItem<>(custom);
                        _treeView.getRoot().getChildren().add(item);
                        Entry child = null;
                        try {
                            child = _currentReader.getOneChild(custom.getEntry().getDN());
                        } catch (Exception e1) {
                            GuiHelper.EXCEPTION("Connection Error", e1.getLocalizedMessage(), e1);
                        }
                        if (child != null) {
                            CustomEntry customEntryDummy = new CollectionEntry(child);
                            customEntryDummy.set_dummy(true);
                            item.expandedProperty().addListener(_expandedListenerOnline);
                            item.getChildren().add(new TreeItem<>(customEntryDummy));
                            _treeView.getRoot().setExpanded(true);
                        }
                    }
                } else {
                    if (_observedEntry != null) {
                        Set<String> observedEntryChildren = _observedEntry.getChildren().stream().map(x -> x.getValue().getDn()).collect(Collectors.toSet());
                        for (CustomEntry customEntry : sorted_items) {
                            if (observedEntryChildren.contains(customEntry.getDn())) continue;
                            TreeItem<CustomEntry> item = new TreeItem<>(customEntry);
                            Entry child = null;
                            try {
                                if (sorted_items.size() > 100)
                                {
                                    child = customEntry.getEntry();
                                }
                                else
                                {
                                    child = _currentReader.getOneChild(customEntry.getEntry().getDN());
                                }
                            } catch (Exception e1) {
                                e1.printStackTrace();
                            }
                            if (child != null && sorted_items.size() < 5000) {
                                item.expandedProperty().addListener(_expandedListenerOnline);
                                CustomEntry customEntryDummy = new CustomEntry(child);
                                customEntryDummy.set_dummy(true);
                                item.getChildren().add(new TreeItem<>(customEntryDummy));
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
        _choiceBoxEnviroment.getSelectionModel().clearSelection();
        if (_ldifReader != null) {
            try {
                _ldifReader.close();
                _ldifReader = null;
            } catch (Exception e) {
            }
        }
        if (_openedLDIFFile != null) _openedLDIFFile = null;

        removeListerners(_treeView.getRoot());

        _treeView.setRoot(null);

        _observedEntry = null;
        if (get_currentConnection() != null) get_currentConnection().disconect();
        set_currentConnection(null);
        _main.get_entryDiffView().updateValues(null);
    }

    private void removeListerners(TreeItem<CustomEntry> item) {
         for (TreeItem<CustomEntry> child : item.getChildren()) {
            child.expandedProperty().removeListener(_expandedListenerOnline);
            child.expandedProperty().unbind();
            removeListerners(child);
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
                    _currentReader = new UnboundidLdapSearch(_main._configuration, get_currentConnection(), get_currentConnection().getBaseDN(), null, _this);
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

    public void refreshTree_checkMissingEntries(TreeItem<CustomEntry> entryTreeItem) {
        // check missing first   //entryTreeItem.getValue().getEntry()
        boolean missing = false;
        try {
            Entry entry = _currConnection.getEntry(entryTreeItem.getValue().getDn());
            if (entry == null) missing = true;
        } catch (Exception e) {
            missing = true;
        }
        if (missing) entryTreeItem.getParent().getChildren().remove(entryTreeItem);
        else {
            for (TreeItem<CustomEntry> entry : entryTreeItem.getChildren()) refreshTree_checkMissingEntries(entry);
        }
        _treeView.refresh();
    }

    public void refreshTree_checkAddedEntries(TreeItem<CustomEntry> entryTreeItem) {
        String searchDN = entryTreeItem.getValue().getDn();
        Set<String> childrenDN = new HashSet<>();
        for (TreeItem<CustomEntry> c : entryTreeItem.getChildren()) {
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
                    CustomEntry customEntry = new CustomEntry(c);
                    TreeItem<CustomEntry> item = new TreeItem<>(customEntry);
                    Entry child = _currentReader.getOneChild(customEntry.getEntry().getDN());
                    if (child != null && _currentReader.get_children().size() < 5000) {
                        item.expandedProperty().addListener(_expandedListenerOnline);
                        CustomEntry customEntryDummy = new CustomEntry(child);
                        customEntryDummy.set_dummy(true);
                        item.getChildren().add(new TreeItem<>(customEntryDummy));
                    }
                    entryTreeItem.getChildren().add(item);
                    _treeView.refresh();
                }
            }
        } catch (Exception e) { }
    }

    @Override
    public void setProgress(String taskName, double progress) { }

    @Override
    public void setMain(Main main) {
        _main = main;
        _choiceBoxEnviroment.setItems(_main._ctManager._settingsController._connectionObservableList);
        _this = this;
        initProgressWindow(_main);
        _treeView = new TreeView<>();
        VBox.setVgrow(_treeView, Priority.ALWAYS);
        _treeView.setMaxHeight(Double.MAX_VALUE);

        _treeView.setCellFactory(new Callback<TreeView<CustomEntry>, TreeCell<CustomEntry>>() {
            @Override
            public TreeCell<CustomEntry> call(TreeView<CustomEntry> param) {
                return new TreeCell<CustomEntry>() {
                    protected void updateItem(CustomEntry item, boolean empty) {
                        textProperty().unbind();
                        styleProperty().unbind();
                        if (empty || item == null) {
                            setGraphic(null);
                            textProperty().set(null);
                            styleProperty().set(null);
                            return;
                        }
                        if (item != null) {
                            styleProperty().bind(item.StyleProperty());
                            textProperty().bind(item.rdnProperty());
                        }
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
                .addListener(new ChangeListener<TreeItem<CustomEntry>>() {
                    @Override
                    public void changed(ObservableValue<? extends TreeItem<CustomEntry>> observable, TreeItem<CustomEntry> oldValue, TreeItem<CustomEntry> newValue) {
                        if (newValue != null) {
                            _observedEntry = newValue;
                            _selectedDN = _observedEntry.getValue().getDn();
                            //_main.get_entryDiffView().updateValues(_observedEntry.getValue());
                            _treeView.fireEvent(new LdapExplorerEvent(LdapExplorerEvent.ELEMENT_SELECTED, _observedEntry));
                            _hboxFilter.setDisable(false);

                        }
                    }
                });
    }

}
