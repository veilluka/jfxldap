package ch.vilki.jfxldap.backend;

import ch.vilki.secured.SecureString;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.Modification;
import com.unboundid.ldap.sdk.ModificationType;
import com.unboundid.ldap.sdk.ModifyRequest;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CmdLine implements IProgress {

    static Logger logger = LogManager.getLogger(CmdLine.class);
    ArgumentParser _parser = null;
    Config _configuration;
    Namespace _ns = null;

    public void runCmd(String args[]) throws Exception {
        _configuration = new Config();
        _configuration.openConfiguration(Config.getConfigurationFile());
        initParser();
        Namespace ns;

        try {
            _ns = _parser.parseArgs(args);
        } catch (Exception e1) {
            logger.error("Exception in runCMD," + e1.getMessage(), e1);
            _parser.printUsage();
            return;
        }
        String operation = _ns.get("op");
        if (operation.equalsIgnoreCase("export_collection")) {
            exportCollection();
        }
        if (operation.equalsIgnoreCase("get_enviroments")) getEnviroments();
        if (operation.equalsIgnoreCase("import_collection")) importCollection();
        if (operation.equalsIgnoreCase("delete_collection")) deleteCollection();
        if (operation.equalsIgnoreCase("delete_entries")) deleteEntries();
        if (operation.equalsIgnoreCase("delete_attributes")) deleteAttributes();
    }

    private Connection connectToEnviroment(String enviroment) {
        Connection connection = _configuration.getConnection(enviroment);
        SecureString pass = null;
        try {
            pass = _configuration.getConnectionPassword(connection);
        } catch (Exception e) {
            logger.error("Exception in resolving password of the connection " + e.getMessage(), e);
        }
        try {
            if (pass == null) {
                BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
                System.out.println("Enter password for enviroment->" + enviroment);
                pass = new SecureString(br.readLine());
                if (pass.get_value() == null) return null;
            }
            connection.connect(pass);
        } catch (Exception e) {
            logger.error("Error getting password from user", e);
            return null;
        }
        return connection;
    }

    private void exportCollection() {
        logger.info("Calling export collection method");
        String projectFile = _ns.get("projectFile");
        String enviroment = _ns.get("env");
        String exportDirectory = _ns.get("exportDirectory");
        String projectDir = _ns.get("projectDir");
        if (projectFile == null && projectDir == null) {
            System.out.println("ERROR: no project file or directory defined");
            return;
        }
        if (exportDirectory == null && projectDir == null) {
            Path path = Paths.get(projectFile);
            exportDirectory = path.getParent().toAbsolutePath().toString();
        } else if (exportDirectory == null && projectDir != null) {
            exportDirectory = projectDir;
        }
        logger.info("Connecting to enviroment->" + enviroment);
        Connection connection = connectToEnviroment(enviroment);
        if (projectDir != null) {
            logger.info("Exporting from project directory with all project files->" + projectDir);
            Path source = Paths.get(projectDir);
            try {
                final String exportDir = exportDirectory;
                Files.walk(source).filter(Files::isRegularFile).forEach(x -> {
                    if (x.getFileName().toString().toLowerCase().endsWith("project.xlsx")) {
                        CollectionsProject collectionsProject = new CollectionsProject(_configuration, x.getFileName().toAbsolutePath().toString());
                        try {
                            String exportFileName = x.getFileName() + "_export.ldif";
                            collectionsProject.readProject(x.toAbsolutePath().toString());
                            logger.info("Exporting ldif now to ->" + exportFileName);
                            collectionsProject.exportLdif(exportDir, exportFileName, connection, this);
                        } catch (Exception e) {
                            System.out.println("ERROR Exporting" + e.getMessage());
                            e.printStackTrace();
                        }
                    }


                });
                logger.info("All files exported, disconnect now");
                connection.disconect();
            } catch (Exception e) {
                logger.error("ERROR Exporting" + e.getMessage(), e);
            }
        } else {
            logger.info("Exporting from project file specified->" + projectFile);
            try {
                Path path = Paths.get(projectFile);
                CollectionsProject collectionsProject = new CollectionsProject(_configuration, projectFile);
                String exportFileName = path.getFileName() + "_export.ldif";
                collectionsProject.readProject(projectFile);
                collectionsProject.exportLdif(exportDirectory, exportFileName, connection, this);
                connection.disconect();

            } catch (Exception e) {
                System.err.println("Exception occured during export" + e.getMessage());
                return;
            }
        }
    }

    private void deleteCollection() {
        logger.info("delete collection called ");
        String projectFile = _ns.get("projectFile");
        String enviroment = _ns.get("env");
        if (enviroment == null) {
            System.out.println("ERROR: enviroment not  provided");
            return;
        }
        if (projectFile == null) {
            System.out.println("ERROR: no project file  provided");
            return;
        }
        Connection connection = connectToEnviroment(enviroment);
        CollectionsProject collectionsProject = new CollectionsProject(_configuration, projectFile, connection);
        try {
            collectionsProject.readProject(projectFile);
            collectionsProject.deleteAllCollectionEntriesFromEnviromentWithoutParent(this);
        } catch (Exception e) {
            logger.error("Exception during load of project", e);
            return;
        }
    }

    private void importCollection() {
        logger.info("Import collection called ");
        String importOptions = _ns.get("importOptions");
        if (importOptions == null) {
            logger.error("No import options have been defined");
            _parser.printHelp();
            return;
        }
        String inputFile = _ns.get("inputFile");
        String importDirectory = _ns.get("importDirectory");
        if (inputFile == null && importDirectory == null) {
            logger.error("No input file or directory have been provided");
            _parser.printUsage();
            return;
        }
        String enviroment = _ns.get("env");
        if (enviroment == null) {
            logger.error("No enviroment to import to has been provided");
            _parser.printUsage();
            return;
        }
        Connection connection = connectToEnviroment(enviroment);
        if (connection == null) {
            System.err.println("could not connect to->" + enviroment);
            return;
        }
        CollectionImport collectionImport = new CollectionImport();
        collectionImport.set_progress(this);
        if (importDirectory != null) {
            if (!confirm("Start dir import ->" + importDirectory + " in ->" + connection.getName())) return;
            collectionImport.uploadDirectory(importDirectory, false, connection, null,
                    this, CollectionImport.get_IMPORT_OPTIONS(importOptions));
        } else {
            if (!confirm("Start ldif import ->" + inputFile + " in ->" + connection.getName())) return;
            collectionImport.loadFile(inputFile, null);
            try {
                collectionImport.importInEnviroment(false, connection, CollectionImport.get_IMPORT_OPTIONS(importOptions));
            } catch (Exception e) {
                System.err.println("Exception during import" + e.getMessage());
                return;
            }
        }
    }

    private void deleteEntries() {
        String inputFile = _ns.get("inputFile");
        String enviroment = _ns.get("env");
        if (inputFile == null) {
            System.err.println("No collection project file has been specified, " +
                    "need collection project file location with -projectFile PROJECT_FILE");
            return;
        }
        if (enviroment == null) {
            System.err.println("Enviroment from which entries are to be deleted not specified, specify with -env ENVIROMENT");
            return;
        }
        if (!confirm("WARNING!!! THIS WILL DELETE ENTRIES FROM ->" + enviroment)) return;
        Connection connection = connectToEnviroment(enviroment);
        if (connection == null) {
            System.err.println("Connection not established, break the operation");
            return;
        }
        CollectionImport collectionImport = new CollectionImport();
        collectionImport.set_progress(this);
        try {
            collectionImport.loadFile(inputFile, null);

        } catch (Exception e) {
            System.err.println("Error during reading of project" + e.getMessage());
        }
        collectionImport.deleteEntries(connection, null);
    }

    private void deleteAttributes() throws Exception {
        String inputFile = _ns.get("inputFile");
        String attributes = _ns.get("attributes");
        if (inputFile == null) throw new Exception("No input file has been specified");
        if (!Files.exists(Paths.get(inputFile))) throw new Exception("File does not exist");
        if (attributes == null) throw new Exception("Attributes not specified");
        Connection connection = new Connection(inputFile,this,true);
        connection.set_readOnly(false);
        String split[] = attributes.split(",");
        List<ModifyRequest> modifyRequests = new ArrayList<>();
        for (String dn : connection.get_fileEntries().keySet()) {
            List<Modification> modifications = new ArrayList<>();
            for (String attName : split) {
                Modification modification = new Modification(ModificationType.DELETE, attName);
                modifications.add(modification);
            }

            ModifyRequest modifyRequest = new ModifyRequest(dn, modifications);
            modifyRequests.add(modifyRequest);
        }
        connection.modify(modifyRequests);
    }

    private void getEnviroments() throws LDAPException {

        if (_configuration == null) {
            System.err.println("ERROR: Did not find configuration file, can not proceed");
            return;
        }
        for (String envKey : _configuration._connections.keySet()) {
            System.out.println(envKey);
            Connection connection = _configuration._connections.get(envKey);
            System.out.println(connection.getServer());
            System.out.println(connection.getUser());
            System.out.println(connection.getBaseDN());
            System.out.println("--------------------------------------");
        }
    }

    private boolean confirm(String message) {
        java.util.Scanner s = new java.util.Scanner(System.in);
        System.out.println(message);
        System.out.println("Confirm YES[y] or any other key for NO");
        String name = s.nextLine();
        if (name.equalsIgnoreCase("Y")) return true;
        return false;
    }

    private void initParser() {
        _parser = ArgumentParsers.newArgumentParser("cncOperations")
                .description("Calls different operations")
                .defaultHelp(true);

        _parser.addArgument("-op")
                .choices("export_collection", "get_enviroments", "import_collection", "delete_entries", "delete_collection", "delete_attributes")
                .help("Specify operation type")
                .required(true)
                .dest("op");

        _parser.addArgument("-projectFile")
                .help("Specify project file ")
                .dest("projectFile");

        _parser.addArgument("-projectDir")
                .help("Specify project dir ")
                .dest("projectDir");

        _parser.addArgument("-inputFile")
                .help("Specify input file ")
                .dest("inputFile");

        _parser.addArgument("-importDirectory")
                .help("Specify project dir ")
                .dest("importDirectory");


        _parser.addArgument("-exportFile")
                .help("Specify exportFile file ")
                .dest("exportFile");

        _parser.addArgument("-exportDirectory")
                .help("Specify exportDirectory  ")
                .dest("exportDirectory");

        _parser.addArgument("-env")
                .choices(_configuration._connections.keySet())
                .help("Specify Enviroment " + _configuration._connections.keySet())
                .dest("env");
        _parser.addArgument("-attributes").dest("attributes");


        List<String> importOptions = new ArrayList<>();
        Arrays.stream(CollectionImport.IMPORT_OPTIONS.values()).forEach(x -> importOptions.add(x.toString()));
        _parser.addArgument("-importOptions")
                .choices(importOptions)
                .help("Specify import option type->" + importOptions)
                .dest("importOptions");
    }

    @Override
    public void setProgress(double progress, String description) {
        if (description != null) {
            System.out.println(description + " [" + progress + "]");
        } else {
            System.out.println("Progress" + " [" + progress + "]");
        }
    }

    @Override
    public void signalTaskDone(String taskName, String description, Exception e) {
        if (e != null) {
            System.err.println("ERROR Task->" + taskName + " , description->" + description + "  " + e.toString());
            e.printStackTrace();
        } else {
            System.out.println("TASK DONE->" + taskName + ", description->" + description);
        }

    }

    @Override
    public void setProgress(String taskName, double progress) {
        if (taskName != null) {
            System.out.println(taskName + " [" + progress + "]");
        } else {
            System.out.println("Progress" + " [" + progress + "]");
        }
    }
}
