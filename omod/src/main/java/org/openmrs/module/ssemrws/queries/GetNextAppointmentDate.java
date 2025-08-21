package org.openmrs.module.ssemrws.queries;

import org.openmrs.Patient;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Date;
import java.util.List;

import static org.openmrs.module.ssemrws.constants.SharedConstants.dateTimeFormatter;

@Component
public class GetNextAppointmentDate {
	
	@PersistenceContext
	private EntityManager entityManager;
	
	public String getNextAppointmentDate(String patientUuid) {
		return getNextOrLastAppointmentDateByUuid(patientUuid);
	}
	
	public String getNextArtAppointmentDate(Patient patient) {
		return getNextOrLastAppointmentDateByUuid(patient.getUuid());
	}
	
	private String getNextOrLastAppointmentDateByUuid(String patientUuid) {
		if (patientUuid == null || patientUuid.trim().isEmpty()) {
			return "Invalid patient UUID";
		}
		
		if (entityManager == null) {
			throw new IllegalStateException("EntityManager is not initialized!");
		}
		
		Date now = new Date();
		
		// Query for the next upcoming appointment
		String futureQuery = "select fp.start_date_time " + "from openmrs.patient_appointment fp "
		        + "join openmrs.person p on fp.patient_id = p.person_id " + "where p.uuid = :patientUuid "
		        + "and fp.start_date_time >= :now " + "and fp.status <> 'Cancelled' " + "order by fp.start_date_time asc";
		
		List<Date> futureResults = entityManager.createNativeQuery(futureQuery).setParameter("patientUuid", patientUuid)
		        .setParameter("now", now).getResultList();
		
		if (futureResults != null && !futureResults.isEmpty()) {
			return dateTimeFormatter.format(futureResults.get(0));
		}
		
		// If no upcoming appointments, query for the most recent past appointment
		String pastAppointmentQuery = "select fp.start_date_time " + "from openmrs.patient_appointment fp "
		        + "join openmrs.person p on fp.patient_id = p.person_id " + "where p.uuid = :patientUuid "
		        + "and fp.start_date_time < :now " + "and fp.status NOT IN ('Missed', 'Cancelled') "
		        + "order by fp.start_date_time desc";
		
		List<Date> pastResults = entityManager.createNativeQuery(pastAppointmentQuery)
		        .setParameter("patientUuid", patientUuid).setParameter("now", now).getResultList();
		
		if (pastResults != null && !pastResults.isEmpty()) {
			return dateTimeFormatter.format(pastResults.get(0));
		}
		
		// Query for missed appointments
		String missedAppointmentQuery = "select fp.start_date_time " + "from openmrs.patient_appointment fp "
		        + "join openmrs.person p on fp.patient_id = p.person_id " + "where p.uuid = :patientUuid "
		        + "and fp.start_date_time < :now " + "and fp.status = 'Missed' " + "order by fp.start_date_time desc";
		
		List<Date> missedResults = entityManager.createNativeQuery(missedAppointmentQuery)
		        .setParameter("patientUuid", patientUuid).setParameter("now", now).getResultList();
		
		if (missedResults != null && !missedResults.isEmpty()) {
			return "Appointment Missed: " + dateTimeFormatter.format(missedResults.get(0));
		}
       // Query for cancelled appointments
		String cancelledAppointmentQuery = "select fp.start_date_time " + "from openmrs.patient_appointment fp "
		        + "join openmrs.person p on fp.patient_id = p.person_id " + "where p.uuid = :patientUuid "
		        + "and fp.status = 'Cancelled' " + "order by fp.start_date_time desc";
		
		List<Date> cancelledResults = entityManager.createNativeQuery(cancelledAppointmentQuery)
		        .setParameter("patientUuid", patientUuid).getResultList();
		
		if (cancelledResults != null && !cancelledResults.isEmpty()) {
			return "Appointment was cancelled";
		}
		
		return "No Appointments Found";
	}
	
	public String getNextAppointmentDateByUuid(String patientUuid) {
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
}
