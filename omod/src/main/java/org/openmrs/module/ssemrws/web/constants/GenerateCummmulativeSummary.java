package org.openmrs.module.ssemrws.web.constants;

import java.util.*;
import java.util.stream.Collectors;

public class GenerateCummmulativeSummary {
	
	public static Map<String, Map<String, Integer>> generateCumulativeSummary(List<Date> dates) {
		String[] months = { "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec" };
		String[] days = { "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun" };
		
		Map<String, Integer> monthlySummary = new LinkedHashMap<>();
		Map<String, Integer> weeklySummary = new LinkedHashMap<>();
		Map<String, Integer> dailySummary = new LinkedHashMap<>();
		
		// Track cumulative totals
		int cumulativeMonthTotal = 0;
		Map<String, Integer> cumulativeWeekTotals = new LinkedHashMap<>();
		Map<String, Integer> cumulativeDayTotals = new LinkedHashMap<>();
		
		// Iterate through all dates and build cumulative summary
		for (Date date : dates) {
			Calendar calendar = Calendar.getInstance();
			calendar.setTime(date);
			
			// Get month and week of the month
			String month = months[calendar.get(Calendar.MONTH)];
			int week = calendar.get(Calendar.WEEK_OF_MONTH);
			String weekOfTheMonth = String.format("%s_Week%d", month, week);
			
			// Get day of the week
			int day = calendar.get(Calendar.DAY_OF_WEEK);
			String dayInWeek = String.format("%s_%s", month, days[day - 1]);
			
			// Accumulate monthly values
			cumulativeMonthTotal += 1;
			monthlySummary.put(month, cumulativeMonthTotal);
			
			// Accumulate weekly values
			cumulativeWeekTotals.put(weekOfTheMonth, cumulativeWeekTotals.getOrDefault(weekOfTheMonth, 0) + 1);
			weeklySummary.put(weekOfTheMonth, cumulativeWeekTotals.values().stream().reduce(0, Integer::sum));
			
			// Accumulate daily values
			cumulativeDayTotals.put(dayInWeek, cumulativeDayTotals.getOrDefault(dayInWeek, 0) + 1);
			dailySummary.put(dayInWeek, cumulativeDayTotals.values().stream().reduce(0, Integer::sum));
		}
		
		// Sorting the summaries based on predefined orders
		Map<String, Integer> sortedMonthlySummary = monthlySummary.entrySet().stream()
		        .sorted(Comparator.comparingInt(e -> Arrays.asList(months).indexOf(e.getKey())))
		        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
		
		Map<String, Integer> sortedWeeklySummary = weeklySummary.entrySet().stream().sorted((e1, e2) -> {
			String[] parts1 = e1.getKey().split("_Week");
			String[] parts2 = e2.getKey().split("_Week");
			int monthCompare = Arrays.asList(months).indexOf(parts1[0]) - Arrays.asList(months).indexOf(parts2[0]);
			if (monthCompare != 0) {
				return monthCompare;
			} else {
				return Integer.parseInt(parts1[1]) - Integer.parseInt(parts2[1]);
			}
		}).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
		
		Map<String, Integer> sortedDailySummary = dailySummary.entrySet().stream().sorted((e1, e2) -> {
			String[] parts1 = e1.getKey().split("_");
			String[] parts2 = e2.getKey().split("_");
			int monthCompare = Arrays.asList(months).indexOf(parts1[0]) - Arrays.asList(months).indexOf(parts2[0]);
			if (monthCompare != 0) {
				return monthCompare;
			} else {
				return Arrays.asList(days).indexOf(parts1[1]) - Arrays.asList(days).indexOf(parts2[1]);
			}
		}).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
		
		// Return cumulative summaries for year, month, and week
		Map<String, Map<String, Integer>> summary = new HashMap<>();
		summary.put("groupYear", sortedMonthlySummary);
		summary.put("groupMonth", sortedWeeklySummary);
		summary.put("groupWeek", sortedDailySummary);
		
		return summary;
	}
}
