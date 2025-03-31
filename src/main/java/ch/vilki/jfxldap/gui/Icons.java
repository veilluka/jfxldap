package ch.vilki.jfxldap.gui;

import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Icons {

    public static enum ICON_NAME {
        ENTRY_EQUAL,
        COPY_PASTE_SMALL,
        SUBFOLDER_NOT_EQUAL,
        ARROW_RIGHT,
        ARROW_LEFT,
        ENTRY_NOT_EQUAL,
        KEY,
        PASSWORD,
        REMOVE,
        SEARCH,
        SEARCH_SMALL,
        STOP,
        TREE_SELECTED,
        ENTRY_SELECTED,
        PARENT_SELECTED,
        CONNECT_ICON,
        DISCONNECT_ICON,
        OPEN_FILE,
        DOCK_NODE,
        APP,
        LDAP_TREE,
        LDAP_TREE_SMALL,
        UPLOAD_FILE,
        CLOSE_FILE,
        SETTINGS,
        FILTER_ADD,
        FILTER_REMOVE,
        ENTRY,
        ENTRY_SMALL,
        TARGET,
        TARGET_SMALL,
        FOLDER_SMALL,
        ADD,
        COMPARE,
        SEARCH_RESULT,
        UNEQUAL,
        EQUAL,
        EXPORT_SMALL,
        SET_ATTRIBUTE_SMALL,
        COMPARE_SMALL,
        SORT_SMALL
    };


    private static final Map<ICON_NAME, String> ICONS_STRING_MAP = new HashMap<ICON_NAME, String>() {
        {
            put(ICON_NAME.COMPARE_SMALL, "compare_small.png");
            put(ICON_NAME.COPY_PASTE_SMALL, "copypaste_small.png");
            put(ICON_NAME.SET_ATTRIBUTE_SMALL, "set_attribute_small.png");
            put(ICON_NAME.ENTRY_EQUAL, "Good_mark.png");
            put(ICON_NAME.EXPORT_SMALL, "export_small.png");
            put(ICON_NAME.KEY, "key.png");
            put(ICON_NAME.PASSWORD, "icons8-passwort-16.png");
            put(ICON_NAME.ARROW_RIGHT, "Forward.png");
            put(ICON_NAME.ARROW_LEFT, "Back.png");
            put(ICON_NAME.ENTRY_NOT_EQUAL, "Alert.png");
            put(ICON_NAME.REMOVE, "remove.png");
            put(ICON_NAME.SUBFOLDER_NOT_EQUAL, "Arrow-icon.png");
            put(ICON_NAME.SEARCH, "searchIcon.png");
            put(ICON_NAME.SEARCH_SMALL, "search_small.png");
            put(ICON_NAME.STOP, "stopIcon.png");
            put(ICON_NAME.TREE_SELECTED, "treeIcon.png");
            put(ICON_NAME.CONNECT_ICON, "icons8-verbunden-16.png");
            put(ICON_NAME.DISCONNECT_ICON, "icons8-getrennt-16.png");
            put(ICON_NAME.OPEN_FILE, "icons8-openFile-16.png");
            put(ICON_NAME.DOCK_NODE, "docknode.png");
            put(ICON_NAME.ENTRY_SELECTED, "icon-next-page.png");
            put(ICON_NAME.PARENT_SELECTED, "next-page-blue.png");
            put(ICON_NAME.APP, "app.png");
            put(ICON_NAME.LDAP_TREE, "ldapTree.png");
            put(ICON_NAME.LDAP_TREE_SMALL, "ldapTreeSmall.png");
            put(ICON_NAME.UPLOAD_FILE, "uploadDocument.png");
            put(ICON_NAME.CLOSE_FILE, "closeFile.png");
            put(ICON_NAME.SETTINGS, "settings_icon.png");
            put(ICON_NAME.FILTER_ADD, "icon-filter-on.png");
            put(ICON_NAME.FILTER_REMOVE, "icon-filter-off.png");
            put(ICON_NAME.ENTRY, "icon-entry.png");
            put(ICON_NAME.ENTRY_SMALL, "icon-entry_small.png");
            put(ICON_NAME.TARGET, "icon-target.png");
            put(ICON_NAME.TARGET_SMALL, "icon-target_small.png");
            put(ICON_NAME.FOLDER_SMALL, "icon-folder-small.png");
            put(ICON_NAME.ADD, "add.png");
            put(ICON_NAME.UNEQUAL, "unequal.png");
            put(ICON_NAME.COMPARE, "compare.png");
            put(ICON_NAME.EQUAL, "equal.png");
            put(ICON_NAME.SEARCH_RESULT, "searchResult.png");
            put(ICON_NAME.SORT_SMALL, "icon_sort.png");
        }
    };


    private static Icons _iconInstance = null;

    public static Icons get_iconInstance() {
        if (_iconInstance == null) {
            _iconInstance = new Icons();
        }

        return _iconInstance;
    }

    public Node getObjectType(Set<String> objectClass) {
        if (objectClass.contains("person"))
            return new ImageView(new Image(_iconInstance.getClass().getResourceAsStream("/ch/vilki/jfxldap/icons/objectclass/person.png")));
        if (objectClass.contains("organization"))
            return new ImageView(new Image(_iconInstance.getClass().getResourceAsStream("/ch/vilki/jfxldap/icons/objectclass/organisation.png")));
        if (objectClass.contains("groupofuniquenames") || objectClass.contains("group"))
            return new ImageView(new Image(_iconInstance.getClass().getResourceAsStream("/ch/vilki/jfxldap/icons/objectclass/group.png")));
        if (objectClass.contains("organizationalunit"))
            return new ImageView(new Image(_iconInstance.getClass().getResourceAsStream("/ch/vilki/jfxldap/icons/objectclass/orgUnit.png")));
        if (objectClass.contains("country"))
            return new ImageView(new Image(_iconInstance.getClass().getResourceAsStream("/ch/vilki/jfxldap/icons/objectclass/country.png")));
        if (objectClass.contains("locality"))
            return new ImageView(new Image(_iconInstance.getClass().getResourceAsStream("/ch/vilki/jfxldap/icons/objectclass/address.png")));
        return new ImageView(new Image(_iconInstance.getClass().getResourceAsStream("/ch/vilki/jfxldap/icons/objectclass/top.png")));
    }

    public Node getIcon(ICON_NAME icon) {
        return new ImageView(new Image(_iconInstance.getClass().getResourceAsStream("/ch/vilki/jfxldap/icons/" + ICONS_STRING_MAP.get(icon))));
    }
}
