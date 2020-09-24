/*
 * The authorship of this project and accompanying materials is held by medshare GmbH, Switzerland.
 * All rights reserved. https://medshare.net
 *
 * Source code, documentation and other resources have been contributed by various people.
 * Project Team: https://gitlab.com/ehealth-connector/api/wikis/Team/
 * For exact developer information, please refer to the commit history of the forge.
 *
 * This code is made available under the terms of the Eclipse Public License v1.0.
 *
 * Accompanying materials are made available under the terms of the Creative Commons
 * Attribution-ShareAlike 4.0 License.
 *
 * This line is intended for UTF-8 encoding checks, do not modify/delete: Ã¤Ã¶Ã¼Ã©Ã¨
 *
 */
package connector;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.ehealth_connector.common.enums.LanguageCode;
import org.ehealth_connector.common.mdht.Code;
import org.ehealth_connector.common.mdht.Identificator;
import org.ehealth_connector.common.utils.DebugUtil;
import org.ehealth_connector.common.utils.FileUtil;
import org.ehealth_connector.common.utils.XdsMetadataUtil;
import org.ehealth_connector.communication.AffinityDomain;
import org.ehealth_connector.communication.ConvenienceCommunication;
import org.ehealth_connector.communication.Destination;
import org.ehealth_connector.communication.DocumentMetadata;
import org.ehealth_connector.communication.DocumentRequest;
import org.ehealth_connector.communication.xd.storedquery.FindDocumentsQuery;
import org.ehealth_connector.communication.xd.storedquery.GetDocumentsQuery;
import org.openhealthtools.ihe.common.ebxml._3._0.rim.ObjectRefType;
import org.openhealthtools.ihe.xds.document.DocumentDescriptor;
import org.openhealthtools.ihe.xds.document.XDSDocument;
import org.openhealthtools.ihe.xds.metadata.AvailabilityStatusType;
import org.openhealthtools.ihe.xds.metadata.DocumentEntryType;
import org.openhealthtools.ihe.xds.metadata.SubmissionSetType;
import org.openhealthtools.ihe.xds.response.DocumentEntryResponseType;
import org.openhealthtools.ihe.xds.response.XDSErrorType;
import org.openhealthtools.ihe.xds.response.XDSQueryResponseType;
import org.openhealthtools.ihe.xds.response.XDSResponseType;
import org.openhealthtools.ihe.xds.response.XDSRetrieveResponseType;
import org.openhealthtools.ihe.xds.response.XDSStatusType;

import py4j.GatewayServer;

public class XDSConnector {

	/** Sample ID of your Organization */
	public static final String ORGANIZATIONAL_ID = "1.19.6.24.109.42.1";

	public static void main(String[] args) throws Exception {

		XDSConnector xdsconnector = new XDSConnector();
		// xds_connector is now the gateway.entry_point
		GatewayServer server = new GatewayServer(xdsconnector);
		server.start();
	}

	/** The out str. */
	private StringBuffer outStr = new StringBuffer();

	/** The con com. */
	private ConvenienceCommunication conCom;

