SWORD 2.0 Client
================

This client library is an implementation of the SWORD 2.0 standard defined here

http://swordapp.org/sword-v2/sword-v2-specifications/


Build
-----

To build this library use maven 2:

    mvn clean package

In the root directory will build the software

Running the script
------------------
The script can be launched with the help of maven, for example:

```
 $ mvn exec:java -Dexec.mainClass="com.atmire.lne.swordv2.client.Swordv2ClientScript" -Dexec.args="-d '/Users/tom/temp/DBA_2015000265-aparte-sip-paketten' -m 'application/zip' -p src/main/resources/swordv2-server.properties"
```

The possible command line parameters are:

| Flag                          | Description                                                                                    | Usage                |
|-------------------------------|------------------------------------------------------------------------------------------------|----------------------|
| -i,--in-progress              | When used, the script will send a request with the In-Progress header set to true.             | Optional             |
| -p,--server-properties <path> | Path to the server properties file that contains the server URL and authentication credentials | Required             |
| -d,--directory <path>         | Path to the directory containing all archive ZIP files that needs to be uploaded               | -d or -f is required |
| -f,--file <path>              | Path to the archive file that needs to be uploaded                                             | -d or -f is required |
| -m,--mimetype <type>          | The mime type of the archive file, e.g. 'application/zip'                                      | Required             |
| -s,--slug <id>                | The suggested identifier to pass to the SWORD server                                           | Optional             |

If the upload was successful, the script will print out the deposit receipt(s). If it encountered an error, it will print out the error code and a description (if the server returned an error description).


API Documentation
-----------------

The main point of entry for client operations is org.swordapp.client.SWORDClient

To perform deposits, create instances of org.swordapp.client.Deposit and pass it to the SWORDClient's methods.

To authenticate, create instances of org.swordapp.client.AuthCredentials and pass them in with the Deposit object to the SWORDClient.


For example:

    SWORDClient client = new SWORDClient()

Obtain a service document:

    ServiceDocument sd = client.getServiceDocument(this.sdIRI, new AuthCredentials(this.user, this.pass));

Get the first collection from the first workspace in the service document:

    SWORDCollection col = sd.getWorkspaces().get(0).getCollections().get(0);

Create a binary file only Deposit object:

    Deposit deposit = new Deposit();
    deposit.setFile(new FileInputStream(myFile));
    deposit.setMimeType("application/zip");
    deposit.setFilename("example.zip");
    deposit.setPackaging(UriRegistry.PACKAGE_SIMPLE_ZIP);
    deposit.setMd5(fileMD5);
    deposit.setInProgress(true);
    deposit.setSuggestedIdentifier("abcdefg");

Pass the deposit object to the client:

    DepositReceipt receipt = client.deposit(col, deposit, auth)

We can create entry-only depsits too:

    EntryPart ep = new EntryPart();
    ep.addDublinCore("title", "My Title");

    Deposit deposit = new Deposit();
    deposit.setEntryPart(ep);

For some deposit operations we get back a DepositReceipt object, from which we can get all the details we might want about the item:

    receipt.getStatusCode();
    receipt.getLocation();
    receipt.getDerivedResourceLinks();
    receipt.getOriginalDepositLink();
    receipt.getEditMediaLink();
    receipt.getAtomStatementLink();
    receipt.getContentLink();
    receipt.getEditLink();
    receipt.getOREStatementLink();
    receipt.getPackaging();
    receipt.getSplashPageLink();
    receipt.getStatementLink("application/rdf+xml");
    receipt.getStatementLink("application/atom+xml;type=feed");
    receipt.getSwordEditLink();
    receipt.getTreatment();
    receipt.getVerboseDescription();


Limitations
-----------

Currently the client DOES NOT support multipart deposit.  Therefore the specification sections 6.3.2, 6.5.3, and 6.7.3 are not supported yet.
