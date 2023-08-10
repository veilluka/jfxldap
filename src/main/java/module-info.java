module ch.vilki.jfxldap {

    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires javafx.web;
    requires javafx.base;
    requires unboundid.ldapsdk;
    requires net.sourceforge.argparse4j;

     requires java.naming;
    requires java.xml;
    requires org.apache.poi.poi;
    requires org.apache.poi.ooxml;
    requires java.datatransfer;
    requires java.desktop;
    requires org.apache.logging.log4j;
    requires bcprov.jdk16;
    requires org.apache.commons.codec;
    requires com.google.common;
    requires commons.cli;

    requires org.slf4j;
    requires org.slf4j.simple;
    requires kotlin.stdlib;
    requires org.jfxtras.styles.jmetro;
    requires ch.vilki.secured;

    opens ch.vilki.jfxldap to javafx.fxml;
    exports ch.vilki.jfxldap;
    exports ch.vilki.jfxldap.gui;
    opens ch.vilki.jfxldap.gui to javafx.fxml;
    exports ch.vilki.jfxldap.backend;
    opens ch.vilki.jfxldap.backend to javafx.fxml;

}