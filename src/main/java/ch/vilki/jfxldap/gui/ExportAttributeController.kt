package ch.vilki.jfxldap.gui

import ch.vilki.jfxldap.Main
import ch.vilki.jfxldap.backend.Connection
import com.unboundid.ldap.sdk.LDAPException
import com.unboundid.ldap.sdk.SearchScope
import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.VBox
import javafx.stage.DirectoryChooser
import javafx.stage.Modality
import javafx.stage.Stage
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

class ExportAttributeController {
    companion object {
        private val logger: Logger = LogManager.getLogger(ExportAttributeController::class.java)
    }

    private lateinit var stage: Stage
    private lateinit var scene: Scene
    private lateinit var rootPane: VBox

    @FXML private lateinit var comboAttributes: ComboBox<String>
    @FXML private lateinit var txtExportPath: TextField
    @FXML private lateinit var txtFilter: TextField
    @FXML private lateinit var btnBrowse: Button
    @FXML private lateinit var btnExport: Button
    @FXML private lateinit var btnCancel: Button
    @FXML private lateinit var progressBar: ProgressBar
    @FXML private lateinit var lblStatus: Label

    private lateinit var ldapConnection: Connection
    private lateinit var baseDN: String
    private var exportPath: String = ""
    private lateinit var allAttributes: List<String>

    fun showExportAttributeWindow(connection: Connection, dn: String) {
        try {
            ldapConnection = connection
            baseDN = dn
            
            // Create the stage if not already created
            if (!::stage.isInitialized) {
                val loader = FXMLLoader(javaClass.getResource("/ch/vilki/jfxldap/fxml/export_attribute.fxml"))
                loader.setController(this)
                rootPane = loader.load()
                scene = Scene(rootPane)
                stage = Stage()
                stage.title = "Export Attribute"
                stage.scene = scene
                stage.initModality(Modality.APPLICATION_MODAL)
                stage.isResizable = false
                initialize()
            }
            
            // Load available attributes from the schema
            loadAttributes()
            
            // Show the stage
            stage.show()
        } catch (e: Exception) {
            logger.error("Error showing export attribute window", e)
            GuiHelper.EXCEPTION("Error", "Could not open export attribute window", e)
        }
    }

    private fun initialize() {
        // Configure the browse button
        btnBrowse.setOnAction {
            val directoryChooser = DirectoryChooser()
            directoryChooser.title = "Select Export Directory"
            if (exportPath.isNotEmpty()) {
                val dir = File(exportPath)
                if (dir.exists()) {
                    directoryChooser.initialDirectory = dir
                }
            }
            
            val selectedDirectory = directoryChooser.showDialog(stage)
            if (selectedDirectory != null) {
                exportPath = selectedDirectory.absolutePath
                txtExportPath.text = exportPath
            }
        }
        
        // Configure the filter text field
        txtFilter.textProperty().addListener { _, _, newValue ->
            filterAttributes(newValue)
        }
        
        // Configure the export button
        btnExport.setOnAction {
            val selectedAttribute = comboAttributes.selectionModel.selectedItem
            if (selectedAttribute == null) {
                GuiHelper.ERROR("Validation Error", "Please select an attribute to export")
                return@setOnAction
            }
            
            if (exportPath.isEmpty()) {
                GuiHelper.ERROR("Validation Error", "Please select an export directory")
                return@setOnAction
            }
            
            executeExport(selectedAttribute)
        }
        
        // Configure the cancel button
        btnCancel.setOnAction {
            stage.close()
        }
        
        // Initialize progress bar as invisible
        progressBar.progress = 0.0
        progressBar.isVisible = false
        lblStatus.text = ""
    }
    
    private fun loadAttributes() {
        try {
            val attributes = ldapConnection.schemaAttributes
            
            // Sort attributes alphabetically
            allAttributes = attributes.sorted()
            
            // Initialize with all attributes
            comboAttributes.items = FXCollections.observableArrayList(allAttributes)
            
            // Select the first item if available
            if (allAttributes.isNotEmpty()) {
                comboAttributes.selectionModel.selectFirst()
            }
        } catch (e: Exception) {
            logger.error("Error loading attributes", e)
            GuiHelper.EXCEPTION("Error", "Failed to load attributes", e)
        }
    }
    
