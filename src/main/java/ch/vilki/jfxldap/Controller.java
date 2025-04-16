package ch.vilki.jfxldap;

import ch.vilki.jfxldap.backend.Config;
import ch.vilki.jfxldap.gui.CollectionsController;
import ch.vilki.jfxldap.gui.CustomLdapFxToolbar;
import ch.vilki.jfxldap.gui.GuiHelper;
import ch.vilki.jfxldap.gui.LdifEditorController;
import ch.vilki.secured.SecStorage;
import ch.vilki.secured.SecureString;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import jfxtras.styles.jmetro.JMetro;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static ch.vilki.jfxldap.Main._ctManager;

/**
 * Controller for the main application window
 */
public class Controller implements Initializable {

    private static final Logger logger = LogManager.getLogger(Controller.class);

    @FXML private SplitPane _mainPane;
    @FXML private MenuItem _keyStore;
    @FXML private MenuItem _openProject;
    @FXML private MenuItem _newProject;
    @FXML private MenuItem _saveProject;
    @FXML private MenuItem _closeProject;
    @FXML private MenuItem _changeMasterPassword;
    @FXML private MenuItem _settings;
    @FXML private MenuItem _exit;
    @FXML private MenuItem _ldifEditor;
    @FXML private MenuItem _editMenuItem;
    @FXML private BorderPane _mainWindow;

    private int counter = 0;
    private JMetro jMetro;
    HBox _collectionProjectNode = null;
    CustomLdapFxToolbar _toolBar = null;

    private Stage _primaryStage;

