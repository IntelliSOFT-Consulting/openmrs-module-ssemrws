package org.openmrs.module.ssemrws.web.controller;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.test.BaseModuleContextSensitiveTest;

import java.util.List;

@SuppressWarnings("deprecation")
public class SSEMRWebServicesControllerTest extends BaseModuleContextSensitiveTest {
	
	// private static final String DATASET_FILENAME =
	// "org/openmrs/module/ssemrws/web/controller/resources/SSEMRWebServicesControllerTest.xml";
	
	@Before
	public void setUp() throws Exception {
		initializeInMemoryDatabase();
		executeDataSet("org/openmrs/module/ssemrws/web/controller/resources/SSEMRWebServicesControllerTest.xml");
	}
	
	@Test
	public void getNewPatients() {
		List<Patient> patientList = Context.getPatientService().getAllPatients();
		assertEquals(1, patientList.size());
	}
	
}
