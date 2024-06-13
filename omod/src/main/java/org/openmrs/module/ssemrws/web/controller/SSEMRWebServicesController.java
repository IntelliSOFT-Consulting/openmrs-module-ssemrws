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

import javax.servlet.http.HttpServletRequest;
import java.text.DateFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.time.DateUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.ObjectNode;
import org.openmrs.*;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.parameter.EncounterSearchCriteria;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * This class configured as controller using annotation and mapped with the URL of
 * 'module/${rootArtifactid}/${rootArtifactid}Link.form'.
 */
@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + "/ssemr")
public class SSEMRWebServicesController {
	
	public static final String VL_LAB_REQUEST_ENCOUNTER_TYPE = "82024e00-3f10-11e4-adec-0800271c1b75";
	
	public static final String FOLLOW_UP_FORM_ENCOUNTER_TYPE = "e8481555-9dd1-4bb5-ba8c-cb721dafb166";
	
	public static final String SAMPLE_COLLECTION_DATE_UUID = "ed520e2d-acb4-4ea9-8ae5-16ca27ace96d";
	
	public static final String YES_CONCEPT = "78763e68-104e-465d-8ce3-35f9edfb083d";
	
	public static final String LAST_REFILL_DATE_UUID = "80e34f1b-26e8-49ea-9b6e-d7d903a91e26";
	
	public static final String VIRAL_LOAD_CONCEPT_UUID = "01c3ce55-b7eb-45f5-93d5-bace353e3cfd";
	
	public static final String RETURNING_TO_TREATMENT_UUID = "4913c7f0-3362-4407-8d48-4b115f2f59dd";
	
	public static final String INTERRUPTION_IN_TREATMENT_UUID = "84c23dc4-40f4-4d9a-a2f5-ebeb4b4f3250";
	
	public static final String ART_TREATMENT_INTURRUPTION_ENCOUNTER_TYPE_UUID = "81852aee-3f10-11e4-adec-0800271c1b75";
	
	public static final String ACTIVE_REGIMEN_CONCEPT_UUID = "23322fd6-3dbb-410e-8bee-6210dfcd5f71";
	
	public static final String PERSONAL_FAMILY_HISTORY_ENCOUNTERTYPE_UUID = "0e9f540d-92cb-43c9-a95c-9407f5bf3f2a";
	
	public static final String COMMUNITY_LINKAGE_ENCOUNTER_UUID = "3c2df02e-6856-11ee-8c99-0242ac120002";
	
	public static final String DATE_OF_ENROLLMENT_UUID = "e27f8561-e242-4744-9193-b84d752dd86d";
	
	// Create Enum of the following filter categories: CHILDREN_ADOLESCENTS,
	// PREGNANT_BREASTFEEDING, RETURN_FROM_IIT, RETURN_TO_TREATMENT
	public enum filterCategory {
		CHILDREN_ADOLESCENTS,
		PREGNANT_BREASTFEEDING,
		IIT,
		RETURN_TO_TREATMENT
	};
	
	public static final String ENROLMENT_ENCOUNTER_TYPE_UUID = "f469b65f-a4f6-4723-989a-46090de6a0e5";
	
	public static final String TRANSFER_IN_CONCEPT_UUID = "735cd395-0ef1-4832-a58c-e8afb567d3b3";
	
	public static final String CURRENTLY_BREASTFEEDING_CONCEPT_UUID = "e288fc7d-bbc5-479a-b94d-857e3819f926";
	
	private static final String CURRENTLY_PREGNANT_CONCEPT_UUID = "235a6246-6179-4309-ba84-6f0ec337eb48";
	
	public static final String CONCEPT_BY_UUID = "78763e68-104e-465d-8ce3-35f9edfb083d";
	
	public static final String TELEPHONE_NUMBER_UUID = "8f0a2a16-c073-4622-88ad-a11f2d6966ad";
	
	public static final String regimen_1A = "9062c6d9-a650-44d2-8929-da84f617c427";
	
	public static final String regimen_1B = "c22e6700-a937-4909-b4ad-e82ff51325ac";
	
	public static final String regimen_1C = "a00d9620-e88b-4c2f-9293-b1ac9e5943f2";
	
	public static final String regimen_1D = "5d500ca2-350a-49ee-a3d4-f340db32ff31";
	
	public static final String regimen_1E = "3fd242a8-7ade-463c-8919-d82573ea8526";
	
	public static final String regimen_1F = "91f5d0d6-eb81-484f-9376-4bfb926a5a81";
	
	public static final String regimen_1G = "0dd3e78f-e1fc-47de-95bc-1f489d0dfcc5";
	
	public static final String regimen_1H = "4a86fbee-07a9-422a-bf69-16256c0c2b8b";
	
	public static final String regimen_1J = "03224cae-f115-4814-bd53-c99c72288446";
	
	public static final String regimen_2A = "bd97cacd-4a91-4901-8803-3a4a2e5f1ca8";
	
	public static final String regimen_2B = "ad6ff4ef-769e-4aec-b8bb-7f033fe6aaaa";
	
	public static final String regimen_2C = "47167ec1-4957-4d0a-a58c-3894bdeb93ff";
	
	public static final String regimen_2D = "e649a0ec-e193-4af0-bb49-02687107a893";
	
	public static final String regimen_2E = "accca537-b8ee-41ec-b902-7de814d099b2";
	
	public static final String regimen_2F = "77f201fc-aefc-4068-baa7-cb3284782a38";
	
