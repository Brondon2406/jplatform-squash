package co.kozao.jcmsplugin.squashtm.model.mapping;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Description complète de la façon dont le module doit lire le template Excel.
 */
public class TemplateMapping {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	public static final int DEFAULT_HEADER_ROW = 3;
	public static final int DEFAULT_FIRST_DATA_ROW = 4;
	public static final String DEFAULT_LEVEL_NAME = "Général";
	private int headerRow = DEFAULT_HEADER_ROW;
	private int firstDataRow = DEFAULT_FIRST_DATA_ROW;
	private List<String> ignoredSheets = new ArrayList<>();
	private List<TemplateColumnMapping> columns = new ArrayList<>();
	private String defaultSubModuleName = DEFAULT_LEVEL_NAME;
	private String defaultViewName = DEFAULT_LEVEL_NAME;
	private String defaultFeatureName = DEFAULT_LEVEL_NAME;
	private String updatedBy = "";
	private long updatedAt;

	public TemplateMapping() {
	}

	public static TemplateMapping createDefault() {

		TemplateMapping mapping = new TemplateMapping();

		mapping.headerRow = DEFAULT_HEADER_ROW;
		mapping.firstDataRow = DEFAULT_FIRST_DATA_ROW;

		mapping.setColumn(new TemplateColumnMapping(TemplateField.SUBMODULE, 0, ""));
		mapping.setColumn(new TemplateColumnMapping(TemplateField.VIEW, 1, "vues"));
		mapping.setColumn(new TemplateColumnMapping(TemplateField.FEATURE, 2, "Les fonctionnalités"));
		mapping.setColumn(
				new TemplateColumnMapping(TemplateField.ELEMENT, 3, "Libellé de l'exigence et du Cas de test"));
		mapping.setColumn(new TemplateColumnMapping(TemplateField.ACTION, 4, "Actions"));
		mapping.setColumn(new TemplateColumnMapping(TemplateField.EXPECTED_RESULT, 5, "Résultats attendu"));

		return mapping;
	}

	/*
	 * ----------------- COLONNES ------------------
	 */

	public List<TemplateColumnMapping> getColumns() {
		return columns;
	}

	public void setColumns(List<TemplateColumnMapping> columns) {
		this.columns = columns != null ? columns : new ArrayList<>();
	}

	/**
	 * Ajoute une association. Si le rôle était déjà associé à une autre colonne,
	 * l'ancienne association est remplacée.
	 */
	public void setColumn(TemplateColumnMapping column) {

		if (column == null || column.getTemplateField() == null) {
			return;
		}

		columns.removeIf(existing -> column.getTemplateField().equals(existing.getTemplateField()));
		columns.add(column);
	}

	/**
	 * Retourne l'association d'un rôle, ou null si le rôle n'est pas associé.
	 */
	public TemplateColumnMapping getColumn(TemplateField field) {

		if (field == null) {
			return null;
		}

		for (TemplateColumnMapping column : columns) {
			if (field.equals(column.getTemplateField())) {
				return column;
			}
		}

		return null;
	}

	/**
	 * Indique si le rôle est associé à une colonne.
	 */
	public boolean isMapped(TemplateField field) {
		return getColumn(field) != null;
	}

	/*
	 * ---------------------- LIGNES -------------------------
	 */

	public int getHeaderRow() {
		return headerRow;
	}

	public void setHeaderRow(int headerRow) {
		this.headerRow = headerRow;
	}

	public int getFirstDataRow() {
		return firstDataRow;
	}

	public void setFirstDataRow(int firstDataRow) {
		this.firstDataRow = firstDataRow;
	}

	/*
	 * -------------------- ONGLETS ---------------------------
	 */

	public List<String> getIgnoredSheets() {
		return ignoredSheets;
	}

	public void setIgnoredSheets(List<String> ignoredSheets) {
		this.ignoredSheets = ignoredSheets != null ? ignoredSheets : new ArrayList<>();
	}

	/**
	 * Renseigne les onglets ignorés à partir d'un texte séparé par des virgules,
	 * des points-virgules ou des retours à la ligne.
	 */
	public void setIgnoredSheetsFromText(String text) {

		List<String> names = new ArrayList<>();

		if (text != null) {
			for (String name : text.split("[,;\\r\\n]+")) {
				if (!name.trim().isEmpty()) {
					names.add(name.trim());
				}
			}
		}

		this.ignoredSheets = names;
	}

	/**
	 * Retourne les onglets ignorés sous forme de texte séparé par des virgules.
	 */
	public String getIgnoredSheetsAsText() {
		return String.join(", ", ignoredSheets);
	}

