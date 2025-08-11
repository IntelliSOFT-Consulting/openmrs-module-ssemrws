package org.openmrs.module.ssemrws.queries;

import org.openmrs.Patient;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Component
public class GetVLDueDate {
	
	@PersistenceContext
	private EntityManager entityManager;
	
	/**
	 * Checks if the patient's most recent follow-up encounter has a collected VL sample but is still
	 * awaiting results.
	 * 
	 * @param patient The patient to check.
	 * @return True if a VL result is pending, otherwise false.
	 */
	private boolean isPatientVLPending(Patient patient) {
		String pendingCheckQuery = "SELECT 1 " + "FROM ssemr_etl.ssemr_flat_encounter_hiv_care_follow_up fp "
		        + "LEFT JOIN ssemr_etl.ssemr_flat_encounter_high_viral_load hvl ON fp.client_id = hvl.client_id "
		        + "WHERE fp.client_id = :patientId " + "AND fp.encounter_datetime = ( "
		        + "    SELECT MAX(f.encounter_datetime) " + "    FROM ssemr_etl.ssemr_flat_encounter_hiv_care_follow_up f "
		        + "    WHERE f.client_id = fp.client_id " + ") " + "AND ( "
		        + "    (fp.date_vl_sample_collected IS NOT NULL AND fp.date_vl_results_received IS NULL) " + "    OR "
		        + "    (hvl.repeat_vl_sample_date IS NOT NULL AND hvl.repeat_vl_result_date IS NULL) " + ") " + "LIMIT 1";
		
		try {
			Query query = entityManager.createNativeQuery(pendingCheckQuery).setParameter("patientId",
			    patient.getPatientId());
			return !query.getResultList().isEmpty();
		}
		catch (Exception e) {
			System.err.println("Error checking for pending VL status: " + e.getMessage());
			return false;
		}
	}
	
	/**
	 * Checks if the patient's latest viral load result is high (>= 1000) AND they have not yet
	 * completed their third EAC session.
	 * 
	 * @param patient The patient to check.
	 * @return True if the patient meets the HVL criteria, otherwise false.
	 */
	private boolean isPatientInHVLCohort(Patient patient) {
		String hvlCheckQuery = "WITH LatestHVL AS ( " + "    SELECT " + "        hvl.*, "
		        + "        ROW_NUMBER() OVER(PARTITION BY hvl.client_id ORDER BY hvl.encounter_datetime DESC) as rn "
		        + "    FROM ssemr_etl.ssemr_flat_encounter_high_viral_load hvl " + "    WHERE hvl.client_id = :patientId "
		        + ") " + "SELECT 1 " + "FROM ssemr_etl.ssemr_flat_encounter_hiv_care_follow_up fp "
		        + "LEFT JOIN LatestHVL hvl ON fp.client_id = hvl.client_id AND hvl.rn = 1 "
		        + "WHERE fp.client_id = :patientId " + "AND fp.encounter_datetime = ( "
		        + "    SELECT MAX(f.encounter_datetime) " + "    FROM ssemr_etl.ssemr_flat_encounter_hiv_care_follow_up f "
		        + "    WHERE f.client_id = fp.client_id " + ") " + "AND hvl.third_eac_session_date IS NULL "
		        + "AND (fp.viral_load_value >= 1000 OR hvl.repeat_vl_value >= 1000) " + "LIMIT 1";
		
		try {
			Query query = entityManager.createNativeQuery(hvlCheckQuery).setParameter("patientId", patient.getPatientId());
			return !query.getResultList().isEmpty();
		}
		catch (Exception e) {
			System.err.println("Error checking for HVL cohort status: " + e.getMessage());
			return false;
		}
	}
	
