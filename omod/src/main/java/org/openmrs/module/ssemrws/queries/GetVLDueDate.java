package org.openmrs.module.ssemrws.queries;

import org.openmrs.Patient;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Component
public class GetVLDueDate {
	
	@PersistenceContext
	private EntityManager entityManager;
	
	// Query to fetch the VL due date for a specific patient
	public String getVLDueDate(Patient patient) {
		String query = "SELECT client_id, DATE_FORMAT(MAX(eligibility_date), '%d-%m-%Y') AS max_due_date FROM ("
		        + "SELECT fp.client_id, " + "CASE " +
				// Adults suppressed
		        "WHEN (mp.age > 18 AND pfh.art_start_date IS NOT NULL AND fp.client_pmtct = 'No' "
		        + " AND (fp.viral_load_value < 1000 OR fp.vl_results = 'Below Detectable (BDL)') "
		        + " AND EXISTS (SELECT 1 FROM ssemr_etl.ssemr_flat_encounter_hiv_care_follow_up prev "
		        + "     WHERE prev.client_id = fp.client_id AND prev.date_vl_sample_collected < fp.date_vl_sample_collected "
		        + "     AND (prev.viral_load_value < 1000 OR prev.vl_results = 'Below Detectable (BDL)')) "
		        + ") THEN DATE_ADD(fp.date_vl_sample_collected, INTERVAL 12 MONTH) " +
				
		        "WHEN (mp.age > 18 AND pfh.art_start_date IS NOT NULL AND fp.client_pmtct = 'No' "
		        + " AND (fp.viral_load_value < 1000 OR fp.vl_results = 'Below Detectable (BDL)') "
		        + " AND NOT EXISTS (SELECT 1 FROM ssemr_etl.ssemr_flat_encounter_hiv_care_follow_up prev "
		        + "     WHERE prev.client_id = fp.client_id AND prev.date_vl_sample_collected < fp.date_vl_sample_collected "
		        + "     AND (prev.viral_load_value < 1000 OR prev.vl_results = 'Below Detectable (BDL)')) "
		        + ") THEN DATE_ADD(fp.date_vl_sample_collected, INTERVAL 6 MONTH) " +
				
				// Adults newly on ART
		        "WHEN (mp.age > 18 AND pfh.art_start_date IS NOT NULL "
		        + " AND NOT EXISTS (SELECT 1 FROM ssemr_etl.ssemr_flat_encounter_hiv_care_follow_up v2 "
		        + "     WHERE v2.client_id = fp.client_id AND v2.date_vl_sample_collected IS NOT NULL)) "
		        + "THEN DATE_ADD(pfh.art_start_date, INTERVAL 6 MONTH) " +
				
				// Children
		        "WHEN (mp.age <= 18 AND pfh.art_start_date IS NOT NULL) THEN DATE_ADD(pfh.art_start_date, INTERVAL 6 MONTH) "
		        + "WHEN (mp.age <= 18 AND vlr.date_of_sample_collection IS NOT NULL) THEN DATE_ADD(vlr.date_of_sample_collection, INTERVAL 6 MONTH) "
		        +
				
				// Pregnant PMTCT
		        "WHEN (fp.client_pmtct = 'Yes' AND vlr.date_of_sample_collection IS NOT NULL) THEN DATE_ADD(vlr.date_of_sample_collection, INTERVAL 3 MONTH) "
		        + "WHEN (fp.client_pmtct = 'Yes' AND vlr.date_of_sample_collection IS NULL) THEN DATE_ADD(fp.encounter_datetime, INTERVAL 3 MONTH) "
		        +
				
				// Pregnant general
		        "WHEN (fp.client_pregnant = 'Yes' AND pfh.art_start_date IS NOT NULL) THEN fp.encounter_datetime " +
				
				// EAC
		        "WHEN (hvl.third_eac_session_date IS NOT NULL) THEN DATE_ADD(hvl.third_eac_session_date, INTERVAL 1 MONTH) "
		        +
				
				// Fallback
		        "WHEN pfh.art_start_date IS NOT NULL THEN DATE_ADD(pfh.art_start_date, INTERVAL 6 MONTH) " +
				
		        "ELSE NULL END AS eligibility_date " + "FROM ssemr_etl.ssemr_flat_encounter_hiv_care_follow_up fp "
		        + "LEFT JOIN ssemr_etl.ssemr_flat_encounter_hiv_care_enrolment en ON en.client_id = fp.client_id "
		        + "LEFT JOIN ssemr_etl.ssemr_flat_encounter_vl_laboratory_request vlr ON vlr.client_id = fp.client_id "
		        + "LEFT JOIN ssemr_etl.mamba_dim_person mp ON mp.person_id = fp.client_id "
		        + "LEFT JOIN ssemr_etl.ssemr_flat_encounter_personal_family_tx_history pfh ON pfh.client_id = fp.client_id "
		        + "LEFT JOIN ssemr_etl.ssemr_flat_encounter_high_viral_load hvl ON hvl.client_id = fp.client_id "
		        + "WHERE fp.client_id = :patientId AND DATE(fp.encounter_datetime) <= CURRENT_DATE "
		        + ") AS t GROUP BY client_id";
				
		// Execute the query with the patient ID as a parameter
		List<Object[]> results = entityManager.createNativeQuery(query).setParameter("patientId", patient.getPatientId())
		        .getResultList();
		
		// Return the next VL due date if found, otherwise return null
		if (!results.isEmpty() && results.get(0) != null) {
			Object[] resultRow = results.get(0);
			return (String) resultRow[1];
		}
		
		return null;
	}
}
