
package co.kozao.jcmsplugin.squashtm.util;

import java.time.ZoneId;

/**
 * Constantes utilisées par le plugin Squash TM.
 *
 */
public final class SquashTmConstants {

	/**
	 * Constructeur privé.
	 *
	 */
	private SquashTmConstants() {
	}

	/*
	 * ============ PLUGIN =========================
	 */

	public static final String PLUGIN_NAME = "SquashTmPlugin";

	/*
	 * ========== PROPRIETES DU PLUGIN ===========================
	 */

	public static final String SQUASHTM_CACHE_ENABLED = "jcmsplugin.squashtm.cache.enabled";

	/**
	 * Durée de conservation (en minutes) des données du dashboard en mémoire.
	 */
	public static final String SQUASHTM_DASHBOARD_CACHE_MINUTES = "jcmsplugin.squashtm.dashboard.cache.minutes";

	/**
	 * URL du serveur Squash TM.
	 */
	public static final String SQUASHTM_REMOTE_SERVER_URL = "jcmsplugin.squashtm.provider.app.auth.domain-name.url";

	/**
	 * Mode d'authentification utilisé par le connecteur.
	 */
	public static final String SQUASHTM_API_AUTHENTICATION_MODE = "jcmsplugin.squashtm.provider.authentication-mode";

	/**
	 * Répertoire de stockage du template personnalisé et de son mapping
	 */
	public static final String SQUASHTM_TEMPLATE_STORAGE_DIR = "jcmsplugin.squashtm.template.storage-dir";

	/*
	 * ========= MODES D'AUTHENTIFICATION =========================
	 */

	public static final String SQUASHTM_API_AUTHENTICATION_MODE_TOKEN = "TOKEN";

	public static final String SQUASHTM_API_AUTHENTICATION_MODE_BASIC = "BASIC";

	/*
	 * ==================== PREFERENCES JCMS MEMBER ==============
	 */

	/**
	 * Token API Squash TM du membre courant.
	 */
	public static final String SQUASHTM_MEMBER_API_TOKEN = "squashtm.api.token";

	/**
	 * Nom d'utilisateur Squash TM.
	 */
	public static final String SQUASHTM_MEMBER_USERNAME = "squashtm.username";

	/**
	 * Mot de passe Squash TM.
	 */
	public static final String SQUASHTM_MEMBER_PASSWORD = "squashtm.password";

	/**
	 * Informations de l'utilisateur Squash TM connecté.
	 */
	public static final String SQUASHTM_REMOTE_APP_USER_INFO = "squashtm.remote.user.info";

	public static final String DEFAULT_LEVEL_NAME = "Général";
	
	/**
	 * Icon App Sidebar Property
	 */
	public static final String SQUASHTM_ICON_CHOOSER_IMAGE_SIDEBAR_APP = "jcmsplugin.squashtm.properties.app.sidebar.icon.chooser-image";
	public static final String SQUASHTM_ICON_SIDEBAR_APP = "icon.jcmsplugin.squashtm.box-app-sidebar";

	public static final String SQUASHTM_CACHE_KEY_API_REACHABLE = "jcmsplugin.squashtm.cache.api-reachable";

	public static final String DEFAULT_TEMPLATE_PATH = "plugins/SquashTmPlugin/docs/template/Modele_Import_SquashTM.xlsx";

	public static final String MODEL_SHEET_NAME = "__modele__";

	public static final String I18N_PREFIX = "jcmsplugin.squashtm.dashboard.";

	public static final String MAPPING_FILE_NAME = "template-mapping.json";

	public static final String TEMPLATE_FILE_NAME = "active-template.xlsx";

	public static final String NO_SELECTION_MESSAGE = "Aucun élément sélectionné : cochez au moins un élément à créer ou à renommer.";

	public static final String ALREADY_UP_TO_DATE_MESSAGE = "Le projet Squash TM est déjà à jour : aucun élément à créer ou à renommer.";

