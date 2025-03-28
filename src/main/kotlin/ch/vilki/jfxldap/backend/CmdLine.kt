package ch.vilki.jfxldap.backend

import ch.vilki.secured.ApplicationVersion
import ch.vilki.secured.SecureString
import com.unboundid.ldap.sdk.LDAPException
import com.unboundid.ldap.sdk.Modification
import com.unboundid.ldap.sdk.ModificationType
import com.unboundid.ldap.sdk.ModifyRequest
import net.sourceforge.argparse4j.ArgumentParsers
import net.sourceforge.argparse4j.inf.ArgumentParser
import net.sourceforge.argparse4j.inf.Namespace
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

class CmdLine : IProgress {
    companion object {
        private val logger: Logger = LogManager.getLogger(CmdLine::class.java)
    }

    private var _parser: ArgumentParser? = null
    private lateinit var _configuration: Config
    private var _ns: Namespace? = null

    @Throws(Exception::class)
    fun runCmd(args: Array<String>) {

        _configuration = Config()
        _configuration.openConfiguration(Config.configurationFile)
        println("jfxldap ${ApplicationVersion.VERSION}")
        initParser()

        try {
            _ns = _parser?.parseArgs(args)
        } catch (e1: Exception) {
            println(e1.localizedMessage)
            _parser?.printUsage()
            return
        }

        val operation = _ns?.getString("op") ?: return
        when {
            operation.equals("export_collection", ignoreCase = true) -> exportCollection()
            operation.equals("get_enviroments", ignoreCase = true) -> getEnviroments()
            operation.equals("import_collection", ignoreCase = true) -> importCollection()
            operation.equals("delete_collection", ignoreCase = true) -> deleteCollection()
            operation.equals("delete_entries", ignoreCase = true) -> deleteEntries()
            operation.equals("delete_attributes", ignoreCase = true) -> deleteAttributes()
        }
    }

    private fun connectToEnviroment(enviroment: String): Connection? {
        val connection: Connection = _configuration.getConnection(enviroment) ?: return null
        var pass: SecureString? = null
        try {
            pass = _configuration.getConnectionPassword(connection)
        } catch (e: Exception) {
            logger.error("Exception in resolving password of the connection ${e.message}", e)
        }
        
        try {
            if (pass == null) {
                val br = BufferedReader(InputStreamReader(System.`in`))
                println("Enter password for enviroment->$enviroment")
                pass = SecureString(br.readLine())
                if (pass.get_value() == null) return null
            }
            connection.connect(pass)
        } catch (e: Exception) {
            logger.error("Error getting password from user", e)
            return null
        }
        
        return connection
    }

    private fun exportCollection() {
        logger.info("Calling export collection method")
        val projectFile = _ns?.getString("projectFile")
        val enviroment = _ns?.getString("env")
        var exportDirectory = _ns?.getString("exportDirectory")
        val projectDir = _ns?.getString("projectDir")
        
        if (projectFile == null && projectDir == null) {
            println("ERROR: no project file or directory defined")
            return
        }
        
        if (exportDirectory == null && projectDir == null) {
            val path = Paths.get(projectFile)
            exportDirectory = path.parent.toAbsolutePath().toString()
        } else if (exportDirectory == null && projectDir != null) {
            exportDirectory = projectDir
        }
        
        logger.info("Connecting to enviroment->$enviroment")
        val connection = connectToEnviroment(enviroment!!) ?: return
        
        if (projectDir != null) {
            logger.info("Exporting from project directory with all project files->$projectDir")
            val source = Paths.get(projectDir)
            
            try {
                val exportDir = exportDirectory!!
                Files.walk(source).filter(Files::isRegularFile).forEach { x ->
                    if (x.fileName.toString().lowercase().endsWith("project.xlsx")) {
                        val collectionsProject = CollectionsProject(_configuration, x.fileName.toAbsolutePath().toString())
                        try {
                            val exportFileName = "${x.fileName}_export.ldif"
                            collectionsProject.readProject(x.toAbsolutePath().toString())
                            logger.info("Exporting ldif now to ->$exportFileName")
                            collectionsProject.exportLdif(exportDir, exportFileName, connection, this)
                        } catch (e: Exception) {
                            println("ERROR Exporting${e.message}")
                            e.printStackTrace()
                        }
                    }
                }
                
                logger.info("All files exported, disconnect now")
                connection.disconect()
            } catch (e: Exception) {
                logger.error("ERROR Exporting${e.message}", e)
            }
        } else {
            logger.info("Exporting from project file specified->$projectFile")
            
            try {
                val path = Paths.get(projectFile!!)
                val collectionsProject = CollectionsProject(_configuration, projectFile)
                val exportFileName = "${path.fileName}_export.ldif"
                collectionsProject.readProject(projectFile)
                collectionsProject.exportLdif(exportDirectory!!, exportFileName, connection, this)
                connection.disconect()
            } catch (e: Exception) {
                System.err.println("Exception occured during export${e.message}")
                return
            }
        }
    }

