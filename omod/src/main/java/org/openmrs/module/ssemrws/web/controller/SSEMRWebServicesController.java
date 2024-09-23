/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.ssemrws.web.controller;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.servlet.http.HttpServletRequest;
import java.math.BigInteger;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.ObjectNode;
import org.openmrs.*;
import org.openmrs.api.context.Context;
import org.openmrs.module.ssemrws.service.FacilityDashboardService;
import org.openmrs.module.ssemrws.web.constants.VlEligibilityResult;
import org.openmrs.module.ssemrws.web.dto.PatientObservations;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.parameter.EncounterSearchCriteria;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import static org.openmrs.module.ssemrws.constants.SharedConstants.*;
import static org.openmrs.module.ssemrws.web.constants.AllConcepts.*;
import static org.openmrs.module.ssemrws.web.constants.RegimenConcepts.*;
import static org.openmrs.module.ssemrws.web.constants.ViralLoadCascade.getViralLoadCascade;

/**
 * This class configured as controller using annotation and mapped with the URL of
 * 'module/${rootArtifactid}/${rootArtifactid}Link.form'.
 */
@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + "/ssemr")
public class SSEMRWebServicesController {
	
	protected final Log log = LogFactory.getLog(getClass());
	
	public static SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("dd-MM-yyyy");
	
	public enum filterCategory {
		CHILDREN_ADOLESCENTS,
		PREGNANT_BREASTFEEDING,
		IIT
	};
	
	@PersistenceContext
	private EntityManager entityManager;
	