	public static final String regimen_2G = "cb0f9fcd-fb52-493f-95aa-d0197387fbdb";
	
	public static final String regimen_2H = "28790bde-81db-4490-806b-ac10c17b41dc";
	
	public static final String regimen_2I = "aae69cae-2806-4e8b-a916-f22ed733a19b";
	
	public static final String regimen_2J = "64336206-c9bc-4d37-accf-c7abac7a37f6";
	
	public static final String regimen_2K = "25f0cca5-902d-4e36-9e4f-5ce5da744a75";
	
	public static final String regimen_4A = "c224b116-27ec-4156-93ba-d4838a3ac1c0";
	
	public static final String regimen_4B = "5efe8d99-c65e-4136-8820-5f3646437ff7";
	
	public static final String regimen_4C = "f8f64be8-ccb4-404d-b99e-3c4975155da5";
	
	public static final String regimen_4D = "28c5d192-ba71-4ef5-8604-ecf6bd177126";
	
	public static final String regimen_4E = "3eaf04dc-e284-42f7-860e-02cda37cf230";
	
	public static final String regimen_4F = "0372f3fb-5e8a-474f-8250-01af7a485778";
	
	public static final String regimen_4G = "ce2412c4-a041-4328-bfaa-35e041ca4802";
	
	public static final String regimen_4H = "6ed47806-8809-4c5a-a1b6-fe2ec0158563";
	
	public static final String regimen_4I = "f6b1c6ea-b0a2-46a0-b7e0-3038d268356c";
	
	public static final String regimen_4J = "2e1ab9d3-7fe1-48ba-a12c-fd8d26bc161d";
	
	public static final String regimen_4K = "99f54f96-e761-4d86-bb1b-0abc2a24fa16";
	
	public static final String regimen_4L = "5287f2a4-23e5-4314-b60c-0a4b91753ec6";
	
	public static final String regimen_5A = "b23cf614-dfec-48c9-a12f-ba577e28347d";
	
	public static final String regimen_5B = "dabc93c3-8c3d-41e1-b3e3-d7e14c4765b6";
	
	public static final String regimen_5C = "da3c6710-c431-4582-a444-a466d54693ec";
	
	public static final String regimen_5D = "6c383d11-2b29-4cc2-bfa4-811ff7a988f1";
	
	public static final String regimen_5E = "82725d14-00c6-4864-bf8b-ad5db0b3c3fa";
	
	public static final String regimen_5F = "140ede93-5691-463b-9d17-2dc8834621f8";
	
	public static final String regimen_5G = "06017ac1-2ce8-4689-a3bf-4e9f3d54978f";
	
	public static final String regimen_5H = "2c0a5b91-7b2a-4f8e-86fd-a8007841fca8";
	
	public static final String regimen_5I = "50b60d77-186d-4a0d-8784-659ee2d60ec9";
	
	public static final String regimen_5J = "78e49624-0e33-4374-93b7-60b132b26dae";

	
	private static final double THRESHOLD = 1000.0;
	
	/** Logger for this class and subclasses */
	protected final Log log = LogFactory.getLog(getClass());
	
	static SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
	
	/**
	 * Gets a list of available/completed forms for a patient
	 * 
	 * @param request
	 * @param patientUuid
	 * @return
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/forms")
	// gets all visit forms for a patient
	@ResponseBody
	public Object getAllAvailableFormsForVisit(HttpServletRequest request, @RequestParam("patientUuid") String patientUuid) {
		if (StringUtils.isBlank(patientUuid)) {
			return new ResponseEntity<Object>("You must specify patientUuid in the request!", new HttpHeaders(),
			        HttpStatus.BAD_REQUEST);
		}
		
		Patient patient = Context.getPatientService().getPatientByUuid(patientUuid);
		
		if (patient == null) {
			return new ResponseEntity<Object>("The provided patient was not found in the system!", new HttpHeaders(),
			        HttpStatus.NOT_FOUND);
		}
		
		List<Visit> activeVisits = Context.getVisitService().getActiveVisitsByPatient(patient);
		ArrayNode formList = JsonNodeFactory.instance.arrayNode();
		ObjectNode allFormsObj = JsonNodeFactory.instance.objectNode();
		
		if (!activeVisits.isEmpty()) {
			Visit patientVisit = activeVisits.get(0);
			
			/**
			 * {uuid: string; encounterType?: EncounterType; name: string; display: string; version: string;
			 * published: boolean; retired: boolean;}
			 */
			
