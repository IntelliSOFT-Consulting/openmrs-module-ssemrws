package org.openmrs.module.ssemrws.constants;

import org.apache.commons.lang3.time.DateUtils;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.ObjectNode;
import org.openmrs.*;
import org.openmrs.api.PatientService;
import org.openmrs.api.context.Context;
import org.openmrs.module.ssemrws.web.controller.SSEMRWebServicesController;
import org.openmrs.module.ssemrws.web.dto.PatientObservations;
import org.openmrs.parameter.EncounterSearchCriteria;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import static org.openmrs.module.ssemrws.web.constants.AllConcepts.*;
import static org.openmrs.module.ssemrws.web.constants.GenerateSummary.generateSummary;
import static org.openmrs.module.ssemrws.web.constants.RegimenConcepts.*;

public class SharedConstants {
	
	public static SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("dd-MM-yyyy");
	
	public static final double THRESHOLD = 1000.0;
	
	public static final int SIX_MONTHS_IN_DAYS = 183;
	
	@PersistenceContext
	private EntityManager entityManager;
	
	public static Date[] getStartAndEndDate(String qStartDate, String qEndDate, SimpleDateFormat dateTimeFormatter)
	        throws ParseException {
		Date endDate = (qEndDate != null) ? dateTimeFormatter.parse(qEndDate) : new Date();
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(endDate);
		calendar.set(Calendar.DAY_OF_MONTH, 1);
		Date startDate = (qStartDate != null) ? dateTimeFormatter.parse(qStartDate) : calendar.getTime();
		
		return new Date[] { startDate, endDate };
	}
	
	public static Object getPatientsOnRegimenTreatment(String qStartDate, String qEndDate, List<String> regimenConceptUuids,
	        String activeRegimenConceptUuid) throws ParseException {
		
		SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
		Date[] dates = getStartAndEndDate(qStartDate, qEndDate, dateTimeFormatter);
		
		List<Concept> regimenConcepts = getConceptsByUuids(regimenConceptUuids);
		
		List<Obs> regimenTreatmentObs = Context.getObsService().getObservations(null, null,
		    Collections.singletonList(Context.getConceptService().getConceptByUuid(activeRegimenConceptUuid)),
		    regimenConcepts, null, null, null, null, null, dates[0], dates[1], false);
		
		Map<String, Integer> regimenCounts = new HashMap<>();
		
		for (Obs obs : regimenTreatmentObs) {
			Concept regimenConcept = obs.getValueCoded();
			if (regimenConcept != null) {
				String conceptName = regimenConcept.getName().getName();
				regimenCounts.put(conceptName, regimenCounts.getOrDefault(conceptName, 0) + 1);
			}
		}
		
		Map<String, Object> results = new HashMap<>();
		List<Map<String, Object>> regimenList = new ArrayList<>();
		
		for (Map.Entry<String, Integer> entry : regimenCounts.entrySet()) {
			Map<String, Object> regimenEntry = new HashMap<>();
			regimenEntry.put("text", entry.getKey());
			regimenEntry.put("total", entry.getValue());
			regimenList.add(regimenEntry);
		}
		
		results.put("results", regimenList);
		return results;
	}
	
	// Retrieves a list of encounters filtered by encounter types.
	public static List<Encounter> getEncountersByEncounterTypes(List<String> encounterTypeUuids, Date startDate,
	        Date endDate) {
		List<EncounterType> encounterTypes = encounterTypeUuids.stream()
		        .map(uuid -> Context.getEncounterService().getEncounterTypeByUuid(uuid)).collect(Collectors.toList());
		
		EncounterSearchCriteria encounterCriteria = new EncounterSearchCriteria(null, null, startDate, endDate, null, null,
		        encounterTypes, null, null, null, false);
		return Context.getEncounterService().getEncounters(encounterCriteria);
	}
	
	/**
	 * Retrieves a list of concepts based on their UUIDs.
	 * 
	 * @param conceptUuids A list of UUIDs of concepts to retrieve.
	 * @return A list of concepts corresponding to the given UUIDs.
	 */
	public static List<Concept> getConceptsByUuids(List<String> conceptUuids) {
		return conceptUuids.stream().map(uuid -> Context.getConceptService().getConceptByUuid(uuid))
		        .collect(Collectors.toList());
	}
	
