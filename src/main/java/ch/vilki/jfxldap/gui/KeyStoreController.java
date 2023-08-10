package ch.vilki.jfxldap.gui;

import ch.vilki.jfxldap.Main;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.openssl.PEMReader;
import org.bouncycastle.openssl.PEMWriter;
import org.bouncycastle.openssl.PasswordFinder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;

public class KeyStoreController implements ILoader{

    static Logger logger = LogManager.getLogger(KeyStoreController.class);
    Main _main = null;
    Scene _scene;
    Stage _stage;
    VBox _keyStoreWindow;
    ListView<String> _listViewFound = null;
    Map<String, X509Certificate> _addCertificates = new HashMap<>();
    Map<String, X509Certificate> _saveCertificate = new HashMap<>();

    @FXML Button _buttonMoveToStore;
    @FXML Button _buttonDeleteFromStore;
    @FXML Button _buttonSave;
    @FXML Button _buttonClose;
    @FXML Button _buttonAddToStore;
    @FXML Button _buttonExportPEM;
    @FXML HBox _hBoxAddList;
    @FXML TextField _textFieldSubject;
    @FXML TextField _textFieldIssuer;
    @FXML TextField _textFieldValidFrom;
    @FXML TextField _textFieldValidTo;
    @FXML TextField _textFieldVersion;
    @FXML TextField _textFieldAlgo;
    @FXML ListView<String> _listViewStored;
    @Override
    public void setMain(Main main) {
        _main = main;
    }

    @Override
    public void setWindow(Parent parent) {
        _keyStoreWindow = (VBox) parent;
        _scene = new Scene(_keyStoreWindow);
        _stage = new Stage();
        _stage.setScene(_scene);
        _stage.setTitle("Keystore");
        _stage.initStyle(StageStyle.DECORATED);
        _stage.initModality(Modality.NONE);
        _stage.onCloseRequestProperty().addListener(x->{
          cleanUp();
        });
    }

    public void initWindow()
    {
        _listViewStored.getItems().clear();
        KeyStore keyStore = _main._configuration.get_keyStore();
        if(keyStore == null)
        {
            GuiHelper.ERROR("No keystore defined","Create or open keystore first");
            return;
        }
        try {
            for (Enumeration<String> e = keyStore.aliases(); e.hasMoreElements();)
            {
                _listViewStored.getItems().add(e.nextElement());
            }
        } catch (KeyStoreException e) {
            logger.error("Error reading from keystore",e);
        }
        _buttonDeleteFromStore.setDisable(true);
        _buttonMoveToStore.setDisable(true);
        _buttonClose.setDisable(false);
        _buttonSave.setDisable(true);
    }

    public void showWindow()
    {
         initWindow();
         _stage.showAndWait();

    }

    public void setCertificatesToBeAdded(Map<String,X509Certificate> certificates )
    {
        _addCertificates.clear();
        _addCertificates.putAll(certificates);
        if(_listViewFound == null)
        {
            _listViewFound = new ListView<>();
            VBox.setVgrow(_listViewFound, Priority.ALWAYS);
            _listViewFound.getSelectionModel().selectedItemProperty().addListener(x->{
                _buttonMoveToStore.setDisable(false);
                String alias = _listViewFound.getSelectionModel().getSelectedItem();
                X509Certificate certificate = _addCertificates.get(alias);
                if(certificate == null) certificate = _saveCertificate.get(alias);
                if(certificate == null) return;
                _textFieldValidFrom.setText(certificate.getNotBefore().toString());
                _textFieldAlgo.setText(certificate.getSigAlgOID());
                _textFieldIssuer.setText(certificate.getIssuerDN().getName());
                _textFieldSubject.setText(certificate.getSubjectDN().getName());
                _textFieldValidTo.setText(certificate.getNotAfter().toString());
                _textFieldVersion.setText(String.valueOf(certificate.getVersion()));

            });
        }
        _listViewFound.getItems().clear();
        for(String alias: certificates.keySet())
        {
            _listViewFound.getItems().add(alias);
        }
        if(!_hBoxAddList.getChildren().contains(_listViewFound))
        {
            _hBoxAddList.getChildren().add(0, _listViewFound);

        }
        _buttonMoveToStore.setDisable(false);
    }

    public void closeWindow()
    {
        cleanUp();
        _stage.close();
    }
    public void cleanUp()
    {
        _buttonDeleteFromStore.setDisable(true);
        _buttonMoveToStore.setDisable(true);
        _buttonSave.setDisable(true);
        _buttonClose.setDisable(true);
        if(_listViewFound != null) _listViewFound.getSelectionModel().clearSelection();
        _listViewStored.getSelectionModel().clearSelection();
        _textFieldVersion.clear();
        _textFieldValidTo.clear();
        _textFieldSubject.clear();
        _textFieldIssuer.clear();
        _textFieldValidFrom.clear();
        _textFieldAlgo.clear();
        _addCertificates.clear();
        _saveCertificate.clear();
    }
    @Override
    public void setOwner(Stage stage) {
        _stage.initOwner(stage);
    }

