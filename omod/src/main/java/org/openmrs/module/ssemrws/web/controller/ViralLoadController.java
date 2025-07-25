package org.openmrs.module.ssemrws.web.controller;

import org.codehaus.jackson.map.ObjectMapper;
import org.openmrs.*;
import org.openmrs.api.context.Context;
import org.openmrs.module.ssemrws.queries.GetDueForVL;
import org.openmrs.module.ssemrws.web.constants.FilterUtility;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.parameter.EncounterSearchCriteria;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import static org.openmrs.module.ssemrws.constants.SharedConstants.*;
import static org.openmrs.module.ssemrws.web.constants.AllConcepts.*;
import static org.openmrs.module.ssemrws.web.constants.AllConcepts.EAC_SESSION_CONCEPT_UUID;
import static org.openmrs.module.ssemrws.web.constants.ViralLoadCascade.getViralLoadCascade;

/**
 * This class configured as controller using annotation and mapped with the URL of
 * 'module/${rootArtifactid}/${rootArtifactid}Link.form'.
 */
@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + "/ssemr")
public class ViralLoadController {
	
	private final GetDueForVL getDueForVl;
	
	public ViralLoadController(GetDueForVL getDueForVl) {
		this.getDueForVl = getDueForVl;
	}
	
	/**
	 * Handles the HTTP GET request for retrieving the list of patients who are due for viral load
	 * testing.
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/dueForVl")
	// gets all visit forms for a patient
	@ResponseBody
	public Object getPatientsDueForVl(HttpServletRequest request, @RequestParam("startDate") String qStartDate,
	        @RequestParam("endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") SSEMRWebServicesController.filterCategory filterCategory,
	        @RequestParam(value = "page", required = false) Integer page,
	        @RequestParam(value = "size", required = false) Integer size) throws ParseException {
		
		SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
		Date[] dates = getStartAndEndDate(qStartDate, qEndDate, dateTimeFormatter);
		
		if (page == null)
			page = 0;
		if (size == null)
			size = 15;
		
		HashSet<Patient> dueForVlClients = getDueForVl.getDueForVl(dates[0], dates[1]);
		
		dueForVlClients = dueForVlClients.stream()
		        .filter(patient -> FilterUtility.applyFilter(patient, filterCategory, dates[1]))
		        .collect(Collectors.toCollection(HashSet::new));
		
		int totalPatients = dueForVlClients.size();
		
		List<Patient> dueForVlList = new ArrayList<>(dueForVlClients);
		
		return paginateAndGenerateSummary(dueForVlList, page, size, totalPatients, dates[0], dates[1], filterCategory);
	}
	
	/**
	 * Handles the HTTP GET request to retrieve patients with high viral load values within a specified
	 * date range. This method filters patients based on their viral load observations, identifying
	 * those with values above a predefined threshold.
	 * 
	 * @return A JSON representation of the list of patients with high viral load, including summary
	 *         information about each patient.
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/highVl")
	// gets all visit forms for a patient
	@ResponseBody
	public Object getPatientsOnHighVl(HttpServletRequest request, @RequestParam("startDate") String qStartDate,
	        @RequestParam("endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") SSEMRWebServicesController.filterCategory filterCategory,
	        @RequestParam(value = "page", required = false) Integer page,
	        @RequestParam(value = "size", required = false) Integer size) throws ParseException {
		
		SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
		Date[] dates = getStartAndEndDate(qStartDate, qEndDate, dateTimeFormatter);
		
		if (page == null)
			page = 0;
		if (size == null)
			size = 15;
		
		HashSet<Patient> highVLPatients = getPatientsWithHighVL(dates[0], dates[1]);
		
		highVLPatients = highVLPatients.stream()
		        .filter(patient -> FilterUtility.applyFilter(patient, filterCategory, dates[1]))
		        .collect(Collectors.toCollection(HashSet::new));
		
		int totalPatients = highVLPatients.size();
		
		List<Patient> highVlList = new ArrayList<>(highVLPatients);
		
		return fetchAndPaginatePatients(highVlList, page, size, totalPatients, dates[0], dates[1], filterCategory);
	}
	
	/**
	 * Retrieves patients with Viral Load Sample collections within a specified date range.
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/viralLoadSamplesCollected")
	@ResponseBody
	public String getViralLoadSamplesCollected(HttpServletRequest request,
	        @RequestParam(value = "startDate") String qStartDate, @RequestParam(value = "endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") SSEMRWebServicesController.filterCategory filterCategory) {
		try {
			SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
			Date[] dates = getStartAndEndDate(qStartDate, qEndDate, dateTimeFormatter);
			
			EncounterType viralLoadEncounterType = Context.getEncounterService()
			        .getEncounterTypeByUuid(FOLLOW_UP_FORM_ENCOUNTER_TYPE);
			EncounterSearchCriteria encounterSearchCriteria = new EncounterSearchCriteria(null, null, dates[0], dates[1],
			        null, null, Collections.singletonList(viralLoadEncounterType), null, null, null, false);
			List<Encounter> viralLoadSampleEncounters = Context.getEncounterService().getEncounters(encounterSearchCriteria);
			
			Concept sampleCollectionDateConcept = Context.getConceptService().getConceptByUuid(SAMPLE_COLLECTION_DATE_UUID);
			List<Obs> sampleCollectionDateObs = Context.getObsService().getObservations(null, viralLoadSampleEncounters,
			    Collections.singletonList(sampleCollectionDateConcept), null, null, null, null, null, null, dates[0],
			    dates[1], false);
			
			// Generate the summary data
			Object summaryData = generateDashboardSummaryFromObs(dates[0], dates[1], sampleCollectionDateObs,
			    filterCategory);
			
			// Convert the summary data to JSON format
			ObjectMapper objectMapper = new ObjectMapper();
			
			return objectMapper.writeValueAsString(summaryData);
			
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Retrieves patients with Viral Load results within a specified date range.
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/viralLoadResults")
	// gets all visit forms for a patient
	@ResponseBody
	public Object getViralLoadResults(HttpServletRequest request, @RequestParam(value = "startDate") String qStartDate,
	        @RequestParam(value = "endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") SSEMRWebServicesController.filterCategory filterCategory) {
		try {
			SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
			Date[] dates = getStartAndEndDate(qStartDate, qEndDate, dateTimeFormatter);
			
			EncounterType viralLoadEncounterType = Context.getEncounterService()
			        .getEncounterTypeByUuid(FOLLOW_UP_FORM_ENCOUNTER_TYPE);
			if (viralLoadEncounterType == null) {
				throw new RuntimeException("Encounter type not found: " + FOLLOW_UP_FORM_ENCOUNTER_TYPE);
			}
			
			EncounterSearchCriteria encounterSearchCriteria = new EncounterSearchCriteria(null, null, dates[0], dates[1],
			        null, null, Collections.singletonList(viralLoadEncounterType), null, null, null, false);
			List<Encounter> viralLoadSampleEncounters = Context.getEncounterService().getEncounters(encounterSearchCriteria);
			if (viralLoadSampleEncounters == null || viralLoadSampleEncounters.isEmpty()) {
				throw new RuntimeException("No encounters found for criteria");
			}
			
			Concept viralLoadResultConcept = Context.getConceptService().getConceptByUuid(VIRAL_LOAD_RESULTS_UUID);
			if (viralLoadResultConcept == null) {
				throw new RuntimeException("Concept not found: " + VIRAL_LOAD_RESULTS_UUID);
			}
			
			List<Obs> viralLoadResultObs = Context.getObsService().getObservations(null, viralLoadSampleEncounters,
			    Collections.singletonList(viralLoadResultConcept), null, null, null, null, null, null, dates[0], dates[1],
			    false);
			if (viralLoadResultObs == null || viralLoadResultObs.isEmpty()) {
				throw new RuntimeException("No observations found for the given criteria");
			}
			
			// Generate the summary data
			Map<String, Map<String, Integer>> summaryData = generateDashboardSummaryFromObs(dates[0], dates[1],
			    viralLoadResultObs, filterCategory);
			if (summaryData.isEmpty()) {
				throw new RuntimeException("Failed to generate summary data");
			}
			
			// Convert the summary data to JSON format
			ObjectMapper objectMapper = new ObjectMapper();
			
			return objectMapper.writeValueAsString(summaryData);
			
		}
		catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Error occurred while processing viral load results", e);
		}
	}
	
	/**
	 * Retrieves Clients with viral load coverage data
	 * 
	 * @return JSON representation of the list of patients with viral load coverage data
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/viralLoadCoverage")
	@ResponseBody
	public Object getViralLoadCoverage(HttpServletRequest request, @RequestParam("startDate") String qStartDate,
	        @RequestParam("endDate") String qEndDate) throws ParseException {
		
		SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
		Date[] dates = getStartAndEndDate(qStartDate, qEndDate, dateTimeFormatter);
		
		int totalPatients = Context.getPatientService().getAllPatients().size();
		
		List<Patient> patientsWithVLCoverage = fetchPatientsWithViralLoadCoverage(dates[0], dates[1]);
		int vlCoverage = patientsWithVLCoverage.size();
		
		int notVlCovered = totalPatients - vlCoverage;
		
		Map<String, Integer> response = new HashMap<>();
		response.put("totalPatients", totalPatients);
		response.put("vlCoverage", vlCoverage);
		response.put("notVlCovered", notVlCovered);
		
		return response;
	}
	
	/**
	 * This method handles the calculation for the viral Load coverage chart and retrieves the necessary
	 * data to be displayed
	 */
	
