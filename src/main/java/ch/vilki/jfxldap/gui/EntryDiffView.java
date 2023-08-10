package ch.vilki.jfxldap.gui;
import ch.vilki.jfxldap.Main;
import ch.vilki.jfxldap.backend.CollectionEntry;
import ch.vilki.jfxldap.backend.CompResult;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.Entry;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import java.util.*;
import java.util.stream.Collectors;

public class EntryDiffView extends TreeTableView<EntryDiffView.EntryValue> {

    TreeTableColumn<EntryValue, String> nameColumn;
    TreeTableColumn<EntryValue, String> resultColumn;
    TreeTableColumn<EntryValue, String> valueColumnSource;
    TreeTableColumn<EntryValue, String> valueColumnTarget;

    Main _mainApp;
    private static CompResult _currentCompResult = null;

    final ContextMenu _attributesContextMenu = new ContextMenu();
    MenuItem _attributeMenuCopyValueToLeft = new MenuItem();
    MenuItem _attributeMenuCopyValueToRight = new MenuItem();

    public EntryDiffView(Main main) {
        _mainApp = main;
        nameColumn = new TreeTableColumn<>("Attribute");
        resultColumn = new TreeTableColumn<>("R");
        valueColumnSource = new TreeTableColumn<>("S");
        valueColumnTarget = new TreeTableColumn<>("T");
        nameColumn.setCellValueFactory(param -> param.getValue().getValue().attributeName);
        resultColumn.setCellValueFactory(param -> param.getValue().getValue().resultProperty);
        valueColumnSource.setCellValueFactory(param -> {

            return param.getValue().getValue().attributeValueSource;
        });
        valueColumnTarget.setCellValueFactory(param -> param.getValue().getValue().attributeValueTarget);
        getColumns().add(nameColumn);
        getColumns().add(resultColumn);
        getColumns().add(valueColumnSource);
        getColumns().addAll(valueColumnTarget);
        resultColumn.setMaxWidth(32);
        setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(this, Priority.ALWAYS);
        getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        resultColumn.setCellFactory(param -> new TreeTableCell<EntryValue, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setGraphic(null);
                    return;
                }
                if (item.equalsIgnoreCase(CompResult.COMPARE_RESULT.ONLY_IN_TARGET.toString()))
                    setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.ARROW_LEFT));
                else if (item.equalsIgnoreCase(CompResult.COMPARE_RESULT.ONLY_IN_SOURCE.toString()))
                    setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.ARROW_RIGHT));
                else if (item.equalsIgnoreCase(CompResult.COMPARE_RESULT.ENTRY_NOT_EQUAL.toString()))
                    setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.UNEQUAL));
                else if (item.equalsIgnoreCase(CompResult.COMPARE_RESULT.ENTRY_EQUAL.toString()))
                    setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.EQUAL));
                else {
                    textProperty().set(item);
                }
            }
        });
        nameColumn.setCellFactory(param -> new TreeTableCell<EntryValue, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setGraphic(null);
                    return;
                }
                setStyle("-fx-font-weight:bold;");
                setText(item);
            }
        });
        valueColumnSource.setCellFactory(param -> new TreeTableCell<EntryValue, String>() {
            TreeTableColumn<EntryValue, String> x = param;

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setGraphic(null);
                    return;
                }
                if (item.length() > 300) setText(item.substring(0, 300));
                else setText(item);
            }
        });
        valueColumnTarget.setCellFactory(param -> new TreeTableCell<EntryValue, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setGraphic(null);
                    return;
                }
                if (item.length() > 300) setText(item.substring(0, 300));
                else setText(item);
            }
        });

        getSelectionModel().selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> {
                    if (newValue == null) {
                        _mainApp._ctManager._ldapCompareController.set_attributeDifferences(null, null);
                    } else {
                        _mainApp._ctManager._ldapCompareController.set_attributeDifferences(
                                newValue.getValue().attributeValueSource.get(), newValue.getValue().attributeValueTarget.get());
                    }
                });
        _attributeMenuCopyValueToRight.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.ARROW_RIGHT));
        _attributeMenuCopyValueToLeft.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.ARROW_LEFT));
        _attributesContextMenu.getItems().addAll(_attributeMenuCopyValueToRight, _attributeMenuCopyValueToLeft);
        _attributesContextMenu.getItems().forEach(x -> x.setStyle("-fx-font: 10px \"Segoe UI\";  -fx-font-weight:bold;"));

        addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
            if (e.getButton() == MouseButton.SECONDARY) {
                if (getSelectionModel().getSelectedItem() == null) return;
                TreeItem<EntryValue> item = getSelectionModel().getSelectedItem();
                if (item.getValue() == null) return;
                if (item.getValue().resultProperty.get().equalsIgnoreCase(CompResult.COMPARE_RESULT.ENTRY_EQUAL.toString()))
                    return;
                _attributesContextMenu.show(_mainApp.get_primaryStage(), e.getScreenX(), e.getScreenY());
            }
        });
        _attributeMenuCopyValueToRight.setOnAction(x -> {
            ObservableList<TreeItem<EntryValue>> items = getSelectionModel().getSelectedItems();
            List<String> attributes = new ArrayList<>();
            for (TreeItem<EntryValue> item : items) {
                String attName = item.getValue().attributeName.get();
                if (attName == null || attName.equalsIgnoreCase("")) {
                    if (item.getParent() != null) attName = item.getParent().getValue().attributeName.get();
                }
                if (attName != null && !attName.equalsIgnoreCase("")) attributes.add(attName);
            }
            if (attributes.isEmpty()) return;
            _mainApp._ctManager._ldapCompareController.copyAttributeValueToTarget(attributes);
        });


        TreeItem<EntryValue> rootItem = new TreeItem<EntryValue>(new EntryValue("", "", "", ""));
        setRoot(rootItem);
        getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {

        });
        widthProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
                if (_currentCompResult != null) {
                    StringBuilder builder = new StringBuilder();
                    _currentCompResult.getDifferentAttributes().stream().sorted().forEach(x -> {
                        if (x.length() > builder.length()) builder.append(x);
                    });
                    double widthWindow = widthProperty().get();
                    Text t = new Text(builder.toString());
                    double nC = t.getLayoutBounds().getWidth();
                    nC = nC * 1.5;
                    widthWindow = widthWindow - nC;
                    nameColumn.setPrefWidth(nC);
                    widthWindow = widthWindow - 32;
                    valueColumnSource.setPrefWidth(widthWindow / 2);
                    valueColumnTarget.setPrefWidth(widthWindow / 2);
                }
            }
        });
    }

    class EntryValue {

        public StringProperty attributeName = new SimpleStringProperty();
        public StringProperty attributeValueSource = new SimpleStringProperty();
        public StringProperty attributeValueTarget = new SimpleStringProperty();
        public StringProperty resultProperty = new SimpleStringProperty();

        public EntryValue(String attname, String result, String attValueSource, String attValueTarget) {
            attributeName.set(attname);
            attributeValueSource.set(attValueSource);
            attributeValueTarget.set(attValueTarget);
            resultProperty.set(result);
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
        TreeItem<EntryValue> rootItem = new TreeItem<EntryValue>(new EntryValue("", "", "", ""));
        setRoot(rootItem);
        refresh();
    }

    public void updateValue(CompResult compResult, boolean showEquals) {
        _currentCompResult = compResult;
        StringBuilder builder = new StringBuilder();
        builder.append(nameColumn.getText());
        cleanUpTree();
        Entry sourceEntry = compResult.get_sourceEntry();
        Entry targetEntry = compResult.get_targetEntry();
        TreeSet<String> evalAttributes = new TreeSet<>();
        if (sourceEntry == null) {
            evalAttributes.addAll(targetEntry.getAttributes().stream().map(x -> x.getName()).collect(Collectors.toSet()));
        } else if (targetEntry == null) {
            evalAttributes.addAll(sourceEntry.getAttributes().stream().map(x -> x.getName()).collect(Collectors.toSet()));
        } else {
            if (showEquals) {
                evalAttributes.addAll(targetEntry.getAttributes().stream().map(x -> x.getName()).collect(Collectors.toSet()));
                evalAttributes.addAll(sourceEntry.getAttributes().stream().map(x -> x.getName()).collect(Collectors.toSet()));
            } else {
                evalAttributes = compResult.getDifferentAttributes();
            }
        }
        evalAttributes.removeAll(_mainApp._ctManager._ldapCompareController._currentSourceConnection.getOperationalAttributes());
        evalAttributes.removeAll(_mainApp._ctManager._ldapCompareController._currentTargetConnection.getOperationalAttributes());
        for (String x : evalAttributes) {
            if (x.length() > builder.length()) {
                builder.setLength(0);
                builder.append(x);
            }
            Attribute sourceAttribute = null;
            Attribute targetAttribute = null;
            if (sourceEntry != null) sourceAttribute = sourceEntry.getAttribute(x);
            if (targetEntry != null) targetAttribute = targetEntry.getAttribute(x);

            boolean multiValue = false;
            if (sourceAttribute != null && sourceAttribute.getValues() != null && sourceAttribute.getValues().length > 1)
                multiValue = true;
            if (targetAttribute != null && targetAttribute.getValues() != null && targetAttribute.getValues().length > 1)
                multiValue = true;
            if (!multiValue) {
                String sourceValue = "";
                String targetValue = "";
                if (sourceAttribute != null && sourceAttribute.getValue() != null)
                    sourceValue = sourceAttribute.getValue();
                if (targetAttribute != null && targetAttribute.getValue() != null)
                    targetValue = targetAttribute.getValue();
                String state = "";
                if (sourceValue.equalsIgnoreCase("")) state = CompResult.COMPARE_RESULT.ONLY_IN_TARGET.toString();
                else if (targetValue.equalsIgnoreCase(""))
                    state = CompResult.COMPARE_RESULT.ONLY_IN_SOURCE.toString();
                else {
                    if (sourceValue.equals(targetValue)) state = CompResult.COMPARE_RESULT.ENTRY_EQUAL.toString();
                    else state = CompResult.COMPARE_RESULT.ENTRY_NOT_EQUAL.toString();
                }
                getRoot().getChildren().add(new TreeItem<>(new EntryValue(x, state, sourceValue, targetValue)));
            } else {
                String attState = "";
                if (compResult.getDifferentAttributes().contains(x)) {
                    attState = CompResult.COMPARE_RESULT.ENTRY_NOT_EQUAL.toString();
                } else if (targetAttribute == null) {
                    attState = CompResult.COMPARE_RESULT.ONLY_IN_SOURCE.toString();
                } else if (sourceAttribute == null) {
                    attState = CompResult.COMPARE_RESULT.ONLY_IN_TARGET.toString();
                } else {
                    attState = CompResult.COMPARE_RESULT.ENTRY_EQUAL.toString();
                }
                TreeItem<EntryValue> attributeNode = new TreeItem<EntryValue>(new EntryValue(x, attState, "", ""));
                getRoot().getChildren().add(attributeNode);
                Set<String> sourceValues = new HashSet<>();
                Set<String> targetValues = new HashSet<>();
                if (sourceAttribute != null && sourceAttribute.getValues() != null)
                    for (String s : sourceAttribute.getValues()) sourceValues.add(s);
                if (targetAttribute != null && targetAttribute.getValues() != null)
                    for (String s : targetAttribute.getValues()) targetValues.add(s);
                if (sourceValues.isEmpty()) {

                    getRoot().getChildren().add(attributeNode);
                    for (String v : targetValues) {
                        TreeItem<EntryValue> valueNode = new TreeItem<>(new EntryValue("", CompResult.COMPARE_RESULT.ONLY_IN_TARGET.toString(), "", v));
                        attributeNode.getChildren().add(valueNode);
                    }
                } else if (targetValues.isEmpty()) {
                    for (String v : sourceValues) {
                        TreeItem<EntryValue> valueNode = new TreeItem<>(new EntryValue("", CompResult.COMPARE_RESULT.ONLY_IN_SOURCE.toString(), v, ""));
                        attributeNode.getChildren().add(valueNode);
                    }
                } else {

                    for (String v : sourceValues) {
                        String tValue = "";
                        boolean same = false;
                        boolean onlySource = false;
                        boolean onlyTarget = false;
                        boolean nEqual = false;
                        if (targetValues.contains(v)) {
                            same = true;
                            tValue = v;
                            targetValues.remove(v);
                        } else {
                            if (!targetValues.isEmpty()) {
                                onlySource = true;
                            }
                        }
                        TreeItem<EntryValue> valueNode = null;
                        String state = "";
                        if (same) state = CompResult.COMPARE_RESULT.ENTRY_EQUAL.toString();
                        else if (onlySource) state = CompResult.COMPARE_RESULT.ONLY_IN_SOURCE.toString();
                        else if (onlyTarget) state = CompResult.COMPARE_RESULT.ONLY_IN_TARGET.toString();
                        else if (nEqual) state = CompResult.COMPARE_RESULT.ENTRY_NOT_EQUAL.toString();

                        if (same && !showEquals) continue;
                        valueNode = new TreeItem<>(new EntryValue("", state, v, tValue));
                        attributeNode.getChildren().add(valueNode);
                    }
                    for (String v : targetValues) {
                        TreeItem<EntryValue> valueNode = new TreeItem<>(new EntryValue("", CompResult.COMPARE_RESULT.ONLY_IN_TARGET.toString(), "", v));
                        attributeNode.getChildren().add(valueNode);
                    }
                }
                //attributeNode.setExpanded(true);
            }
        }
        if (_mainApp._ctManager._ldapCompareController._currentSourceConnection != null)
            valueColumnSource.setText(_mainApp._ctManager._ldapCompareController._currentSourceConnection.getName());
        if (_mainApp._ctManager._ldapCompareController._currentTargetConnection != null)
            valueColumnTarget.setText(_mainApp._ctManager._ldapCompareController._currentTargetConnection.getName());
        _attributeMenuCopyValueToRight.setText(valueColumnTarget.getText());
        _attributeMenuCopyValueToLeft.setText(valueColumnSource.getText());

        double widthWindow = widthProperty().get();
        Text t = new Text(builder.toString());
        t.setStyle("-fx-font:12px \"Segoe UI\";");
        double nC = t.getLayoutBounds().getWidth();
        nC = nC * 1.5;
        widthWindow = widthWindow - nC;
        nameColumn.setPrefWidth(nC);
        widthWindow = widthWindow - 32;

        if (sourceEntry == null) {
            Text ttm = new Text(valueColumnSource.getText());

            valueColumnSource.setPrefWidth(ttm.getLayoutBounds().getWidth());
            valueColumnTarget.setPrefWidth(widthWindow - ttm.getLayoutBounds().getWidth());
        }
        if (targetEntry == null) {
            Text ttm = new Text(valueColumnTarget.getText());
            valueColumnTarget.setPrefWidth(ttm.getLayoutBounds().getWidth());
            valueColumnSource.setPrefWidth(widthWindow - ttm.getLayoutBounds().getWidth());

        } else {
            valueColumnSource.setPrefWidth(widthWindow / 2);
            valueColumnTarget.setPrefWidth(widthWindow / 2);
        }
        setShowRoot(false);
    }

    public void updateValues(CollectionEntry c) {}


}
