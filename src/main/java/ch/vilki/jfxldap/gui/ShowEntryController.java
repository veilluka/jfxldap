package ch.vilki.jfxldap.gui;

import ch.vilki.jfxldap.Main;
import ch.vilki.jfxldap.backend.Connection;
import ch.vilki.jfxldap.backend.CustomEntry;
import ch.vilki.jfxldap.backend.Helper;
import ch.vilki.jfxldap.backend.LdapExplorerEvent;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.Modification;
import com.unboundid.ldap.sdk.ModificationType;
import com.unboundid.ldap.sdk.ModifyRequest;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

public class ShowEntryController implements ILoader {

    static Logger logger = LogManager.getLogger(ShowEntryController.class);
    @FXML TextField _textFieldFilterAttribute;
    @FXML ListView<Object> _listViewResult;

    @FXML
    ObservableList<Object> _observableListTextFlowFoundResult = FXCollections.observableArrayList();
    Map<TextFlow, String> _valuesTextFlowMap = new HashMap();

    Scene _scene;
    Stage _stage;
    VBox _showEntryWindow;

    private Main _main;
    private CustomEntry _selectedEntry = null;
    private boolean _ignoreCase = false;
    private boolean _deadLink = false;
    private String _searchValue;

    public Scene get_scene() {return _scene;}
    public void set_scene(Scene _scene) {this._scene = _scene;}
    public Stage get_stage() {return _stage;}

    /********************* CONTEXT MENU ***********************/
    final ContextMenu _attributeValuesContextMenu = new ContextMenu();
    MenuItem _copyAttribute = new MenuItem("copy");
    MenuItem _edit = new MenuItem("edit");



    TextField _editTextField = new TextField();
    String _editedAttribute = null;
    String _editedValueBefore = null;

    private static int _currentEditIndex = 0;

    MenuItem _entryMenuDeleteValue = new MenuItem("  DELETE  ", Icons.get_iconInstance().getIcon(Icons.ICON_NAME.CLOSE_FILE));
    Connection _currentConnection = null;

    public void setMain(Main main) {
        _main = main;
        _main._ctManager._ldapSourceExploreCtrl._treeView.addEventHandler(LdapExplorerEvent.ELEMENT_SELECTED, e -> {
            try {
                /*
                 clearResult();
            writeSearchResult();
            if (newValue != null && !newValue.equalsIgnoreCase("")) {
                filterResult(newValue);
            }
                 */
               writeEntry(e.treeEntry, _main._ctManager._ldapSourceExploreCtrl.get_currentConnection());
                if(!_textFieldFilterAttribute.getText().equalsIgnoreCase("")) filterResult(_textFieldFilterAttribute.getText());
            } catch (Exception exc) {
                GuiHelper.EXCEPTION("Connection error", exc.getMessage(), exc);
                logger.error(exc.getStackTrace());
                logger.error(e);
            }
        });
    }

    @Override
    public void setWindow(Parent parent) {
        _showEntryWindow = (VBox) parent;
        _scene = new Scene(_showEntryWindow);
        _stage = new Stage();
        _stage.setScene(_scene);
        _showEntryWindow.getChildren().clear();
        _showEntryWindow.getChildren().add(0,_main._ctManager._entryView);
    }

    @Override
    public void setOwner(Stage stage) {
    }

    public VBox get_window() {
        return _showEntryWindow;
    }

    private void initEntryDialog(String text, String attName) {
        if (_observableListTextFlowFoundResult != null &&
                !_observableListTextFlowFoundResult.isEmpty() &&
                _observableListTextFlowFoundResult.get(_currentEditIndex) != null) {
            _observableListTextFlowFoundResult.remove(_currentEditIndex);
            _editTextField.setText(text);
            _observableListTextFlowFoundResult.add(_currentEditIndex, _editTextField);
            _listViewResult.getSelectionModel().select(_currentEditIndex);
            _listViewResult.getFocusModel().focus(_currentEditIndex);
            _editedAttribute = attName;
            _editedValueBefore = text;
        }
    }

