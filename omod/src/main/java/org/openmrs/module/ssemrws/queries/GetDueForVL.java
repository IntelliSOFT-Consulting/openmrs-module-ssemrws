package org.openmrs.module.ssemrws.queries;

import org.openmrs.Patient;
import org.openmrs.module.ssemrws.web.constants.FetchPatientsByIdentifier;
import org.openmrs.module.ssemrws.web.constants.VlEligibilityResult;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
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
		String selectClause = "distinct fp.client_id";
		String baseQuery = "select " + selectClause + " from ssemr_etl.ssemr_flat_encounter_hiv_care_follow_up fp "
		        + "join ssemr_etl.mamba_dim_person mp on fp.client_id = mp.person_id "
		        + "left join ssemr_etl.ssemr_flat_encounter_hiv_care_enrolment en ON en.client_id = fp.client_id "
		        + "left join ssemr_etl.ssemr_flat_encounter_personal_family_tx_history pfh on pfh.client_id = fp.client_id "
		        + "left join ssemr_etl.ssemr_flat_encounter_vl_laboratory_request vlr on vlr.client_id = fp.client_id "
		        + "left join ssemr_etl.ssemr_flat_encounter_high_viral_load hvl on hvl.client_id = fp.client_id " + "where ("
				// Criteria 1: Adults, ART > 6 months, VL suppressed (<1000), not pmtct,
				// next due VL in 6 months or 12 months
		        + "(mp.age > 18 " + " AND pfh.art_start_date IS NOT NULL "
		        + " AND TIMESTAMPDIFF(MONTH, pfh.art_start_date, :endDate) >= 6 " + " AND fp.client_pmtct = 'No' "
		        + " AND (fp.viral_load_value < 1000 OR fp.vl_results = 'Below Detectable (BDL)') " + " AND (" + "     ("
		        + "         EXISTS (" + "             SELECT 1 FROM ssemr_etl.ssemr_flat_encounter_hiv_care_follow_up prev "
		        + "             WHERE prev.client_id = fp.client_id "
		        + "             AND prev.date_vl_sample_collected < fp.date_vl_sample_collected "
		        + "             AND (prev.viral_load_value < 1000 OR prev.vl_results = 'Below Detectable (BDL)') "
		        + "         ) " + "         AND TIMESTAMPDIFF(MONTH, fp.date_vl_sample_collected, :endDate) >= 12 "
		        + "     ) " + "     OR (" + "         NOT EXISTS ("
		        + "             SELECT 1 FROM ssemr_etl.ssemr_flat_encounter_hiv_care_follow_up prev "
		        + "             WHERE prev.client_id = fp.client_id "
		        + "             AND prev.date_vl_sample_collected < fp.date_vl_sample_collected "
		        + "             AND (prev.viral_load_value < 1000 OR prev.vl_results = 'Below Detectable (BDL)') "
		        + "         ) " + "         AND TIMESTAMPDIFF(MONTH, fp.date_vl_sample_collected, :endDate) >= 6 "
		        + "     ) " + " )" + ")"
				// Criteria 2: Adults newly on ART, no VL test yet, due 6 months from ART start
				// date
		        + "or (mp.age > 18 AND pfh.art_start_date IS NOT NULL " + " AND NOT EXISTS ( "
		        + "     SELECT 1 FROM ssemr_etl.ssemr_flat_encounter_hiv_care_follow_up v2 "
		        + "     WHERE v2.client_id = fp.client_id AND v2.date_vl_sample_collected IS NOT NULL " + " ) "
		        + " AND TIMESTAMPDIFF(MONTH, pfh.art_start_date, :endDate) >= 6 " + ")"
				// Criteria 3: Child/Adolescent up to 18 years old, next VL in 6 months, join
				// criteria 1 at age 19
				// If no sample is collected use the art_start_date to determine the next
				// eligibility (which should be 6 months)
		        + "or (mp.age <= 18 " + " AND pfh.art_start_date is not null " + " AND ( "
		        + "     (TIMESTAMPDIFF(MONTH, pfh.art_start_date, :endDate) >= 6) " + "     OR " + "     ( "
		        + "         vlr.date_of_sample_collection is not null "
		        + "         AND TIMESTAMPDIFF(MONTH, vlr.date_of_sample_collection, :endDate) >= 6 " + "     ) " + " ) ) "
				// Criteria 4: Pregnant women newly enrolled on ART, due for VL every 3 months
				// while in PMTCT
				// If the sample is collected use sample collection date to determine the next
				// eligibility which is 3 months,
				// If sample collection is null on all encounters eligibity will be 3 months
				// from date of enrollment.
		        + "or (fp.client_pmtct = 'Yes' " + " AND pfh.art_start_date is not null " + " AND ( "
		        + "     (vlr.date_of_sample_collection is not null "
		        + "      AND TIMESTAMPDIFF(MONTH, vlr.date_of_sample_collection, :endDate) >= 3) " + "     OR "
		        + "     (vlr.date_of_sample_collection is null "
		        + "      AND TIMESTAMPDIFF(MONTH, fp.encounter_datetime, :endDate) >= 3) " + " ) ) "
				// Criteria 5: Pregnant woman already on ART, eligible immediately after
				// discovering pregnancy
				// Use the encounter date as the eligibility of when the button was checked and
				// the client should be on ART
		        + "or (fp.client_pregnant = 'Yes' " + " AND pfh.art_start_date is not null "
		        + " AND TIMESTAMPDIFF(MONTH, pfh.art_start_date, :endDate) >= 6 "
		        + " AND fp.encounter_datetime <= :endDate) "
				// Criteria 6: Post EAC 3, eligible for VL in 1 month
				// This is for the Unsuppressed clients, eligible after 1 month if the results
				// are still Unsuppressed. Eligible after Third EAC Date
		        + "or (hvl.third_eac_session_date is not null "
		        + " AND TIMESTAMPDIFF(MONTH, hvl.third_eac_session_date, :endDate) >= 1) " + ") "
		        + "and fp.encounter_datetime <= :endDate";
		try {
			Query query = entityManager.createNativeQuery(baseQuery).setParameter("endDate", endDate);
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