			/*
			 * FormManager formManager =
			 * CoreContext.getInstance().getManager(FormManager.class); List<FormDescriptor>
			 * uncompletedFormDescriptors =
			 * formManager.getAllUncompletedFormsForVisit(patientVisit);
			 * 
			 * if (!uncompletedFormDescriptors.isEmpty()) {
			 * 
			 * for (FormDescriptor descriptor : uncompletedFormDescriptors) {
			 * if(!descriptor.getTarget().getRetired()) { ObjectNode formObj =
			 * generateFormDescriptorPayload(descriptor); formObj.put("formCategory",
			 * "available"); formList.add(formObj); } } PatientWrapper patientWrapper = new
			 * PatientWrapper(patient); Encounter lastMchEnrollment =
			 * patientWrapper.lastEncounter(MetadataUtils.existing(EncounterType.class,
			 * MchMetadata._EncounterType.MCHMS_ENROLLMENT)); if(lastMchEnrollment != null)
			 * { ObjectNode delivery = JsonNodeFactory.instance.objectNode();
			 * delivery.put("uuid", MCH_DELIVERY_FORM_UUID); delivery.put("name",
			 * "Delivery"); delivery.put("display", "MCH Delivery Form");
			 * delivery.put("version", "1.0"); delivery.put("published", true);
			 * delivery.put("retired", false); formList.add(delivery); } CalculationResult
			 * eligibleForDischarge =
			 * EmrCalculationUtils.evaluateForPatient(EligibleForMchmsDischargeCalculation.
			 * class, null, patient); if((Boolean) eligibleForDischarge.getValue() == true)
			 * { ObjectNode discharge = JsonNodeFactory.instance.objectNode();
			 * discharge.put("uuid", MCH_DISCHARGE_FORM_UUID); discharge.put("name",
			 * "Discharge"); discharge.put("display", "MCH Discharge Form");
			 * discharge.put("version", "1.0"); discharge.put("published", true);
			 * discharge.put("retired", false); formList.add(discharge); } ObjectNode
			 * labOrder = JsonNodeFactory.instance.objectNode(); labOrder.put("uuid",
			 * LAB_ORDERS_FORM_UUID); labOrder.put("name", "Laboratory Test Orders");
			 * labOrder.put("display", "Laboratory Test Orders"); labOrder.put("version",
			 * "1.0"); labOrder.put("published", true); labOrder.put("retired", false);
			 * formList.add(labOrder); }
			 */
		}
		
		allFormsObj.put("results", formList);
		
		return allFormsObj.toString();
	}
	
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/allClients")
	// gets all visit forms for a patient
	@ResponseBody
	public Object getAllPatients(HttpServletRequest request, @RequestParam("startDate") String qStartDate,
	        @RequestParam("endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory) throws ParseException {
		Date startDate = dateTimeFormatter.parse(qStartDate);
		Date endDate = dateTimeFormatter.parse(qEndDate);
		List<Patient> allPatients = Context.getPatientService().getAllPatients(false);
		return generatePatientListObj(new HashSet<>(allPatients), endDate, filterCategory);
	}
	
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/dueForVl")
	// gets all visit forms for a patient
	@ResponseBody
	public Object getPatientsDueForVl(HttpServletRequest request) {
		List<Patient> allPatients = Context.getPatientService().getAllPatients(false);
		
		return generatePatientListObj((HashSet<Patient>) allPatients);
	}
	
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/missedAppointment")
	// gets all visit forms for a patient
	@ResponseBody
	public Object getPatientsMissedAppointment(HttpServletRequest request) {
		List<Patient> allPatients = Context.getPatientService().getAllPatients(false);
		
		return generatePatientListObj((HashSet<Patient>) allPatients);
	}
	
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/adultRegimenTreatment")
	// gets all visit forms for a patient
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
	
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/childRegimenTreatment")
	// gets all visit forms for a patient
	@ResponseBody
	public Object getPatientsOnChildRegimenTreatment(HttpServletRequest request,
	        @RequestParam("startDate") String qStartDate, @RequestParam("endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory) throws ParseException {
		
		return getPatientsOnRegimenTreatment(qStartDate, qEndDate,
		    Arrays.asList(regimen_4A, regimen_4B, regimen_4C, regimen_4D, regimen_4E, regimen_4F, regimen_4G, regimen_4H,
		        regimen_4I, regimen_4J, regimen_4K, regimen_4L, regimen_5A, regimen_5B, regimen_5C, regimen_5D, regimen_5E,
		        regimen_5F, regimen_5G, regimen_5H, regimen_5I, regimen_5J),
		    ACTIVE_REGIMEN_CONCEPT_UUID);
	}
	
	private Object getPatientsOnRegimenTreatment(String qStartDate, String qEndDate, List<String> regimenConceptUuids,
	        String activeRegimenConceptUuid) throws ParseException {
		
		Date startDate = dateTimeFormatter.parse(qStartDate);
		Date endDate = dateTimeFormatter.parse(qEndDate);
		
		List<String> regimenTreatmentEncounterTypeUuids = Arrays.asList(PERSONAL_FAMILY_HISTORY_ENCOUNTERTYPE_UUID,
		    FOLLOW_UP_FORM_ENCOUNTER_TYPE);
		
		List<Encounter> regimenTreatmentEncounters = getEncountersByEncounterTypes(regimenTreatmentEncounterTypeUuids,
		    startDate, endDate);
		
		List<Concept> regimenConcepts = getConceptsByUuids(regimenConceptUuids);
		
		List<Obs> regimenTreatmentObs = Context.getObsService().getObservations(null, regimenTreatmentEncounters,
		    Collections.singletonList(Context.getConceptService().getConceptByUuid(activeRegimenConceptUuid)),
		    regimenConcepts, null, null, null, null, null, null, endDate, false);
		
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
	
	/**
	 * Retrieves a list of concepts based on their UUIDs.
	 * 
	 * @param conceptUuids A list of UUIDs of concepts to retrieve.
	 * @return A list of concepts corresponding to the given UUIDs.
	 */
	private static List<Concept> getConceptsByUuids(List<String> conceptUuids) {
		return conceptUuids.stream().map(uuid -> Context.getConceptService().getConceptByUuid(uuid))
		        .collect(Collectors.toList());
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
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory) throws ParseException {
		Date startDate = dateTimeFormatter.parse(qStartDate);
		Date endDate = dateTimeFormatter.parse(qEndDate);
		
		EncounterType communityLinkageEncounterType = Context.getEncounterService()
		        .getEncounterTypeByUuid(COMMUNITY_LINKAGE_ENCOUNTER_UUID);
		EncounterSearchCriteria encounterSearchCriteria = new EncounterSearchCriteria(null, null, startDate, endDate, null,
		        null, Collections.singletonList(communityLinkageEncounterType), null, null, null, false);
		List<Encounter> encounters = Context.getEncounterService().getEncounters(encounterSearchCriteria);
		
		HashSet<Patient> underCareOfCommunityPatients = encounters.stream().map(Encounter::getPatient).collect(HashSet::new,
		    HashSet::add, HashSet::addAll);
		
		return generatePatientListObj(underCareOfCommunityPatients, endDate);
	}
	
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/viralLoadSamplesCollected")
	@ResponseBody
	public Object getViralLoadSamplesCollected(HttpServletRequest request,
	        @RequestParam(value = "startDate") String qStartDate, @RequestParam(value = "endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory) {
		try {
			Date startDate = dateTimeFormatter.parse(qStartDate);
			Date endDate = dateTimeFormatter.parse(qEndDate);
			
			EncounterType viralLoadEncounterType = Context.getEncounterService()
			        .getEncounterTypeByUuid(VL_LAB_REQUEST_ENCOUNTER_TYPE);
			EncounterSearchCriteria encounterSearchCriteria = new EncounterSearchCriteria(null, null, null, endDate, null,
			        null, Collections.singletonList(viralLoadEncounterType), null, null, null, false);
			List<Encounter> viralLoadSampleEncounters = Context.getEncounterService().getEncounters(encounterSearchCriteria);
			
			// get the date of sample collection from the obs in the viral load encounters
			Concept sampleCollectionDateConcept = Context.getConceptService().getConceptByUuid(SAMPLE_COLLECTION_DATE_UUID);
			List<Obs> sampleCollectionDateObs = Context.getObsService().getObservations(null, viralLoadSampleEncounters,
			    Collections.singletonList(sampleCollectionDateConcept), null, null, null, null, null, null, null, endDate,
			    false);
			
			ObjectNode simpleObject = generateDashboardSummaryFromObs(startDate, endDate, sampleCollectionDateObs,
			    filterCategory);
			
			return simpleObject;
			
		}
		catch (APIException | ParseException e) {
			throw new RuntimeException(e);
		}
	}
	
	private static ObjectNode generateDashboardSummaryFromObs(Date startDate, Date endDate, List<Obs> obsList,
	        filterCategory filterCategory) {
		// TODO: Implement filter category logic
		
		ObjectNode simpleObject = JsonNodeFactory.instance.objectNode();
		// Instantiate an array with all months of the year
		String[] months = new String[] { "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov",
		        "Dec" };
		
		// Instantiate an array with all days of the week
		String[] days = new String[] { "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun" };
		
		HashMap<String, List<ObjectNode>> monthlyGrouping = new HashMap<>();
		HashMap<String, Integer> weeklySummary = new HashMap<>();
		HashMap<String, Integer> monthlySummary = new HashMap<>();
		HashMap<String, Integer> dailySummary = new HashMap<>();
		
		// For each obs in the obsList, filter Value Datetime that falls between the
		// start date and end date
		for (Obs obs : obsList) {
			if (obs.getValueDate().after(DateUtils.addDays(startDate, -1))
			        && obs.getValueDate().before(DateUtils.addDays(endDate, 1))) {
				// Add logic to group the data by month and week and day and calculate counts
				// for each group
				Calendar calendar = Calendar.getInstance();
				calendar.setTime(obs.getValueDate());
				String month = months[calendar.get(Calendar.MONTH)];
				
				Person person = obs.getPerson();
				ObjectNode personObj = generatePatientObject(endDate, filterCategory, (Patient) person);
				if (monthlyGrouping.containsKey(month)) {
					// check if person already exists in the list for the month
					if (!monthlyGrouping.get(month).contains(personObj)) {
						monthlyGrouping.get(month).add(personObj);
					}
				} else {
					monthlyGrouping.put(month, Collections.singletonList(personObj));
				}
				
				// Group by month
				if (monthlySummary.containsKey(month)) {
					monthlySummary.put(month, monthlySummary.get(month) + 1);
				} else {
					monthlySummary.put(month, 1);
				}
				
				// Group by week
				int week = calendar.get(Calendar.WEEK_OF_MONTH);
				String weekOfTheMonth = String.format("%s_%s", month, week);
				if (weeklySummary.containsKey(weekOfTheMonth)) {
					weeklySummary.put(weekOfTheMonth, weeklySummary.get(weekOfTheMonth) + 1);
				} else {
					weeklySummary.put(weekOfTheMonth, 1);
				}
				
				// Group by day
				
				int day = calendar.get(Calendar.DAY_OF_WEEK);
				// use string.format instead of concatenation
				String day_in_week = String.format("%s_%s", week, days[day]);
				if (dailySummary.containsKey(day_in_week)) {
					dailySummary.put(day_in_week, dailySummary.get(day_in_week) + 1);
				} else {
					dailySummary.put(day_in_week, 1);
				}
			}
		}
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode summaryNode = mapper.createObjectNode();
		summaryNode.put("groupYear", mapper.valueToTree(monthlySummary));
		summaryNode.put("groupMonth", mapper.valueToTree(weeklySummary));
		summaryNode.put("groupWeek", mapper.valueToTree(dailySummary));
		
		simpleObject.put("summary", summaryNode);
		
		return simpleObject;
	}
	
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/viralLoadResults")
	// gets all visit forms for a patient
	@ResponseBody
	public Object getViralLoadResults(HttpServletRequest request) {
		List<Patient> allPatients = Context.getPatientService().getAllPatients(false);
		// Add logic to filter patients on Child regimen treatment
		
		return generateViralLoadListObj(allPatients);
	}
	
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/viralLoadCoverage")
	// gets all visit forms for a patient
	@ResponseBody
	public Object getViralLoadCoverage(HttpServletRequest request) {
		List<Patient> allPatients = Context.getPatientService().getAllPatients(false);
		// Add logic to filter patients on Child regimen treatment
		
		return generateViralLoadListObj(allPatients);
	}
	
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/viralLoadSuppression")
	// gets all visit forms for a patient
	@ResponseBody
	public Object getViralLoadSuppression(HttpServletRequest request) {
		List<Patient> allPatients = Context.getPatientService().getAllPatients(false);
		// Add logic to filter patients on Child regimen treatment
		
		return generateViralLoadListObj(allPatients);
	}
	
	private Object generatePatientListObj(HashSet<Patient> allPatients) {
		return generatePatientListObj(allPatients, new Date());
	}
	
	private Object generatePatientListObj(HashSet<Patient> allPatients, Date endDate) {
		return generatePatientListObj(allPatients, new Date(), null);
	}
	
	private Object generatePatientListObj(HashSet<Patient> allPatients, Date endDate, filterCategory filterCategory) {
		ArrayNode patientList = JsonNodeFactory.instance.arrayNode();
		ObjectNode allPatientsObj = JsonNodeFactory.instance.objectNode();
		
		// Initialize HashMaps to store counts
		HashMap<String, Integer> yearlySummary = new HashMap<>();
		HashMap<String, Integer> monthlySummary = new HashMap<>();
		HashMap<String, Integer> weeklySummary = new HashMap<>();
		
		for (Patient patient : allPatients) {
			ObjectNode patientObj = generatePatientObject(endDate, filterCategory, patient);
			if (patientObj != null) {
				patientList.add(patientObj);
				
				// Extract month, week, and day
				Calendar calendar = Calendar.getInstance();
				calendar.setTime(endDate);
				int monthOfYear = calendar.get(Calendar.MONTH);
				int weekOfMonth = calendar.get(Calendar.WEEK_OF_MONTH);
				int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
				
				// Update counts for month
				String monthKey = new DateFormatSymbols().getMonths()[monthOfYear];
				monthlySummary.put(monthKey, monthlySummary.getOrDefault(monthKey, 0) + 1);
				
				// Update counts for week
				String weekKey = "Week" + weekOfMonth;
				weeklySummary.put(weekKey, weeklySummary.getOrDefault(weekKey, 0) + 1);
				
				// Update counts for day
				String dayKey = new DateFormatSymbols().getShortWeekdays()[dayOfWeek];
				if (!dayKey.isEmpty()) {
					yearlySummary.put(dayKey, yearlySummary.getOrDefault(dayKey, 0) + 1);
				}
			}
		}
		
		// Construct summary object
		ObjectNode groupingObj = JsonNodeFactory.instance.objectNode();
		ObjectNode groupYear = JsonNodeFactory.instance.objectNode();
		ObjectNode groupMonth = JsonNodeFactory.instance.objectNode();
		ObjectNode groupWeek = JsonNodeFactory.instance.objectNode();
		
		groupYear.putAll(yearlySummary.entrySet().stream()
		        .collect(Collectors.toMap(Map.Entry::getKey, e -> JsonNodeFactory.instance.numberNode(e.getValue()))));
		groupMonth.putAll(monthlySummary.entrySet().stream()
		        .collect(Collectors.toMap(Map.Entry::getKey, e -> JsonNodeFactory.instance.numberNode(e.getValue()))));
		groupWeek.putAll(weeklySummary.entrySet().stream()
		        .collect(Collectors.toMap(Map.Entry::getKey, e -> JsonNodeFactory.instance.numberNode(e.getValue()))));
		
		groupingObj.put("groupYear", groupYear);
		groupingObj.put("groupMonth", groupMonth);
		groupingObj.put("groupWeek", groupWeek);
		
		allPatientsObj.put("results", patientList);
		allPatientsObj.put("summary", groupingObj);
		
		return allPatientsObj.toString();
	}
	
	private static ObjectNode generatePatientObject(Date endDate, filterCategory filterCategory, Patient patient) {
		ObjectNode patientObj = JsonNodeFactory.instance.objectNode();
		Date startDate = new Date();
		String dateEnrolled = determineEnrolmentDate(patient, startDate, endDate);
		String lastRefillDate = getLastRefillDate(patient, startDate, endDate);
		// Calculate age in years based on patient's birthdate and current date
		Date birthdate = patient.getBirthdate();
		Date currentDate = new Date();
		long age = (currentDate.getTime() - birthdate.getTime()) / (1000L * 60 * 60 * 24 * 365);
		
		patientObj.put("uuid", patient.getUuid());
		patientObj.put("name", patient.getPersonName() != null ? patient.getPersonName().toString() : "");
		patientObj.put("identifier",
		    patient.getPatientIdentifier() != null ? patient.getPatientIdentifier().toString() : "");
		patientObj.put("sex", patient.getGender());
		patientObj.put("dateEnrolled", dateEnrolled);
		patientObj.put("lastRefillDate", lastRefillDate);
		patientObj.put("newClient", determineIfPatientIsNewClient(patient, startDate, endDate));
		patientObj.put("childOrAdolescent", age <= 19 ? true : false);
		patientObj.put("pregnantAndBreastfeeding", determineIfPatientIsPregnantOrBreastfeeding(patient, endDate));
		patientObj.put("IIT", determineIfPatientIsIIT(patient, endDate));
		patientObj.put("returningToTreatment", determineIfPatientIsReturningToTreatment(patient, endDate));
		patientObj.put("dueForVl", determineIfPatientIsDueForVl(patient));
		patientObj.put("highVl", determineIfPatientIsHighVl(patient, endDate));
		patientObj.put("onAppointment", determineIfPatientIsOnAppointment(patient));
		patientObj.put("missedAppointment", determineIfPatientMissedAppointment(patient));
		
		// check filter category and filter patients based on the category
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
					if (determineIfPatientIsIIT(patient, endDate)) {
						return patientObj;
					}
					break;
				case RETURN_TO_TREATMENT:
					if (determineIfPatientIsReturningToTreatment(patient, endDate)) {
						return patientObj;
					}
			}
		} else {
			return patientObj;
		}
		return null;
	}
	
	private static boolean determineIfPatientMissedAppointment(Patient patient) {
		
		return Math.random() < 0.5;
		// TODO: Add logic to determine if patient Missed appointment
		// return false;
	}
	
	private static boolean determineIfPatientIsOnAppointment(Patient patient) {
		return Math.random() < 0.5;
		// TODO: Add logic to determine if patient was on appointment
		// return false;
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
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory) throws ParseException {
		
		Date startDate = dateTimeFormatter.parse(qStartDate);
		Date endDate = dateTimeFormatter.parse(qEndDate);
		
		HashSet<Patient> highVLPatients = getPatientsWithHighVL(startDate, endDate);
		
		return generatePatientListObj(highVLPatients, endDate);
	}
	
	// Get all patients who have high Viral Load
	private HashSet<Patient> getPatientsWithHighVL(Date startDate, Date endDate) {
		EncounterType followUpEncounterType = Context.getEncounterService()
		        .getEncounterTypeByUuid(FOLLOW_UP_FORM_ENCOUNTER_TYPE);
		EncounterSearchCriteria encounterSearchCriteria = new EncounterSearchCriteria(null, null, startDate, endDate, null,
		        null, Collections.singletonList(followUpEncounterType), null, null, null, false);
		List<Encounter> encounters = Context.getEncounterService().getEncounters(encounterSearchCriteria);
		
		HashSet<Patient> highVLPatients = new HashSet<>();
		
		List<Obs> highVLObs = Context.getObsService().getObservations(null, encounters,
		    Collections.singletonList(Context.getConceptService().getConceptByUuid(VIRAL_LOAD_CONCEPT_UUID)), null, null,
		    null, null, null, null, null, endDate, false);
		
		for (Obs obs : highVLObs) {
			if (obs.getValueNumeric() != null && obs.getValueNumeric() >= THRESHOLD) {
				highVLPatients.add((Patient) obs.getPerson());
			}
		}
		
		return highVLPatients;
		
	}
	
	// Determine if Patient is High Viral Load and return true if it is equal or
	// above threshold
	private static boolean determineIfPatientIsHighVl(Patient patient, Date endDate) {
		Concept vlConcept = Context.getConceptService().getConceptByUuid(VIRAL_LOAD_CONCEPT_UUID);
		List<Obs> vlObs = Context.getObsService().getObservations(Collections.singletonList(patient.getPerson()), null,
		    Collections.singletonList(vlConcept), null, null, null, null, 1, null, null, endDate, false);
		if (vlObs != null && !vlObs.isEmpty()) {
			return vlObs.get(0).getValueNumeric() >= THRESHOLD;
		}
		return false;
	}
	
	private static boolean determineIfPatientIsDueForVl(Patient patient) {
		return Math.random() < 0.5;
		// TODO: Add logic to determine if patient is due for VL
		// return false;
	}
	
	private static boolean determineIfPatientIsNewClient(Patient patient, Date startDate, Date endDate) {
		// return random true or false value for now
		return Math.random() < 0.5;
		// TODO: Add logic to determine if patient is new client - Check
		// #logicToDetermineIfNewlyEnrolled method
		// return false;
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
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory) throws ParseException {
		
		Date startDate = dateTimeFormatter.parse(qStartDate);
		Date endDate = dateTimeFormatter.parse(qEndDate);
		
		List<String> encounterTypeUuids = Collections.singletonList(ART_TREATMENT_INTURRUPTION_ENCOUNTER_TYPE_UUID);
		
		List<Encounter> returnedToTreatmentEncounters = getEncountersByEncounterTypes(encounterTypeUuids, startDate,
		    endDate);
		
		HashSet<Patient> returnedToTreatmentPatients = returnedToTreatmentEncounters.stream().map(Encounter::getPatient)
		        .collect(Collectors.toCollection(HashSet::new));
		
		List<Obs> returnedToTreatmentObs = Context.getObsService().getObservations(null, returnedToTreatmentEncounters,
		    Collections.singletonList(Context.getConceptService().getConceptByUuid(RETURNING_TO_TREATMENT_UUID)),
		    Collections.singletonList(Context.getConceptService().getConceptByUuid(CONCEPT_BY_UUID)), null, null, null, null,
		    null, null, endDate, false);
		
		HashSet<Patient> interruptedInTreatmentEncountersClients = returnedToTreatmentObs.stream()
		        .filter(obs -> obs.getPerson() instanceof Patient).map(obs -> (Patient) obs.getPerson())
		        .collect(Collectors.toCollection(HashSet::new));
		
		returnedToTreatmentPatients.addAll(interruptedInTreatmentEncountersClients);
		
		return generatePatientListObj(returnedToTreatmentPatients, endDate);
	}
	
	// Determine if patient is returning to treatment
	private static boolean determineIfPatientIsReturningToTreatment(Patient patient, Date endDate) {
		List<Concept> returningToTreatmentConcept = new ArrayList<>();
		returningToTreatmentConcept.add(Context.getConceptService().getConceptByUuid(RETURNING_TO_TREATMENT_UUID));
		
		List<Obs> obsList = Context.getObsService().getObservations(Collections.singletonList(patient), null,
		    returningToTreatmentConcept,
		    Collections.singletonList(Context.getConceptService().getConceptByUuid(CONCEPT_BY_UUID)), null, null, null, null,
		    null, null, endDate, false);
		
		return !obsList.isEmpty();
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
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory) throws ParseException {
		Date startDate = dateTimeFormatter.parse(qStartDate);
		Date endDate = dateTimeFormatter.parse(qEndDate);
		
		List<String> encounterTypeUuids = Collections.singletonList(ART_TREATMENT_INTURRUPTION_ENCOUNTER_TYPE_UUID);
		
		List<Encounter> interruptedInTreatmentEncounters = getEncountersByEncounterTypes(encounterTypeUuids, startDate,
		    endDate);
		
		HashSet<Patient> interruptedInTreatmentPatients = interruptedInTreatmentEncounters.stream()
		        .map(Encounter::getPatient).collect(Collectors.toCollection(HashSet::new));
		
		List<Obs> interruptedInTreatmentObs = Context.getObsService().getObservations(null, interruptedInTreatmentEncounters,
		    Collections.singletonList(Context.getConceptService().getConceptByUuid(INTERRUPTION_IN_TREATMENT_UUID)), null,
		    null, null, null, null, null, null, endDate, false);
		
		HashSet<Patient> interruptedInTreatmentEncountersClients = interruptedInTreatmentObs.stream()
		        .filter(obs -> obs.getPerson() instanceof Patient).map(obs -> (Patient) obs.getPerson())
		        .collect(Collectors.toCollection(HashSet::new));
		
		interruptedInTreatmentPatients.addAll(interruptedInTreatmentEncountersClients);
		
		return generatePatientListObj(interruptedInTreatmentPatients, endDate);
		
	}
	
	// Determine if patient is Interrupted In Treatment
	private static boolean determineIfPatientIsIIT(Patient patient, Date endDate) {
		List<Concept> interruptionIntreatmentConcept = new ArrayList<>();
		interruptionIntreatmentConcept.add(Context.getConceptService().getConceptByUuid(INTERRUPTION_IN_TREATMENT_UUID));
		
		List<Obs> obsList = Context.getObsService().getObservations(Collections.singletonList(patient), null,
		    interruptionIntreatmentConcept, null, null, null, null, null, null, null, endDate, false);
		
		return !obsList.isEmpty();
	}
	
	/**
	 * Handles the request to get a list of active patients within a specified date range. Active
	 * patients are determined based on an active Regimen.
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/activeClients")
	@ResponseBody
	public Object getActivePatients(HttpServletRequest request, @RequestParam("startDate") String qStartDate,
	        @RequestParam("endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory) throws ParseException {
		
		Date startDate = dateTimeFormatter.parse(qStartDate);
		Date endDate = dateTimeFormatter.parse(qEndDate);
		
		List<String> encounterTypeUuids = Arrays.asList(PERSONAL_FAMILY_HISTORY_ENCOUNTERTYPE_UUID,
		    FOLLOW_UP_FORM_ENCOUNTER_TYPE);
		
		List<Encounter> activeRegimenEncounters = getEncountersByEncounterTypes(encounterTypeUuids, startDate, endDate);
		
		HashSet<Patient> activePatients = activeRegimenEncounters.stream().map(Encounter::getPatient)
		        .collect(Collectors.toCollection(HashSet::new));
		
		List<Obs> regimenObs = Context.getObsService().getObservations(null, activeRegimenEncounters,
		    Collections.singletonList(Context.getConceptService().getConceptByUuid(ACTIVE_REGIMEN_CONCEPT_UUID)), null, null,
		    null, null, null, null, null, endDate, false);
		
		HashSet<Patient> activeClients = regimenObs.stream().filter(obs -> obs.getPerson() instanceof Patient)
		        .map(obs -> (Patient) obs.getPerson()).collect(Collectors.toCollection(HashSet::new));
		
		activePatients.addAll(activeClients);
		
		return generatePatientListObj(activePatients, endDate);
	}
	
	// Retrieves a list of encounters filtered by encounter types.
	private List<Encounter> getEncountersByEncounterTypes(List<String> encounterTypeUuids, Date startDate, Date endDate) {
		List<EncounterType> encounterTypes = encounterTypeUuids.stream()
		        .map(uuid -> Context.getEncounterService().getEncounterTypeByUuid(uuid)).collect(Collectors.toList());
		
		EncounterSearchCriteria encounterCriteria = new EncounterSearchCriteria(null, null, startDate, endDate, null, null,
		        encounterTypes, null, null, null, false);
		return Context.getEncounterService().getEncounters(encounterCriteria);
	}
	
	// Determine if Patient is Pregnant or Breastfeeding
	private static boolean determineIfPatientIsPregnantOrBreastfeeding(Patient patient, Date endDate) {
		
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
	
	// Retrieve the Last Refill Date from Patient Observation
	private static String getLastRefillDate(Patient patient, Date startDate, Date endDate) {
		Concept lastRefillDateConcept = Context.getConceptService().getConceptByUuid(LAST_REFILL_DATE_UUID);
		List<Obs> lastRefillDateObs = Context.getObsService().getObservations(Collections.singletonList(patient.getPerson()),
		    null, Collections.singletonList(lastRefillDateConcept), null, null, null, null, null, null, startDate, endDate,
		    false);
		
		if (lastRefillDateObs != null && !lastRefillDateObs.isEmpty()) {
			Obs lastObs = lastRefillDateObs.get(0);
			Date lastRefillDate = lastObs.getValueDate();
			if (lastRefillDate != null) {
				return dateTimeFormatter.format(lastRefillDate);
			}
		}
		return "";
	}
	
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/newClients")
	@ResponseBody
	public Object getNewPatients(HttpServletRequest request, @RequestParam("startDate") String qStartDate,
	        @RequestParam("endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory) throws ParseException {
		Date startDate = dateTimeFormatter.parse(qStartDate);
		Date endDate = dateTimeFormatter.parse(qEndDate);
		HashSet<Patient> enrolledPatients = getNewlyEnrolledPatients(startDate, endDate);
		return generatePatientListObj(enrolledPatients, endDate);
	}
	
	private HashSet<Patient> getNewlyEnrolledPatients(Date startDate, Date endDate) {
		// Get all patients who were enrolled within the specified date range
		EncounterType enrolmentEncounterType = Context.getEncounterService()
		        .getEncounterTypeByUuid(ENROLMENT_ENCOUNTER_TYPE_UUID);
		// Add a filter for current location
		EncounterSearchCriteria encounterSearchCriteria = new EncounterSearchCriteria(null, null, startDate, endDate, null,
		        null, Collections.singletonList(enrolmentEncounterType), null, null, null, false);
		List<Encounter> encounters = Context.getEncounterService().getEncounters(encounterSearchCriteria);
		// Extract patients from encounters into a hashset to remove duplicates
		HashSet<Patient> enrolledPatients = encounters.stream().map(Encounter::getPatient).collect(HashSet::new,
		    HashSet::add, HashSet::addAll);
		// Get Patients who were transferred in
		List<Obs> transferInObs = Context.getObsService().getObservations(null, encounters,
		    Collections.singletonList(Context.getConceptService().getConceptByUuid(TRANSFER_IN_CONCEPT_UUID)),
		    Collections.singletonList(Context.getConceptService().getConceptByUuid(YES_CONCEPT)), null, null, null, null,
		    null, null, endDate, false);
		// Extract patients from transfer in obs into a hashset to remove duplicates
		HashSet<Person> transferInPatients = transferInObs.stream().map(Obs::getPerson).collect(HashSet::new, HashSet::add,
		    HashSet::addAll);
		
		enrolledPatients.removeIf(transferInPatients::contains);
		
		return enrolledPatients;
		
	}
	
	// Determine Patient Enrollment Date From the Adult and Adolescent and Pediatric
	// Forms
	private static String determineEnrolmentDate(Patient patient, Date startDate, Date endDate) {
		Concept enrollmentDateConcept = Context.getConceptService().getConceptByUuid(DATE_OF_ENROLLMENT_UUID);
		List<Obs> enrollmentDateObs = Context.getObsService().getObservations(Collections.singletonList(patient.getPerson()),
		    null, Collections.singletonList(enrollmentDateConcept), null, null, null, null, null, null, startDate, endDate,
		    false);
		
		if (enrollmentDateObs != null && !enrollmentDateObs.isEmpty()) {
			Obs dateObs = enrollmentDateObs.get(0);
			Date enrollmentDate = dateObs.getValueDate();
			if (enrollmentDate != null) {
				return dateTimeFormatter.format(enrollmentDate);
			}
		}
		return "";
		
	}
	
	private Object generateViralLoadListObj(List<Patient> allPatients) {
		// The expected output for this method should resemble this JSON output
		// [{
		// "Jan"":[{""patient1""},{""patient2""}...],
		// "Feb"":[{""patient1""},{""patient2""}...],
		// "Mar"":[{""patient1""},{""patient2""}...],
		// "Apr"":[{""patient1""},{""patient2""}...],
		// "May"":[{""patient1""},{""patient2""}...],
		// "Jun"":[{""patient1""},{""patient2""}...],
		// }]"
		// }
		// ArrayNode patientList = JsonNodeFactory.instance.arrayNode();
		// ObjectNode allPatientsObj = JsonNodeFactory.instance.objectNode();
		
		return new ArrayList<>();
	}
	
	private static class SummarySection extends ObjectNode {
		
		private ObjectNode groupWeek;
		
		private ObjectNode groupMonth;
		
		private ObjectNode groupYear;
		
		public SummarySection(JsonNodeFactory nc) {
			super(nc);
		}
		
		public void setGroupYear(ObjectNode groupYear) {
			this.groupYear = groupYear;
		}
		
		public ObjectNode getGroupYear() {
			return groupYear;
		}
		
		public void setGroupMonth(ObjectNode groupMonth) {
			this.groupMonth = groupMonth;
		}
		
		public ObjectNode getGroupMonth() {
			return groupMonth;
		}
		
		public void setGroupWeek(ObjectNode groupWeek) {
			this.groupWeek = groupWeek;
		}
		
		public ObjectNode getGroupWeek() {
			return groupWeek;
		}
	}
}
