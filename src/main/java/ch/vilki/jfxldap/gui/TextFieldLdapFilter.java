package ch.vilki.jfxldap.gui;

import ch.vilki.jfxldap.backend.Connection;
import com.unboundid.ldap.sdk.Filter;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Side;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

public class TextFieldLdapFilter extends TextField {

    private static String _textCoulourError = "-fx-text-fill: red;-fx-font-weight:bold;";
    private static String _textColourOK =  "-fx-fill: #4F8A10";

    public Filter get_filter() {
        return _filter;
    }

    private Filter _filter = null;
    private boolean _filterOK = false;
    private ContextMenu _autoCompleteMenuLdapAttributes = new ContextMenu();
    private Connection _currConnection = null;

    public boolean is_filterOK() {return _filterOK;}
    public Connection get_currConnection() {return _currConnection;}

    public void set_currConnection(Connection _currConnection) {this._currConnection = _currConnection;}


    public TextFieldLdapFilter()
    {
        setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(this, Priority.ALWAYS);
        textProperty().addListener((observable, oldValue, newValue) -> {
            if(newValue == null || newValue.equalsIgnoreCase(""))
            {
                setStyle(_textColourOK);
                _filter = null;
                _autoCompleteMenuLdapAttributes.hide();
                _filterOK=false;
            }
            else
            {
                try
                {
                    _filter = Filter.create(newValue);
                    setStyle(_textColourOK);
                    _autoCompleteMenuLdapAttributes.hide();
                    _filterOK=true;

                }
                catch (Exception e)
                {
                    _filterOK=false;
                    _filter = null;
                    setStyle(_textCoulourError);
                    if(_currConnection != null)  fillSuggestion(newValue);
                }
            }
        });
    }

    private void fillSuggestion(String input)
    {
        String val = getAttName(input);
        _autoCompleteMenuLdapAttributes.getItems().clear();
        _autoCompleteMenuLdapAttributes.hide();
        if(_currConnection == null || val.isEmpty())
        {
            _autoCompleteMenuLdapAttributes.hide();
            return;
        }

        for(String s: _currConnection.SchemaAttributes)
        {
            if(s.startsWith(val))
            {
                Label entryLabel = new Label(s);
                CustomMenuItem item = new CustomMenuItem(entryLabel, true);
                item.setOnAction(new EventHandler<ActionEvent>()
                {
                    @Override
                    public void handle(ActionEvent actionEvent) {
                        String replaced = input.replace(val,s);

                        setText(replaced);
                        _autoCompleteMenuLdapAttributes.hide();
                        positionCaret(getText().length());
                    }
                });
                _autoCompleteMenuLdapAttributes.getItems().add(item);
                if(_autoCompleteMenuLdapAttributes.getItems().size()>50)
                {
                    _autoCompleteMenuLdapAttributes.getItems().clear();
                    return;
                }

            }
        }
        if(!_autoCompleteMenuLdapAttributes.getItems().isEmpty())
        {
            _autoCompleteMenuLdapAttributes.show(this, Side.BOTTOM, 0, 0);
        }
    }

    private String getAttName(String input)
    {
        if(input == null || input.equalsIgnoreCase("") ) return "";
        StringBuilder stringBuilder = new StringBuilder();
        for(int i=input.length()-1;i>-1;i--)
        {
            char f = input.charAt(i);
            if ( (f=='(') || (f==')') ||  (f=='&') || (f=='|') || (f=='*') || (f=='<') || (f=='>')|| (f=='~')|| (f=='!'))
            {
                return stringBuilder.reverse().toString();
            }
            else
            {
                stringBuilder.append(f);
            }
        }
        return stringBuilder.reverse().toString();
    }

}