	public static final String[] I18N_KEYS = { "title", "subtitle", "refresh", "refresh.title", "export",
			"export.requirements", "export.testcases", "export.campaigns", "export.executions", "export.reporting",
			"filters.reset", "filters.advanced", "filters.active", "filters.clear-all", "filters.remove",
			"filters.none-available", "filters.selected", "filter.scope", "scope.ALL", "scope.REQUIREMENTS",
			"scope.TEST_CASES", "scope.CAMPAIGNS", "scope.REPORTING", "filter.projects", "filter.projects.all",
			"filter.period", "period.all", "period.7", "period.30", "period.90", "period.year", "filter.from",
			"filter.to", "filter.dateField", "dateField.CREATED", "dateField.MODIFIED", "filter.groupBy", "group.AUTO",
			"group.DAY", "group.WEEK", "group.MONTH", "filter.search", "filter.search.placeholder", "filter.path",
			"filter.path.placeholder", "filter.authors", "filter.reqStatus", "filter.criticality", "filter.category",
			"filter.reqCoverage", "reqCoverage.ALL", "reqCoverage.COVERED", "reqCoverage.UNCOVERED", "filter.tcStatus",
			"filter.importance", "filter.nature", "filter.type", "filter.tcCoverage", "tcCoverage.ALL",
			"tcCoverage.COVERED", "tcCoverage.UNCOVERED", "filter.automatable", "automatable.ALL", "automatable.Y",
			"automatable.N", "automatable.M", "filter.campaignStatus", "filter.execStatus", "filter.reportType",
			"state.loading", "state.loading.first", "state.error", "state.retry", "state.empty", "state.partial",
			"state.updated", "state.cache", "kpi.requirements", "kpi.testCases", "kpi.campaigns", "kpi.executions",
			"kpi.coverage", "kpi.linked", "kpi.success", "kpi.progress", "kpi.never", "kpi.reporting",
			"section.requirements", "section.testCases", "section.campaigns", "section.reporting", "chart.byReportType",
			"reporting.note", "reporting.open", "chart.byStatus", "chart.byCriticality", "chart.byCategory",
			"chart.byProject", "chart.byImportance", "chart.byNature", "chart.byType", "chart.byAutomation",
			"chart.coverage", "chart.linkage", "chart.covered", "chart.uncovered", "chart.linked", "chart.notLinked",
			"chart.timeline", "chart.timeline.requirements", "chart.timeline.testCases", "chart.execStatus",
			"chart.execByProject", "chart.execTimeline", "chart.campaignStatus", "chart.success", "chart.failure",
			"chart.total", "chart.others", "chart.filter-hint", "col.id", "col.reference", "col.name", "col.project",
			"col.status", "col.criticality", "col.category", "col.importance", "col.nature", "col.type", "col.links",
			"col.created", "col.modified", "col.iterations", "col.executions", "col.success", "col.failure",
			"col.pending", "col.progress", "col.successRate", "col.lastExecution", "col.scheduledStart", "table.empty",
			"table.truncated", "value.UNDEFINED", "value.WORK_IN_PROGRESS", "value.UNDER_REVIEW", "value.APPROVED",
			"value.OBSOLETE", "value.TO_BE_UPDATED", "value.CRITICAL", "value.MAJOR", "value.MINOR", "value.VERY_HIGH",
			"value.HIGH", "value.MEDIUM", "value.LOW", "value.Y", "value.N", "value.M", "value.PLANNED",
			"value.IN_PROGRESS", "value.FINISHED", "value.ARCHIVED", "value.SUCCESS", "value.FAILURE", "value.BLOCKED",
			"value.RUNNING", "value.READY", "value.UNTESTABLE", "value.SETTLED", "value.ERROR", "value.WARNING",
			"value.NOT_RUN", "value.CANCELLED", "value.DASHBOARD", "value.CHART", "value.REPORT", "value.CUSTOM_EXPORT",
			"value.CAT_FUNCTIONAL", "value.CAT_NON_FUNCTIONAL", "value.CAT_USE_CASE", "value.CAT_BUSINESS",
			"value.CAT_TEST_REQUIREMENT", "value.CAT_ERGONOMIC", "value.CAT_PERFORMANCE", "value.CAT_TECHNICAL",
			"value.CAT_USER_STORY", "value.CAT_SECURITY", "value.CAT_UNDEFINED" };

	/*
	 * ========== DATES ===========================
	 */

	public static final ZoneId ZONE_ID = ZoneId.systemDefault();

	public static final String DATE_FORMAT_ISO_DAY = "yyyy-MM-dd";

	public static final String DATE_FORMAT_FILE_STAMP = "yyyyMMdd_HHmm";

	public static final String DATE_FORMAT_DISPLAY = "dd/MM/yyyy HH:mm";

	public static final String DATE_FORMAT_DASHBOARD_EXPORT = "yyyy-MM-dd HH:mm";

	/*
	 * ========== AFFICHAGE ===========================
	 */

	public static final int MAX_NAME_PREVIEW_LENGTH = 40;

	/*
	 * ========== CLES I18N ===========================
	 */

	public static final String I18N_APP_IMPORTFILES_TITLE = "jcmsplugin.squashtm.app.importfiles.title";

	public static final String I18N_APP_DASHBOARD_TITLE = "jcmsplugin.squashtm.app.dashboard.title";

	public static final String I18N_APP_SIDEBAR_ADMINISTRATION_LABEL = "jcmsplugin.squashtm.app.sidebar.administration.label";

	public static final String I18N_APP_LABEL_TITLE = "jcmsplugin.squashtm.app.label.title";

	public static final String I18N_BASIC_FORM_ERROR_USERNAME = "jcmsplugin.squashtm.basic-form.error.username";

	public static final String I18N_BASIC_FORM_ERROR_PASSWORD = "jcmsplugin.squashtm.basic-form.error.password";

	public static final String I18N_BASIC_FORM_ERROR_MEMBER = "jcmsplugin.squashtm.basic-form.error.member";

	public static final String I18N_BASIC_FORM_ERROR_INVALID = "jcmsplugin.squashtm.basic-form.error.invalid";

	public static final String I18N_TOKEN_FORM_ERROR_EMPTY = "jcmsplugin.squashtm.token-form.error.empty";

	public static final String I18N_TOKEN_FORM_ERROR_MEMBER = "jcmsplugin.squashtm.token-form.error.member";

	public static final String I18N_TOKEN_FORM_ERROR_INVALID = "jcmsplugin.squashtm.token-form.error.invalid";
}