package org.openmrs.module.ssemrws.web.dto;

import ca.uhn.hl7v2.model.v23.datatype.ST;

public class PatientObservations {
	
	private String enrollmentDate;
	
	private String lastRefillDate;
	
	private String dateOfinitiation;
	
	private String arvRegimen;
	
	private Double lastCD4Count;
	
	private String tbStatus;
	
	private String arvRegimenDose;
	
	private String whoClinicalStage;
	
	private String dateVLResultsReceived;
	
	private String chwName;
	
	private String chwPhone;
	
	private String chwAddress;
	
	private String vlResults;
	
	private String vlStatus;
	
	private Double bmi;
	
	private Double muac;
	
	private String appointmentDate;
	
	private String clinicianName;
	
	private String vlEligibility;
	
	private String vlDueDate;
	
	public String getEnrollmentDate() {
		return enrollmentDate;
	}
	
	public void setEnrollmentDate(String enrollmentDate) {
		this.enrollmentDate = enrollmentDate;
	}
	
	public String getLastRefillDate() {
		return lastRefillDate;
	}
	
	public void setLastRefillDate(String lastRefillDate) {
		this.lastRefillDate = lastRefillDate;
	}
	
	public String getDateOfInitiation() {
		return dateOfinitiation;
	}
	
	public void setDateOfinitiation(String dateOfinitiation) {
		this.dateOfinitiation = dateOfinitiation;
	}
	
	public String getArvRegimen() {
		return arvRegimen;
	}
	
	public void setArvRegimen(String arvRegimen) {
		this.arvRegimen = arvRegimen;
	}
	
	public Double getLastCD4Count() {
		return lastCD4Count;
	}
	
	public void setLastCD4Count(Double lastCD4Count) {
		this.lastCD4Count = lastCD4Count;
	}
	
	public String getTbStatus() {
		return tbStatus;
	}
	
	public void setTbStatus(String tbStatus) {
		this.tbStatus = tbStatus;
	}
	
	public String getArvRegimenDose() {
		return arvRegimenDose;
	}
	
	public void setArvRegimenDose(String arvRegimenDose) {
		this.arvRegimenDose = arvRegimenDose;
	}
	
	public String getWhoClinicalStage() {
		return whoClinicalStage;
	}
	
	public void setWhoClinicalStage(String whoClinicalStage) {
		this.whoClinicalStage = whoClinicalStage;
	}
	
	public String getDateVLResultsReceived() {
		return dateVLResultsReceived;
	}
	
	public void setDateVLResultsReceived(String dateVLResultsReceived) {
		this.dateVLResultsReceived = dateVLResultsReceived;
	}
	
	public String getChwName() {
		return chwName;
	}
	
	public void setChwName(String chwName) {
		this.chwName = chwName;
	}
	
	public String getChwPhone() {
		return chwPhone;
	}
	
	public void setChwPhone(String chwPhone) {
		this.chwPhone = chwPhone;
	}
	
	public String getChwAddress() {
		return chwAddress;
	}
	
	public void setChwAddress(String chwAddress) {
		this.chwAddress = chwAddress;
	}
	
	public String getVlResults() {
		return vlResults;
	}
	
	public void setVlResults(String vlResults) {
		this.vlResults = vlResults;
	}
	
	public String getVlStatus() {
		return vlStatus;
	}
	
	public void setVlStatus(String vlStatus) {
		this.vlStatus = vlStatus;
	}
	
	public Double getBmi() {
		return bmi;
	}
	
	public void setBmi(Double bmi) {
		this.bmi = bmi;
	}
	
	public Double getMuac() {
		return muac;
	}
	
	public void setMuac(Double muac) {
		this.muac = muac;
	}
	
	public String getAppointmentDate() {
		return appointmentDate;
	}
	
	public void setAppointmentDate(String appointmentDate) {
		this.appointmentDate = appointmentDate;
	}
	
	public String getClinicianName() {
		return clinicianName;
	}
	
	public void setClinicianName(String clinicianName) {
		this.clinicianName = clinicianName;
	}
	
	public String getVlEligibility() {
		return vlEligibility;
	}
	
	public void setVlEligibility(String vlEligibility) {
		this.vlEligibility = vlEligibility;
	}
	
	public String getVlDueDate() {
		return vlDueDate;
	}
	
	public void setVlDueDate(String vlDueDate) {
		this.vlDueDate = vlDueDate;
	}
}
