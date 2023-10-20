package ch.vilki.jfxldap.gui;

import ch.vilki.jfxldap.Main;
import ch.vilki.jfxldap.backend.Config;
import ch.vilki.jfxldap.backend.Connection;
import ch.vilki.secured.SecureStorageException;
import ch.vilki.secured.SecureString;
import com.unboundid.ldap.sdk.LDAPException;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.text.Text;
import javafx.stage.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

public class SettingsController implements ILoader {
    static Logger logger = LogManager.getLogger(SettingsController.class);


    Scene _scene;
    Stage _stage;

    public TabPane get_settingsWindow() {
        return _settingsWindow;
    }

    TabPane _settingsWindow;
    public Scene get_scene() {
        return _scene;
    }
    public void set_scene(Scene _scene) {
        this._scene = _scene;
    }
    public Stage get_stage() {
        return _stage;
    }
    private Main _main;

    @FXML TableView _tableViewConnections;
    @FXML TableColumn<Connection, String> _tableColumnName;
    @FXML TableColumn<Connection, String> _tableColumnTag;
    @FXML TableColumn<Connection, String> _tableColumnServer;
    @FXML TableColumn<Connection, String> _tableColumnPort;
    @FXML TableColumn<Connection, String> _tableColumnUser;
    @FXML TableColumn<Connection, String> _tableColumnPassword;
    @FXML TableColumn<Connection, String> _tableColumnBaseDN;

    @FXML TextField _textFieldConnectionName;
    @FXML TextField _textFieldTAG;
    @FXML TextField _textFieldServer;
    @FXML TextField _textFieldPort;
    @FXML TextField _textFieldUser;
    @FXML TextField _textFieldBaseDN;
    @FXML PasswordField _passwordFieldConnection;
    @FXML TextField _textFieldTempDirectoryPath;
    @FXML TextField _textFieldBeyondCompareExe;
    @FXML TextField _textFieldDisplayRDN;
    @FXML TextField _textFieldKeystoreFile;
    @FXML TextField _textFieldVisualCode;


    @FXML Button _buttonSelectTempDirectory;
    @FXML Button _buttonSelectBeyondCompare;
    @FXML Button _buttonSelectKeyStore;
    @FXML Button _buttonCreateKeyStore;
    @FXML Button _buttonSelectVisualCode;



    @FXML Button _buttonAddNewConnection;
    @FXML Button _buttonDeleteConnection;
    @FXML Button _buttonSaveConnections;
    @FXML Button _buttonShowPassword;
    @FXML Button _buttonCopyClipboard;
    @FXML Button _buttonGetBasisDN;
    @FXML Button _buttonExportConnections;
    @FXML Button _buttonGetCertificatesFromServer;

    @FXML CheckBox _checkBoxJNDIReader;
    @FXML CheckBox _checkBoxReadOnly;
    @FXML CheckBox _checkBoxUseSSL;

    @FXML TabPane _tabPane;
    Tab _keyStoreTab = null;
    @FXML ObservableList<Connection> _connectionObservableList = FXCollections.observableArrayList();
    @FXML ObservableList<String> _tagsObservableList = FXCollections.observableArrayList();

    @FXML
    private void initialize() {

        initControls();
        initButtons();
        initTableConnections();
        _keyStoreTab = new Tab("Key-Store");
    }

    public void setMain(Main mainApp) {
        _main = mainApp;
    }

    public void showWindow() {
        _stage.showAndWait();
    }

    public void addKeyStoreTab() {
        if (_tabPane.getTabs().contains(_keyStoreTab)) return;
        if (_keyStoreTab.getContent() == null) {
            _keyStoreTab.setContent(_main._ctManager.get_keyStoreController()._keyStoreWindow);
        }
        _tabPane.getTabs().add(_keyStoreTab);
    }

    public void removeKeyStoreTab() {
        if (!_tabPane.getTabs().contains(_keyStoreTab)) return;
        _tabPane.getTabs().remove(_keyStoreTab);
    }