    private fun deleteCollection() {
        logger.info("delete collection called ")
        val projectFile = _ns?.getString("projectFile")
        val enviroment = _ns?.getString("env")
        
        if (enviroment == null) {
            println("ERROR: enviroment not provided")
            return
        }
        
        if (projectFile == null) {
            println("ERROR: no project file provided")
            return
        }
        
        val connection = connectToEnviroment(enviroment)
        val collectionsProject = CollectionsProject(_configuration, projectFile, connection)
        
        try {
            collectionsProject.readProject(projectFile)
            collectionsProject.deleteAllCollectionEntriesFromEnviromentWithoutParent(this)
        } catch (e: Exception) {
            logger.error("Exception during load of project", e)
            return
        }
    }

    private fun importCollection() {
        logger.info("Import collection called ")
        val importOptions = _ns?.getString("importOptions")
        
        if (importOptions == null) {
            logger.error("No import options have been defined")
            _parser?.printHelp()
            return
        }
        
        val inputFile = _ns?.getString("inputFile")
        val importDirectory = _ns?.getString("importDirectory")
        
        if (inputFile == null && importDirectory == null) {
            logger.error("No input file or directory have been provided")
            _parser?.printUsage()
            return
        }
        
        val enviroment = _ns?.getString("env")
        
        if (enviroment == null) {
            logger.error("No enviroment to import to has been provided")
            _parser?.printUsage()
            return
        }
        
        val connection = connectToEnviroment(enviroment)
        
        if (connection == null) {
            System.err.println("could not connect to->$enviroment")
            return
        }
        
        val collectionImport = CollectionImport()
        collectionImport.set_progress(this)
        
        if (importDirectory != null) {
            if (!confirm("Start dir import ->$importDirectory in ->${connection.getName()}")) return
            collectionImport.uploadDirectory(
                importDirectory, 
                false, 
                connection, 
                null,
                this, 
                CollectionImport.get_IMPORT_OPTIONS(importOptions)
            )
        } else {
            if (!confirm("Start ldif import ->$inputFile in ->${connection.getName()}")) return
            collectionImport.loadFile(inputFile, null)
            
            try {
                collectionImport.importInEnviroment(false, connection, CollectionImport.get_IMPORT_OPTIONS(importOptions))
            } catch (e: Exception) {
                System.err.println("Exception during import${e.message}")
                return
            }
        }
    }

    private fun deleteEntries() {
        val inputFile = _ns?.getString("inputFile")
        val enviroment = _ns?.getString("env")
        
        if (inputFile == null) {
            System.err.println("No collection project file has been specified, " +
                    "need collection project file location with -projectFile PROJECT_FILE")
            return
        }
        
        if (enviroment == null) {
            System.err.println("Enviroment from which entries are to be deleted not specified, specify with -env ENVIROMENT")
            return
        }
        
        if (!confirm("WARNING!!! THIS WILL DELETE ENTRIES FROM ->$enviroment")) return
        
        val connection = connectToEnviroment(enviroment)
        
        if (connection == null) {
            System.err.println("Connection not established, break the operation")
            return
        }
        
        val collectionImport = CollectionImport()
        collectionImport.set_progress(this)
        
        try {
            collectionImport.loadFile(inputFile, null)
        } catch (e: Exception) {
            System.err.println("Error during reading of project${e.message}")
        }
        
        collectionImport.deleteEntries(connection, null)
    }

