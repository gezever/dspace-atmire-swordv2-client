package com.atmire.lne.swordv2.client;

import org.apache.commons.cli.*;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.swordapp.client.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

/**
 * Script to make a SWORDv2 deposit to the LNE DSpace instance
 * Example: mvn exec:java -Dexec.mainClass="com.atmire.lne.swordv2.client.Swordv2ClientScript" -Dexec.args="-f /Users/tom/temp/LNE-dossier.zip -m 'application/zip' -p src/main/resources/swordv2-server.properties -s abc1234 -i"
 */
public class Swordv2ClientScript {

    private static Log log = LogFactory.getLog(Swordv2ClientScript.class);

    private static final String FILE_PATH_FLAG = "f";
    private static final String DIRECTORY_PATH_FLAG = "d";
    private static final String PROPERTIES_PATH_FLAG = "p";
    private static final String MIMETYPE_FLAG = "m";
    private static final String SLUG_FLAG = "s";
    private static final String IN_PROGRESS_FLAG = "i";
    private static final String OPENAMSSOID_FLAG = "o";


    private static String dspaceSwordUrl;
    private static String ePerson;
    private static String ePersonPassword;
    private static String openAmSSOID;

    public static void main(String[] args) {
        int result = 1;
        Options options = createCommandLineOptions();
        CommandLine cmd = parseCommandLineArguments(options, args);

        if(cmd != null) {
            String filePath = cmd.getOptionValue(FILE_PATH_FLAG);
            String directoryPath = cmd.getOptionValue(DIRECTORY_PATH_FLAG);
            String mimeType = cmd.getOptionValue(MIMETYPE_FLAG);
            String suggestedIdentifier = cmd.getOptionValue(SLUG_FLAG);
            boolean inProgress = cmd.hasOption(IN_PROGRESS_FLAG);
            String propertiesPath = cmd.getOptionValue(PROPERTIES_PATH_FLAG);
            boolean skipOpenAm = cmd.hasOption(OPENAMSSOID_FLAG);

            try {
                if (!skipOpenAm) {
                    requestOpenAmSSOID();
                }

                loadSwordv2ServerProperties(Paths.get(propertiesPath));

                SWORDCollection targetCollection = getTargetCollection();

                //Upload all zips in a directory
                if (StringUtils.isNotEmpty(directoryPath)) {
                    int fails = 0;
                    int imported = 0;

                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(directoryPath))) {
                        for (Path path : stream) {
                            if(path.getFileName().toString().endsWith(".zip") || path.getFileName().toString().endsWith(".ZIP")) {
                                log.info("Uploading file " + path.toAbsolutePath().toString());

                                try {
                                    result = doSwordDeposit(path, mimeType, suggestedIdentifier, inProgress, targetCollection);
                                    imported++;
                                } catch(Exception e) {
                                    log.error("There is a problem with file " + path.toString() + ": " + e.getMessage());
                                    fails++;
                                }
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    log.info("Successfully imported " + imported + " files and encountered " + fails + " failures");

                //Upload a single zip
                } else if (StringUtils.isNotEmpty(filePath)) {
                    result = doSwordDeposit(Paths.get(filePath), mimeType, suggestedIdentifier, inProgress, targetCollection);

                //We don't know what to do
                } else {
                    log.error("You have to specify at least a file or a directory");
                }

            } catch (SWORDClientException e) {
                log.error("Unable to connect to SWORD server", e);
            } catch (IOException e) {
                log.error("Unable to open archive file or swordv2-server.properties file", e);
            } catch (SWORDError e) {
                log.error("SWORD server was unable to process the request, received response code " + e.getStatus(), e);
            } catch (ProtocolViolationException e) {
                log.error("SWORD server protocol violation", e);
            }
        }

        System.exit(result);
    }

    private static void requestOpenAmSSOID() {
        log.info("If you want to authenticate with an OpenAM SSO ID, you can enter it now. Otherwise, just press enter: ");
        Scanner scanner = new Scanner(System.in);
        String ssoId = scanner.next();
        if(StringUtils.isNotEmpty(ssoId)) {
            openAmSSOID = ssoId;
        } else {
            openAmSSOID = null;
        }
    }

    private static SWORDCollection getTargetCollection() throws SWORDClientException, ProtocolViolationException {
        SWORDClient client = new SWORDClient();
        ServiceDocument sd = client.getServiceDocument(dspaceSwordUrl, getAuthCredentials());
        SWORDWorkspace dspaceRepository = sd.getWorkspaces().get(0);

        SWORDCollection targetCollection = requestTargetCollections(dspaceRepository);
        if (targetCollection == null) {
            throw new SWORDClientException("The target collection does not exist, we cannot continue");
        } else {
            log.info("The selected target collection is " + targetCollection.getTitle());
        }
        return targetCollection;
    }

    private static AuthCredentials getAuthCredentials() {
        AuthCredentials authCredentials = new AuthCredentials(ePerson, ePersonPassword);
        authCredentials.setOpenAmSSOID(openAmSSOID);
        return authCredentials;
    }

    private static int doSwordDeposit(final Path filePath, final String mimeType, final String suggestedIdentifier, final boolean inProgress, final SWORDCollection targetCollection) throws IOException, ProtocolViolationException, SWORDError, SWORDClientException {
        try (InputStream fileStream = Files.newInputStream(filePath)) {
            SWORDClient client = new SWORDClient();

            Deposit deposit = new Deposit();
            deposit.setFile(fileStream);
            deposit.setMimeType(mimeType);
            deposit.setFilename(filePath.getFileName().toString());
            deposit.setPackaging(UriRegistry.PACKAGE_DSPACE_SAF);
            deposit.setInProgress(inProgress);
            deposit.setSuggestedIdentifier(suggestedIdentifier);

            DepositReceipt receipt = client.deposit(targetCollection, deposit, getAuthCredentials());

            log.info(buildReceiptReport(receipt));


            log.info("File :["+filePath+"] StatusCode :["+receipt+"]");
        }

        return 0;
    }

    private static void loadSwordv2ServerProperties(final Path propertiesPath) throws IOException {
        InputStream props = Files.newInputStream(propertiesPath);
        Properties properties = new Properties();
        properties.load(props);

        dspaceSwordUrl = properties.getProperty("sdIRI");
        ePerson = properties.getProperty("user");
        ePersonPassword = properties.getProperty("pass");
    }

    private static String buildReceiptReport(final DepositReceipt receipt) throws SWORDClientException {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(title("RECEIPT REPORT"));
        stringBuilder.append("Status code: " + receipt.getStatusCode() + "\n");
        stringBuilder.append("Location: " + receipt.getLocation() + "\n");
        stringBuilder.append("Original Deposit link: " + receipt.getOriginalDepositLink().getHref() + "\n");
        stringBuilder.append("Edit media link: " + receipt.getEditMediaLink().getHref() + "\n");
        stringBuilder.append("Atom statement link: " + receipt.getAtomStatementLink().getHref() + "\n");
        stringBuilder.append("Content link: " + receipt.getContentLink().getHref() + "\n");
        stringBuilder.append("Edit link: " + receipt.getEditLink().getHref() + "\n");
        stringBuilder.append("ORE statement link: " + receipt.getOREStatementLink().getHref() + "\n");
        stringBuilder.append("Packaging: " + receipt.getPackaging() + "\n");
        stringBuilder.append("RDF link: " + receipt.getStatementLink("application/rdf+xml").getHref() + "\n");
        stringBuilder.append("Atom link: " + receipt.getStatementLink("application/atom+xml;type=feed").getHref() + "\n");
        stringBuilder.append("SWORD edit link: " + receipt.getSwordEditLink().getHref() + "\n");
        stringBuilder.append("DSpace link: " + receipt.getSplashPageLink().getHref() + "\n");
        stringBuilder.append("Status description: " + receipt.getTreatment() + "\n");

        return stringBuilder.toString();
    }

    private static CommandLine parseCommandLineArguments(final Options options, final String[] args) {
        CommandLineParser parser = new PosixParser();
        try {
            return parser.parse(options, args);
        } catch (ParseException e) {
            printUsage(options);
            return null;
        }
    }

    private static void printUsage(final Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("com.atmire.lne.swordv2.client.Swordv2ClientScript", options, true);
    }

    private static Options createCommandLineOptions() {
        Options options = new Options();
        options.addOption(OptionBuilder.hasArg(true).withArgName("path").withLongOpt("file")
                .withDescription("Path to the archive file that needs to be uploaded").create(FILE_PATH_FLAG));
        options.addOption(OptionBuilder.hasArg(true).withArgName("path").withLongOpt("directory")
                .withDescription("Path to the directory containing all archive files that needs to be uploaded").create(DIRECTORY_PATH_FLAG));
        options.addOption(OptionBuilder.hasArg(true).withArgName("path").isRequired().withLongOpt("server-properties")
                .withDescription("Path to the server properties file to authenticate").create(PROPERTIES_PATH_FLAG));
        options.addOption(OptionBuilder.hasArg(true).withArgName("type").isRequired().withLongOpt("mimetype")
                .withDescription("The mimetype of the archive file").create(MIMETYPE_FLAG));
        options.addOption(OptionBuilder.hasArg(true).withArgName("id").withLongOpt("slug")
                .withDescription("The suggested identifier to pass to the SWORD server").create(SLUG_FLAG));
        options.addOption(OptionBuilder.withLongOpt("in-progress")
                .withDescription("When used, the script will send a request with the In-Progress header set to true.")
                .create(IN_PROGRESS_FLAG));

        options.addOption(OptionBuilder.hasArg(false).
            withLongOpt("no-openam")
            .withDescription("When used, no OpenAM SSO ID will be asked")
            .create(OPENAMSSOID_FLAG));



        return options;
    }

    private static SWORDCollection requestTargetCollections(final SWORDWorkspace dspaceRepository) {
        log.info("The available collections and their allowed package types are: ");
        int i = 0;
        List<SWORDCollection> collections = ListUtils.emptyIfNull(dspaceRepository.getCollections());
        for (SWORDCollection swordCollection : collections) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(i + ": ");
            stringBuilder.append(swordCollection.getTitle());
            stringBuilder.append(" - ");
            stringBuilder.append(swordCollection.getAbstract());
            stringBuilder.append(" (");
            stringBuilder.append(StringUtils.join(ListUtils.emptyIfNull(swordCollection.getAcceptPackaging()).iterator(), ", "));
            stringBuilder.append(")\n");
            log.info(stringBuilder.toString());
            i++;
        }

        log.info("Please enter the number of the collection to use for the deposit: ");
        Scanner scanner = new Scanner(System.in);
        int collectionIndex = scanner.nextInt();
        if(collectionIndex >= 0 && collectionIndex < collections.size()) {
            return collections.get(collectionIndex);
        } else {
            return null;
        }
    }

    private static String title(final String title) {
        return String.format("\n*************** %s ***************\n", title);
    }

}