	public static String getARTRegimen(Patient patient) {
		List<Obs> artRegimenObs = Context.getObsService().getObservations(Collections.singletonList(patient.getPerson()),
		    null, Collections.singletonList(Context.getConceptService().getConceptByUuid(ACTIVE_REGIMEN_CONCEPT_UUID)), null,
		    null, null, null, null, null, null, null, false);
		
		artRegimenObs.sort(Comparator.comparing(Obs::getObsDatetime).reversed());
		
		for (Obs obs : artRegimenObs) {
			if (obs.getValueCoded() != null) {
				return obs.getValueCoded().getName().getName();
			}
		}
		return "";
	}
	
	// Determine Patient Enrollment Date From the Adult and Adolescent and Pediatric
	// Forms
	public static String getEnrolmentDate(Patient patient) {
		List<Obs> enrollmentDateObs = Context.getObsService().getObservations(Collections.singletonList(patient.getPerson()),
		    null, Collections.singletonList(Context.getConceptService().getConceptByUuid(DATE_OF_ENROLLMENT_UUID)), null,
		    null, null, null, null, null, null, null, false);
		
		enrollmentDateObs.sort(Comparator.comparing(Obs::getObsDatetime).reversed());
		
		if (!enrollmentDateObs.isEmpty()) {
			Obs dateObs = enrollmentDateObs.get(0);
			Date enrollmentDate = dateObs.getValueDate();
			if (enrollmentDate != null) {
				return dateTimeFormatter.format(enrollmentDate);
			}
		}
		return "";
		
	}
	
	// Retrieve the Last Refill Date from Patient Observation
	public static String getLastRefillDate(Patient patient) {
		List<Obs> lastRefillDateObs = Context.getObsService().getObservations(Collections.singletonList(patient.getPerson()),
		    null, Collections.singletonList(Context.getConceptService().getConceptByUuid(LAST_REFILL_DATE_UUID)), null, null,
		    null, null, null, null, null, null, false);
		
		lastRefillDateObs.sort(Comparator.comparing(Obs::getObsDatetime).reversed());
		
		if (!lastRefillDateObs.isEmpty()) {
			Obs lastObs = lastRefillDateObs.get(0);
			Date lastRefillDate = lastObs.getValueDate();
			if (lastRefillDate != null) {
				return dateTimeFormatter.format(lastRefillDate);
			}
		}
		return "";
	}
	
	public static boolean determineIfPatientIsPregnantOrBreastfeeding(Patient patient, Date endDate) {
		
		List<Concept> pregnantAndBreastfeedingConcepts = new ArrayList<>();
		pregnantAndBreastfeedingConcepts
		        .add(Context.getConceptService().getConceptByUuid(CURRENTLY_BREASTFEEDING_CONCEPT_UUID));
		pregnantAndBreastfeedingConcepts.add(Context.getConceptService().getConceptByUuid(CURRENTLY_PREGNANT_CONCEPT_UUID));
		
		List<Obs> obsList = Context.getObsService().getObservations(Collections.singletonList(patient), null,
		    pregnantAndBreastfeedingConcepts,
		    Collections.singletonList(Context.getConceptService().getConceptByUuid(CONCEPT_BY_UUID)), null, null, null, null,
		    null, null, endDate, false);
		
		return !obsList.isEmpty();
	}
	
	public static Map<String, Object> createResultMap(String key, int value) {
		Map<String, Object> resultMap = new HashMap<>();
		resultMap.put(key, value);
		return resultMap;
	}
	
	public static ResponseEntity<Object> buildErrorResponse(String message, HttpStatus status) {
		return new ResponseEntity<>(message, new HttpHeaders(), status);
	}
	
	public static List<Map<String, String>> getIdentifiersList(Patient patient) {
		List<Map<String, String>> identifiersList = new ArrayList<>();
		for (PatientIdentifier identifier : patient.getIdentifiers()) {
			Map<String, String> identifierObj = new HashMap<>();
			identifierObj.put("identifier", identifier.getIdentifier().trim());
			identifierObj.put("identifierType", identifier.getIdentifierType().getName().trim());
			identifiersList.add(identifierObj);
		}
		return identifiersList;
	}
	
