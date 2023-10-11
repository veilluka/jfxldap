package ch.vilki.jfxldap.gui;

import ch.vilki.jfxldap.Main;
import ch.vilki.jfxldap.backend.*;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.SearchScope;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class DeleteEntriesController implements ILoader, IProgress {

    static Logger logger = LogManager.getLogger(DeleteEntriesController.class);
    static String st1 = "-fx-background-color: #14563f;-fx-text-fill: #ffffff ";
    static String st2 = "-fx-background-color:#c62f21 ;-fx-text-fill: #ffffff ";
    private static boolean realDeleteDone = false;
    @FXML   VBox _window;
    @FXML   RadioButton _radioButtonSelectedWithChildren;
    @FXML   RadioButton _radioButtonOnlyChildren;
    @FXML   Button _buttonDelete;
    @FXML   Button _buttonCancel;
    @FXML   HBox _hboxFilter;
    @FXML   ListView<TextField> _listViewResults;
    TextFieldLdapFilter _textFieldLdapFilter = null;
    Connection _currentConnection = null;
    String      _selectedDN = null;
    ToggleGroup _deleteEntryToggleGroup = new ToggleGroup();
    UnboundidLdapSearch _unboundidLdapSearch = null;
    TreeItem<CustomEntryItem> _selectedEntry = null;
    Scene _scene;
    Stage _stage;
    private Main _main;
    private List<String> _itemsToDelete = null;

    @FXML
    private void initialize() {
        _radioButtonOnlyChildren.setToggleGroup(_deleteEntryToggleGroup);
        _radioButtonSelectedWithChildren.setToggleGroup(_deleteEntryToggleGroup);
        _textFieldLdapFilter = new TextFieldLdapFilter();
        _hboxFilter.getChildren().add(_textFieldLdapFilter);
        _listViewResults.setDisable(true);
        _textFieldLdapFilter.setDisable(true);
        _radioButtonSelectedWithChildren.setSelected(true);
        _radioButtonOnlyChildren.setOnAction(x->{
            _listViewResults.setDisable(false);
            _textFieldLdapFilter.setDisable(false);
        });
        _radioButtonSelectedWithChildren.setOnAction(x->{
            _listViewResults.setDisable(true);
            _textFieldLdapFilter.setDisable(true);
        });
        _buttonDelete.setOnAction(x->{
                delete();
        });
        _buttonCancel.setOnAction(x->{
            _stage.close();
        });
        _buttonDelete.setStyle(st1);
    }
    public VBox getWindow() {
        return _window;
    }

    @Override
    public void setWindow(Parent parent) {
        _window = (VBox) parent;
        _scene = new Scene(_window);
        _stage = new Stage();
        _stage.setScene(_scene);
    }

    @Override
    public void setMain(Main main) {
        _main = main;
    }

    public void show(TreeItem<CustomEntryItem> selectedEntry, Connection connection, String dn )
    {
        if(selectedEntry == null || connection == null || dn == null ) return;
        _buttonDelete.setStyle(st1);
        _buttonDelete.setDisable(false);
        _buttonDelete.setText("Get entries");
        realDeleteDone = false;
        _selectedDN = dn;
        _currentConnection = connection;
        _textFieldLdapFilter.set_currConnection(connection);
        _listViewResults.getItems().clear();
        _textFieldLdapFilter.setText("");
        _selectedEntry = selectedEntry;
        _stage.showAndWait();
    }

    private void delete()
    {
        if(_currentConnection == null)
        {
            GuiHelper.ERROR("Connection error","Found no connection to delete");
            return;
        }
        if(_selectedDN==null) return;
        if(!realDeleteDone && !_listViewResults.getItems().isEmpty())
        {
             if(!GuiHelper.confirm("Confirm delete","Delete found entries?","This will delete all found entries, " +
                    "in case LDAP Filter has been used under children only, only leafs will be deleted!")) return;
            _stage.close();
            Main._ctManager._progressWindowController._stage.show();
            _itemsToDelete = _listViewResults.getItems().stream().map(x->x.getText()).toList();
            new Thread(deleteEntries).start();
            return;

        }
        try {
            _unboundidLdapSearch = new UnboundidLdapSearch(_main._configuration
                    ,_currentConnection,this);
            _unboundidLdapSearch.set_searchDN(_selectedDN);
            _unboundidLdapSearch.setReadAttributes(UnboundidLdapSearch.READ_ATTRIBUTES.none);
            Filter filter = Filter.create("objectclass=*");
            if(_textFieldLdapFilter.is_filterOK()) filter = _textFieldLdapFilter.get_filter();
            _unboundidLdapSearch.set_filter(filter);
            _unboundidLdapSearch.set_searchScope(SearchScope.SUB);
            _listViewResults.getItems().clear();
            _listViewResults.setDisable(false);
            Main._ctManager._progressWindowController._stage.show();
            ExecutorService executorService = Executors.newSingleThreadExecutor();
            executorService.execute(_unboundidLdapSearch);
        }
        catch (Exception e)
        {
            GuiHelper.EXCEPTION("Error deleting",e.getMessage(),e);
        }
    }

    @Override
    public void setOwner(Stage stage) {
        _stage.initOwner(stage);

    }

    @Override
    public void setProgress(double progress, String description) {
          Platform.runLater(()-> Main._ctManager._progressWindowController.setProgress(progress,description));
    }

    @Override
    public void signalTaskDone(String taskName, String description, Exception e) {
        if(taskName.equals("deleted")){
            Platform.runLater(()->{
                //Main._ctManager._progressWindowController._stage.close();
                //_stage.show();
                _buttonDelete.setDisable(true);
                _listViewResults.getItems().clear();
            });
            return;
        }

        Platform.runLater(()->{
             _main._ctManager._progressWindowController._stage.close();
             _buttonDelete.setStyle(st2);
            _buttonDelete.setText("DELETE");
        });
        List<Entry>sorted =  _unboundidLdapSearch.get_children().stream().sorted(Helper.DN_LengthComparator).collect(Collectors.toList());

        for(Entry s:  sorted)
        {
            if(_radioButtonOnlyChildren.isSelected() && s.getDN().equalsIgnoreCase(_selectedDN)) continue;
            TextField textField = new TextField(s.getDN());
            Platform.runLater(()->_listViewResults.getItems().add(textField));
        }
    }

    @Override
    public void setProgress(String taskName, double progress) {
        Platform.runLater(()->Main._ctManager._progressWindowController.setProgress(progress,taskName));
    }

    Task<Void> deleteEntries = new Task<>() {
        @Override
        protected Void call() throws Exception {
            Integer done = 0;
            double nrOfEntries = _itemsToDelete.size();
            for (String dn : _itemsToDelete) {
                done++;
                _currentConnection.delete(dn);
                if (done % 10 == 0) {
                    double prog = (double) done / nrOfEntries;
                    setProgress(prog, dn);
                }
            }
            realDeleteDone = true;
            Platform.runLater(() -> {
                Main._ctManager._progressWindowController._stage.close();
                _stage.show();
                _buttonDelete.setDisable(true);
                _listViewResults.getItems().clear();
            });
            signalTaskDone("deleted", "done", null);
            return null;
        }
    };
}