    @Override
    public void setWindow(Parent parent) {
        _settingsWindow = (TabPane) parent;
        _scene = new Scene(_settingsWindow);
        _stage = new Stage();
        _stage.setScene(_scene);
        _stage.setTitle("Settings");
        _stage.initStyle(StageStyle.DECORATED);
        _stage.initModality(Modality.NONE);
        _stage.onCloseRequestProperty().addListener((observable, oldValue, newValue) -> {
            if (_main == null || _main._ctManager._keyStoreController == null) return;
            _main._ctManager._keyStoreController.cleanUp();
        });
        _stage.onShowingProperty().addListener((observable, oldValue, newValue) -> {
            if (_main._configuration.get_keyStore() == null) removeKeyStoreTab();
            else addKeyStoreTab();
            autoResizeColumns(_tableViewConnections);
            _main._ctManager._keyStoreController.initWindow();
        });
        _tableColumnUser.setCellFactory(tc -> {
            TableCell<Connection, String> cell = new TableCell<>();
            Text text = new Text();
            cell.setGraphic(text);
            cell.setPrefHeight(Control.USE_COMPUTED_SIZE);
            text.wrappingWidthProperty().bind(tc.widthProperty());
            text.textProperty().bind(cell.itemProperty());
            return cell;
        });
        _tableViewConnections.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        _tableColumnName.setPrefWidth(1f * Integer.MAX_VALUE * 15);
        _tableColumnServer.setPrefWidth(1f * Integer.MAX_VALUE * 10);
        _tableColumnPort.setPrefWidth(1f * Integer.MAX_VALUE * 5);
        _tableColumnUser.setPrefWidth(1f * Integer.MAX_VALUE * 30);
        _tableColumnBaseDN.setPrefWidth(1f * Integer.MAX_VALUE * 30);
        _tableColumnPassword.setPrefWidth(1f * Integer.MAX_VALUE * 10);

        _tableColumnUser.resizableProperty().addListener(x -> {
            autoResizeColumns(_tableViewConnections);
        });

        this.get_stage().setOnCloseRequest(x -> {
            initButtonsState();
        });
    }

    private void initButtonsState() {
        _buttonSaveConnections.setDisable(true);
        _textFieldConnectionName.setDisable(true);
        _textFieldServer.setDisable(true);
        _textFieldPort.setDisable(true);
        _textFieldUser.setDisable(true);
        _textFieldDisplayRDN.setDisable(true);
        _passwordFieldConnection.setDisable(true);
        _buttonAddNewConnection.setDisable(false);
        _textFieldBaseDN.setDisable(true);
        _buttonExportConnections.setDisable(false);
    }

    public static void autoResizeColumns(TableView<?> table) {
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.getColumns().stream().forEach((column) ->
        {
            //Minimal width = columnheader
            Text t = new Text(column.getText());
            double max = t.getLayoutBounds().getWidth();
            for (int i = 0; i < table.getItems().size(); i++) {
                //cell must not be empty
                if (column.getCellData(i) != null) {
                    t = new Text(column.getCellData(i).toString());
                    double calcwidth = t.getLayoutBounds().getWidth();
                    //remember new max-width
                    if (calcwidth > max) {
                        max = calcwidth;
                    }
                }
            }
            //set the new max-widht with some extra space
            column.setPrefWidth(max + 10.0d);
        });
    }

    @Override
    public void setOwner(Stage stage) {
        _stage.initOwner(stage);
    }

    private void initControls() {
        _textFieldConnectionName.setDisable(true);
        _textFieldServer.setDisable(true);
        _textFieldPort.setDisable(true);
        _textFieldUser.setDisable(true);
        _textFieldDisplayRDN.setDisable(true);
        _textFieldBaseDN.setDisable(true);
        _passwordFieldConnection.setDisable(true);
        _buttonSaveConnections.setDisable(true);
        _buttonDeleteConnection.setDisable(true);
        _buttonGetCertificatesFromServer.setDisable(true);
    }

