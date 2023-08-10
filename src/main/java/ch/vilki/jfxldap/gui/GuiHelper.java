package ch.vilki.jfxldap.gui;

import ch.vilki.jfxldap.Main;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class GuiHelper {

    public static enum FILE_OPTIONS{
        SAVE_AS,
        OPEN_DIRECTORY,
        OPEN_FILE
    }

    public static void ERROR(String title,String content)
    {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }

    public static void ERROR_DETAILED(String title, String content, String details)
    {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("ERROR");
        alert.setHeaderText(title);
        alert.setContentText(content);

        Label label = new Label("Error Details");
        TextArea textArea = new TextArea(details);
        textArea.setEditable(false);
        textArea.setWrapText(true);

        textArea.setMaxWidth(Double.MAX_VALUE);
        textArea.setMaxHeight(Double.MAX_VALUE);
        GridPane.setVgrow(textArea, Priority.ALWAYS);
        GridPane.setHgrow(textArea, Priority.ALWAYS);

        GridPane expContent = new GridPane();
        expContent.setMaxWidth(Double.MAX_VALUE);
        expContent.add(label, 0, 0);
        expContent.add(textArea, 0, 1);
        alert.getDialogPane().setExpandableContent(expContent);
        alert.showAndWait();
    }


    public static void EXCEPTION(String header, String errorDescription, Exception e)
    {

        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("ERROR");
        alert.setHeaderText(header);
        alert.setContentText(errorDescription);

        Label label = new Label("Exception detail:");
        TextArea textArea = new TextArea();
        textArea.setEditable(false);
        textArea.setWrapText(true);
        if(e != null)
        {
            StringBuilder builder = new StringBuilder();
            if(e.getMessage() != null) builder.append(e.getMessage());
            builder.append("\n");
            builder.append(e);
            builder.append("\n");
            for(StackTraceElement stack: e.getStackTrace())
            {
                builder.append(stack.getFileName() + "->" + stack.getLineNumber() + " ->" + stack.getClassName());
                builder.append("\n");
            }
            textArea.appendText(builder.toString());
        }

        textArea.setMaxWidth(Double.MAX_VALUE);
        textArea.setMaxHeight(Double.MAX_VALUE);
        GridPane.setVgrow(textArea, Priority.ALWAYS);
        GridPane.setHgrow(textArea, Priority.ALWAYS);

        GridPane expContent = new GridPane();
        expContent.setMaxWidth(Double.MAX_VALUE);
        expContent.add(label, 0, 0);
        expContent.add(textArea, 0, 1);
        alert.getDialogPane().setExpandableContent(expContent);
        alert.showAndWait();
    }

    public static void INFO(String title,String content)
    {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);

        alert.setContentText(content);
        alert.showAndWait();
    }


    /*
    public static boolean confirm(String title, String header, String description)
    {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        ButtonType okbutton = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okbutton,ButtonType.CANCEL);
        Icons ic = new Icons();
        dialog.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICONS.ENTRY_EQUAL));
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        TextArea textArea = new TextArea();
        grid.add(new Label("Description"),0,0);
        grid.add(textArea,0,1);
         Optional<String> result = dialog.showAndWait();


    }


    public static boolean confirm(String title, String header, String description)
    {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        TextArea textArea = new TextArea();
        grid.add(new Label("Description"),0,0);
        grid.add(textArea,0,1);
        textArea.setText(description);
        DialogPane dialogPane = new DialogPane();
        dialogPane.setContent(grid);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setDialogPane(dialogPane);
        alert.showAndWait();
        ButtonType result = alert.getResult();
        if(result.equals(ButtonType.OK)) return true;
        else return  false;

    }
     */
    public static boolean confirm(String title, String header, String description)
    {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setWidth(800);


        Label label = new Label("DESCRIPTION:");
        TextArea textArea = new TextArea(description);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setMaxWidth(Double.MAX_VALUE);
        textArea.setMaxHeight(Double.MAX_VALUE);
        textArea.setStyle(Styling.SMALL_TEXT);
        GridPane.setVgrow(textArea, Priority.ALWAYS);
        GridPane.setHgrow(textArea, Priority.ALWAYS);
        if(description != null && !description.equalsIgnoreCase(""))
        {
            GridPane expContent = new GridPane();
            expContent.setMaxWidth(Double.MAX_VALUE);
            expContent.add(label, 0, 0);
            expContent.add(textArea, 0, 1);
            alert.getDialogPane().setContent(expContent);
        }
        alert.showAndWait();
        if(alert.getResult().equals(ButtonType.OK)) return  true;
        return  false;
    }



    public static String enterPassword(String title,String header)
    {
        String pass[] = new String[1];
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        ButtonType loginButtonType = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);
        Icons ic = new Icons();
        dialog.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.KEY));
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        final PasswordField[] password = {new PasswordField()};
        password[0].setPromptText("Password");

        grid.add(new Label("Password:"), 0, 1);
        grid.add(password[0], 1, 1);
        Node loginButton = dialog.getDialogPane().lookupButton(loginButtonType);
        loginButton.setDisable(true);
        dialog.getDialogPane().setContent(grid);
        Platform.runLater(() -> password[0].requestFocus());

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == loginButtonType) {
                return new String( password[0].getText());
            }
            return null;
        });

        password[0].textProperty().addListener((observable, oldValue, newValue) -> {
            loginButton.setDisable(newValue.trim().isEmpty());
        });

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(usernamePassword -> {pass[0] =  usernamePassword;});
        return  pass[0];
    }

    public static File selectFile(Main main, FileChooser.ExtensionFilter extensionFilter , String title, FILE_OPTIONS file_options)
    {
        File selectedFile = null;
        FileChooser.ExtensionFilter extension = null;
        if(extensionFilter == null) {extension = new FileChooser.ExtensionFilter("*.*","*.*");}
        else extension = extensionFilter;

        if(file_options.equals(FILE_OPTIONS.OPEN_DIRECTORY))
        {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle(title);
            File defaultDirectory = new File(main._configuration.get_lastUsedDirectory());
            chooser.setInitialDirectory(defaultDirectory);
            selectedFile = chooser.showDialog(main.get_primaryStage());
        }
        else if(file_options.equals(FILE_OPTIONS.SAVE_AS) || file_options.equals(FILE_OPTIONS.OPEN_FILE))
        {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle(title);
            FileChooser.ExtensionFilter filter = extension;
            fileChooser.getExtensionFilters().add(filter);
            fileChooser.setInitialDirectory(new File(main._configuration.get_lastUsedDirectory()));

            if(file_options.equals(FILE_OPTIONS.SAVE_AS)) selectedFile = fileChooser.showSaveDialog(main.get_primaryStage());
            if(file_options.equals(FILE_OPTIONS.OPEN_FILE)) selectedFile = fileChooser.showOpenDialog(main.get_primaryStage());
        }

        if(selectedFile != null)
        {
            if(file_options.equals(FILE_OPTIONS.OPEN_DIRECTORY))   main._configuration.set_lastUsedDirectory(selectedFile.getAbsolutePath());
            else main._configuration.set_lastUsedDirectory(selectedFile.getParent());

        }
        return selectedFile;
    }


    public static String enterValue(String title,String header, String promptText,boolean allowNull)
    {
        String retValue[] = new String[1];
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        ButtonType okButtonType = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okButtonType, ButtonType.CANCEL);
        Icons ic = new Icons();
        dialog.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.ENTRY_EQUAL));
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        final TextField[] text = {new TextField()};
        text[0].setPromptText(promptText);

        grid.add(new Label(promptText), 0, 1);
        grid.add(text[0], 1, 1);
        Node okButton = dialog.getDialogPane().lookupButton(okButtonType);
        if(!allowNull) okButton.setDisable(true);
        dialog.getDialogPane().setContent(grid);
        Platform.runLater(() -> text[0].requestFocus());
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == okButtonType) {
                return new String( text[0].getText());
            }
            if(dialogButton == ButtonType.CANCEL)
            {
                return null;
            }
            return null;
        });

        if(!allowNull)
        {
            text[0].textProperty().addListener((observable, oldValue, newValue) -> {
                okButton.setDisable(newValue.trim().isEmpty());
            });
        }
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(usernamePassword -> {retValue[0] =  usernamePassword;});
        return  retValue[0];
    }

    public static String enterValue(String title,String header, String promptText,List<String> autoComplete )
    {
        String pass[] = new String[1];
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        ButtonType loginButtonType = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);
        Icons ic = new Icons();
        dialog.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.ENTRY_EQUAL));
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        AutoCompleteTextField autoCompleteTextField = new AutoCompleteTextField();
        autoCompleteTextField.getEntries().addAll(autoComplete);

        final TextField[] text = {autoCompleteTextField};
        text[0].setPromptText(promptText);

        grid.add(new Label(promptText), 0, 1);
        grid.add(text[0], 1, 1);
        Node loginButton = dialog.getDialogPane().lookupButton(loginButtonType);
        loginButton.setDisable(true);
        dialog.getDialogPane().setContent(grid);
        Platform.runLater(() -> text[0].requestFocus());

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == loginButtonType) {
                return new String( text[0].getText());
            }
            return null;
        });

        text[0].textProperty().addListener((observable, oldValue, newValue) -> {
            loginButton.setDisable(newValue.trim().isEmpty());
        });

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(usernamePassword -> {pass[0] =  usernamePassword;});
        return  pass[0];
    }

    public static String selectValue(String title, String header, String content,String[] choices)
    {
        if(choices == null || choices.length == 0) return null;
        ChoiceDialog<String> dialog = new ChoiceDialog<>(choices[0], choices);
        String[] selection = new String[1];
        dialog.setTitle(title);

        dialog.setHeaderText(header);
        dialog.setContentText(content);
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(x -> {selection[0] =  x;});
        return selection[0];
    }

    public static String selectValue(String title, String header, String content, String[] choices, Scene scene)
    {
        if(choices == null || choices.length == 0) return null;
        List<String> sorted = Arrays.stream(choices).sorted().collect(Collectors.toList());
        ChoiceDialog<String> dialog = new ChoiceDialog<>(choices[0], sorted);

        String[] selection = new String[1];
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.setContentText(content);

        dialog.getDialogPane().addEventHandler(KeyEvent.KEY_PRESSED, (key) -> {
            String keycode = key.getCode().getName();
            for(String s: sorted)
            {
                if(s.toLowerCase().startsWith(keycode.toLowerCase()))
                {
                    dialog.setSelectedItem(s);
                }
            }

        });

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(x -> {selection[0] =  x;});
        return selection[0];
    }
}