	private Object fetchAndPaginatePatients(List<Patient> patientList, int page, int size, String totalKey, int totalCount,
	        Date startDate, Date endDate, SSEMRWebServicesController.filterCategory filterCategory,
	        boolean includeClinicalStatus) {
		
		if (page < 0 || size <= 0) {
			return "Invalid page or size value. Page must be >= 0 and size must be > 0.";
		}
		
		int fromIndex = page * size;
		if (fromIndex >= patientList.size()) {
			return "Page out of bounds. Please check the page number and size.";
		}
		int toIndex = Math.min((page + 1) * size, patientList.size());
		
		List<Patient> paginatedPatients = patientList.subList(fromIndex, toIndex);
		
		ObjectNode allPatientsObj = JsonNodeFactory.instance.objectNode();
		allPatientsObj.put(totalKey, totalCount);
		
		return generatePatientListObj(new HashSet<>(paginatedPatients), startDate, endDate, filterCategory,
		    includeClinicalStatus, allPatientsObj);
	}
	
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/allClients")
	@ResponseBody
	public Object getAllPatients(HttpServletRequest request, @RequestParam("startDate") String qStartDate,
	        @RequestParam("endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory,
	        @RequestParam(required = false, value = "page") Integer page,
	        @RequestParam(required = false, value = "size") Integer size) throws ParseException {
		
		DateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
		
		Date startDate = dateTimeFormatter.parse(qStartDate);
		Date endDate = dateTimeFormatter.parse(qEndDate);
		
		if (qStartDate != null && qEndDate != null) {
			Calendar calendar = Calendar.getInstance();
			calendar.setTime(endDate);
			calendar.add(Calendar.DAY_OF_MONTH, 1);
			endDate = calendar.getTime();
		}
		
		if (page == null)
			page = 0;
		if (size == null)
			size = 50;
		
		List<Patient> allPatients = Context.getPatientService().getAllPatients(false);
		if (allPatients == null || allPatients.isEmpty()) {
			return "No Clients found for the given date range.";
		}
		
		return fetchAndPaginatePatients(allPatients, page, size, "totalPatients", allPatients.size(), startDate, endDate,
		    filterCategory, false);
	}
	
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/activeClients")
	@ResponseBody
	public Object getActiveClientsEndpoint(HttpServletRequest request,
	        @RequestParam(required = false, value = "startDate") String qStartDate,
	        @RequestParam(required = false, value = "endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory,
	        @RequestParam(value = "page", required = false) Integer page,
	        @RequestParam(value = "size", required = false) Integer size) throws ParseException {
		
		SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
		Date[] dates = getStartAndEndDate(qStartDate, qEndDate, dateTimeFormatter);
		
		if (page == null)
			page = 0;
		if (size == null)
			size = 15;
		
		int totalPatients = countTxCurr(dates[0], dates[1]);
		
		HashSet<Patient> activeClients = getTxCurr(dates[0], dates[1]);
		if (activeClients.isEmpty()) {
			return "No Active Clients found for the given date range.";
		}
		
		List<Patient> patientList = new ArrayList<>(activeClients);
		
		// Use the reusable method
		return txCurrSummary(patientList, page, size, "totalPatients", totalPatients, dates[0], dates[1], filterCategory,
		    false);
	}
	
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/newClients")
	@ResponseBody
	public Object getNewPatients(HttpServletRequest request,
	        @RequestParam(required = false, value = "startDate") String qStartDate,
	        @RequestParam(required = false, value = "endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory,
	        @RequestParam(value = "page", required = false) Integer page,
	        @RequestParam(value = "size", required = false) Integer size) throws ParseException {
		
		SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
		Date[] dates = getStartAndEndDate(qStartDate, qEndDate, dateTimeFormatter);
		
		if (page == null)
			page = 0;
		if (size == null)
			size = 15;
		
		int totalPatients = countTxNew(dates[0], dates[1]);
		
		HashSet<Patient> enrolledPatients = getNewlyEnrolledPatients(dates[0], dates[1]);
		if (enrolledPatients.isEmpty()) {
			return "No Newly Enrolled clients found for the given date range.";
		}
		
		List<Patient> txNewList = new ArrayList<>(enrolledPatients);
		
		// Use the reusable method
		return paginateAndGenerateSummary(txNewList, page, size, "totalPatients", totalPatients, dates[0], dates[1],
		    filterCategory, false);
	}
	
	/**
	 * Handles the HTTP GET request to retrieve patients who have experienced an interruption in their
	 * treatment. This method filters encounters based on ART treatment interruption encounter types and
	 * aggregates patients who have had such encounters within the specified date range. It aims to
	 * identify patients who might need follow-up or intervention due to treatment interruption.
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/interruptedInTreatment")
	@ResponseBody
	public Object getPatientsInterruptedInTreatment(HttpServletRequest request, @RequestParam("startDate") String qStartDate,
	        @RequestParam("endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory,
	        @RequestParam(value = "page", required = false) Integer page,
	        @RequestParam(value = "size", required = false) Integer size) throws ParseException {
		
		SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
		Date[] dates = getStartAndEndDate(qStartDate, qEndDate, dateTimeFormatter);
		
		if (page == null)
			page = 0;
		if (size == null)
			size = 15;
		
		int totalPatients = countIit(dates[0], dates[1]);
		
		HashSet<Patient> interruptedInTreatmentPatients = getIit(dates[0], dates[1]);
		
		if (interruptedInTreatmentPatients.isEmpty()) {
			return "No IIT Clients found for the given date range.";
		}
		
		List<Patient> iitList = new ArrayList<>(interruptedInTreatmentPatients);
		
		return fetchAndPaginatePatients(iitList, page, size, "totalPatients", totalPatients, dates[0], dates[1],
		    filterCategory, false);
	}
	
	/**
	 * Returns a list of patients on appointment.
	 * 
	 * @return A JSON representation of the list of patients on appointment, including summary
	 *         information about each patient.
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/onAppointment")
	@ResponseBody
	public Object getPatientsOnAppointment(HttpServletRequest request, @RequestParam("startDate") String qStartDate,
	        @RequestParam("endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory,
	        @RequestParam(value = "page", required = false) Integer page,
	        @RequestParam(value = "size", required = false) Integer size) throws ParseException {
		
		SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
		Date startDate = dateTimeFormatter.parse(qStartDate);
		Date endDate = dateTimeFormatter.parse(qEndDate);
		
		if (page == null)
			page = 0;
		if (size == null)
			size = 15;
		
		int totalPatients = countOnAppoinment(startDate, endDate);
		
		HashSet<Patient> onAppointment = getOnAppoinment(startDate, endDate);
		
		if (onAppointment.isEmpty()) {
			return "No Clients on Appointment found for the given date range.";
		}
		
		List<Patient> onAppoinmentList = new ArrayList<>(onAppointment);
		
		return fetchAndPaginatePatients(onAppoinmentList, page, size, "totalPatients", totalPatients, startDate, endDate,
		    filterCategory, false);
	}
	
	/**
	 * Returns a list of patients who missed an appointment.
	 * 
	 * @return A JSON representation of the list of patients who missed an appointment, including
	 *         summary information about each patient.
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/missedAppointment")
	@ResponseBody
	public Object getPatientsMissedAppointment(HttpServletRequest request, @RequestParam("startDate") String qStartDate,
	        @RequestParam("endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory,
	        @RequestParam(value = "page", required = false) Integer page,
	        @RequestParam(value = "size", required = false) Integer size) throws ParseException {
		
		SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
		Date startDate = dateTimeFormatter.parse(qStartDate);
		Date endDate = dateTimeFormatter.parse(qEndDate);
		
		if (page == null)
			page = 0;
		if (size == null)
			size = 15;
		
		HashSet<Patient> missedAppointment = getMissedAppoinment(startDate, endDate);
		
		int totalPatients = missedAppointment.size();
		
		if (missedAppointment.isEmpty()) {
			return "No Missed Appointments found for the given date range.";
		}
		
		List<Patient> missedAppoinmentList = new ArrayList<>(missedAppointment);
		
		return fetchAndPaginatePatients(missedAppoinmentList, page, size, "totalPatients", totalPatients, startDate, endDate,
		    filterCategory, false);
	}
	
	/**
	 * Handles the HTTP GET request to retrieve patients who have returned to treatment after an
	 * interruption. This method filters encounters based on ART treatment interruption encounter types
	 * and aggregates patients who have returned to treatment within the specified date range.
	 * 
	 * @param request The HttpServletRequest object, providing request information for HTTP servlets.
	 * @return A JSON representation of the list of patients who have returned to treatment, including
	 *         summary information about each patient.
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/returnedToTreatment")
	@ResponseBody
	public Object getPatientsReturnedToTreatment(HttpServletRequest request, @RequestParam("startDate") String qStartDate,
	        @RequestParam("endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory,
	        @RequestParam(value = "page", required = false) Integer page,
	        @RequestParam(value = "size", required = false) Integer size) throws ParseException {
		
		SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
		Date[] dates = getStartAndEndDate(qStartDate, qEndDate, dateTimeFormatter);
		
		if (page == null)
			page = 0;
		if (size == null)
			size = 15;
		
		HashSet<Patient> rttPatients = getReturnToTreatmentPatients(dates[0], dates[1]);
		
		int totalPatients = rttPatients.size();
		
		if (rttPatients.isEmpty()) {
			return "No Returning to Treatment Clients found for the given date range.";
		}
		
		List<Patient> rttList = new ArrayList<>(rttPatients);
		
		return fetchAndPaginatePatients(rttList, page, size, "totalPatients", totalPatients, dates[0], dates[1],
		    filterCategory, false);
	}
	
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/dueForVl")
	// gets all visit forms for a patient
	@ResponseBody
	public Object getPatientsDueForVl(HttpServletRequest request, @RequestParam("startDate") String qStartDate,
	        @RequestParam("endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory,
	        @RequestParam(value = "page", required = false) Integer page,
	        @RequestParam(value = "size", required = false) Integer size) throws ParseException {
		
		SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
		Date[] dates = getStartAndEndDate(qStartDate, qEndDate, dateTimeFormatter);
		
		if (page == null)
			page = 0;
		if (size == null)
			size = 15;
		
		int totalPatients = countDueForVl(dates[0], dates[1]);
		
		HashSet<Patient> dueForVlClients = getDueForVl(dates[0], dates[1]);
		if (dueForVlClients.isEmpty()) {
			return "No Due For VL Clients found for the given date range.";
		}
		
		List<Patient> dueForVlList = new ArrayList<>(dueForVlClients);
		
		return paginateAndGenerateSummary(dueForVlList, page, size, "totalPatients", totalPatients, dates[0], dates[1],
		    filterCategory, false);
	}
	
	/**
	 * This method handles the HTTP GET request for retrieving the list of patients who have been
	 * transferred out.
	 * 
	 * @param request The HTTP request object.
	 * @param qStartDate The start date for the transferred out patients in the format "yyyy-MM-dd".
	 * @param qEndDate The end date for the transferred out patients in the format "yyyy-MM-dd".
	 * @param filterCategory The filter category for the transferred out patients.
	 * @return A JSON object containing the list of transferred out patients.
	 * @throws ParseException If the start or end date cannot be parsed.
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/transferredOut")
	@ResponseBody
	public Object getTransferredOutPatients(HttpServletRequest request, @RequestParam("startDate") String qStartDate,
	        @RequestParam("endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory,
	        @RequestParam(value = "page", required = false) Integer page,
	        @RequestParam(value = "size", required = false) Integer size) throws ParseException {
		
		SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
		Date[] dates = getStartAndEndDate(qStartDate, qEndDate, dateTimeFormatter);
		
		if (page == null)
			page = 0;
		if (size == null)
			size = 15;
		
		HashSet<Patient> transferredOutPatients = getTransferredOutClients(dates[0], dates[1]);
		// Filter out patients who have an upcoming appointment
		transferredOutPatients.removeIf(patient -> {
			String nextAppointmentDate = getNextAppointmentDateByUuid(patient.getUuid());
			return !nextAppointmentDate.equals("No Upcoming Appointments");
		});
		
		int totalPatients = transferredOutPatients.size();
		
		if (transferredOutPatients.isEmpty()) {
			return "No Transferred Out Clients found for the given date range.";
		}
		
		List<Patient> transferOutList = new ArrayList<>(transferredOutPatients);
		
		return fetchAndPaginatePatients(transferOutList, page, size, "totalPatients", totalPatients, dates[0], dates[1],
		    filterCategory, false);
	}
	
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/deceased")
	@ResponseBody
	public Object getDeceasedPatients(HttpServletRequest request, @RequestParam("startDate") String qStartDate,
	        @RequestParam("endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory,
	        @RequestParam(value = "page", required = false) Integer page,
	        @RequestParam(value = "size", required = false) Integer size) throws ParseException {
		
		SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
		Date[] dates = getStartAndEndDate(qStartDate, qEndDate, dateTimeFormatter);
		
		if (page == null)
			page = 0;
		if (size == null)
			size = 15;
		
		HashSet<Patient> deceasedPatients = getDeceasedPatientsByDateRange(dates[0], dates[1]);
		
		// Filter out patients who have an upcoming appointment
		deceasedPatients.removeIf(patient -> {
			String nextAppointmentDate = getNextAppointmentDateByUuid(patient.getUuid());
			return !nextAppointmentDate.equals("No Upcoming Appointments");
		});
		
		int totalPatients = deceasedPatients.size();
		
		if (deceasedPatients.isEmpty()) {
			return "No Died Clients found for the given date range.";
		}
		
		List<Patient> deceasedList = new ArrayList<>(deceasedPatients);
		
		return fetchAndPaginatePatients(deceasedList, page, size, "totalPatients", totalPatients, dates[0], dates[1],
		    filterCategory, false);
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
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory,
	        @RequestParam(value = "page", required = false) Integer page,
	        @RequestParam(value = "size", required = false) Integer size) throws ParseException {
		
		SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
		Date[] dates = getStartAndEndDate(qStartDate, qEndDate, dateTimeFormatter);
		
		if (page == null)
			page = 0;
		if (size == null)
			size = 15;
		
		HashSet<Patient> highVLPatients = getPatientsWithHighVL(dates[0], dates[1]);
		
		int totalPatients = highVLPatients.size();
		
		if (highVLPatients.isEmpty()) {
			return "No Clients with High VL found for the given date range.";
		}
		
		List<Patient> highVlList = new ArrayList<>(highVLPatients);
		
		return fetchAndPaginatePatients(highVlList, page, size, "totalPatients", totalPatients, dates[0], dates[1],
		    filterCategory, false);
	}
	
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/adultRegimenTreatment")
	@ResponseBody
	public Object getPatientsOnAdultRegimenTreatment(HttpServletRequest request,
	        @RequestParam("startDate") String qStartDate, @RequestParam("endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory) throws ParseException {
		
		return getPatientsOnRegimenTreatment(qStartDate, qEndDate,
		    Arrays.asList(regimen_1A, regimen_1B, regimen_1C, regimen_1D, regimen_1E, regimen_1F, regimen_1G, regimen_1H,
		        regimen_1J, regimen_2A, regimen_2B, regimen_2C, regimen_2D, regimen_2E, regimen_2F, regimen_2G, regimen_2H,
		        regimen_2I, regimen_2J, regimen_2K),
		    ACTIVE_REGIMEN_CONCEPT_UUID);
	}
	
	@Autowired
	FacilityDashboardService facilityDashboardService;
	
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/childRegimenTreatment")
	@ResponseBody
	public Object getPatientsOnChildRegimenTreatment(HttpServletRequest request,
	        @RequestParam("startDate") String qStartDate, @RequestParam("endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory) throws ParseException {
		
		return facilityDashboardService.getPatientsOnChildRegimenTreatment(qStartDate, qEndDate,
		    Arrays.asList(regimen_4A, regimen_4B, regimen_4C, regimen_4D, regimen_4E, regimen_4F, regimen_4G, regimen_4H,
		        regimen_4I, regimen_4J, regimen_4K, regimen_4L, regimen_5A, regimen_5B, regimen_5C, regimen_5D, regimen_5E,
		        regimen_5F, regimen_5G, regimen_5H, regimen_5I, regimen_5J),
		    ACTIVE_REGIMEN_CONCEPT_UUID);
	}
	
	/**
	 * Retrieves a list of patients under the care of community programs within a specified date range.
	 * 
	 * @return An Object representing the list of patients under the care of community programs within
	 *         the specified date range.
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/underCareOfCommunityProgrammes")
	@ResponseBody
	public Object getPatientsUnderCareOfCommunityProgrammes(HttpServletRequest request,
	        @RequestParam("startDate") String qStartDate, @RequestParam("endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory,
	        @RequestParam(value = "page", required = false) Integer page,
	        @RequestParam(value = "size", required = false) Integer size) throws ParseException {
		
		SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
		Date[] dates = getStartAndEndDate(qStartDate, qEndDate, dateTimeFormatter);
		
		if (page == null)
			page = 0;
		if (size == null)
			size = 15;
		
		EncounterType communityLinkageEncounterType = Context.getEncounterService()
		        .getEncounterTypeByUuid(COMMUNITY_LINKAGE_ENCOUNTER_UUID);
		EncounterSearchCriteria encounterSearchCriteria = new EncounterSearchCriteria(null, null, dates[0], dates[1], null,
		        null, Collections.singletonList(communityLinkageEncounterType), null, null, null, false);
		List<Encounter> encounters = Context.getEncounterService().getEncounters(encounterSearchCriteria);
		
		HashSet<Patient> underCareOfCommunityPatients = encounters.stream().map(Encounter::getPatient).collect(HashSet::new,
		    HashSet::add, HashSet::addAll);
		
		int totalPatients = underCareOfCommunityPatients.size();
		
		if (underCareOfCommunityPatients.isEmpty()) {
			return "No Clients Under Care of Community found for the given date range.";
		}
		
		List<Patient> underCareList = new ArrayList<>(underCareOfCommunityPatients);
		
		return paginateAndGenerateSummary(underCareList, page, size, "totalPatients", totalPatients, dates[0], dates[1],
		    filterCategory, false);
	}
	
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/viralLoadSamplesCollected")
	@ResponseBody
	public String getViralLoadSamplesCollected(HttpServletRequest request,
	        @RequestParam(value = "startDate") String qStartDate, @RequestParam(value = "endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory) {
		try {
			SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
			Date[] dates = getStartAndEndDate(qStartDate, qEndDate, dateTimeFormatter);
			
			EncounterType viralLoadEncounterType = Context.getEncounterService()
			        .getEncounterTypeByUuid(VL_LAB_REQUEST_ENCOUNTER_TYPE);
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
			String jsonResponse = objectMapper.writeValueAsString(summaryData);
			
			return jsonResponse;
			
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/viralLoadResults")
	// gets all visit forms for a patient
	@ResponseBody
	public Object getViralLoadResults(HttpServletRequest request, @RequestParam(value = "startDate") String qStartDate,
	        @RequestParam(value = "endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory) {
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
			if (summaryData == null || summaryData.isEmpty()) {
				throw new RuntimeException("Failed to generate summary data");
			}
			
			// Convert the summary data to JSON format
			ObjectMapper objectMapper = new ObjectMapper();
			String jsonResponse = objectMapper.writeValueAsString(summaryData);
			
			return jsonResponse;
			
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
	        @RequestParam("endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory,
	        @RequestParam(value = "page", required = false) Integer page,
	        @RequestParam(value = "size", required = false) Integer size) throws ParseException {
		
		SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
		Date[] dates = getStartAndEndDate(qStartDate, qEndDate, dateTimeFormatter);
		
		if (page == null)
			page = 0;
		if (size == null)
			size = 15;
		
		HashSet<Patient> viralLoadCoveragePatients = new HashSet<>();
		
		List<Obs> viralLoadObs = Context.getObsService().getObservations(null, null,
		    Collections.singletonList(Context.getConceptService().getConceptByUuid(VIRAL_LOAD_CONCEPT_UUID)), null, null,
		    null, null, null, null, dates[0], dates[1], false);
		
		if (viralLoadObs.isEmpty()) {
			return "No Clients with VL Coverage found for the given date range.";
		}
		
		for (Obs obs : viralLoadObs) {
			Patient patient = Context.getPatientService().getPatient(obs.getPersonId());
			if (patient != null) {
				viralLoadCoveragePatients.add(patient);
			}
		}
		
		int totalPatients = viralLoadCoveragePatients.size();
		
		if (viralLoadCoveragePatients.isEmpty()) {
			return "No Clients with VL Coverage found for the given date range";
		}
		
		List<Patient> underVLCoverageList = new ArrayList<>(viralLoadCoveragePatients);
		
		return paginateAndGenerateSummary(underVLCoverageList, page, size, "totalPatients", totalPatients, dates[0],
		    dates[1], filterCategory, false);
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
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory,
	        @RequestParam(value = "page", required = false) Integer page,
	        @RequestParam(value = "size", required = false) Integer size) throws ParseException {
		
		// Date format and range parsing
		SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
		Date[] dates = getStartAndEndDate(qStartDate, qEndDate, dateTimeFormatter);
		
		// Set default values for pagination
		if (page == null)
			page = 0;
		if (size == null)
			size = 15;
		
		HashSet<Patient> viralLoadSuppressedPatients = new HashSet<>();
		
		List<Obs> viralLoadSuppressedPatientsObs = Context.getObsService().getObservations(null, null,
		    Collections.singletonList(Context.getConceptService().getConceptByUuid(VIRAL_LOAD_CONCEPT_UUID)), null, null,
		    null, null, null, null, dates[0], dates[1], false);
		
		if (viralLoadSuppressedPatientsObs.isEmpty()) {
			return "No Clients with Suppressed VL found for the given date range.";
		}
		
		for (Obs obs : viralLoadSuppressedPatientsObs) {
			if (obs.getValueNumeric() != null && obs.getValueNumeric() < THRESHOLD) {
				Patient patient = Context.getPatientService().getPatient(obs.getPersonId());
				if (patient != null) {
					viralLoadSuppressedPatients.add(patient);
				}
			}
		}
		
		int totalPatients = viralLoadSuppressedPatients.size();
		
		if (viralLoadSuppressedPatients.isEmpty()) {
			return "No Clients with Suppressed VL found for the given date range.";
		}
		
		List<Patient> vlSuppressedList = new ArrayList<>(viralLoadSuppressedPatients);
		
		return paginateAndGenerateSummary(vlSuppressedList, page, size, "totalPatients", totalPatients, dates[0], dates[1],
		    filterCategory, false);
	}
	
	/**
	 * This method handles the viral load cascade endpoint for the ART dashboard. It retrieves the
	 * necessary data from the database and calculates the viral load cascade.
	 * 
	 * @param request The HTTP request object.
	 * @param qStartDate The start date for the viral load cascade in the format "yyyy-MM-dd".
	 * @param qEndDate The end date for the viral load cascade in the format "yyyy-MM-dd".
	 * @param filterCategory The filter category for the viral load cascade.
	 * @return A JSON object containing the results of the viral load cascade.
	 * @throws ParseException If the start or end date cannot be parsed.
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/viralLoadCascade")
	@ResponseBody
	public Object viralLoadCascade(HttpServletRequest request, @RequestParam("startDate") String qStartDate,
	        @RequestParam("endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory) throws ParseException {
		
		return getViralLoadCascade(qStartDate, qEndDate,
		    Arrays.asList(FIRST_EAC_SESSION, SECOND_EAC_SESSION, THIRD_EAC_SESSION, EXTENDED_EAC_CONCEPT_UUID,
		        REAPEAT_VL_COLLECTION, REPEAT_VL_RESULTS, HIGH_VL_ENCOUNTERTYPE_UUID, ACTIVE_REGIMEN_CONCEPT_UUID),
		    EAC_SESSION_CONCEPT_UUID);
	}
	
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/waterfallAnalysis")
	@ResponseBody
	public Object getWaterfallAnalysis(HttpServletRequest request, @RequestParam("startDate") String qStartDate,
	        @RequestParam("endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory) throws ParseException {
		
		return getWaterfallAnalysisChart(qStartDate, qEndDate);
	}
	
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/obs")
	@ResponseBody
	public ResponseEntity<Object> getPatientObs(HttpServletRequest request, @RequestParam("patientUuid") String patientUuid,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory) throws ParseException {
		
		if (StringUtils.isBlank(patientUuid)) {
			return buildErrorResponse("You must specify patientUuid in the request!", HttpStatus.BAD_REQUEST);
		}
		
		Patient patient = Context.getPatientService().getPatientByUuid(patientUuid);
		
		if (patient == null) {
			return buildErrorResponse("The provided patient was not found in the system!", HttpStatus.NOT_FOUND);
		}
		
		PatientObservations observations = getPatientObservations(patient);
		List<Map<String, String>> identifiersList = getIdentifiersList(patient);
		
		String formattedBirthDate = formatBirthdate(patient.getBirthdate());
		long age = calculateAge(patient.getBirthdate());
		
		Map<String, Object> responseMap = buildResponseMap(patient, age, formattedBirthDate, identifiersList, observations);
		
		return new ResponseEntity<>(responseMap, new HttpHeaders(), HttpStatus.OK);
	}
	
	/**
	 * Generates a summary of patient data within a specified date range, grouped by year, month, and
	 * week.
	 * 
	 * @param allPatients A set of all patients to be considered for the summary.
	 * @param startDate The start date of the range for which to generate the summary.
	 * @param endDate The end date of the range for which to generate the summary.
	 * @param filterCategory The category to filter patients.
	 * @param includeClinicalStatus A flag to include clinical status.
	 * @param allPatientsObj The object to store all patient details.
	 * @return A JSON string representing the summary of patient data.
	 */
	public Object generatePatientListObj(HashSet<Patient> allPatients, Date startDate, Date endDate,
	        filterCategory filterCategory, Boolean includeClinicalStatus, ObjectNode allPatientsObj) {
		ArrayNode patientList = JsonNodeFactory.instance.arrayNode();
		
		List<Date> patientDates = new ArrayList<>();
		Calendar startCal = Calendar.getInstance();
		startCal.setTime(startDate);
		Calendar endCal = Calendar.getInstance();
		endCal.setTime(endDate);
		
		for (Patient patient : allPatients) {
			ObjectNode patientObj = generatePatientObject(startDate, endDate, filterCategory, patient,
			    includeClinicalStatus);
			if (patientObj != null) {
				patientList.add(patientObj);
				
				Calendar patientCal = Calendar.getInstance();
				patientCal.setTime(patient.getDateCreated());
				
				if (!patientCal.before(startCal) && !patientCal.after(endCal)) {
					if (patient.getDateCreated() != null && !patientCal.before(startCal) && !patientCal.after(endCal)) {
						patientDates.add(patient.getDateCreated());
					}
				} else {
					System.out.println("Patient date out of range");
				}
			}
		}
		
		Map<String, Map<String, Integer>> summary = generateSummary(patientDates);
		
		ObjectNode groupingObj = JsonNodeFactory.instance.objectNode();
		ObjectNode groupYear = JsonNodeFactory.instance.objectNode();
		ObjectNode groupMonth = JsonNodeFactory.instance.objectNode();
		ObjectNode groupWeek = JsonNodeFactory.instance.objectNode();
		
		summary.get("groupYear").forEach(groupYear::put);
		summary.get("groupMonth").forEach(groupMonth::put);
		summary.get("groupWeek").forEach(groupWeek::put);
		
		groupingObj.put("groupYear", groupYear);
		groupingObj.put("groupMonth", groupMonth);
		groupingObj.put("groupWeek", groupWeek);
		
		allPatientsObj.put("pageSize", allPatients.size());
		allPatientsObj.put("results", patientList);
		allPatientsObj.put("summary", groupingObj);
		
		return allPatientsObj.toString();
	}
	
	private Map<String, Map<String, Integer>> generateCumulativeSummary(List<Date> dates) {
		String[] months = { "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec" };
		String[] days = { "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun" };
		
		Map<String, Integer> monthlySummary = new LinkedHashMap<>();
		Map<String, Integer> weeklySummary = new LinkedHashMap<>();
		Map<String, Integer> dailySummary = new LinkedHashMap<>();
		
		// Track cumulative totals
		int cumulativeMonthTotal = 0;
		Map<String, Integer> cumulativeWeekTotals = new LinkedHashMap<>();
		Map<String, Integer> cumulativeDayTotals = new LinkedHashMap<>();
		
		// Iterate through all dates and build cumulative summary
		for (Date date : dates) {
			Calendar calendar = Calendar.getInstance();
			calendar.setTime(date);
			
			// Get month and week of the month
			String month = months[calendar.get(Calendar.MONTH)];
			int week = calendar.get(Calendar.WEEK_OF_MONTH);
			String weekOfTheMonth = String.format("%s_Week%d", month, week);
			
			// Get day of the week
			int day = calendar.get(Calendar.DAY_OF_WEEK);
			String dayInWeek = String.format("%s_%s", month, days[day - 1]);
			
			// Accumulate monthly values
			cumulativeMonthTotal += 1;
			monthlySummary.put(month, cumulativeMonthTotal);
			
			// Accumulate weekly values
			cumulativeWeekTotals.put(weekOfTheMonth, cumulativeWeekTotals.getOrDefault(weekOfTheMonth, 0) + 1);
			weeklySummary.put(weekOfTheMonth, cumulativeWeekTotals.values().stream().reduce(0, Integer::sum));
			
			// Accumulate daily values
			cumulativeDayTotals.put(dayInWeek, cumulativeDayTotals.getOrDefault(dayInWeek, 0) + 1);
			dailySummary.put(dayInWeek, cumulativeDayTotals.values().stream().reduce(0, Integer::sum));
		}
		
		// Sorting the summaries based on predefined orders
		Map<String, Integer> sortedMonthlySummary = monthlySummary.entrySet().stream()
		        .sorted(Comparator.comparingInt(e -> Arrays.asList(months).indexOf(e.getKey())))
		        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
		
		Map<String, Integer> sortedWeeklySummary = weeklySummary.entrySet().stream().sorted((e1, e2) -> {
			String[] parts1 = e1.getKey().split("_Week");
			String[] parts2 = e2.getKey().split("_Week");
			int monthCompare = Arrays.asList(months).indexOf(parts1[0]) - Arrays.asList(months).indexOf(parts2[0]);
			if (monthCompare != 0) {
				return monthCompare;
			} else {
				return Integer.parseInt(parts1[1]) - Integer.parseInt(parts2[1]);
			}
		}).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
		
		Map<String, Integer> sortedDailySummary = dailySummary.entrySet().stream().sorted((e1, e2) -> {
			String[] parts1 = e1.getKey().split("_");
			String[] parts2 = e2.getKey().split("_");
			int monthCompare = Arrays.asList(months).indexOf(parts1[0]) - Arrays.asList(months).indexOf(parts2[0]);
			if (monthCompare != 0) {
				return monthCompare;
			} else {
				return Arrays.asList(days).indexOf(parts1[1]) - Arrays.asList(days).indexOf(parts2[1]);
			}
		}).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
		
		// Return cumulative summaries for year, month, and week
		Map<String, Map<String, Integer>> summary = new HashMap<>();
		summary.put("groupYear", sortedMonthlySummary);
		summary.put("groupMonth", sortedWeeklySummary);
		summary.put("groupWeek", sortedDailySummary);
		
		return summary;
	}
	
	private Object generateSummaryResponse(List<Patient> patientList, int page, int size, String totalKey, int totalCount,
	        Date startDate, Date endDate, filterCategory filterCategory, boolean includeClinicalStatus,
	        Function<List<Date>, Map<String, Map<String, Integer>>> summaryGenerator) {
		// Step 1: Calculate the summary based on the full patient list
		List<Date> patientDates = patientList.stream().map(Patient::getDateCreated).collect(Collectors.toList());
		
		Map<String, Map<String, Integer>> summary = summaryGenerator.apply(patientDates);
		
		// Step 2: Paginate the patient list
		Object paginatedResponse = fetchAndPaginatePatients(patientList, page, size, totalKey, totalCount, startDate,
		    endDate, filterCategory, includeClinicalStatus);
		
		// Convert to ObjectNode if it's a String (which could be a JSON string)
		ObjectNode responseObj;
		if (paginatedResponse instanceof String) {
			ObjectMapper objectMapper = new ObjectMapper();
			try {
				responseObj = (ObjectNode) objectMapper.readTree((String) paginatedResponse);
			}
			catch (Exception e) {
				throw new RuntimeException("Failed to parse paginated response as JSON", e);
			}
		} else {
			responseObj = (ObjectNode) paginatedResponse;
		}
		
		// Step 3: Add the summary to the paginated response
		ObjectNode groupingObj = JsonNodeFactory.instance.objectNode();
		ObjectNode groupYear = JsonNodeFactory.instance.objectNode();
		ObjectNode groupMonth = JsonNodeFactory.instance.objectNode();
		ObjectNode groupWeek = JsonNodeFactory.instance.objectNode();
		
		// Populate the summary into the response
		summary.get("groupYear").forEach(groupYear::put);
		summary.get("groupMonth").forEach(groupMonth::put);
		summary.get("groupWeek").forEach(groupWeek::put);
		
		groupingObj.put("groupYear", groupYear);
		groupingObj.put("groupMonth", groupMonth);
		groupingObj.put("groupWeek", groupWeek);
		
		responseObj.put("summary", groupingObj);
		
		return responseObj.toString();
	}
	
	private Object txCurrSummary(List<Patient> patientList, int page, int size, String totalKey, int totalCount,
	        Date startDate, Date endDate, filterCategory filterCategory, boolean includeClinicalStatus) {
		return generateSummaryResponse(patientList, page, size, totalKey, totalCount, startDate, endDate, filterCategory,
		    includeClinicalStatus, this::generateCumulativeSummary);
	}
	
	private Object paginateAndGenerateSummary(List<Patient> patientList, int page, int size, String totalKey, int totalCount,
	        Date startDate, Date endDate, filterCategory filterCategory, boolean includeClinicalStatus) {
		return generateSummaryResponse(patientList, page, size, totalKey, totalCount, startDate, endDate, filterCategory,
		    includeClinicalStatus, this::generateSummary);
	}
	
	private Map<String, Map<String, Integer>> generateSummary(List<Date> dates) {
		String[] months = new String[] { "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov",
		        "Dec" };
		String[] days = new String[] { "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun" };
		
		Map<String, Integer> monthlySummary = new HashMap<>();
		Map<String, Integer> weeklySummary = new HashMap<>();
		Map<String, Integer> dailySummary = new HashMap<>();
		
		for (Date date : dates) {
			Calendar calendar = Calendar.getInstance();
			calendar.setTime(date);
			
			String month = months[calendar.get(Calendar.MONTH)];
			monthlySummary.put(month, monthlySummary.getOrDefault(month, 0) + 1);
			
			int week = calendar.get(Calendar.WEEK_OF_MONTH);
			String weekOfTheMonth = String.format("%s_Week%s", month, week);
			weeklySummary.put(weekOfTheMonth, weeklySummary.getOrDefault(weekOfTheMonth, 0) + 1);
			
			int day = calendar.get(Calendar.DAY_OF_WEEK);
			String dayInWeek = String.format("%s_%s", month, days[day - 1]);
			dailySummary.put(dayInWeek, dailySummary.getOrDefault(dayInWeek, 0) + 1);
		}
		
		// Sorting the summaries
		Map<String, Integer> sortedMonthlySummary = monthlySummary.entrySet().stream()
		        .sorted(Comparator.comparingInt(e -> Arrays.asList(months).indexOf(e.getKey())))
		        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
		
		Map<String, Integer> sortedWeeklySummary = weeklySummary.entrySet().stream().sorted((e1, e2) -> {
			String[] parts1 = e1.getKey().split("_Week");
			String[] parts2 = e2.getKey().split("_Week");
			int monthCompare = Arrays.asList(months).indexOf(parts1[0]) - Arrays.asList(months).indexOf(parts2[0]);
			if (monthCompare != 0) {
				return monthCompare;
			} else {
				return Integer.parseInt(parts1[1]) - Integer.parseInt(parts2[1]);
			}
		}).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
		
		Map<String, Integer> sortedDailySummary = dailySummary.entrySet().stream().sorted((e1, e2) -> {
			String[] parts1 = e1.getKey().split("_");
			String[] parts2 = e2.getKey().split("_");
			int monthCompare = Arrays.asList(months).indexOf(parts1[0]) - Arrays.asList(months).indexOf(parts2[0]);
			if (monthCompare != 0) {
				return monthCompare;
			} else {
				return Arrays.asList(days).indexOf(parts1[1]) - Arrays.asList(days).indexOf(parts2[1]);
			}
		}).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
		
		Map<String, Map<String, Integer>> summary = new HashMap<>();
		summary.put("groupYear", sortedMonthlySummary);
		summary.put("groupMonth", sortedWeeklySummary);
		summary.put("groupWeek", sortedDailySummary);
		
		return summary;
	}
	
	private ObjectNode generatePatientObject(Date startDate, Date endDate, filterCategory filterCategory, Patient patient,
	        boolean includeClinicalStatus) {
		ObjectNode patientObj = JsonNodeFactory.instance.objectNode();
		String artRegimen = getARTRegimen(patient);
		String dateEnrolled = getEnrolmentDate(patient);
		String artInitiationDate = getEnrolmentDate(patient);
		String lastRefillDate = getLastRefillDate(patient);
		String artAppointmentDate = getNextArtAppointmentDate(patient);
		String contact = patient.getAttribute("Client Telephone Number") != null
		        ? String.valueOf(patient.getAttribute("Client Telephone Number"))
		        : "";
		String alternateContact = patient.getAttribute("AltTelephoneNo") != null
		        ? String.valueOf(patient.getAttribute("AltTelephoneNo"))
		        : "";
		
		// Calculate age in years based on patient's birthdate and current date
		Date birthdate = patient.getBirthdate();
		Date currentDate = new Date();
		long age = (currentDate.getTime() - birthdate.getTime()) / (1000L * 60 * 60 * 24 * 365);
		
		ArrayNode identifiersArray = getPatientIdentifiersArray(patient);
		
		String fullAddress = getPatientFullAddress(patient);
		
		// Populate common fields
		patientObj.put("name", patient.getPersonName() != null ? patient.getPersonName().toString() : "");
		patientObj.put("uuid", patient.getUuid());
		patientObj.put("sex", patient.getGender());
		patientObj.put("age", age);
		patientObj.put("identifiers", identifiersArray);
		patientObj.put("address", fullAddress);
		patientObj.put("contact", contact);
		patientObj.put("alternateContact", alternateContact);
		patientObj.put("childOrAdolescent", age <= 19);
		patientObj.put("ARTRegimen", artRegimen);
		patientObj.put("initiationDate", artInitiationDate);
		patientObj.put("dateEnrolled", dateEnrolled);
		patientObj.put("lastRefillDate", lastRefillDate);
		patientObj.put("appointmentDate", artAppointmentDate);
		
		// Add clinical status if needed
		if (includeClinicalStatus) {
			ClinicalStatus clinicalStatus = determineClinicalStatus(patient, startDate, endDate);
			patientObj.put("clinicalStatus", clinicalStatus.toString());
		}
		
		// Check filter category and return only the matching patients
		if (filterCategory != null) {
			switch (filterCategory) {
				case CHILDREN_ADOLESCENTS:
					if (age <= 19) {
						return patientObj;
					}
					break;
				case PREGNANT_BREASTFEEDING:
					if (determineIfPatientIsPregnantOrBreastfeeding(patient, endDate)) {
						return patientObj;
					}
					break;
				case IIT:
					if (determineIfPatientIsIIT(startDate, endDate)) {
						return patientObj;
					}
					break;
				default:
					return null;
			}
		} else {
			return patientObj;
		}
		return null;
	}
	
	private ClinicalStatus determineClinicalStatus(Patient patient, Date startDate, Date endDate) {
		HashSet<Patient> deceasedPatients = getDeceasedPatientsByDateRange(startDate, endDate);
		if (deceasedPatients.contains(patient)) {
			return ClinicalStatus.DIED;
		}
		
		HashSet<Patient> activeClients = getTxCurr(startDate, endDate);
		if (activeClients.contains(patient)) {
			return ClinicalStatus.ACTIVE;
		}
		
		HashSet<Patient> transferredOutPatients = getTransferredOutClients(startDate, endDate);
		if (transferredOutPatients.contains(patient)) {
			return ClinicalStatus.TRANSFERRED_OUT;
		}
		
		HashSet<Patient> interruptedInTreatment = getIit(startDate, endDate);
		if (interruptedInTreatment.contains(patient)) {
			return ClinicalStatus.INTERRUPTED_IN_TREATMENT;
		}
		
		return ClinicalStatus.ACTIVE;
	}
	
	private PatientObservations getPatientObservations(Patient patient) {
		PatientObservations observations = new PatientObservations();
		
		observations.setEnrollmentDate(getEnrolmentDate(patient));
		observations.setDateOfinitiation(getEnrolmentDate(patient));
		observations.setLastRefillDate(getLastRefillDate(patient));
		observations.setArvRegimen(getARTRegimen(patient));
		observations.setLastCD4Count(getLastCD4Count(patient));
		observations.setTbStatus(getTbStatus(patient));
		observations.setArvRegimenDose(getARVRegimenDose(patient));
		observations.setWhoClinicalStage(getWHOClinicalStage(patient));
		observations.setDateVLResultsReceived(getDateVLResultsReceived(patient));
		observations.setChwName(getCHWName(patient));
		observations.setChwPhone(getCHWPhone(patient));
		observations.setChwAddress(getCHWAddress(patient));
		observations.setVlResults(getVLResults(patient));
		observations.setVlStatus(getVLStatus(patient));
		observations.setBmi(getBMI(patient));
		observations.setMuac(getMUAC(patient));
		observations.setAppointmentDate(getNextAppointmentDate(patient.getUuid()));
		observations.setClinicianName(getClinicianName(patient));
		
		VlEligibilityResult eligibilityResult = isPatientDueForVl(patient.getUuid());
		
		if (eligibilityResult.isEligible()) {
			observations.setVlEligibility("Eligible");
			
			if (eligibilityResult.getVlDueDate() != null) {
				SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy");
				String formattedDate = dateFormat.format(eligibilityResult.getVlDueDate());
				observations.setVlDueDate(formattedDate);
			}
		} else {
			observations.setVlEligibility("Not Eligible");
			observations.setVlDueDate(null);
		}
		
		return observations;
	}
	
	private VlEligibilityResult isPatientDueForVl(String patientUuid) {
		String baseQuery = "select count(distinct fp.client_id), fp.date_vl_sample_collected from ssemr_etl.ssemr_flat_encounter_hiv_care_follow_up fp "
		        + "join ssemr_etl.mamba_dim_person mp on fp.client_id = mp.person_id "
		        + "left join ssemr_etl.ssemr_flat_encounter_hiv_care_enrolment en ON en.client_id = fp.client_id "
		        + "left join ssemr_etl.ssemr_flat_encounter_personal_family_tx_history pfh on pfh.client_id = fp.client_id "
		        + "left join ssemr_etl.ssemr_flat_encounter_vl_laboratory_request vlr on vlr.client_id = fp.client_id "
		        + "left join ssemr_etl.ssemr_flat_encounter_high_viral_load hvl on hvl.client_id = fp.client_id "
		        + "where mp.uuid = :patientUuid and ("
				// Criteria 1: Adults, ART > 6 months, VL suppressed (<1000)
		        + "   (mp.age > 18 and pfh.art_start_date is not null and TIMESTAMPDIFF(MONTH, pfh.art_start_date, NOW()) >= 6 "
		        + "    and fp.viral_load_value < 1000 and (TIMESTAMPDIFF(MONTH, fp.date_vl_sample_collected, NOW()) >= 6 "
		        + "    or TIMESTAMPDIFF(MONTH, fp.date_vl_sample_collected, NOW()) >= 12)) "
				// Criteria 2: Child/Adolescent up to 18 years old
		        + "or (mp.age <= 18 and TIMESTAMPDIFF(MONTH, fp.date_vl_sample_collected, NOW()) >= 6) "
				// Criteria 3: Pregnant women newly enrolled on ART
		        + "or (fp.client_pregnant = 'Yes' and pfh.art_start_date is not null and TIMESTAMPDIFF(MONTH, pfh.art_start_date, NOW()) < 6 "
		        + "    and TIMESTAMPDIFF(MONTH, vlr.date_of_sample_collection, NOW()) >= 3) "
				// Criteria 4: Pregnant woman already on ART
		        + "or (fp.client_pregnant = 'Yes' and TIMESTAMPDIFF(MONTH, pfh.art_start_date, NOW()) >= 6) "
				// Criteria 5: Post EAC 3
		        + "or (hvl.eac_session = 'Third EAC Session' and TIMESTAMPDIFF(MONTH, hvl.adherence_date, NOW()) >= 1) "
		        + ")";
				
		try {
			Query query = entityManager.createNativeQuery(baseQuery).setParameter("patientUuid", patientUuid);
			
			// Fetch the result and date
			Object[] result = (Object[]) query.getSingleResult();
			BigInteger resultCount = (BigInteger) result[0];
			Date lastVlSampleDate = (Date) result[1];
			
			// Check if patient is eligible
			boolean isEligible = resultCount.intValue() > 0;
			
			// Dynamically calculate the next VL due date if eligible
			Date vlDueDate = null;
			if (isEligible && lastVlSampleDate != null) {
				Calendar calendar = Calendar.getInstance();
				calendar.setTime(lastVlSampleDate);
				
				// Add 6 months for adults or based on other criteria
				calendar.add(Calendar.MONTH, 6);
				vlDueDate = calendar.getTime();
			}
			
			return new VlEligibilityResult(isEligible, vlDueDate);
		}
		catch (NoResultException e) {
			System.err.println("No data found for the query: " + e.getMessage());
			return new VlEligibilityResult(false, null);
		}
		catch (Exception e) {
			System.err.println("Error executing VL eligibility query: " + e.getMessage());
			throw new RuntimeException("Failed to check VL eligibility", e);
		}
	}
	
	private String getNextArtAppointmentDate(Patient patient) {
		return getNextAppointmentDateByUuid(patient.getUuid());
	}
	
	private String getNextAppointmentDate(String patientUuid) {
		return getNextAppointmentDateByUuid(patientUuid);
	}
	
	// Private method to reduce repetition
	private String getNextAppointmentDateByUuid(String patientUuid) {
		if (patientUuid == null || patientUuid.trim().isEmpty()) {
			return "Invalid patient UUID";
		}
		
		if (entityManager == null) {
			throw new IllegalStateException("EntityManager is not initialized!");
		}
		
		Date now = new Date();
		
		String query = "select fp.start_date_time " + "from openmrs.patient_appointment fp "
		        + "join openmrs.person p on fp.patient_id = p.person_id " + "where p.uuid = :patientUuid "
		        + "and fp.start_date_time >= :now " + "order by fp.start_date_time asc";
		
		List<Date> results = entityManager.createNativeQuery(query).setParameter("patientUuid", patientUuid)
		        .setParameter("now", now).getResultList();
		
		if (results != null && !results.isEmpty()) {
			return dateTimeFormatter.format(results.get(0));
		} else {
			return "No Upcoming Appointments";
		}
	}
	
	// Method to fetch the list of IIT patients
	private HashSet<Patient> getIit(Date startDate, Date endDate) {
		List<Integer> iitIds = (List<Integer>) executePatientQuery(startDate, endDate, false, "Missed", true);
		HashSet<Patient> patients = fetchPatientsByIds(iitIds);
		
		// Filter out patients with upcoming appointments
		patients.removeIf(patient -> {
			String nextAppointmentDate = getNextAppointmentDateByUuid(patient.getUuid());
			return !nextAppointmentDate.equals("No Upcoming Appointments");
		});
		
		return patients;
	}
	
	// Method to count IIT patients
	private int countIit(Date startDate, Date endDate) {
		List<Integer> iitIds = (List<Integer>) executePatientQuery(startDate, endDate, false, "Missed", true);
		HashSet<Patient> patients = fetchPatientsByIds(iitIds);
		
		// Filter out patients with upcoming appointments
		patients.removeIf(patient -> {
			String nextAppointmentDate = getNextAppointmentDateByUuid(patient.getUuid());
			return !nextAppointmentDate.equals("No Upcoming Appointments");
		});
		
		return patients.size();
	}
	
	// Method to fetch the list of IIT patients
	private HashSet<Patient> getOnAppoinment(Date startDate, Date endDate) {
		List<Integer> iitIds = (List<Integer>) executePatientQuery(startDate, endDate, false, "Scheduled", false);
		return fetchPatientsByIds(iitIds);
	}
	
	// Method to count On Appoinment patients
	private int countOnAppoinment(Date startDate, Date endDate) {
		return (int) executePatientQuery(startDate, endDate, true, "Scheduled", false);
	}
	
	// Method to fetch the list of IIT patients
	private HashSet<Patient> getMissedAppoinment(Date startDate, Date endDate) {
		// Execute the query to fetch the list of missed appointment patient IDs
		List<Integer> missedAppoinmentIds = (List<Integer>) executePatientQuery(startDate, endDate, false, "Missed", false);
		
		// Fetch patients by their IDs
		HashSet<Patient> patients = fetchPatientsByIds(missedAppoinmentIds);
		
		// Filter out patients with upcoming appointments
		patients.removeIf(patient -> {
			String nextAppointmentDate = getNextAppointmentDateByUuid(patient.getUuid());
			return !nextAppointmentDate.equals("No Upcoming Appointments");
		});
		
		return patients;
	}
	
	// Determine if patient is Interrupted In Treatment
	private boolean determineIfPatientIsIIT(Date startDate, Date endDate) {
		return !getIit(startDate, endDate).isEmpty();
	}
	
	// Method to fetch the list of TxCurr patients
	private HashSet<Patient> getTxCurr(Date startDate, Date endDate) {
		List<Integer> patientIds = (List<Integer>) executeTxCurrQuery(startDate, endDate, false);
		return fetchPatientsByIds(patientIds);
	}
	
	// Method to count TxCurr patients
	private int countTxCurr(Date startDate, Date endDate) {
		return (int) executeTxCurrQuery(startDate, endDate, true);
	}
	
	// Method to fetch the list of TxCurr patients
	private HashSet<Patient> getDueForVl(Date startDate, Date endDate) {
		List<Integer> patientIds = (List<Integer>) executeDueForVlQuery(startDate, endDate, false);
		return fetchPatientsByIds(patientIds);
	}
	
	// Method to count TxCurr patients
	private int countDueForVl(Date startDate, Date endDate) {
		return (int) executeDueForVlQuery(startDate, endDate, true);
	}
	
	private HashSet<Patient> getFilteredEnrolledPatients(Date startDate, Date endDate) {
		List<Obs> enrollmentObs = getObservationsByDateRange(null,
		    Collections.singletonList(Context.getConceptService().getConceptByUuid(DATE_OF_ENROLLMENT_UUID)), startDate,
		    endDate);
		HashSet<Patient> enrolledClients = extractPatientsFromObservations(enrollmentObs);
		
		// Combine all the patients
		HashSet<Patient> enrolledPatients = new HashSet<>(enrolledClients);
		
		// Filter out transferred-in, deceased, and transferred-out patients
		HashSet<Patient> transferredInPatients = getTransferredInPatients(startDate, endDate);
		HashSet<Patient> deceasedPatients = getDeceasedPatientsByDateRange(startDate, endDate);
		HashSet<Patient> transferredOutPatients = getTransferredOutClients(startDate, endDate);
		HashSet<Patient> iitPatients = getIit(startDate, endDate);
		
		enrolledPatients.removeAll(transferredInPatients);
		enrolledPatients.removeAll(transferredOutPatients);
		enrolledPatients.removeAll(deceasedPatients);
		enrolledPatients.removeAll(iitPatients);
		
		return enrolledPatients;
	}
	
	// Method to get newly enrolled patients
	private HashSet<Patient> getNewlyEnrolledPatients(Date startDate, Date endDate) {
		return getFilteredEnrolledPatients(startDate, endDate);
	}
	
	// Method to calculate total TxNew patients
	private int countTxNew(Date startDate, Date endDate) {
		return getFilteredEnrolledPatients(startDate, endDate).size();
	}
	
	private Object executeTxCurrQuery(Date startDate, Date endDate, boolean isCountQuery) {
		String selectClause = isCountQuery ? "count(distinct fp.patient_id)" : "distinct fp.patient_id";
		String baseQuery = "select " + selectClause + " from openmrs.patient_appointment fp "
		        + "join openmrs.person p on fp.patient_id = p.person_id " + "where (fp.start_date_time >= :now "
		        + "or (fp.start_date_time between :startDate and :endDate "
		        + "and date(fp.start_date_time) >= current_date() - interval 28 day))";
		
		try {
			Query query = entityManager.createNativeQuery(baseQuery).setParameter("now", new Date())
			        .setParameter("startDate", startDate).setParameter("endDate", endDate);
			
			if (isCountQuery) {
				BigInteger totalTxCurr = (BigInteger) query.getSingleResult();
				return totalTxCurr.intValue();
			} else {
				return query.getResultList();
			}
		}
		catch (Exception e) {
			System.err.println("Error executing TxCurr query: " + e.getMessage());
			throw new RuntimeException("Failed to execute TxCurr query", e);
		}
	}
	
	private Object executePatientQuery(Date startDate, Date endDate, boolean isCountQuery, String status,
	        boolean useCutoffDate) {
		String baseQuery = getQueryString(isCountQuery, status, useCutoffDate);
		
		try {
			// Create and configure the query
			Query query = entityManager.createNativeQuery(baseQuery).setParameter("startDate", startDate)
			        .setParameter("endDate", endDate);
			
			// Set the status parameter if required
			if (status != null) {
				query.setParameter("status", status);
			}
			
			// Set the cutoff date if required
			if (useCutoffDate) {
				// Calculate the cutoff date (28 days ago from today)
				Calendar calendar = Calendar.getInstance();
				calendar.add(Calendar.DAY_OF_YEAR, -28);
				Date cutoffDate = calendar.getTime();
				query.setParameter("cutoffDate", cutoffDate);
			}
			
			// Execute the query based on `isCountQuery`
			if (isCountQuery) {
				BigInteger totalCount = (BigInteger) query.getSingleResult();
				return totalCount.intValue();
			} else {
				return query.getResultList();
			}
		}
		catch (Exception e) {
			// Log the error and rethrow a runtime exception
			System.err.println("Error executing patient query: " + e.getMessage());
			throw new RuntimeException("Failed to execute patient query", e);
		}
	}
	
	private static String getQueryString(boolean isCountQuery, String status, boolean useCutoffDate) {
		String selectClause = isCountQuery ? "count(distinct fp.patient_id)" : "distinct fp.patient_id";
		
		// Start constructing the query
		String baseQuery = "select " + selectClause + " from openmrs.patient_appointment fp "
		        + "join openmrs.person p on fp.patient_id = p.person_id "
		        + (status != null ? "where fp.status = :status " : "where 1=1 ")
		        + (useCutoffDate ? "and fp.start_date_time <= :cutoffDate " : "")
		        + "and fp.start_date_time between :startDate and :endDate";
		return baseQuery;
	}
	
	private HashSet<Patient> fetchPatientsByIds(List<Integer> patientIds) {
		HashSet<Patient> patients = new HashSet<>();
		for (Integer patientId : patientIds) {
			Patient patient = Context.getPatientService().getPatient(patientId);
			if (patient != null) {
				patients.add(patient);
			}
		}
		return patients;
	}
	
	private Object executeDueForVlQuery(Date startDate, Date endDate, boolean isCountQuery) {
		String selectClause = isCountQuery ? "count(distinct fp.client_id)" : "distinct fp.client_id";
		String baseQuery = "select " + selectClause + " from ssemr_etl.ssemr_flat_encounter_hiv_care_follow_up fp "
		        + "join ssemr_etl.mamba_dim_person mp on fp.client_id = mp.person_id "
		        + "left join ssemr_etl.ssemr_flat_encounter_hiv_care_enrolment en ON en.client_id = fp.client_id "
		        + "left join ssemr_etl.ssemr_flat_encounter_personal_family_tx_history pfh on pfh.client_id = fp.client_id "
		        + "left join ssemr_etl.ssemr_flat_encounter_vl_laboratory_request vlr on vlr.client_id = fp.client_id "
		        + "left join ssemr_etl.ssemr_flat_encounter_high_viral_load hvl on hvl.client_id = fp.client_id " + "where ("
				// Criteria 1: Adults, ART > 6 months, VL suppressed (<1000), not breastfeeding,
				// next due VL in 6 months or 12 months
		        + "   (mp.age > 18 and pfh.art_start_date is not null and TIMESTAMPDIFF(MONTH, pfh.art_start_date, :endDate) >= 6 "
		        + "    and fp.viral_load_value < 1000 and (TIMESTAMPDIFF(MONTH, fp.date_vl_sample_collected, :endDate) >= 6 "
		        + "    or TIMESTAMPDIFF(MONTH, fp.date_vl_sample_collected, :endDate) >= 12)) "
				// Criteria 2: Child/Adolescent up to 18 years old, next VL in 6 months, join
				// criteria 1 at age 19
		        + "or (mp.age <= 18 and TIMESTAMPDIFF(MONTH, fp.date_vl_sample_collected, :endDate) >= 6) "
				// Criteria 3: Pregnant women newly enrolled on ART, due for VL every 3 months
				// while in PMTCT
		        + "or (fp.client_pregnant = 'Yes' and pfh.art_start_date is not null and TIMESTAMPDIFF(MONTH, pfh.art_start_date, :endDate) < 6 "
		        + "    and TIMESTAMPDIFF(MONTH, vlr.date_of_sample_collection, :endDate) >= 3) "
				// Criteria 4: Pregnant woman already on ART, eligible immediately after
				// discovering pregnancy
		        + "or (fp.client_pregnant = 'Yes' and TIMESTAMPDIFF(MONTH, pfh.art_start_date, :endDate) >= 6) "
				// Criteria 5: Post EAC 3, eligible for VL in 1 month
		        + "or (hvl.eac_session = 'Third EAC Session' and TIMESTAMPDIFF(MONTH, hvl.adherence_date, :endDate) >= 1) "
		        + ") " + "and fp.encounter_datetime between :startDate and :endDate";
				
		try {
			Query query = entityManager.createNativeQuery(baseQuery).setParameter("startDate", startDate)
			        .setParameter("endDate", endDate);
			
			if (isCountQuery) {
				BigInteger totalDueForVl = (BigInteger) query.getSingleResult();
				return totalDueForVl.intValue();
			} else {
				return query.getResultList();
			}
		}
		catch (NoResultException e) {
			System.err.println("No data found for the query: " + e.getMessage());
			return new ArrayList<>();
		}
		catch (Exception e) {
			System.err.println("Error executing Due for VL query: " + e.getMessage());
			throw new RuntimeException("Failed to execute Due for VL query", e);
		}
	}
	
	private Object getWaterfallAnalysisChart(String qStartDate, String qEndDate) throws ParseException {
		SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
		Date[] dates = getStartAndEndDate(qStartDate, qEndDate, dateTimeFormatter);
		
		// Get all active clients for the entire period
		HashSet<Patient> activeClientsEntirePeriod = getTxCurr(dates[0], dates[1]);
		
		// Get active clients for the last 30 days
		Calendar last30DaysCal = Calendar.getInstance();
		last30DaysCal.setTime(dates[1]);
		last30DaysCal.add(Calendar.DAY_OF_MONTH, -30);
		Date last30DaysStartDate = last30DaysCal.getTime();
		HashSet<Patient> activeClientsLast30Days = getTxCurr(last30DaysStartDate, dates[1]);
		
		// Exclude active clients from the last 30 days
		activeClientsEntirePeriod.removeAll(activeClientsLast30Days);
		
		// TX_CURR is the total number of active clients excluding the last 30 days
		int txCurrFirstTwoMonths = activeClientsEntirePeriod.size();
		
		// Get active clients for the third month
		HashSet<Patient> activeClientsThirdMonth = getTxCurr(dates[0], dates[1]);
		
		// TX_NEW is the new clients in the third month, excluding those from the first
		// two months
		HashSet<Patient> newClientsThirdMonth = new HashSet<>(activeClientsThirdMonth);
		newClientsThirdMonth.removeAll(activeClientsEntirePeriod);
		int txNewThirdMonth = newClientsThirdMonth.size();
		
		// Other calculations remain unchanged
		HashSet<Patient> transferredInPatientsCurrentQuarter = getTransferredInPatients(dates[0], dates[1]);
		HashSet<Patient> returnToTreatmentPatientsCurrentQuarter = getReturnToTreatmentPatients(dates[0], dates[1]);
		HashSet<Patient> transferredOutPatientsCurrentQuarter = getTransferredOutClients(dates[0], dates[1]);
		HashSet<Patient> deceasedPatientsCurrentQuarter = new HashSet<>(getDeceasedPatientsByDateRange(dates[0], dates[1]));
		HashSet<Patient> interruptedInTreatmentPatientsCurrentQuarter = getIit(dates[0], dates[1]);
		
		int transferInCurrentQuarter = transferredInPatientsCurrentQuarter.size();
		int txRttCurrentQuarter = returnToTreatmentPatientsCurrentQuarter.size();
		int transferOutCurrentQuarter = transferredOutPatientsCurrentQuarter.size();
		int txDeathCurrentQuarter = deceasedPatientsCurrentQuarter.size();
		
		HashSet<Patient> interruptedInTreatmentLessThan3Months = filterInterruptedInTreatmentPatients(
		    interruptedInTreatmentPatientsCurrentQuarter, 3, false);
		int txMlIitLessThan3MoCurrentQuarter = interruptedInTreatmentLessThan3Months.size();
		
		HashSet<Patient> interruptedInTreatmentMoreThan3Months = filterInterruptedInTreatmentPatients(
		    interruptedInTreatmentPatientsCurrentQuarter, 3, true);
		int txMlIitMoreThan3MoCurrentQuarter = interruptedInTreatmentMoreThan3Months.size();
		
		// Potential TX_CURR
		int potentialTxCurr = txNewThirdMonth + txCurrFirstTwoMonths + transferInCurrentQuarter + txRttCurrentQuarter;
		
		// CALCULATED TX_CURR
		
		// Prepare the results
		List<Map<String, Object>> waterfallAnalysisList = new ArrayList<>();
		waterfallAnalysisList.add(createResultMap("TX_CURR", txCurrFirstTwoMonths));
		waterfallAnalysisList.add(createResultMap("TX_NEW", txNewThirdMonth));
		waterfallAnalysisList.add(createResultMap("Transfer In", transferInCurrentQuarter));
		waterfallAnalysisList.add(createResultMap("TX_RTT", txRttCurrentQuarter));
		waterfallAnalysisList.add(createResultMap("Potential TX_CURR", potentialTxCurr));
		waterfallAnalysisList.add(createResultMap("Transfer Out", transferOutCurrentQuarter));
		waterfallAnalysisList.add(createResultMap("TX_DEATH", txDeathCurrentQuarter));
		waterfallAnalysisList.add(createResultMap("TX_ML_Self Transfer", 0));
		waterfallAnalysisList.add(createResultMap("TX_ML_Refusal/Stopped", 0));
		waterfallAnalysisList.add(createResultMap("TX_ML_IIT (<3 mo)", txMlIitLessThan3MoCurrentQuarter));
		waterfallAnalysisList.add(createResultMap("TX_ML_IIT (3+ mo)", txMlIitMoreThan3MoCurrentQuarter));
		waterfallAnalysisList.add(createResultMap("CALCULATED TX_CURR", potentialTxCurr));
		
		// Combine the results
		Map<String, Object> results = new HashMap<>();
		results.put("results", waterfallAnalysisList);
		return results;
	}
	
	private HashSet<Patient> filterInterruptedInTreatmentPatients(HashSet<Patient> patients, int months, boolean moreThan) {
		HashSet<Patient> filteredPatients = new HashSet<>();
		LocalDate currentDate = LocalDate.now();
		
		for (Patient patient : patients) {
			Date enrollmentDate = getInitiationDate(patient);
			if (enrollmentDate != null) {
				LocalDate enrollmentLocalDate = enrollmentDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
				long monthsOnTreatment = ChronoUnit.MONTHS.between(enrollmentLocalDate, currentDate);
				
				if ((moreThan && monthsOnTreatment >= months) || (!moreThan && monthsOnTreatment < months)) {
					filteredPatients.add(patient);
				}
			}
		}
		
		return filteredPatients;
	}
	
	private Date getInitiationDate(Patient patient) {
		List<Obs> initiationDateObs = Context.getObsService().getObservations(Collections.singletonList(patient.getPerson()),
		    null,
		    Collections.singletonList(Context.getConceptService().getConceptByUuid(DATE_OF_ART_INITIATION_CONCEPT_UUID)),
		    null, null, null, null, null, null, null, null, false);
		
		initiationDateObs.sort(Comparator.comparing(Obs::getObsDatetime).reversed());
		
		if (!initiationDateObs.isEmpty()) {
			Obs lastObs = initiationDateObs.get(0);
			Date initiationDate = lastObs.getValueDate();
			if (initiationDate != null) {
				return initiationDate;
			}
		}
		return null;
	}
}
