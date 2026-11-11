package co.kozao.jcmsplugin.squashtm.model.squashimport;

import java.util.ArrayList;
import java.util.List;

/**
 * Représente un élément issu de la comparaison entre Excel et Squash TM.
 */
public class ImportSquashTmComparisonItem {

	// Types d'éléments comparés.
	public static final String TYPE_REQUIREMENT_FOLDER = "REQUIREMENT_FOLDER";
	public static final String TYPE_TEST_CASE_FOLDER = "TEST_CASE_FOLDER";
	public static final String TYPE_REQUIREMENT = "REQUIREMENT";
	public static final String TYPE_TEST_CASE = "TEST_CASE";
	public static final String TYPE_TEST_STEP = "TEST_STEP";

	// Résultats utiles de la comparaison.
	public static final String STATUS_EXCEL_ONLY = "EXCEL_ONLY";
	public static final String STATUS_RENAME = "RENAME";

	// Statuts conservés pour compatibilité avec les anciennes utilisations.
	public static final String STATUS_DIFFERENT = "DIFFERENT";
	public static final String STATUS_IDENTICAL = "IDENTICAL";
	public static final String STATUS_SQUASH_ONLY = "SQUASH_ONLY";

	// Actions possibles après la comparaison.
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

	// Identifiant Squash TM. Vaut 0 si l'élément n'existe pas dans Squash.
	private long squashId;

	// Pour une étape : identifiant du cas de test Squash TM qui la contient.
	// Vaut 0 si le cas de test n'existe pas encore dans Squash TM.
	private long parentSquashId;

	// Explication des différences constatées.
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