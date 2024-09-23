package org.openmrs.module.ssemrws.web.constants;

import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.openmrs.module.ssemrws.constants.SharedConstants.*;
import static org.openmrs.module.ssemrws.web.constants.AllConcepts.*;

public class ViralLoadCascade {
	
	/**
	 * This method calculates the viral load cascade for the ART dashboard. It retrieves the necessary
	 * data from the database, calculates the viral load cascade, and returns the results in a JSON
	 * object format.
	 * 
	 * @param qStartDate The start date for the viral load cascade in the format "yyyy-MM-dd".
	 * @param qEndDate The end date for the viral load cascade in the format "yyyy-MM-dd".
	 * @param vlCascadeConceptUuids A list of UUIDs representing the concepts related to the viral load
	 *            cascade.
	 * @param eacSessionConceptUuid The UUID of the concept representing the EAC session.
	 * @return A JSON object containing the results of the viral load cascade.
	 * @throws ParseException If the start or end date cannot be parsed.
	 */
	public static Object getViralLoadCascade(String qStartDate, String qEndDate, List<String> vlCascadeConceptUuids,
	        String eacSessionConceptUuid) throws ParseException {
		
		SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
		Date[] dates = getStartAndEndDate(qStartDate, qEndDate, dateTimeFormatter);
		
		List<String> viralLoadCascadeEncounterTypeUuids = Arrays.asList(HIGH_VL_ENCOUNTERTYPE_UUID,
		    FOLLOW_UP_FORM_ENCOUNTER_TYPE);
		
		List<Encounter> viralLoadCascadeEncounters = getEncountersByEncounterTypes(viralLoadCascadeEncounterTypeUuids,
		    dates[0], dates[1]);
		
		List<Concept> viralLoadCascadeConcepts = getConceptsByUuids(vlCascadeConceptUuids);
		
		List<Obs> viralLoadCascadeObs = Context.getObsService().getObservations(null, viralLoadCascadeEncounters,
		    Collections.singletonList(Context.getConceptService().getConceptByUuid(eacSessionConceptUuid)),
		    viralLoadCascadeConcepts, null, null, null, null, null, dates[0], dates[1], false);
		
		Map<String, Integer> viralLoadCascadeCounts = new HashMap<>();
		
		Set<Patient> patientsWithHighViralLoad = getPatientsWithHighVL(dates[0], dates[1]);
		Set<Patient> patientsWithRepeatedVL = getPatientsWithRepeatedVL(dates[0], dates[1]);
		Set<Patient> patientsWithPersistentHighVL = getPatientsWithPersistentHighVL(dates[0], dates[1]);
		Set<Patient> patientsWithARTSwitch = getPatientsWithSwitchART(dates[0], dates[1]);
		Set<Patient> patientsWithSecondLineSwitch = getPatientsWithSecondLineSwitchART(dates[0], dates[1]);
		
		// Maps to track observation dates for each patient
		Map<Patient, Date> firstEACDates = new HashMap<>();
		Map<Patient, Date> secondEACDates = new HashMap<>();
		Map<Patient, Date> thirdEACDates = new HashMap<>();
		Map<Patient, Date> extendedEACDates = new HashMap<>();
		Map<Patient, Date> repeatVLCollectedDates = new HashMap<>();
		Map<Patient, Date> persistentHVLDates = new HashMap<>();
		Map<Patient, Date> artSwitchDates = new HashMap<>();
		Map<Patient, Date> artSwitchSecondLineDates = new HashMap<>();
		
		// Filter the observations to only include patients with high viral load
		for (Obs obs : viralLoadCascadeObs) {
			Concept viralLoadCascadeConcept = obs.getValueCoded();
			Patient patient = (Patient) obs.getPerson();
			if (viralLoadCascadeConcept != null && patientsWithHighViralLoad.contains(patient)) {
				String conceptName = viralLoadCascadeConcept.getName().getName();
				viralLoadCascadeCounts.put(conceptName, viralLoadCascadeCounts.getOrDefault(conceptName, 0) + 1);
				
				// Track the observation dates for each EAC session
				switch (conceptName) {
					case "First EAC Session":
						firstEACDates.put(patient, obs.getObsDatetime());
						break;
					case "Second EAC Session":
						secondEACDates.put(patient, obs.getObsDatetime());
						break;
					case "Third EAC Session":
						thirdEACDates.put(patient, obs.getObsDatetime());
						break;
					case "Extended EAC Session":
						extendedEACDates.put(patient, obs.getObsDatetime());
						break;
					case "Repeat Viral Load Collected":
						repeatVLCollectedDates.put(patient, obs.getObsDatetime());
						break;
					case "Persistent High Viral Load":
						persistentHVLDates.put(patient, obs.getObsDatetime());
						break;
					case "ART Switch":
						artSwitchDates.put(patient, obs.getObsDatetime());
						break;
					case "ART Switch (2nd Line)":
						artSwitchSecondLineDates.put(patient, obs.getObsDatetime());
						break;
					default:
						break;
				}
			}
		}
		
		// Calculate total turnaround time for each session
		double totalFirstToSecond = calculateTotalTurnaroundTime(firstEACDates, secondEACDates);
		double totalSecondToThird = calculateTotalTurnaroundTime(secondEACDates, thirdEACDates);
		double totalThirdToExtended = calculateTotalTurnaroundTime(thirdEACDates, extendedEACDates);
		double totalExtendedToRepeatVL = calculateTotalTurnaroundTime(extendedEACDates, repeatVLCollectedDates);
		
		// Calculate counts based on hierarchical structure
		int highViralLoadCount = patientsWithHighViralLoad.size();
		int firstEACCount = (int) firstEACDates.keySet().stream().filter(patientsWithHighViralLoad::contains).count();
		int secondEACCount = (int) secondEACDates.keySet().stream().filter(firstEACDates::containsKey).count();
		int thirdEACCount = (int) thirdEACDates.keySet().stream().filter(secondEACDates::containsKey).count();
		int extendedEACCount = (int) extendedEACDates.keySet().stream().filter(thirdEACDates::containsKey).count();
		int repeatVLCount = (int) repeatVLCollectedDates.keySet().stream().filter(extendedEACDates::containsKey).count();
		int persistentHighVLCount = (int) patientsWithPersistentHighVL.stream().filter(repeatVLCollectedDates::containsKey)
		        .count();
		int artSwitchCount = (int) patientsWithARTSwitch.stream().filter(persistentHVLDates::containsKey).count();
		int secondLineSwitchCount = (int) patientsWithSecondLineSwitch.stream().filter(artSwitchDates::containsKey).count();
		
		// Combine the results
		Map<String, Object> results = new LinkedHashMap<>();
		List<Map<String, Object>> viralLoadCascadeList = new ArrayList<>();
		
		// Add the entries in the desired order
		addCascadeEntry(viralLoadCascadeList, "HVL(≥1000 c/ml)", highViralLoadCount, highViralLoadCount,
		    calculateAverageTurnaroundTime(dates[0], dates[1], highViralLoadCount), true);
		addCascadeEntry(viralLoadCascadeList, "First EAC Session", firstEACCount, highViralLoadCount,
		    totalFirstToSecond / Math.max(firstEACCount, 1), false);
		addCascadeEntry(viralLoadCascadeList, "Second EAC Session", secondEACCount, firstEACCount,
		    totalSecondToThird / Math.max(secondEACCount, 1), false);
		addCascadeEntry(viralLoadCascadeList, "Third EAC Session", thirdEACCount, secondEACCount,
		    totalThirdToExtended / Math.max(thirdEACCount, 1), false);
		addCascadeEntry(viralLoadCascadeList, "Extended EAC Session", extendedEACCount, thirdEACCount,
		    totalExtendedToRepeatVL / Math.max(extendedEACCount, 1), false);
		addCascadeEntry(viralLoadCascadeList, "Repeat Viral Load Collected", repeatVLCount, extendedEACCount,
		    calculateAverageTurnaroundTime(dates[0], dates[1], repeatVLCount), false);
		addCascadeEntry(viralLoadCascadeList, "Persistent High Viral Load", persistentHighVLCount, repeatVLCount,
		    calculateAverageTurnaroundTime(dates[0], dates[1], persistentHighVLCount), false);
		addCascadeEntry(viralLoadCascadeList, "ART Switch", artSwitchCount, persistentHighVLCount,
		    calculateAverageTurnaroundTime(dates[0], dates[1], artSwitchCount), false);
		addCascadeEntry(viralLoadCascadeList, "ART Switch (2nd Line)", secondLineSwitchCount, artSwitchCount,
		    calculateAverageTurnaroundTime(dates[0], dates[1], secondLineSwitchCount), false);
		
		results.put("results", viralLoadCascadeList);
		return results;
	}
	
