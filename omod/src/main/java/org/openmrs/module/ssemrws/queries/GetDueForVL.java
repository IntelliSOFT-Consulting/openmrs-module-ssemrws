package org.openmrs.module.ssemrws.queries;

import org.openmrs.Patient;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import static org.openmrs.module.ssemrws.web.constants.FetchPatientsByIdentifier.fetchPatientsByIds;

@Component
public class GetDueForVL {
	
	@PersistenceContext
	private EntityManager entityManager;
	
	private Object executeDueForVlQuery(Date startDate, Date endDate) {
		String baseQuery = "WITH LatestFP AS ( " + "    SELECT f.*, ROW_NUMBER() OVER(PARTITION BY f.client_id "
		        + "        ORDER BY " + "            CASE WHEN f.date_vl_sample_collected IS NOT NULL THEN 1 ELSE 2 END, "
		        + "            f.date_vl_sample_collected DESC, " + "            f.encounter_datetime DESC " + "    ) as rn "
		        + "    FROM ssemr_etl.ssemr_flat_encounter_hiv_care_follow_up f " + "), " + "LatestHVL AS ( "
		        + "    SELECT h.*, ROW_NUMBER() OVER(PARTITION BY h.client_id ORDER BY h.encounter_datetime DESC) as rn "
		        + "    FROM ssemr_etl.ssemr_flat_encounter_high_viral_load h " + "), "
		        
		        + "patients_with_pending_vl AS ( " + "  SELECT DISTINCT fp.client_id " + "  FROM LatestFP fp "
		        + "  LEFT JOIN LatestHVL hvl ON fp.client_id = hvl.client_id AND hvl.rn = 1 " + "  WHERE fp.rn = 1 "
		        + "  AND ((fp.date_vl_sample_collected IS NOT NULL AND fp.date_vl_results_received IS NULL) OR (hvl.repeat_vl_sample_date IS NOT NULL AND hvl.repeat_vl_result_date IS NULL))"
		        + "), "
		        
		        + "patients_pending_eac AS ( " + "  SELECT DISTINCT fp.client_id " + "  FROM LatestFP fp "
		        + "  LEFT JOIN LatestHVL hvl ON fp.client_id = hvl.client_id AND hvl.rn = 1 " + "  WHERE fp.rn = 1 "
		        + "  AND (fp.viral_load_value >= 1000 OR hvl.repeat_vl_value >= 1000) " + "), "
		        
		        + "eligibility_dates AS ( " + "  SELECT " + "    p.person_id AS client_id, " + "    (CASE "
		        + "WHEN hvl.encounter_datetime > fp.encounter_datetime THEN " + "CASE "
		        + "WHEN hvl.third_eac_session_date IS NOT NULL AND hvl.repeat_vl_results IS NULL THEN DATE_ADD(hvl.third_eac_session_date, INTERVAL 1 MONTH) "
		        + "WHEN hvl.repeat_vl_sample_date IS NOT NULL AND (hvl.repeat_vl_value < 1000 OR hvl.repeat_vl_results = 'Below Detectable (BDL)') THEN DATE_ADD(hvl.repeat_vl_sample_date, INTERVAL 6 MONTH) "
		        + "ELSE DATE_ADD(hvl.encounter_datetime, INTERVAL 1 MONTH) " + "END " + "ELSE " + "CASE "
		        + "WHEN (mp.age > 18 AND pfh.art_start_date IS NOT NULL AND (fp.client_pmtct = 'No' OR fp.client_pmtct IS NULL) AND (fp.viral_load_value < 1000 OR fp.vl_results = 'Below Detectable (BDL)') AND EXISTS (SELECT 1 FROM ssemr_etl.ssemr_flat_encounter_hiv_care_follow_up prev WHERE prev.client_id = fp.client_id AND prev.date_vl_sample_collected < fp.date_vl_sample_collected AND (prev.viral_load_value < 1000 OR prev.vl_results = 'Below Detectable (BDL)'))) THEN DATE_ADD(fp.date_vl_sample_collected, INTERVAL 12 MONTH) "
		        + "WHEN (mp.age > 18 AND pfh.art_start_date IS NOT NULL AND (fp.client_pmtct = 'No' OR fp.client_pmtct IS NULL) AND (fp.viral_load_value < 1000 OR fp.vl_results = 'Below Detectable (BDL)') AND NOT EXISTS (SELECT 1 FROM ssemr_etl.ssemr_flat_encounter_hiv_care_follow_up prev WHERE prev.client_id = fp.client_id AND prev.date_vl_sample_collected < fp.date_vl_sample_collected AND (prev.viral_load_value < 1000 OR prev.vl_results = 'Below Detectable (BDL)'))) THEN DATE_ADD(fp.date_vl_sample_collected, INTERVAL 6 MONTH) "
		        + "WHEN (mp.age > 18 AND pfh.art_start_date IS NOT NULL AND NOT EXISTS (SELECT 1 FROM ssemr_etl.ssemr_flat_encounter_hiv_care_follow_up v2 WHERE v2.client_id = fp.client_id AND v2.date_vl_sample_collected IS NOT NULL)) THEN DATE_ADD(pfh.art_start_date, INTERVAL 6 MONTH) "
		        + "WHEN hvl.third_eac_session_date IS NOT NULL AND (hvl.repeat_vl_results IS NULL) THEN DATE_ADD(hvl.third_eac_session_date, INTERVAL 1 MONTH) "
		        + "WHEN (mp.age <= 18 AND hvl.repeat_vl_sample_date IS NOT NULL) AND (fp.client_pmtct = 'No' OR fp.client_pmtct IS NULL) AND (hvl.repeat_vl_value IS NULL AND hvl.repeat_vl_results IS NULL) THEN DATE_ADD(hvl.third_eac_session_date, INTERVAL 1 MONTH) "
		        + "WHEN hvl.repeat_vl_sample_date IS NOT NULL AND (hvl.repeat_vl_value < 1000 OR hvl.repeat_vl_results = 'Below Detectable (BDL)') THEN DATE_ADD(hvl.repeat_vl_sample_date, INTERVAL 6 MONTH) "
		        + "WHEN (mp.age <= 18 AND pfh.art_start_date IS NOT NULL) AND (fp.client_pmtct = 'No' OR fp.client_pmtct IS NULL) AND (fp.date_vl_sample_collected IS NULL AND fp.vl_results IS NULL) AND hvl.repeat_vl_sample_date IS NULL THEN DATE_ADD(pfh.art_start_date, INTERVAL 6 MONTH) "
		        + "WHEN (mp.age <= 18 AND fp.date_vl_sample_collected IS NOT NULL) AND (fp.client_pmtct = 'No' OR fp.client_pmtct IS NULL) AND hvl.repeat_vl_sample_date IS NULL THEN DATE_ADD(fp.date_vl_sample_collected, INTERVAL 6 MONTH) "
		        + "WHEN (fp.client_pmtct = 'Yes' AND fp.date_vl_sample_collected IS NOT NULL) AND (fp.viral_load_value < 1000 OR fp.vl_results = 'Below Detectable (BDL)') THEN DATE_ADD(fp.date_vl_sample_collected, INTERVAL 3 MONTH) "
		        + "WHEN (fp.client_pmtct = 'Yes' AND fp.date_vl_sample_collected IS NULL) THEN DATE_ADD(fp.encounter_datetime, INTERVAL 3 MONTH) "
		        + "WHEN (fp.client_pregnant = 'Yes' AND pfh.art_start_date IS NOT NULL) THEN fp.encounter_datetime "
		        + "WHEN pfh.art_start_date IS NOT NULL AND (hvl.repeat_vl_result_date IS NULL AND fp.date_vl_sample_collected IS NULL) THEN DATE_ADD(pfh.art_start_date, INTERVAL 6 MONTH) "
		        + "ELSE NULL " + "END " + "    END) AS eligibility_date " + "  FROM ssemr_etl.mamba_dim_person p "
		        + "  JOIN ssemr_etl.mamba_dim_person mp ON p.person_id = mp.person_id "
		        + "  LEFT JOIN LatestFP fp ON p.person_id = fp.client_id AND fp.rn = 1 "
		        + "  LEFT JOIN LatestHVL hvl ON p.person_id = hvl.client_id AND hvl.rn = 1 "
		        + "  LEFT JOIN ssemr_etl.ssemr_flat_encounter_personal_family_tx_history pfh ON p.person_id = pfh.client_id "
		        + "  LEFT JOIN ssemr_etl.ssemr_flat_encounter_end_of_follow_up fup ON p.person_id = fup.client_id "
		        + "  LEFT JOIN ( " + "      SELECT app.patient_id, app.status, app.start_date_time "
		        + "      FROM openmrs.patient_appointment app " + "      JOIN ( "
		        + "          SELECT patient_id, MAX(start_date_time) AS max_start_date_time "
		        + "          FROM openmrs.patient_appointment GROUP BY patient_id "
		        + "      ) AS latest_appt ON app.patient_id = latest_appt.patient_id AND app.start_date_time = latest_appt.max_start_date_time "
		        + "  ) appt ON appt.patient_id = p.person_id " + "  WHERE pfh.art_start_date IS NOT NULL "
		        + "  AND (fup.death IS NULL OR fup.death != 'Yes') "
		        + "  AND (fup.transfer_out IS NULL OR fup.transfer_out != 'Yes') "
		        + "  AND (fup.client_refused_treatment IS NULL OR fup.client_refused_treatment != 'Yes') "
		        + "  AND (appt.status IS NULL OR appt.status != 'Missed' OR DATEDIFF(:endDate, appt.start_date_time) <= 28)"
		        + ") " + "SELECT ed.client_id " + "FROM eligibility_dates ed "
		        + "LEFT JOIN patients_with_pending_vl p1 ON ed.client_id = p1.client_id "
		        + "LEFT JOIN patients_pending_eac p2 ON ed.client_id = p2.client_id "
		        + "WHERE ed.eligibility_date BETWEEN :startDate AND :endDate " + "AND p1.client_id IS NULL "
		        + "AND p2.client_id IS NULL";
		try {
			Query query = entityManager.createNativeQuery(baseQuery).setParameter("startDate", startDate)
			        .setParameter("endDate", endDate);
			return query.getResultList();
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
	
	// Method to fetch the list of TxCurr patients
	public HashSet<Patient> getDueForVl(Date startDate, Date endDate) {
		List<Integer> patientIds = (List<Integer>) executeDueForVlQuery(startDate, endDate);
		return fetchPatientsByIds(patientIds);
	}
}
