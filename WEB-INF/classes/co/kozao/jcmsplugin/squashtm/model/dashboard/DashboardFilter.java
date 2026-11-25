package co.kozao.jcmsplugin.squashtm.model.dashboard;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import co.kozao.jcmsplugin.squashtm.util.SquashTmConstants;

/**
 * @author Severin Kengne
 */
public class DashboardFilter {

	public static final String SCOPE_ALL = "ALL";
	public static final String SCOPE_REQUIREMENTS = "REQUIREMENTS";
	public static final String SCOPE_TEST_CASES = "TEST_CASES";
	public static final String SCOPE_CAMPAIGNS = "CAMPAIGNS";
	public static final String SCOPE_REPORTING = "REPORTING";

	public static final String DATE_CREATED = "CREATED";
	public static final String DATE_MODIFIED = "MODIFIED";

	public static final String GROUP_AUTO = "AUTO";
	public static final String GROUP_DAY = "DAY";
	public static final String GROUP_WEEK = "WEEK";
	public static final String GROUP_MONTH = "MONTH";

	public static final String COVERAGE_ALL = "ALL";
	public static final String COVERAGE_COVERED = "COVERED";
	public static final String COVERAGE_UNCOVERED = "UNCOVERED";

	private static final int DEFAULT_LIMIT = 100;
	private static final int MAX_LIMIT = 500;

	private final Set<Long> projectIds = new LinkedHashSet<Long>();
	private final Set<String> authors = new LinkedHashSet<String>();

	private final Set<String> requirementStatuses = new LinkedHashSet<String>();
	private final Set<String> criticalities = new LinkedHashSet<String>();
	private final Set<String> categories = new LinkedHashSet<String>();

	private final Set<String> testCaseStatuses = new LinkedHashSet<String>();
	private final Set<String> importances = new LinkedHashSet<String>();
	private final Set<String> natures = new LinkedHashSet<String>();
	private final Set<String> types = new LinkedHashSet<String>();

	private final Set<String> campaignStatuses = new LinkedHashSet<String>();
	private final Set<String> executionStatuses = new LinkedHashSet<String>();

	private final Set<String> reportTypes = new LinkedHashSet<String>();

	private String scope = SCOPE_ALL;
	private String dateField = DATE_CREATED;
	private Long dateFrom;
	private Long dateTo;
	private String search = "";
	private String path = "";
	private String requirementCoverage = COVERAGE_ALL;
	private String testCaseCoverage = COVERAGE_ALL;
	private String automatable = "ALL";
	private String groupBy = GROUP_AUTO;
	private int limit = DEFAULT_LIMIT;
	private boolean refresh;

	/**
	 * Construit les filtres depuis la requête HTTP.
	 */
	public static DashboardFilter fromRequest(HttpServletRequest request) {

		DashboardFilter f = new DashboardFilter();

		for (String value : csv(request.getParameter("projects"))) {
			try {
				f.projectIds.add(Long.valueOf(value));
			} catch (NumberFormatException e) {
			}
		}

		f.scope = pick(request.getParameter("scope"), SCOPE_ALL,
				new String[] { SCOPE_ALL, SCOPE_REQUIREMENTS, SCOPE_TEST_CASES, SCOPE_CAMPAIGNS, SCOPE_REPORTING });
		f.dateField = pick(request.getParameter("dateField"), DATE_CREATED, new String[] { DATE_CREATED, DATE_MODIFIED });
		f.groupBy = pick(request.getParameter("groupBy"), GROUP_AUTO,
				new String[] { GROUP_AUTO, GROUP_DAY, GROUP_WEEK, GROUP_MONTH });
		f.requirementCoverage = pick(request.getParameter("reqCoverage"), COVERAGE_ALL,
				new String[] { COVERAGE_ALL, COVERAGE_COVERED, COVERAGE_UNCOVERED });
		f.testCaseCoverage = pick(request.getParameter("tcCoverage"), COVERAGE_ALL,
				new String[] { COVERAGE_ALL, COVERAGE_COVERED, COVERAGE_UNCOVERED });
		f.automatable = pick(request.getParameter("automatable"), "ALL", new String[] { "ALL", "Y", "N", "M" });

		Long from = parseDay(request.getParameter("dateFrom"), false);
		Long to = parseDay(request.getParameter("dateTo"), true);
		if (from != null && to != null && from.longValue() > to.longValue()) {
			Long swap = from;
			from = to;
			to = swap;
		}
		f.dateFrom = from;
		f.dateTo = to;

		f.search = lower(request.getParameter("search"));
		f.path = lower(request.getParameter("path"));

		f.authors.addAll(csv(request.getParameter("authors")));
		f.requirementStatuses.addAll(upperCsv(request.getParameter("reqStatus")));
		f.criticalities.addAll(upperCsv(request.getParameter("criticality")));
		f.categories.addAll(upperCsv(request.getParameter("category")));
		f.testCaseStatuses.addAll(upperCsv(request.getParameter("tcStatus")));
		f.importances.addAll(upperCsv(request.getParameter("importance")));
		f.natures.addAll(upperCsv(request.getParameter("nature")));
		f.types.addAll(upperCsv(request.getParameter("type")));
		f.campaignStatuses.addAll(upperCsv(request.getParameter("campaignStatus")));
		f.executionStatuses.addAll(upperCsv(request.getParameter("execStatus")));
		f.reportTypes.addAll(upperCsv(request.getParameter("reportType")));

		try {
			int value = Integer.parseInt(request.getParameter("limit"));
			f.limit = Math.max(1, Math.min(MAX_LIMIT, value));
		} catch (NumberFormatException e) {
			f.limit = DEFAULT_LIMIT;
		}

		f.refresh = "true".equalsIgnoreCase(request.getParameter("refresh"));

		return f;
	}


