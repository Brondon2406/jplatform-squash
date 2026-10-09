package co.kozao.jcmsplugin.squashtm.model.squashimport;

import java.util.ArrayList;
import java.util.List;

/**
 * Représente un module du projet.
 *
 * Dans Excel : un onglet = un module.
 *
 * Dans Squash TM : un module = un dossier.
 */
public class ImportSquashTmModule {

	private String name;
	private List<ImportSquashTmView> views;

	public ImportSquashTmModule() {
		this.views = new ArrayList<>();
	}

	public ImportSquashTmModule(String name) {
		this.name = name;
		this.views = new ArrayList<>();
	}

	public void addView(ImportSquashTmView view) {
		if (view != null) {
			views.add(view);
		}
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public List<ImportSquashTmView> getViews() {
		return views;
	}

	public void setViews(List<ImportSquashTmView> views) {
		this.views = views;
	}
}
