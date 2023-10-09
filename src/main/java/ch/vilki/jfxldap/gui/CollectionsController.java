package ch.vilki.jfxldap.gui;

import ch.vilki.jfxldap.Main;
import ch.vilki.jfxldap.backend.*;
import ch.vilki.secured.SecureString;
import com.unboundid.ldap.sdk.*;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class CollectionsController implements IProgress, ILoader {

    private Main _main;
    static Logger logger = LogManager.getLogger(LdapExploreController.class);
    @FXML
    TreeView<CollectionEntry> _treeView;

    @FXML
    VBox _progressPane;
    Stage _progressStage;
    Scene _progressScene;
    private ProgressWindowController _progressController = null;

    Scene _scene;
    Stage _stage;
    @FXML
    SplitPane _window;

    private TreeItem<CollectionEntry> _rootItem;

    @FXML
    private Parent embeddedProjectView;

    public CollectionProjectController getEmbeddedCollectionProjectController() {
        return embeddedProjectViewController;
    }

    @FXML
    private CollectionProjectController embeddedProjectViewController;


    /************* FILTER WINDOW **************/
    @FXML
    VBox _filterWindow;
    Stage _filterStage;
    Scene _filterScene;
    private FilterWindowController _filterWindowController = null;


    /***************** CONTEXT MENU ***********************/
    final ContextMenu _contextMenu = new ContextMenu();


    private static Connection _currentConnection = null;
    ExecutorService executor = Executors.newFixedThreadPool(5);
    private CollectionsController _collectionController;
    private static TreeItem<CollectionEntry> _observedEntry = null;
    private static UnboundidLdapSearch _currentReader = null;

    public static CollectionsProject get_currentCollectionsProject() {
        return _currentCollectionsProject;
    }

    private static CollectionsProject _currentCollectionsProject = null;
    private static boolean _connectionSetupRunning = false;

    ChangeListener expandedListener = null;

    @Override
    public void setMain(Main main) {
        _main = main;

        _collectionController = this;
        try {
            initProgressWindow(main);
            initFilterWindow();
        } catch (IOException e) {
            GuiHelper.EXCEPTION("Initialization error", "collection controller throws exception", e);
            logger.error(e);
        }
    }


    @Override
    public void setWindow(Parent parent) {
        _window = (SplitPane) parent;
        _scene = new Scene(_window);
        _stage = new Stage();
        _stage.setScene(_scene);
    }

    public SplitPane getWindow() {
        return _window;
    }

    @Override
    public void setOwner(Stage stage) {
        _stage.initOwner(stage);
    }

    void initFilterWindow() throws IOException {
        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(Main.class.getResource(ControllerManager.Companion.fxmlDir("FilterWindow.fxml")));
        _filterWindow = (VBox) loader.load();
        _filterWindowController = loader.getController();
        _filterWindowController.set_mainApp(_main);
        // _filterWindowController.get_observableConfigAllAttributes().addAll(_main.get_cfg()._allAttributes);
        _filterScene = new Scene(_filterWindow);
        _filterStage = new Stage();
        _filterStage.setScene(_filterScene);
        _filterStage.initStyle(StageStyle.DECORATED);
        _filterStage.initModality(Modality.APPLICATION_MODAL);
        _filterStage.initOwner(_main.get_primaryStage());
        _filterWindowController._radioButtonCompareAttributes.setText("Export only");
        _filterWindowController._radioButtonIgnoreAttributes.setText("Ignore");

        Button buttonCancel = new Button("CANCEL");
        buttonCancel.setCancelButton(true);
        Button buttonOK = new Button("APPLY");
        buttonOK.setDefaultButton(true);
        buttonOK.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                _filterStage.close();
                TreeItem<CollectionEntry> selectedItem = _treeView.getSelectionModel().getSelectedItem();
                if (selectedItem == null) return;
                CollectionEntry tableItem = embeddedProjectViewController.getEntryFromTable(selectedItem.getValue().getDn());

                if (!_filterWindowController._radioButtonDisableFilter.isSelected()) {
                    selectedItem.getValue().setAttributesAction(_filterWindowController._radioButtonIgnoreAttributes.isSelected());
                    selectedItem.getValue().setAttributes(_filterWindowController._listFilterAttributes.getItems());
                    tableItem.setAttributesAction(_filterWindowController._radioButtonIgnoreAttributes.isSelected());
                    tableItem.setAttributes(_filterWindowController._listFilterAttributes.getItems());
                } else {
                    Boolean action = null;
                    selectedItem.getValue().setAttributesAction(action);
                    selectedItem.getValue().setAttributes(null);
                    tableItem.setAttributesAction(action);
                    tableItem.setAttributes(null);

                }
                selectedItem.getValue().setLdapFilter(_filterWindowController._textFieldSourceFilter.getText());
                tableItem.setLdapFilter(_filterWindowController._textFieldSourceFilter.getText());
                updateIconStatus(selectedItem);
                setIconsStatus(selectedItem);
                embeddedProjectViewController._tableViewTargetDN.refresh();
            }
        });
        buttonCancel.setOnAction(e -> _filterStage.close());
        HBox hBox = new HBox();
        hBox.setAlignment(Pos.CENTER_RIGHT);
        buttonCancel.setPrefSize(80, 20);
        HBox.setMargin(buttonCancel, new Insets(3, 10, 3, 10));
        HBox.setMargin(buttonOK, new Insets(3, 10, 3, 10));
        buttonOK.setPrefSize(80, 20);
        hBox.getChildren().addAll(buttonCancel, buttonOK);
        _filterWindow.getChildren().add(hBox);

    }

    public void openFilterWindow() {
        if (_currentConnection != null) {
            _filterWindowController.setSchemaAttributes(_currentConnection.SchemaAttributes);
            _filterStage.showAndWait();
        }
    }

    @FXML
    private void initialize() {
        Icons icons = new Icons();
        _treeView = new TreeView<>();
        VBox.setVgrow(_treeView, Priority.ALWAYS);
        _treeView.prefWidth(Double.MAX_VALUE);

        MenuItem childrenFilter = new MenuItem("Filter Subentries");
        MenuItem addEntry = new MenuItem("Add Entry");
        MenuItem addTree = new MenuItem("Add Tree");
        MenuItem remove = new MenuItem("remove");
        MenuItem importType = new MenuItem("");
        addEntry.setOnAction(e -> addCollectionEntry());
        addTree.setOnAction(e -> addCollectionTree());
        remove.setOnAction(e -> removeCollectionEntry());
        childrenFilter.setOnAction(e -> openFilterWindow());
        importType.setOnAction(e -> {
            TreeItem<CollectionEntry> item = _treeView.getSelectionModel().getSelectedItem();
            if (item == null) return;
            item.getValue().setOverwriteEntry(!item.getValue().getOverwriteEntry());
            CollectionEntry tableEntry = embeddedProjectViewController.getEntryFromTable(item.getValue().getDn());
            CollectionEntry treeEntry = item.getValue();
            embeddedProjectViewController._tableViewTargetDN.refresh();
        });
        _treeView.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
            if (e.getButton() == MouseButton.SECONDARY) {
                TreeItem<CollectionEntry> item = _treeView.getSelectionModel().getSelectedItem();
                if (item != null) {
                    _contextMenu.getItems().clear();
                    if (item.getValue().isSelected()) {
                        _contextMenu.getItems().add(remove);
                        if (item.getValue().getOverwriteEntry()) importType.setText("update target");
                        else importType.setText("overwrite target");
                        _contextMenu.getItems().add(importType);
                        if (!item.getValue().isSubtree()) {
                            _contextMenu.getItems().add(addTree);
                        } else {
                            _contextMenu.getItems().add(childrenFilter);
                        }
                    } else {
                        if (!item.getValue().isParentSelected()) {
                            _contextMenu.getItems().add(addEntry);
                            _contextMenu.getItems().add(addTree);
                        }
                    }
                    _contextMenu.show(_main.get_primaryStage(), e.getScreenX(), e.getScreenY());
                }
            }
        });

        embeddedProjectViewController._tableViewTargetDN.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
            if (e.getButton() == MouseButton.SECONDARY) {
                Object selected = embeddedProjectViewController._tableViewTargetDN.getSelectionModel().getSelectedItem();
                if (selected == null) return;
                embeddedProjectViewController._contextMenu.show(_main.get_primaryStage(), e.getScreenX(), e.getScreenY());
            }
        });

        embeddedProjectViewController.deleteEntry.setOnAction(e -> {
            Object object = embeddedProjectViewController._tableViewTargetDN.getSelectionModel().getSelectedItem();
            if (object == null) return;
            CollectionEntry collectionEntry = (CollectionEntry) object;
            embeddedProjectViewController._tableViewTargetDN.getItems().remove(object);
            embeddedProjectViewController._tableViewTargetDN.refresh();
            TreeItem<CollectionEntry> selectedItem = embeddedProjectViewController._searchCollectionMap.get(collectionEntry.getDn());
            if (selectedItem == null) return;
            selectedItem.getValue().setSelected(false);
            if (selectedItem.getValue().isSubtree()) {
                selectedItem.getValue().setSubtree(false);
                removeSubtreeRecursive(selectedItem);
            }
            embeddedProjectViewController._dnEntryObservableList.remove(selectedItem.getValue());
            embeddedProjectViewController._searchCollectionMap.remove(selectedItem.getValue().getDn());
            updateIconStatus(_rootItem);
            setIconsStatus(_rootItem);
        });
        embeddedProjectViewController._checkBoxSubtree.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                if (embeddedProjectViewController._tableViewTargetDN.getSelectionModel().getSelectedItem() == null)
                    return;
                CollectionEntry selected = (CollectionEntry) embeddedProjectViewController._tableViewTargetDN.getSelectionModel().getSelectedItem();
                selected.setSubtree(embeddedProjectViewController._checkBoxSubtree.isSelected());
                embeddedProjectViewController._tableViewTargetDN.refresh();
            }
        });

        embeddedProjectViewController._checkBoxMergeEntry.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                if (embeddedProjectViewController._tableViewTargetDN.getSelectionModel().getSelectedItem() == null)
                    return;
                CollectionEntry selected = (CollectionEntry) embeddedProjectViewController._tableViewTargetDN.getSelectionModel().getSelectedItem();
                selected.setOverwriteEntry(embeddedProjectViewController._checkBoxMergeEntry.isSelected());
                embeddedProjectViewController._tableViewTargetDN.refresh();

            }
        });
        embeddedProjectViewController._checkBoxDeleteTarget.setOnAction(event -> {
            if (embeddedProjectViewController._tableViewTargetDN.getSelectionModel().getSelectedItem() == null)
                return;
            CollectionEntry selected = (CollectionEntry) embeddedProjectViewController._tableViewTargetDN.getSelectionModel().getSelectedItem();
            selected.setOverwriteEntry(embeddedProjectViewController._checkBoxDeleteTarget.isSelected());
            embeddedProjectViewController._tableViewTargetDN.refresh();
        });
        embeddedProjectViewController._buttonImportLDIF.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                if (_currentConnection == null) {
                    GuiHelper.ERROR("Connection not set ", "Connect to target first!");
                    return;
                }
                if (!_currentConnection.isConnected()) {
                    GuiHelper.ERROR("Export Error", "LDAP Connection error ");
                    return;
                }

                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("Select import LDIF File");
                FileChooser.ExtensionFilter filter = new FileChooser.ExtensionFilter("LDIF files (*.ldif)", "*.ldif");
                fileChooser.getExtensionFilters().add(filter);
                fileChooser.setInitialDirectory(new File(_main._configuration.get_lastUsedDirectory()));
                File selectedFile = fileChooser.showOpenDialog(_main.get_primaryStage());
                if (selectedFile == null) return;
                _main._configuration.set_lastUsedDirectory(selectedFile.getParent());
                CollectionImport collectionImport = new CollectionImport();
                ExecutorService executor = Executors.newSingleThreadExecutor();
                _progressStage.show();
                executor.submit(() -> {
                    if (!collectionImport.loadFile(selectedFile, _collectionController, null)) {
                        Platform.runLater(() -> GuiHelper.ERROR("File Load Error", "Could not load file"));
                        return;
                    }
                    Platform.runLater(() -> _progressController.setProgress(0, "File LOADED in MEMORY, Import in Target now"));
                    try {
                        collectionImport.importInEnviroment(false, _currentConnection, CollectionImport.IMPORT_OPTIONS.ADD_OR_MODIFY);
                    } catch (Exception e) {
                        Platform.runLater(() -> {
                                    GuiHelper.EXCEPTION("Import Error", "Exception during ldif import", e);
                                    logger.error(e);
                                }
                        );
                        return;
                    }
                });
            }
        });

        embeddedProjectViewController._buttonExportLDIF.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                if (_currentCollectionsProject == null) {
                    GuiHelper.ERROR("Export Error", "Project is not set");
                    return;
                }
                if (_currentConnection == null || !_currentConnection.isConnected()) {
                    GuiHelper.ERROR("Export Error", "LDAP Connection not set ");
                    return;
                }
                DirectoryChooser chooser = new DirectoryChooser();
                chooser.setTitle("Select destination directory");
                File defaultDirectory = new File(_main._configuration.get_lastUsedDirectory());
                chooser.setInitialDirectory(defaultDirectory);
                File selectedDirectory = chooser.showDialog(_main.get_primaryStage());
                if (selectedDirectory == null) return;
                _main._configuration.set_lastUsedDirectory(selectedDirectory.getAbsolutePath());
                _progressStage.show();
                ExecutorService executor = Executors.newSingleThreadExecutor();
                executor.submit(() -> {
                    try {
                        List<String> missedEntries = _currentCollectionsProject.exportLdif(selectedDirectory.toString(), null,
                                _currentConnection, _collectionController);
                        if (!missedEntries.isEmpty()) {
                            StringBuilder builder = new StringBuilder();
                            for (String s : missedEntries) {
                                builder.append(s);
                                builder.append("\n");
                            }
                            Platform.runLater(() ->
                                    GuiHelper.ERROR_DETAILED("Exportine LDIF Error", "Not all entries were exported!", builder.toString()));
                        }

                    } catch (Exception e) {
                        Platform.runLater(() -> {
                            GuiHelper.EXCEPTION("File creation failed", "File export throws exception ", e);
                            logger.error(e);
                            return;
                        });
                    }
                });
            }
        });
        expandedListener = new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
                BooleanProperty bb = (BooleanProperty) observable;
                TreeItem t = (TreeItem<CollectionEntry>) bb.getBean();
                _observedEntry = t;
                embeddedProjectViewController._ldapEntryObservableList.clear();
                Collection<Attribute> attributes = _observedEntry.getValue().getEntry().getAttributes();
                for (Attribute attribute : attributes) {
                    for (String value : attribute.getValues()) {
                        TableLdapEntry tableLdapEntry = new TableLdapEntry(attribute.getName(), value);
                        embeddedProjectViewController._ldapEntryObservableList.add(tableLdapEntry);
                    }
                }
                embeddedProjectViewController._ldapEntryObservableList.stream().sorted().collect(Collectors.toList());
                TableLdapEntry tableLdapEntry = new TableLdapEntry("DN", _observedEntry.getValue().getEntry().getDN());
                embeddedProjectViewController._ldapEntryObservableList.add(0, tableLdapEntry);
                if (_observedEntry.isExpanded()) {
                    if (_observedEntry.getChildren().size() == 1 && _observedEntry.getChildren().get(0).getValue().is_dummy()) {
                        _observedEntry.getChildren().clear();
                        _currentReader = new UnboundidLdapSearch(_main._configuration,_currentConnection, _observedEntry.getValue().getEntry().getDN(), null, _collectionController);
                        _currentReader.setDisplayAttribute(_currentConnection.getDisplayAttribute());
                        executor.execute(_currentReader);
                    }
                }
            }
        };

        _treeView.getSelectionModel().selectedItemProperty()
                .addListener(new ChangeListener<TreeItem<CollectionEntry>>() {
                    @Override
                    public void changed(ObservableValue<? extends TreeItem<CollectionEntry>> observable, TreeItem<CollectionEntry> oldValue, TreeItem<CollectionEntry> newValue) {
                        if (newValue != null) {
                            _observedEntry = newValue;
                            embeddedProjectViewController._ldapEntryObservableList.clear();

                            Collection<Attribute> attributes = _observedEntry.getValue().getEntry().getAttributes();
                            for (Attribute attribute : attributes) {
                                for (String value : attribute.getValues()) {
                                    TableLdapEntry tableLdapEntry = new TableLdapEntry(attribute.getName(), value);
                                    embeddedProjectViewController._ldapEntryObservableList.add(tableLdapEntry);
                                }
                            }
                            embeddedProjectViewController._ldapEntryObservableList.stream().sorted().collect(Collectors.toList());
                            TableLdapEntry tableLdapEntry = new TableLdapEntry("DN", _observedEntry.getValue().getEntry().getDN());
                            embeddedProjectViewController._ldapEntryObservableList.add(0, tableLdapEntry);

                            _main.get_entryDiffView().updateValues(_observedEntry.getValue());

                        }
                    }
                });

        embeddedProjectViewController._tableViewTargetDN.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                CollectionEntry selectedEntry = (CollectionEntry) newSelection;
                embeddedProjectViewController._checkBoxDeleteTarget.setSelected(selectedEntry.getOverwriteEntry());
                embeddedProjectViewController._checkBoxSubtree.setSelected(selectedEntry.isSubtree());
                embeddedProjectViewController._checkBoxMergeEntry.setSelected(selectedEntry.getOverwriteEntry());
                TreeItem<CollectionEntry> treeItem = embeddedProjectViewController._searchCollectionMap.get(selectedEntry.getDn());
                if (treeItem != null) {
                    _treeView.getSelectionModel().select(treeItem);
                    int selectedIndex = _treeView.getSelectionModel().getSelectedIndex();
                    _treeView.scrollTo(selectedIndex);
                    // _treeView.getFocusModel().focus(selectedIndex);
                }
            }
        });
    }

    void initProgressWindow(Main main) throws IOException {
        FXMLLoader settingsLoader = new FXMLLoader();
        settingsLoader.setLocation(SettingsController.class.getResource(ControllerManager.Companion.fxmlDir("ProgressWindow.fxml")));
        _progressPane = (VBox) settingsLoader.load();
        _progressController = settingsLoader.getController();
        _progressScene = new Scene(_progressPane);
        _progressStage = new Stage();
        _progressStage.setTitle("TASK PROGRESS");
        _progressStage.setScene(_progressScene);
        _progressStage.initStyle(StageStyle.DECORATED);
        _progressStage.initModality(Modality.APPLICATION_MODAL);
        _progressStage.initOwner(main.get_primaryStage());

        _progressController._buttonCancel.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                Platform.runLater(() -> {
                    if (_currentCollectionsProject != null) _currentCollectionsProject.set_breakOperation(true);
                });
            }
        });
        _progressStage.setOnHiding(new EventHandler<WindowEvent>() {
            @Override
            public void handle(WindowEvent event) {
                Platform.runLater(() -> {

                    if (_currentCollectionsProject != null) _currentCollectionsProject.set_breakOperation(true);
                });
            }
        });
    }

    private void addProjectEntriesToTree(CollectionEntry selectedEntry) {
        try {
            if (_currentConnection.getEntry(selectedEntry.getDn()) == null) return;
        } catch (Exception e) {
            return;
        }
        TreeItem<CollectionEntry> found = null;
        String[] dnSplit = selectedEntry.getDn().split(",");
        for (int i = dnSplit.length - 1; i >= 0; i--) {
            StringBuilder builder = new StringBuilder();
            for (int j = i; j < dnSplit.length; j++) {
                builder.append(dnSplit[j]);
                builder.append(",");
            }
            builder.deleteCharAt(builder.length() - 1);
            String searchItem = builder.toString();
            TreeItem<CollectionEntry> f = findTreeItem(_treeView.getRoot(), builder.toString(), null);
            if (f != null) {
                found = f;
                if (found.getValue().getDn().equalsIgnoreCase(selectedEntry.getDn())) {
                    found.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.ENTRY_EQUAL));
                    embeddedProjectViewController._searchCollectionMap.put(selectedEntry.getDn(), found);
                    return;
                }

            } else {
                String searchDN = found.getValue().getDn();
                found.expandedProperty().removeListener(expandedListener);
                found.setExpanded(true);
                if (!found.getChildren().isEmpty()) found.getChildren().removeAll(found.getChildren());
                UnboundidLdapSearch unboundidLdapSearch = new UnboundidLdapSearch(_main._configuration,_currentConnection, searchDN, null, _collectionController);
                unboundidLdapSearch.run();
                TreeItem<CollectionEntry> addedWantedItem = null;
                for (Entry entry : unboundidLdapSearch.get_children()) {
                    CollectionEntry collectionEntry = new CollectionEntry(entry);
                    collectionEntry.setDisplayAttribute(_currentConnection.getDisplayAttribute());
                    TreeItem<CollectionEntry> ce = new TreeItem<>(collectionEntry);
                    found.getChildren().add(ce);
                    String entryDN = entry.getDN();
                    if (entryDN.equalsIgnoreCase(searchItem)) {
                        addedWantedItem = ce;
                    } else {
                        ce.expandedProperty().addListener(expandedListener);
                        CollectionEntry dummyCE = new CollectionEntry(found.getValue().getEntry());
                        dummyCE.setDummy();
                        TreeItem<CollectionEntry> dummyItem = new TreeItem<>(dummyCE);
                        ce.getChildren().add(dummyItem);
                    }
                }
                if (addedWantedItem != null) found = addedWantedItem;
                if (addedWantedItem.getValue().getDn().equalsIgnoreCase(selectedEntry.getDn())) {
                    addedWantedItem.expandedProperty().addListener(expandedListener);
                    addedWantedItem.getParent().setExpanded(true);
                    addedWantedItem.getValue().setSelected(true);
                    for (CollectionEntry tableEntry : embeddedProjectViewController._dnEntryObservableList) {
                        if (tableEntry.getDn().equalsIgnoreCase(addedWantedItem.getValue().getDn())) {
                            addedWantedItem.getValue().setAttributesAction(tableEntry.getAttributesAction());
                            addedWantedItem.getValue().setSubtree(tableEntry.isSubtree());
                            addedWantedItem.getValue().setAttributes(tableEntry.getAttributesAsSet().stream().collect(Collectors.toList()));
                            addedWantedItem.getValue().setLdapFilter(tableEntry.getLdapFilter());
                        }
                    }
                    CollectionEntry collectionEntry = new CollectionEntry(found.getValue().getEntry());
                    collectionEntry.setDummy();
                    TreeItem<CollectionEntry> dummyItem = new TreeItem<>(collectionEntry);
                    addedWantedItem.getChildren().add(dummyItem);
                    addedWantedItem.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.ENTRY_EQUAL));
                    embeddedProjectViewController._searchCollectionMap.put(selectedEntry.getDn(), addedWantedItem);
                }
            }
        }
    }

    private void updateIconStatus(TreeItem<CollectionEntry> entry) {
        if (entry == null) return;
        if (entry.getValue().isSubtree() || entry.getValue().isParentSelected()) {
            for (TreeItem<CollectionEntry> child : entry.getChildren()) {
                child.getValue().setParentSelected(true);
                updateIconStatus(child);
            }
        } else {
            for (TreeItem<CollectionEntry> child : entry.getChildren()) {
                child.getValue().setParentSelected(false);
                updateIconStatus(child);
            }
        }
    }

    private void setIconsStatus(TreeItem<CollectionEntry> entry) {
        if (entry == null) return;
        if (entry.getGraphic() != null && !entry.getValue().isSelected()) {
            entry.setGraphic(null);
        }
        if (entry.getValue().isSelected()) {
            if (entry.getValue().isSubtree()) {
                entry.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.TREE_SELECTED));
            } else {
                entry.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.ENTRY_SELECTED));
            }
        }
        if (entry.getValue().isParentSelected())
            entry.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.PARENT_SELECTED));

        for (TreeItem<CollectionEntry> ce : entry.getChildren()) {
            setIconsStatus(ce);
        }
    }

    public boolean newProject() {
        String projectName = GuiHelper.enterValue("PROJECT", "Project name", "Enter project name", false);
        if (projectName == null) {
            GuiHelper.ERROR("PROJECT", "Project name not set");
            return false;
        }
        _currentCollectionsProject = new CollectionsProject(_main._configuration,projectName);
        embeddedProjectViewController._textFieldProjectName.setText(projectName);
        _currentConnection = null;
        _treeView.setRoot(null);
        embeddedProjectViewController._dnEntryObservableList.clear();
        return true;
    }

    public void closeProject() {
        _currentCollectionsProject = null;

        embeddedProjectViewController._textFieldProjectName.setText("");
        if (_currentConnection != null) _currentConnection.disconect();
        _currentConnection = null;
        _treeView.setRoot(null);
        embeddedProjectViewController._dnEntryObservableList.clear();
    }

    public boolean openProject() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open Collections project");
        File defaultDirectory = new File(_main._configuration.get_lastUsedDirectory());
        if (Files.exists(defaultDirectory.toPath())) chooser.setInitialDirectory(defaultDirectory);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("excel project", "*.project.xlsx"));

        File selectedFile = chooser.showOpenDialog(_stage);
        if (selectedFile == null) return false;
        String projectName = selectedFile.getName();
        if (projectName == null) return false;
        projectName = projectName.toLowerCase().replaceAll(".project.xlsx", "");
        _main._configuration.set_lastUsedDirectory(selectedFile.getParent());
        ;
        _currentCollectionsProject = new CollectionsProject(_main._configuration,projectName);
        try {
            _currentCollectionsProject.readProject(selectedFile.getAbsolutePath());
        } catch (Exception e) {
            GuiHelper.EXCEPTION("Exception occured", e.toString(), e);
            return false;
        }
        embeddedProjectViewController._textFieldFileName.setText(_currentCollectionsProject.get_fileName());
        embeddedProjectViewController._textFieldProjectName.setText(_currentCollectionsProject.get_projectName());
        embeddedProjectViewController._dnEntryObservableList.clear();
        for (String entryName : _currentCollectionsProject.get_collectionEntries().keySet())
            embeddedProjectViewController._dnEntryObservableList.add(_currentCollectionsProject.get_collectionEntries().get(entryName));
        return true;

    }

    public void saveProject() throws Exception {
        if (_currentCollectionsProject.get_fileName() == null) {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Select destination directory");
            File defaultDirectory = new File(_main._configuration.get_lastUsedDirectory());
            if (Files.exists(defaultDirectory.toPath())) chooser.setInitialDirectory(defaultDirectory);
            File selectedDirectory = chooser.showDialog(_main.get_primaryStage());
            if (selectedDirectory == null) return;
            _main._configuration.set_lastUsedDirectory(selectedDirectory.getAbsolutePath());
            _currentCollectionsProject.setCollectionEntries(embeddedProjectViewController._dnEntryObservableList);
            _currentCollectionsProject.writeProject(selectedDirectory.getAbsolutePath(), false);
            embeddedProjectViewController._textFieldFileName.setText(_currentCollectionsProject.get_fileName());
            embeddedProjectViewController._textFieldProjectName.setText(_currentCollectionsProject.get_projectName());
        } else {
            _currentCollectionsProject.setCollectionEntries(embeddedProjectViewController._dnEntryObservableList);
            _currentCollectionsProject.writeProject(_currentCollectionsProject.get_fileName(), true);
        }
    }

    private TreeItem<CollectionEntry> findTreeItem(TreeItem<CollectionEntry> entry, String dn, TreeItem<CollectionEntry> found) {
        if (entry == null || found != null) return found;
        if (entry.getValue().getDn().equalsIgnoreCase(dn)) {
            found = entry;
            return found;
        }
        for (TreeItem<CollectionEntry> child : entry.getChildren()) {
            found = findTreeItem(child, dn, found);
        }
        return found;
    }


    private void setDisplayDn(TreeItem<CollectionEntry> entry) {
        StringBuilder builder = new StringBuilder();
        builder.append(entry.getValue().getRdn());
        builder.append(",");
        TreeItem<CollectionEntry> parent = entry.getParent();
        while (parent != null) {
            builder.append(parent.getValue().getRdn());
            builder.append(",");
            parent = parent.getParent();
        }
        builder.deleteCharAt(builder.length() - 1);
        entry.getValue().setDisplayDN(builder.toString());
    }

    @Override
    public void signalTaskDone(String taskName, String description, Exception e) {
        Platform.runLater(() -> {
            if (taskName != null && taskName.equalsIgnoreCase("ldifexport")) {
                if (e != null) {
                    GuiHelper.EXCEPTION("Exception", "Error exporting ldif", e);
                } else {
                    _progressStage.hide();
                    GuiHelper.INFO("LDIF Export done", _currentCollectionsProject.get_fileName());
                }

            } else if (taskName != null && taskName.equalsIgnoreCase(CollectionImport.TASK_NAME)) {
                if (e != null) {
                    GuiHelper.EXCEPTION("Exception", "Error importing ldif", e);
                } else {
                    _progressStage.hide();
                    GuiHelper.INFO("LDIF Import done", "Entries imported");
                }
            } else {
                if (_currentReader != null) {
                    if (_rootItem == null) {
                        CollectionEntry rootCollectionEntry = new CollectionEntry(_currentReader.get_mainEntry());
                        rootCollectionEntry.setDisplayAttribute(_currentConnection.getDisplayAttribute());
                        rootCollectionEntry.setDisplayDN(rootCollectionEntry.getRdn());
                        _rootItem = new TreeItem<>(rootCollectionEntry);
                        List<CollectionEntry> items = new ArrayList();
                        for (Entry entry : _currentReader.get_children()) {
                            CollectionEntry collectionEntry = new CollectionEntry(entry);

                            if (_currentConnection.getDisplayAttribute() != null) {
                                collectionEntry.setDisplayAttribute(_currentConnection.getDisplayAttribute());
                            }
                            items.add(collectionEntry);
                        }
                        List<CollectionEntry> sorted_items = items.stream().sorted().collect(Collectors.toList());
                        for (CollectionEntry collectionEntry : sorted_items) {
                            TreeItem<CollectionEntry> item = new TreeItem<>(collectionEntry);
                            Entry child = null;
                            try {
                                child = _currentReader.getOneChild(collectionEntry.getEntry().getDN());
                            } catch (Exception e1) {
                                e1.printStackTrace();
                            }
                            if (child != null) {
                                CollectionEntry collectionEntryDummy = new CollectionEntry(child);
                                collectionEntryDummy.setDummy();
                                TreeItem<CollectionEntry> dummyItem = new TreeItem<>(collectionEntryDummy);
                                item.expandedProperty().addListener(expandedListener);
                                item.getChildren().add(new TreeItem<>(collectionEntryDummy));
                            }
                            _rootItem.getChildren().add(item);
                            setDisplayDn(item);
                            _rootItem.setExpanded(true);
                        }

                        _treeView.setRoot(_rootItem);

                    } else {
                        if (_observedEntry != null) {
                            List<CollectionEntry> items = new ArrayList();
                            for (Entry entry : _currentReader.get_children()) {
                                CollectionEntry collectionEntry = new CollectionEntry(entry);
                                if (_currentConnection.getDisplayAttribute() != null) {
                                    collectionEntry.setDisplayAttribute(_currentConnection.getDisplayAttribute());
                                }
                                items.add(collectionEntry);
                            }
                            List<CollectionEntry> sorted_items = items.stream().sorted().toList();
                            for (CollectionEntry collectionEntry : sorted_items) {
                                TreeItem<CollectionEntry> item = new TreeItem<>(collectionEntry);
                                if (_observedEntry.getValue().isSubtree()) {
                                    item.getValue().setParentSelected(true);
                                }
                                Entry child = null;
                                try {
                                    child = _currentReader.getOneChild(collectionEntry.getEntry().getDN());
                                } catch (Exception e1) {
                                    e1.printStackTrace();
                                }
                                item.getValue().setDisplayDN(_observedEntry.getValue().getDisplayDN() + "->" + item.getValue().getRdn());
                                if (child != null) {
                                    item.expandedProperty().addListener(expandedListener);
                                    CollectionEntry collectionEntryDummy = new CollectionEntry(child);
                                    collectionEntryDummy.setDummy();
                                    item.getChildren().add(new TreeItem<>(collectionEntryDummy));
                                }
                                _observedEntry.getChildren().add(item);
                                setDisplayDn(item);
                            }
                            _observedEntry.setExpanded(true);

                        }
                    }
                }
                updateIconStatus(_rootItem);
                setIconsStatus(_rootItem);
                if (_connectionSetupRunning) {
                    _connectionSetupRunning = false;
                    if (!embeddedProjectViewController._dnEntryObservableList.isEmpty()) {
                        embeddedProjectViewController._searchCollectionMap.clear();
                        for (CollectionEntry collEntry : embeddedProjectViewController._dnEntryObservableList)
                            addProjectEntriesToTree(collEntry);
                    }
                }
            }
        });
    }

    public void connect(Connection selectedConnection) {
        _observedEntry = null;
        _rootItem = null;
        if (selectedConnection == null) {
            GuiHelper.ERROR("Connection not selected", "Select connection first");
            return;
        }
        Connection connection = null;
        if (_currentConnection != null) _currentConnection.disconect();
        _currentConnection = null;
        SecureString password = null;
        password = _main._configuration.getConnectionPassword(selectedConnection);

        if (password == null) {
            password = new SecureString(GuiHelper.enterPassword("Source Connection Password", "Enter login password"));
            connection = selectedConnection.copy();
            if (password.get_value() == null) return;
            connection.setPassword(password.toString());
        } else {
            connection = selectedConnection.copy();
            connection.setPassword(password.toString());
        }
        try {
            connection.connect();
            String selectedValue = connection.getBaseDN();
            RootDSE root = connection.getRootDSE();
            String context[] = root.getNamingContextDNs();
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
            _currentConnection = connection;
            _currentReader = new UnboundidLdapSearch(_main._configuration,_currentConnection, _currentConnection.getBaseDN(), null, _collectionController);
            _currentReader.setDisplayAttribute(_currentConnection.getDisplayAttribute());
            _connectionSetupRunning = true;
            executor.execute(_currentReader);
            _connectionSetupRunning = true;

        } catch (Exception e) {
            e.printStackTrace();
            GuiHelper.EXCEPTION("Connection Error", e.toString(), e);
            _currentConnection = null;
            _currentReader = null;
            _connectionSetupRunning = false;
            return;
        }
    }


    public void addCollectionEntry() {

        TreeItem<CollectionEntry> selectedItem = _treeView.getSelectionModel().getSelectedItem();
        if (selectedItem == null) return;
        if (!embeddedProjectViewController._dnEntryObservableList.contains(selectedItem.getValue()))
            embeddedProjectViewController._dnEntryObservableList.add(selectedItem.getValue());
        embeddedProjectViewController._searchCollectionMap.put(selectedItem.getValue().getDn(), selectedItem);
        selectedItem.getValue().setSelected(true);
        selectedItem.getValue().setSubtree(false);
        updateIconStatus(selectedItem);
        setIconsStatus(selectedItem);
    }


    public void addCollectionTree() {
        TreeItem<CollectionEntry> selectedItem = _treeView.getSelectionModel().getSelectedItem();
        selectedItem.getValue().setSubtree(true);
        selectedItem.getValue().setSelected(true);

        if (!embeddedProjectViewController._dnEntryObservableList.contains(selectedItem.getValue()))
            embeddedProjectViewController._dnEntryObservableList.add(selectedItem.getValue());

        embeddedProjectViewController._searchCollectionMap.put(selectedItem.getValue().getDn(), selectedItem);
        selectedItem.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.TREE_SELECTED));
        updateIconStatus(selectedItem);
        setIconsStatus(selectedItem);

    }


    public void removeCollectionEntry() {
        TreeItem<CollectionEntry> selectedItem = _treeView.getSelectionModel().getSelectedItem();
        if (selectedItem == null) return;
        selectedItem.getValue().setSelected(false);
        if (selectedItem.getValue().isSubtree()) {
            selectedItem.getValue().setSubtree(false);
            removeSubtreeRecursive(selectedItem);
        }
        embeddedProjectViewController._dnEntryObservableList.remove(selectedItem.getValue());
        embeddedProjectViewController._searchCollectionMap.remove(selectedItem.getValue().getDn());
        updateIconStatus(_rootItem);
        setIconsStatus(_rootItem);
    }

    private void removeSubtreeRecursive(TreeItem<CollectionEntry> collectionEntryTreeItem) {
        if (collectionEntryTreeItem == null) return;
        for (TreeItem<CollectionEntry> child : collectionEntryTreeItem.getChildren()) {
            if (child.getValue().isParentSelected()) child.getValue().setParentSelected(false);
            removeSubtreeRecursive(child);
        }
    }


    @Override
    public void setProgress(String taskName, double progress) {
        Platform.runLater(() -> {

        });
    }

    @Override
    public void setProgress(double progress, String description) {
        Platform.runLater(() -> {
            Platform.runLater(() -> _progressController.setProgress(progress, description));
        });
    }


}
