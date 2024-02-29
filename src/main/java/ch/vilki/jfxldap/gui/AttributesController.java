package ch.vilki.jfxldap.gui;

import ch.vilki.jfxldap.backend.Connection;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class AttributesController {

    @FXML
    ListView<String> _listAllAttributes;
    @FXML
    ListView<String> _listFilterAttributes;

    @FXML
    Button _buttonAddFilterList;
    @FXML
    Button _buttonRemoveFilterList;
    @FXML
    Button _buttonRemoveAllAttributes;
    @FXML
    Button _buttonAddOperational;
    @FXML
    Button _buttonRemoveOperational;
    @FXML Button _buttonADDAttributeLike;


    @FXML
    RadioButton _radioButtonDisableFilter;
    @FXML
    RadioButton _radioButtonCompareAttributes;
    @FXML
    RadioButton _radioButtonIgnoreAttributes;

    @FXML
    TextField _textFieldFilterAttributesText;

    @FXML TextField _textFieldAddAttributeLike;

    ToggleGroup _radioButtonFilterToggleGroup = new ToggleGroup();

    public void set_currentConnection(Connection _currentConnection) {
        this._currentConnection = _currentConnection;
    }

    private Connection _currentConnection;

    public void disableAllElements(boolean disabled) {
        _listAllAttributes.setDisable(disabled);
        _listFilterAttributes.setDisable(disabled);
        _buttonAddFilterList.setDisable(disabled);
        _buttonRemoveFilterList.setDisable(disabled);
        _buttonRemoveAllAttributes.setDisable(disabled);
        _radioButtonDisableFilter.setDisable(disabled);
        _radioButtonCompareAttributes.setDisable(disabled);
        _radioButtonIgnoreAttributes.setDisable(disabled);
        _textFieldFilterAttributesText.setDisable(disabled);
    }

    public ObservableSet<String> get_observableConfigAllAttributes() {
        return _observableConfigAllAttributes;
    }

    private ObservableSet<String> _observableConfigAllAttributes = FXCollections.observableSet();

    public ObservableSet<String> get_observableConfigFilterAttributes() {
        return _observableConfigFilterAttributes;
    }

    private ObservableSet<String> _observableConfigFilterAttributes = FXCollections.observableSet();


    @FXML
    private void initialize() {
        initRadioButtons();
        _buttonRemoveFilterList.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.ARROW_LEFT));
        _buttonAddFilterList.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.ARROW_RIGHT));
        _buttonRemoveAllAttributes.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.REMOVE));
        _buttonAddOperational.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.ARROW_RIGHT));
        _buttonRemoveOperational.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.ARROW_LEFT));
        _observableConfigAllAttributes.addListener((SetChangeListener.Change<? extends String> c) -> {
            if (c.wasAdded()) {
                _listAllAttributes.getItems().add(c.getElementAdded());
                _listAllAttributes.getItems().sort(Comparator.comparing(String::toString));

            }
            if (c.wasRemoved()) {
                _listAllAttributes.getItems().remove(c.getElementRemoved());
                _listAllAttributes.getItems().sort(Comparator.comparing(String::toString));
            }
        });
        _observableConfigFilterAttributes.addListener((SetChangeListener.Change<? extends String> c) -> {
            if (c.wasAdded()) {
                _listFilterAttributes.getItems().add(c.getElementAdded());
            }
            if (c.wasRemoved()) {
                _listFilterAttributes.getItems().remove(c.getElementRemoved());
            }
        });

        _buttonADDAttributeLike.setOnAction(event -> {
            if (_listFilterAttributes == null) return;
            if(!_textFieldAddAttributeLike.getText().isEmpty()){
                String like = "isLike->" + _textFieldAddAttributeLike.getText();
                _listFilterAttributes.getItems().add(like);
            }
        });

        _buttonRemoveFilterList.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                if (_listFilterAttributes == null) return;
                String selectedItem = _listFilterAttributes.getSelectionModel().getSelectedItem();
                if (selectedItem != null && !selectedItem.equalsIgnoreCase("")) {
                    _listAllAttributes.getItems().add(selectedItem);
                    _listFilterAttributes.getItems().remove(selectedItem);
                }
            }
        });
        _buttonRemoveAllAttributes.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                if (_listFilterAttributes == null) return;
                _listFilterAttributes.getItems().clear();
                _listAllAttributes.getItems().clear();
                _listAllAttributes.getItems().addAll(_currentConnection.getSchemaAttributes());
            }
        });
        _buttonAddFilterList.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                if (_listFilterAttributes == null) return;
                String selectedItem = _listAllAttributes.getSelectionModel().getSelectedItem();
                if (selectedItem != null && !selectedItem.equalsIgnoreCase("")) {
                    if (_listFilterAttributes.getItems().contains(selectedItem)) return;
                    _listFilterAttributes.getItems().add(selectedItem);
                    _listAllAttributes.getItems().removeAll(_listFilterAttributes.getItems());
                }
            }
        });
        _textFieldFilterAttributesText.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null || newValue.equalsIgnoreCase("")) {
                _listAllAttributes.getItems().clear();
                _listAllAttributes.getItems().addAll(_currentConnection.getSchemaAttributes());
                _listAllAttributes.getItems().removeAll(_listFilterAttributes.getItems());
            } else {
                _listAllAttributes.getItems().clear();
                _listAllAttributes.getItems().addAll(
                        _currentConnection.getSchemaAttributes().stream().filter(s -> s.toLowerCase().contains(newValue.toLowerCase())).collect(Collectors.toCollection(ArrayList::new)));
                _listAllAttributes.getItems().removeAll(_listFilterAttributes.getItems());
            }
        });
        _textFieldFilterAttributesText.setOnKeyPressed(e -> {
            if (e.getCode().equals(KeyCode.ENTER)) {

                if (_listAllAttributes.getItems().size() == 1) {
                    _listFilterAttributes.getItems().add(_listAllAttributes.getItems().get(0));
                    _listAllAttributes.getItems().removeAll(_listFilterAttributes.getItems());
                    _textFieldFilterAttributesText.setText("");
                }
            }
        });
        _buttonAddOperational.setOnAction(x -> {
            TreeSet<String> opAttributes = _currentConnection.getOperationalAttributes();
            if (opAttributes == null) return;
            _listAllAttributes.getItems().removeAll(opAttributes);
            _listFilterAttributes.getItems().addAll(opAttributes);

        });
        _buttonRemoveOperational.setOnAction(x -> {
            TreeSet<String> opAttributes = _currentConnection.getOperationalAttributes();
            if (opAttributes == null) return;
            _listAllAttributes.getItems().addAll(opAttributes);
            _listFilterAttributes.getItems().removeAll(opAttributes);
        });
    }

    private void initRadioButtons() {
        _radioButtonIgnoreAttributes.setSelected(false);
        _radioButtonCompareAttributes.setSelected(false);
        _radioButtonDisableFilter.setSelected(true);
        _radioButtonIgnoreAttributes.setToggleGroup(_radioButtonFilterToggleGroup);
        _radioButtonCompareAttributes.setToggleGroup(_radioButtonFilterToggleGroup);
        _radioButtonDisableFilter.setToggleGroup(_radioButtonFilterToggleGroup);
    }


}
