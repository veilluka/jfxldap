package ch.vilki.jfxldap.gui;


import ch.vilki.jfxldap.Main;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;


public class ProgressWindowController implements ILoader {

    @FXML Button _buttonCancel;
    @FXML ProgressBar _progressBar;
    @FXML  Label _labelHeader;
    @FXML HBox _hboxHeader;
    @FXML TextArea _textAreaMessage;

    Scene _scene;
    Stage _stage;
    VBox _progressWindow;

    public VBox get_window(){return _progressWindow;}

    @FXML
    private void initialize() {
        Icons icons = new Icons();
        _labelHeader.setGraphic(icons.getIcon(Icons.ICON_NAME.SEARCH));
        _buttonCancel.setGraphic(icons.getIcon(Icons.ICON_NAME.STOP));
        _textAreaMessage.setStyle("-fx-background-color: #000000");
    }

    public void clearProgressWindow()
    {
        _progressBar.setProgress(0.0);
        _labelHeader.setText("");
        _textAreaMessage.setText("");
    }
   public void setProgress(double progress, String message)
   {
       _progressBar.setProgress(progress);
       if(_textAreaMessage.getLength() > 10000) _textAreaMessage.clear();
       _textAreaMessage.appendText(message);
       _textAreaMessage.appendText("\n");
   }

   /**
    * Appends a message to the log area without updating the progress bar
    * @param message The message to append to the log
    */
   public void appendToLog(String message)
   {
       if(_textAreaMessage.getLength() > 10000) _textAreaMessage.clear();
       _textAreaMessage.appendText(message);
       _textAreaMessage.appendText("\n");
   }


    @Override
    public void setMain(Main main) {

    }

    @Override
    public void setWindow(Parent parent) {
        _progressWindow = (VBox) parent;
        _scene = new Scene(_progressWindow);
        _stage = new Stage();
        _stage.setScene(_scene);
    }

    @Override
    public void setOwner(Stage stage) {

    }
}
