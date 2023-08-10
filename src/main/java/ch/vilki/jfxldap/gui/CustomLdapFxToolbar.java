package ch.vilki.jfxldap.gui;

import ch.vilki.jfxldap.Main;
import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;

public class CustomLdapFxToolbar extends ToolBar {

    Main _main;
    public Button buttonSettings = new Button();
    public Button buttonLdapExplorerSourceWindow =  new Button();
    public Button buttonLdapExplorerTargetWindow = new Button();
    public Button buttonEntryView= new Button();
    public Button buttonCompareResultWindow = new Button();
    public Button buttonSearchResultWindow = new Button();
    public Button loadStyles = new Button("load styles");

    public CustomLdapFxToolbar(Main main)
    {
        _main = main;
        initButtons();
    }

    public void initButtons()
    {
        buttonEntryView.setTooltip( new Tooltip("LDAP Entry-View"));
        buttonEntryView.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.ENTRY));

        buttonCompareResultWindow.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.COMPARE));
        buttonCompareResultWindow.setTooltip(new Tooltip("Compare-Result"));

        buttonSearchResultWindow.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.SEARCH_RESULT));
        buttonSearchResultWindow.setTooltip(new Tooltip("Search-Result"));

        buttonLdapExplorerTargetWindow.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.TARGET));
        buttonLdapExplorerTargetWindow.setTooltip(new Tooltip("LDAP-TARGET Explorer"));

        buttonLdapExplorerSourceWindow.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.LDAP_TREE));
        buttonLdapExplorerTargetWindow.setTooltip(new Tooltip("LDAP-SOURCE Explorer"));
           buttonSettings.setGraphic(Icons.get_iconInstance().getIcon(Icons.ICON_NAME.SETTINGS));


        getItems().addAll(buttonSettings,buttonLdapExplorerSourceWindow,buttonLdapExplorerTargetWindow,
                buttonEntryView,buttonCompareResultWindow,buttonSearchResultWindow);
    }

    public void switchWindowOnOf(Pane pane){
        if(_main._mainSplitPane.getItems().contains(pane)) _main._mainSplitPane.getItems().remove(pane);
        else _main._mainSplitPane.getItems().add(pane);
    }

    public void switchWindowOnOf(Control pane){
        if(_main._mainSplitPane.getItems().contains(pane)) _main._mainSplitPane.getItems().remove(pane);
        else _main._mainSplitPane.getItems().add(pane);
    }



}
