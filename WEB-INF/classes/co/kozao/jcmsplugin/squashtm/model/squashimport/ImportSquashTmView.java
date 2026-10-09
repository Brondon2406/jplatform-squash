package co.kozao.jcmsplugin.squashtm.model.squashimport;

import java.util.ArrayList;
import java.util.List;

/**
 * Représente une vue du projet.
 *
 * Dans Squash TM, une vue correspond à un dossier sous le module.
 */
public class ImportSquashTmView {

	private String name;
	private List<ImportSquashTmFeature> features;

	public ImportSquashTmView() {
		this.features = new ArrayList<>();
	}

	public ImportSquashTmView(String name) {
		this.name = name;
		this.features = new ArrayList<>();
	}

	public void addFeature(ImportSquashTmFeature feature) {
		if (feature != null) {
			features.add(feature);
		}
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public List<ImportSquashTmFeature> getFeatures() {
		return features;
	}

	public void setFeatures(List<ImportSquashTmFeature> features) {
		this.features = features;
	}
}