	public static ArrayNode getPatientIdentifiersArray(Patient patient) {
		ArrayNode identifiersArray = JsonNodeFactory.instance.arrayNode();
		for (PatientIdentifier identifier : patient.getIdentifiers()) {
			ObjectNode identifierObj = JsonNodeFactory.instance.objectNode();
			identifierObj.put("identifier", identifier.getIdentifier());
			identifierObj.put("identifierType", identifier.getIdentifierType().getName());
			identifiersArray.add(identifierObj);
		}
		return identifiersArray;
	}
	
	public static String getPatientFullAddress(Patient patient) {
		String village = "";
		String landmark = "";
		for (PersonAddress address : patient.getAddresses()) {
			if (address.getAddress5() != null) {
				village = address.getAddress5();
			}
			if (address.getAddress6() != null) {
				landmark = address.getAddress6();
			}
		}
		return "Village: " + village + ", Landmark: " + landmark;
	}
	
	// Method to extract the numeric part of the identifier
	public static String extractNumericPart(String identifier) {
		String numericPart = identifier.replaceAll("\\D+", "");
		return numericPart.isEmpty() ? "0" : numericPart;
	}
	
	public static String formatBirthdate(Date birthdate) {
		return dateTimeFormatter.format(birthdate);
	}
	
	public static long calculateAge(Date birthdate) {
		Date currentDate = new Date();
		return (currentDate.getTime() - birthdate.getTime()) / (1000L * 60 * 60 * 24 * 365);
	}
	
	public static Map<String, Object> buildResponseMap(Patient patient, long age, String birthdate,
	        List<Map<String, String>> identifiersList, PatientObservations observations) {
		Map<String, Object> responseMap = new LinkedHashMap<>();
		responseMap.put("Name", patient.getPersonName() != null ? patient.getPersonName().toString() : "");
		responseMap.put("uuid", patient.getUuid());
		responseMap.put("age", age);
		responseMap.put("birthdate", birthdate);
		responseMap.put("sex", patient.getGender());
		responseMap.put("Identifiers", identifiersList);
		responseMap.put("results", Collections.singletonList(observations));
		
		return responseMap;
	}
	
	public static Double getLastCD4Count(Patient patient) {
		Concept lastCD4CountConcept = Context.getConceptService().getConceptByUuid(LAST_CD4_COUNT_UUID);
		List<Obs> lastCD4CountObs = Context.getObsService().getObservations(Collections.singletonList(patient.getPerson()),
		    null, Collections.singletonList(lastCD4CountConcept), null, null, null, null, null, null, null, null, false);
		
		if (!lastCD4CountObs.isEmpty()) {
			Obs lastcd4Obs = lastCD4CountObs.get(0);
			return lastcd4Obs.getValueNumeric();
		}
		return null;
	}
	
	public static String getTbStatus(Patient patient) {
		Concept tbStatusConcepts = Context.getConceptService().getConceptByUuid(TB_STATUS_CONCEPT_UUID);
		
		List<Obs> tbStatusObs = Context.getObsService().getObservations(Collections.singletonList(patient.getPerson()), null,
		    Collections.singletonList(tbStatusConcepts), null, null, null, null, null, null, null, null, false);
		
		if (!tbStatusObs.isEmpty()) {
			Obs tbStatus = tbStatusObs.get(0);
			return tbStatus.getValueCoded().getName().getName();
		}
		return "";
	}
	
	public static String getARVRegimenDose(Patient patient) {
		Concept arvRegimenDoseConcept = Context.getConceptService().getConceptByUuid(ARV_REGIMEN_DOSE_UUID);
		
		List<Obs> arvRegimenDoseObs = Context.getObsService().getObservations(Collections.singletonList(patient.getPerson()),
		    null, Collections.singletonList(arvRegimenDoseConcept), null, null, null, null, null, null, null, null, false);
		
		if (arvRegimenDoseObs.isEmpty()) {
			System.err.println("No observations found for the concept " + arvRegimenDoseConcept.getName().getName() + ".");
			return "";
		}
		
		Obs arvRegimenDose = arvRegimenDoseObs.get(0);
		
		if (arvRegimenDose.getValueText() != null) {
			return arvRegimenDose.getValueText();
		} else if (arvRegimenDose.getValueCoded() != null) {
			return arvRegimenDose.getValueCoded().getName().getName();
		} else {
			return "";
		}
	}
	
