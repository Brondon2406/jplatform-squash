package co.kozao.jcmsplugin.squashtm.handler;

import org.apache.log4j.Logger;

import com.jalios.jcms.handler.JcmsFormHandler;

import co.kozao.jcmsplugin.squashtm.util.SquashTmConstants;

/**
 * @author Severin Kengne
 */
public class SquashTmAppHandler extends JcmsFormHandler {
	private static final Logger LOGGER = Logger.getLogger(SquashTmAppHandler.class);
	private View view = View.DASHBOARD;

	public enum View {
		DASHBOARD, IMPORT_FILES, ADMINISTRATION
	}

	public String getAppUrl() {
		return "/jcms/plugins/SquashTmPlugin/jsp/app/squashTm.jsp";
	}

	public String getAppTitle() {
		if (showImportFiles()) {
			return glp(SquashTmConstants.I18N_APP_IMPORTFILES_TITLE);

		} else if (showDashboard()) {
			return glp(SquashTmConstants.I18N_APP_DASHBOARD_TITLE);

		} else if (showAdministration()) {
			return glp(SquashTmConstants.I18N_APP_SIDEBAR_ADMINISTRATION_LABEL);
		}
		return glp(SquashTmConstants.I18N_APP_LABEL_TITLE);
	}

	public String getDashboardUrl() {
		return getViewUrl(View.DASHBOARD);
	}

	public String getImportFilesUrl() {
		return getViewUrl(View.IMPORT_FILES);
	}

	public String getAdministrationUrl() {
		return getViewUrl(View.ADMINISTRATION);
	}

	private String getViewUrl(View view) {
		return getAppUrl() + "?view=" + view.name();
	}

	public void setView(String value) {
		LOGGER.debug("Paramètre view reçu : " + value);
		if (value == null || value.trim().isEmpty()) {
			view = View.DASHBOARD;
			return;
		}
		try {
			view = View.valueOf(value.trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			LOGGER.warn("Vue inconnue : " + value + ". Dashboard utilisé.");
			view = View.DASHBOARD;
		}
	}

	public boolean showDashboard() {
		boolean display = getBoxDisplayFilter().equalsIgnoreCase(View.DASHBOARD.name());
		return display;
	}

	public boolean showImportFiles() {
		boolean display = getBoxDisplayFilter().equalsIgnoreCase(View.IMPORT_FILES.name());
		return display;
	}

	public boolean showAdministration() {
		boolean display = getBoxDisplayFilter().equalsIgnoreCase(View.ADMINISTRATION.name());
		return display;
	}

	public String getBoxDisplayFilter() {
		return view.name();
	}

}