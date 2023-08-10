package ch.vilki.jfxldap.gui;

import ch.vilki.jfxldap.Main;
import com.unboundid.ldap.sdk.Filter;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;


public class FilterWindowController {

    static Logger logger = LogManager.getLogger(FilterWindowController.class);

    @FXML ListView<String> _listAllAttributes;
    @FXML ListView<String> _listFilterAttributes;

    @FXML Button _buttonAddFilterList;
    @FXML Button _buttonRemoveFilterList;
    @FXML Button _buttonRemoveAllAttributes;

    @FXML RadioButton _radioButtonDisableFilter;
    @FXML RadioButton _radioButtonCompareAttributes;
    @FXML RadioButton _radioButtonIgnoreAttributes;
    @FXML TextField _textFieldFilterAttributesText;
    @FXML TextField _textFieldSourceFilter;
    @FXML VBox _vboxFilter;
    @FXML Label _labelSourceFilter;
    @FXML Label _labelTargetFilter;
    AutoCompleteTextField _autoCompleteTextField;


    String _textCoulourError = "-fx-text-fill: red;-fx-font-weight:bold;";
    String _textColourOK =  "-fx-fill: #4F8A10";
    Filter _sourceFilter = null;
    Filter _targetFilter = null;
    private List<String> schemaAttributes = new ArrayList<String>();
    private boolean _ldapExplorerTargetAction = false;
    private Main _mainApp;


    public void setSchemaAttributes(List<String> schemaAttributes) {
        this.schemaAttributes = schemaAttributes.stream().sorted().collect(Collectors.toCollection(ArrayList::new));
         _listAllAttributes.getItems().clear();
        _listAllAttributes.getItems().addAll(this.schemaAttributes);
        _listAllAttributes.getItems().removeAll(_listFilterAttributes.getItems());
        _autoCompleteTextField.getEntries().clear();
        _autoCompleteTextField.getEntries().addAll( this.schemaAttributes);
    }

    public ObservableSet<String> get_observableConfigAllAttributes() {
        return _observableConfigAllAttributes;
    }
    private ObservableSet<String> _observableConfigAllAttributes = FXCollections.observableSet();
    public ObservableSet<String> get_observableConfigFilterAttributes() {
        return _observableConfigFilterAttributes;
    }
    private ObservableSet<String> _observableConfigFilterAttributes = FXCollections.observableSet();
    public Filter get_sourceFilter() {
        return _sourceFilter;
    }
    public Filter get_targetFilter() {
        return _targetFilter;
    }

    public void set_mainApp(Main main){ _mainApp = main;}
    public boolean is_ldapExplorerTargetAction() {
        return _ldapExplorerTargetAction;
    }
    public void set_ldapExplorerTargetAction(boolean _ldapExplorerTargetAction) {
        this._ldapExplorerTargetAction = _ldapExplorerTargetAction;
    }

    @FXML
    private void onTextFieldFilterAttributesTextChanged(ActionEvent event)
    {
        String text = _textFieldFilterAttributesText.getText();
        if(text == null || text.equalsIgnoreCase(""))
        {
            _listAllAttributes.getItems().clear();
            _listAllAttributes.getItems().addAll(_mainApp._ctManager._ldapSourceExploreCtrl.get_currentConnection().SchemaAttributes);
        }
        else
        {
            _listAllAttributes.getItems().clear();
            for(String s: _mainApp._configuration._allAttributes)
            {
                if(s.toLowerCase().startsWith(text.toLowerCase())) _listAllAttributes.getItems().add(s);
            }
        }
    }

    private void initRadioButtons() {
        _radioButtonIgnoreAttributes.setSelected(false);
        _radioButtonCompareAttributes.setSelected(false);
        _radioButtonDisableFilter.setSelected(true);
        _radioButtonCompareAttributes.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                if (_radioButtonCompareAttributes.isSelected()) {
                    _radioButtonIgnoreAttributes.setSelected(false);
                    _radioButtonDisableFilter.setSelected(false);
                } else _radioButtonCompareAttributes.setSelected(true);
            }
        });
        _radioButtonIgnoreAttributes.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                if (_radioButtonIgnoreAttributes.isSelected()) {
                    _radioButtonCompareAttributes.setSelected(false);
                    _radioButtonDisableFilter.setSelected(false);
                } else _radioButtonIgnoreAttributes.setSelected(true);
            }
        });
        _radioButtonDisableFilter.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                if (_radioButtonDisableFilter.isSelected()) {
                    _radioButtonCompareAttributes.setSelected(false);
                    _radioButtonIgnoreAttributes.setSelected(false);
                } else _radioButtonDisableFilter.setSelected(true);
            }
        });
    }

    @FXML
    private void initialize() {
        _autoCompleteTextField = new AutoCompleteTextField();
        _vboxFilter.getChildren().add(_autoCompleteTextField);
        initRadioButtons();
        _buttonRemoveFilterList.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.ARROW_LEFT));
        _buttonAddFilterList.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.ARROW_RIGHT));
        _buttonRemoveAllAttributes.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.REMOVE));

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

        _buttonRemoveFilterList.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                if (_listFilterAttributes == null) return;
                String selectedItem = _listFilterAttributes.getSelectionModel().getSelectedItem();
                if (selectedItem != null && !selectedItem.equalsIgnoreCase("")) {
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
                _listAllAttributes.getItems().addAll(schemaAttributes);
            }
        });
        _textFieldFilterAttributesText.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                if (_listAllAttributes.getItems().size() == 1) {
                    String selectedItem = _listAllAttributes.getItems().get(0);
                    if (_listFilterAttributes.getItems().contains(selectedItem)) return;
                    _listFilterAttributes.getItems().add(selectedItem);
                    _listAllAttributes.getItems().remove(selectedItem);
                    _textFieldFilterAttributesText.setText("");
                }
            }
        });
        _textFieldFilterAttributesText.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null || newValue.equalsIgnoreCase("")) {
                _listAllAttributes.getItems().clear();
                _listAllAttributes.getItems().addAll(schemaAttributes);
                _listAllAttributes.getItems().removeAll(_listFilterAttributes.getItems());
            } else {
                _listAllAttributes.getItems().clear();
                _listAllAttributes.getItems().addAll(
                        schemaAttributes.stream().filter(s -> s.toLowerCase().startsWith(newValue.toLowerCase())).collect(Collectors.toCollection(ArrayList::new)));
                _listAllAttributes.getItems().removeAll(_listFilterAttributes.getItems());
            }
        });
        _textFieldSourceFilter.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null || newValue.equalsIgnoreCase("")) {
                _textFieldSourceFilter.setStyle(_textColourOK);
                _labelSourceFilter.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.ENTRY_EQUAL));
                _sourceFilter = null;

            } else {
                try {
                    _sourceFilter = Filter.create(newValue);
                    _textFieldSourceFilter.setStyle(_textColourOK);
                    _labelSourceFilter.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.ENTRY_EQUAL));
                } catch (Exception e) {
                    _sourceFilter = null;
                    _textFieldSourceFilter.setStyle(_textCoulourError);
                    _labelSourceFilter.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.ENTRY_NOT_EQUAL));

                }

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
                    _listAllAttributes.getItems().remove(selectedItem);

                }
            }
        });
    }
}