	@RequestMapping(method = RequestMethod.GET, value = "/chart/viralLoadCoverage")
	@ResponseBody
	public Object viralLoadCoverageChart(HttpServletRequest request, @RequestParam("startDate") String qStartDate,
	        @RequestParam("endDate") String qEndDate) throws ParseException {
		SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
		Date[] dates = getStartAndEndDate(qStartDate, qEndDate, dateTimeFormatter);
		
		int totalPatients = Context.getPatientService().getAllPatients().size();

		if (totalPatients == 0) {
			Map<String, Object> response = new HashMap<>();
			response.put("Total Patients", 0);
			response.put("covered", 0);
			response.put("notCovered", 0);
			return response;
		}
		
		List<Patient> patientsWithVLCoverage = fetchPatientsWithViralLoadCoverage(dates[0], dates[1]);

		if (patientsWithVLCoverage == null) {
			patientsWithVLCoverage = new ArrayList<>();
		};
		int vlCoverage = patientsWithVLCoverage.size();
		
		int notVlCovered = totalPatients - vlCoverage;
		
		int covered = Math.round(((float) vlCoverage / totalPatients) * 100);
		int notCovered = Math.round(((float) notVlCovered / totalPatients) * 100);
		
		Map<String, Object> response = new HashMap<>();
		response.put("Total Patients", totalPatients);
		response.put("covered", covered);
		response.put("notCovered", notCovered);
		
		return response;
	}
	
