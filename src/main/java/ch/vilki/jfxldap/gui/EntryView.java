package ch.vilki.jfxldap.gui;

import ch.vilki.jfxldap.Main;
import ch.vilki.jfxldap.backend.Connection;
import ch.vilki.jfxldap.backend.CustomEntry;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.Modification;
import com.unboundid.ldap.sdk.ModificationType;
import com.unboundid.ldap.sdk.ModifyRequest;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTreeTableCell;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;


public class EntryView extends TreeTableView<EntryView.EntryValue> {

    static String FOUND_VALUE_STYLE = "-fx-font-weight:bold; -fx-text-fill: red; ";
    static Logger logger = LogManager.getLogger(EntryView.class);
    TreeTableColumn<EntryValue, String> nameColumn;
    TreeTableColumn<EntryValue, String> valueColumn;
    Connection _currentConnection = null;
    CustomEntry _currentEntry = null;
    static String _searchValue = null;

    Main _mainApp;

    final ContextMenu _attributesContextMenu = new ContextMenu();
    MenuItem _deleteValue = new MenuItem("Delete Value");
    MenuItem _addValue = new MenuItem("Add Value");
    MenuItem _addAttribute = new MenuItem("Add Attribute");
    MenuItem _deleteAttribute = new MenuItem("Delete Attribute");

