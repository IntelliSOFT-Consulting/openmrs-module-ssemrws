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
import javax.persistence.PersistenceContext;
import javax.servlet.http.HttpServletRequest;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.ObjectNode;
import org.openmrs.*;
import org.openmrs.api.context.Context;
import org.openmrs.module.ssemrws.queries.*;
import org.openmrs.module.ssemrws.web.constants.*;
import org.openmrs.module.ssemrws.web.dto.PatientObservations;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.parameter.EncounterSearchCriteria;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import static org.openmrs.module.ssemrws.constants.SharedConstants.*;
import static org.openmrs.module.ssemrws.web.constants.AllConcepts.*;
import static org.openmrs.module.ssemrws.web.constants.RegimenConcepts.*;

/**
 * This class configured as controller using annotation and mapped with the URL of
 * 'module/${rootArtifactid}/${rootArtifactid}Link.form'.
 */
@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + "/ssemr")
public class SSEMRWebServicesController {
	
	private final GetNextAppointmentDate getNextAppointmentDate;
	
	private final GetInterruptedInTreatment getInterruptedInTreatment;
	
	private final GetInterruptedInTreatmentWithinRange getInterruptedInTreatmentWithinRange;
	
	private final GetMissedAppointments getMissedAppointments;
	
	private final GetOnAppointment getOnAppoinment;
	
	private final GetAllPatients getAllPatients;
	
	private final GetPatientRegimens getPatientRegimens;
	
	private final GetVLDueDate getVLDueDate;
	
	private final GetTxCurr getTxCurrMain;
	
	private final GetRecurrenceOfIIT getRecurrenceOfIIT;
	
	public SSEMRWebServicesController(GetNextAppointmentDate getNextAppointmentDate,
	    GetInterruptedInTreatment getInterruptedInTreatment,
	    GetInterruptedInTreatmentWithinRange getInterruptedInTreatmentWithinRange,
	    GetMissedAppointments getMissedAppointments, GetOnAppointment getOnAppoinment, GetAllPatients getAllPatients,
	    GetPatientRegimens getPatientRegimens, GetVLDueDate getVLDueDate, GetTxCurr getTxCurrMain,
	    GetRecurrenceOfIIT getRecurrenceOfIIT) {
		this.getNextAppointmentDate = getNextAppointmentDate;
		this.getInterruptedInTreatment = getInterruptedInTreatment;
		this.getInterruptedInTreatmentWithinRange = getInterruptedInTreatmentWithinRange;
		this.getMissedAppointments = getMissedAppointments;
		this.getOnAppoinment = getOnAppoinment;
		this.getAllPatients = getAllPatients;
		this.getPatientRegimens = getPatientRegimens;
		this.getVLDueDate = getVLDueDate;
		this.getTxCurrMain = getTxCurrMain;
		this.getRecurrenceOfIIT = getRecurrenceOfIIT;
	}
	
	public enum filterCategory {
		CHILDREN_ADOLESCENTS,
		PREGNANT_BREASTFEEDING
	};
	
	@PersistenceContext
	private EntityManager entityManager;
	
