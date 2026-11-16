package co.kozao.jcmsplugin.squashtm.model.mapping;

import java.util.ArrayList;
import java.util.List;

/**
 * Résultat de l'inspection d'un template Excel téléversé par l'administrateur.
 *
 * Il décrit les colonnes détectées (en-tête, exemples de valeurs, rôle suggéré)
 * afin que l'administrateur puisse confirmer ou corriger les associations avant
 * de les enregistrer.
 */
public class TemplateAnalysis {

	private String sheetName = "";
	private int headerRow;
	private int firstDataRow;
	private List<String> sheetNames = new ArrayList<>();
	private List<Column> columns = new ArrayList<>();
	private List<String> warnings = new ArrayList<>();

	public String getSheetName() {
		return sheetName;
	}

	public void setSheetName(String sheetName) {
		this.sheetName = sheetName;
	}

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

	public List<String> getSheetNames() {
		return sheetNames;
	}

	public List<Column> getColumns() {
		return columns;
	}

	public List<String> getWarnings() {
		return warnings;
	}

	public Column getColumn(int index) {

		for (Column column : columns) {
			if (column.getIndex() == index) {
				return column;
			}
		}

		return null;
	}
	
	public static class Column {

		private final int index;
		private final String header;
		private final List<String> samples = new ArrayList<>();
		private TemplateField suggestedField;

		public Column(int index, String header) {
			this.index = index;
			this.header = header == null ? "" : header.trim();
		}

		public int getIndex() {
			return index;
		}

		public String getLetter() {
			return TemplateColumnMapping.columnLetter(index);
		}

		public String getHeader() {
			return header;
		}

		public List<String> getSamples() {
			return samples;
		}

		public TemplateField getSuggestedField() {
			return suggestedField;
		}

		public void setSuggestedField(TemplateField suggestedField) {
			this.suggestedField = suggestedField;
		}

		public String getSuggestedFieldName() {
			return suggestedField == null ? "" : suggestedField.name();
		}
	}
}
