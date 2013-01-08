package org.collectionspace.services.batch.nuxeo;

import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.ws.rs.WebApplicationException;

import org.apache.commons.lang.StringUtils;
import org.collectionspace.services.client.CollectionObjectClient;
import org.collectionspace.services.client.PoxPayloadOut;
import org.collectionspace.services.client.TaxonomyAuthorityClient;
import org.collectionspace.services.client.workflow.WorkflowClient;
import org.collectionspace.services.collectionobject.nuxeo.CollectionObjectConstants;
import org.collectionspace.services.common.ResourceBase;
import org.collectionspace.services.common.api.RefName;
import org.collectionspace.services.common.invocable.InvocationContext.ListCSIDs;
import org.collectionspace.services.common.invocable.InvocationResults;
import org.collectionspace.services.taxonomy.nuxeo.TaxonConstants;
import org.dom4j.DocumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpdateRareFlagBatchJob extends AbstractBatchJob {
	final Logger logger = LoggerFactory.getLogger(UpdateRareFlagBatchJob.class);

	private final String[] TAXON_FIELD_NAME_PARTS = CollectionObjectConstants.TAXON_FIELD_NAME.split("\\/");
	private final String TAXON_FIELD_NAME_WITHOUT_PATH = TAXON_FIELD_NAME_PARTS[TAXON_FIELD_NAME_PARTS.length - 1];
	
	public UpdateRareFlagBatchJob() {
		this.setSupportedInvocationModes(Arrays.asList(INVOCATION_MODE_SINGLE, INVOCATION_MODE_LIST, INVOCATION_MODE_NO_CONTEXT));
	}

	@Override
	public void run() {
		setCompletionStatus(STATUS_MIN_PROGRESS);
		
		try {
			String mode = getInvocationContext().getMode();
			
			if (mode.equals(INVOCATION_MODE_SINGLE)) {
				/*
				 * In a single document context, the single csid must specify a collectionobject or a
				 * taxonomy record. If it's a collectionobject, the rare flag for the specified
				 * collectionobject will be updated. If it's a taxonomy record, the rare flag will be
				 * updated for each collectionobject with a primary determination that refers to the
				 * specified taxonomy record.
				 */
				
				String csid = getInvocationContext().getSingleCSID();
				
				if (StringUtils.isEmpty(csid)) {
					throw new Exception("Missing context csid");
				}
				
				String docType = getInvocationContext().getDocType();
				
				if (docType.equals(CollectionObjectConstants.NUXEO_DOCTYPE)) {
					setResults(updateRareFlag(csid));
				}
				else if (docType.equals(TaxonConstants.NUXEO_DOCTYPE)) {
					setResults(updateReferencingRareFlags(csid));
				}
				else {
					throw new Exception("Unsupported document type: " + docType);
				}
			}
			else if (mode.equals(INVOCATION_MODE_LIST)) {
				/*
				 * In a list context, the csids must specify collectionobjects. The rare flag for
				 * each collectionobject will be updated.
				 */
				ListCSIDs csids = getInvocationContext().getListCSIDs();
				
				setResults(updateRareFlags(csids.getCsid()));
			}
			else if (mode.equals(INVOCATION_MODE_NO_CONTEXT)) {
				/*
				 * If there is no context, the rare flag will be updated for all (non-deleted)
				 * collectionobjects.
				 */
				
				setResults(updateAllRareFlags());
			}
			else {
				throw new Exception("Unsupported invocation mode: " + mode);
			}
			
			setCompletionStatus(STATUS_COMPLETE);
		}
		catch(Exception e) {
			setCompletionStatus(STATUS_ERROR);
			setErrorInfo(new InvocationError(INT_ERROR_STATUS, e.getMessage()));
		}
	}
	
	/**
	 * Updates the rare flags of collectionobjects that refer to the specified taxon record.
	 * A collectionobject is considered to refer to the taxon record if the refname of its
	 * primary taxonomic identification is the refname of the taxon record.
	 * 
	 * @param taxonCsid		The csid of the taxon record
	 * @return
	 * @throws URISyntaxException
	 * @throws DocumentException
	 */
	public InvocationResults updateReferencingRareFlags(String taxonCsid) throws URISyntaxException, DocumentException {
		PoxPayloadOut taxonPayload = findTaxonByCsid(taxonCsid);
		String taxonRefName = getFieldValue(taxonPayload, TaxonConstants.REFNAME_SCHEMA_NAME, TaxonConstants.REFNAME_FIELD_NAME);

		RefName.AuthorityItem item = RefName.AuthorityItem.parse(taxonRefName);
		String vocabularyShortId = item.getParentShortIdentifier();

		List<String> collectionObjectCsids = findReferencingCollectionObjects(TaxonomyAuthorityClient.SERVICE_NAME, vocabularyShortId, taxonCsid, CollectionObjectConstants.TAXON_SCHEMA_NAME + ":" + TAXON_FIELD_NAME_WITHOUT_PATH);
	 	long numFound = 0;
		long numAffected = 0;
		
		for (String collectionObjectCsid : collectionObjectCsids) {
			// Filter out results where the taxon is referenced in the correct field, but isn't the primary value.
			
			PoxPayloadOut collectionObjectPayload = findCollectionObjectByCsid(collectionObjectCsid);
			String primaryTaxonRefName = getFieldValue(collectionObjectPayload, CollectionObjectConstants.TAXON_SCHEMA_NAME, CollectionObjectConstants.TAXON_FIELD_NAME);
						
			if (primaryTaxonRefName.equals(taxonRefName)) {	
				numFound++;
				
				InvocationResults itemResults = updateRareFlag(collectionObjectPayload);
				numAffected += itemResults.getNumAffected();
			}
		}
		
		InvocationResults results = new InvocationResults();
		results.setNumAffected(numAffected);
		results.setUserNote(numFound + " referencing cataloging " + (numFound == 1 ? "record" : "records") + " found, " + numAffected + " updated");
		
		return results;
	}
	
	/**
	 * Updates the rare flag of the specified collectionobject.
	 * 
	 * @param collectionObjectCsid	The csid of the collectionobject
	 * @return
	 * @throws URISyntaxException
	 * @throws DocumentException
	 */
	public InvocationResults updateRareFlag(String collectionObjectCsid) throws URISyntaxException, DocumentException {
		PoxPayloadOut collectionObjectPayload = findCollectionObjectByCsid(collectionObjectCsid);
		
		return updateRareFlag(collectionObjectPayload);
	}
	
	/**
	 * Updates the rare flag of the specified collectionobject. The rare flag is determined by looking at
	 * the taxon record that is referenced by the primary taxonomic determination of the collectionobject.
	 * If the taxon record has any non-null conservation category, the rare flag is set to true. Otherwise,
	 * it is set to false.
	 * 
	 * @param collectionObjectPayload	The payload representing the collectionobject
	 * @return
	 * @throws URISyntaxException
	 * @throws DocumentException
	 */
	public InvocationResults updateRareFlag(PoxPayloadOut collectionObjectPayload) throws URISyntaxException, DocumentException {
		InvocationResults results = new InvocationResults();

		String uri = this.getFieldValue(collectionObjectPayload, CollectionObjectConstants.URI_SCHEMA_NAME, CollectionObjectConstants.URI_FIELD_NAME);
		String[] uriParts = uri.split("\\/");
		String collectionObjectCsid = uriParts[uriParts.length-1];

		String workflowState = getFieldValue(collectionObjectPayload, CollectionObjectConstants.WORKFLOW_STATE_SCHEMA_NAME, CollectionObjectConstants.WORKFLOW_STATE_FIELD_NAME);
		
		if (workflowState.equals(WorkflowClient.WORKFLOWSTATE_DELETED)) {
			logger.debug("skipping deleted collectionobject: " + collectionObjectCsid);
		}
		else {
			String taxonRefName = getFieldValue(collectionObjectPayload, CollectionObjectConstants.TAXON_SCHEMA_NAME, CollectionObjectConstants.TAXON_FIELD_NAME);
			boolean oldIsRare = getBooleanFieldValue(collectionObjectPayload, CollectionObjectConstants.RARE_FLAG_SCHEMA_NAME, CollectionObjectConstants.RARE_FLAG_FIELD_NAME);
			boolean newIsRare = false;

			if (StringUtils.isNotBlank(taxonRefName)) {
				PoxPayloadOut taxonPayload = null;
			
				try {
					taxonPayload = findTaxonByRefName(taxonRefName);
				}
				catch (WebApplicationException e) {
					logger.error("Error finding taxon: refName=" + taxonRefName, e);
				}
				
				if (taxonPayload != null) {
					List<String> conservationCategories = getFieldValues(taxonPayload, TaxonConstants.CONSERVATION_CATEGORY_SCHEMA_NAME, TaxonConstants.CONSERVATION_CATEGORY_FIELD_NAME);
					
					for (String conservationCategory : conservationCategories) {
						if (StringUtils.isNotEmpty(conservationCategory)) {
							logger.debug("found non-null conservation category: " + conservationCategory);
							
							newIsRare = true;
							break;
						}
					}
				}
			}
			
			if (newIsRare != oldIsRare) {
				logger.debug("setting rare flag: collectionObjectCsid=" + collectionObjectCsid + " oldIsRare=" + oldIsRare +" newIsRare=" + newIsRare);
				
				setRareFlag(collectionObjectCsid, newIsRare);

				results.setNumAffected(1);
				results.setUserNote("rare flag set to " + (newIsRare ? "true" : "false"));
			}
			else {
				logger.debug("not setting rare flag: collectionObjectCsid=" + collectionObjectCsid + " oldIsRare=" + oldIsRare +" newIsRare=" + newIsRare);
				
				results.setNumAffected(0);
				results.setUserNote("rare flag not changed");
			}
		}
		
		return results;
	}
	
	/**
	 * Updates the rare flags of the specified collectionobjects.
	 * 
	 * @param collectionObjectCsids		The csids of the collectionobjects
	 * @return
	 * @throws URISyntaxException
	 * @throws DocumentException
	 */
	public InvocationResults updateRareFlags(List<String> collectionObjectCsids) throws URISyntaxException, DocumentException {
		int numSubmitted = collectionObjectCsids.size();
		long numAffected = 0;
		
		
		for (String collectionObjectCsid : collectionObjectCsids) {
			InvocationResults itemResults = updateRareFlag(collectionObjectCsid);

			numAffected += itemResults.getNumAffected();
		}
		
		InvocationResults results = new InvocationResults();
		results.setNumAffected(numAffected);
		results.setUserNote("updated " + numAffected + " of " + numSubmitted + " cataloging records");
		
		return results;
	}

	/**
	 * Updates the rare flags of all collectionobjects.
	 * 
	 * @return
	 * @throws URISyntaxException
	 * @throws DocumentException
	 */
	public InvocationResults updateAllRareFlags() throws URISyntaxException, DocumentException {
		long numFound = 0;
		long numAffected = 0;
		
		int pageSize = 50;
		int pageNum = 0;
		List<String> csids = Collections.emptyList();
		
		do {
			csids = findAllCollectionObjects(pageSize, pageNum);
			logger.debug("pageNum=" + pageNum + " pageSize=" + pageSize + " result size=" + csids.size());
			
			InvocationResults pageResults = updateRareFlags(csids);
			
			numAffected += pageResults.getNumAffected();
			numFound += csids.size();
			
			pageNum++;
		}
		while (csids.size() == pageSize);
		
		InvocationResults results = new InvocationResults();
		results.setNumAffected(numAffected);
		results.setUserNote("updated " + numAffected + " of " + numFound + " cataloging records");
		
		return null;
	}

	/**
	 * Sets the rare flag of the specified collectionobject to the specified value.
	 * 
	 * @param collectionObjectCsid	The csid of the collectionobject
	 * @param rareFlag				The value of the rare flag
	 */
	private void setRareFlag(String collectionObjectCsid, boolean rareFlag) {
		String updatePayload = 
			"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
			"<document name=\"collectionobjects\">" +
				"<ns2:collectionobjects_naturalhistory xmlns:ns2=\"http://collectionspace.org/services/collectionobject/domain/naturalhistory\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">" +
					getFieldXml("rare", (rareFlag ? "true" : "false")) +
				"</ns2:collectionobjects_naturalhistory>" +
				"<ns2:collectionobjects_common xmlns:ns2=\"http://collectionspace.org/services/collectionobject\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">" +
				"</ns2:collectionobjects_common>" +					
			"</document>";
		
		ResourceBase resource = getResourceMap().get(CollectionObjectClient.SERVICE_NAME);
		resource.update(getResourceMap(), collectionObjectCsid, updatePayload);
	}
}