	/**
	 * Retrieve filtered patients list
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/filteredPatientsList")
	@ResponseBody
	public Object getFilteredPatients(HttpServletRequest request,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory,
	        @RequestParam(required = false, value = "page") Integer page,
	        @RequestParam(required = false, value = "size") Integer size) {
		if (page == null)
			page = 0;
		if (size == null)
			size = 15;
		
		HashSet<Patient> filteredPatientsSet = getAllPatients.getAllPatients(page, size);
		
		if (filteredPatientsSet.isEmpty()) {
			return "No Patients found.";
		}
		
		List<Patient> filteredPatientsList = new ArrayList<>(filteredPatientsSet);
		filteredPatientsList.sort(Comparator.comparing(Patient::getPatientId).reversed());
		
		ObjectNode allPatientsObj = JsonNodeFactory.instance.objectNode();
		
		return getAllPatients.filteredPatientsListObj(filteredPatientsList, allPatientsObj);
	}
	
	/**
	 * Retrieves all patients from the system, applying pagination and filtering options.
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/allClients")
	@ResponseBody
	public Object getAllPatients(HttpServletRequest request,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory,
	        @RequestParam(required = false, value = "page") Integer page,
	        @RequestParam(required = false, value = "size") Integer size) {
		
		if (page == null)
			page = 0;
		if (size == null)
			size = 15;
		
		HashSet<Patient> allClientsSet = getAllPatients.getAllPatients(page, size);
		
		if (allClientsSet.isEmpty()) {
			return "No Patients found.";
		}
		
		List<Patient> allClients = new ArrayList<>(allClientsSet);
		allClients.sort(Comparator.comparing(Patient::getPatientId).reversed());
		
		ObjectNode allPatientsObj = JsonNodeFactory.instance.objectNode();
		
		return getAllPatients.allPatientsListObj(allClients, allPatientsObj);
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
		
		HashSet<Patient> interruptedInTreatmentPatients = getInterruptedInTreatment.getIit(dates[0], dates[1]);
		
		interruptedInTreatmentPatients = interruptedInTreatmentPatients.stream()
		        .filter(patient -> FilterUtility.applyFilter(patient, filterCategory, dates[1]))
		        .collect(Collectors.toCollection(HashSet::new));
		
		int totalPatients = interruptedInTreatmentPatients.size();
		
		List<Patient> iitList = new ArrayList<>(interruptedInTreatmentPatients);
		
		return fetchAndPaginatePatients(iitList, page, size, totalPatients, dates[0], dates[1], filterCategory);
	}
	
	/**
	 * Handles the HTTP GET request to retrieve patients who have experienced an interruption in their
	 * treatment. This method filters encounters based on ART treatment interruption encounter types and
	 * aggregates patients who have had such encounters within the specified date range. It aims to
	 * identify patients who might need follow-up or intervention due to treatment interruption.
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/interruptedInTreatmentWithinRange")
	@ResponseBody
	public Object getPatientsInterruptedInTreatmentWithinRange(HttpServletRequest request,
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
		
		HashSet<Patient> iitWithinRangePatients = getInterruptedInTreatmentWithinRange.getIitWithinRange(dates[0], dates[1]);
		
		iitWithinRangePatients = iitWithinRangePatients.stream()
		        .filter(patient -> FilterUtility.applyFilter(patient, filterCategory, dates[1]))
		        .collect(Collectors.toCollection(HashSet::new));
		
		int totalPatients = iitWithinRangePatients.size();
		
		List<Patient> iitWithinRangeList = new ArrayList<>(iitWithinRangePatients);
		
		return fetchAndPaginatePatients(iitWithinRangeList, page, size, totalPatients, dates[0], dates[1], filterCategory);
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
		
		HashSet<Patient> onAppointment = getOnAppoinment.getOnAppoinment(startDate, endDate);
		
		onAppointment = onAppointment.stream().filter(patient -> FilterUtility.applyFilter(patient, filterCategory, endDate))
		        .collect(Collectors.toCollection(HashSet::new));
		
		int totalPatients = onAppointment.size();
		
		List<Patient> onAppoinmentList = new ArrayList<>(onAppointment);
		
		return fetchAndPaginatePatients(onAppoinmentList, page, size, totalPatients, startDate, endDate, filterCategory);
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
		
		HashSet<Patient> missedAppointment = getMissedAppointments.getMissedAppointment(startDate, endDate);
		
		missedAppointment = missedAppointment.stream()
		        .filter(patient -> FilterUtility.applyFilter(patient, filterCategory, endDate))
		        .collect(Collectors.toCollection(HashSet::new));
		
		int totalPatients = missedAppointment.size();
		
		List<Patient> missedAppoinmentList = new ArrayList<>(missedAppointment);
		
		return fetchAndPaginatePatients(missedAppoinmentList, page, size, totalPatients, startDate, endDate, filterCategory);
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
		
		rttPatients = rttPatients.stream().filter(patient -> FilterUtility.applyFilter(patient, filterCategory, dates[1]))
		        .collect(Collectors.toCollection(HashSet::new));
		
		int totalPatients = rttPatients.size();
		
		List<Patient> rttList = new ArrayList<>(rttPatients);
		
		return fetchAndPaginatePatients(rttList, page, size, totalPatients, dates[0], dates[1], filterCategory);
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
		
		transferredOutPatients = transferredOutPatients.stream()
		        .filter(patient -> FilterUtility.applyFilter(patient, filterCategory, dates[1]))
		        .collect(Collectors.toCollection(HashSet::new));
		
		int totalPatients = transferredOutPatients.size();
		
		List<Patient> transferOutList = new ArrayList<>(transferredOutPatients);
		
		return fetchAndPaginatePatients(transferOutList, page, size, totalPatients, dates[0], dates[1], filterCategory);
	}
	
	/**
	 * This method handles the HTTP GET request for retrieving the list of patients who are deceased.
	 */
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
			String nextAppointmentDate = getNextAppointmentDate.getNextAppointmentDateByUuid(patient.getUuid());
			return !nextAppointmentDate.equals("No Upcoming Appointments");
		});
		
		deceasedPatients = deceasedPatients.stream()
		        .filter(patient -> FilterUtility.applyFilter(patient, filterCategory, dates[1]))
		        .collect(Collectors.toCollection(HashSet::new));
		
		int totalPatients = deceasedPatients.size();
		
		List<Patient> deceasedList = new ArrayList<>(deceasedPatients);
		
		return fetchAndPaginatePatients(deceasedList, page, size, totalPatients, dates[0], dates[1], filterCategory);
	}
	
	/**
	 * Retrieves patients on adult regimen treatment within a specified date range.
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/adultRegimenTreatment")
	@ResponseBody
	public Object getPatientsOnAdultRegimenTreatment(HttpServletRequest request,
	        @RequestParam("startDate") String qStartDate, @RequestParam("endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory) throws ParseException {
		
		SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
		Date startDate = dateTimeFormatter.parse(qStartDate);
		Date endDate = dateTimeFormatter.parse(qEndDate);
		
		// Add 23 hours to endDate
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(endDate);
		calendar.add(Calendar.HOUR_OF_DAY, 23);
		endDate = calendar.getTime();
		
		// Get txCurrPatients within the date range
		List<GetTxNew.PatientEnrollmentData> txCurrPatients = getTxCurrMain.getTxCurrPatients(startDate, endDate);
		
		// Fetch adult and child regimens
		return getPatientRegimens.getFilteredPatientsOnRegimenTreatment(qStartDate, qEndDate,
		    Arrays.asList(regimen_1A, regimen_1B, regimen_1C, regimen_1D, regimen_1E, regimen_1F, regimen_1G, regimen_1H,
		        regimen_1J, regimen_2A, regimen_2B, regimen_2C, regimen_2D, regimen_2E, regimen_2F, regimen_2G, regimen_2H,
		        regimen_2I, regimen_2J, regimen_2K),
		    Arrays.asList(regimen_4A, regimen_4B, regimen_4C, regimen_4D, regimen_4E, regimen_4F, regimen_4G, regimen_4H,
		        regimen_4I, regimen_4J, regimen_4K, regimen_4L, regimen_5A, regimen_5B, regimen_5C, regimen_5D, regimen_5E,
		        regimen_5F, regimen_5G, regimen_5H, regimen_5I, regimen_5J),
		    ACTIVE_REGIMEN_CONCEPT_UUID, txCurrPatients, true);
	}
	
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/childRegimenTreatment")
	@ResponseBody
	public Object getPatientsOnChildRegimenTreatment(HttpServletRequest request,
	        @RequestParam("startDate") String qStartDate, @RequestParam("endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory) throws ParseException {
		
		SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
		Date startDate = dateTimeFormatter.parse(qStartDate);
		Date endDate = dateTimeFormatter.parse(qEndDate);
		
		// Add 23 hours to endDate
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(endDate);
		calendar.add(Calendar.HOUR_OF_DAY, 23);
		endDate = calendar.getTime();
		
		// Get txCurrPatients within the date range
		List<GetTxNew.PatientEnrollmentData> txCurrPatients = getTxCurrMain.getTxCurrPatients(startDate, endDate);
		
		// Fetch adult and child regimens
		return getPatientRegimens.getFilteredPatientsOnRegimenTreatment(qStartDate, qEndDate,
		    Arrays.asList(regimen_1A, regimen_1B, regimen_1C, regimen_1D, regimen_1E, regimen_1F, regimen_1G, regimen_1H,
		        regimen_1J, regimen_2A, regimen_2B, regimen_2C, regimen_2D, regimen_2E, regimen_2F, regimen_2G, regimen_2H,
		        regimen_2I, regimen_2J, regimen_2K),
		    Arrays.asList(regimen_4A, regimen_4B, regimen_4C, regimen_4D, regimen_4E, regimen_4F, regimen_4G, regimen_4H,
		        regimen_4I, regimen_4J, regimen_4K, regimen_4L, regimen_5A, regimen_5B, regimen_5C, regimen_5D, regimen_5E,
		        regimen_5F, regimen_5G, regimen_5H, regimen_5I, regimen_5J),
		    ACTIVE_REGIMEN_CONCEPT_UUID, txCurrPatients, false);
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
		
		underCareOfCommunityPatients = underCareOfCommunityPatients.stream()
		        .filter(patient -> FilterUtility.applyFilter(patient, filterCategory, dates[1]))
		        .collect(Collectors.toCollection(HashSet::new));
		
		int totalPatients = underCareOfCommunityPatients.size();
		
		List<Patient> underCareList = new ArrayList<>(underCareOfCommunityPatients);
		
		return paginateAndGenerateSummary(underCareList, page, size, totalPatients, dates[0], dates[1], filterCategory);
	}
	
	/**
	 * Retrieves a list of patients under the care of community programs within a specified date range.
	 * 
	 * @return An Object representing the percentage of the clients who have and have not been linked to
	 *         a community health worker
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/chart/underCareOfCommunityProgrammes")
	@ResponseBody
	public Object getUnderCareOfCommunityProgram(HttpServletRequest request, @RequestParam("startDate") String qStartDate,
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
		
		int totalPatients = Context.getPatientService().getAllPatients().size();
		
		if (totalPatients == 0) {
			Map<String, Integer> response = new HashMap<>();
			response.put("totalPatients", 0);
			response.put("linkedToCHW", 0);
			response.put("notLinkedToCHW", 0);
			return response;
		}
		
		EncounterType communityLinkageEncounterType = Context.getEncounterService()
		        .getEncounterTypeByUuid(COMMUNITY_LINKAGE_ENCOUNTER_UUID);
		EncounterSearchCriteria encounterSearchCriteria = new EncounterSearchCriteria(null, null, dates[0], dates[1], null,
		        null, Collections.singletonList(communityLinkageEncounterType), null, null, null, false);
		List<Encounter> encounters = Context.getEncounterService().getEncounters(encounterSearchCriteria);
		
		HashSet<Patient> underCareOfCommunityPatients = encounters.stream().map(Encounter::getPatient).collect(HashSet::new,
		    HashSet::add, HashSet::addAll);
		
		underCareOfCommunityPatients = underCareOfCommunityPatients.stream()
		        .filter(patient -> FilterUtility.applyFilter(patient, filterCategory, dates[1]))
		        .collect(Collectors.toCollection(HashSet::new));
		
		List<Patient> underCareList = new ArrayList<>(underCareOfCommunityPatients);
		
		int linkedToCHW = Math.round(((float) underCareList.size() / totalPatients) * 100);
		int notLinkedToCHW = 100 - linkedToCHW;
		
		Map<String, Integer> response = new HashMap<>();
		response.put("totalPatients", totalPatients);
		response.put("linkedToCHW", linkedToCHW);
		response.put("notLinkedToCHW", notLinkedToCHW);
		
		return response;
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
	
	private PatientObservations getPatientObservations(Patient patient) {
		PatientObservations observations = new PatientObservations();
		
		observations.setEnrollmentDate(getEnrolmentDate(patient));
		observations.setDateOfinitiation(getEnrolmentDate(patient));
		observations.setLastRefillDate(getLastRefillDate(patient));
		observations.setArvRegimen(getARTRegimen(patient));
		observations.setLastCD4Count(getLastCD4Count(patient));
		observations.setCd4Done(getCD4Done(patient));
		observations.setTbStatus(getTbStatus(patient));
		observations.setArvRegimenDose(getARVRegimenDose(patient));
		observations.setWhoClinicalStage(getWHOClinicalStage(patient));
		observations.setDateVLSampleCollected(getLatestVLSampleCollectionDate(patient));
		observations.setDateVLResultsReceived(getDateVLResultsReceived(patient));
		observations.setVlResults(getVLResults(patient));
		observations.setVlStatus(getVLStatus(patient));
		observations.setBmi(getBMI(patient));
		observations.setMuac(getMUAC(patient));
		observations.setAppointmentDate(getNextAppointmentDate.getNextAppointmentDate(patient.getUuid()));
		observations.setClinicianName(getClinicianName(patient));
		observations.setLastVisitDate(getLastVisitDate(patient));
		observations.setTbNumber(getTbNumber(patient));
		observations.setFamilyMembers(getFamilyMemberObservations(patient));
		observations.setIndexFamilyMembers(getIndexFamilyMemberObservations(patient));
		observations.setVlDueDate(getVLDueDate.getVLDueDate(patient));
		observations.setIitRecurrence(String.valueOf(getRecurrenceOfIIT.getRecurrenceOfIIT(patient.getUuid())));
		observations.setTemperature(getPatientTemperature(patient));
		observations.setBlood_pressure(populateBloodPressure(patient));
		observations.setChw(getCommunityHealthWorkerObservations(patient));
		observations.setOnTb(getIsPatientOnTb(patient));
		observations.setHasPendingVl(hasPendingVlResults(patient));
		
		return observations;
	}
	
}