	/**
	 * <div class="en">Demonstrates the document consumer. It executes first a
	 * query to the registry and then retrieves the last PDF and XML document
	 * that are listed in the query result.
	 *
	 * @param affDomain
	 *            the affinity domain setting containing registry and repository
	 *            endpoints to be used
	 * @param patientId
	 *            the patient id to be used </div> <div class="de"></div>
	 *            <div class="fr"></div>
	 * @param assertionFile
	 *            the assertion file
	 */
	public void downloadPatientFiles(String oid, String id) {

		Identificator patientId = new Identificator(oid, id);

		AffinityDomain affDomain = null;
		outStr = new StringBuffer();
		XDSQueryResponseType qr;

		try {
			Destination registryUnsecure = new Destination(oid,
					new URI("http://localhost:9091/xds-iti18"));

			Destination repositoryUnsecure = new Destination(oid,
					new URI("http://localhost:9091/xds-iti43"));

			affDomain = new AffinityDomain(null, registryUnsecure, repositoryUnsecure);

		} catch (final URISyntaxException e) {
			System.out.print("SOURCE URI CANNOT BE SET: \n" + e.getMessage() + "\n\n");
		}

		try {
			// Create a new ConvenienceCommunication Object
			conCom = new ConvenienceCommunication(affDomain);

			// 1. Create and perform query for references
			final FindDocumentsQuery fdq = new FindDocumentsQuery(patientId,
					AvailabilityStatusType.APPROVED_LITERAL);
			qr = conCom.queryDocumentsReferencesOnly(fdq);
			outStr.append("\nQuery for document references. Response status: "
					+ qr.getStatus().getName());
			outStr.append(". Returned " + qr.getReferences().size() + " references.");

			if (qr.getReferences().size() < 1) {
				outStr.append("\nNo Documents found for patient '" + patientId.getRoot() + "/"
						+ patientId.getExtension() + "' in registry: "
						+ affDomain.getRegistryDestination().getUri());
			} else {

				// 2. Create and perform query for document metadata
				int numberOfDocumentMetadataQuery = qr.getReferences().size();

				final String[] docUUIDs = new String[numberOfDocumentMetadataQuery];
				for (int i = 0; i < numberOfDocumentMetadataQuery; i++) {
					final ObjectRefType ort = qr.getReferences()
							.get((qr.getReferences().size() - 1) - i);
					docUUIDs[i] = ort.getId();
				}
				final GetDocumentsQuery gdq = new GetDocumentsQuery(docUUIDs, true);
				qr = conCom.queryDocuments(gdq);
				if (qr != null) {
					outStr.append("\nQuery for document metadata of the last "
							+ Integer.toString(numberOfDocumentMetadataQuery)
							+ " documents. Response status: " + qr.getStatus().getName());
					outStr.append(
							". Returned " + qr.getDocumentEntryResponses().size() + " documents.");
					if (qr.getDocumentEntryResponses().size() < 1) {
						outStr.append("\nNo Documents found for patient '" + patientId.getRoot()
								+ "/" + patientId.getExtension() + "' in registry: "
								+ affDomain.getRegistryDestination().getUri());
					} else {
						DocumentEntryType docEntry = null;
						// Retrieve Files from the repository and store it to
						// disc
						List<DocumentEntryResponseType> responses = qr.getDocumentEntryResponses();

						for (DocumentEntryResponseType e : responses) {
							docEntry = e.getDocumentEntry();
							outStr.append("\nFound XML document for patient '" + patientId.getRoot()
									+ "/" + patientId.getExtension() + "' in registry: "
									+ affDomain.getRegistryDestination().getUri() + ":\n"
									+ DebugUtil.debugDocumentMetaData(docEntry));
							storeDocument(affDomain, docEntry);
						}
					}

				} else
					outStr.append("\n*** FAILURE :" + conCom.getLastError());
			}

		} catch (final Exception e) {
			System.out.print(e.getMessage() + "\n");
			e.printStackTrace();
		}

		System.out.print(outStr.toString() + "\n");
	}

	/**
	 * <div class="en">Method to get instance of Destination with the correct
	 * paramters set.
	 *
	 * @param organizationalId
	 *            the organizational id
	 * @param repository
	 *            the uri string
	 * @param keystore
	 *            the the path to the keystore
	 * @param keyStorePass
	 *            the password to the keystore
	 * @return the initialized Destination
	 * @throws URISyntaxException
	 *             on incorrect uri string the exception will be thrown </div>
	 */
	private Destination getDestination(String organizationalId, String repository, String keystore,
			String keyStorePass, String keyStoreType) throws URISyntaxException {
		final URI repositoryUri = new java.net.URI(repository);
		if ((keystore != null) && !"".equals(keystore)) {
			return new Destination(organizationalId, repositoryUri, keystore, keyStorePass,
					keyStoreType);
		}
		return new Destination(organizationalId, repositoryUri);
	}

	/**
	 * Gets the sample CDA document stream.
	 *
	 * @return a CDA document stream
	 * @throws FileNotFoundException
	 */
	private InputStream getDocCda(String path) throws FileNotFoundException {
		System.out.println("Get File: " + path);
		InputStream cda = new FileInputStream(path);
		// getClass().getResourceAsStream(path);
		// InputStream cda = getClass().getResourceAsStream("/demoDocSource/" +
		// path);
		return cda;
	}

	private void printXdsResponse(XDSResponseType aResponse) {
		if (XDSStatusType.SUCCESS_LITERAL.equals(aResponse.getStatus())) {
			System.out.print("done. Response: " + aResponse.getStatus().getName() + "\n\n");
		} else if (XDSStatusType.ERROR_LITERAL.equals(aResponse.getStatus())
				|| XDSStatusType.FAILURE_LITERAL.equals(aResponse.getStatus())
				|| XDSStatusType.PARTIAL_SUCCESS_LITERAL.equals(aResponse.getStatus())
				|| XDSStatusType.UNAVAILABLE_LITERAL.equals(aResponse.getStatus())
				|| XDSStatusType.WARNING_LITERAL.equals(aResponse.getStatus())) {
			System.out.print("done. Response: " + aResponse.getStatus().getName() + "\n");
			if ((aResponse.getErrorList() != null)
					&& (aResponse.getErrorList().getError() != null)) {
				for (final XDSErrorType error : aResponse.getErrorList().getError()) {
					System.out.print("      Context:  " + error.getCodeContext() + "\n");
					System.out.print("      Location: " + error.getLocation() + "\n");
					System.out.print("      Value:    " + error.getValue() + "\n");
					System.out.print("\n");
				}
			}
			System.out.print("\n\n");
		}

	}

