package org.openmrs.module.ssemrws.web.constants;

import java.util.Date;

public class VlEligibilityResult {
	
	private final boolean isEligible;
	
	private final Date vlDueDate;
	
	public VlEligibilityResult(boolean isEligible, Date vlDueDate) {
		this.isEligible = isEligible;
		this.vlDueDate = vlDueDate;
	}
	
	public boolean isEligible() {
		return isEligible;
	}
	
	public Date getVlDueDate() {
		return vlDueDate;
	}
}