    private void initButtons() {
        _buttonGetBasisDN.setOnAction(x -> getBaseDN());
        _buttonSelectBeyondCompare.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Beyond Compare EXE select");
            File defaultDirectory = new File("c://");
            chooser.setInitialDirectory(defaultDirectory);
            File selectedFile = chooser.showOpenDialog(_main.get_primaryStage());
            _textFieldBeyondCompareExe.setText(selectedFile.getAbsolutePath());
            _main._configuration.set_beyondCompareExe(selectedFile.getAbsolutePath());
        });

        _buttonSelectVisualCode.setOnAction(event ->{
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Visula Code select");
            File defaultDirectory = new File("c://");
            chooser.setInitialDirectory(defaultDirectory);
            File selectedFile = chooser.showOpenDialog(_main.get_primaryStage());
            _textFieldVisualCode.setText(selectedFile.getAbsolutePath());
            _main._configuration.set_visualCodeExe(selectedFile.getAbsolutePath());
        });

        _buttonSelectTempDirectory.setOnAction(x -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("JavaFX Projects");
            File defaultDirectory = new File("c://");
            chooser.setInitialDirectory(defaultDirectory);
            File selectedDirectory = chooser.showDialog(_main.get_primaryStage());
            _textFieldTempDirectoryPath.setText(selectedDirectory.getAbsolutePath());
            _main._configuration.set_tempDir(selectedDirectory.getAbsolutePath());
        });

        _buttonSelectKeyStore.setOnAction(x -> {
            FileChooser.ExtensionFilter filter = new FileChooser.ExtensionFilter("jks files (*.jks)", "*.jks");
            File file = GuiHelper.selectFile(_main, null, "Enter fileName", GuiHelper.FILE_OPTIONS.OPEN_FILE);
            if (file == null) return;
            String pass = GuiHelper.enterPassword("Enter keystore password", "Enter password for keystore,it will be stored in secured storage");
            try {
                _main._configuration.readKeyStoreFile(new SecureString(pass));
                _textFieldKeystoreFile.setText(file.getAbsolutePath());
                _main._configuration.set_keyStoreFile(file.getAbsolutePath());
                _main._configuration.set_keyStorePassword(new SecureString(pass));
                _main._ctManager._keyStoreController.cleanUp();
                _main._ctManager._keyStoreController.initWindow();
            } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException |
                     SecureStorageException e) {
                logger.error("Exception in opening keystore file", e);
                GuiHelper.EXCEPTION("Error occured during opening", e.getLocalizedMessage(), e);
            }
        });

        _buttonCreateKeyStore.setOnAction(x -> {
            FileChooser.ExtensionFilter filter = new FileChooser.ExtensionFilter("jks files (*.jks)", "*.jks");
            File file = GuiHelper.selectFile(_main, filter, "Enter fileName", GuiHelper.FILE_OPTIONS.SAVE_AS);
            if (file == null) return;
            String pass = GuiHelper.enterPassword("Define password", "Enter password for keystore,it will be stored in secured storage");
            if (pass == null) return;
            try {
                _main._configuration.createKeyStoreFile(new SecureString(pass), file);
                _main._configuration.set_keyStoreFile(file.getAbsolutePath());
                _main._configuration.set_keyStorePassword(new SecureString(pass));
                _textFieldKeystoreFile.setText(file.getAbsolutePath());
                _main._ctManager._keyStoreController.cleanUp();
                _main._ctManager._keyStoreController.initWindow();
            } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
                logger.error("Exception in creating keystore file", e);
                GuiHelper.EXCEPTION("Error occured during creation", e.getLocalizedMessage(), e);
            }
        });
        _buttonSaveConnections.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                if (_textFieldConnectionName.getText() == null || _textFieldConnectionName.getText().equalsIgnoreCase("")) {
                    GuiHelper.ERROR("Modify Error", "Connection name is not set!");
                    return;
                }
                try {
                    Integer.parseInt(_textFieldPort.getText());
                } catch (Exception e) {
                    GuiHelper.ERROR("Parsing Error", "Port number is not an Integer!");
                    return;
                }
                Connection c = _main._configuration.getConnection(_textFieldConnectionName.getText());
                if (_passwordFieldConnection.getText() != null) {
                    if (!_main._configuration.is_configSecured()) {
                        if (!GuiHelper.confirm("Config not secured", "Store password in plain text?",
                                "Your configuration is not secured and you have set password in your connection. This means " +
                                        "that the password will be stored in plain text!")) return;

                        else {
                            //TODO define master password
                        }
                    }
                }
                updateConnection(_textFieldConnectionName.getText());
                _buttonSaveConnections.setDisable(true);
                _textFieldConnectionName.setDisable(true);
                _textFieldServer.setDisable(true);
                _textFieldPort.setDisable(true);
                _textFieldUser.setDisable(true);
                _textFieldDisplayRDN.setDisable(true);
                _passwordFieldConnection.setDisable(true);
                _buttonAddNewConnection.setDisable(false);
                _textFieldBaseDN.setDisable(true);
            }
        });


        _buttonDeleteConnection.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {

                Object selected = _tableViewConnections.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    Connection c = (Connection) selected;
                    try {
                        deleteConnection(c);
                    } catch (IOException e) {
                        GuiHelper.EXCEPTION("Error deleting connection",e.getMessage(),e);
                        return;
                    }
                }
                if (!_connectionObservableList.isEmpty()) {
                    _tableViewConnections.getSelectionModel().select(0);
                } else {
                    _buttonDeleteConnection.setDisable(true);
                    _buttonSaveConnections.setDisable(false);
                    _buttonGetCertificatesFromServer.setDisable(true);
                }
                _textFieldConnectionName.setDisable(false);
                _textFieldTAG.setDisable(false);
                _textFieldServer.setDisable(false);
                _textFieldPort.setDisable(false);
                _textFieldBaseDN.setDisable(false);
                _textFieldUser.setDisable(false);
                _textFieldDisplayRDN.setDisable(false);
                _passwordFieldConnection.setDisable(false);
                _textFieldConnectionName.clear();
                _textFieldServer.clear();
                _textFieldPort.clear();
                _textFieldUser.clear();
                _textFieldDisplayRDN.clear();
                _textFieldBaseDN.clear();
                _passwordFieldConnection.clear();
            }
        });

        _buttonAddNewConnection.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                _textFieldConnectionName.setDisable(false);
                _textFieldTAG.setDisable(false);
                _textFieldServer.setDisable(false);
                _textFieldPort.setDisable(false);
                _textFieldUser.setDisable(false);
                _textFieldBaseDN.setDisable(false);
                _textFieldDisplayRDN.setDisable(false);
                _passwordFieldConnection.setDisable(false);
                _textFieldConnectionName.clear();
                _textFieldServer.clear();
                _textFieldPort.clear();
                _textFieldUser.clear();
                _textFieldDisplayRDN.clear();
                _textFieldBaseDN.clear();
                _passwordFieldConnection.clear();
                _buttonDeleteConnection.setDisable(true);
                _buttonGetCertificatesFromServer.setDisable(true);
                _buttonAddNewConnection.setDisable(true);
                _buttonSaveConnections.setDisable(false);
                _buttonExportConnections.setDisable(true);
            }
        });

        _buttonShowPassword.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                Connection selected = (Connection) _tableViewConnections.getSelectionModel().getSelectedItem();
                if (selected == null) GuiHelper.ERROR("Failed", "Select connection first");
                SecureString pass = _main._configuration.getConnectionPassword(selected);
                if (pass != null) {
                    GuiHelper.INFO("PASSWORD", pass.toString());
                    pass.destroyValue();
                }

                return;
            }
        });

        _buttonCopyClipboard.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                Connection selected = (Connection) _tableViewConnections.getSelectionModel().getSelectedItem();
                if (selected == null) GuiHelper.ERROR("Failed", "Select connection first");
                SecureString pass = _main._configuration.getConnectionPassword(selected);
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                        new StringSelection(pass.toString()), null
                );
                pass.destroyValue();

            }
        });

        _buttonGetCertificatesFromServer.setOnAction(x -> {
            if (_textFieldKeystoreFile.getText().isEmpty()) {
                GuiHelper.ERROR("No keystore", "Select or create keystore first in general settings");
                return;
            }
            Connection selected = (Connection) _tableViewConnections.getSelectionModel().getSelectedItem();
            if (selected == null) GuiHelper.ERROR("Failed", "Select connection first");
            SecureString pass = _main._configuration.getConnectionPassword(selected);

            Connection connection = new Connection(_textFieldConnectionName.getText(),
                    _textFieldServer.getText(), _textFieldPort.getText(), _textFieldUser.getText(), pass.toString(),
                    "","", null);

            Set<X509Certificate> certificates = null;
            try {
                certificates = connection.getCertificates();
            } catch (GeneralSecurityException | IOException | LDAPException e) {
                logger.error("Exception during getting certificate from server", e);
                GuiHelper.EXCEPTION("Error loading certificate from server", e.getMessage(), e);
                return;
            }
            if (certificates == null || certificates.isEmpty()) {
                GuiHelper.INFO("Read Certificate", "Did not find any certificate on server");
                return;
            }
            Map<String, X509Certificate> foundCertificates = new HashMap<>();
            List<byte[]> allreadyHasCertificate = new ArrayList<>();

            try {
                for (Enumeration<String> e = _main._configuration.get_keyStore().aliases(); e.hasMoreElements(); ) {
                    String name = e.nextElement();
                    X509Certificate c = (X509Certificate) _main._configuration.get_keyStore().getCertificate(name);
                    allreadyHasCertificate.add(c.getSignature());
                }
            } catch (KeyStoreException e) {
                logger.error("Error reading certificates", e);
            }

            for (X509Certificate certificate : certificates) {
                byte[] sig = certificate.getSignature();
                boolean found = false;
                for (byte[] cer : allreadyHasCertificate) {
                    if (Arrays.equals(cer, sig)) found = true;
                }
                if (!found)
                    foundCertificates.put(certificate.getSubjectX500Principal().getName(), certificate);
            }
            if (foundCertificates.isEmpty()) {
                GuiHelper.INFO("Certificate search", "Found certificates on server, but you have " +
                        "them allready in the key-store");
                return;
            }

            _main._ctManager._keyStoreController.setCertificatesToBeAdded(foundCertificates);
            _main._ctManager._keyStoreController._buttonClose.setVisible(true);
            _tabPane.getSelectionModel().select(_keyStoreTab);
            Main._ctManager._keyStoreController.showWindow();

        });
        _buttonExportConnections.setOnAction(x -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Export CSV File");
            File defaultDirectory = new File(_main._configuration.get_lastUsedDirectory());
            chooser.setInitialDirectory(defaultDirectory);
            FileChooser.ExtensionFilter csvFilter = new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv");
            chooser.getExtensionFilters().add(csvFilter);
            File selectedFile = chooser.showSaveDialog(_main.get_primaryStage());
            if (selectedFile == null) return;
            try {
                exportConnectionsAsCSV(selectedFile, true);
            } catch (IOException e) {
                GuiHelper.EXCEPTION("Export failed", e.getMessage(), e);
                return;
            }
            GuiHelper.INFO("Export succeded", "Connections exported in to->" + selectedFile.getAbsolutePath());

        });
    }

    private void deleteConnection(Connection connection) throws IOException {
        if (_connectionObservableList.isEmpty()) return;
        _main._configuration.deleteConnection(connection.getName());
        for (Connection c : _connectionObservableList) {
            if (c.getName().equalsIgnoreCase(connection.getName())) {
                _connectionObservableList.remove(c);
                _main._configuration._connections.clear();
                for (Connection con : _connectionObservableList) {
                    _main._configuration._connections.put(con.getName(), con);
                }
            }
        }
    }

    private void initTableConnections() {
        _tableViewConnections.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                Connection connection = (Connection) newSelection;
                _textFieldConnectionName.setText(connection.getName());
                _textFieldTAG.setText(connection.getTag());
                _textFieldPort.setText(connection.getPort());
                _textFieldServer.setText(connection.getServer());
                _textFieldUser.setText(connection.getUser());
                _textFieldBaseDN.setText(connection.getBaseDN());
                _textFieldDisplayRDN.setText(connection.getDisplayAttribute());
                _passwordFieldConnection.setText(connection.getPassword());
                _checkBoxJNDIReader.setSelected(connection.isUseJNDI());
                _checkBoxUseSSL.setSelected(connection.isSSL());
                _checkBoxReadOnly.setSelected(connection.is_readOnly());
                _textFieldConnectionName.setDisable(false);
                _textFieldServer.setDisable(false);
                _textFieldPort.setDisable(false);
                _textFieldUser.setDisable(false);
                _textFieldDisplayRDN.setDisable(false);
                _textFieldBaseDN.setDisable(false);
                _passwordFieldConnection.setDisable(false);
                _buttonDeleteConnection.setDisable(false);
                _buttonGetCertificatesFromServer.setDisable(false);
                _buttonSaveConnections.setDisable(false);
                _buttonGetBasisDN.setDisable(false);
                _buttonShowPassword.setDisable(false);
                _buttonCopyClipboard.setDisable(false);

            } else {
                _buttonDeleteConnection.setDisable(true);
                _buttonDeleteConnection.setDisable(true);
                _buttonSaveConnections.setDisable(true);
                _buttonGetBasisDN.setDisable(true);
                _buttonShowPassword.setDisable(true);
                _buttonCopyClipboard.setDisable(true);
            }
        });
        _tableColumnName.setCellValueFactory(new PropertyValueFactory<>("Name"));
        _tableColumnTag.setCellValueFactory(new PropertyValueFactory<>("Tag"));
        _tableColumnName.setEditable(true);
        _tableColumnServer.setCellValueFactory(new PropertyValueFactory<Connection, String>("Server"));
        _tableColumnPort.setCellValueFactory(new PropertyValueFactory<Connection, String>("Port"));
        _tableColumnUser.setCellValueFactory(new PropertyValueFactory<Connection, String>("User"));
        _tableColumnPassword.setCellValueFactory(new PropertyValueFactory<Connection, String>("Password"));
        _tableColumnBaseDN.setCellValueFactory(new PropertyValueFactory<Connection, String>("BaseDN"));
        _tableViewConnections.setItems(_connectionObservableList);
    }

    private void updateConnection(String name) {
        Connection c = null;
        String pass = null;

        c = new Connection(_textFieldConnectionName.getText(), _textFieldServer.getText(),
                _textFieldPort.getText(), _textFieldUser.getText(), _passwordFieldConnection.getText(),
                _textFieldBaseDN.getText(), _textFieldTAG.getText(),_textFieldDisplayRDN.getText());
        if (_passwordFieldConnection.getText() != null && !_passwordFieldConnection.getText().equalsIgnoreCase(Config.PASSWORD_NOT_SET) &&
                !_passwordFieldConnection.getText().equalsIgnoreCase(Config.PASSWORD_SET)) {
            try {
                _main._configuration.updatePassword(c, new SecureString(_passwordFieldConnection.getText()));
            } catch (Exception e) {
                logger.error("Error updating password", e);
                GuiHelper.EXCEPTION("Error Updating password ", "Password error " + e.getMessage(), e);
            }
        }
        c.setUseJNDI(_checkBoxJNDIReader.isSelected());
        c.set_readOnly(_checkBoxReadOnly.isSelected());
        c.setSSL(_checkBoxUseSSL.isSelected());
        ObservableList<Connection> connections = FXCollections.observableArrayList();
        for (Connection old : _connectionObservableList) {

            if (!old.getName().equalsIgnoreCase(c.getName())) connections.add(old);
        }
        connections.add(c);
        _connectionObservableList.clear();
        _connectionObservableList.addAll(connections.stream().sorted().collect(Collectors.toList()));

        _main._configuration._connections.clear();
        for (Connection con : _connectionObservableList) {
            _main._configuration._connections.put(con.getName(), con);
        }
        _main._configuration.saveConnections();
    }

    private void getBaseDN() {
        if (_textFieldServer.getText() == null || _textFieldServer.getText().equalsIgnoreCase("")) {
            GuiHelper.ERROR("Connection problem", "Server Name not set");
            return;
        }
        if (_textFieldPort.getText() == null || _textFieldPort.getText().equalsIgnoreCase("")) {
            GuiHelper.ERROR("Connection problem", "Port not set");
            return;
        }
        if (_textFieldUser.getText() == null || _textFieldUser.getText().equalsIgnoreCase("")) {
            GuiHelper.ERROR("Connection problem", "User name not set");
            return;
        }
        SecureString pass = null;
        if (_tableViewConnections.getSelectionModel().getSelectedItem() == null) {
            if (_passwordFieldConnection.getText() == null || _passwordFieldConnection.getText().equalsIgnoreCase("")) {
                GuiHelper.ERROR("Connection problem", "Password not set");
                return;
            }
            pass = new SecureString(_passwordFieldConnection.getText());
        } else {
            Connection selected = (Connection) _tableViewConnections.getSelectionModel().getSelectedItem();
            pass = _main._configuration.getConnectionPassword(selected);

        }

        Connection connection = new Connection(_textFieldConnectionName.getText(),
                _textFieldServer.getText(), _textFieldPort.getText(),
                _textFieldUser.getText(), pass.toString(), "","", null);
        try {
            connection.connect();
            String selectedValue = null;
            if (connection.get_context() != null)
                selectedValue = GuiHelper.selectValue("LDA Context", "Context ", "SELECT", connection.get_context());
            else {
                GuiHelper.ERROR("Context error", "Could not resolve context");
                return;
            }
            if (selectedValue != null) _textFieldBaseDN.setText(selectedValue);
        } catch (Exception e) {
            GuiHelper.EXCEPTION("Connection Problem", e.getMessage(), e);
            return;
        }
    }

    public void readConfiguration() {
        _textFieldBeyondCompareExe.setText(_main._configuration.get_beyondCompareExe());
        _textFieldVisualCode.setText(_main._configuration.get_visualCodeExe());
        _textFieldTempDirectoryPath.setText(_main._configuration.get_tempDir());
        _textFieldKeystoreFile.setText(_main._configuration.get_keyStoreFile());
        for (String conName : _main._configuration._connections.keySet().stream().sorted().collect(Collectors.toList())) {
            Connection con = _main._configuration._connections.get(conName);
            _connectionObservableList.add(con);
        }
    }

    public void exportConnectionsAsCSV(File file, boolean decrypt) throws IOException {

        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            boolean headerAdded = false;
            for (Connection connection : _connectionObservableList) {
                String[] csvString = get_CSVString(connection, decrypt);
                if (!headerAdded) {
                    writer.write(csvString[0]);
                    writer.write("\n");
                    headerAdded = true;

                }
                writer.write(csvString[1]);
                writer.write(System.lineSeparator());
            }
        }
    }

    public String[] get_CSVString(Connection connection, boolean decrypt) {
        StringBuilder stringBuilder = new StringBuilder();
        String[] retValue = new String[2];
        stringBuilder.append("Name;");
        stringBuilder.append("Server;");
        stringBuilder.append("Port;");
        stringBuilder.append("User;");
        stringBuilder.append("Password;");
        stringBuilder.append("BaseDN;");
        stringBuilder.append("DisplayAttribute;");
        stringBuilder.append("UseJNDI");
        stringBuilder.append("ReadOnly");
        retValue[0] = stringBuilder.toString();
        stringBuilder = new StringBuilder();
        stringBuilder.append(connection.getName());
        stringBuilder.append(";");
        stringBuilder.append(connection.getServer());
        stringBuilder.append(";");
        stringBuilder.append(connection.getPort());
        stringBuilder.append(";");
        stringBuilder.append(connection.getUser());
        stringBuilder.append(";");
        if (decrypt) {
            try {
                stringBuilder.append(_main._configuration.getConnectionPassword(connection));
                stringBuilder.append(";");
            } catch (Exception e) {
                logger.error("Error decrypting password for csv", e);
            }
        } else {
            stringBuilder.append(connection.getPassword());
            stringBuilder.append(";");
        }
        try {
            stringBuilder.append(connection.getBaseDN());
            stringBuilder.append(";");
        } catch (Exception e) {
            stringBuilder.append(";");
        }
        stringBuilder.append(connection.getDisplayAttribute());
        stringBuilder.append(";");
        stringBuilder.append(connection.isUseJNDI());
        stringBuilder.append(";");
        stringBuilder.append(connection.is_readOnly());
        retValue[1] = stringBuilder.toString();
        return retValue;
    }
}