    @FXML
    private void initialize() {
        _buttonDeleteFromStore.setGraphic( Icons.get_iconInstance().getIcon(Icons.ICON_NAME.REMOVE)  );
        _buttonMoveToStore.setGraphic( Icons.get_iconInstance().getIcon(Icons.ICON_NAME.ARROW_RIGHT)  );
        _buttonAddToStore.setGraphic( Icons.get_iconInstance().getIcon(Icons.ICON_NAME.ADD)  );

        _listViewStored.getSelectionModel().selectedItemProperty().addListener(x->{
            String alias = _listViewStored.getSelectionModel().getSelectedItem();
            if(alias == null) return;
            try {
                X509Certificate certificate = (X509Certificate) _main._configuration.get_keyStore().getCertificate(alias);
                if(certificate == null) certificate = _saveCertificate.get(alias);
                if(certificate == null) return;
                _textFieldValidFrom.setText(certificate.getNotBefore().toString());
                _textFieldAlgo.setText(certificate.getSigAlgOID());
                _textFieldIssuer.setText(certificate.getIssuerDN().getName());
                _textFieldSubject.setText(certificate.getSubjectDN().getName());
                _textFieldValidTo.setText(certificate.getNotAfter().toString());
                _textFieldVersion.setText(String.valueOf(certificate.getVersion()));
            } catch (KeyStoreException e) {
                logger.error("Error reading certificate from store",e);
            }
            _buttonDeleteFromStore.setDisable(false);

       });
        _buttonMoveToStore.setOnAction(x->{
            String selected = _listViewFound.getSelectionModel().getSelectedItem();
            if(selected == null) return;
            _saveCertificate.put(selected,_addCertificates.get(selected));
            _buttonSave.setDisable(false);
            _addCertificates.remove(selected);
            _listViewStored.getItems().add(selected);
            _listViewFound.getItems().remove(selected);
        });
        _buttonSave.setOnAction(x->{
            if(!_saveCertificate.isEmpty())
            {
                try {
                    for(String alias: _saveCertificate.keySet())
                    {
                        _main._configuration.get_keyStore().setCertificateEntry(alias,_saveCertificate.get(alias));
                    }
                    _main._configuration.saveKeyStoreFile();
                }
                catch (KeyStoreException | IOException | CertificateException | NoSuchAlgorithmException e) {
                    logger.error("Error adding certificate",e);
                    GuiHelper.EXCEPTION("Error adding certificate",e.getMessage(),e);
                }
                closeWindow();
            }
        });
        _buttonClose.setOnAction(x->closeWindow());
        _buttonAddToStore.setOnAction(x->{
           File file = GuiHelper.selectFile(_main,null,"Enter fileName", GuiHelper.FILE_OPTIONS.OPEN_FILE);
            if(file == null) return;
            try {
                PasswordFinder passwordFinder = new PasswordFinder() {
                    @Override
                    public char[] getPassword() {
                        String pass = GuiHelper.enterPassword("Found password","Found keypair, enter password");
                        if(pass == null) return new char[]{};
                        return pass.toCharArray();

                    }
                };
                PEMReader pemReader = new PEMReader(new FileReader(file),passwordFinder);
                List<Object> pemEntries = new ArrayList<>();
                Object o = "";
                while(o!=null)
                {
                    try {
                        o = pemReader.readObject();
                        if(o!=null) pemEntries.add(o);
                    }
                    catch (Exception e){
                        logger.error("FOO",e);
                    }
                }
                Map<String,X509Certificate> addCertificates = new HashMap<>();
                for(Object entry: pemEntries)
                {
                    if(entry instanceof X509Certificate )
                    {
                        X509Certificate cert = (X509Certificate)entry;
                        addCertificates.put(cert.getSubjectX500Principal().getName(),cert);
                    }
                }
                if(!addCertificates.isEmpty())
                {
                    setCertificatesToBeAdded(addCertificates);
                }

            } catch (Exception e) {
                GuiHelper.EXCEPTION("Error reading certificate file",e.getMessage(),e);
            }
        });
        _buttonDeleteFromStore.setOnAction(x->{
            String selected = _listViewStored.getSelectionModel().getSelectedItem();
            if(selected == null) return;
            if(_saveCertificate.containsKey(selected))
            {
                _addCertificates.put(selected,_saveCertificate.get(selected));
                _saveCertificate.remove(selected);
                _listViewFound.getItems().add(selected);
                _listViewStored.getItems().remove(selected);
                return;
            }
            try {
                Certificate c  = _main._configuration.get_keyStore().getCertificate(selected);
                if(c!=null)
                {
                    if(!GuiHelper.confirm("Key-Store","Delete certificate from the store?","")) return;
                    _main._configuration.get_keyStore().deleteEntry(selected);
                    _main._configuration.saveKeyStoreFile();
                    _listViewStored.getItems().remove(selected);

                }
            } catch (KeyStoreException | IOException | CertificateException | NoSuchAlgorithmException e) {
                GuiHelper.EXCEPTION("Delete certificate error",e.getMessage(),e);
            }
        });
        _buttonExportPEM.setOnAction(x->{
            String selected = _listViewStored.getSelectionModel().getSelectedItem();
            if(selected == null) return;
            try {
                Certificate c  = _main._configuration.get_keyStore().getCertificate(selected);
                if(c!=null)
                {
                    FileChooser.ExtensionFilter filter = new FileChooser.ExtensionFilter("PEM files (*.pem)","*.pem");
                    File file = GuiHelper.selectFile(_main,filter,"Enter file name",GuiHelper.FILE_OPTIONS.SAVE_AS);
                    if(file == null) return;
                    PEMWriter pemWriter = new PEMWriter(new FileWriter(file));
                    pemWriter.writeObject(c);
                    pemWriter.close();

                }
            } catch (KeyStoreException | IOException e) {
                GuiHelper.EXCEPTION("Delte certificate error",e.getMessage(),e);
            }
        });
    }
}