    //private ArrayList<SplitPane> _mainSplitPanes = new ArrayList;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initControlls();
        _mainWindow.setBottom(_toolBar);
    }

    public void initControlls(){
        logger.debug("Init controlls");
        initAllSplitPanes();
        _toolBar = new CustomLdapFxToolbar(Main._main);
        _toolBar.buttonLdapExplorerSourceWindow.setOnAction(x->showComponentWindow(0, _ctManager._ldapSourceExploreCtrl.get_window(),"EXPLORER",true));
        _toolBar.buttonEntryView.setOnAction(x->  showComponentWindow(1, _ctManager._entryView,"ENTRY VIEW",false));
        _toolBar.buttonCompareResultWindow.setOnAction(x->  showComponentWindow(2, _ctManager._ldapCompareController.get_window(),"LDAP COMPARE",false));
        _toolBar.buttonSettings.setOnAction(x-> _ctManager._settingsController.get_stage().showAndWait());
        _toolBar.buttonLdapExplorerTargetWindow.setOnAction(x->  showComponentWindow(0, _ctManager._ldapTargetExploreController.get_window(),"TARGET",true));
        _toolBar.buttonSearchResultWindow.setOnAction(x-> showComponentWindow(1,Main._ctManager._searchResultController.get_window(),"SEARCH RESULT",false));
        _mainPane.autosize();
        initMenu();
    }

    public Boolean findElement(Node searchPane){
        logger.info("find id=" + searchPane.getTypeSelector());
        Set<Node> found = _mainPane.lookupAll(searchPane.getTypeSelector());
        AtomicReference<Boolean> retValue = new AtomicReference<>(false);
        if(found != null && !found.isEmpty()){
            found.forEach(x->{
                if(x.equals(searchPane)) retValue.set(true);
            });

        }
        logger.info("found object = " + retValue.get());
       return retValue.get();
    }

    private void initAllSplitPanes(){
        for(int i=0; i<3; i++) {
            SplitPane sp = new SplitPane();
            sp.setOrientation(Orientation.VERTICAL);
            sp.setMinSize(100,100);
            sp.getStyleClass().add("custom-split-pane");
            _mainPane.getItems().add(sp);
            DetachableTabPane detachableTabPane = createDetachableTabPane();

            detachableTabPane.setOnClosedPassSibling(x-> cleanupGui());
            if(i==0){
                detachableTabPane.addTab("EXPLORER", _ctManager._ldapSourceExploreCtrl.get_window());
                detachableTabPane.addTab("TARGET", _ctManager._ldapTargetExploreController.get_window());
                ((SplitPane)_mainPane.getItems().get(i)).getItems().add(detachableTabPane);
            }
            if(i==1){
                 detachableTabPane.addTab("SEARCH RESULT", _ctManager._searchResultController.get_window());
                ((SplitPane)_mainPane.getItems().get(i)).getItems().add(detachableTabPane);
                detachableTabPane = createDetachableTabPane();
                detachableTabPane.addTab("ENTRY VIEW", _ctManager._entryView);
                ((SplitPane)_mainPane.getItems().get(i)).getItems().add(detachableTabPane);
            }
            if(i==2){
                detachableTabPane.addTab("LDAP COMPARE", _ctManager._ldapCompareController.get_window());
                ((SplitPane)_mainPane.getItems().get(i)).getItems().add(detachableTabPane);
            }
        }
    }

    private void cleanupGui(){
        if(_mainPane.getItems() == null || _mainPane.getItems().isEmpty()) return;
        logger.info("main pane has " + _mainPane.getItems().size() + " elements");
        _mainPane.getItems().forEach(x->{
            logger.info(x.getTypeSelector());
            if(x.getTypeSelector().equals("SplitPane")){
                SplitPane sp = (SplitPane) x;
                if(sp.getItems() == null || sp.getItems().isEmpty())
                {
                    logger.info("remove split pane now ");
                    _mainPane.getItems().remove(x);
                }
            }
        });

        _mainPane.autosize();
        _mainPane.getItems().forEach(x->{

        });
    }

    private void showComponentWindow(Integer tabPosition, Node pane, String name, Boolean addToDetachable ){
       if(findElement(pane)) return;
       if(_mainPane.getItems() == null || _mainPane.getItems().isEmpty()){
           initAllSplitPanes();
           return;
       }
       if(_mainPane.getItems().size() <= tabPosition){
          addDetachablePane(pane,name);
          return;
       }
       if(_mainPane.getItems().get(tabPosition).getTypeSelector().equals("SplitPane"))
       {
           SplitPane sp = (SplitPane) _mainPane.getItems().get(tabPosition);
           DetachableTabPane detachableTabPane = createDetachableTabPane();
           detachableTabPane.setOnClosedPassSibling(x-> cleanupGui());
           detachableTabPane.addTab(name, pane);
           sp.getItems().add(detachableTabPane);
           return;
       }
       if(_mainPane.getItems().get(tabPosition).getTypeSelector().equals("DetachableTabPane")){
           DetachableTabPane tp = (DetachableTabPane) _mainPane.getItems().get(tabPosition);
           tp.addTab(name,pane);
       }
    }

    private void addDetachablePane(Node pane, String name){
        SplitPane sp = new SplitPane();
        sp.setOrientation(Orientation.VERTICAL);
        sp.setMinSize(100,100);
        DetachableTabPane detachableTabPane = createDetachableTabPane();
        detachableTabPane.addTab(name, pane);
        sp.getItems().add(detachableTabPane);
        _mainPane.getItems().add(sp);
    }

    public DetachableTabPane addDetachablePaneToMainWindow(Integer position){
        logger.debug("called addTabPane on position " + position);
        AtomicReference<DetachableTabPane> tabPane = new AtomicReference<>(new DetachableTabPane());
        tabPane.get().setOnClosedPassSibling(tabPane::set);
        if(_mainPane.getItems().size() >= position){
            logger.debug("Adding on position " + position);
            _mainPane.getItems().add( position,tabPane.get());
        }

        else _mainPane.getItems().add(tabPane.get());
        return tabPane.get();
    }

    public DetachableTabPane createDetachableTabPane(){
        AtomicReference<DetachableTabPane> tabPane = new AtomicReference<>(new DetachableTabPane());
        tabPane.get().setOnClosedPassSibling(tabPane::set);
        return tabPane.get();
    }

    public void setPrimaryStage(Stage stage){
        this._primaryStage = stage;
    }

    /**
     * Initialize the menu items
     * This method is called from Main.java
     */
    public void initMenu(){
        _saveProject.setDisable(true);
        _closeProject.setDisable(true);
        _settings.setOnAction(event -> _ctManager._settingsController.showWindow());
        _ldifEditor.setOnAction(event -> {
            if (_ctManager._ldapSourceExploreCtrl.get_currentConnection() == null) {
                GuiHelper.ERROR("Keine Verbindung", "Es ist keine LDAP-Verbindung aktiv. Bitte verbinden Sie sich zuerst mit einem LDAP-Server.");
                return;
            }

            openLdifEditor();
        });
        _keyStore.setOnAction(x->{
            if(Main._configuration.get_keyStore() == null)
            {
                GuiHelper.ERROR("No keystore defined","Create or open keystore in settings first");
                return;
            }
            _ctManager._keyStoreController.showWindow();
        });
        _changeMasterPassword.setOnAction(x->{
            String currentMaster = null;
            if(Main._configuration.is_configSecured())
            {
                currentMaster = GuiHelper.enterPassword("Password","Enter current master password");

                if(currentMaster == null || !Main._configuration.get_secStorage().checkMasterKey(new SecureString(currentMaster)))
                {
                    GuiHelper.ERROR("Master key no match","Master key does not match current master key");
                    return;
                }
            }
            String first = GuiHelper.enterPassword("Password","Enter new password");
            if(first == null) return;
            if(first.length() < 8){
                GuiHelper.ERROR("Password error","Password length must be at least 8");
                return;
            }
            String second = GuiHelper.enterPassword("Password","Retype new password");
            if(second == null) return;
            if(!second.equals(first))
            {
                GuiHelper.ERROR("Password error","Passwords do not match");
                return;
            }
            try {
                SecStorage cur = SecStorage.open_SecuredStorage(Config.getConfigurationFile(),new SecureString(currentMaster));
                SecureString kPass = cur.getPropValue("general@@_keyStorePassword");
                SecStorage.changeMasterPassword(Config.getConfigurationFile(),new SecureString(currentMaster),new SecureString(second));
                Main._configuration.openConfiguration(Config.getConfigurationFile(),new SecureString(second));
                if(kPass!=null) Main._configuration.set_keyStorePassword(kPass);
            } catch (Exception e) {
                LogManager.getLogger(Controller.class).error("Exception occured", e);
                GuiHelper.EXCEPTION("Error changing master password", e.getMessage(), e);
            }
            GuiHelper.INFO("Master Operation Change","Master Password changed!");
        });
        _exit.setOnAction(x->{
            System.exit(0);
        });

        _newProject.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                if(_ctManager._collectionsController.get_currentCollectionsProject() != null)
                {
                    if(!GuiHelper.confirm("New Project","Close current project?","Current project->" +
                            _ctManager._collectionsController.get_currentCollectionsProject().get_projectName() + " shall be closed now")) return;
                }
                if(!_ctManager._collectionsController.newProject()) return;
                if(_ctManager._collectionsController != null)
                {
                    showComponentWindow(2,_ctManager._collectionsController.getWindow(),"Collections",false);
                    //_collectionProjectNode = new HBox( _ctManager._collectionsController.getWindow());
                    //_collectionProjectNode.setPrefSize(800,800);
                    _ctManager._ldapSourceExploreCtrl.switch2CollectionTree();
                    _saveProject.setDisable(false);
                    _closeProject.setDisable(false);
                }
                showComponentWindow(2,_ctManager._collectionsController.getWindow(),"Collections",false);
            }
        });

        _openProject.setOnAction(event -> {
            if(CollectionsController.get_currentCollectionsProject() != null)
            {
                if(!GuiHelper.confirm("New Project","Close current project?","Current project->" +
                        CollectionsController.get_currentCollectionsProject().get_projectName() + " shall be closed now")) return;
                else
                {
                    _ctManager._collectionsController.closeProject();
                }
            }
            if(!_ctManager._collectionsController.openProject()) return;
            showComponentWindow(2,_ctManager._collectionsController.getWindow(),"Collections",false);
            if(_collectionProjectNode == null)
            {
                //_collectionProjectNode = new HBox(_ctManager._collectionsController.getWindow());
                //_collectionProjectNode.setPrefSize(800,800);
                _ctManager._ldapSourceExploreCtrl.switch2CollectionTree();
                _saveProject.setDisable(false);
                _closeProject.setDisable(false);
            }
            else
            {
                _ctManager._ldapSourceExploreCtrl.switch2CollectionTree();
                _saveProject.setDisable(false);
                _closeProject.setDisable(false);
            }
        });

        _saveProject.setOnAction(e->{
            try {
                _ctManager._collectionsController.saveProject();
            } catch (Exception e1) {
                LogManager.getLogger(Controller.class).error("Exception during project save",e);
                GuiHelper.EXCEPTION("Error saving project",e1.getMessage(),e1);
            }
        });
        _closeProject.setOnAction(e->{
            _ctManager._collectionsController.closeProject();
             _ctManager._ldapSourceExploreCtrl.switch2LdapTree();
            _saveProject.setDisable(true);
            _closeProject.setDisable(true);
        });
        _editMenuItem.setOnAction(e -> openAttributeEditor());
    }

    /**
     * Opens the LDIF editor dialog
     */
    private void openLdifEditor() {
        try {
            // Load the LDIF editor FXML
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(Main.class.getResource("/ch/vilki/jfxldap/fxml/LdifEditor.fxml"));
            javafx.scene.Parent page = loader.load();
            
            // Get the controller and initialize it
            LdifEditorController controller = loader.getController();
            controller.setMain(Main._main);
            controller.setWindow(page);
            
            // Use the primary stage we have stored
            if (_primaryStage != null) {
                controller.setOwner(_primaryStage);
            } else if (_mainWindow.getScene() != null && _mainWindow.getScene().getWindow() instanceof javafx.stage.Stage) {
                controller.setOwner((javafx.stage.Stage) _mainWindow.getScene().getWindow());
            }
            controller.setLdapExploreController(_ctManager._ldapSourceExploreCtrl);

            // Show the dialog
            controller.show();
        } catch (java.io.IOException e) {
            GuiHelper.EXCEPTION("Error Opening LDIF Editor", "Failed to open LDIF editor", e);
        }
    }

    /**
     * Opens the attribute editor
     */
    private void openAttributeEditor() {
        try {
            // This will be expanded in the future to provide more editing capabilities
            GuiHelper.INFO("Attribute Editor", "The attribute editor is not yet implemented.\n\nPlease use the LDIF Editor for now.");
        } catch (Exception e) {
            GuiHelper.EXCEPTION("Error", "Error opening attribute editor", e);
        }
    }
}