	public static String getWHOClinicalStage(Patient patient) {
		Concept whoClinicalConcept = Context.getConceptService().getConceptByUuid(WHO_CLINICAL_UUID);
		Concept whoClinicalStageIntakeConcept = Context.getConceptService().getConceptByUuid(WHO_CLINICAL_STAGE_INTAKE_UUID);
		
		List<Concept> whoConcepts = Arrays.asList(whoClinicalConcept, whoClinicalStageIntakeConcept);
		
		List<Obs> obsList = Context.getObsService().getObservations(Collections.singletonList(patient), null, whoConcepts,
		    null, null, null, null, null, null, null, null, false);
		
		if (obsList.isEmpty()) {
			return "";
		}
		
		Obs whoClinicalStageObs = obsList.get(0);
		
		if (whoClinicalStageObs.getValueText() != null) {
			return whoClinicalStageObs.getValueText();
		} else if (whoClinicalStageObs.getValueCoded() != null) {
			return whoClinicalStageObs.getValueCoded().getName().getName();
		} else {
			return "";
		}
	}
	
	public static String getDateVLResultsReceived(Patient patient) {
		Concept dateVLResultsReceivedConcept = Context.getConceptService().getConceptByUuid(DATE_VL_RESULTS_RECEIVED_UUID);
		
		List<Obs> dateVLResultsReceivedObs = Context.getObsService().getObservations(
		    Collections.singletonList(patient.getPerson()), null, Collections.singletonList(dateVLResultsReceivedConcept),
		    null, null, null, null, null, null, null, null, false);
		
		if (!dateVLResultsReceivedObs.isEmpty()) {
			Obs dateVLReceivedObs = dateVLResultsReceivedObs.get(0);
			Date dateVLResultsReceived = dateVLReceivedObs.getValueDate();
			if (dateVLResultsReceived != null) {
				return dateTimeFormatter.format(dateVLResultsReceived);
			}
		}
		return "";
	}
	
	public static String getCHWName(Patient patient) {
		Concept chwNameConcepts = Context.getConceptService().getConceptByUuid(CHW_NAME_UUID);
		
		List<Obs> chwNameObs = Context.getObsService().getObservations(Collections.singletonList(patient.getPerson()), null,
		    Collections.singletonList(chwNameConcepts), null, null, null, null, null, null, null, null, false);
		
		if (!chwNameObs.isEmpty()) {
			Obs chwName = chwNameObs.get(0);
			return chwName.getValueText();
		}
		return "";
	}
	
	public static String getCHWPhone(Patient patient) {
		Concept chwPhoneConcepts = Context.getConceptService().getConceptByUuid(CHW_PHONE_UUID);
		
		List<Obs> chwPhoneObs = Context.getObsService().getObservations(Collections.singletonList(patient.getPerson()), null,
		    Collections.singletonList(chwPhoneConcepts), null, null, null, null, null, null, null, null, false);
		
		if (!chwPhoneObs.isEmpty()) {
			Obs chwPhone = chwPhoneObs.get(0);
			return chwPhone.getValueText();
		}
		return "";
	}
	
	public static String getCHWAddress(Patient patient) {
		Concept chwAddressConcepts = Context.getConceptService().getConceptByUuid(CHW_ADDRESS_UUID);
		
		List<Obs> chwAddressObs = Context.getObsService().getObservations(Collections.singletonList(patient.getPerson()),
		    null, Collections.singletonList(chwAddressConcepts), null, null, null, null, null, null, null, null, false);
		
		if (!chwAddressObs.isEmpty()) {
			Obs chwAddress = chwAddressObs.get(0);
			return chwAddress.getValueText();
		}
		return "";
	}
	
