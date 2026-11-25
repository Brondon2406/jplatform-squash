package co.kozao.jcmsplugin.squashtm.model.squashimport;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Severin Kengne
 */
public class ImportSquashTmComparisonItem {

	
	public static final String TYPE_REQUIREMENT_FOLDER = "REQUIREMENT_FOLDER";
	public static final String TYPE_TEST_CASE_FOLDER = "TEST_CASE_FOLDER";
	public static final String TYPE_REQUIREMENT = "REQUIREMENT";
	public static final String TYPE_TEST_CASE = "TEST_CASE";
	public static final String TYPE_TEST_STEP = "TEST_STEP";

	
	public static final String STATUS_EXCEL_ONLY = "EXCEL_ONLY";
	public static final String STATUS_RENAME = "RENAME";

	
	public static final String STATUS_DIFFERENT = "DIFFERENT";
	public static final String STATUS_IDENTICAL = "IDENTICAL";
	public static final String STATUS_SQUASH_ONLY = "SQUASH_ONLY";

	
	public static final String ACTION_CREATE = "CREATE";
	public static final String ACTION_UPDATE = "UPDATE";
	public static final String ACTION_IGNORE = "IGNORE";
	public static final String ACTION_KEEP = "KEEP";
	public static final String ACTION_DELETE = "DELETE";

	private String comparisonKey;
	private String elementType;
	private String elementName;
	private String originalName;
	private String elementPath;

	private String status;
	private String selectedAction;

	
	private long squashId;


	private long parentSquashId;

	
	private List<String> differences = new ArrayList<>();

	public ImportSquashTmComparisonItem() {
	}

	public ImportSquashTmComparisonItem(String comparisonKey, String elementType, String elementName,
			String elementPath, String status, long squashId) {

		this.comparisonKey = comparisonKey;
		this.elementType = elementType;
		this.elementName = elementName;
		this.elementPath = elementPath;
		this.status = status;
		this.squashId = squashId;
	}

	public String getComparisonKey() {
		return comparisonKey;
	}

	public void setComparisonKey(String comparisonKey) {
		this.comparisonKey = comparisonKey;
	}

	public String getElementType() {
		return elementType;
	}

	public void setElementType(String elementType) {
		this.elementType = elementType;
	}

	public String getElementName() {
		return elementName;
	}

	public void setElementName(String elementName) {
		this.elementName = elementName;
	}

	public String getElementPath() {
		return elementPath;
	}

	public String getOriginalName() {
		return originalName;
	}

	public void setOriginalName(String originalName) {
		this.originalName = originalName;
	}

	public void setElementPath(String elementPath) {
		this.elementPath = elementPath;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public long getSquashId() {
		return squashId;
	}

	public void setSquashId(long squashId) {
		this.squashId = squashId;
	}

	public long getParentSquashId() {
		return parentSquashId;
	}

	public void setParentSquashId(long parentSquashId) {
		this.parentSquashId = parentSquashId;
	}

	public List<String> getDifferences() {
		return differences;
	}

	public void setDifferences(List<String> differences) {
		this.differences = differences != null ? differences : new ArrayList<>();
	}

	public void addDifference(String difference) {
		if (difference != null && !difference.trim().isEmpty()) {
			differences.add(difference);
		}
	}

	public String getSelectedAction() {
		return selectedAction;
	}

	public void setSelectedAction(String selectedAction) {
		this.selectedAction = selectedAction;
	}
}