package co.kozao.jcmsplugin.squashtm.handler;

import org.apache.log4j.Logger;

import com.jalios.jcms.handler.JcmsFormHandler;

/**
 * @author Severin kengne
 */

public class SquashTmAppHandler extends JcmsFormHandler {
	private static final Logger LOGGER = Logger.getLogger(SquashTmAppHandler.class);
	private View view = View.DASHBOARD;

	public enum View {
		DASHBOARD, IMPORT_FILES
	}

	public String getAppUrl() {
		return "/jcms/plugins/SquashTmPlugin/jsp/app/squashTm.jsp";
	}

	public String getAppTitle() {
		if (showImportFiles()) {
			return glp("jcmsplugin.squashtm.app.importfiles.title");
		} else if (showDashboard()) {
			return glp("jcmsplugin.squashtm.app.dashboard.title");

		}
		return glp("jcmsplugin.squashtm.app.label.title");
	}

	public String getDashboardUrl() {
		return getViewUrl(View.DASHBOARD);
	}

	public String getImportFilesUrl() {
		return getViewUrl(View.IMPORT_FILES);
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

	public String getBoxDisplayFilter() {
		return view.name();
	}

}