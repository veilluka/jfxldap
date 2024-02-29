package ch.vilki.jfxldap.gui;

import ch.vilki.jfxldap.Main;
import ch.vilki.jfxldap.backend.*;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.Filter;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class ExportWindowController  implements ILoader, IProgress {

    @FXML  RadioButton _radioButtonCSV;
    @FXML  RadioButton _radioButtonLDIF;
    @FXML  RadioButton _radioButtonEXCEL;
    @FXML  RadioButton _radioButtonAllAttributes;
    @FXML  RadioButton _radioButtonOnlyFound;
    @FXML  RadioButton _radioButtonExportDN;
    @FXML  RadioButton _radioButtonOnlySelectedEntry;
    @FXML  RadioButton _radioButtonChildren;
    @FXML  RadioButton _radioButtonRemoveBASE64;

    @FXML Button _buttonGroupWith;

    @FXML TextField _textFieldReplaceText;
    @FXML TextField _textFieldReplaceWithText;


    ToggleGroup _radioButtonExportToggleGroup = new ToggleGroup();
    ToggleGroup _radioButtonExportAttributesToggleGroup = new ToggleGroup();
    ToggleGroup _radioButtonExportEntry = new ToggleGroup();

    @FXML Button _buttonSaveAs;
    @FXML Button _buttonCancel;

    @FXML Label _labelGroupWith;

    TextFieldLdapFilter _textFilter = new TextFieldLdapFilter();
    HBox _filterHBox = new HBox();
    Label _filterLabel = new Label("Export Filter");

    String _textCoulourError = "-fx-text-fill: red;-fx-font-weight:bold;";
    String _textColourOK =  "-fx-fill: #4F8A10";
    Filter _ldapFilter = null;
    String _groupWithAttribute = null;

    @FXML
    VBox _window;

    private Main _main;
    Scene _scene;
    Stage _stage;

    private Connection _currentConnection;
    private TreeItem<SearchEntry> _startExportEntry;
    private String _exportDN = null;
    private boolean _searchExport=false;

    public void showExportWindow(Connection connection, TreeItem<SearchEntry> searchEntryTreeItem)
    {
        this._currentConnection = connection;

        _textFilter.set_currConnection(_currentConnection);
        embeddedAttributesViewController.set_currentConnection(connection);
        embeddedAttributesViewController._listAllAttributes.getItems().clear();
        embeddedAttributesViewController._listAllAttributes.getItems().addAll(_currentConnection.getSchemaAttributes());
        _startExportEntry = searchEntryTreeItem;
        _exportDN = null;
        _searchExport = true;
        _radioButtonOnlyFound.setDisable(false);
        showInit();
        _stage.show();
        if(connection.is_fileMode())
        {
            embeddedAttributesViewController._buttonAddOperational.setDisable(true);
            embeddedAttributesViewController._buttonRemoveOperational.setDisable(true);
        }

    }

    public void showExportWindow(Connection connection, String exportDN)
    {
        _searchExport = false;
        _radioButtonAllAttributes.setSelected(true);
        _exportDN = exportDN;
        getEmbeddedAttributesController().disableAllElements(false);
        this._currentConnection = connection;
        _textFilter.set_currConnection(_currentConnection);
        embeddedAttributesViewController.set_currentConnection(connection);
        embeddedAttributesViewController._listAllAttributes.getItems().clear();
        embeddedAttributesViewController._listAllAttributes.getItems().addAll(_currentConnection.getSchemaAttributes());
        _startExportEntry = null;
        _radioButtonOnlyFound.setDisable(true);
        if(connection.is_fileMode())
        {
            embeddedAttributesViewController._buttonAddOperational.setDisable(true);
            embeddedAttributesViewController._buttonRemoveOperational.setDisable(true);
        }
        showInit();
        _stage.showAndWait();
    }

    private void showInit()
    {
        _groupWithAttribute = null;
        _buttonGroupWith.setVisible(false);
        _radioButtonEXCEL.setSelected(false);
        _labelGroupWith.setText("");
    }



    @FXML
    private Parent embeddedAttributesView;
    @FXML
    AttributesController embeddedAttributesViewController;

    public AttributesController getEmbeddedAttributesController() {
        return embeddedAttributesViewController;
    }


    @FXML
    private void initialize() {
        _filterHBox.getChildren().addAll(_filterLabel,_textFilter);
        HBox.setHgrow(_textFilter, Priority.ALWAYS);
        Insets textInsets = new Insets(3, 5, 0, 10);
        HBox.setMargin(_textFilter,textInsets);
        Insets labelInsets = new Insets(8, 5, 0, 10);
        HBox.setMargin(_filterLabel,labelInsets);
        _radioButtonCSV.setToggleGroup(_radioButtonExportToggleGroup);
        _radioButtonLDIF.setToggleGroup(_radioButtonExportToggleGroup);
        _radioButtonEXCEL.setToggleGroup(_radioButtonExportToggleGroup);
        _radioButtonOnlySelectedEntry.setToggleGroup(_radioButtonExportEntry);
        _radioButtonChildren.setToggleGroup(_radioButtonExportEntry);
        _radioButtonAllAttributes.setToggleGroup(_radioButtonExportAttributesToggleGroup);
        _radioButtonOnlyFound.setToggleGroup(_radioButtonExportAttributesToggleGroup);
        _radioButtonOnlyFound.setSelected(true);
        embeddedAttributesViewController.disableAllElements(true);
        _radioButtonLDIF.setSelected(true);
        _radioButtonExportDN.setVisible(false);
        _radioButtonExportDN.setSelected(false);
        _radioButtonAllAttributes.setOnAction(x -> {
            getEmbeddedAttributesController().disableAllElements(false);
        });
        _radioButtonOnlyFound.setOnAction(x -> {
            getEmbeddedAttributesController().disableAllElements(true);
        });

        _radioButtonCSV.setOnAction(x -> {
            _radioButtonExportDN.setVisible(true);
            _buttonGroupWith.setVisible(true);
        });
        _radioButtonEXCEL.setOnAction(x -> {
            _radioButtonExportDN.setVisible(true);
            _buttonGroupWith.setVisible(true);
        });
        _radioButtonLDIF.setOnAction(x -> {
            _radioButtonExportDN.setVisible(false);
            _buttonGroupWith.setVisible(false);
        });

        _radioButtonExportEntry.selectedToggleProperty().addListener(x->{
            if(_radioButtonChildren.isSelected())
            {
                _window.getChildren().add(2,_filterHBox);
            }
            else {
                _window.getChildren().remove(_filterHBox);
            }
        });

        _buttonCancel.setOnAction(x -> cancel());
        _buttonSaveAs.setOnAction(x->exportFile());

        _buttonGroupWith.setOnAction(x->{
            List<String> atts = _currentConnection.getAllMultivalueSchemaAttributes();
            String[] multiValueAttributes = null;
            if(atts != null)
            {
                atts.sort(String.CASE_INSENSITIVE_ORDER);
                multiValueAttributes = atts.toArray(new String[atts.size()]);
            }
            _groupWithAttribute = GuiHelper.selectValue("Group with",
                    "Select Attribute to group with","Attribute",multiValueAttributes);
            if(_groupWithAttribute != null && !_groupWithAttribute.equalsIgnoreCase("")) _labelGroupWith.setText(_groupWithAttribute);
        });

    }

    public VBox getWindow() {
        return _window;
    }

    @Override
    public void setMain(Main main) {
        _main = main;
    }

    @Override
    public void setWindow(Parent parent) {
        _window = (VBox) parent;
        _scene = new Scene(_window);
        _stage = new Stage();
        _stage.setScene(_scene);
    }


    @Override
    public void setOwner(Stage stage) {
        _stage.initOwner(stage);

    }

    private void exportFile()
    {
        List<TreeItem<SearchEntry>> resultList = new ArrayList<>();
        if(_searchExport) getAllFoundEntries(_startExportEntry,resultList);
        if(_searchExport && resultList.isEmpty())
        {
            GuiHelper.INFO("Export Entries","Did not find entries to export");
            cancel();
            return;
        }
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("FileName");
        String extDescription = null;
        String extfilter = null;

        if(_radioButtonLDIF.isSelected()){extDescription = "LDIF files (*.ldif)"; extfilter="*.ldif";}
        if(_radioButtonCSV.isSelected()){extDescription = "CSV files (*.csv)"; extfilter="*.csv";}
        if(_radioButtonEXCEL.isSelected()){extDescription = "Excel files (*.xlsx)"; extfilter="*.xlsx";}

        FileChooser.ExtensionFilter filter = new FileChooser.ExtensionFilter(extDescription,extfilter);
        fileChooser.getExtensionFilters().add(filter);
        fileChooser.setInitialDirectory(new File(_main._configuration.get_lastUsedDirectory()));
        File selectedFile = fileChooser.showSaveDialog(_main.get_primaryStage());
        if(selectedFile == null) {
          return;
        }
        else
        {
            _stage.close();
        }
        _main._configuration.set_lastUsedDirectory(selectedFile.getParent());
        List<Entry> allEntries = resultList.stream().map(x->x.getValue().getEntry()).collect(Collectors.toList());
        _main._ctManager._progressWindowController._labelHeader.setText("Export LDIF in File->" + selectedFile.getAbsolutePath());
        _main._ctManager._progressWindowController._stage.show();
        if(_radioButtonLDIF.isSelected())
        {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.submit(()-> {
                List ignore = null;
                List export = null;
                if(embeddedAttributesViewController._radioButtonCompareAttributes.isSelected())
                {
                    export = embeddedAttributesViewController._listFilterAttributes.getItems();
                }
                if(embeddedAttributesViewController._radioButtonIgnoreAttributes.isSelected())
                {
                    ignore = embeddedAttributesViewController._listFilterAttributes.getItems();
                }
                LdifHandler ldifHandler = new LdifHandler();
                if(_searchExport)ldifHandler.exportLdif(selectedFile,_currentConnection,allEntries,export,ignore,this);
                else ldifHandler.exportLdif(selectedFile,_currentConnection,export,ignore,_ldapFilter,_exportDN,_radioButtonChildren.isSelected(),
                        this,_main._configuration,
                        _radioButtonRemoveBASE64.isSelected(),_textFieldReplaceText.getText(),_textFieldReplaceWithText.getText());
            });
        }
        else if(_radioButtonEXCEL.isSelected() || _radioButtonCSV.isSelected())
        {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.submit(()-> {
                List ignore = null;
                List export = null;
                if(embeddedAttributesViewController._radioButtonCompareAttributes.isSelected())
                {
                    export = embeddedAttributesViewController._listFilterAttributes.getItems();
                }
                if(embeddedAttributesViewController._radioButtonIgnoreAttributes.isSelected())
                {
                    ignore = embeddedAttributesViewController._listFilterAttributes.getItems();
                }

                ExcelFileHandler excelFileHandler = new ExcelFileHandler();
                if(_searchExport) excelFileHandler.exportTree(allEntries,_currentConnection,export,ignore,
                        _radioButtonExportDN.isSelected(),selectedFile, _radioButtonCSV.isSelected(),this);

                else excelFileHandler.exportTree(_currentConnection,export,ignore,_radioButtonExportDN.isSelected(),_exportDN,
                        selectedFile,_textFilter.get_filter(),_radioButtonCSV.isSelected(),_radioButtonOnlySelectedEntry.isSelected(), _groupWithAttribute, this);
            });
        }
    }

    private void cancel()
    {
        _stage.close();
        _currentConnection = null;
        _startExportEntry = null;
    }

    private void getAllFoundEntries(TreeItem<SearchEntry> item, List<TreeItem<SearchEntry>> resultList )
    {
        if(item != null )
        {
            if(item.getValue().ValueFound.get())
            {
                resultList.add(item);
            }
            for(TreeItem<SearchEntry> child: item.getChildren()) getAllFoundEntries(child,resultList);
        }
    }

    @Override
    public void setProgress(double progress, String description) {
        Platform.runLater(()-> {
            _main._ctManager._progressWindowController.setProgress(progress,description);
        });
    }

    @Override
    public void signalTaskDone(String taskName, String description, Exception e) {
        Platform.runLater(()-> {
            if(taskName.equalsIgnoreCase("file_export"))
            {
                _main._ctManager._progressWindowController._stage.close();
                if(e!= null)
                {
                    GuiHelper.EXCEPTION("Error during export",e.getMessage(),e);
                }
                else
                {
                    if(description == null) GuiHelper.INFO("Export done", "Export succeded");
                    else GuiHelper.INFO("Export done", description);
                }
                _currentConnection = null;
                _startExportEntry = null;

            }

        });

    }

    @Override
    public void setProgress(String taskName, double progress) {

    }
}




