/**	
 * This document is a part of the source code and related artifacts
 * for CollectionSpace, an open source collections management system
 * for museums and related institutions:
 *
 * http://www.collectionspace.org
 * http://wiki.collectionspace.org
 *
 * Copyright © 2009 Regents of the University of California
 *
 * Licensed under the Educational Community License (ECL), Version 2.0.
 * You may not use this file except in compliance with this License.
 *
 * You may obtain a copy of the ECL 2.0 License at
 * https://source.collectionspace.org/collection-space/LICENSE.txt
 */

package org.collectionspace.services.client;

import org.collectionspace.services.jaxb.AbstractCommonList;
import org.jboss.resteasy.client.ClientResponse;

/**
 * MovementClient.java
 *
 * $LastChangedRevision$
 * $LastChangedDate$
 *
 */
public class MovementClient extends AbstractCommonListPoxServiceClientImpl<MovementProxy> {
    public static final String SERVICE_NAME = "movements";
	public static final String SERVICE_PATH_COMPONENT = SERVICE_NAME;	
	public static final String SERVICE_PATH = "/" + SERVICE_PATH_COMPONENT;
	public static final String SERVICE_PATH_PROXY = SERVICE_PATH + "/";	
	public static final String SERVICE_PAYLOAD_NAME = SERVICE_NAME;

    @Override
    public String getServiceName() {
        return SERVICE_NAME;
    }

    @Override
    public String getServicePathComponent() {
        return SERVICE_PATH_COMPONENT;
    }

	@Override
	public Class<MovementProxy> getProxyClass() {
		return MovementProxy.class;
	}


    /**
     * @param sortFieldName
     * @return
     * @see org.collectionspace.services.client.MovementProxy#readList(java.lang.String)
     */
    public ClientResponse<AbstractCommonList> readListSortedBy(String sortFieldName) {
        return getProxy().readListSortedBy(sortFieldName);
    }

    /**
     * @param sortFieldName
     * @param keywords
     * @return
     * @see org.collectionspace.services.client.MovementProxy#keywordSearchSortedBy(java.lang.String, java.lang.String)
     */
    public ClientResponse<AbstractCommonList> keywordSearchSortedBy(String keywords, String sortFieldName) {
        return getProxy().keywordSearchSortedBy(keywords, sortFieldName);
    }    
}
