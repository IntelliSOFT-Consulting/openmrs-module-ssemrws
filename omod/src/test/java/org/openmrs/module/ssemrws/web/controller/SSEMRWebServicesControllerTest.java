package org.openmrs.module.ssemrws.web.controller;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.test.BaseModuleContextSensitiveTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Date;
import java.util.List;

@SuppressWarnings("deprecation")
@RunWith(SpringJUnit4ClassRunner.class)
public class SSEMRWebServicesControllerTest extends BaseModuleContextSensitiveTest {
	
	@Before
	public void setUp() throws Exception {
		initializeInMemoryDatabase();
		Patient patient = new Patient();
		patient.setGender("M");
		patient.setBirthdate(new Date());
		patient.setBirthdateEstimated(false);
		patient.setDead(false);
		patient.setDeathDate(null);
		patient.setCauseOfDeath(null);
		patient.setCreator(Context.getAuthenticatedUser());
		patient.setDateCreated(new Date());
		patient.setChangedBy(null);
		patient.setDateChanged(null);
		patient.setVoided(false);
		patient.setVoidedBy(null);
		patient.setDateVoided(null);
		patient.setVoidReason(null);
		Context.getPatientService().savePatient(patient);
	}
	
	@Test
	public void getNewPatients() {
		List<Patient> patientList = Context.getPatientService().getAllPatients();
		assertEquals(1, patientList.size());
	}
	
}
