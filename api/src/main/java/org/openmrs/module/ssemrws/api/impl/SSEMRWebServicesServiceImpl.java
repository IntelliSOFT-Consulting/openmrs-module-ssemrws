/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.ssemrws.api.impl;

import org.openmrs.api.APIException;
import org.openmrs.api.UserService;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.ssemrws.Item;
import org.openmrs.module.ssemrws.api.SSEMRWebServicesService;
import org.openmrs.module.ssemrws.api.dao.SSEMRWebServicesDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SSEMRWebServicesServiceImpl extends BaseOpenmrsService implements SSEMRWebServicesService {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(SSEMRWebServicesServiceImpl.class);
	
	SSEMRWebServicesDao dao;
	
	UserService userService;
	
	/**
	 * Injected in moduleApplicationContext.xml
	 */
	public void setDao(SSEMRWebServicesDao dao) {
		this.dao = dao;
	}
	
	/**
	 * Injected in moduleApplicationContext.xml
	 */
	public void setUserService(UserService userService) {
		this.userService = userService;
	}
	
	@Override
	public Item getItemByUuid(String uuid) throws APIException {
		return dao.getItemByUuid(uuid);
	}
	
	@Override
	public Item saveItem(Item item) throws APIException {
		if (item.getOwner() == null) {
			item.setOwner(userService.getUser(1));
		}
		LOGGER.info("saving into database using dao");
		return dao.saveItem(item);
	}
}
