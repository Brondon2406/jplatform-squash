package co.kozao.jcmsplugin.squashtm.model.squashimport;

import java.util.ArrayList;
import java.util.List;

/**
 * Représente un cas de test à importer dans Squash TM.
 */
public class ImportSquashTmTestCase {

	private String reference;
	private String name;

	private List<ImportSquashTmTestStep> steps;

	public ImportSquashTmTestCase() {
		this.steps = new ArrayList<>();
	}

	public ImportSquashTmTestCase(String reference, String name) {
		this.reference = reference;
		this.name = name;
		this.steps = new ArrayList<>();
	}

	public void addStep(ImportSquashTmTestStep step) {
		if (step != null) {
			steps.add(step);
		}
	}

	public String getReference() {
		return reference;
	}

	public void setReference(String reference) {
		this.reference = reference;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public List<ImportSquashTmTestStep> getSteps() {
		return steps;
	}

	public void setSteps(List<ImportSquashTmTestStep> steps) {
		this.steps = steps;
	}
}