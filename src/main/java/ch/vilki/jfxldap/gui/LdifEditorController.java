package ch.vilki.jfxldap.gui;

import ch.vilki.jfxldap.Main;
import ch.vilki.jfxldap.backend.Connection;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldif.LDIFChangeRecord;
import com.unboundid.ldif.LDIFException;
import com.unboundid.ldif.LDIFReader;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LdifEditorController implements ILoader {

    private static final Logger logger = LogManager.getLogger(LdifEditorController.class);

    @FXML
    private BorderPane rootPane;

    @FXML
    private TextArea ldifTextArea;

    @FXML
    private ChoiceBox<Connection> connectionChoiceBox;

    @FXML
    private TextField baseDnField;

    @FXML
    private Button executeButton;

    @FXML
    private Button cancelButton;

    @FXML
    private Label statusLabel;

    @FXML
    private ProgressBar progressBar;

    @FXML
    private TextField ldapFilterField;

    @FXML
    private Button applyFilterButton;

    @FXML
    private ComboBox<String> modificationTypeComboBox;

    @FXML
    private ComboBox<String> attributeComboBox;

    @FXML
    private TextArea attributeValueArea;

    @FXML
    private Button addModificationButton;

    private Stage stage;
    private Scene scene;
    private Main main;
    private Connection currentConnection;
    private LdapExploreController ldapExploreController;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @FXML
    public void initialize() {
        // Initialize modification types
        ObservableList<String> modificationTypes = FXCollections.observableArrayList();
        modificationTypes.addAll("ADD", "DELETE", "REPLACE");
        modificationTypeComboBox.setItems(modificationTypes);
        modificationTypeComboBox.getSelectionModel().select(0);

        // Set up connection choice box
        connectionChoiceBox.setTooltip(new Tooltip("Select LDAP connection"));
        connectionChoiceBox.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<Connection>() {
            @Override
            public void changed(ObservableValue<? extends Connection> observable, Connection oldValue, Connection newValue) {
                if (newValue != null) {
                    currentConnection = newValue;
                    // Set default value for Base DN from connection
                    if (newValue.baseDNProperty() != null && !newValue.baseDNProperty().get().isEmpty()) {
                        baseDnField.setText(newValue.baseDNProperty().get());
                    }
                    updateAttributeComboBox();
                }
            }
        });

        // Set up base DN field
        baseDnField.setTooltip(new Tooltip("Starting point for LDAP search"));

        // Set up LDAP filter field
        ldapFilterField.setTooltip(new Tooltip("Enter LDAP filter to search for entries"));
        ldapFilterField.textProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
                if (newValue != null && !newValue.isEmpty()) {
                    validateLdapFilter(newValue);
                }
            }
        });

        // Set up buttons
        setupButtonActions();

        // Set up attribute combo box
        attributeComboBox.setTooltip(new Tooltip("Select or type an attribute"));
        attributeComboBox.setEditable(true);
        
        // Add auto-completion support for attribute ComboBox
        setupAttributeAutoCompletion();
        
        // Rename the "Add to LDIF" button to "Create LDIF"
        addModificationButton.setText("Create LDIF");
        addModificationButton.setTooltip(new Tooltip("Create LDIF modifications for entries matching the filter"));
    }

    private void updateAttributeComboBox() {
        if (currentConnection != null) {
            ObservableList<String> attributes = FXCollections.observableArrayList();
            String[] schemaAttributes = currentConnection.getAllSchemaAttributes();
            if (schemaAttributes != null) {
                attributes.addAll(schemaAttributes);
                java.util.Collections.sort(attributes, String.CASE_INSENSITIVE_ORDER);
            }
            attributeComboBox.setItems(attributes);
        }
    }

    private void validateLdapFilter(String filterText) {
        try {
            Filter.create(filterText);
            statusLabel.setText("Valid LDAP filter");
            statusLabel.setStyle("-fx-text-fill: green;");
        } catch (Exception e) {
            statusLabel.setText("Invalid LDAP filter: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: red;");
        }
    }

    private void setupButtonActions() {
        executeButton.setOnAction(new javafx.event.EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                executeLdif();
            }
        });
        cancelButton.setOnAction(new javafx.event.EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                stage.close();
            }
        });
        applyFilterButton.setOnAction(new javafx.event.EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                applyFilter();
            }
        });
        addModificationButton.setOnAction(new javafx.event.EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                addModificationToLdif();
            }
        });
    }

    private void applyFilter() {
        String filterText = ldapFilterField.getText();
        if (filterText == null || filterText.isEmpty()) {
            GuiHelper.ERROR("Filter Required", "Please enter an LDAP filter");
            return;
        }

        try {
            Filter.create(filterText);
            // TODO: Implement actual LDAP search using the filter
            statusLabel.setText("Filter applied successfully");
            statusLabel.setStyle("-fx-text-fill: green;");
        } catch (Exception e) {
            GuiHelper.ERROR("Invalid Filter", "Invalid LDAP filter: " + e.getMessage());
        }
    }

    private void addModificationToLdif() {
        if (currentConnection == null) {
            GuiHelper.ERROR("Connection Required", "Please select a connection");
            return;
        }

        String filterText = ldapFilterField.getText();
        if (filterText == null || filterText.isEmpty()) {
            GuiHelper.ERROR("Filter Required", "Please enter an LDAP filter to find entries");
            return;
        }

        String modificationType = modificationTypeComboBox.getValue();
        String attribute = attributeComboBox.getValue();
        String value = attributeValueArea.getText();

        if (attribute == null || attribute.isEmpty()) {
            GuiHelper.ERROR("Attribute Required", "Please select or enter an attribute");
            return;
        }

        if (value == null || value.isEmpty() && !modificationType.equals("DELETE")) {
            GuiHelper.ERROR("Value Required", "Please enter a value (except for DELETE operations)");
            return;
        }

        // Get base DN for search
        final String baseDN;
        String tempBaseDN = baseDnField.getText();
        if (tempBaseDN == null || tempBaseDN.isEmpty()) {
            // If base DN is not specified, use the connection's base DN
            if (currentConnection.baseDNProperty() != null && !currentConnection.baseDNProperty().get().isEmpty()) {
                tempBaseDN = currentConnection.baseDNProperty().get();
                baseDnField.setText(tempBaseDN); // Update the field
            } else {
                // Use empty string for root DSE if no base DN is available
                tempBaseDN = "";
            }
        }
        baseDN = tempBaseDN; // Assign to final variable

        // Clear existing LDIF content
        ldifTextArea.clear();
        
        // Show progress
        progressBar.setVisible(true);
        statusLabel.setText("Searching for entries and creating LDIF modifications...");
        statusLabel.setStyle("-fx-text-fill: blue;");

        // Execute search and create LDIF in background
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    // Create the search filter
                    Filter filter = Filter.create(filterText);
                    
                    // Perform the search
                    final StringBuilder ldifContent = new StringBuilder();
                    int entryCount = 0;
                    
                    // Use the unboundid API to search for entries
                    com.unboundid.ldap.sdk.SearchRequest searchRequest = 
                        new com.unboundid.ldap.sdk.SearchRequest(
                            baseDN, // Use the base DN from the field
                            com.unboundid.ldap.sdk.SearchScope.SUB,
                            filter,
                            "dn"); // Only need the DN
                    
                    com.unboundid.ldap.sdk.SearchResult searchResult = 
                        currentConnection.get_ldapConnection().search(searchRequest);
                    
                    for (com.unboundid.ldap.sdk.SearchResultEntry entry : searchResult.getSearchEntries()) {
                        String dn = entry.getDN();
                        
                        // Create LDIF modification for this entry
                        ldifContent.append("dn: ").append(dn).append("\n");
                        ldifContent.append("changetype: modify\n");
                        
                        if (modificationType.equals("ADD")) {
                            ldifContent.append("add: ").append(attribute).append("\n");
                            ldifContent.append(attribute).append(": ").append(value).append("\n");
                        } else if (modificationType.equals("DELETE")) {
                            ldifContent.append("delete: ").append(attribute).append("\n");
                            if (value != null && !value.isEmpty()) {
                                ldifContent.append(attribute).append(": ").append(value).append("\n");
                            }
                        } else if (modificationType.equals("REPLACE")) {
                            ldifContent.append("replace: ").append(attribute).append("\n");
                            ldifContent.append(attribute).append(": ").append(value).append("\n");
                        }
                        
                        ldifContent.append("-\n\n");
                        entryCount++;
                    }
                    
                    final int finalEntryCount = entryCount;
                    
                    // Update UI on JavaFX thread
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            ldifTextArea.setText(ldifContent.toString());
                            progressBar.setVisible(false);
                            
                            if (finalEntryCount == 0) {
                                statusLabel.setText("No entries found matching filter");
                                statusLabel.setStyle("-fx-text-fill: orange;");
                            } else {
                                statusLabel.setText("Created LDIF modifications for " + finalEntryCount + " entries");
                                statusLabel.setStyle("-fx-text-fill: green;");
                            }
                        }
                    });
                    
                } catch (Exception e) {
                    logger.error("Error creating LDIF modifications: {}", e.getMessage(), e);
                    
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setVisible(false);
                            statusLabel.setText("Error: " + e.getMessage());
                            statusLabel.setStyle("-fx-text-fill: red;");
                            GuiHelper.ERROR("Error", "Failed to create LDIF: " + e.getMessage());
                        }
                    });
                }
            }
        });
    }

    private void executeLdif() {
        final Connection selectedConnection = connectionChoiceBox.getSelectionModel().getSelectedItem();
        if (selectedConnection == null) {
            GuiHelper.ERROR("Connection Required", "Please select a connection");
            return;
        }

        final String ldifContent = ldifTextArea.getText();
        if (ldifContent == null || ldifContent.trim().isEmpty()) {
            GuiHelper.ERROR("LDIF Required", "Please enter LDIF content");
            return;
        }

        // Show confirmation dialog
        if (!GuiHelper.confirm("Execute LDIF", "Are you sure you want to execute this LDIF?", 
                "This operation will modify data on the LDAP server and may not be reversible.")) {
            return;
        }

        // Start execution in background
        progressBar.setVisible(true);
        statusLabel.setText("Executing LDIF modifications...");
        statusLabel.setStyle("-fx-text-fill: blue;");

        executorService.submit(new Runnable() {
            @Override
            public void run() {
                File tempFile = null;
                try {
                    // Create temp file with LDIF content
                    tempFile = File.createTempFile("ldap_editor_execute_", ".ldif");
                    try (FileWriter writer = new FileWriter(tempFile)) {
                        writer.write(ldifContent);
                    }

                    // Parse and execute LDIF
                    LDIFReader reader = new LDIFReader(tempFile);
                    int successCount = 0;
                    int errorCount = 0;
                    StringBuilder resultBuilder = new StringBuilder();

                    try {
                        LDIFChangeRecord record;
                        while ((record = reader.readChangeRecord()) != null) {
                            try {
                                // Execute the change
                                record.processChange(selectedConnection.get_ldapConnection());
                                successCount++;
                                
                                // Add to results
                                resultBuilder.append("Success: ")
                                        .append(record.getDN())
                                        .append(" (")
                                        .append(record.getChangeType())
                                        .append(")\n");
                                
                            } catch (LDAPException e) {
                                errorCount++;
                                resultBuilder.append("Error: ")
                                        .append(record.getDN())
                                        .append(" (")
                                        .append(record.getChangeType())
                                        .append("): ")
                                        .append(e.getMessage())
                                        .append("\n");
                                
                                logger.error("LDAP error executing change for DN {}: {}", 
                                        record.getDN(), e.getMessage(), e);
                            }
                        }
                    } finally {
                        reader.close();
                    }

                    // Update UI with results
                    final int finalSuccessCount = successCount;
                    final int finalErrorCount = errorCount;
                    final String results = resultBuilder.toString();
                    
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setVisible(false);
                            String resultMessage = String.format("Completed: %d successful, %d failed", 
                                    finalSuccessCount, finalErrorCount);
                            
                            statusLabel.setText(resultMessage);
                            if (finalErrorCount > 0) {
                                statusLabel.setStyle("-fx-text-fill: red;");
                                GuiHelper.ERROR_DETAILED("LDIF Execution Results", resultMessage, results);
                            } else {
                                statusLabel.setStyle("-fx-text-fill: green;");
                                GuiHelper.INFO("LDIF Execution Complete", resultMessage);
                            }
                        }
                    });
                    
                } catch (IOException e) {
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setVisible(false);
                            statusLabel.setText("I/O Error: " + e.getMessage());
                            statusLabel.setStyle("-fx-text-fill: red;");
                            GuiHelper.ERROR("I/O Error", e.getMessage());
                        }
                    });
                    logger.error("I/O error executing LDIF: {}", e.getMessage(), e);
                } catch (LDIFException e) {
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setVisible(false);
                            statusLabel.setText("Invalid LDIF: " + e.getMessage());
                            statusLabel.setStyle("-fx-text-fill: red;");
                            GuiHelper.ERROR("Invalid LDIF", e.getMessage());
                        }
                    });
                    logger.error("Invalid LDIF syntax: {}", e.getMessage(), e);
                } finally {
                    // Clean up the temporary file
                    if (tempFile != null && tempFile.exists()) {
                        try {
                            Files.delete(tempFile.toPath());
                        } catch (IOException e) {
                            logger.warn("Failed to delete temporary LDIF file: {}", e.getMessage());
                        }
                    }
                }
            }
        });
    }

    /**
     * Sets up auto-completion for the attribute ComboBox
     */
    private void setupAttributeAutoCompletion() {
        // Get the editor from the ComboBox
        TextField editor = attributeComboBox.getEditor();
        
        // Keep a reference to the original full list of items
        final ObservableList<String> originalItemsList = FXCollections.observableArrayList();
        
        // Add listener to the text property of the editor
        editor.textProperty().addListener((observable, oldValue, newValue) -> {
            // Save original items the first time we get them
            if (originalItemsList.isEmpty() && attributeComboBox.getItems() != null && !attributeComboBox.getItems().isEmpty()) {
                originalItemsList.setAll(attributeComboBox.getItems());
            }
            
            // If we have no items to filter, do nothing
            if (originalItemsList.isEmpty()) {
                return;
            }
            
            // If text is empty, restore all items
            if (newValue == null || newValue.isEmpty()) {
                attributeComboBox.setItems(originalItemsList);
                if (!attributeComboBox.isShowing() && attributeComboBox.isFocused()) {
                    attributeComboBox.show();
                }
                return;
            }
            
            // Filter the items based on the entered text (case-insensitive)
            String lowerCaseNewValue = newValue.toLowerCase();
            ObservableList<String> filteredItems = FXCollections.observableArrayList();
            
            // First add exact matches and then prefix matches
            for (String item : originalItemsList) {
                if (item.toLowerCase().equals(lowerCaseNewValue)) {
                    filteredItems.add(item);
                }
            }
            
            for (String item : originalItemsList) {
                if (item.toLowerCase().startsWith(lowerCaseNewValue) && 
                    !filteredItems.contains(item)) {
                    filteredItems.add(item);
                }
            }
            
            // Add contains matches (but not at beginning)
            for (String item : originalItemsList) {
                if (item.toLowerCase().contains(lowerCaseNewValue) && 
                    !item.toLowerCase().startsWith(lowerCaseNewValue) &&
                    !filteredItems.contains(item)) {
                    filteredItems.add(item);
                }
            }
            
            // Update the items and show the dropdown
            if (!filteredItems.isEmpty()) {
                attributeComboBox.setItems(filteredItems);
                
                // Show dropdown when we have matches and the field is focused
                if (!attributeComboBox.isShowing() && attributeComboBox.isFocused()) {
                    attributeComboBox.show();
                }
            } else {
                // If no matches, just restore the original list so user can browse
                attributeComboBox.setItems(originalItemsList);
                attributeComboBox.hide();
            }
        });
        
        // Add mouse click handler to show all items when clicking on the field
        editor.setOnMouseClicked(event -> {
            if (!originalItemsList.isEmpty()) {
                attributeComboBox.setItems(originalItemsList);
                if (!attributeComboBox.isShowing()) {
                    attributeComboBox.show();
                }
            }
        });
        
        // Add focus handler to show items when field gets focus
        editor.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal && !originalItemsList.isEmpty()) {
                // When focus is gained, show dropdown if there's text to filter, or show all if empty
                String currentText = editor.getText();
                if (currentText == null || currentText.isEmpty()) {
                    attributeComboBox.setItems(originalItemsList);
                    if (!attributeComboBox.isShowing()) {
                        attributeComboBox.show();
                    }
                }
            }
        });
        
        // Add key pressed event handler to handle Enter key and backspace
        editor.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case ENTER:
                    ObservableList<String> items = attributeComboBox.getItems();
                    if (items != null && !items.isEmpty()) {
                        // Select the first item if there are suggestions
                        String firstMatch = items.get(0);
                        editor.setText(firstMatch);
                        attributeComboBox.setValue(firstMatch);
                        attributeComboBox.hide();
                        
                        // Move focus to the next field (attributeValueArea)
                        attributeValueArea.requestFocus();
                        
                        // Consume the event to prevent it from propagating
                        event.consume();
                    }
                    break;
                    
                case DELETE:
                case BACK_SPACE:
                    // If the user is deleting content, ensure we'll show the dropdown again
                    if (!originalItemsList.isEmpty()) {
                        Platform.runLater(() -> {
                            String currentText = editor.getText();
                            if (currentText == null || currentText.isEmpty()) {
                                attributeComboBox.setItems(originalItemsList);
                                if (!attributeComboBox.isShowing() && attributeComboBox.isFocused()) {
                                    attributeComboBox.show();
                                }
                            }
                        });
                    }
                    break;
                    
                default:
                    break;
            }
        });
    }

    public void show() {
        if (stage == null) {
            logger.error("Stage is null, cannot show LDIF Editor");
            return;
        }
        
        // Reset UI elements each time the dialog is shown
        resetDialog();
        
        // Ensure stage properties are set correctly
        if (!stage.isShowing()) {
            stage.show();
        } else {
            // If already showing, bring to front
            stage.toFront();
            stage.requestFocus();
        }
    }
    
    /**
     * Resets the dialog UI elements to their default state for a fresh start
     */
    private void resetDialog() {
        // Clear LDIF text area
        ldifTextArea.clear();
        
        // Reset status message
        statusLabel.setText("Ready");
        statusLabel.setStyle("");
        
        // Hide progress bar
        progressBar.setVisible(false);
        
        // Make sure the cancel button works to properly close the window
        if (cancelButton.getOnAction() == null) {
            cancelButton.setOnAction(event -> {
                stage.close();
            });
        }
    }

    public void setMain(Main main) {
        this.main = main;
    }

    /**
     * Sets the LdapExploreController reference and initializes the connection
     * @param controller The LdapExploreController instance
     */
    public void setLdapExploreController(LdapExploreController controller) {
        this.ldapExploreController = controller;
        
        if (ldapExploreController != null) {
            // Get the current connection from the LdapExploreController
            Connection explorerConnection = ldapExploreController.get_currentConnection();
            
            if (explorerConnection != null) {
                // Set the current connection
                this.currentConnection = explorerConnection;
                
                // Set the connection in the choice box and disable it
                connectionChoiceBox.getSelectionModel().select(explorerConnection);
                connectionChoiceBox.setDisable(true);
                
                // Update the attribute combo box with schema attributes
                updateAttributeComboBox();
            } else {
                logger.error("No active LDAP connection in LdapExploreController");
                GuiHelper.ERROR("Connection Error", "No active LDAP connection available");
            }
        }
    }

    public void setWindow(Parent parent) {
        rootPane = (BorderPane) parent;
        scene = new Scene(rootPane);
        stage = new Stage();
        stage.setScene(scene);
        stage.setTitle("LDIF Editor");
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.DECORATED);
    }

    public void setOwner(Stage stage) {
        if (this.stage != null) {
            this.stage.initOwner(stage);
        }
    }

    /**
     * Sets the currently selected DN from the LDAP Explorer in the base DN field
     * 
     * @param selectedDN The selected DN from the LDAP Explorer
     */
    public void setSelectedDN(String selectedDN) {
        if (selectedDN != null && !selectedDN.isEmpty()) {
            baseDnField.setText(selectedDN);
            
            // Generate a basic filter to find this entry and all child entries
            ldapFilterField.setText("(objectClass=*)");
        }
    }
}
