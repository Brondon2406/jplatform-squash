package co.kozao.jcmsplugin.squashtm.model.squashimport;

import java.util.ArrayList;
import java.util.List;

/**
 * Contient le résultat final de l'import.
 *
 * Cet objet est temporaire : aucune donnée n'est conservée dans JCMS après
 * l'import.
 */
public class ImportSquashTmResult {

	private boolean success;

	private String projectName;
	private String fileName;

	private int requirementCount;
	private int testCaseCount;
	private int testStepCount;

	private List<String> inconsistencies;
	private List<String> technicalErrors;

	public ImportSquashTmResult() {
		this.inconsistencies = new ArrayList<>();
		this.technicalErrors = new ArrayList<>();
	}

	public boolean isSuccess() {
		return success;
	}

	public void setSuccess(boolean success) {
		this.success = success;
	}

	public String getProjectName() {
		return projectName;
	}

	public void setProjectName(String projectName) {
		this.projectName = projectName;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public int getRequirementCount() {
		return requirementCount;
	}

	public void setRequirementCount(int requirementCount) {
		this.requirementCount = requirementCount;
	}

	public int getTestCaseCount() {
		return testCaseCount;
	}

	public void setTestCaseCount(int testCaseCount) {
		this.testCaseCount = testCaseCount;
	}

	public int getTestStepCount() {
		return testStepCount;
	}

	public void setTestStepCount(int testStepCount) {
		this.testStepCount = testStepCount;
	}

	public List<String> getInconsistencies() {
		return inconsistencies;
	}

	public void setInconsistencies(List<String> inconsistencies) {
		this.inconsistencies = inconsistencies != null ? inconsistencies : new ArrayList<>();
	}

	public List<String> getTechnicalErrors() {
		return technicalErrors;
	}

	public void setTechnicalErrors(List<String> technicalErrors) {
		this.technicalErrors = technicalErrors != null ? technicalErrors : new ArrayList<>();
	}

	public void addInconsistency(String inconsistency) {
		if (inconsistency != null && !inconsistency.trim().isEmpty()) {
			inconsistencies.add(inconsistency);
		}
	}

	public void addTechnicalError(String technicalError) {
		if (technicalError != null && !technicalError.trim().isEmpty()) {
			technicalErrors.add(technicalError);
		}
	}
}
