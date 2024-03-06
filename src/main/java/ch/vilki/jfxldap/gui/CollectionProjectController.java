package ch.vilki.jfxldap.gui;

import ch.vilki.jfxldap.backend.CollectionEntry;
import ch.vilki.jfxldap.backend.TableLdapEntry;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.util.HashMap;

public class CollectionProjectController {

    @FXML
    TextField _textFieldFileName;
    @FXML TextField _textFieldProjectName;

    @FXML  TableView _tableViewTargetDN;
    @FXML
    TableColumn<CollectionEntry,String> _tableColumnEntryDN;
    @FXML TableColumn<CollectionEntry,String> _tableColumnSubtree;
    @FXML TableColumn<CollectionEntry,String> _tableColumnFilter;
    @FXML TableColumn<CollectionEntry,String> _tableColumnEntryRDN;
    @FXML TableColumn<CollectionEntry,String> _tableColumnAttributes;
    @FXML TableColumn<CollectionEntry,String> _tableColumnAttributeAction;
    @FXML TableColumn<CollectionEntry,String> _tableColumnOverwrite;

    @FXML
    CheckBox _checkBoxSubtree;
    @FXML CheckBox _checkBoxDeleteTarget;
    @FXML CheckBox _checkBoxMergeEntry;

    @FXML   ObservableList<TableLdapEntry> _ldapEntryObservableList = FXCollections.observableArrayList();
    @FXML ObservableList<CollectionEntry> _dnEntryObservableList = FXCollections.observableArrayList();

    HashMap<String,TreeItem<CollectionEntry>> _searchCollectionMap = new HashMap<>();

    @FXML Button _buttonExportLDIF;
    @FXML Button _buttonImportLDIF;

    /***************** CONTEXT MENU ***********************/
    final ContextMenu _contextMenu = new ContextMenu();
    MenuItem deleteEntry = new MenuItem("Delete");
    MenuItem editEntry = new MenuItem("Edit");

    public CollectionEntry getEntryFromTable(String dn)
    {
        for(CollectionEntry entry: _dnEntryObservableList)
        {
            if(entry.getDn().equalsIgnoreCase(dn)) return entry;
        }
        return null;
    }
    @FXML
    private void initialize() {

        _tableViewTargetDN.setItems(_dnEntryObservableList);
        _tableColumnEntryDN.setCellValueFactory(new PropertyValueFactory<CollectionEntry,String>("Dn"));
        _tableColumnSubtree.setCellValueFactory(new PropertyValueFactory<>("Subtree"));
        _tableColumnFilter.setCellValueFactory(new PropertyValueFactory<CollectionEntry,String>("LdapFilter"));
        _tableColumnEntryRDN.setCellValueFactory(new PropertyValueFactory<CollectionEntry,String>("DisplayDN"));
        _tableColumnAttributes.setCellValueFactory(new PropertyValueFactory<CollectionEntry,String>("Attributes"));
        _tableColumnAttributeAction.setCellValueFactory(new PropertyValueFactory<CollectionEntry,String>("AttributesAction"));
        _tableColumnOverwrite.setCellValueFactory(new PropertyValueFactory<CollectionEntry,String>("OverwriteEntry"));
        _contextMenu.getItems().addAll(deleteEntry,editEntry);
    }

}
