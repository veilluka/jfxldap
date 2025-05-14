package ch.vilki.jfxldap.gui;

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
    }

    public void setMain(Main main) {
        this.main = main;
    }

    public void setLdapExploreController(LdapExploreController controller) {
        this.ldapExploreController = controller;
        // Use the application's observable list of connections
        ObservableList<Connection> connections = Main._main._ctManager._settingsController._connectionObservableList;
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
