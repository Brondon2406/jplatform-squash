package co.kozao.jcmsplugin.squashtm.handler;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.log4j.Logger;

import com.google.gson.JsonObject;
import com.jalios.jcms.Member;
import com.jalios.jcms.handler.JcmsFormHandler;
import com.jalios.util.Util;

import co.kozao.jcmsplugin.squashtm.SquashTmManager;
import co.kozao.jcmsplugin.squashtm.model.dashboard.DashboardFilter;
import co.kozao.jcmsplugin.squashtm.util.SquashTmConstants;
import co.kozao.jcmsplugin.squashtm.service.SquashTmDashboardService;
import co.kozao.jcmsplugin.squashtm.util.SquashTmUtils;

/**
 * Handler de la vue Dashboard Squash TM.
 *
 * <p>
 * Suit le même schéma que les autres vues du plugin :
 * JSP → Handler → Manager → Service.
 * </p>
 *
 * <ul>
 * <li>Bind des paramètres de filtres (search, dates, scope, …)</li>
 * <li>Construction de {@link DashboardFilter}</li>
 * <li>Délégation au {@link SquashTmManager} / {@link SquashTmDashboardService}</li>
 * <li>Exposition de l'i18n et de l'URL AJAX pour le JS de rendu</li>
 * </ul>
 *
 * Le rendu des graphiques reste côté client (JSON). Ce handler est le point
 * d'entrée métier pour les opérations AJAX (data / projects / export).
 */
public class SquashTmDashboardHandler extends JcmsFormHandler {

	private static final Logger LOGGER = Logger.getLogger(SquashTmDashboardHandler.class);


	// -------------------------------------------------------------------------
	// Propriétés de filtres (bind HTTP via property="*")
	// -------------------------------------------------------------------------

	private String op;
	private String dataset;

	private String scope = DashboardFilter.SCOPE_ALL;
	private String preset = "all";
	private String dateField = DashboardFilter.DATE_CREATED;
	private String groupBy = DashboardFilter.GROUP_AUTO;
	private String reqCoverage = DashboardFilter.COVERAGE_ALL;
	private String tcCoverage = DashboardFilter.COVERAGE_ALL;
	private String automatable = "ALL";

	/** Chaîne saisie par le datepicker Jalios (format locale). */
	private String dateFromStr;
	private String dateToStr;

	/** Valeurs déjà normalisées yyyy-MM-dd (envoyées par le JS). */
	private String dateFrom;
	private String dateTo;

	private String search = "";
	private String path = "";
	private String projects = "";
	private String authors = "";
	private String reqStatus = "";
	private String criticality = "";
	private String category = "";
	private String tcStatus = "";
	private String importance = "";
	private String nature = "";
	private String type = "";
	private String campaignStatus = "";
	private String execStatus = "";
	private String reportType = "";
	private String limit;
	private boolean refresh;

	// -------------------------------------------------------------------------
	// Manager / Service
	// -------------------------------------------------------------------------

	private SquashTmManager getManager() {
		return SquashTmManager.getInstance();
	}

	private SquashTmDashboardService newService() throws IOException {
		Member member = getLoggedMember();
		if (member == null) {
			throw new IOException("Accès interdit.");
		}
		if (!getManager().isPluginActive(member)) {
			throw new IOException("Le plugin Squash TM n'est pas configuré.");
		}
		return getManager().newDashboardService(member);
	}

	/**
	 * Construit le filtre métier à partir des propriétés du handler.
	 * Compatible avec les paramètres envoyés par le JS (dateFrom/dateTo ISO)
	 * et avec les champs Jalios (dateFromStr/dateToStr au format locale).
	 */
	public DashboardFilter buildFilter() {
		// Si le JS a envoyé des ISO (dateFrom/dateTo), on les privilégie.
		// Sinon on parse les chaînes Jalios (dateFromStr/dateToStr).
		String fromParam = Util.notEmpty(dateFrom) ? dateFrom : toIsoDay(dateFromStr);
		String toParam = Util.notEmpty(dateTo) ? dateTo : toIsoDay(dateToStr);

		// Réutilise le parseur centralisé de DashboardFilter via une mini-request synthétique
		// en passant par les setters du filtre n'est pas public : on délègue à fromRequest
		// en construisant les paramètres attendus. Pour rester simple et stable, on
		// reconstruit via les paramètres HTTP déjà présents sur la request.
		return DashboardFilter.fromRequest(request);
	}

	/**
	 * Normalise une date saisie (datepicker Jalios, format userLang) en yyyy-MM-dd.
	 */
	private String toIsoDay(String value) {
		if (Util.isEmpty(value)) {
			return null;
		}
		try {
			DateFormat dayFormat = channel.getDateFormat(userLang);
			Date parsed = dayFormat.parse(value.trim());
			return new SimpleDateFormat(SquashTmConstants.DATE_FORMAT_ISO_DAY).format(parsed);
		} catch (ParseException e) {
			// Déjà en ISO ?
			if (value.trim().matches("\\d{4}-\\d{2}-\\d{2}")) {
				return value.trim();
			}
			LOGGER.warn("Impossible de parser la date dashboard : " + value);
			return null;
		}
	}

	// -------------------------------------------------------------------------
	// Opérations métier (appelées depuis dashboardData.jsp)
	// -------------------------------------------------------------------------

