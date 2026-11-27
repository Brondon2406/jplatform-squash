
package co.kozao.jcmsplugin.squashtm.handler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.log4j.Logger;

import com.jalios.jcms.handler.JcmsFormHandler;

import co.kozao.jcmsplugin.squashtm.SquashTmManager;
import co.kozao.jcmsplugin.squashtm.model.api.SquashTmProject;
import co.kozao.jcmsplugin.squashtm.util.SquashTmConstants;
import co.kozao.jcmsplugin.squashtm.util.SquashTmUtils;

/**
 * Handler principal de l'application Squash TM.
 *
 * Gère la navigation et la préparation des projets destinés à la vue d'export.
 *
 * @author Severin Kengne
 */
public class SquashTmAppHandler extends JcmsFormHandler {

	private static final Logger LOGGER = Logger.getLogger(SquashTmAppHandler.class);

	private View view = View.DASHBOARD;
	private String exportProjectsError;

	public enum View {
		DASHBOARD, IMPORT_FILES, EXPORT_FILES, ADMINISTRATION
	}

	public String getAppUrl() {
		return "/jcms/plugins/SquashTmPlugin/jsp/app/squashTm.jsp";
	}

	public String getAppTitle() {

		if (showDashboard()) {
			return glp(SquashTmConstants.I18N_APP_DASHBOARD_TITLE);

		} else if (showImportFiles()) {
			return glp(SquashTmConstants.I18N_APP_IMPORTFILES_TITLE);

		} else if (showExportFiles()) {
			return glp(SquashTmConstants.I18N_APP_EXPORTFILES_TITLE);

		} else if (showAdministration()) {
			return glp(SquashTmConstants.I18N_APP_ADMINISTRATION_LABEL);
		}

		return glp(SquashTmConstants.I18N_APP_LABEL_TITLE);
	}

	public String getDashboardUrl() {
		return getViewUrl(View.DASHBOARD);
	}

	public String getImportFilesUrl() {
		return getViewUrl(View.IMPORT_FILES);
	}

	public String getExportFilesUrl() {
		return getViewUrl(View.EXPORT_FILES);
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
		return getBoxDisplayFilter().equalsIgnoreCase(View.DASHBOARD.name());
	}

	public boolean showImportFiles() {
		return getBoxDisplayFilter().equalsIgnoreCase(View.IMPORT_FILES.name());
	}

	public boolean showExportFiles() {
		return getBoxDisplayFilter().equalsIgnoreCase(View.EXPORT_FILES.name());
	}

	public boolean showAdministration() {
		return getBoxDisplayFilter().equalsIgnoreCase(View.ADMINISTRATION.name());
	}

	/**
	 * Vue Export : récupère les projets Squash TM (via le manager) et les trie.
	 *
	 * @param sortField  champ de tri : "id" (défaut), "name" ou "label"
	 * @param descending true pour un tri décroissant
	 * @param searchTerm terme de recherche (null ou vide : aucun filtre)
	 * @return la liste triée, vide en cas d'erreur (voir getExportProjectsError())
	 */
	public List<SquashTmProject> getExportProjects(String sortField, boolean descending,  String searchTerm) {

		exportProjectsError = null;
		List<SquashTmProject> projects;

		try {
			projects = new ArrayList<SquashTmProject>(SquashTmManager.getInstance().getProjects());
		} catch (IOException | RuntimeException e) {
			LOGGER.error("Impossible de récupérer les projets Squash TM", e);
			exportProjectsError = e.getMessage() != null ? e.getMessage() : e.toString();
			return new ArrayList<SquashTmProject>();
		}

		final String field = sortField == null ? "id" : sortField;
		final boolean desc = descending;

		Collections.sort(projects, new Comparator<SquashTmProject>() {
			@Override
			public int compare(SquashTmProject first, SquashTmProject second) {
				int result;
				if ("name".equals(field)) {
					result = first.getName().compareToIgnoreCase(second.getName());
				} else if ("label".equals(field)) {
					result = first.getLabel().compareToIgnoreCase(second.getLabel());
				} else {
					result = Long.compare(first.getId(), second.getId());
				}
				return desc ? -result : result;
			}
		});

		return filterProjects(projects, searchTerm);
	}
	
	/**
	 * Recherche on garde les projets dont l'id, le nom, l'étiquette ou la description contiennent tous les mots saisis.
	 */
	private List<SquashTmProject> filterProjects(List<SquashTmProject> projects, String searchTerm) {
 
		if (projects == null || searchTerm == null || searchTerm.trim().isEmpty()) {
			return projects;
		}
 
		String[] tokens = SquashTmUtils.normalizeForSearch(searchTerm.trim()).split("\\s+");
		List<SquashTmProject> found = new ArrayList<SquashTmProject>();
 
		for (SquashTmProject project : projects) {
			String haystack = SquashTmUtils.normalizeForSearch(project.getId() + " " + project.getName() + " "
					+ project.getLabel() + " " + project.getDescriptionText());
			boolean matches = true;
			for (String token : tokens) {
				if (!haystack.contains(token)) {
					matches = false;
					break;
				}
			}
			if (matches) {
				found.add(project);
			}
		}
 
		return found;
	}

	public String getExportProjectsError() {
		return exportProjectsError;
	}

	public String getBoxDisplayFilter() {
		return view.name();
	}

}
