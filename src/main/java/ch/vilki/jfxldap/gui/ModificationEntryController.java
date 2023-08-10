package ch.vilki.jfxldap.gui;

import ch.vilki.jfxldap.Main;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

public class ModificationEntryController implements ILoader {

    @FXML  Button _buttonRemove;
    @FXML  TextField _textFieldAttribute;
    @FXML  TextField _textFieldDN;
    @FXML  ListView<String> _listViewBefore;
    @FXML  ListView<String> _listViewAfter;

    Main _main = null;
    Scene _scene;
    Stage _stage;
    VBox _showModificationEntry;

    @FXML
    ObservableList<String> _observableListValuesBefore = FXCollections.observableArrayList();
    ObservableList<String> _observableListValuesAfter = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        _listViewBefore.setItems(_observableListValuesBefore);
        _listViewAfter.setItems(_observableListValuesAfter);

    }
    public static ModificationEntryController getModificationEntryController()
    {
        FXMLLoader loader = new FXMLLoader();
        URL url = Main.class.getResource(ControllerManager.Companion.fxmlDir("/ModificationEntry.fxml"));
        loader.setLocation(url);
        try {
            Parent parent = loader.load();
        } catch (IOException e) {
            e.printStackTrace();
        }
        ModificationEntryController controller = loader.getController();
        return controller;
     }

    @Override
    public void setMain(Main main) {

    }

    @Override
    public void setWindow(Parent parent) {
        _showModificationEntry = (VBox) parent;
        _scene = new Scene(_showModificationEntry);
        _stage = new Stage();
        _stage.setScene(_scene);
    }

    @Override
    public void setOwner(Stage stage) {

    }
}
