package ch.vilki.jfxldap.gui;

import ch.vilki.jfxldap.Main;
import ch.vilki.jfxldap.backend.Connection;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;


public class StartLdapCompareController implements ILoader {

    @FXML TextField _textFieldSelectedSourceDN;
    @FXML TextField _textFieldTargetDN;
    @FXML ChoiceBox<Connection> _choiceBoxTargetConnection;
    @FXML Button _buttonRunCompare;
    @FXML Button _buttonCancel;

    @FXML CheckBox _checkBoxIgnoreMissingEntries;
    @FXML CheckBox _checkBoxIgnoreOperationalAttributes;
    @FXML CheckBox _checkBoxShowAllEntries;
    @FXML CheckBox _checkBoxIgnoreWhiteSpace;
    @FXML CheckBox _checkBoxSubtree;


    @FXML private Parent embeddedFilterView;
    public FilterWindowController getEmbeddedFilterViewController() {
        return embeddedFilterViewController;
    }
    @FXML private FilterWindowController embeddedFilterViewController;

    Main _main;

    Scene _scene;
    Stage _stage;

    public boolean is_targetExplorerAction() {
        return _targetExplorerAction;
    }

    public void set_targetExplorerAction(boolean _targetExplorerAction) {
        this._targetExplorerAction = _targetExplorerAction;
    }

    private boolean _targetExplorerAction = false;


    public VBox get_window() {
        return _startLdapCompareWindow;
    }

    VBox _startLdapCompareWindow;

    public StartLdapCompareController(){}

    @FXML
    private void initialize() {

        _buttonCancel.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                _stage.close();
            }
        });
        _textFieldSelectedSourceDN.setEditable(false);
        _textFieldTargetDN.setEditable(false);

        _buttonRunCompare.setOnAction(x->{
            _main._ctManager._ldapCompareController.runCompare();
        });
    }

    @Override
    public void setMain(Main main) {
        _main = main;
        _stage = new Stage();
        _choiceBoxTargetConnection.setItems(_main._ctManager._settingsController._connectionObservableList);
    }

    public void setTargetSelectedView(String targetDN, int targetSelected)
    {
        if(targetSelected != -1 )
        {
            _choiceBoxTargetConnection.setDisable(true);
            _choiceBoxTargetConnection.getSelectionModel().select(targetSelected);
        }
        _textFieldTargetDN.setText(targetDN);
    }

    @Override
    public void setWindow(Parent parent) {
        _startLdapCompareWindow = (VBox) parent;
        _scene = new Scene(_startLdapCompareWindow);
        _stage = new Stage();
        _stage.setScene(_scene);
        _stage.initStyle(StageStyle.DECORATED);
        _stage.initModality(Modality.APPLICATION_MODAL);
    }





    @Override
    public void setOwner(Stage stage) {
        _stage.initOwner(stage);
    }
}
