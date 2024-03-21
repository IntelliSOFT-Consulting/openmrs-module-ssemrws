package org.openmrs.module.ssemrws.web.controller;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.test.jupiter.BaseModuleContextSensitiveTest;

import java.util.List;

public class SSEMRWebServicesControllerTest extends BaseModuleContextSensitiveTest {
	
	private static final String DATASET_FILENAME = "SSEMRWebServicesControllerTest.xml";
	
	@Before
	public void setUp() throws Exception {
		executeDataSet(DATASET_FILENAME);
		Context.getPatientService();
	}
	
	@Test
	public void getNewPatients() {
		List<Patient> patientList = Context.getPatientService().getAllPatients();
		assertEquals(1, patientList.size());
	}
	
}