    @Throws(Exception::class)
    private fun deleteAttributes() {
        val inputFile = _ns?.getString("inputFile")
        val attributes = _ns?.getString("attributes")
        
        if (inputFile == null) throw Exception("No input file has been specified")
        if (!Files.exists(Paths.get(inputFile))) throw Exception("File does not exist")
        if (attributes == null) throw Exception("Attributes not specified")
        
        val connection = Connection(inputFile, this, true)
        connection.set_readOnly(false)
        
        val split = attributes.split(",").toTypedArray()
        val modifyRequests = ArrayList<ModifyRequest>()
        
        for (dn in connection.get_fileEntries().keys) {
            val modifications = ArrayList<Modification>()
            
            for (attName in split) {
                val modification = Modification(ModificationType.DELETE, attName)
                modifications.add(modification)
            }
            
            val modifyRequest = ModifyRequest(dn, modifications)
            modifyRequests.add(modifyRequest)
        }
        
        connection.modify(modifyRequests)
    }

    @Throws(LDAPException::class)
    private fun getEnviroments() {
        if (_configuration == null) {
            System.err.println("ERROR: Did not find configuration file, can not proceed")
            return
        }
        
        for (envKey in _configuration._connections.keys) {
            println(envKey)
            val connection = _configuration._connections[envKey]
            println(connection?.getServer())
            println(connection?.getUser())
            println(connection?.getBaseDN())
            println("--------------------------------------")
        }
    }

    private fun confirm(message: String): Boolean {
        val s = Scanner(System.`in`)
        println(message)
        println("Confirm YES[y] or any other key for NO")
        val name = s.nextLine()
        return name.equals("Y", ignoreCase = true)
    }

    private fun initParser() {
        _parser = ArgumentParsers.newArgumentParser("cncOperations")
            .description("Calls different operations")
            .defaultHelp(true)

        _parser?.addArgument("-op")
            ?.choices("export_collection", "get_enviroments", "import_collection", "delete_entries", "delete_collection", "delete_attributes")
            ?.help("Specify operation type")
            ?.required(true)
            ?.dest("op")

        _parser?.addArgument("-projectFile")
            ?.help("Specify project file ")
            ?.dest("projectFile")

        _parser?.addArgument("-projectDir")
            ?.help("Specify project dir ")
            ?.dest("projectDir")

        _parser?.addArgument("-inputFile")
            ?.help("Specify input file ")
            ?.dest("inputFile")

        _parser?.addArgument("-importDirectory")
            ?.help("Specify project dir ")
            ?.dest("importDirectory")

        _parser?.addArgument("-exportFile")
            ?.help("Specify exportFile file ")
            ?.dest("exportFile")

        _parser?.addArgument("-exportDirectory")
            ?.help("Specify exportDirectory  ")
            ?.dest("exportDirectory")

        _parser?.addArgument("-env")
            ?.choices(_configuration._connections.keys)
            ?.help("Specify Enviroment ${_configuration._connections.keys}")
            ?.dest("env")
            
        _parser?.addArgument("-attributes")?.dest("attributes")

        val importOptions = ArrayList<String>()
        CollectionImport.IMPORT_OPTIONS.values().forEach { x -> importOptions.add(x.toString()) }
        
        _parser?.addArgument("-importOptions")
            ?.choices(importOptions)
            ?.help("Specify import option type->$importOptions")
            ?.dest("importOptions")
    }

    override fun setProgress(progress: Double, description: String?) {
        if (description != null) {
            println("$description [$progress]")
        } else {
            println("Progress [$progress]")
        }
    }

    override fun signalTaskDone(taskName: String?, description: String?, e: Exception?) {
        if (e != null) {
            System.err.println("ERROR Task->$taskName , description->$description  ${e}")
            e.printStackTrace()
        } else {
            println("TASK DONE->$taskName, description->$description")
        }
    }

    override fun setProgress(taskName: String?, progress: Double) {
        if (taskName != null) {
            println("$taskName [$progress]")
        } else {
            println("Progress [$progress]")
        }
    }
}
