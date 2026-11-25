
package co.kozao.jcmsplugin.squashtm.model.squashimport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Severin Kengne
 */
public class ImportSquashTmAnalysis {

	private final String projectName;
	private final int detectedLineCount;
	private final int validLineCount;
	private final int anomalousLineCount;
	private final int duplicateLineCount;
	private final List<String> inconsistencies;
	private boolean existingProject;
	private long existingProjectId;
	private List<ImportSquashTmComparisonItem> comparisonItems = new ArrayList<>();

	public ImportSquashTmAnalysis(String projectName, int detectedLineCount, int validLineCount, int anomalousLineCount,
			int duplicateLineCount, List<String> inconsistencies) {

		this.projectName = projectName;
		this.detectedLineCount = detectedLineCount;
		this.validLineCount = validLineCount;
		this.anomalousLineCount = anomalousLineCount;
		this.duplicateLineCount = duplicateLineCount;

		this.inconsistencies = inconsistencies == null ? new ArrayList<>() : new ArrayList<>(inconsistencies);
	}

	public String getProjectName() {
		return projectName;
	}

	public int getDetectedLineCount() {
		return detectedLineCount;
	}

	public int getValidLineCount() {
		return validLineCount;
	}

	public int getAnomalousLineCount() {
		return anomalousLineCount;
	}

	public int getDuplicateLineCount() {
		return duplicateLineCount;
	}

	public List<String> getInconsistencies() {
		return Collections.unmodifiableList(inconsistencies);
	}

	public double getValidityRate() {
		if (detectedLineCount == 0) {
			return 0.0;
		}

		return (validLineCount * 100.0) / detectedLineCount;
	}

	public boolean hasAnomalies() {
		return anomalousLineCount > 0;
	}

	public boolean isExistingProject() {
		return existingProject;
	}

	public void setExistingProject(boolean existingProject) {
		this.existingProject = existingProject;
	}

	public long getExistingProjectId() {
		return existingProjectId;
	}

	public void setExistingProjectId(long existingProjectId) {
		this.existingProjectId = existingProjectId;
	}

	public List<ImportSquashTmComparisonItem> getComparisonItems() {
		return Collections.unmodifiableList(comparisonItems);
	}

	public boolean hasActionsToPerform() {

		if (comparisonItems == null || comparisonItems.isEmpty()) {
			return false;
		}

		for (ImportSquashTmComparisonItem item : comparisonItems) {

			if (item == null) {
				continue;
			}

			if (ImportSquashTmComparisonItem.STATUS_EXCEL_ONLY.equals(item.getStatus())
					|| ImportSquashTmComparisonItem.STATUS_RENAME.equals(item.getStatus())) {

				return true;
			}
		}

		return false;
	}

	public void setComparisonItems(List<ImportSquashTmComparisonItem> comparisonItems) {
		this.comparisonItems = comparisonItems != null ? new ArrayList<>(comparisonItems) : new ArrayList<>();
	}

	public void addComparisonItem(ImportSquashTmComparisonItem item) {

		if (item == null) {
			return;
		}


		if (ImportSquashTmComparisonItem.TYPE_REQUIREMENT_FOLDER.equals(item.getElementType())
				|| ImportSquashTmComparisonItem.TYPE_TEST_CASE_FOLDER.equals(item.getElementType())) {
			return;
		}

		comparisonItems.add(item);
	}
}