	private List<Patient> fetchPatientsWithViralLoadCoverage(Date startDate, Date endDate) {
		Set<Integer> patientIds = fetchViralLoadPatientIds(startDate, endDate);
		
		return Context.getPatientService().getAllPatients().stream().filter(patient -> {
			boolean found = patientIds.contains(patient.getPerson().getPersonId());
			if (!found) {
				System.out.println("❌ Patient Not Found in VL List: " + patient.getPatientId() + " | Person ID: "
				        + patient.getPerson().getPersonId());
			}
			return found;
		}).collect(Collectors.toList());
	}
	
	private Set<Integer> fetchViralLoadPatientIds(Date startDate, Date endDate) {
		Concept viralLoadResultsConcept = Context.getConceptService().getConceptByUuid(VIRAL_LOAD_RESULTS_UUID);
		
		List<Obs> vlResultsObs = Context.getObsService().getObservations(null, null,
		    Collections.singletonList(viralLoadResultsConcept), null, null, null, null, null, null, startDate, endDate,
		    false);
		
		if (vlResultsObs.isEmpty()) {
			return Collections.emptySet();
		}
		
		Set<Integer> patientIds = vlResultsObs.stream().map(obs -> {
			return obs.getPerson().getPersonId();
		}).collect(Collectors.toSet());
		
		return patientIds;
	}
	