	public static String getVLResults(Patient patient) {
		Concept viralLoadResultsConcept = Context.getConceptService().getConceptByUuid(VIRAL_LOAD_RESULTS_UUID);
		Concept bdlConcept = Context.getConceptService().getConceptByUuid(BDL_CONCEPT_UUID);
		Concept viralLoadConcept = Context.getConceptService().getConceptByUuid(VIRAL_LOAD_CONCEPT_UUID);
		
		List<Obs> getVLResultNumericObs = Context.getObsService().getObservations(
		    Collections.singletonList(patient.getPerson()), null, Collections.singletonList(viralLoadConcept), null, null,
		    null, null, 1, null, null, null, false);
		
		List<Obs> getVLResultObs = Context.getObsService().getObservations(Collections.singletonList(patient.getPerson()),
		    null, Collections.singletonList(viralLoadResultsConcept), null, null, null, null, 1, null, null, null, false);
		
		List<Obs> allObservations = new ArrayList<>();
		allObservations.addAll(getVLResultNumericObs);
		allObservations.addAll(getVLResultObs);
		
		allObservations.sort((o1, o2) -> o2.getObsDatetime().compareTo(o1.getObsDatetime()));
		
		if (!allObservations.isEmpty()) {
			Obs mostRecentObs = allObservations.get(0);
			
			if (mostRecentObs.getValueNumeric() != null) {
				return mostRecentObs.getValueNumeric().toString();
			} else if (mostRecentObs.getValueText() != null) {
				return mostRecentObs.getValueText();
			} else if (mostRecentObs.getValueCoded() != null) {
				if (mostRecentObs.getValueCoded().equals(bdlConcept)) {
					return "Below Detectable (BDL)";
				} else {
					return mostRecentObs.getValueCoded().getName().getName();
				}
			} else {
				System.err.println("Observation value is neither numeric, text, nor coded.");
			}
		}
		return null;
	}
	
	public static String getVLStatus(Patient patient) {
		String vlResult = getVLResults(patient);
		
		if (vlResult == null) {
			System.err.println("VL result is null for patient: " + patient);
			return "Unknown";
		}
		
		try {
			double vlValue = Double.parseDouble(vlResult);
			
			if (vlValue >= 1000) {
				return "Unsuppressed";
			} else {
				return "Suppressed";
			}
		}
		catch (NumberFormatException e) {
			if ("Below Detectable (BDL)".equalsIgnoreCase(vlResult)) {
				return "Suppressed";
			} else {
				System.err.println("Error parsing VL result or unrecognized value: " + vlResult);
				return "Unknown";
			}
		}
	}
	
	public static Double getBMI(Patient patient) {
		List<Obs> bmiObs = Context.getObsService().getObservations(Collections.singletonList(patient.getPerson()), null,
		    Collections.singletonList(Context.getConceptService().getConceptByUuid(BMI_CONCEPT_UUID)), null, null, null,
		    null, 1, null, null, null, false);
		
		if (!bmiObs.isEmpty()) {
			Obs bmiObservation = bmiObs.get(0);
			return bmiObservation.getValueNumeric();
		}
		
		return null;
	}
	
	public static Double getMUAC(Patient patient) {
		List<Obs> muacObs = Context.getObsService().getObservations(Collections.singletonList(patient.getPerson()), null,
		    Collections.singletonList(Context.getConceptService().getConceptByUuid(MUAC_CONCEPT_UUID)), null, null, null,
		    null, 1, null, null, null, false);
		
		if (!muacObs.isEmpty()) {
			Obs muacObservation = muacObs.get(0);
			return muacObservation.getValueNumeric();
		}
		
		return null;
	}
	
	public static String getClinicianName(Patient patient) {
		List<Obs> clinicianObs = Context.getObsService().getObservations(Collections.singletonList(patient.getPerson()),
		    null, Collections.singletonList(Context.getConceptService().getConceptByUuid(CLINICIAN_CONCEPT_UUID)), null,
		    null, null, null, 1, null, null, null, false);
		
		if (!clinicianObs.isEmpty()) {
			Obs clinicianObservation = clinicianObs.get(0);
			return clinicianObservation.getValueText();
		}
		
		return "";
	}
	
	public enum ClinicalStatus {
		INTERRUPTED_IN_TREATMENT,
		DIED,
		ACTIVE,
		TRANSFERRED_OUT
	}
	
