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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.commons.io.FileUtils;
import org.ehealth_connector.common.enums.LanguageCode;
import org.ehealth_connector.common.mdht.Code;
import org.ehealth_connector.common.mdht.Identificator;
import org.ehealth_connector.common.utils.DebugUtil;
import org.ehealth_connector.common.utils.FileUtil;
import org.ehealth_connector.common.utils.Util;
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

	// public static final Identificator EPR_PATIENT_ID = new Identificator(
	// "1.3.6.1.4.1.21367.2005.3.7", "SELF-5");

	// TESTs
	public static void main(String[] args) throws Exception {
		XDSConnector xdsconnector = new XDSConnector();

		// app is now the gateway.entry_point
		GatewayServer server = new GatewayServer(xdsconnector);
		server.start();
		// c.uploadDocument(String oid, String id);
		// c.queryRetrieveDemo(EPR_PATIENT_ID);

	}

	/** The out str. */
	private StringBuffer outStr = new StringBuffer();
	/** The con com. */
	private ConvenienceCommunication conCom;

	/**
	 * Finds the last registered document.
	 *
	 * @param qr
	 *            the query response
	 * @param mimeType
	 *            the mime type
	 * @return the document entry type
	 */
	private DocumentEntryType findLastDoc(XDSQueryResponseType qr, String mimeType) {
		DocumentEntryType docEntry = null;
		for (int i = (qr.getDocumentEntryResponses().size() - 1); i < qr.getDocumentEntryResponses()
				.size(); i--) {
			final DocumentEntryResponseType response = qr.getDocumentEntryResponses()
					.get((qr.getDocumentEntryResponses().size() - 1) - i);
			docEntry = response.getDocumentEntry();
			if (docEntry.getMimeType().equals(mimeType)) {
				return docEntry;
			}
		}
		return null;
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
	private InputStream getDocCda(String path) {
		System.out.println("Get File: " + path);
		InputStream cda = getClass().getResourceAsStream("/demoDocSource/" + path);
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
	 */
	public void queryRetrieveDemo(String oid, String id) {

		Identificator patientId = new Identificator(oid, id);

		AffinityDomain affDomain = null;
		outStr = new StringBuffer();
		int numberOfDocumentMetadataQuery = 10;
		XDSQueryResponseType qr;

		try {
			Destination registryUnsecure = new Destination(ORGANIZATIONAL_ID,
					new URI("http://localhost:9091/xds-iti18"));

			Destination repositoryUnsecure = new Destination(ORGANIZATIONAL_ID,
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
						+ affDomain.getRegistryDestination().getUri()
						+ ".\nYou might use the DocumentSourceDemo to upload sample files (one pdf and one xml).");
			} else {

				// 2. Create and perform query for document metadata (of the
				// last 10 documents)
				if (qr.getReferences().size() < numberOfDocumentMetadataQuery) {
					numberOfDocumentMetadataQuery = qr.getReferences().size();
				}
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

						// Find the last XML Document, retrieve it from the
						// repository and store it to disc
						DocumentEntryType docEntry = findLastDoc(qr, "text/xml");
						if (docEntry == null) {
							outStr.append(
									"\nNo XML document found for patient '" + patientId.getRoot()
											+ "/" + patientId.getExtension() + "' in registry: "
											+ affDomain.getRegistryDestination().getUri() + "\n");
						} else {
							outStr.append("\nFound XML document for patient '" + patientId.getRoot()
									+ "/" + patientId.getExtension() + "' in registry: "
									+ affDomain.getRegistryDestination().getUri() + ":\n"
									+ DebugUtil.debugDocumentMetaData(docEntry));
							retrieveAndStore(affDomain, docEntry);
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
	 * Retrieve and store.
	 *
	 * @param affDomain
	 *            the aff domain
	 * @param docEntry
	 *            the doc entry
	 * @return true, if successful
	 * @throws URISyntaxException
	 *             the URI syntax exception
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	private boolean retrieveAndStore(AffinityDomain affDomain, DocumentEntryType docEntry)
			throws URISyntaxException, IOException {

		final DocumentRequest documentRequest = new DocumentRequest(
				docEntry.getRepositoryUniqueId(), affDomain.getRepositoryDestination().getUri(),
				docEntry.getUniqueId());
		final XDSRetrieveResponseType rrt = conCom.retrieveDocument(documentRequest);

		final boolean stored = storeDocument(docEntry, rrt);

		if (!stored) {
			outStr.append("\nDOCUMENT NOT RETRIEVED (" + docEntry.getUniqueId()
					+ ") from repository: " + affDomain.getRepositoryDestination().getUri());

		}
		return stored;
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
			final String filePath = Util.getTempDirectory();
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
	public void uploadDocument(String oid, String id, String documentId, String docPath) {

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
					getDocCda(docPath), getDocCda(docPath));
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

}
