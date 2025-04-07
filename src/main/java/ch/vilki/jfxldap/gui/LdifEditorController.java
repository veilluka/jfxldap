package ch.vilki.jfxldap.gui;

import ch.vilki.jfxldap.Main;
import ch.vilki.jfxldap.backend.Connection;
import com.unboundid.ldap.sdk.*;
import com.unboundid.ldif.LDIFChangeRecord;
import com.unboundid.ldif.LDIFException;
import com.unboundid.ldif.LDIFReader;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Controller for the LDIF editor which allows users to write and execute LDIF operations
 * on a selected LDAP connection.
 */
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

    private Stage stage;
    private Scene scene;
    private Main main; // Used in setMain method, required by ILoader interface

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    /**
     * Initializes the controller after FXML loading.
     */
    @FXML
    public void initialize() {
        // Set up the connection choice box
        connectionChoiceBox.setTooltip(new Tooltip("Select LDAP connection"));

        // Bind execute button to connection selection
        executeButton.disableProperty().bind(
                connectionChoiceBox.getSelectionModel().selectedItemProperty().isNull());

        // Set up syntax highlighting and validation for the LDIF text area
        configureLdifTextArea();

        // Set up button actions
        setupButtonActions();
    }

    private void configureLdifTextArea() {
        // Add key event handler for syntax highlighting
        ldifTextArea.addEventFilter(KeyEvent.KEY_RELEASED, event -> {
            validateLdifSyntax();
        });

        // Set default placeholder text
        ldifTextArea.setPromptText("# Enter LDIF content here\n" +
                "# Example:\n" +
                "dn: cn=example,dc=example,dc=com\n" +
                "changetype: add\n" +
                "objectClass: top\n" +
                "objectClass: person\n" +
                "cn: example\n" +
                "sn: User\n" +
                "description: Example LDIF entry");
    }

    private void setupButtonActions() {
        executeButton.setOnAction(event -> executeLdif());
        
        cancelButton.setOnAction(event -> {
            stage.close();
        });
    }

    /**
     * Validates LDIF syntax in the text area and updates UI feedback.
     */
    private void validateLdifSyntax() {
        String ldifContent = ldifTextArea.getText();
        if (ldifContent == null || ldifContent.trim().isEmpty()) {
            statusLabel.setText("Enter LDIF content");
            statusLabel.setStyle("-fx-text-fill: gray;");
            return;
        }

        // Validate basic LDIF format (must start with "dn: " and have proper syntax)
        if (!ldifContent.trim().toLowerCase().startsWith("dn:")) {
            statusLabel.setText("Invalid LDIF: Must start with 'dn:'");
            statusLabel.setStyle("-fx-text-fill: red;");
            return;
        }

        try {
            // Use temporary file since the UnboundID reader doesn't have a StringReader constructor
            File tempFile = null;
            try {
                tempFile = File.createTempFile("ldap_editor_validate_", ".ldif");
                try (FileWriter writer = new FileWriter(tempFile)) {
                    writer.write(ldifContent);
                }
                
                // Try to parse the LDIF content
                LDIFReader reader = new LDIFReader(tempFile);
                
                try {
                    // Try to actually read and parse all records
                    boolean hasRecords = false;
                    LDIFChangeRecord record;
                    while ((record = reader.readChangeRecord()) != null) {
                        hasRecords = true;
                        
                        // Basic validation
                        if (record.getDN() == null || record.getDN().isEmpty()) {
                            statusLabel.setText("Invalid LDIF: Missing DN in record");
                            statusLabel.setStyle("-fx-text-fill: red;");
                            return;
                        }
                        
                        // Validate change type
                        ChangeType changeType = record.getChangeType();
                        if (changeType == null) {
                            statusLabel.setText("Invalid LDIF: Missing changetype");
                            statusLabel.setStyle("-fx-text-fill: red;");
                            return;
                        }
                    }
                    
                    if (!hasRecords) {
                        statusLabel.setText("Warning: No LDIF records found");
                        statusLabel.setStyle("-fx-text-fill: orange;");
                    } else {
                        statusLabel.setText("Valid LDIF syntax");
                        statusLabel.setStyle("-fx-text-fill: green;");
                    }
                } finally {
                    reader.close();
                }
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
        } catch (IOException e) {
            statusLabel.setText("I/O Error: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: red;");
            logger.error("I/O error validating LDIF: {}", e.getMessage(), e);
        } catch (LDIFException e) {
            statusLabel.setText("Invalid LDIF: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: red;");
            logger.error("Invalid LDIF syntax: {}", e.getMessage(), e);
        }
    }

    /**
     * Executes the LDIF content against the selected connection.
     */
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

        // Disable UI during execution
        setOperationInProgress(true);
        
        // Execute LDIF in background
        executorService.submit(() -> {
            File tempFile = null;
            try {
                // Create a temporary file for LDIF execution
                tempFile = File.createTempFile("ldap_editor_execute_", ".ldif");
                try (FileWriter writer = new FileWriter(tempFile)) {
                    writer.write(ldifContent);
                }
                
                // Check if connection is established
                LDAPConnection ldapConnection = null;
                if (selectedConnection.get_ldapConnection() != null) {
                    ldapConnection = selectedConnection.get_ldapConnection();
                } else {
                    // Need to establish connection first
                    Platform.runLater(() -> {
                        GuiHelper.INFO("Connection Required", "Connecting to " + selectedConnection.getName());
                    });
                    
                    try {
                        selectedConnection.connect();
                        ldapConnection = selectedConnection.get_ldapConnection();
                    } catch (GeneralSecurityException e) {
                        throw new LDAPException(ResultCode.LOCAL_ERROR, "Security error during connection: " + e.getMessage(), e);
                    }
                }
                
                // Process the LDIF
                LDIFReader reader = new LDIFReader(tempFile);
                LDIFChangeRecord changeRecord;
                int successCount = 0;
                int totalCount = 0;
                
                StringBuilder resultBuilder = new StringBuilder();
                
                try {
                    while ((changeRecord = reader.readChangeRecord()) != null) {
                        totalCount++;
                        try {
                            // Process the change and ignore the result
                            changeRecord.processChange(ldapConnection);
                            successCount++;
                            resultBuilder.append("SUCCESS: ")
                                    .append(changeRecord.getDN())
                                    .append(" (")
                                    .append(changeRecord.getChangeType())
                                    .append(")\n");
                        } catch (LDAPException e) {
                            resultBuilder.append("ERROR: ")
                                    .append(changeRecord.getDN())
                                    .append(" (")
                                    .append(changeRecord.getChangeType())
                                    .append("): ")
                                    .append(e.getMessage())
                                    .append("\n");
                            logger.error("Error processing LDIF change: {}", e.getMessage(), e);
                        }
                        
                        // Update progress UI
                        final double progress = (double) totalCount / Math.max(1, totalCount + 1);
                        Platform.runLater(() -> progressBar.setProgress(progress));
                    }
                } finally {
                    reader.close();
                }
                
                // Display results
                final int failureCount = totalCount - successCount;
                final String resultSummary = "Completed: " + successCount + " successful, " + 
                        failureCount + " failed\n\n" + resultBuilder.toString();
                
                Platform.runLater(() -> {
                    if (failureCount > 0) {
                        GuiHelper.ERROR("LDIF Execution Results", resultSummary);
                    } else {
                        GuiHelper.INFO("LDIF Execution Complete", resultSummary);
                    }
                    setOperationInProgress(false);
                });
                
            } catch (LDAPException | IOException | LDIFException e) {
                logger.error("Error executing LDIF: {}", e.getMessage(), e);
                Platform.runLater(() -> {
                    GuiHelper.EXCEPTION("LDIF Execution Error", "Failed to execute LDIF", e);
                    setOperationInProgress(false);
                });
            } finally {
                // Clean up the temporary file
                if (tempFile != null && tempFile.exists()) {
                    tempFile.delete();
                }
            }
        });
    }
    
    private void setOperationInProgress(boolean inProgress) {
        executeButton.setDisable(inProgress);
        ldifTextArea.setDisable(inProgress);
        connectionChoiceBox.setDisable(inProgress);
        progressBar.setVisible(inProgress);
        
        if (!inProgress) {
            progressBar.setProgress(0);
        }
    }

    /**
     * Shows the LDIF editor dialog.
     */
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