	/**
	 * <div class="en">Demonstrates the document consumer. It executes first a
	 * query to the registry and then retrieves the last PDF and XML document
	 * that are listed in the query result.
	 *
	 * @param affDomain
	 *            the affinity domain setting containing registry and repository
	 *            endpoints to be used
	 * @param patientId
	 *            the patient id to be used </div> <div class="de"></div>
	 *            <div class="fr"></div>
	 * @param assertionFile
	 *            the assertion file
	 * @return
	 */
	public String queryDocumentWithId(String oid, String id, String documentId) {

		Identificator patientId = new Identificator(oid, id);

		AffinityDomain affDomain = null;
		outStr = new StringBuffer();
		XDSQueryResponseType documentEntryResponse;

		try {
			Destination registryUnsecure = new Destination(oid,
					new URI("http://localhost:9091/xds-iti18"));

			Destination repositoryUnsecure = new Destination(oid,
					new URI("http://localhost:9091/xds-iti43"));

			affDomain = new AffinityDomain(null, registryUnsecure, repositoryUnsecure);

		} catch (final URISyntaxException e) {
			System.out.print("SOURCE URI CANNOT BE SET: \n" + e.getMessage() + "\n\n");
		}

		// Create a new ConvenienceCommunication Object
		conCom = new ConvenienceCommunication(affDomain);
		final FindDocumentsQuery fdq = new FindDocumentsQuery(patientId,
				AvailabilityStatusType.APPROVED_LITERAL);
		documentEntryResponse = conCom.queryDocuments(fdq);
		List<DocumentEntryResponseType> responses = documentEntryResponse
				.getDocumentEntryResponses();
		if (responses.size() > 0) {
			// TODO: make Temp folder empty
			DocumentEntryType entry = null;
			for (DocumentEntryResponseType e : responses) {
				DocumentEntryType _entry = e.getDocumentEntry();
				if (_entry.getUniqueId().equals(documentId)) {
					entry = _entry;
					break;
				} else {
					return "NO_DOCUMENT_FOUND";
				}

			}
			// TODO: use method retrieveAndStore
			final DocumentRequest documentRequest = new DocumentRequest(
					entry.getRepositoryUniqueId(), affDomain.getRepositoryDestination().getUri(),
					entry.getUniqueId());
			final XDSRetrieveResponseType rrt = conCom.retrieveDocument(documentRequest);
			final XDSDocument document = rrt.getAttachments().get(0);
			final InputStream docIS = document.getStream();

			File file = new File(
					"C:\\Users\\Raik Müller\\Documents\\GitHub\\RecruitmentTool_Backend\\Django_Server\\recruitmenttool\\cda_files\\tempDownload\\return_cda.xml");
			try {
				FileUtils.copyInputStreamToFile(docIS, file);

			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
		return "C:\\Users\\Raik Müller\\Documents\\GitHub\\RecruitmentTool_Backend\\Django_Server\\recruitmenttool\\cda_files\\temp\\return_cda.xml";

	}

	/** set metaData only needed for IPF XDS Framework **/
	public void setMetaDatForCDA(DocumentMetadata metaData, Identificator patientId,
			String documentId) {
		// metaData.addAuthor(new Author(new Name("Sigrid", "Kollmann"),
		// "2323"));
		// Dokumentenklasse (Oberklasse) z.B.: 18842-5 „Entlassungsbrief“
		// TODO: optional to extract from each CDA but not necarry for test
		// cases
		metaData.setClassCode(new Code("History and Physical",
				"urn:uuid:41a5887f-8865-4c09-adf7-e362475b143a", "Connect-a-thon classCodes"));
		// Vertraulichkeitscode des Dokuments
		metaData.addConfidentialityCode(
				org.ehealth_connector.common.mdht.enums.ConfidentialityCode.NORMAL);
		// TODO: optional to extract from each CDA but not necarry for test
		// cases
		// metaData.setCreationTime(new Date());
		// UUID des Metadaten-Records des Doku- ments (XDS DocumentEntry)
		// metaData.setEntryUUID(UUID.randomUUID().toString());
		metaData.setFormatCode(new Code("CDAR2/IHE 1.0",
				"urn:uuid:a09d5840-386c-46f2-b5ad-9c3699a4309d", "Connect-a-thon formatCodes"));
		// Klassifizierung des GDA
		// TODO: change name?
		metaData.setHealthcareFacilityTypeCode(
				new Code("Outpatient", "urn:uuid:f33fb8ac-18af-42cc-ae0e-ed0b0bdb91e1",
						"Connect-a-thon healthcareFacilityTypeCodes"));
		metaData.setCodedLanguage(LanguageCode.GERMAN_CODE);
		metaData.setMimeType("text/xml");
		// Patienten-ID in der XDS Affinity Domain
		metaData.setDestinationPatientId(patientId);
		// Fachliche Zuordnung des Dokuments
		// TODO: optional to extract from each CDA but not necarry for test
		// cases
		metaData.setPracticeSettingCode(
				new Code("General Medicine", "urn:uuid:cccf5598-8b07-4b77-a05e-ae952c785ead",
						"Connect-a-thon practiceSettingCodes"));
		// Patienten ID im Informationssystem des GDA. z.B.: im KIS des KH
		// TODO: optional to extract from each CDA but not necarry for test
		// cases
		metaData.setSourcePatientId(new Identificator("1.2.3.4", "2342134localid"));
		// Dokumententyp (Unterklasse) codierter Wert, z.B.: 11490-0,
		// „Entlassungsbrief aus statio- närer Behandlung (Arzt)“
		// TODO: change name?
		metaData.setTypeCode(new Code("Outpatient", "urn:uuid:f33fb8ac-18af-42cc-ae0e-ed0b0bdb91e1",
				"Connect-a-thon healthcareFacilityTypeCodes"));

		// Global eindeutige ID des Dokuments
		metaData.setUniqueId(documentId);

		if (metaData.getMdhtDocumentEntryType() != null
				&& metaData.getMdhtDocumentEntryType().getLegalAuthenticator() != null) {
			metaData.getMdhtDocumentEntryType().getLegalAuthenticator()
					.setAssigningAuthorityName("");
		}

	}

	/**
	 * Retrieve and store.
	 *
	 * @param affDomain
	 *            the aff domain
	 * @param docEntry
	 *            the doc entry
	 */
	private void storeDocument(AffinityDomain affDomain, DocumentEntryType docEntry) {

		final DocumentRequest documentRequest = new DocumentRequest(
				docEntry.getRepositoryUniqueId(), affDomain.getRepositoryDestination().getUri(),
				docEntry.getUniqueId());
		final XDSRetrieveResponseType rrt = conCom.retrieveDocument(documentRequest);
		final XDSDocument document = rrt.getAttachments().get(0);
		final InputStream docIS = document.getStream();
		String patientID = docEntry.getPatientId().getIdNumber();
		String documentID = docEntry.getUniqueId();

		File file = new File(
				"C:\\Users\\Raik Müller\\Documents\\GitHub\\RecruitmentTool_Backend\\Django_Server\\recruitmenttool\\cda_files\\tempDownload\\"
						+ patientID + "\\" + documentID + "_" + patientID + ".xml");
		try {
			FileUtils.copyInputStreamToFile(docIS, file);
			System.out.println(
					"Document saved: C:\\Users\\Raik Müller\\Documents\\GitHub\\RecruitmentTool_Backend\\Django_Server\\recruitmenttool\\cda_files\\tempDownload\\"
							+ patientID + "\\" + documentID + "_" + patientID + ".xml");

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	/**
	 * Store document.
	 *
	 * @param docEntry
	 *            the doc entry
	 * @param rrt
	 *            the rrt
	 * @return true, if successful
	 * @throws URISyntaxException
	 *             the URI syntax exception
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	private boolean storeDocument(DocumentEntryType docEntry, XDSRetrieveResponseType rrt)
			throws URISyntaxException, IOException {
		if (rrt.getAttachments() == null) {
		} else if (rrt.getAttachments().size() > 0) {
			if (rrt.getErrorList() != null) {
				outStr.append(
						"\nErrors: " + rrt.getErrorList().getHighestSeverity().getName() + ".");
			}
			outStr.append("\nRetrieve sucessful. Retrieved: " + rrt.getAttachments().size()
					+ " documents.");
			final XDSDocument document = rrt.getAttachments().get(0);

			outStr.append("\nFirst document returned: " + document.toString());
			final InputStream docIS = document.getStream();

			// Create a new File with the RepositoryId as prefix
			final String filePath = "C:\\Users\\Raik Müller\\Documents\\GitHub\\RecruitmentTool_Backend\\Django_Server\\recruitmenttool\\cda_files"; // Temp
																																						// Folder:
																																						// Util.getTempDirectory();

			final File targetFile = new File(filePath + FileUtil.getPlatformSpecificPathSeparator()
					+ docEntry.getRepositoryUniqueId() + "_"
					+ docEntry.getEntryUUID().replace("urn:uuid:", "") + "_"
					+ docEntry.getMimeType().replace("/", "."));

			FileUtils.copyInputStreamToFile(docIS, targetFile);
			outStr.append("\nDocument was stored to: " + targetFile.getCanonicalPath() + "\n");
		} else {
			return false;
		}
		return true;
	}

	public void testPythonConnection() {
		System.out.println("Python Connection Test: Successful\n");
	}

	/**
	 * <div class="en">Demonstrates how to submit documents to NIST Toolkit
	 * repository using the eHealth Connector Convenience API.</div>
	 * <div class="de"></div> <div class="fr"></div>
	 *
	 * @param assertionFile
	 * @throws Exception
	 */
	public void uploadDocument(String oid, String id, String documentId, String fileTempPath) {

		Identificator patientId = new Identificator(oid, id);

		Destination repo = null;
		try {
			repo = getDestination(ORGANIZATIONAL_ID, "http://localhost:9091/xds-iti41", null, null,
					null);
		} catch (final URISyntaxException e) {
			System.out.print("SOURCE URI CANNOT BE SET: \n" + e.getMessage() + "\n\n");
		}
		try {
			// Create unsecure destination
			final AffinityDomain affinityDomain = new AffinityDomain(null, null, repo);

			final ConvenienceCommunication conCom1 = new ConvenienceCommunication(affinityDomain);

			// Sending CDA Document to Repository (NON-TLS)
			final DocumentMetadata metaData1 = conCom1.addDocument(DocumentDescriptor.CDA_R2,
					getDocCda(fileTempPath), getDocCda(fileTempPath));
			setMetaDatForCDA(metaData1, patientId, documentId);

			System.out.print("Sending CDA Document...");

			SubmissionSetType subset = conCom1.generateDefaultSubmissionSetAttributes();
			// TODO: needed?
			subset.setContentTypeCode(XdsMetadataUtil.convertEhcCodeToCodedMetadataType(
					new Code("2.16.840.1.113883.6.96", "35971002", "Ambulatory care site")));

			final XDSResponseType response1 = conCom1.submit();
			printXdsResponse(response1);

		} catch (final Exception e) {
			System.out.print(e.getMessage() + "\n\n");
		}

	}

	/**
	 * <div class="en">check if document doesn't already exist for this
	 * patient</div>
	 *
	 */
	public boolean validateNewDocument(String oid, String id, String documentId) {
		Identificator patientId = new Identificator(oid, id);
		AffinityDomain affDomain = null;
		outStr = new StringBuffer();
		XDSQueryResponseType documentEntryResponse;
		try {
			Destination registryUnsecure = new Destination(oid,
					new URI("http://localhost:9091/xds-iti18"));

			Destination repositoryUnsecure = new Destination(oid,
					new URI("http://localhost:9091/xds-iti43"));

			affDomain = new AffinityDomain(null, registryUnsecure, repositoryUnsecure);

		} catch (final URISyntaxException e) {
			System.out.print("SOURCE URI CANNOT BE SET: \n" + e.getMessage() + "\n\n");
		}

		conCom = new ConvenienceCommunication(affDomain);
		final FindDocumentsQuery fdq = new FindDocumentsQuery(patientId,
				AvailabilityStatusType.APPROVED_LITERAL);
		documentEntryResponse = conCom.queryDocuments(fdq);
		List<DocumentEntryResponseType> responses = documentEntryResponse
				.getDocumentEntryResponses();
		if (responses.size() > 0) {
			for (DocumentEntryResponseType e : responses) {
				DocumentEntryType _entry = e.getDocumentEntry();
				System.out.println("Compare: " + _entry.getUniqueId() + " == " + documentId);
				if (_entry.getUniqueId().equals(documentId)) {
					System.out.println("Document schon vorhanden!");
					return true;
				}

			}
		}
		System.out.println("Document noch nicht vorhanden!");
		return false;
	}

}
