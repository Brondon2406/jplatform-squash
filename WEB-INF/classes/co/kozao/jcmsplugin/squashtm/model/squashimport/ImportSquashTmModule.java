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
	private List<ImportSquashTmSubModule> subModules;

	public ImportSquashTmModule() {
		this.subModules = new ArrayList<>();
	}

	public ImportSquashTmModule(String name) {
		this.name = name;
		this.subModules = new ArrayList<>();
	}

	public void addSubModule(ImportSquashTmSubModule subModule) {
		if (subModule != null) {
			subModules.add(subModule);
		}
	}
	

	public List<ImportSquashTmSubModule> getSubModules() {
		return subModules;
	}

	public void setSubModules(List<ImportSquashTmSubModule> subModules) {
		this.subModules = subModules != null
				? subModules
				: new ArrayList<>();
	}


	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

}