	public static HashSet<Patient> getTransferredOutClients(Date startDate, Date endDate) {
		Concept transferredOutConcept = ConceptCache.getCachedConcept(TRANSFERRED_OUT_CONCEPT_UUID);
		Concept yesConcept = ConceptCache.getCachedConcept(YES_CONCEPT);
		
		List<Obs> transferredOutPatientsObs = Context.getObsService().getObservations(null, null,
		    Collections.singletonList(transferredOutConcept), Collections.singletonList(yesConcept), null, null, null, null,
		    null, startDate, endDate, false);
		
		HashSet<Patient> transferredOutPatients = new HashSet<>();
		
		for (Obs obs : transferredOutPatientsObs) {
			Person person = obs.getPerson();
			if (person != null) {
				Patient patient = Context.getPatientService().getPatient(person.getPersonId());
				if (patient != null) {
					transferredOutPatients.add(patient);
				}
			}
		}
		
		HashSet<Patient> deceasedPatients = getDeceasedPatientsByDateRange(startDate, endDate);
		transferredOutPatients.removeAll(deceasedPatients);
		
		return transferredOutPatients;
	}
	
	public static HashSet<Patient> getDeceasedPatientsByDateRange(Date startDate, Date endDate) {
		Concept deceasedConcept = ConceptCache.getCachedConcept(DECEASED_CONCEPT_UUID);
		Concept yesConcept = ConceptCache.getCachedConcept(YES_CONCEPT);
		
		List<Obs> deceasedPatientsObs = Context.getObsService().getObservations(null, null,
		    Collections.singletonList(deceasedConcept), Collections.singletonList(yesConcept), null, null, null, null, null,
		    startDate, endDate, false);
		
		HashSet<Patient> deadPatients = new HashSet<>();
		
		for (Obs obs : deceasedPatientsObs) {
			Person person = obs.getPerson();
			if (person != null) {
				Patient patient = Context.getPatientService().getPatient(person.getPersonId());
				if (patient != null) {
					deadPatients.add(patient);
				}
			}
		}
		
		return deadPatients;
	}
	
	public static List<Encounter> getEncountersByDateRange(List<String> encounterTypeUuids, Date startDate, Date endDate) {
		return getEncountersByEncounterTypes(encounterTypeUuids, startDate, endDate);
	}
	
	public static List<Obs> getObservationsByDateRange(List<Encounter> encounters, List<Concept> concepts, Date startDate,
	        Date endDate) {
		return Context.getObsService().getObservations(null, null, concepts, null, null, null, null, null, null, startDate,
		    endDate, false);
	}
	
	public static HashSet<Patient> extractPatientsFromEncounters(List<Encounter> encounters) {
		HashSet<Patient> patients = new HashSet<>();
		for (Encounter encounter : encounters) {
			patients.add(encounter.getPatient());
		}
		return patients;
	}
	
	public static HashSet<Patient> extractPatientsFromObservations(List<Obs> observations) {
		HashSet<Patient> patients = new HashSet<>();
		for (Obs obs : observations) {
			Person person = obs.getPerson();
			if (person != null) {
				Patient patient = Context.getPatientService().getPatient(person.getPersonId());
				if (patient != null) {
					patients.add(patient);
				}
			}
		}
		return patients;
	}
	
	public static HashSet<Patient> getTransferredInPatients(Date startDate, Date endDate) {
		PatientService patientService = Context.getPatientService();
		List<Patient> allPatients = patientService.getAllPatients();
		
		return allPatients.stream()
		        .filter(patient -> patient.getIdentifiers().stream()
		                .anyMatch(identifier -> identifier.getIdentifier().startsWith("TI-")))
		        .collect(Collectors.toCollection(HashSet::new));
	}
	
	public static HashSet<Patient> getReturnToTreatmentPatients(Date startDate, Date endDate) {
		List<String> returnedToTreatmentencounterTypeUuids = Collections
		        .singletonList(ART_TREATMENT_INTURRUPTION_ENCOUNTER_TYPE_UUID);
		
		List<Encounter> returnedToTreatmentEncounters = getEncountersByEncounterTypes(returnedToTreatmentencounterTypeUuids,
		    startDate, endDate);
		
		List<Obs> returnedToTreatmentObs = Context.getObsService().getObservations(null, returnedToTreatmentEncounters,
		    Collections.singletonList(Context.getConceptService().getConceptByUuid(RETURNING_TO_TREATMENT_UUID)),
		    Collections.singletonList(Context.getConceptService().getConceptByUuid(CONCEPT_BY_UUID)), null, null, null, null,
		    null, startDate, endDate, false);
		
		HashSet<Patient> returnToTreatmentPatients = new HashSet<>();
		
		for (Obs obs : returnedToTreatmentObs) {
			Patient patient = (Patient) obs.getPerson();
			returnToTreatmentPatients.add(patient);
		}
		
		return returnToTreatmentPatients;
	}
	
