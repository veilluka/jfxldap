package ch.vilki.jfxldap.gui;

import ch.vilki.jfxldap.Main;
import javafx.scene.Parent;
import javafx.stage.Stage;

public interface ILoader {
    public void setMain(Main main);
    public void setWindow(Parent parent);
    public void setOwner(Stage stage);

}
