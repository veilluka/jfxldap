package ch.vilki.jfxldap;

import ch.vilki.jfxldap.backend.CmdLine;
import ch.vilki.jfxldap.backend.Config;
import ch.vilki.jfxldap.backend.Errors;
import ch.vilki.jfxldap.gui.*;
import ch.vilki.secured.SecureString;
import javafx.application.Application;
import javafx.collections.ObservableMap;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.SplitPane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import jfxtras.styles.jmetro.JMetro;
import jfxtras.styles.jmetro.Style;
import org.apache.logging.log4j.LogManager;
import ch.vilki.jfxldap.ApplicationVersion;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.Runtime.Version;
import java.util.Properties;


public class Main extends Application {

    Stage _primaryStage = null;
    public Stage get_primaryStage(){return _primaryStage;}
    public static  Config _configuration;
    public static ControllerManager _ctManager;
    public SplitPane _mainSplitPane = new SplitPane();
    EntryDiffView _entryDiffView = null;
    public EntryDiffView get_entryDiffView() {return _entryDiffView;}
    public static Main _main = null;

    @Override
    public void start(Stage primaryStage) throws Exception{

        _primaryStage = primaryStage;
        _main=this;
        readConfiguration();
        _ctManager =new ControllerManager(_main);
        _ctManager.initControllers();
        
        FXMLLoader loader = new FXMLLoader(getClass().getResource(ControllerManager.Companion.fxmlDir("MainWindow.fxml")));
        Parent root = loader.load();

        ObservableMap<String, Object> ns = loader.getNamespace();
        Controller controller = (Controller )ns.get("controller");
        controller.initMenu();
        controller.setPrimaryStage(primaryStage);

        primaryStage.setTitle("jfxLDAP by Bauer Vedran [" + AppVersion.version + "]");
        //JMetro jMetro = new JMetro(root, Style.LIGHT);
        Screen screen = Screen.getPrimary();
        Rectangle2D bounds = screen.getVisualBounds();
        primaryStage.setX(bounds.getMinX());
        primaryStage.setY(bounds.getMinY());
        primaryStage.setWidth(bounds.getWidth());
        primaryStage.setHeight(bounds.getHeight());
        primaryStage.setScene(new Scene(root));
        primaryStage.getScene().getStylesheets().add("/ch/vilki/jfxldap/style.css");
        primaryStage.show();
    }

    private void readConfiguration() {

        try {
            _configuration = new Config();
            String err = _configuration.openConfiguration(Config.getConfigurationFile());
            if(err != null)
            {
                if(err.equalsIgnoreCase(Errors.MASTER_PASSWORD_SECURED_ONLY))
                {
                    String pass = GuiHelper.enterPassword("Enter master password","Not secured with windows ");
                    _configuration.openConfiguration(Config.getConfigurationFile(),new SecureString(pass));

                }
                if(err.equalsIgnoreCase(Errors.WINDOWS_NOT_SECURED_WITH_CURRENT_USER))
                {
                    SecureString pass = null;
                    if(GuiHelper.confirm(Messages.Companion.getMB2().getT(),Messages.Companion.getMB2().getH(),Messages.Companion.getMB2().getD()))
                    {
                        pass = new SecureString(GuiHelper.enterPassword(Messages.Companion.getMB3().getT(),Messages.Companion.getMB3().getH()));
                    }
                    _configuration.openConfiguration(Config.getConfigurationFile(),pass);
                }
                else if(err.equalsIgnoreCase(Errors.FILE_NOT_FOUND))
                {
                    if(GuiHelper.confirm(Messages.Companion.getMB4().getT(),Messages.Companion.getMB4().getH(),Messages.Companion.getMB4().getD()))
                    {
                        String pass = null;
                        while (true)
                        {
                            pass = GuiHelper.enterPassword("Enter master password","Enter password to protect config file");
                            if(pass != null) {
                                if (pass.getBytes().length > 7) break;
                                else
                                {
                                    GuiHelper.INFO("Password length to short","Please at least 8 charachters for master password!");
                                }
                            }
                            else
                            {
                                if(GuiHelper.confirm(Messages.Companion.getMB5().getT(),Messages.Companion.getMB5().getH(),Messages.Companion.getMB5().getD()))
                                {
                                    break;
                                }
                            }
                        }
                        if(pass == null)
                        {
                            _configuration.createConfigurationFile(Config.getConfigurationFile(),null);
                            _configuration.openConfiguration(Config.getConfigurationFile(),null);
                        }
                        else
                        {
                            _configuration.createConfigurationFile(Config.getConfigurationFile(),pass);
                            _configuration.openConfiguration(Config.getConfigurationFile(),new SecureString(pass));
                        }
                    }
                    else
                    {
                        _configuration.createConfigurationFile(Config.getConfigurationFile(),null);
                        _configuration.openConfiguration(Config.getConfigurationFile(),null);
                    }
                }
            }
            if(_configuration.get_lastUsedDirectory() == null) _configuration.set_lastUsedDirectory(System.getProperty("user.home"));

        } catch (Exception e) {
            GuiHelper.EXCEPTION("Error reading configuration", e.toString(),e);
        }
    }

    public static void main(String[] args) {
        String[] testArguments = null;
        /*
        String[] testArguments = new String[6];
        testArguments[0] = "-op";
        testArguments[1] = "export_collection";
        testArguments[2] = "-projectFile";
        testArguments[3] = "C:\\data\\cnc_gitlab\\work\\dev\\openDJ\\data\\opendj_export.project.xlsx";
        testArguments[4] = "-env";
        testArguments[5] = "openDJ";
        */

        if(testArguments != null)
        {
            CmdLine cLine = new CmdLine();
            try {
                cLine.runCmd(testArguments);
            } catch (Exception e) {
                LogManager.getLogger(Main.class).error("Exception in main",e);
                System.err.println(e.getMessage());

            }
            System.exit(0);
        }
        if(args!= null && args.length > 0)
        {
            LogManager.getLogger(Main.class).info("Input arguments found, running console only");
            CmdLine cLine = new CmdLine();
            try {
                cLine.runCmd(args);
            } catch (Exception e) {
                LogManager.getLogger(Main.class).error("Exception in main",e);
                System.err.println(e.getMessage());
            }
            System.exit(0);
        }
        launch(args);
    }
}