    private void checkAttributeModification(String currentValue) {
        if (_editedValueBefore.equals(currentValue)) return;
        List<Modification> modifications = new ArrayList<>();
        Attribute entryAtt = _selectedEntry.getEntry().getAttribute(_editedAttribute);
        if (currentValue == null || currentValue.equalsIgnoreCase("")) {
            if (entryAtt.getValues().length == 1) {
                Modification modification = new Modification(ModificationType.DELETE, _editedAttribute);
                modifications.add(modification);
            }
        } else {
            Set<String> newAtts = Arrays.stream(entryAtt.getValues()).collect(Collectors.toSet());
            newAtts.remove(_editedValueBefore);
            newAtts.add(currentValue);
            Modification modification = new Modification(ModificationType.REPLACE, _editedAttribute, newAtts.toArray(new String[newAtts.size()]));
            modifications.add(modification);
        }
        List<ModifyRequest> modifyRequests = new ArrayList<>();
        ModifyRequest modifyRequest = new ModifyRequest(_selectedEntry.getDn(), modifications);
        modifyRequests.add(modifyRequest);
        if (!GuiHelper.confirm("Confirm attribute modification", "Modify attribute?", modifyRequest.toLDIFString())) ;

        try {
            _currentConnection.modify(modifyRequests);
            _selectedEntry.readAllAttributes(_currentConnection);
            // writeSearchResult();
        } catch (Exception e) {
            GuiHelper.EXCEPTION("Modify failed", e.getMessage(), e);
        }
    }

    public ShowEntryController() {
    }

