package ch.vilki.jfxldap.gui;

import ch.vilki.jfxldap.backend.UnboundidLdapSearch;
import com.unboundid.ldap.sdk.LDAPException;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ch.vilki.jfxldap.backend.Connection;
import ch.vilki.jfxldap.Main;
import ch.vilki.jfxldap.gui.LdapExploreController;
import ch.vilki.jfxldap.gui.GuiHelper;

import java.security.GeneralSecurityException;

import static ch.vilki.jfxldap.Main._main;

public class AddLdifEntriesController implements ILoader {
    private static final Logger logger = LogManager.getLogger(AddLdifEntriesController.class);

    @FXML private BorderPane rootPane;
    @FXML private ChoiceBox<Connection> connectionChoiceBox;
    @FXML private TextArea ldifTextArea;
    @FXML private Label statusLabel;
    @FXML private ProgressBar progressBar;
    @FXML private Button executeButton;
    @FXML private Button cancelButton;

    private Scene scene;
    private Stage stage;
    private Main main;
    private LdapExploreController ldapExploreController;
    private Connection currentConnection;

    @FXML
    public void initialize() {
        // Set up cancel button
        cancelButton.setOnAction(event -> {
            if (stage != null) stage.close();
        });
        progressBar.setVisible(false);
        statusLabel.setText("Ready");
        executeButton.setOnAction(this::onExecute);

        // Live LDIF validation
        ldifTextArea.textProperty().addListener((obs, oldText, newText) -> validateForm());
        connectionChoiceBox.getSelectionModel().selectedItemProperty().addListener((obs, oldConn, newConn) -> validateForm());
        validateForm();
    }

    private void validateForm() {
        // Check connection
        Connection selectedConnection = connectionChoiceBox.getSelectionModel().getSelectedItem();
        if (selectedConnection == null) {
            executeButton.setDisable(true);
            statusLabel.setText("Please select a connection");
            statusLabel.setStyle("-fx-text-fill: red;");
            return;
        }
        String ldifContent = ldifTextArea.getText();
        if (ldifContent == null || ldifContent.trim().isEmpty()) {
            executeButton.setDisable(true);
            statusLabel.setText("Enter LDIF content");
            statusLabel.setStyle("");
            return;
        }
        // Try parsing the LDIF
        try (com.unboundid.ldif.LDIFReader reader = new com.unboundid.ldif.LDIFReader(new java.io.BufferedReader(new java.io.StringReader(ldifContent)))) {
            while (reader.readEntry() != null) {
                // Just parse for validity
            }
            executeButton.setDisable(false);
            statusLabel.setText("Ready");
            statusLabel.setStyle("-fx-text-fill: green;");
        } catch (Exception e) {
            executeButton.setDisable(true);
            statusLabel.setText("Invalid LDIF: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: red;");
        }
    }

    public void setMain(Main main) {
        this.main = main;
    }

    public void setLdapExploreController(LdapExploreController controller) {
        this.ldapExploreController = controller;
        // Use the application's observable list of connections
        ObservableList<Connection> connections = _main._ctManager._settingsController._connectionObservableList;
        connectionChoiceBox.setItems(connections);
        if (!connections.isEmpty()) {
            connectionChoiceBox.getSelectionModel().selectFirst();
            currentConnection = connectionChoiceBox.getSelectionModel().getSelectedItem();
        }
        connectionChoiceBox.getSelectionModel().selectedItemProperty().addListener((obs, oldConn, newConn) -> {
            currentConnection = newConn;
        });
    }

    public void setWindow(Parent parent) {
        rootPane = (BorderPane) parent;
        scene = new Scene(rootPane);
        stage = new Stage();
        stage.setScene(scene);
        stage.setTitle("Add LDIF Entries");
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.DECORATED);
    }

    public void setOwner(Stage stage) {
        if (this.stage != null) {
            this.stage.initOwner(stage);
        }
    }

    public void show() {
        if (stage == null) {
            logger.error("Stage is null, cannot show Add LDIF Entries window");
            return;
        }
        if (!stage.isShowing()) {
            stage.show();
        } else {
            stage.toFront();
            stage.requestFocus();
        }
    }

    private void onExecute(ActionEvent event) {
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
        if (!GuiHelper.confirm("Execute LDIF", "Are you sure you want to execute this LDIF?", "This operation will create entries on the LDAP server.")) {
            return;
        }
        progressBar.setVisible(true);
        statusLabel.setText("Executing LDIF entry creation...");
        statusLabel.setStyle("-fx-text-fill: blue;");
        new Thread(() -> {
            int successCount = 0;
            int errorCount = 0;
            StringBuilder resultBuilder = new StringBuilder();
            try {
                // Ensure connection is established using the application's CollectionsController logic
                try {
                    // Initiate connection on UI thread
                    Platform.runLater(() -> {
                        selectedConnection.setPassword(_main._configuration.getConnectionPassword(selectedConnection).toString());

                        try {
                            selectedConnection.connect();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }

                    });
                    // Wait up to 10 seconds for connection to be ready
                    int waited = 0;
                    while (selectedConnection.get_ldapConnection() == null && waited < 10000) {
                        Thread.sleep(100);
                        waited += 100;
                    }

                    if (selectedConnection.get_ldapConnection() == null) {
                        Platform.runLater(() -> {
                            progressBar.setVisible(false);
                            statusLabel.setText("Failed to connect: Timeout waiting for connection.");
                            statusLabel.setStyle("-fx-text-fill: red;");
                            GuiHelper.ERROR("Connection Error", "Timeout waiting for connection to be established.");
                        });
                        return;
                    }
                } catch (Exception ce) {
                    Platform.runLater(() -> {
                        progressBar.setVisible(false);
                        statusLabel.setText("Failed to connect: " + ce.getMessage());
                        statusLabel.setStyle("-fx-text-fill: red;");
                        GuiHelper.ERROR("Connection Error", ce.getMessage());
                    });
                    return;
                }
                try (com.unboundid.ldif.LDIFReader reader = new com.unboundid.ldif.LDIFReader(new java.io.BufferedReader(new java.io.StringReader(ldifContent)))) {
                    com.unboundid.ldap.sdk.Entry entry;
                    while ((entry = reader.readEntry()) != null) {
                        try {
                            selectedConnection.get_ldapConnection().add(entry);
                            successCount++;
                            resultBuilder.append("Success: ").append(entry.getDN()).append("\n");
                        } catch (Exception e) {
                            errorCount++;
                            resultBuilder.append("Error: ").append(entry.getDN()).append(": ").append(e.getMessage()).append("\n");
                        }
                    }
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    statusLabel.setText("Invalid LDIF: " + e.getMessage());
                    statusLabel.setStyle("-fx-text-fill: red;");
                    GuiHelper.ERROR("LDIF Error", e.getMessage());
                });
                return;
            }
            final int finalSuccessCount = successCount;
            final int finalErrorCount = errorCount;
            final String results = resultBuilder.toString();
            Platform.runLater(() -> {
                progressBar.setVisible(false);
                String resultMessage = String.format("Completed: %d successful, %d failed", finalSuccessCount, finalErrorCount);
                statusLabel.setText(resultMessage);
                if (finalErrorCount > 0) {
                    statusLabel.setStyle("-fx-text-fill: red;");
                    GuiHelper.ERROR_DETAILED("LDIF Execution Results", resultMessage, results);
                } else {
                    statusLabel.setStyle("-fx-text-fill: green;");
                    GuiHelper.INFO("LDIF Entry Creation Complete", resultMessage);
                }
            });
        }).start();
    }




}
