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
import org.ehealth_connector.common.mdht.Code;
import org.ehealth_connector.common.mdht.Identificator;
import org.ehealth_connector.common.utils.DebugUtil;
import org.ehealth_connector.common.utils.FileUtil;
import org.ehealth_connector.common.utils.Util;
import org.ehealth_connector.communication.AffinityDomain;
import org.ehealth_connector.communication.ConvenienceCommunication;
import org.ehealth_connector.communication.Destination;
import org.ehealth_connector.communication.DocumentMetadata;
import org.ehealth_connector.communication.DocumentRequest;
import org.ehealth_connector.communication.xd.storedquery.FindDocumentsQuery;
import org.ehealth_connector.communication.xd.storedquery.GetDocumentsQuery;
import org.openhealthtools.ihe.common.ebxml._3._0.rim.ObjectRefType;
import org.openhealthtools.ihe.xds.document.XDSDocument;
import org.openhealthtools.ihe.xds.metadata.DocumentEntryType;
import org.openhealthtools.ihe.xds.response.DocumentEntryResponseType;
import org.openhealthtools.ihe.xds.response.XDSQueryResponseType;
import org.openhealthtools.ihe.xds.response.XDSRetrieveResponseType;

public class XDSConnector {

	// https://gitlab.com/ehealth-connector/demo-java/-/blob/master/src/main/java/org/ehealth_connector/demo/iti/xd/DemoDocSource.java
	// Line 355
	public static final String DOC_CDA = "C:/Users/Raik Müller/Desktop/ELGA-023-Entlassungsbrief_aerztlich_EIS-FullSupport.xml";
	public static final Identificator EPR_PATIENT_ID = new Identificator(
			"1.3.6.1.4.1.21367.2003.3.9", "eHC-Demo-Patient-ID");

	public static void main(String[] args) throws Exception {
		XDSConnector c = new XDSConnector();
		final Destination registryUnsecure = new Destination("", new URI(""));

		final Destination repositoryUnsecure = new Destination("", new URI(""));

		final AffinityDomain adUnsecure = new AffinityDomain(null, registryUnsecure,
				repositoryUnsecure);
		c.queryRetrieveDemo(adUnsecure, EPR_PATIENT_ID);

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
			final FindDocumentsQuery fdq = new FindDocumentsQuery(patientId, null, null, null, null,
					null, null, null, null); // AvailabilityStatus.APPROVED
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

						// 3. Find the last PDF Document, retrieve it from the
						// repository and store it to disc
						DocumentEntryType docEntry = findLastDoc(qr, "application/pdf");
						if (docEntry == null) {
							outStr.append(
									"\nNo PDF document found for patient '" + patientId.getRoot()
											+ "/" + patientId.getExtension() + "' in registry: "
											+ affDomain.getRegistryDestination().getUri() + "\n");
						} else {
							outStr.append("\nFound PDF document for patient '" + patientId.getRoot()
									+ "/" + patientId.getExtension() + "' in registry: "
									+ affDomain.getRegistryDestination().getUri() + ":\n"
									+ DebugUtil.debugDocumentMetaData(docEntry));
							retrieveAndStore(affDomain, docEntry);
						}

						// 4. Find the last XML Document, retrieve it from the
						// repository and store it to disc
						docEntry = findLastDoc(qr, "text/xml");
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
		// BUGFIX: metaData.addAuthor(new Author(new Name("Gerald", "Smitty"),
		// "1234"));
		metaData.setClassCode(
				new Code("2.16.840.1.113883.6.96", "1331000195101", "Alert (record artifact)"));
		metaData.addConfidentialityCode(
				new Code("2.16.840.1.113883.6.96", "1051000195109", "normal"));
		metaData.setCreationTime(new Date());
		metaData.setEntryUUID("123");
		metaData.setFormatCode(new Code("1.3.6.1.4.1.19376.1.2.3", "urn:ihe:pcc:ic:2009",
				"Immunization Content (IC)"));
		metaData.setHealthcareFacilityTypeCode(
				new Code("2.16.840.1.113883.6.96", "394747008", "Health Authority"));
		metaData.setCodedLanguage(LanguageCode.GERMAN_CODE);
		metaData.setMimeType("mimeType");
		metaData.setDestinationPatientId(EPR_PATIENT_ID);
		metaData.setSourcePatientId(new Identificator("1.2.3.4", "2342134localid"));
		metaData.setPracticeSettingCode(
				new Code("1.3.6.1.4.1.21367.2017.3", "Practice-F", "Family Practice"));
		metaData.setTypeCode(
				new Code("2.16.840.1.113883.6.1", "34133-9", "Summarization of Episode Note"));
		metaData.setUniqueId("123");
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

}
