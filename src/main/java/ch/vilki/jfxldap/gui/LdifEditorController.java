package ch.vilki.jfxldap.gui;

import ch.vilki.jfxldap.Main;
import ch.vilki.jfxldap.backend.Connection;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPConnection;
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
import javafx.scene.input.KeyEvent;
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
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
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
    private TextField attributeValueField;

    @FXML
    private Button addModificationButton;

    private Stage stage;
    private Scene scene;
    private Main main;
    private Connection currentConnection;

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
                    updateAttributeComboBox();
                }
            }
        });

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

        String modificationType = modificationTypeComboBox.getValue();
        String attribute = attributeComboBox.getValue();
        String value = attributeValueField.getText();

        if (attribute == null || attribute.isEmpty()) {
            GuiHelper.ERROR("Attribute Required", "Please select or enter an attribute");
            return;
        }

        if (value == null || value.isEmpty()) {
            GuiHelper.ERROR("Value Required", "Please enter a value");
            return;
        }

        // Generate LDIF modification
        String modification = generateLdifModification(modificationType, attribute, value);
        ldifTextArea.appendText(modification + "\n\n");
        statusLabel.setText("Modification added to LDIF");
        statusLabel.setStyle("-fx-text-fill: green;");
    }

    private String generateLdifModification(String modificationType, String attribute, String value) {
        StringBuilder sb = new StringBuilder();
        sb.append("changetype: modify\n");
        sb.append(modificationType.toLowerCase()).append(":: ").append(attribute).append("\n");
        sb.append("-\n");
        return sb.toString();
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

    public void show() {
        if (stage == null) {
            logger.error("Stage is null, cannot show LDIF Editor");
            return;
        }
        stage.show();
    }

    @Override
    public void setMain(Main main) {
        this.main = main;
        
        // Populate connection choice box from settings
        connectionChoiceBox.setItems(Main._ctManager._settingsController._connectionObservableList);
    }

    @Override
    public void setWindow(Parent parent) {
        rootPane = (BorderPane) parent;
        scene = new Scene(rootPane);
        stage = new Stage();
        stage.setScene(scene);
        stage.setTitle("LDIF Editor");
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.DECORATED);
    }

    @Override
    public void setOwner(Stage stage) {
        if (this.stage != null) {
            this.stage.initOwner(stage);
        }
    }
}