	/**
	 * Indique si un onglet doit être ignoré (comparaison sans tenir compte de la
	 * casse).
	 */
	public boolean isSheetIgnored(String sheetName) {

		if (sheetName == null) {
			return false;
		}

		for (String ignored : ignoredSheets) {
			if (ignored != null
					&& ignored.trim().toLowerCase(Locale.ROOT).equals(sheetName.trim().toLowerCase(Locale.ROOT))) {
				return true;
			}
		}

		return false;
	}

	/*
	 * --------------------- NIVEAUX PAR DEFAUT -------------------------
	 */

	public String getDefaultSubModuleName() {
		return defaultSubModuleName;
	}

	public void setDefaultSubModuleName(String defaultSubModuleName) {
		this.defaultSubModuleName = defaultSubModuleName;
	}

	public String getDefaultViewName() {
		return defaultViewName;
	}

	public void setDefaultViewName(String defaultViewName) {
		this.defaultViewName = defaultViewName;
	}

	public String getDefaultFeatureName() {
		return defaultFeatureName;
	}

	public void setDefaultFeatureName(String defaultFeatureName) {
		this.defaultFeatureName = defaultFeatureName;
	}

	/**
	 * Retourne le nom à utiliser lorsqu'un niveau de dossier n'a pas de colonne.
	 */
	public String getDefaultName(TemplateField field) {

		String name = null;

		if (TemplateField.SUBMODULE.equals(field)) {
			name = defaultSubModuleName;
		} else if (TemplateField.VIEW.equals(field)) {
			name = defaultViewName;
		} else if (TemplateField.FEATURE.equals(field)) {
			name = defaultFeatureName;
		}

		return name == null || name.trim().isEmpty() ? DEFAULT_LEVEL_NAME : name.trim();
	}

	/*
	 * ------------------- SUIVI --------------------------
	 */

	public String getUpdatedBy() {
		return updatedBy == null ? "" : updatedBy;
	}

	public void setUpdatedBy(String updatedBy) {
		this.updatedBy = updatedBy;
	}

	public long getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(long updatedAt) {
		this.updatedAt = updatedAt;
	}

	/*
	 * ------------------- VALIDATION -------------------------
	 */

	/**
	 * Vérifie la cohérence du mapping.
	 *
	 * @return la liste des erreurs (vide si le mapping est valide)
	 */
	public List<String> validate() {

		List<String> errors = new ArrayList<>();

		if (headerRow < 0) {
			errors.add("La ligne d'en-tête ne peut pas être négative.");
		}

		if (firstDataRow < 1) {
			errors.add("La première ligne de données doit être supérieure ou égale à 1.");
		}

		if (headerRow >= firstDataRow) {
			errors.add("La première ligne de données doit être située après la ligne d'en-tête.");
		}

		Set<String> usedFields = new HashSet<>();
		Set<Integer> usedColumns = new HashSet<>();

		for (TemplateColumnMapping column : columns) {

			TemplateField field = column.getTemplateField();

			if (field == null) {
				errors.add("Rôle inconnu : " + column.getField());
				continue;
			}

			if (column.getColumnIndex() < 0) {
				errors.add("La colonne associée à « " + field.getDefaultLabel() + " » est invalide.");
			}

			if (!usedFields.add(field.name())) {
				errors.add("« " + field.getDefaultLabel() + " » est associé à plusieurs colonnes.");
			}

			if (column.getColumnIndex() >= 0 && !usedColumns.add(column.getColumnIndex())) {
				errors.add("La colonne " + column.getColumnLetter() + " est associée à plusieurs rôles.");
			}
		}

		for (TemplateField field : TemplateField.values()) {
			if (field.isRequired() && !isMapped(field)) {
				errors.add("Le rôle obligatoire « " + field.getDefaultLabel() + " » n'est associé à aucune colonne.");
			}
		}

		validateLevelName(errors, "sous-module", defaultSubModuleName);
		validateLevelName(errors, "vue", defaultViewName);
		validateLevelName(errors, "fonctionnalité", defaultFeatureName);

		return errors;
	}

	private void validateLevelName(List<String> errors, String levelLabel, String name) {

		if (name == null || name.trim().isEmpty()) {
			return;
		}

		if (name.trim().length() > 255) {
			errors.add("Le nom par défaut du " + levelLabel + " dépasse 255 caractères.");
		}

		if (name.contains("/")) {
			errors.add("Le nom par défaut du " + levelLabel + " ne peut pas contenir le caractère « / ».");
		}
	}

	/*
	 * --------------- JSON -------------------
	 */

	public String toJson() {
		return GSON.toJson(this);
	}

	/**
	 * Reconstruit un mapping à partir de son JSON. Retourne null si le JSON est
	 * vide.
	 */
	public static TemplateMapping fromJson(String json) {

		if (json == null || json.trim().isEmpty()) {
			return null;
		}

		return GSON.fromJson(json, TemplateMapping.class);
	}

	/**
	 * Retourne une copie indépendante du mapping.
	 */
	public TemplateMapping copy() {
		return fromJson(toJson());
	}
}
