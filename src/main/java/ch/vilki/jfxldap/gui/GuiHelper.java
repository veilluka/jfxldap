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
        ButtonType result = alert.getResult();
        if(result != null && result.equals(ButtonType.OK)) return  true;
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

    /**
     * Advanced password dialog with both password and confirmation fields in the same window,
     * plus a visibility toggle checkbox.
     * 
     * @param title Dialog title
     * @param header Dialog header text
     * @param userDN Distinguished name for which to set the password (for display purposes)
     * @return The entered password or null if canceled
     */
    public static String enterPasswordWithConfirmation(String title, String header, String userDN) {
        // Create a dialog
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.setWidth(400);
        
        // Set up buttons
        ButtonType confirmButtonType = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(confirmButtonType, ButtonType.CANCEL);
        
        // Set up icon
        dialog.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.KEY));
        
        // Set up the grid
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 20, 10, 10));
        
        // Display user DN if provided
        if (userDN != null && !userDN.isEmpty()) {
            Label dnLabel = new Label("User DN:");
            TextField dnField = new TextField(userDN);
            dnField.setEditable(false);
            dnField.setStyle("-fx-background-color: #f4f4f4;");
            grid.add(dnLabel, 0, 0);
            grid.add(dnField, 1, 0, 2, 1);
            GridPane.setHgrow(dnField, Priority.ALWAYS);
        }
        
        // Create password fields and text fields for visible passwords
        PasswordField passwordField = new PasswordField();
        PasswordField confirmPasswordField = new PasswordField();
        TextField visiblePasswordField = new TextField();
        TextField visibleConfirmPasswordField = new TextField();
        
        // Set prompt text
        passwordField.setPromptText("Enter password");
        confirmPasswordField.setPromptText("Confirm password");
        visiblePasswordField.setPromptText("Enter password");
        visibleConfirmPasswordField.setPromptText("Confirm password");
        
        // Initially hide the visible text fields
        visiblePasswordField.setVisible(false);
        visiblePasswordField.setManaged(false);
        visibleConfirmPasswordField.setVisible(false);
        visibleConfirmPasswordField.setManaged(false);
        
        // Create the checkbox for showing/hiding passwords
        CheckBox showPasswordCheckBox = new CheckBox("Show password");
        
        // Add fields to the grid
        grid.add(new Label("Password:"), 0, 1);
        grid.add(passwordField, 1, 1);
        grid.add(visiblePasswordField, 1, 1);
        
        grid.add(new Label("Confirm Password:"), 0, 2);
        grid.add(confirmPasswordField, 1, 2);
        grid.add(visibleConfirmPasswordField, 1, 2);
        
        grid.add(showPasswordCheckBox, 1, 3);
        
        // Bind the password field values together
        showPasswordCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                // Show as plain text
                visiblePasswordField.setText(passwordField.getText());
                visibleConfirmPasswordField.setText(confirmPasswordField.getText());
                
                passwordField.setVisible(false);
                passwordField.setManaged(false);
                visiblePasswordField.setVisible(true);
                visiblePasswordField.setManaged(true);
                
                confirmPasswordField.setVisible(false);
                confirmPasswordField.setManaged(false);
                visibleConfirmPasswordField.setVisible(true);
                visibleConfirmPasswordField.setManaged(true);
            } else {
                // Show as password
                passwordField.setText(visiblePasswordField.getText());
                confirmPasswordField.setText(visibleConfirmPasswordField.getText());
                
                passwordField.setVisible(true);
                passwordField.setManaged(true);
                visiblePasswordField.setVisible(false);
                visiblePasswordField.setManaged(false);
                
                confirmPasswordField.setVisible(true);
                confirmPasswordField.setManaged(true);
                visibleConfirmPasswordField.setVisible(false);
                visibleConfirmPasswordField.setManaged(false);
            }
        });
        
        // Sync the visible and masked password fields
        visiblePasswordField.textProperty().addListener((observable, oldValue, newValue) -> {
            passwordField.setText(newValue);
            validatePasswords(passwordField.getText(), confirmPasswordField.getText(), 
                            dialog.getDialogPane().lookupButton(confirmButtonType));
        });
        
        passwordField.textProperty().addListener((observable, oldValue, newValue) -> {
            visiblePasswordField.setText(newValue);
            validatePasswords(newValue, confirmPasswordField.getText(), 
                            dialog.getDialogPane().lookupButton(confirmButtonType));
        });
        
        visibleConfirmPasswordField.textProperty().addListener((observable, oldValue, newValue) -> {
            confirmPasswordField.setText(newValue);
            validatePasswords(passwordField.getText(), newValue, 
                            dialog.getDialogPane().lookupButton(confirmButtonType));
        });
        
        confirmPasswordField.textProperty().addListener((observable, oldValue, newValue) -> {
            visibleConfirmPasswordField.setText(newValue);
            validatePasswords(passwordField.getText(), newValue, 
                            dialog.getDialogPane().lookupButton(confirmButtonType));
        });
        
        // Initially disable the login button
        Node confirmButton = dialog.getDialogPane().lookupButton(confirmButtonType);
        confirmButton.setDisable(true);
        
        // Set the grid as the dialog content
        dialog.getDialogPane().setContent(grid);
        
        // Set the focus to the password field
        Platform.runLater(() -> passwordField.requestFocus());
        
        // Set up the result converter
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == confirmButtonType) {
                return passwordField.isVisible() ? passwordField.getText() : visiblePasswordField.getText();
            }
            return null;
        });
        
        // Show the dialog and return the result
        Optional<String> result = dialog.showAndWait();
        return result.orElse(null);
    }
    
    /**
     * Helper method to validate passwords match and enable/disable the confirm button
     */
    private static void validatePasswords(String password, String confirmPassword, Node confirmButton) {
        boolean passwordsMatch = password != null && !password.isEmpty() && password.equals(confirmPassword);
        confirmButton.setDisable(!passwordsMatch);
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
        autoCompleteTextField.getEntries().addAll(
            autoComplete.stream()
            .map(String::toLowerCase)
                    .collect(Collectors.toList())
        );
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
