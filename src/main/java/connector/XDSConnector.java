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
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;

import org.apache.commons.io.FileUtils;
import org.ehealth_connector.common.enums.LanguageCode;
import org.ehealth_connector.common.mdht.Author;
import org.ehealth_connector.common.mdht.Code;
import org.ehealth_connector.common.mdht.Identificator;
import org.ehealth_connector.common.mdht.Name;
import org.ehealth_connector.common.utils.DebugUtil;
import org.ehealth_connector.common.utils.FileUtil;
import org.ehealth_connector.common.utils.Util;
import org.ehealth_connector.common.utils.XdsMetadataUtil;
import org.ehealth_connector.communication.AffinityDomain;
import org.ehealth_connector.communication.ConvenienceCommunication;
import org.ehealth_connector.communication.Destination;
import org.ehealth_connector.communication.DocumentMetadata;
import org.ehealth_connector.communication.DocumentMetadata.DocumentMetadataExtractionMode;
import org.ehealth_connector.communication.DocumentRequest;
import org.ehealth_connector.communication.SubmissionSetMetadata.SubmissionSetMetadataExtractionMode;
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

public class XDSConnector {

	// https://gitlab.com/ehealth-connector/demo-java/-/blob/master/src/main/java/org/ehealth_connector/demo/iti/xd/DemoDocSource.java
	public static final String DOC_CDA = "C:/Users/Raik Müller/Desktop/ELGA-023-Entlassungsbrief_aerztlich_EIS-FullSupport.xml";

	/** Sample ID of your Organization */
	public static final String ORGANIZATIONAL_ID = "1.19.6.24.109.42.1"; // TODO://
																			// check/find
																			// ID?
																			// =
																			// RepositroyID?

	public static final Identificator EPR_PATIENT_ID = new Identificator(
			"1.3.6.1.4.1.21367.2005.3.7", "SELF-5");

	public static void main(String[] args) throws Exception {
		XDSConnector c = new XDSConnector();
		final Destination registryUnsecure = new Destination(ORGANIZATIONAL_ID,
				new URI("http://localhost:9091/xds-iti18"));

		final Destination repositoryUnsecure = new Destination(ORGANIZATIONAL_ID,
				new URI("http://localhost:9091/xds-iti43"));

		final AffinityDomain adUnsecure = new AffinityDomain(null, registryUnsecure,
				repositoryUnsecure);
		c.queryRetrieveDemo(adUnsecure, EPR_PATIENT_ID);
		// c.uploadDocument();

	}

	/** The out str. */
	// String to display after the demo has run
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
	 */
	private InputStream getDocCda() {
		return getClass().getResourceAsStream(DOC_CDA);
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
	public void queryRetrieveDemo(AffinityDomain affDomain, Identificator patientId) {
		outStr = new StringBuffer();
		int numberOfDocumentMetadataQuery = 10;
		XDSQueryResponseType qr;

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

		// Display the demo result
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

	public void setMetaDatForCDA(DocumentMetadata metaData) {
		metaData.addAuthor(new Author(new Name("Gerald", "Smitty"), "1234"));
		metaData.setDestinationPatientId(EPR_PATIENT_ID);
		metaData.setSourcePatientId(new Identificator("1.2.3.4", "2342134localid"));
		metaData.setCodedLanguage(LanguageCode.GERMAN_CODE);
		metaData.setTypeCode(new Code("Outpatient", "urn:uuid:f33fb8ac-18af-42cc-ae0e-ed0b0bdb91e1",
				"Connect-a-thon healthcareFacilityTypeCodes"));
		metaData.setFormatCode(new Code("CDAR2/IHE 1.0",
				"urn:uuid:a09d5840-386c-46f2-b5ad-9c3699a4309d", "Connect-a-thon formatCodes"));

		metaData.setClassCode(new Code("History and Physical",
				"urn:uuid:41a5887f-8865-4c09-adf7-e362475b143a", "Connect-a-thon classCodes"));

		metaData.addConfidentialityCode(
				org.ehealth_connector.common.mdht.enums.ConfidentialityCode.NORMAL);

		metaData.setCreationTime(new Date());
		// Bugfix
		metaData.setEntryUUID("urn:uuid:2e82c1f6-a085-4c72-9da3-8640a32e42ab");
		// metaData.setEntryUUID(UUID.randomUUID().toString());
		metaData.setHealthcareFacilityTypeCode(
				new Code("Outpatient", "urn:uuid:f33fb8ac-18af-42cc-ae0e-ed0b0bdb91e1",
						"Connect-a-thon healthcareFacilityTypeCodes"));

		metaData.setMimeType("text/xml");
		metaData.setPracticeSettingCode(
				new Code("General Medicine", "urn:uuid:cccf5598-8b07-4b77-a05e-ae952c785ead",
						"Connect-a-thon practiceSettingCodes"));

		metaData.setUniqueId("1.3.6.1.4.1.21367.2005.3.9999.32");
		metaData.setTitle("Title");
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

	/**
	 * <div class="en">Demonstrates how to submit documents to NIST Toolkit
	 * repository using the eHealth Connector Convenience API.</div>
	 * <div class="de"></div> <div class="fr"></div>
	 *
	 * @param assertionFile
	 */
	public void uploadDocument() {

		Destination repo = null;
		Destination registryUnsecure = null;
		try {
			repo = getDestination(ORGANIZATIONAL_ID, "http://localhost:9091/xds-iti41", null, null,
					null);
			registryUnsecure = new Destination(ORGANIZATIONAL_ID,
					new URI("http://localhost:9091/xds-iti18"));

		} catch (final URISyntaxException e) {
			System.out.print("SOURCE URI CANNOT BE SET: \n" + e.getMessage() + "\n\n");
		}
		try {
			// Create unsecure destination: this was prov. from demo but reg ==
			// null?
			// final AffinityDomain affinityDomain = new AffinityDomain(null,
			// null, repo);

			final AffinityDomain adUnsecure = new AffinityDomain(null, registryUnsecure, repo);

			final ConvenienceCommunication conCom1 = new ConvenienceCommunication(adUnsecure, null,
					DocumentMetadataExtractionMode.DEFAULT_EXTRACTION,
					SubmissionSetMetadataExtractionMode.NO_METADATA_EXTRACTION);

			// Sending CDA Document to Repository (NON-TLS)
			final DocumentMetadata metaData1 = conCom1.addDocument(DocumentDescriptor.CDA_R2,
					getDocCda(), getDocCda());
			setMetaDatForCDA(metaData1);

			SubmissionSetType subset = conCom1.generateDefaultSubmissionSetAttributes();
			subset.setContentTypeCode(XdsMetadataUtil.convertEhcCodeToCodedMetadataType(
					new Code("2.16.840.1.113883.6.96", "35971002", "Ambulatory care site")));
			final XDSResponseType response1 = conCom1.submit();
			printXdsResponse(response1);
		} catch (final Exception e) {
			System.out.print(e.getMessage() + "\n\n");
		}

	}

}