	/**
	 * Handles the HTTP GET request to retrieve patients with Suppressed viral load values. This method
	 * filters patients based on their viral load observations, identifying those with values below a
	 * predefined threshold.
	 * 
	 * @return A JSON representation of the list of patients with Suppressed viral load
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/viralLoadSuppression")
	@ResponseBody
	public Object getViralLoadSuppression(HttpServletRequest request, @RequestParam("startDate") String qStartDate,
	        @RequestParam("endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") SSEMRWebServicesController.filterCategory filterCategory,
	        @RequestParam(value = "page", required = false) Integer page,
	        @RequestParam(value = "size", required = false) Integer size) throws ParseException {
		
		SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
		Date[] dates = getStartAndEndDate(qStartDate, qEndDate, dateTimeFormatter);
		
		int totalPatients = Context.getPatientService().getAllPatients().size();
		
		List<Patient> vlCoveredPatients = fetchPatientsWithViralLoadCoverage(dates[0], dates[1]);
		int vlCoverage = vlCoveredPatients.size();
		
		int vlSuppressed = countViralLoadSuppressedPatients(vlCoveredPatients, dates[0], dates[1]);
		
		Map<String, Integer> response = new HashMap<>();
		response.put("totalPatients", totalPatients);
		response.put("vlCoverage", vlCoverage);
		response.put("vlSuppressed", vlSuppressed);
		
		return response;
	}
	
	/**
	 * Get count of VL Suppressed Patients (BDL or VL < 1000)
	 */
	private int countViralLoadSuppressedPatients(List<Patient> vlCoveredPatients, Date startDate, Date endDate) {
		Concept vlResultConcept = Context.getConceptService().getConceptByUuid(VIRAL_LOAD_RESULTS_UUID);
		Concept vlNumericConcept = Context.getConceptService().getConceptByUuid(VIRAL_LOAD_CONCEPT_UUID);
		
		HashSet<Integer> suppressedPatients = new HashSet<>();
		
		for (Patient patient : vlCoveredPatients) {
			List<Obs> vlObservations = Context.getObsService().getObservations(
			    Collections.singletonList(patient.getPerson()), null, Arrays.asList(vlResultConcept, vlNumericConcept), null,
			    null, null, null, null, null, startDate, endDate, false);
			
			for (Obs obs : vlObservations) {
				// Check if VL is Below Detectable (BDL)
				if (obs.getConcept().equals(vlResultConcept)
				        && "Below Detectable (BDL)".equalsIgnoreCase(obs.getValueText())) {
					suppressedPatients.add(patient.getPatientId());
					break;
				}
				
				// Check if VL Numeric Value is < 1000
				if (obs.getConcept().equals(vlNumericConcept) && obs.getValueNumeric() != null
				        && obs.getValueNumeric() < 1000) {
					suppressedPatients.add(patient.getPatientId());
					break;
				}
			}
		}
		
		return suppressedPatients.size();
	}
	
	/**
	 * This method handles the viral load cascade endpoint for the ART dashboard. It retrieves the
	 * necessary data from the database and calculates the viral load cascade.
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/viralLoadCascade")
	@ResponseBody
	public Object viralLoadCascade(HttpServletRequest request, @RequestParam("startDate") String qStartDate,
	        @RequestParam("endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") SSEMRWebServicesController.filterCategory filterCategory)
	        throws ParseException {
		
		return getViralLoadCascade(qStartDate, qEndDate,
		    Arrays.asList(FIRST_EAC_SESSION, SECOND_EAC_SESSION, THIRD_EAC_SESSION, EXTENDED_EAC_CONCEPT_UUID,
		        REAPEAT_VL_COLLECTION, REPEAT_VL_RESULTS, HIGH_VL_ENCOUNTERTYPE_UUID, ACTIVE_REGIMEN_CONCEPT_UUID),
		    EAC_SESSION_CONCEPT_UUID);
	}
}