	// Get all patients who have high Viral Load
	public static HashSet<Patient> getPatientsWithHighVL(Date startDate, Date endDate) {
		return getPatientsWithVL(startDate, endDate, FOLLOW_UP_FORM_ENCOUNTER_TYPE, VIRAL_LOAD_CONCEPT_UUID);
	}
	
	public static HashSet<Patient> getPatientsWithPersistentHighVL(Date startDate, Date endDate) {
		return getPatientsWithVL(startDate, endDate, HIGH_VL_ENCOUNTERTYPE_UUID, REPEAT_VL_RESULTS);
	}
	
	public static HashSet<Patient> getPatientsWithVL(Date startDate, Date endDate, String encounterTypeUuid,
	        String conceptUuid) {
		EncounterType encounterType = Context.getEncounterService().getEncounterTypeByUuid(encounterTypeUuid);
		EncounterSearchCriteria encounterSearchCriteria = new EncounterSearchCriteria(null, null, startDate, endDate, null,
		        null, Collections.singletonList(encounterType), null, null, null, false);
		List<Encounter> encounters = Context.getEncounterService().getEncounters(encounterSearchCriteria);
		
		HashSet<Patient> vlPatients = new HashSet<>();
		
		List<Obs> vlObs = Context.getObsService().getObservations(null, encounters,
		    Collections.singletonList(Context.getConceptService().getConceptByUuid(conceptUuid)), null, null, null, null,
		    null, null, startDate, endDate, false);
		
		for (Obs obs : vlObs) {
			if (obs.getValueNumeric() != null && obs.getValueNumeric() >= THRESHOLD) {
				vlPatients.add((Patient) obs.getPerson());
			}
		}
		
		HashSet<Patient> deceasedPatients = getDeceasedPatientsByDateRange(startDate, endDate);
		HashSet<Patient> transferredOutPatients = getTransferredOutClients(startDate, endDate);
		
		vlPatients.removeAll(deceasedPatients);
		vlPatients.removeAll(transferredOutPatients);
		
		return vlPatients;
	}
	
	public static HashSet<Patient> getPatientsWithRepeatedVL(Date startDate, Date endDate) {
		EncounterType repeatViralLoadEncounterType = Context.getEncounterService()
		        .getEncounterTypeByUuid(HIGH_VL_ENCOUNTERTYPE_UUID);
		EncounterSearchCriteria encounterSearchCriteria = new EncounterSearchCriteria(null, null, startDate, endDate, null,
		        null, Collections.singletonList(repeatViralLoadEncounterType), null, null, null, false);
		List<Encounter> encounters = Context.getEncounterService().getEncounters(encounterSearchCriteria);
		
		HashSet<Patient> repeatviralLoadPatients = new HashSet<>();
		
		List<Obs> repeatviralLoadObs = Context.getObsService().getObservations(null, encounters,
		    Collections.singletonList(Context.getConceptService().getConceptByUuid(REAPEAT_VL_COLLECTION)), null, null, null,
		    null, null, null, startDate, endDate, false);
		
		for (Obs obs : repeatviralLoadObs) {
			if (obs != null) {
				repeatviralLoadPatients.add((Patient) obs.getPerson());
			}
		}
		
		return repeatviralLoadPatients;
		
	}
	
