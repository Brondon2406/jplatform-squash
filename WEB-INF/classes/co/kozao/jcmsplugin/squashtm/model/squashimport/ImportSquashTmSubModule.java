
package co.kozao.jcmsplugin.squashtm.model.squashimport;

import java.util.ArrayList;
import java.util.List;

/**
 * Représente un sous-module d'un module.
 *
 * Dans Excel : une valeur de la colonne A. Dans Squash TM : un dossier de
 * sous-module.
 */
public class ImportSquashTmSubModule {

	private String name;
	private List<ImportSquashTmView> views;

	public ImportSquashTmSubModule() {
		this.views = new ArrayList<>();
	}

	public ImportSquashTmSubModule(String name) {
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