    private fun filterAttributes(filterText: String) {
        try {
            // Check if we have attributes to filter
            if (!this::allAttributes.isInitialized || allAttributes.isEmpty()) return
            
            // Save the current selection if any
            val currentSelection = comboAttributes.selectionModel.selectedItem
            
            // Filter attributes based on the filter text
            val filteredAttributes = if (filterText.isBlank()) {
                allAttributes
            } else {
                allAttributes.filter { it.contains(filterText, ignoreCase = true) }
            }
            
            // Update the combo box items
            comboAttributes.items = FXCollections.observableArrayList(filteredAttributes)
            
            // Try to restore the previous selection if it still exists in the filtered list
            if (currentSelection != null && filteredAttributes.contains(currentSelection)) {
                comboAttributes.selectionModel.select(currentSelection)
            } else if (filteredAttributes.isNotEmpty()) {
                // Otherwise select the first item
                comboAttributes.selectionModel.selectFirst()
            }
        } catch (e: Exception) {
            logger.error("Error filtering attributes", e)
        }
    }
    
    private fun executeExport(attribute: String) {
        progressBar.isVisible = true
        btnExport.isDisable = true
        lblStatus.text = "Starting export..."
        
        // Run the export in a background thread
        Thread {
            try {
                // Process the export starting from the base DN
                processExport(baseDN, attribute, exportPath)
                
                javafx.application.Platform.runLater {
                    GuiHelper.INFO("Export Complete", "Attribute export completed successfully")
                    progressBar.progress = 1.0
                    lblStatus.text = "Export completed"
                    btnExport.isDisable = false
                }
            } catch (e: Exception) {
                logger.error("Error during export", e)
                javafx.application.Platform.runLater {
                    GuiHelper.EXCEPTION("Export Error", "Failed to complete the export", e)
                    progressBar.progress = 0.0
                    lblStatus.text = "Export failed"
                    btnExport.isDisable = false
                }
            }
        }.start()
    }
    
    private fun processExport(baseDN: String, attribute: String, basePath: String) {
        try {
            // Count the number of RDN components in the base DN to determine relative paths later
            val baseComponentCount = baseDN.split(",").size
            
            // Find all entries under the base DN that have the requested attribute
            val searchRequest = ldapConnection.search(
                baseDN, 
                SearchScope.SUB, 
                com.unboundid.ldap.sdk.Filter.createPresenceFilter(attribute),
                "dn", "cn", attribute
            )
            
            // Process each entry with the attribute
            for (entry in searchRequest.searchEntries) {
                val entryDn = entry.dn
                val attributeValue = entry.getAttributeValue(attribute)
                
                if (attributeValue != null) {
                    try {
                        // Determine the relative path and filename
                        val pathInfo = determinePathAndFilename(entryDn, baseDN, baseComponentCount)
                        val relativePath = pathInfo.first
                        val fileName = pathInfo.second
                        
                        // Create full directory path
                        val outputDir = if (relativePath.isEmpty()) {
                            Paths.get(basePath)
                        } else {
                            val dirPath = Paths.get(basePath, *relativePath.split("/").toTypedArray())
                            if (!Files.exists(dirPath)) {
                                Files.createDirectories(dirPath)
                            }
                            dirPath
                        }
                        
                        // Write the file
                        val filePath = outputDir.resolve(fileName)
                        Files.write(filePath, attributeValue.toByteArray())
                        
                        logger.info("Exported $attribute from $entryDn to $filePath")
                        
                        // Update status
                        javafx.application.Platform.runLater {
                            lblStatus.text = "Exported: $fileName"
                        }
                    } catch (e: Exception) {
                        logger.error("Error exporting entry $entryDn: ${e.message}", e)
                        // Continue with other entries even if one fails
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error processing export from DN: $baseDN", e)
            throw e
        }
    }
    
    /**
     * Determines the relative path and filename for an entry based on its DN and the base DN.
     * Returns a Pair where first is the relative path and second is the filename.
     */
    private fun determinePathAndFilename(entryDn: String, baseDN: String, baseComponentCount: Int): Pair<String, String> {
        val dnComponents = entryDn.split(",")
        // Get the entry's CN (the filename)
        val entryCn = extractCnFromDn(dnComponents.first())
        val fileName = if (entryCn.endsWith(".js")) entryCn else "$entryCn.txt"
        
        // If this is a direct child of baseDN, no subdirectory needed
        if (dnComponents.size <= baseComponentCount + 1) {
            return Pair("", fileName)
        }
        
        // There are intermediate nodes, create subdirectory structure
        // Extract components between the entry and the base DN (in reverse order)
        val pathSize = dnComponents.size - baseComponentCount - 1
        if (pathSize <= 0) {
            return Pair("", fileName)
        }
        
        val intermediateComponents = dnComponents.subList(1, dnComponents.size - baseComponentCount)
        // Get CNs from these components to form directory path
        val directories = intermediateComponents.map { extractCnFromDn(it) }
        val relativePath = directories.joinToString(separator = "/")
        
        return Pair(relativePath, fileName)
    }
    
    private fun extractCnFromDn(dnComponent: String): String {
        // Simple extraction of CN from DN component
        val cnValue = dnComponent.substringAfter("=").trim()
        return cnValue.ifEmpty { "unknown" }
    }
}
