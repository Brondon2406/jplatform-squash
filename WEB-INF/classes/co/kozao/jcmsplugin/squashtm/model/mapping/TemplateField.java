package co.kozao.jcmsplugin.squashtm.model.mapping;

import java.text.Normalizer;
import java.util.Locale;

/**
 * @author Severin Kengne
 */
public enum TemplateField {

	SUBMODULE("Sous-module", "jcmsplugin.squashtm.app.administration.field.submodule", false, "sousmodule"),
	VIEW("Vue", "jcmsplugin.squashtm.app.administration.field.view", false, "vue"),
	FEATURE("Fonctionnalité", "jcmsplugin.squashtm.app.administration.field.feature", false, "fonctionnalit"),
	ELEMENT("Libellé de l'exigence et du cas de test", "jcmsplugin.squashtm.app.administration.field.element", true,
			"libelle", "exigence", "casdetest"),
	ACTION("Action de l'étape", "jcmsplugin.squashtm.app.administration.field.action", true, "action"),
	EXPECTED_RESULT("Résultat attendu", "jcmsplugin.squashtm.app.administration.field.expected-result", true,
			"resultat", "attendu");

	
	private static final TemplateField[] SUGGESTION_ORDER = { EXPECTED_RESULT, ACTION, ELEMENT, SUBMODULE, VIEW,
			FEATURE };

	private final String defaultLabel;
	private final String labelKey;
	private final boolean required;
	private final String[] keywords;

	TemplateField(String defaultLabel, String labelKey, boolean required, String... keywords) {
		this.defaultLabel = defaultLabel;
		this.labelKey = labelKey;
		this.required = required;
		this.keywords = keywords;
	}

	public String getDefaultLabel() {
		return defaultLabel;
	}

	public String getLabelKey() {
		return labelKey;
	}

	public boolean isRequired() {
		return required;
	}

	public boolean isHierarchyLevel() {
		return this == SUBMODULE || this == VIEW || this == FEATURE;
	}

	public static TemplateField fromName(String name) {

		if (name == null || name.trim().isEmpty()) {
			return null;
		}

		try {
			return TemplateField.valueOf(name.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	
	public static TemplateField suggest(String header) {

		String normalizedHeader = normalize(header);

		if (normalizedHeader.isEmpty()) {
			return null;
		}

		for (TemplateField field : SUGGESTION_ORDER) {
			for (String keyword : field.keywords) {
				if (normalizedHeader.contains(keyword)) {
					return field;
				}
			}
		}

		return null;
	}

	public static String normalize(String value) {

		if (value == null) {
			return "";
		}

		String withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");

		return withoutAccents.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}
}
