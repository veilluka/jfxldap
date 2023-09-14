package ch.vilki.jfxldap.gui;

import ch.vilki.jfxldap.Main;
import ch.vilki.jfxldap.backend.IProgress;
import ch.vilki.jfxldap.backend.SearchEntry;
import ch.vilki.jfxldap.backend.SearchTree;
import com.unboundid.ldap.sdk.ModifyRequest;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class SearchResultController implements ILoader, IProgress {

    static Logger logger = LogManager.getLogger(SearchResultController.class);

    TextField textFieldReplace;
    Button buttonReplaceAll;

    final ContextMenu _searchResultcontextMenu = new ContextMenu();
    public SearchTree _searchTree = new SearchTree();

    Scene _scene;
    Stage _stage;
    SplitPane _searchResultWindow;
    @FXML CheckBox _checkBoxCreateLdifReplace;

    /*......................... CONTEXT MENU ...................*/
    static String TAB="     ";

    MenuItem _replace_value = new MenuItem(TAB+"Replace value",Icons.get_iconInstance().getIcon(Icons.ICON_NAME.SET_ATTRIBUTE_SMALL));
    MenuItem _deleteEntry = new MenuItem(TAB+"Delete entry",Icons.get_iconInstance().getIcon(Icons.ICON_NAME.CLOSE_FILE));
    MenuItem _exportEntry = new MenuItem(TAB+"File export",Icons.get_iconInstance().getIcon(Icons.ICON_NAME.EXPORT_SMALL));
    MenuItem _clipBoardLDIF = new MenuItem(TAB+"Clipboard LDIF",Icons.get_iconInstance().getIcon(Icons.ICON_NAME.COPY_PASTE_SMALL));


    Main _main;
    public LdapExploreController get_ldapExploreController() {
        return _ldapExploreController;
    }
    private void set_ldapExploreController() {
       
        if(_main._ctManager._startSearchController .is_ldapExplorerTargetAction())
        {
           _ldapExploreController = _main._ctManager._ldapTargetExploreController;
        }
        else
        {
            _ldapExploreController = _main._ctManager._ldapSourceExploreCtrl;
        }
    }
    private LdapExploreController _ldapExploreController = null;
    public SplitPane get_window(){return  _searchResultWindow;}

    public SearchResultController(){}
    private void initSearchResultContextMenu()
    {
        _deleteEntry.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent e)
            {
                deleteSelectedEntries();
            }
        });
        _deleteEntry.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.REMOVE));

        _replace_value.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent e)
            {
               List<TreeItem<SearchEntry>> selected = _searchTree.getSelectionModel().getSelectedItems();
               if(selected == null || selected.isEmpty()) return;
               String replace = GuiHelper.enterValue("REPLACE STRING VALUE ",_searchTree.get_searchValue() +
                       " will be replaced with:", "New value"+_searchTree.get_searchValue(),true);
               if(replace == null) return;
               if(replace.equalsIgnoreCase(""))
                   if(!GuiHelper.confirm("Delete value","You have not entered any value","Original value will be deleted")) return;
                ExecutorService executor = Executors.newSingleThreadExecutor();
                _main._ctManager._progressWindowController.clearProgressWindow();
                _main._ctManager._progressWindowController._stage.show();
                executor.execute(()->{
                    try {
                        _searchTree.replaceStringGetModifications(selected,_searchTree.get_searchValue(),replace,_main._ctManager._searchResultController);
                    }
                    catch (Exception e1)
                    {
                        GuiHelper.EXCEPTION("Error replacing",e1.getMessage(),e1);
                    }

                });
            }
        });
        _exportEntry.setOnAction(x->exportTree());
        _clipBoardLDIF.setOnAction(x->copyToClipboard());

        _searchResultcontextMenu.getItems().add(_deleteEntry);
        _searchResultcontextMenu.getItems().add(_replace_value);
        _searchResultcontextMenu.getItems().add(_exportEntry);
        _searchResultcontextMenu.getItems().add(_clipBoardLDIF);
        _searchResultcontextMenu.getItems().forEach(x->x.setStyle("-fx-font: 10px \"Segoe UI\";  -fx-font-weight:bold;"));
    }

    private void copyToClipboard()
    {
        TreeItem<SearchEntry> selected = _searchTree.getSelectionModel().getSelectedItem();
        if(selected == null || selected.getValue() == null) return;
        String[] ldif = selected.getValue().getEntry().toLDIF(1000);
        StringBuilder stringBuilder = new StringBuilder();
        for(String s : ldif)
        {
            stringBuilder.append(s);
            stringBuilder.append(System.lineSeparator());
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                new StringSelection(stringBuilder.toString()), null
        );
    }

    private void exportTree()
    {
        if(_main._ctManager._startSearchController .is_ldapExplorerTargetAction())
        {

            _main._ctManager._exportWindowController.showExportWindow(_main._ctManager._ldapTargetExploreController.get_currentConnection(),
                    _searchTree.getSelectionModel().getSelectedItem());
        }
        else
        {
            _main._ctManager._exportWindowController.showExportWindow(_main._ctManager._ldapSourceExploreCtrl.get_currentConnection(),
                    _searchTree.getSelectionModel().getSelectedItem());
        }
    }

    private void deleteSelectedEntries()
    {
        if(!GuiHelper.confirm("Delete operation","Are you sure you want to delete all selected entries?",
                "Only \n (1) selected entries and \n  (2) which corespond to your search and \n (3) and are leafs \n  will be deleted")) return;

        ObservableList<TreeItem<SearchEntry>> selectedItems = _searchTree.getSelectionModel().getSelectedItems();
        if(selectedItems == null) {GuiHelper.INFO("Selection","Nothing selected"); return;}
        List<TreeItem<SearchEntry>> listToBeDeleted = new ArrayList<>();
        for(TreeItem<SearchEntry> item: selectedItems)
        {
            if(item.getValue().ValueFound.get() && !item.getValue().getChildrenFound().getValue())
            {
               listToBeDeleted.add(item);
            }
        }
        if(listToBeDeleted.isEmpty())
        {
            GuiHelper.INFO("Delete entries","Found no entry which can be deleted, only nodes with children?");
            return;
        }
        StringBuilder stringBuilder = new StringBuilder();
        listToBeDeleted.stream().forEach(x->{stringBuilder.append(x.getValue().getDn()); stringBuilder.append("\n");});
        if(!GuiHelper.confirm("Delete entries","These entries shall be deleted, confirm again",stringBuilder.toString())) return;

        ExecutorService executor = Executors.newSingleThreadExecutor();
        _main._ctManager._progressWindowController.clearProgressWindow();
        _main._ctManager._progressWindowController._stage.show();

        executor.execute(()->
        {
            AtomicInteger deleted = new AtomicInteger(0);
            listToBeDeleted.stream().forEach(item->{
                try {
                    _main._ctManager._ldapSourceExploreCtrl.get_currentConnection().delete(item.getValue().getDn());
                }
                catch (Exception e)
                {
                    Platform.runLater(()->
                    {
                        GuiHelper.EXCEPTION("Delete entry error",e.getMessage(),e);
                        _main._ctManager._progressWindowController._stage.close();
                    });
                }
                if(deleted.get() % 20 == 0)
                Platform.runLater(()->_main._ctManager._progressWindowController.setProgress(
                        (double) deleted.incrementAndGet()/(double)listToBeDeleted.size(),"Deleted entries" + deleted.get()));
            });

        });
        _main._ctManager._progressWindowController._stage.close();
        for(TreeItem item: listToBeDeleted)
        {
            item.getParent().getChildren().remove(item);
        }
        _searchTree.refresh();
    }

    @FXML
    private void initialize() {

        initSearchResultContextMenu();
        _searchTree.getSelectionModel().selectedItemProperty().addListener((observable,oldValue,newValue)->{
            if(newValue== null) return;
            SearchEntry entry = newValue.getValue();
            _main._ctManager._entryView.setSearchMode(_searchTree.get_searchValue());
            _main._ctManager._entryView.updateValue(entry,_ldapExploreController.get_currentConnection());

        });
        _searchTree.addEventHandler(MouseEvent.MOUSE_RELEASED, e->{
            if (e.getButton()==MouseButton.SECONDARY) {
                TreeItem item = _searchTree.getSelectionModel().getSelectedItem();
                if (item != null) {
                    _searchResultcontextMenu.show(_main.get_primaryStage(), e.getScreenX(), e.getScreenY());
                }
            }
        });

        buttonReplaceAll = new Button();
        textFieldReplace = new TextField();

         buttonReplaceAll.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {

                String replace = null;
                if(textFieldReplace.getText() == null || textFieldReplace.getText().equalsIgnoreCase(""))
                {
                    replace = "";
                }
                else replace = textFieldReplace.getText();
                List<ModifyRequest> mods = null;
                if(_checkBoxCreateLdifReplace.isSelected())
                {
                    mods = new ArrayList<>();
                }
                try {
                    _searchTree.replaceAllStringOccurencies(_searchTree.getRoot(),
                            _main._ctManager._startSearchController ._textFieldSearchValue.getText(),
                            replace,_ldapExploreController.get_currentConnection(),mods);
                } catch (Exception e) {
                    GuiHelper.EXCEPTION("Exception occured during replacement",e.getMessage(),e);
                    e.printStackTrace();
                    return;
                }
                String fileName = null;
                if(mods != null && !mods.isEmpty())
                {
                    fileName = _main._ctManager._settingsController._textFieldTempDirectoryPath.getText() + "\\" + "replaceModifications.ldif";
                    try {
                        storeModificationsInFile(fileName,mods);
                    } catch (IOException e) {
                        GuiHelper.ERROR("Exception occured during mod request file save ",e.getMessage());
                        e.printStackTrace();
                        return;
                    }
                }
                if(mods == null)
                {
                    GuiHelper.INFO("Modification success","all replacements have been writen in the tree");
                }
                else
                {
                    GuiHelper.INFO("Modification success","all replacements have been writen in the file ->" + fileName);
                }
            }
        });
    }

    private void storeModificationsInFile(String fileName, List<ModifyRequest> mods) throws IOException {
        BufferedWriter bwTarget = null;
        FileWriter fwTarget = null;
        fwTarget = new FileWriter(fileName);
        bwTarget = new BufferedWriter(fwTarget);
        for(ModifyRequest mod: mods)
        {
            String[] tOut = mod.toLDIF();
            for(String s: tOut)
            {
                if (s.contains("changetype")) continue;
                if (s.contains("::"))
                {
                    int f = s.indexOf(":");
                    CharSequence value = s.subSequence(f+3, s.length());
                    CharSequence attrName = s.subSequence(0, f);
                    try {
                        byte[] dec = com.unboundid.util.Base64.decode(value.toString());
                        String decoded = new String(dec, Charset.forName("UTF-8"));
                        byte[] message2 = decoded.getBytes("ISO-8859-1");
                        String output = attrName +":"+decoded;
                        bwTarget.append(output);
                        bwTarget.append("\r\n");
                    } catch (ParseException e) {
                    }
                }
                else
                {
                    bwTarget.append(s);
                    bwTarget.append("\r\n");
                }
            }

        }
        bwTarget.close();
        fwTarget.close();
    }

    @Override
    public void setMain(Main main) {
        _main = main;
        _searchTree.set_config(_main._configuration);
        _main._ctManager._progressWindowController._buttonCancel.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                Platform.runLater(()-> _searchTree.set_breakOperation(true));
                _main._ctManager._progressWindowController._stage.hide();
                _main._ctManager._progressWindowController.clearProgressWindow();

            }
        });
    }

    @Override
    public void setWindow(Parent parent) {
        _searchResultWindow = (SplitPane) parent;
        _scene = new Scene(_searchResultWindow);
        _stage = new Stage();
        _stage.setScene(_scene);
        _searchResultWindow.getItems().add(0,_searchTree);
        _searchResultWindow.setDividerPositions(0.2);

    }
    @Override
    public void setOwner(Stage stage) {

    }
    @Override
    public void setProgress(double progress, String description) {
        Platform.runLater(()->_main._ctManager._progressWindowController.setProgress(progress,description));
    }

    @Override
    public void signalTaskDone(String taskName,String description, Exception e) {
        Platform.runLater(()->{
            if(taskName.equalsIgnoreCase("searchTree"))
            {
                if(e!=null)
                {
                    GuiHelper.EXCEPTION("Exception occured",e.getMessage(),e);
                }
                set_ldapExploreController();
                _main._ctManager._progressWindowController._stage.hide();
                _main._ctManager._progressWindowController.clearProgressWindow();
                _searchTree.expandTree();
                boolean valuesFound = _searchTree.getRoot().getValue().getChildrenFound().getValue();
                _deleteEntry.setVisible(valuesFound);
                _exportEntry.setVisible(valuesFound);
                _replace_value.setVisible(valuesFound);
                if(_searchTree.get_searchValue() == null)
                {
                    _replace_value.setVisible(false);
                }
                else
                {
                    _replace_value.setText("Replace value \"" + _searchTree.get_searchValue()+"\"");
                }
            }
            if(taskName.equalsIgnoreCase("replaceStringGetModifications"))
            {
                _main._ctManager._progressWindowController._stage.hide();
                _main._ctManager._progressWindowController.clearProgressWindow();
                if(_searchTree.get_replaceStringModifications().isEmpty())
                {
                    GuiHelper.INFO("Replace String value","Found no value to be replaced");
                    return;
                }
                _main._ctManager._modificationsViewController.updateValues(_searchTree.get_replaceStringModifications());
                _main._ctManager._modificationsViewController._stage.show();


            }
        });
    }

    @Override
    public void setProgress(String taskName, double progress) {

    }

}
