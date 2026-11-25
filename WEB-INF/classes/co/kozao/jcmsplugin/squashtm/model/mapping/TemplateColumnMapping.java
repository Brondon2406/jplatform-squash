package co.kozao.jcmsplugin.squashtm.model.mapping;

/**
 * @author Severin Kengne
 */
public class TemplateColumnMapping {

	private String field;
	private int columnIndex;
	private String headerLabel;

	public TemplateColumnMapping() {
		this.columnIndex = -1;
		this.headerLabel = "";
	}

	public TemplateColumnMapping(TemplateField field, int columnIndex, String headerLabel) {
		this.field = field == null ? null : field.name();
		this.columnIndex = columnIndex;
		this.headerLabel = headerLabel == null ? "" : headerLabel.trim();
	}

	public String getField() {
		return field;
	}

	public void setField(String field) {
		this.field = field;
	}

	public TemplateField getTemplateField() {
		return TemplateField.fromName(field);
	}

	public int getColumnIndex() {
		return columnIndex;
	}

	public void setColumnIndex(int columnIndex) {
		this.columnIndex = columnIndex;
	}

	public String getHeaderLabel() {
		return headerLabel == null ? "" : headerLabel;
	}

	public void setHeaderLabel(String headerLabel) {
		this.headerLabel = headerLabel == null ? "" : headerLabel.trim();
	}

	public String getColumnLetter() {
		return columnLetter(columnIndex);
	}

	public static String columnLetter(int index) {

		if (index < 0) {
			return "?";
		}

		StringBuilder letters = new StringBuilder();
		int value = index;

		while (value >= 0) {
			letters.insert(0, (char) ('A' + (value % 26)));
			value = (value / 26) - 1;
		}

		return letters.toString();
	}
}
