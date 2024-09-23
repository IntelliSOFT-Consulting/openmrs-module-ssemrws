package org.openmrs.module.ssemrws.web.constants;

import java.util.*;
import java.util.stream.Collectors;

public class GenerateSummary {
	
	public static Map<String, Map<String, Integer>> generateSummary(List<Date> dates) {
		String[] months = new String[] { "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov",
		        "Dec" };
		String[] days = new String[] { "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun" };
		
		Map<String, Integer> monthlySummary = new HashMap<>();
		Map<String, Integer> weeklySummary = new HashMap<>();
		Map<String, Integer> dailySummary = new HashMap<>();
		
		for (Date date : dates) {
			Calendar calendar = Calendar.getInstance();
			calendar.setTime(date);
			
			String month = months[calendar.get(Calendar.MONTH)];
			monthlySummary.put(month, monthlySummary.getOrDefault(month, 0) + 1);
			
			int week = calendar.get(Calendar.WEEK_OF_MONTH);
			String weekOfTheMonth = String.format("%s_Week%s", month, week);
			weeklySummary.put(weekOfTheMonth, weeklySummary.getOrDefault(weekOfTheMonth, 0) + 1);
			
			int day = calendar.get(Calendar.DAY_OF_WEEK);
			String dayInWeek = String.format("%s_%s", month, days[day - 1]);
			dailySummary.put(dayInWeek, dailySummary.getOrDefault(dayInWeek, 0) + 1);
		}
		
		// Sorting the summaries
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
		
		Map<String, Map<String, Integer>> summary = new HashMap<>();
		summary.put("groupYear", sortedMonthlySummary);
		summary.put("groupMonth", sortedWeeklySummary);
		summary.put("groupWeek", sortedDailySummary);
		
		return summary;
	}
}