	public static HashSet<Patient> getPatientsWithSwitchART(Date startDate, Date endDate) {
		EncounterType switchARTRegimenEncounterType = Context.getEncounterService()
		        .getEncounterTypeByUuid(FOLLOW_UP_FORM_ENCOUNTER_TYPE);
		EncounterSearchCriteria encounterSearchCriteria = new EncounterSearchCriteria(null, null, startDate, endDate, null,
		        null, Collections.singletonList(switchARTRegimenEncounterType), null, null, null, false);
		List<Encounter> encounters = Context.getEncounterService().getEncounters(encounterSearchCriteria);
		
		HashSet<Patient> switchARTRegimenPatients = new HashSet<>();
		Map<Patient, String> patientPreviousRegimen = new HashMap<>();
		
		List<Obs> switchARTRegimenObs = Context.getObsService().getObservations(null, encounters,
		    Collections.singletonList(Context.getConceptService().getConceptByUuid(ACTIVE_REGIMEN_CONCEPT_UUID)), null, null,
		    null, null, null, null, startDate, endDate, false);
		
		for (Obs obs : switchARTRegimenObs) {
			if (obs != null && obs.getPerson() instanceof Patient) {
				Patient patient = (Patient) obs.getPerson();
				String currentRegimen = obs.getValueCoded() != null ? obs.getValueCoded().getUuid() : null;
				
				if (currentRegimen != null) {
					if (patientPreviousRegimen.containsKey(patient)) {
						String previousRegimen = patientPreviousRegimen.get(patient);
						if (!currentRegimen.equals(previousRegimen)) {
							switchARTRegimenPatients.add(patient);
						}
					}
					patientPreviousRegimen.put(patient, currentRegimen);
				}
			}
		}
		
		return switchARTRegimenPatients;
	}
	
	public static HashSet<Patient> getPatientsWithSecondLineSwitchART(Date startDate, Date endDate) {
		EncounterType secondLineSwitchARTRegimenEncounterType = Context.getEncounterService()
		        .getEncounterTypeByUuid(FOLLOW_UP_FORM_ENCOUNTER_TYPE);
		EncounterSearchCriteria encounterSearchCriteria = new EncounterSearchCriteria(null, null, startDate, endDate, null,
		        null, Collections.singletonList(secondLineSwitchARTRegimenEncounterType), null, null, null, false);
		List<Encounter> encounters = Context.getEncounterService().getEncounters(encounterSearchCriteria);
		
		HashSet<Patient> secondLineSwitchARTRegimenPatients = new HashSet<>();
		
		List<Obs> secondLineSwitchARTRegimenObs = Context.getObsService().getObservations(null, encounters,
		    Collections.singletonList(Context.getConceptService().getConceptByUuid(ACTIVE_REGIMEN_CONCEPT_UUID)), null, null,
		    null, null, null, null, startDate, endDate, false);
		
		for (Obs obs : secondLineSwitchARTRegimenObs) {
			if (obs != null && obs.getPerson() instanceof Patient) {
				Patient patient = (Patient) obs.getPerson();
				String currentRegimen = obs.getValueCoded() != null ? obs.getValueCoded().getUuid() : null;
				
				if (currentRegimen != null && SECOND_LINE_REGIMENS.contains(currentRegimen)) {
					secondLineSwitchARTRegimenPatients.add(patient);
				}
			}
		}
		
		return secondLineSwitchARTRegimenPatients;
	}
	
	// Determine if Patient is High Viral Load and return true if it is equal or
	// above threshold
	public static boolean determineIfPatientIsHighVl(Patient patient) {
		List<Obs> vlObs = Context.getObsService().getObservations(Collections.singletonList(patient.getPerson()), null,
		    Collections.singletonList(Context.getConceptService().getConceptByUuid(VIRAL_LOAD_CONCEPT_UUID)), null, null,
		    null, null, 1, null, null, null, false);
		
		if (vlObs != null && !vlObs.isEmpty()) {
			return vlObs.get(0).getValueNumeric() >= THRESHOLD;
		}
		return false;
	}
	
	public static Map<String, Map<String, Integer>> generateDashboardSummaryFromObs(Date startDate, Date endDate,
	        List<Obs> obsList, SSEMRWebServicesController.filterCategory filterCategory) {
		
		if (obsList == null) {
			throw new IllegalArgumentException("Observation list cannot be null");
		}
		
		List<Date> dates = new ArrayList<>();
		for (Obs obs : obsList) {
			if (obs == null) {
				System.out.println("Encountered null observation");
				continue;
			}
			
			Date obsDate = obs.getObsDatetime();
			if (obsDate == null) {
				System.out.println("Encountered observation with null date: " + obs);
				continue;
			}
			
			if (obsDate.after(DateUtils.addDays(startDate, -1)) && obsDate.before(DateUtils.addDays(endDate, 1))) {
				dates.add(obsDate);
			}
		}
		
		return generateSummary(dates);
	}
}