	/**
	 * Liste des projets (JSON).
	 */
	public String getProjectsJson() throws IOException {
		SquashTmDashboardService service = newService();
		JsonObject json = new JsonObject();
		json.add("projects", service.getProjects());
		return json.toString();
	}

	/**
	 * Indicateurs filtrés (JSON).
	 */
	public String getDashboardJson() throws IOException {
		SquashTmDashboardService service = newService();
		DashboardFilter filter = buildFilter();
		return service.buildDashboard(filter).toString();
	}

	/**
	 * Export CSV.
	 */
	public String getExportCsv() throws IOException {
		SquashTmDashboardService service = newService();
		DashboardFilter filter = buildFilter();
		return service.buildCsv(filter, dataset);
	}

	public String getExportFileName() {
		String name = dataset == null ? "export" : dataset.replaceAll("[^a-zA-Z0-9_-]", "");
		return "squashtm-" + name + ".csv";
	}

	// -------------------------------------------------------------------------
	// Présentation (JSP)
	// -------------------------------------------------------------------------

	public String getDataUrl(String contextPath) {
		return contextPath + "/plugins/SquashTmPlugin/jsp/dashboard/dashboardData.jsp";
	}

	public String getI18nAttribute() {
		JsonObject i18n = new JsonObject();
		for (String key : SquashTmConstants.I18N_KEYS) {
			String fullKey = SquashTmConstants.I18N_PREFIX + key;
			String value = glp(fullKey);
			if (value != null && !value.equals(fullKey)) {
				i18n.addProperty(key, value);
			}
		}
		return SquashTmUtils.escapeHtml(i18n.toString());
	}

	// -------------------------------------------------------------------------
	// Getters / Setters (bind HTTP)
	// -------------------------------------------------------------------------

	public String getOp() {
		return op;
	}

	public void setOp(String op) {
		this.op = op;
	}

	public String getDataset() {
		return dataset;
	}

	public void setDataset(String dataset) {
		this.dataset = dataset;
	}

	public String getScope() {
		return scope;
	}

	public void setScope(String scope) {
		this.scope = scope;
	}

	public String getPreset() {
		return preset;
	}

	public void setPreset(String preset) {
		this.preset = preset;
	}

	public String getDateField() {
		return dateField;
	}

	public void setDateField(String dateField) {
		this.dateField = dateField;
	}

	public String getGroupBy() {
		return groupBy;
	}

	public void setGroupBy(String groupBy) {
		this.groupBy = groupBy;
	}

	public String getReqCoverage() {
		return reqCoverage;
	}

	public void setReqCoverage(String reqCoverage) {
		this.reqCoverage = reqCoverage;
	}

	public String getTcCoverage() {
		return tcCoverage;
	}

	public void setTcCoverage(String tcCoverage) {
		this.tcCoverage = tcCoverage;
	}

	public String getAutomatable() {
		return automatable;
	}

	public void setAutomatable(String automatable) {
		this.automatable = automatable;
	}

	public String getDateFromStr() {
		return dateFromStr;
	}

	public void setDateFromStr(String dateFromStr) {
		this.dateFromStr = dateFromStr;
	}

	public String getDateToStr() {
		return dateToStr;
	}

	public void setDateToStr(String dateToStr) {
		this.dateToStr = dateToStr;
	}

	public String getDateFrom() {
		return dateFrom;
	}

	public void setDateFrom(String dateFrom) {
		this.dateFrom = dateFrom;
	}

	public String getDateTo() {
		return dateTo;
	}

	public void setDateTo(String dateTo) {
		this.dateTo = dateTo;
	}

	public String getSearch() {
		return search;
	}

	public void setSearch(String search) {
		this.search = search;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getProjects() {
		return projects;
	}

	public void setProjects(String projects) {
		this.projects = projects;
	}

	public String getAuthors() {
		return authors;
	}

	public void setAuthors(String authors) {
		this.authors = authors;
	}

	public String getReqStatus() {
		return reqStatus;
	}

	public void setReqStatus(String reqStatus) {
		this.reqStatus = reqStatus;
	}

	public String getCriticality() {
		return criticality;
	}

	public void setCriticality(String criticality) {
		this.criticality = criticality;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public String getTcStatus() {
		return tcStatus;
	}

	public void setTcStatus(String tcStatus) {
		this.tcStatus = tcStatus;
	}

	public String getImportance() {
		return importance;
	}

	public void setImportance(String importance) {
		this.importance = importance;
	}

	public String getNature() {
		return nature;
	}

	public void setNature(String nature) {
		this.nature = nature;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getCampaignStatus() {
		return campaignStatus;
	}

	public void setCampaignStatus(String campaignStatus) {
		this.campaignStatus = campaignStatus;
	}

	public String getExecStatus() {
		return execStatus;
	}

	public void setExecStatus(String execStatus) {
		this.execStatus = execStatus;
	}

	public String getReportType() {
		return reportType;
	}

	public void setReportType(String reportType) {
		this.reportType = reportType;
	}

	public String getLimit() {
		return limit;
	}

	public void setLimit(String limit) {
		this.limit = limit;
	}

	public boolean isRefresh() {
		return refresh;
	}

	public void setRefresh(boolean refresh) {
		this.refresh = refresh;
	}
}