	private static void addCascadeEntry(List<Map<String, Object>> list, String text, int count, int previousCount,
	        double averageTurnaroundTime, boolean isBaseCount) {
		Map<String, Object> entry = new LinkedHashMap<>();
		entry.put("text", text);
		entry.put("total", count);
		entry.put("previousCount", previousCount);
		entry.put("percentage", isBaseCount ? 100.0 : (previousCount == 0 ? 0.0 : (count * 100.0 / previousCount)));
		entry.put("averageTurnaroundTimeMonths", averageTurnaroundTime);
		list.add(entry);
	}
	
	private static double calculateTotalTurnaroundTime(Map<Patient, Date> startDates, Map<Patient, Date> endDates) {
		double totalTurnaroundTime = 0.0;
		int count = 0;
		
		for (Map.Entry<Patient, Date> entry : startDates.entrySet()) {
			Patient patient = entry.getKey();
			Date startDate = entry.getValue();
			Date endDate = endDates.get(patient);
			
			if (endDate != null) {
				long timeDifference = endDate.getTime() - startDate.getTime();
				double monthsDifference = timeDifference / (1000.0 * 60 * 60 * 24 * 30);
				totalTurnaroundTime += monthsDifference;
				count++;
			}
		}
		
		return totalTurnaroundTime;
	}
	
	// Method to calculate average turnaround time for a given stage
	private static double calculateAverageTurnaroundTime(Date startDate, Date endDate, int count) {
		if (count == 0)
			return 0.0;
		double totalTime = (endDate.getTime() - startDate.getTime()) / (1000.0 * 60 * 60 * 24 * 30);
		return totalTime / count;
	}
}
