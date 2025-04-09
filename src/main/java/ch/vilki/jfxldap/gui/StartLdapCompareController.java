package ch.vilki.jfxldap.gui;

import ch.vilki.jfxldap.Main;
import ch.vilki.jfxldap.backend.Connection;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Controller for the StartLdapCompare view.
 */
public class StartLdapCompareController implements ILoader {

    @FXML
    TextField _textFieldSelectedSourceDN;
    @FXML
    TextField _textFieldTargetDN;
    @FXML
    ChoiceBox<Connection> _choiceBoxTargetConnection;
    @FXML
    ChoiceBox<String> _choiceBoxTag;
    @FXML
    Button _buttonRunCompare;
    @FXML
    Button _buttonCancel;

    @FXML
    CheckBox _checkBoxIgnoreMissingEntries;
    @FXML
    CheckBox _checkBoxIgnoreOperationalAttributes;
    @FXML
    CheckBox _checkBoxShowAllEntries;
    @FXML
    CheckBox _checkBoxIgnoreWhiteSpace;
    @FXML
    CheckBox _checkBoxSubtree;

    private FilteredList<Connection> _filteredConnections;
    private ObservableList<Connection> _allConnections;

    @FXML
    private Parent embeddedFilterView;
    public FilterWindowController getEmbeddedFilterViewController() {
        return embeddedFilterViewController;
    }
    @FXML
    private FilterWindowController embeddedFilterViewController;

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

    /**
     * Returns the VBox representing the StartLdapCompare window.
     */
    public VBox get_window() {
        return _startLdapCompareWindow;
    }

    VBox _startLdapCompareWindow;

    public StartLdapCompareController(){}

    /**
     * Initializes the controller.
     */
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
            Main._ctManager._ldapCompareController.runCompare();
        });
        
        // Note: Tooltip is now set in FXML
    }

    /**
     * Sets the main application instance.
     */
    @Override
    public void setMain(Main main) {
        _main = main;
        _stage = new Stage();
        
        // Initialize connection list with tag filtering
        _allConnections = Main._ctManager._settingsController._connectionObservableList;
        _filteredConnections = new FilteredList<>(_allConnections, p -> true);
        _choiceBoxTargetConnection.setItems(_filteredConnections);
        
        // Initialize tag choice box
        populateTagChoiceBox();
        
        // Add listener to tag choice box
        _choiceBoxTag.getSelectionModel().selectedItemProperty().addListener(
            (observable, oldValue, newValue) -> {
                if (newValue != null) {
                    System.out.println("Selected tag: " + newValue);
                    updateConnectionFilter(newValue);
                }
            }
        );
        
        // Add listener to connection list to update tags when connections change
        _allConnections.addListener((javafx.collections.ListChangeListener<Connection>) change -> {
            // Store current selection
            String selectedTag = _choiceBoxTag.getSelectionModel().getSelectedItem();
            
            // Repopulate tag choice box
            populateTagChoiceBox();
            
            // Restore previous selection or default to "All"
            if (selectedTag != null && _choiceBoxTag.getItems().contains(selectedTag)) {
                _choiceBoxTag.getSelectionModel().select(selectedTag);
            } else {
                _choiceBoxTag.getSelectionModel().select(0); // Select "All"
            }
            
            // Reapply filter
            updateConnectionFilter(_choiceBoxTag.getSelectionModel().getSelectedItem());
        });
    }
    
    /**
     * Populates the tag choice box with all available tags from connections.
     */
    private void populateTagChoiceBox() {
        ObservableList<String> tags = FXCollections.observableArrayList();
        tags.add("All"); // Default option to show all connections
        
        // Use TreeSet with CASE_INSENSITIVE_ORDER for case-insensitive sorting
        Set<String> uniqueTags = new java.util.TreeSet<>(java.lang.String.CASE_INSENSITIVE_ORDER);
        for (Connection conn : _allConnections) {
            String tag = conn.getTag();
            if (tag != null && !tag.trim().isEmpty()) {
                uniqueTags.add(tag.trim());
            }
        }
        
        // Add all unique tags to the ObservableList
        tags.addAll(uniqueTags);
        
        // Set items and select the "All" option by default
        _choiceBoxTag.setItems(tags);
        _choiceBoxTag.getSelectionModel().select(0); // Select "All" option
    }
    
    /**
     * Updates the connection filter based on the selected tag.
     */
    private void updateConnectionFilter(String selectedTag) {
        if (selectedTag == null || selectedTag.equals("All")) {
            // Show all connections
            _filteredConnections.setPredicate(p -> true);
            System.out.println("Showing all connections");
        } else {
            // Show only connections with the selected tag
            _filteredConnections.setPredicate(conn -> {
                String connTag = conn.getTag();
                return connTag != null && connTag.trim().equalsIgnoreCase(selectedTag);
            });
            System.out.println("Filtering connections by tag: " + selectedTag);
        }
        
        // If no connection is selected after filtering, select first if available
        if (_choiceBoxTargetConnection.getSelectionModel().isEmpty() && 
                !_filteredConnections.isEmpty()) {
            _choiceBoxTargetConnection.getSelectionModel().select(0);
        }
        
        System.out.println("Filtered connections count: " + _filteredConnections.size());
    }

    /**
     * Sets the target selected view.
     */
    public void setTargetSelectedView(String targetDN, int targetSelected)
    {
        if(targetSelected != -1 )
        {
            _choiceBoxTargetConnection.setDisable(true);
            _choiceBoxTag.setDisable(true);
            _choiceBoxTargetConnection.getSelectionModel().select(targetSelected);
        }
        _textFieldTargetDN.setText(targetDN);
    }

    /**
     * Sets the window.
     */
    @Override
    public void setWindow(Parent parent) {
        _startLdapCompareWindow = (VBox) parent;
        _scene = new Scene(_startLdapCompareWindow);
        _stage = new Stage();
        _stage.setScene(_scene);
        _stage.initStyle(StageStyle.DECORATED);
        _stage.initModality(Modality.APPLICATION_MODAL);
    }

    /**
     * Sets the owner stage.
     */
    @Override
    public void setOwner(Stage stage) {
        _stage.initOwner(stage);
    }
}
