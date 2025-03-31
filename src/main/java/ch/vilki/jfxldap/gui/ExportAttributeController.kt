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
import ch.vilki.jfxldap.gui.GuiHelper

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
    @FXML private lateinit var comboSearchScope: ComboBox<String>

    private lateinit var ldapConnection: Connection
    private lateinit var baseDN: String
    private var exportPath: String = ""
    private lateinit var allAttributes: List<String>
    private var lastFilter: String = ""  // Store the last used filter

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
            
            // Restore last filter if available
            if (lastFilter.isNotEmpty()) {
                txtFilter.text = lastFilter
                filterAttributes(lastFilter)
            }
            
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
            try {
                // Use GuiHelper.selectFile with proper configuration
                val file = GuiHelper.selectFile(
                    Main._main,
                    null,
                    "Select Export Directory",
                    GuiHelper.FILE_OPTIONS.OPEN_DIRECTORY
                )
                
                if (file != null) {
                    exportPath = file.absolutePath
                    txtExportPath.text = exportPath
                    // The directory is already saved to configuration by GuiHelper.selectFile
                }
            } catch (e: Exception) {
                logger.error("Error selecting directory", e)
                GuiHelper.EXCEPTION("Error", "Failed to select directory", e)
            }
        }
        
        // Configure the filter text field
        txtFilter.textProperty().addListener { _, _, newValue ->
            lastFilter = newValue  // Save the filter when it changes
            filterAttributes(newValue)
        }
        
        // Setup search scope combo box
        comboSearchScope.items = FXCollections.observableArrayList(
            "Base only", 
            "Direct children only", 
            "All descendants (recursive)"
        )
        comboSearchScope.selectionModel.select(2) // Default to recursive search
        
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
        
        // Determine search scope based on user selection
        val searchScope = when (comboSearchScope.selectionModel.selectedIndex) {
            0 -> SearchScope.BASE
            1 -> SearchScope.ONE 
            else -> SearchScope.SUB
        }
        
        // Run the export in a background thread
        Thread {
            try {
                // Process the export starting from the base DN
                processExport(baseDN, attribute, exportPath, searchScope)
                
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
    
    private fun processExport(baseDN: String, attribute: String, basePath: String, searchScope: SearchScope) {
        try {
            // Find all entries under the base DN that have the requested attribute
            val searchRequest = ldapConnection.search(
                baseDN, 
                searchScope, 
                com.unboundid.ldap.sdk.Filter.createPresenceFilter(attribute),
                "dn", "cn", attribute
            )
            
            // Process each entry with the attribute
            for (entry in searchRequest.searchEntries) {
                val entryDn = entry.dn
                val attributeValue = entry.getAttributeValue(attribute)
                
                if (attributeValue != null) {
                    try {
                        // Extract the file name and directory path
                        val (directoryPath, fileName) = getPathAndFilename(entryDn, baseDN, basePath)
                        
                        // Create directories if they don't exist
                        if (!Files.exists(directoryPath)) {
                            Files.createDirectories(directoryPath)
                        }
                        
                        // Write the file
                        val filePath = directoryPath.resolve(fileName)
                        Files.write(filePath, attributeValue.toByteArray())
                        
                        logger.info("Exported $attribute from $entryDn to $filePath")
                        
                        // Update status
                        Platform.runLater {
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
     * Determines the directory path and filename for an entry based on its DN and the base DN.
     * Returns a Pair where first is the Path to the directory and second is the filename.
     */
    private fun getPathAndFilename(entryDn: String, baseDN: String, basePath: String): Pair<java.nio.file.Path, String> {
        // Split the DNs into components
        val entryComponents = entryDn.split(",").map { it.trim() }
        val baseComponents = baseDN.split(",").map { it.trim() }
        
        // Find where the base DN starts in the entry DN
        var baseStartIndex = -1
        for (i in entryComponents.indices) {
            if (i + baseComponents.size <= entryComponents.size) {
                val potentialBase = entryComponents.subList(i, i + baseComponents.size)
                if (potentialBase.size == baseComponents.size && 
                    potentialBase.withIndex().all { (j, comp) -> 
                        comp.equals(baseComponents[j], ignoreCase = true) 
                    }) {
                    baseStartIndex = i
                    break
                }
            }
        }
        
        if (baseStartIndex == -1) {
            // If we can't find the base DN in the entry DN, use a default approach
            logger.warn("Could not find base DN in entry DN: $entryDn")
            return Pair(Paths.get(basePath), "${getCnFromDn(entryComponents[0])}.txt")
        }
        
        // Extract the path components (between entry and base DN)
        val pathComponents = if (baseStartIndex > 1) {
            entryComponents.subList(1, baseStartIndex).reversed()
        } else {
            listOf() // No intermediate components
        }
        
        // Get the CN (filename)
        val cn = getCnFromDn(entryComponents[0])
        val fileName = if (cn.endsWith(".xml") || cn.endsWith(".js")) cn else "$cn.txt"
        
        // Build directory path
        val dirPath = if (pathComponents.isEmpty()) {
            Paths.get(basePath)
        } else {
            val dirs = pathComponents.map { getCnFromDn(it) }
            Paths.get(basePath, *dirs.toTypedArray())
        }
        
        return Pair(dirPath, fileName)
    }
    
    /**
     * Extracts the CN value from a DN component
     */
    private fun getCnFromDn(dnComponent: String): String {
        // Extract CN value
        val equalsIndex = dnComponent.indexOf('=')
        if (equalsIndex > 0 && equalsIndex < dnComponent.length - 1) {
            return dnComponent.substring(equalsIndex + 1).trim()
        }
        return dnComponent.trim()
    }
}