    public void setSearchMode(String searchValue) {
        if (searchValue != null && !searchValue.equalsIgnoreCase("")) {
            _searchValue = searchValue;
            valueColumn.setCellFactory(column -> {
                return new TreeTableCell<EntryValue, String>() {
                    @Override
                    protected void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item == null || empty) {
                            setGraphic(null);
                            setText(null);
                            setStyle("");
                        } else {
                            setGraphic(null);
                            if (!searchValue.isEmpty() && item.toLowerCase().contains(searchValue.toLowerCase())) {
                                Double rowHeight = this.getTreeTableRow().getHeight();
                                TextFlow textFlow = (buildTextFlow(item, searchValue));
                                textFlow.setPrefHeight(Double.MIN_VALUE);
                                //textFlow.setPrefHeight(30);
                                setGraphic(textFlow);
                                setHeight(rowHeight);
                                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                            } else {
                                setText(item);
                                setTextFill(Color.BLACK);
                                setStyle("");
                                setContentDisplay(ContentDisplay.TEXT_ONLY);
                            }
                        }
                    }
                };
            });
        } else {
            _searchValue = null;
            valueColumn.setCellFactory(TextFieldTreeTableCell.forTreeTableColumn());
        }
    }

    public EntryView(Main main) {
        setEditable(true);
        _mainApp = main;
        nameColumn = new TreeTableColumn<>("Attribute");
        valueColumn = new TreeTableColumn<>("Value");
        getColumns().addAll(nameColumn, valueColumn);
        nameColumn.setCellValueFactory(param -> param.getValue().getValue().attributeName);
        valueColumn.setCellValueFactory(param -> param.getValue().getValue().attributeValue);
        valueColumn.setEditable(true);
        valueColumn.setOnEditStart(x -> {
            if (_searchValue != null) {
                valueColumn.setCellFactory(TextFieldTreeTableCell.forTreeTableColumn());
            }
        });
        valueColumn.setOnEditCancel(x -> {
            if (_searchValue != null) {
                setSearchMode(_searchValue);
            }
        });

        _addAttribute.setDisable(true);
        _addValue.setDisable(true);

        valueColumn.setOnEditCommit(x -> {
            if (x.getOldValue().equals(x.getNewValue())) return;
            if (_currentConnection == null) return;
            if (_currentEntry == null) return;
            if (_currentEntry.getEntry() == null) return;
            if (_currentConnection.getOperationalAttributes().contains(x.getRowValue().getValue().attributeName.get())) {
                GuiHelper.ERROR("Edit error", "Operational attribute, can not modify");
                x.getRowValue().getValue().attributeValue.setValue(x.getOldValue());
                refresh();
                return;
            }
            String attName = x.getRowValue().getValue().attributeName.get();
            Attribute entryAtt = _currentEntry.getEntry().getAttribute(attName);
            List<Modification> modifications = new ArrayList<>();
            if (x.getNewValue() == null || x.getNewValue().equalsIgnoreCase("")) {
                if (entryAtt.getValues().length == 1) {
                    Modification modification = new Modification(ModificationType.DELETE, attName);
                    modifications.add(modification);
                }
            } else {
                Set<String> newAtts = Arrays.stream(entryAtt.getValues()).collect(Collectors.toSet());
                newAtts.remove(x.getOldValue());
                newAtts.add(x.getNewValue());
                Modification modification =
                        new Modification(ModificationType.REPLACE, attName, newAtts.toArray(new String[newAtts.size()]));
                modifications.add(modification);
            }
            List<ModifyRequest> modifyRequests = new ArrayList<>();
            ModifyRequest modifyRequest = new ModifyRequest(_currentEntry.getDn(), modifications);
            modifyRequests.add(modifyRequest);
            if (!GuiHelper.confirm("Confirm attribute modification", "Modify attribute?", modifyRequest.toLDIFString())) {
                x.getRowValue().getValue().attributeValue.setValue(x.getOldValue());
                refresh();
                return;
            }
            try {
                _currentConnection.modify(modifyRequests);
                _currentEntry.readAllAttributes(_currentConnection);
                updateValue(_currentEntry, _currentConnection);
            } catch (Exception e) {
                GuiHelper.EXCEPTION("Modify failed", e.getMessage(), e);
            }
        });

        _deleteAttribute.setOnAction(x -> {
            TreeItem<EntryValue> selected = getSelectionModel().getSelectedItem();
            if (_currentConnection == null) return;
            if (_currentEntry == null) return;
            String attName = selected.getValue().removeAttributeSize();
            if (_currentConnection.getOperationalAttributes().contains(attName)) {
                GuiHelper.ERROR("Can not delete", "Operational attribute, can not delete");
                return;
            }
            Modification modification = new Modification(ModificationType.DELETE, attName);
            List<Modification> modifications = new ArrayList<>();
            modifications.add(modification);
            List<ModifyRequest> modifyRequests = new ArrayList<>();
            ModifyRequest modifyRequest = new ModifyRequest(_currentEntry.getDn(), modifications);
            modifyRequests.add(modifyRequest);
            if (!GuiHelper.confirm("Confirm attribute deletion ", "DELETE attribute?", modifyRequest.toLDIFString())) {
                return;
            }
            try {
                _currentConnection.modify(modifyRequests);
                _currentEntry.readAllAttributes(_currentConnection);
                updateValue(_currentEntry, _currentConnection);
            } catch (Exception e) {
                GuiHelper.EXCEPTION("Modify failed", e.getMessage(), e);
            }
        });

        _deleteValue.setOnAction(x -> {
            TreeItem<EntryValue> selected = getSelectionModel().getSelectedItem();
            if (_currentConnection == null) return;
            if (_currentEntry == null) return;
            String value = selected.getValue().attributeValue.get();
            String attName = selected.getValue().removeAttributeSize();
            logger.info("Attribute name->{}  and value -->{} ", attName, value);
            Modification modification = new Modification(ModificationType.DELETE, attName, value);
            List<Modification> modifications = new ArrayList<>();
            modifications.add(modification);
            List<ModifyRequest> modifyRequests = new ArrayList<>();
            ModifyRequest modifyRequest = new ModifyRequest(_currentEntry.getDn(), modifications);
            modifyRequests.add(modifyRequest);
            if (!GuiHelper.confirm("Confirm value deletion ", "DELETE VALUE?", modifyRequest.toLDIFString())) {
                return;
            }
            try {
                _currentConnection.modify(modifyRequests);
                _currentEntry.readAllAttributes(_currentConnection);
                updateValue(_currentEntry, _currentConnection);
            } catch (Exception e) {
                GuiHelper.EXCEPTION("Modify failed", e.getMessage(), e);
            }
        });

        nameColumn.setCellFactory(column -> {
            return new TreeTableCell<EntryValue, String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    EntryValue entryValue = getTreeTableRow().getItem();
                    if (item == null || empty) {
                        setGraphic(null);
                        setText(null);
                        setStyle("");
                    } else {
                        setGraphic(null);
                        if (entryValue != null && entryValue.get_styleName() != null)
                            setStyle(entryValue.get_styleName());
                        setText(item);
                        setContentDisplay(ContentDisplay.TEXT_ONLY);
                    }
                }
            };
        });

        VBox.setVgrow(this, Priority.ALWAYS);
        getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
            if (e.getButton() == MouseButton.SECONDARY) {
                if (_currentEntry == null) return;
                TreeItem<EntryValue> selected = getSelectionModel().getSelectedItem();
                if (selected == null) return;
                _attributesContextMenu.getItems().clear();
                if (selected.getParent().equals(getRoot())) {
                    _attributesContextMenu.getItems().addAll(_deleteAttribute, _addValue, _addAttribute);
                } else {
                    _attributesContextMenu.getItems().addAll(_deleteValue, _addValue);
                }
                _attributesContextMenu.getItems().forEach(x -> x.setStyle("-fx-font: 10px \"Segoe UI\";  -fx-font-weight:bold;"));
                _attributesContextMenu.show(_mainApp.get_primaryStage(), e.getScreenX(), e.getScreenY());
            }
        });

        widthProperty().addListener(x -> {
            nameColumn.setPrefWidth(100.0);
            valueColumn.setPrefWidth(getWidth() - 100.0);
        });

        TreeItem<EntryValue> rootItem = new TreeItem<EntryValue>(new EntryValue("", ""));
        setRoot(rootItem);
        initContextMenuActions();
    }


    private void initContextMenuActions() {
        //_addAttribute.setStyle("-fx-font: 10px \"Segoe UI\";  -fx-font-weight:bold;");
        //_attributesContextMenu.setStyle("-fx-font: 10px \"Segoe UI\";  -fx-font-weight:bold;");
        _attributesContextMenu.getItems().forEach(x -> x.setStyle("-fx-font: 10px \"Segoe UI\";  -fx-font-weight:bold;"));

    }

    private TextFlow buildTextFlow(String text, String filter) {
        int filterIndex = text.toLowerCase().indexOf(filter.toLowerCase());
        Text textBefore = new Text(text.substring(0, filterIndex));
        Text textAfter = new Text(text.substring(filterIndex + filter.length()));
        Text textFilter = new Text(text.substring(filterIndex, filterIndex + filter.length()));
        textFilter.setFill(Color.RED);
        // textFilter.setFont(Font.font("Helvetica", FontWeight.BOLD, 12));
        return new TextFlow(textBefore, textFilter, textAfter);
    }

    class EntryValue {

        public String get_styleName() {
            return _styleName;
        }

        public void set_styleName(String _styleName) {
            this._styleName = _styleName;
        }

        public String get_styleVal() {
            return _styleVal;
        }

        public void set_styleVal(String _styleVal) {
            this._styleVal = _styleVal;
        }

        private String _styleVal = null;
        private String _styleName = null;
        public StringProperty attributeName = new SimpleStringProperty();
        public StringProperty attributeValue = new SimpleStringProperty();

        public EntryValue(String attname, String attValueSource) {
            attributeName.set(attname);
            attributeValue.set(attValueSource);
        }

        public String removeAttributeSize() {
            if (attributeName.get() == null) return null;
            if (!attributeName.get().contains("[")) return attributeName.get();

            int first = attributeName.get().indexOf("[");
            String sub = attributeName.get().substring(first);
            return attributeName.get().replace(sub, "");
        }
    }


    public void cleanUpTree() {
        ObservableList<TreeItem<EntryValue>> children = getRoot().getChildren();
        for (TreeItem<EntryValue> child : children) {
            if (child.getChildren().isEmpty()) continue;
            child.getChildren().clear();
        }
        getRoot().getChildren().clear();
        setRoot(null);
        refresh();
        TreeItem<EntryValue> rootItem = new TreeItem<>(new EntryValue("", ""));
        setRoot(rootItem);
        refresh();
    }

    public void updateValue(CustomEntry customEntry, Connection connection) {
        _currentConnection = connection;
        _currentEntry = customEntry;
        cleanUpTree();
        if (customEntry == null) return;
        if (customEntry.getEntry() == null) return;
        List<String> attributes = new ArrayList<>();
        List<String> oppAts = customEntry.getEntry().getAttributes().stream()
                .map(x -> x.getName())
                .filter(x -> connection.getOperationalAttributes().contains(x))
                .sorted()
                .collect(Collectors.toList());
        List<String> noOps = customEntry.getEntry().getAttributes().stream()
                .map(x -> x.getName())
                .filter(x -> !connection.getOperationalAttributes().contains(x))
                .sorted()
                .collect(Collectors.toList());

        for (String s : noOps) attributes.add(s);
        for (String s : oppAts) attributes.add(s);
        EntryValue dn = new EntryValue("dn", customEntry.getDn());
        dn.set_styleName("-fx-font-weight:bold");
        getRoot().getChildren().add(new TreeItem<>(dn));

        String longestAttribute = "";
        for (String atName : attributes) {
            if(longestAttribute.length() < atName.length()) longestAttribute = atName;
            Attribute attribute = customEntry.getEntry().getAttribute(atName);
            String[] values = attribute.getValues();
            boolean isOp = false;
            if (oppAts.contains(atName)) isOp = true;

            if (values.length > 1) {
                EntryValue rootValue = new EntryValue(atName + "[" + values.length + "]", "");
                if (isOp) rootValue.set_styleName("-fx-text-fill: blue; -fx-font-style: italic");
                if (_searchValue != null) {
                    for (String s : values) {
                        if (s.toLowerCase().contains(_searchValue.toLowerCase()))
                            rootValue.set_styleName(FOUND_VALUE_STYLE);
                    }
                }
                TreeItem<EntryValue> item = new TreeItem<>(rootValue);
                getRoot().getChildren().add(item);
                for (int i = 0; i < values.length; i++) {
                    EntryValue entryValue = new EntryValue(atName + "[" + i + "]", values[i]);
                    if (_searchValue != null && values[i].toLowerCase().contains(_searchValue.toLowerCase())) {
                        entryValue.set_styleName(FOUND_VALUE_STYLE);
                    }
                    TreeItem<EntryValue> child = new TreeItem<>(entryValue);
                    item.getChildren().add(child);
                }
            } else {
                Date time = null;
                String val = null;
                if (atName.toLowerCase().contains("time")) time = attribute.getValueAsDate();
                if (time != null) {
                    DateFormat df = new SimpleDateFormat("dd.MMM yyyy HH:mm:ss.SSS");
                    df.setTimeZone(TimeZone.getDefault());
                    val = df.format(time);

                } else val = values[0];
                EntryValue entryView = new EntryValue(atName, val);
                if (_searchValue != null && values[0].toLowerCase().contains(_searchValue.toLowerCase()))
                    entryView.set_styleName(FOUND_VALUE_STYLE);
                if (isOp) entryView.set_styleName("-fx-text-fill: blue; -fx-font-style: italic");
                TreeItem<EntryValue> item = new TreeItem<>(entryView);
                getRoot().getChildren().add(item);
            }
        }
       nameColumn.setPrefWidth(longestAttribute.length()*10);
        setShowRoot(false);
    }
}