    @FXML
    private void initialize() {
        _editTextField.setEditable(true);
        _editTextField.setOnAction(e -> {
            TextFlow textFlow = new TextFlow();
            textFlow.getChildren().add(new Text(_editTextField.getText()));
            _observableListTextFlowFoundResult.remove(_currentEditIndex);
            _observableListTextFlowFoundResult.add(_currentEditIndex, textFlow);
            _listViewResult.getSelectionModel().select(_currentEditIndex);

        });
        _listViewResult.setItems(_observableListTextFlowFoundResult);
        _listViewResult.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<Object>() {
            @Override
            public void changed(ObservableValue<? extends Object> observable, Object oldValue, Object newValue) {
                if (oldValue instanceof TextField) {

                    TextField newAttValue = (TextField) oldValue;
                    checkAttributeModification(newAttValue.getText());
                    try {
                        writeSearchResult();
                    } catch (Exception e) {
                        GuiHelper.EXCEPTION("Exception occured", e.getMessage(), e);
                        logger.error(e);
                    }
                    _currentEditIndex = 0;
                }
            }
        });
        _listViewResult.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (oldValue && !newValue && !_editTextField.isFocused()) {
                if (_observableListTextFlowFoundResult.contains(_editTextField)) {
                    checkAttributeModification(_editTextField.getText());

                    int index = _listViewResult.getItems().indexOf(_editTextField);
                    Platform.runLater(() -> {
                        _listViewResult.getItems().remove(_editTextField);
                        TextFlow textFlow = new TextFlow();
                        textFlow.getChildren().add(new Text(_editTextField.getText()));
                        _listViewResult.getItems().add(_currentEditIndex, textFlow);
                        _currentEditIndex = 0;
                    });
                }
            }
            _attributeValuesContextMenu.hide();
        });
        _listViewResult.setOnMouseClicked(mouseEvent -> {
            if (mouseEvent.getButton().equals(MouseButton.PRIMARY)) {
                if (mouseEvent.getClickCount() == 2) {
                    _attributeValuesContextMenu.hide();
                    TextFlow textFlow = (TextFlow) _listViewResult.getSelectionModel().getSelectedItem();
                    if (textFlow == null) return;
                    _currentEditIndex = _observableListTextFlowFoundResult.indexOf(textFlow);
                    String attName = _valuesTextFlowMap.get(textFlow);
                    StringBuilder builder = new StringBuilder();
                    textFlow.getChildren().stream().forEach(x -> {
                        if (x instanceof Text) {
                            Text t = (Text) x;
                            if (!t.getText().toLowerCase().contains(attName.toLowerCase()))
                                builder.append(t.getText());
                        }
                    });
                    initEntryDialog(builder.toString(), attName);
                }
            } else if (mouseEvent.getButton().equals(MouseButton.SECONDARY)) {
                ObservableList<Object> list = _listViewResult.getSelectionModel().getSelectedItems();
                if (list == null || list.isEmpty()) return;
                for (Object o : list) {
                    if (o instanceof TextFlow) {
                        TextFlow textFlow = (TextFlow) o;
                        _attributeValuesContextMenu.show(textFlow, Side.TOP, 10, 20);
                        return;
                    }
                }
            }
        });
        _listViewResult.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        _attributeValuesContextMenu.getItems().add(_entryMenuDeleteValue);
        _entryMenuDeleteValue.setOnAction(x -> {
            ObservableList<Object> selected = _listViewResult.getSelectionModel().getSelectedItems();
            if (selected == null) return;
            Multimap<String, String> delete = ArrayListMultimap.create();
            List<Modification> modifications = new ArrayList<>();
            for (Object o : selected) {
                if (o instanceof TextFlow) {
                    TextFlow textFlow = (TextFlow) o;
                    String attName = _valuesTextFlowMap.get(textFlow);
                    StringBuilder builder = new StringBuilder();
                    textFlow.getChildren().stream().forEach(y -> {
                        if (y instanceof Text) {
                            Text t = (Text) y;
                            if (!t.getText().toLowerCase().contains(attName.toLowerCase()))
                                builder.append(t.getText());
                        }
                    });
                    delete.put(attName, builder.toString());
                }
            }
            for (String atName : delete.keySet()) {
                Collection<String> deleteValues = delete.get(atName);
                Attribute entryAtt = _selectedEntry.getEntry().getAttribute(atName);
                if (entryAtt.getValues().length == deleteValues.size()) {
                    Modification modification = new Modification(ModificationType.DELETE, atName);
                    modifications.add(modification);
                } else {
                    Set<String> newAtts = Arrays.stream(entryAtt.getValues()).collect(Collectors.toSet());
                    newAtts.removeAll(deleteValues);
                    Modification modification = new Modification(ModificationType.REPLACE, atName, newAtts.toArray(new String[newAtts.size()]));
                    modifications.add(modification);
                }
            }
            List<ModifyRequest> modifyRequests = new ArrayList<>();
            ModifyRequest modifyRequest = new ModifyRequest(_selectedEntry.getDn(), modifications);
            modifyRequests.add(modifyRequest);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("DELETE" + System.lineSeparator());
            for (String d : delete.keySet()) {
                stringBuilder.append(d + "->" + delete.get(d) + System.lineSeparator());
            }
            if (!GuiHelper.confirm("Delete attribute value", "Confirm deletion", stringBuilder.toString())) return;
            try {
                _currentConnection.modify(modifyRequests);
                _selectedEntry.readAllAttributes(_currentConnection);
                writeSearchResult();
            } catch (Exception e) {
                GuiHelper.EXCEPTION("Modify failed", e.getMessage(), e);
            }

        });
        _textFieldFilterAttribute.textProperty().addListener((observable, oldValue, newValue) -> {
            clearResult();
            writeSearchResult();
            if (newValue != null && !newValue.equalsIgnoreCase("")) {
                filterResult(newValue);
            }
        });
    }

    private void filterResult(String value) {
        Map<TextFlow, String> copy = new HashMap<>();
        copy.putAll(_valuesTextFlowMap);
        clearResult();
        for (TextFlow key : copy.keySet()) {
            boolean found = false;
            for (Node n : key.getChildren()) {
                if (n instanceof Text) {
                    Text t = (Text) n;
                    if (t.getText().toLowerCase().contains(value.toLowerCase())) found = true;
                }
            }
            if (found) {
                String attName = copy.get(key);
                addResult(key, attName);
            }
        }
    }

    private void writeSearchResult() {
        writeSearchResult(_selectedEntry, _ignoreCase, _deadLink, _searchValue, _currentConnection);
    }

    private void writeSearchResult(String search) {
        writeSearchResult(_selectedEntry, _ignoreCase, _deadLink, search, _currentConnection);
    }

    private void addResult(TextFlow textFlow, String attributeName) {
        Platform.runLater(() -> _observableListTextFlowFoundResult.add(textFlow));
        _valuesTextFlowMap.put(textFlow, attributeName);
    }

    private void clearResult() {
        Platform.runLater(() -> _observableListTextFlowFoundResult.clear());
        _valuesTextFlowMap.clear();
    }

    public void writeEntry(TreeItem<CustomEntry> customEntry, Connection connection) {
        if (!customEntry.getValue().is_dummy()) customEntry.getValue().readAllAttributes(connection);
        writeSearchResult(customEntry.getValue(), false, false, null, connection);
    }

    public void writeSearchResult(CustomEntry entry, boolean ignoreCase, boolean deadLink,
                                  String searchValue, Connection connection) {
        _main._ctManager._entryView.setSearchMode(searchValue);
        _main._ctManager._entryView.updateValue(entry,_main._ctManager._ldapSourceExploreCtrl.get_currentConnection());
         clearResult();
        _ignoreCase = ignoreCase;
        _deadLink = deadLink;
        _searchValue = searchValue;
        _selectedEntry = entry;
        _currentConnection = connection;

        String same = "-fx-fill: #0a3357";
        String different = "-fx-fill: #c01b1b;-fx-font-weight:bold;";
        if (entry != null) {
            TextFlow dnTextFlow = new TextFlow();
            Text dnName = new Text("DN" + ": ");
            dnName.setStyle("-fx-font-weight:bold");
            dnTextFlow.getChildren().add(dnName);
            Text dnValue = new Text();
            dnValue.setText(entry.getDn());
            dnTextFlow.getChildren().add(dnValue);
            addResult(dnTextFlow, "DN");
            if (entry == null || entry.getEntry() == null || entry.getEntry().getAttributes() == null) return;
            try {
                List<String> atts =  entry.getEntry().getAttributes().stream().map(x -> x.getName()).sorted(String::compareTo).collect(Collectors.toList());
                    for (String atName : atts) {
                    Attribute att = entry.getEntry().getAttribute(atName);
                    if (deadLink) {
                        for (String v : att.getValues()) {
                            TextFlow textFlow = new TextFlow();
                            Text t1 = new Text();
                            t1.setText(v);
                            textFlow.getChildren().add(t1);
                            addResult(textFlow, atName);
                        }
                        return;
                    }
                    if (att != null && att.getValues() != null) {
                        String values[] = att.getValues();
                        for (String v : values) {
                            TextFlow textFlowAttribute = new TextFlow();

                            Text attName = new Text(atName + ": " );
                            attName.setStyle("-fx-font-weight:bold");
                            textFlowAttribute.getChildren().add(attName);
                            String srcValue = null;
                            String tgtValue = null;
                            if (ignoreCase && searchValue != null) {
                                tgtValue = searchValue.toLowerCase();
                                srcValue = v.toLowerCase();
                            } else {
                                tgtValue = searchValue;
                                srcValue = v;
                            }
                            if (srcValue.contains("<?xml version=")) {
                                try {
                                    Helper helper = new Helper();
                                    srcValue = helper.format(srcValue);
                                    //srcValue = Helper.getPrettyXml(srcValue);
                                } catch (Exception e) {
                                    logger.error("Error converting to xml->" + srcValue);
                                }
                            }
                            if (tgtValue == null || tgtValue.equalsIgnoreCase("") ||
                                    !srcValue.toLowerCase().contains(tgtValue.toLowerCase())) {
                                Text t1 = new Text();
                                t1.setText(srcValue);
                                textFlowAttribute.getChildren().add(t1);
                                addResult(textFlowAttribute, atName);
                                continue;
                            }
                            List<Integer> found = Helper.findAllPositionsInString(srcValue, tgtValue);
                            int i = 0;
                            int marker = 0;
                            while (i < found.size()) {
                                int start = found.get(i);
                                i++;
                                int end = found.get(i);
                                i++;
                                Text t1 = new Text();
                                t1.setText(srcValue.substring(marker, start));
                                t1.setStyle(same);
                                marker = end;
                                textFlowAttribute.getChildren().add(t1);

                                Text t2 = new Text();
                                t2.setText(srcValue.substring(start, end));
                                t2.setStyle(different);

                                textFlowAttribute.getChildren().add(t2);
                                if (i >= found.size() && marker < srcValue.length()) {
                                    Text t3 = new Text();
                                    t3.setText(srcValue.substring(marker, srcValue.length()));
                                    t3.setStyle(same);
                                    textFlowAttribute.getChildren().add(t3);
                                }
                            }
                            if (textFlowAttribute.getChildren().size() > 1) {
                                addResult(textFlowAttribute, atName);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Error showing entry", e);
            }
        }
    }

}
