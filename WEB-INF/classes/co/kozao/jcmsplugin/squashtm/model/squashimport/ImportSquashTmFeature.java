package co.kozao.jcmsplugin.squashtm.model.squashimport;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Severin Kengne
 */
public class ImportSquashTmFeature {

	private String name;

	private List<ImportSquashTmRequirement> requirements;
	private List<ImportSquashTmTestCase> testCases;

	public ImportSquashTmFeature() {
		this.requirements = new ArrayList<>();
		this.testCases = new ArrayList<>();
	}

	public ImportSquashTmFeature(String name) {
		this.name = name;
		this.requirements = new ArrayList<>();
		this.testCases = new ArrayList<>();
	}

	public void addRequirement(ImportSquashTmRequirement requirement) {
		if (requirement != null) {
			requirements.add(requirement);
		}
	}

	public void addTestCase(ImportSquashTmTestCase testCase) {
		if (testCase != null) {
			testCases.add(testCase);
		}
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public List<ImportSquashTmRequirement> getRequirements() {
		return requirements;
	}

	public void setRequirements(List<ImportSquashTmRequirement> requirements) {
		this.requirements = requirements;
	}

	public List<ImportSquashTmTestCase> getTestCases() {
		return testCases;
	}

	public void setTestCases(List<ImportSquashTmTestCase> testCases) {
		this.testCases = testCases;
	}
}