	private static List<String> csv(String value) {

		List<String> result = new ArrayList<String>();

		if (value == null || value.trim().isEmpty()) {
			return result;
		}

		for (String part : Arrays.asList(value.split(","))) {
			String trimmed = part.trim();
			if (!trimmed.isEmpty()) {
				result.add(trimmed);
			}
		}

		return result;
	}

	private static List<String> upperCsv(String value) {

		List<String> result = new ArrayList<String>();

		for (String part : csv(value)) {
			result.add(part.toUpperCase(Locale.ROOT));
		}

		return result;
	}

	private static String lower(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

	private static String pick(String value, String defaultValue, String[] allowed) {

		if (value == null) {
			return defaultValue;
		}

		String normalized = value.trim().toUpperCase(Locale.ROOT);

		for (String candidate : allowed) {
			if (candidate.equals(normalized)) {
				return candidate;
			}
		}

		return defaultValue;
	}

	
	private static Long parseDay(String value, boolean endOfDay) {

		if (value == null || value.trim().isEmpty()) {
			return null;
		}

		try {
			LocalDate day = LocalDate.parse(value.trim());

			if (endOfDay) {
				return Long.valueOf(day.plusDays(1).atStartOfDay(SquashTmConstants.ZONE_ID).toInstant().toEpochMilli() - 1L);
			}

			return Long.valueOf(day.atStartOfDay(SquashTmConstants.ZONE_ID).toInstant().toEpochMilli());

		} catch (Exception e) {
			return null;
		}
	}


	public boolean wants(String section) {
		return SCOPE_ALL.equals(scope) || scope.equals(section);
	}

	public boolean hasPeriod() {
		return dateFrom != null || dateTo != null;
	}

	
	public boolean inPeriod(Long timestamp) {

		if (!hasPeriod()) {
			return true;
		}

		if (timestamp == null) {
			return false;
		}

		long value = timestamp.longValue();

		if (dateFrom != null && value < dateFrom.longValue()) {
			return false;
		}

		return dateTo == null || value <= dateTo.longValue();
	}


	public Set<Long> getProjectIds() {
		return projectIds;
	}

	public Set<String> getAuthors() {
		return authors;
	}

	public Set<String> getRequirementStatuses() {
		return requirementStatuses;
	}

	public Set<String> getCriticalities() {
		return criticalities;
	}

	public Set<String> getCategories() {
		return categories;
	}

	public Set<String> getTestCaseStatuses() {
		return testCaseStatuses;
	}

	public Set<String> getImportances() {
		return importances;
	}

	public Set<String> getNatures() {
		return natures;
	}

	public Set<String> getTypes() {
		return types;
	}

	public Set<String> getCampaignStatuses() {
		return campaignStatuses;
	}

	public Set<String> getExecutionStatuses() {
		return executionStatuses;
	}

	public Set<String> getReportTypes() {
		return reportTypes;
	}

	public String getScope() {
		return scope;
	}

	public String getDateField() {
		return dateField;
	}

	public Long getDateFrom() {
		return dateFrom;
	}

	public Long getDateTo() {
		return dateTo;
	}

	public String getSearch() {
		return search;
	}

	public String getPath() {
		return path;
	}

	public String getRequirementCoverage() {
		return requirementCoverage;
	}

	public String getTestCaseCoverage() {
		return testCaseCoverage;
	}

	public String getAutomatable() {
		return automatable;
	}

	public String getGroupBy() {
		return groupBy;
	}

	public int getLimit() {
		return limit;
	}

	public boolean isRefresh() {
		return refresh;
	}
}