	public String getVLDueDate(Patient patient) {
		if (isPatientVLPending(patient)) {
			return "Pending Results";
		}
		if (isPatientInHVLCohort(patient)) {
			return "Pending EAC 3";
		}
		String query = "WITH LatestFP AS ( " + "    SELECT f.*, ROW_NUMBER() OVER(PARTITION BY f.client_id "
		        + "        ORDER BY " + "            CASE WHEN f.date_vl_sample_collected IS NOT NULL THEN 1 ELSE 2 END, "
		        + "            f.date_vl_sample_collected DESC, " + "            f.encounter_datetime DESC " + "    ) as rn "
		        + "    FROM ssemr_etl.ssemr_flat_encounter_hiv_care_follow_up f WHERE f.client_id = :patientId " + "), "
		        + "LatestHVL AS ( "
		        + "    SELECT h.*, ROW_NUMBER() OVER(PARTITION BY h.client_id ORDER BY h.encounter_datetime DESC) as rn "
		        + "    FROM ssemr_etl.ssemr_flat_encounter_high_viral_load h WHERE h.client_id = :patientId " + ") "
		        + "SELECT DISTINCT p.person_id as client_id, " + "DATE_FORMAT(CASE "
		        
		        + "WHEN hvl.encounter_datetime > fp.encounter_datetime THEN " + "CASE "
		        + "WHEN hvl.third_eac_session_date IS NOT NULL AND hvl.repeat_vl_results IS NULL THEN DATE_ADD(hvl.third_eac_session_date, INTERVAL 1 MONTH) "
		        + "WHEN hvl.repeat_vl_sample_date IS NOT NULL AND (hvl.repeat_vl_value < 1000 OR hvl.repeat_vl_results = 'Below Detectable (BDL)') THEN DATE_ADD(hvl.repeat_vl_sample_date, INTERVAL 6 MONTH) "
		        + "ELSE DATE_ADD(hvl.encounter_datetime, INTERVAL 1 MONTH) " + "END "
		        
		        + "ELSE " + "CASE "
				// --- ADULT CONDITIONS ---
		        + "WHEN (mp.age > 18 AND pfh.art_start_date IS NOT NULL AND (fp.client_pmtct = 'No' OR fp.client_pmtct IS NULL) "
		        + " AND (fp.viral_load_value < 1000 OR fp.vl_results = 'Below Detectable (BDL)') "
		        + " AND EXISTS (SELECT 1 FROM ssemr_etl.ssemr_flat_encounter_hiv_care_follow_up prev "
		        + "     WHERE prev.client_id = fp.client_id AND prev.date_vl_sample_collected < fp.date_vl_sample_collected "
		        + "     AND (prev.viral_load_value < 1000 OR prev.vl_results = 'Below Detectable (BDL)')) "
		        + ") THEN DATE_ADD(fp.date_vl_sample_collected, INTERVAL 12 MONTH) "
				
		        + "WHEN (mp.age > 18 AND pfh.art_start_date IS NOT NULL AND (fp.client_pmtct = 'No' OR fp.client_pmtct IS NULL) "
		        + " AND (fp.viral_load_value < 1000 OR fp.vl_results = 'Below Detectable (BDL)') "
		        + " AND NOT EXISTS (SELECT 1 FROM ssemr_etl.ssemr_flat_encounter_hiv_care_follow_up prev "
		        + "     WHERE prev.client_id = fp.client_id AND prev.date_vl_sample_collected < fp.date_vl_sample_collected "
		        + "     AND (prev.viral_load_value < 1000 OR prev.vl_results = 'Below Detectable (BDL)')) "
		        + ") THEN DATE_ADD(fp.date_vl_sample_collected, INTERVAL 6 MONTH) "
				
		        + "WHEN (mp.age > 18 AND pfh.art_start_date IS NOT NULL "
		        + " AND NOT EXISTS (SELECT 1 FROM ssemr_etl.ssemr_flat_encounter_hiv_care_follow_up v2 "
		        + "     WHERE v2.client_id = fp.client_id AND v2.date_vl_sample_collected IS NOT NULL) "
		        + ") THEN DATE_ADD(pfh.art_start_date, INTERVAL 6 MONTH) "
				
				// --- CHILD & HVL-SPECIFIC CONDITIONS (Most specific first) ---
		        + "WHEN (mp.age <= 18 AND hvl.repeat_vl_sample_date IS NOT NULL) AND (fp.client_pmtct = 'No' OR fp.client_pmtct IS NULL) AND (hvl.repeat_vl_value IS NULL AND hvl.repeat_vl_results IS NULL) THEN DATE_ADD(hvl.third_eac_session_date, INTERVAL 1 MONTH) "
		        + "WHEN (mp.age <= 18 AND pfh.art_start_date IS NOT NULL) AND (fp.client_pmtct = 'No' OR fp.client_pmtct IS NULL) AND (fp.date_vl_sample_collected IS NULL AND fp.vl_results IS NULL) AND hvl.repeat_vl_sample_date IS NULL THEN DATE_ADD(pfh.art_start_date, INTERVAL 6 MONTH) "
		        + "WHEN (mp.age <= 18 AND fp.date_vl_sample_collected IS NOT NULL) AND (fp.client_pmtct = 'No' OR fp.client_pmtct IS NULL) AND hvl.repeat_vl_sample_date IS NULL THEN DATE_ADD(fp.date_vl_sample_collected, INTERVAL 6 MONTH) "
				
		        + "WHEN hvl.third_eac_session_date IS NOT NULL AND (hvl.repeat_vl_results IS NULL) THEN DATE_ADD(hvl.third_eac_session_date, INTERVAL 1 MONTH) "
		        + "WHEN hvl.repeat_vl_sample_date IS NOT NULL AND (hvl.repeat_vl_value < 1000 OR hvl.repeat_vl_results = 'Below Detectable (BDL)') THEN DATE_ADD(hvl.repeat_vl_sample_date, INTERVAL 6 MONTH) "
				
				// --- PMTCT / PREGNANT & OTHER GENERAL CONDITIONS ---
		        + "WHEN (fp.client_pmtct = 'Yes' AND fp.date_vl_sample_collected IS NOT NULL) AND (fp.viral_load_value < 1000 OR fp.vl_results = 'Below Detectable (BDL)') THEN DATE_ADD(fp.date_vl_sample_collected, INTERVAL 3 MONTH) "
		        + "WHEN (fp.client_pmtct = 'Yes' AND fp.date_vl_sample_collected IS NULL) THEN DATE_ADD(fp.encounter_datetime, INTERVAL 3 MONTH) "
		        + "WHEN (fp.client_pregnant = 'Yes' AND pfh.art_start_date IS NOT NULL) THEN fp.encounter_datetime "
				
				// Eligibity in 6 months from the ART START DAte
		        + "WHEN pfh.art_start_date IS NOT NULL AND (hvl.repeat_vl_result_date IS NULL AND fp.date_vl_sample_collected IS NULL) THEN DATE_ADD(pfh.art_start_date, INTERVAL 6 MONTH) "
				
		        + "ELSE NULL " + "END " + "END, '%d-%m-%Y') AS eligibility_date " + "FROM ssemr_etl.mamba_dim_person p "
		        + "LEFT JOIN LatestFP fp ON p.person_id = fp.client_id AND fp.rn = 1 "
		        + "LEFT JOIN LatestHVL hvl ON p.person_id = hvl.client_id AND hvl.rn = 1 "
		        + "LEFT JOIN ssemr_etl.ssemr_flat_encounter_personal_family_tx_history pfh ON p.person_id = pfh.client_id "
		        + "JOIN ssemr_etl.mamba_dim_person mp ON p.person_id = mp.person_id "
		        + "WHERE p.person_id = :patientId AND pfh.art_start_date IS NOT NULL " + "LIMIT 1";
				
		try {
			Query nativeQuery = entityManager.createNativeQuery(query).setParameter("patientId", patient.getPatientId());
			List<Object[]> results = nativeQuery.getResultList();
			if (results != null && !results.isEmpty()) {
				Object[] firstResult = results.get(0);
				return (firstResult[1] != null) ? firstResult[1].toString() : "N/A";
			}
		}
		catch (Exception e) {
			System.err.println("Error calculating VL due date: " + e.getMessage());
		}
		
		return "N/A";
	}
}
