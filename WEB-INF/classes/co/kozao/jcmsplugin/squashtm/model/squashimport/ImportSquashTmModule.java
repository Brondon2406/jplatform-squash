package co.kozao.jcmsplugin.squashtm.model.squashimport;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Severin Kengne
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
		this.subModules = subModules != null ? subModules : new ArrayList<>();
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

}
