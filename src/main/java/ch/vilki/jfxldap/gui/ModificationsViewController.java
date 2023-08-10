package ch.vilki.jfxldap.gui;


import ch.vilki.jfxldap.Main;
import ch.vilki.jfxldap.backend.SearchEntry;
import com.unboundid.ldap.sdk.Modification;
import com.unboundid.ldap.sdk.ModifyRequest;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ModificationsViewController implements ILoader {

    static Logger logger = LogManager.getLogger(ModificationsViewController.class);

    @FXML
    TreeTableView<ModificationEntry> _treeTableViewEntry;
    @FXML TreeTableColumn<ModificationEntry,String> dnColumn;
    @FXML TreeTableColumn<ModificationEntry,String> attributeNameColumn;
    @FXML TreeTableColumn<ModificationEntry,String> oldValueColumn;
    @FXML TreeTableColumn<ModificationEntry,String> newValueColumn;

    @FXML Button _buttonExportLDIF;
    @FXML Button _buttonModify;
    @FXML Button _buttonDelete;
    @FXML Button _buttonExit;

    private Main _main;


    Scene _scene;
    Stage _stage;
    public VBox get_window() {
        return _entryWindow;
    }

    VBox _entryWindow;

    Map<TreeItem<SearchEntry>, List<ModifyRequest>> _modifications = null;

    @FXML
    private void initialize() {

        dnColumn.setCellValueFactory(param -> param.getValue().getValue().DN);
        attributeNameColumn.setCellValueFactory(param -> param.getValue().getValue().attributeName);
        oldValueColumn.setCellValueFactory(param -> param.getValue().getValue().oldValue);
        newValueColumn.setCellValueFactory(param -> param.getValue().getValue().newValue);

        TreeItem<ModificationEntry> rootItem = new TreeItem<>(new ModificationEntry("","","",""));
        _treeTableViewEntry.setRoot(rootItem);
        _treeTableViewEntry.setShowRoot(false);
        _buttonExit.setOnAction(x->_stage.close());

        _treeTableViewEntry.getSelectionModel().selectedItemProperty().addListener(x->{
            if(_treeTableViewEntry.getSelectionModel().getSelectedItem() != null) _buttonDelete.setDisable(false);
        });
        _buttonDelete.setOnAction(x->deleteEntryFromTable());
        _buttonExportLDIF.setOnAction(x->exportLDIF());
        _buttonModify.setOnAction(x->modifyEntries());
      }


    @Override
    public void setMain(Main main) {
        _main = main;
    }

    @Override
    public void setWindow(Parent parent) {
        _entryWindow = (VBox) parent;
        _scene = new Scene(_entryWindow);
        _stage = new Stage();
        _stage.setScene(_scene);
        _stage.setOnShowing(new EventHandler<WindowEvent>() {
            @Override
            public void handle(WindowEvent event) {
                _buttonExit.requestFocus();
                _buttonDelete.setDisable(true);
            }
        });
    }

    @Override
    public void setOwner(Stage stage) {
        _stage.initOwner(stage);
    }

    class ModificationEntry {

        public StringProperty DN = new SimpleStringProperty();
        public StringProperty attributeName = new SimpleStringProperty();
        public StringProperty oldValue = new SimpleStringProperty();
        public StringProperty newValue = new SimpleStringProperty();
        public ModificationEntry(String dn, String attname, String oldVal, String newVal)
        {
            DN.set(dn);
            attributeName.set(attname);
            oldValue.set(oldVal);
            newValue.set(newVal);

        }

        public void setAll(String dn, String attname, String oldVal, String newVal)
        {
            DN.set(dn);
            attributeName.set(attname);
            oldValue.set(oldVal);
            newValue.set(newVal);
        }

    }

    public void updateValues(Map<TreeItem<SearchEntry>, List<ModifyRequest>> modifications)
    {
        _treeTableViewEntry.getRoot().getChildren().clear();
        _modifications = modifications;
        if(modifications == null)
        {
            return;
        }
        for(TreeItem<SearchEntry> mod: modifications.keySet())
        {
            List<ModifyRequest> modifyRequests = modifications.get(mod);
            String dn = mod.getValue().getDn();
            TreeItem<ModificationEntry> entryNode = new TreeItem<>(new ModificationEntry(dn,"","",""));
            entryNode.setExpanded(true);
            _treeTableViewEntry.getRoot().getChildren().add(entryNode);
            for(ModifyRequest modifyRequest: modifyRequests)
            {
                int pos = 0;
                for(Modification modification: modifyRequest.getModifications())
                {
                    boolean oneLine = false;
                    if(modifyRequest.getModifications().size() == 1) oneLine = true;
                    String attName = modification.getAttributeName();
                    List<String> oldValues = new ArrayList<>();
                    List<String> newValues = new ArrayList<>();
                    for(String val: mod.getValue().getEntry().getAttribute(attName).getValues())
                    {
                        oldValues.add(val);
                    }
                    for(String val: modification.getValues())
                    {
                        newValues.add(val);
                    }
                    if(oldValues.size() > newValues.size())
                    {

                        for(int i=0; i< oldValues.size(); i++)
                        {
                            if(i<newValues.size())
                            {
                                if(!oneLine)
                                {
                                    TreeItem<ModificationEntry> attributeNode = new TreeItem<>(new ModificationEntry("[" + pos + "]",attName,oldValues.get(i),newValues.get(i)));
                                    entryNode.getChildren().add(attributeNode);
                                }
                                else
                                {
                                    entryNode.getValue().setAll(dn,attName,oldValues.get(i),newValues.get(i));
                                }


                            }
                            else
                            {
                                if(!oneLine)
                                {
                                    TreeItem<ModificationEntry> attributeNode = new TreeItem<>(new ModificationEntry("[" + pos + "]",attName,oldValues.get(i),""));
                                    entryNode.getChildren().add(attributeNode);
                                }
                                else {
                                    entryNode.getValue().setAll(dn,attName,oldValues.get(i),newValues.get(i));
                                }

                            }
                        }
                    }
                    else if(newValues.size() > oldValues.size())
                    {
                        for(int i=0; i< newValues.size(); i++)
                        {
                            if(i<oldValues.size())
                            {
                                if(!oneLine)
                                {
                                    TreeItem<ModificationEntry> attributeNode = new TreeItem<>(new ModificationEntry("[" + pos + "]",attName,oldValues.get(i),newValues.get(i)));
                                    entryNode.getChildren().add(attributeNode);
                                }
                                else
                                {
                                    entryNode.getValue().setAll(dn,attName,oldValues.get(i),newValues.get(i));
                                }

                            }
                            else
                            {
                                if(!oneLine) {
                                    TreeItem<ModificationEntry> attributeNode = new TreeItem<>(new ModificationEntry("[" + pos + "]", attName, "", newValues.get(i)));
                                    entryNode.getChildren().add(attributeNode);
                                }
                                else {
                                    entryNode.getValue().setAll(dn,attName,oldValues.get(i),newValues.get(i));
                                }
                            }
                        }
                    }
                    else if (newValues.size() == oldValues.size())
                    {
                        for(int i=0; i< newValues.size(); i++)
                        {
                            if(!oneLine) {
                                TreeItem<ModificationEntry> attributeNode = new TreeItem<>(new ModificationEntry("[" + pos + "]",attName,oldValues.get(i),newValues.get(i)));
                                entryNode.getChildren().add(attributeNode);
                            }
                            else {
                                entryNode.getValue().setAll(dn,attName,oldValues.get(i),newValues.get(i));
                            }

                        }

                    }
                    pos++;
                }
            }
        }
        _treeTableViewEntry.setShowRoot(false);

    }

    private void deleteEntryFromTable()
    {
        TreeItem<ModificationEntry> selectedItem = _treeTableViewEntry.getSelectionModel().getSelectedItem();
        if(selectedItem == null) return;
        String dn = null;
        if(!selectedItem.getParent().equals(_treeTableViewEntry.getRoot()))
        {
            dn = selectedItem.getParent().getValue().DN.get();
        }
        else dn = selectedItem.getValue().DN.get();
        TreeItem<SearchEntry> deleteKey = null;
        for(TreeItem<SearchEntry> key: _modifications.keySet())
        {
            List<ModifyRequest> val = _modifications.get(key);
            ModifyRequest deleteModifyRequest = null;
            for(ModifyRequest modifyRequest : val)
            {
                if(modifyRequest.getDN().equalsIgnoreCase(dn))
                {
                    List<Modification> newModification = new ArrayList<>();
                    for(Modification modification: modifyRequest.getModifications())
                    {
                        boolean delModification = false;
                        if(!modification.getAttributeName().equalsIgnoreCase(selectedItem.getValue().attributeName.get()))
                        {
                            newModification.add(modification);
                        }
                        else
                        {
                            List<String> newModValues = new ArrayList<>();
                            for(String modValue: modification.getValues())
                            {
                                if(!modValue.equalsIgnoreCase(selectedItem.getValue().newValue.get()))
                                {
                                    newModValues.add(modValue);
                                }
                            }
                            if(!newModValues.isEmpty())
                            {
                                for(String s: newModValues)
                                {
                                    Modification modification1 = new Modification(modification.getModificationType(),modification.getAttributeName(),s);
                                    newModification.add(modification1);
                                }

                            }
                        }
                    }
                    if(newModification.isEmpty())
                    {
                        deleteModifyRequest = modifyRequest;
                    }
                    else
                    {
                        modifyRequest.setModifications(newModification);
                    }
                }

            }
            if(deleteModifyRequest != null) val.remove(deleteModifyRequest);
            if(val.isEmpty()) deleteKey = key;
        }
        if(deleteKey != null)
        {
            _treeTableViewEntry.getRoot().getChildren().remove(selectedItem);
            _modifications.remove(deleteKey);
        }
        updateValues(_modifications);
    }

    private void exportLDIF()
    {
        FileChooser.ExtensionFilter filter = new FileChooser.ExtensionFilter("LDIF files (*.ldif)","*.ldif");
        File file = GuiHelper.selectFile(_main,filter,"Enter file name",GuiHelper.FILE_OPTIONS.SAVE_AS);
        if(file== null) return;
        try {
            FileWriter fileWriter = new FileWriter(file);
            for(TreeItem<SearchEntry> key: _modifications.keySet())
            {
                List<ModifyRequest> requests  = _modifications.get(key);
                for(ModifyRequest modifyRequest: requests)
                {
                    String[] ldif = modifyRequest.toLDIF();
                    for(int i=0; i< ldif.length; i++)
                    {
                        if(ldif[i].equalsIgnoreCase("-"))
                        {
                            if(i!= ldif.length-1)
                            {
                                fileWriter.write(System.lineSeparator());
                                fileWriter.write(ldif[0]);
                                fileWriter.write(System.lineSeparator());
                            }
                        }
                        else
                        {
                            fileWriter.write(ldif[i]);
                            fileWriter.write(System.lineSeparator());
                        }

                    }
                    fileWriter.write(System.lineSeparator());

                }

            }
            fileWriter.close();
        } catch (IOException e) {
            GuiHelper.EXCEPTION("Error saving file",e.getMessage(),e);
        }
        GuiHelper.INFO("LDIF File","EXPORT DONE");

    }

    private void modifyEntries(){
        try {
            _main._ctManager._searchResultController._searchTree.doReplace();
        }
        catch (Exception e)
        {

            GuiHelper.EXCEPTION("Error during modification",e.getMessage(),e);
        }
        _stage.close();
        GuiHelper.INFO("Operation success","Entries modified");

    }


}
