package co.kozao.jcmsplugin.squashtm.service.mapping;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import co.kozao.jcmsplugin.squashtm.model.mapping.TemplateAnalysis;
import co.kozao.jcmsplugin.squashtm.model.mapping.TemplateColumnMapping;
import co.kozao.jcmsplugin.squashtm.model.mapping.TemplateField;
import co.kozao.jcmsplugin.squashtm.model.mapping.TemplateMapping;
import co.kozao.jcmsplugin.squashtm.util.SquashTmUtils;

/**
 * Inspecte un template Excel téléversé dans la vue d'administration.
 *
 * Ce service ne modifie rien : il lit le premier onglet, repère la ligne
 * d'en-tête, liste les colonnes et suggère un rôle pour chacune d'elles.
 */

/**
 * @author Severin Kengne
 */
public class ExcelTemplateInspector {

	private static final int MAX_HEADER_SCAN_ROWS = 20;
	private static final int MAX_SAMPLE_ROWS = 200;
	private static final int MAX_SAMPLES_PER_COLUMN = 3;
	private static final int MAX_COLUMNS = 50;
	private static final int MAX_SAMPLE_LENGTH = 80;

	private final DataFormatter dataFormatter = new DataFormatter();

	/**
	 * Inspecte un template.
	 *
	 * @param file             fichier Excel
	 * @param headerRowHint    ligne d'en-tête imposée (à partir de 1), 0 pour indiquer qu'il n'y en a pas, valeur négative pour une détection automatique
	 * @param firstDataRowHint première ligne de données imposée (à partir de 1), valeur inférieure ou égale à 0 pour la déduire de l'en-tête
	 * @return la description des colonnes détectées
	 * @throws IOException si le fichier n'est pas un classeur Excel valide
	 */
	public TemplateAnalysis inspect(File file, int headerRowHint, int firstDataRowHint) throws IOException {

		if (file == null || !file.isFile()) {
			throw new IOException("Le fichier du modèle est introuvable.");
		}

		TemplateAnalysis analysis = new TemplateAnalysis();

		try (InputStream inputStream = new FileInputStream(file);
				Workbook workbook = WorkbookFactory.create(inputStream)) {

			if (workbook.getNumberOfSheets() == 0) {
				throw new IOException("Le classeur ne contient aucun onglet.");
			}

			for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
				analysis.getSheetNames().add(workbook.getSheetName(i));
			}

			Sheet sheet = workbook.getSheetAt(0);

			analysis.setSheetName(sheet.getSheetName());

			if (sheet.getPhysicalNumberOfRows() == 0) {
				throw new IOException("Le premier onglet est vide.");
			}

			int headerRow = headerRowHint >= 0 ? headerRowHint : detectHeaderRow(sheet);
			int firstDataRow = firstDataRowHint > 0 ? firstDataRowHint : headerRow + 1;

			if (headerRow >= firstDataRow) {
				analysis.getWarnings().add("La première ligne de données doit être située après la ligne d'en-tête.");
				firstDataRow = headerRow + 1;
			}

			analysis.setHeaderRow(headerRow);
			analysis.setFirstDataRow(firstDataRow);

			buildColumns(sheet, analysis);
			suggestFields(analysis);

			if (analysis.getColumns().isEmpty()) {
				analysis.getWarnings().add("Aucune colonne n'a été détectée avec ces numéros de lignes.");
			}

			if (workbook.getNumberOfSheets() > 1) {
				analysis.getWarnings().add("Le classeur contient " + workbook.getNumberOfSheets()
						+ " onglets. Seul le premier sert à détecter les colonnes ; le mapping s'applique à tous les onglets, sauf ceux que vous choisissez d'ignorer.");
			}

		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			throw new IOException("Le fichier n'est pas un classeur Excel valide.", e);
		}

		return analysis;
	}

	/**
	 * Détecte la ligne d'en-tête : première ligne comportant au moins trois
	 * cellules renseignées. Retourne un numéro à partir de 1.
	 */
	private int detectHeaderRow(Sheet sheet) {

		int lastRow = Math.min(sheet.getLastRowNum(), MAX_HEADER_SCAN_ROWS);

		for (int rowIndex = 0; rowIndex <= lastRow; rowIndex++) {

			Row row = sheet.getRow(rowIndex);

			if (row == null) {
				continue;
			}

			int filledCells = 0;

			for (int columnIndex = 0; columnIndex < Math.max(row.getLastCellNum(), 0); columnIndex++) {
				if (!SquashTmUtils.getCellValue(row, columnIndex, dataFormatter).isEmpty()) {
					filledCells++;
				}
			}

			if (filledCells >= 3) {
				return rowIndex + 1;
			}
		}

		return 1;
	}

	/**
	 * Construit la liste des colonnes avec leur en-tête et des exemples.
	 */
	private void buildColumns(Sheet sheet, TemplateAnalysis analysis) {

		int headerIndex = analysis.getHeaderRow() - 1;
		int firstDataIndex = analysis.getFirstDataRow() - 1;
		int lastSampleIndex = Math.min(sheet.getLastRowNum(), firstDataIndex + MAX_SAMPLE_ROWS);

		int columnCount = 0;

		Row headerRow = headerIndex >= 0 ? sheet.getRow(headerIndex) : null;

		if (headerRow != null) {
			columnCount = Math.max(columnCount, headerRow.getLastCellNum());
		}

		for (int rowIndex = firstDataIndex; rowIndex <= lastSampleIndex; rowIndex++) {

			Row row = sheet.getRow(rowIndex);

			if (row != null) {
				columnCount = Math.max(columnCount, row.getLastCellNum());
			}
		}

		columnCount = Math.min(columnCount, MAX_COLUMNS);

		for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {

			String header = headerRow == null ? "" : SquashTmUtils.getCellValue(headerRow, columnIndex, dataFormatter);

			TemplateAnalysis.Column column = new TemplateAnalysis.Column(columnIndex, header);

			for (int rowIndex = firstDataIndex; rowIndex <= lastSampleIndex
					&& column.getSamples().size() < MAX_SAMPLES_PER_COLUMN; rowIndex++) {

				Row row = sheet.getRow(rowIndex);

				if (row == null) {
					continue;
				}

				String value = SquashTmUtils.getCellValue(row, columnIndex, dataFormatter);

				if (!value.isEmpty() && !column.getSamples().contains(value)) {
					column.getSamples().add(SquashTmUtils.abbreviate(value, MAX_SAMPLE_LENGTH));
				}
			}

			analysis.getColumns().add(column);
		}
	}

	/**
	 * Suggère un rôle pour chaque colonne.
	 *
	 * 1. Les mots-clés de l'en-tête sont testés en premier.
	 *
	 * 2. Pour les rôles restants, la position du template historique est utilisée,
	 * uniquement si la colonne correspondante n'a pas d'en-tête (c'est le cas de la
	 * colonne du sous-module dans le modèle actuel).
	 */
	private void suggestFields(TemplateAnalysis analysis) {

		Map<TemplateField, Boolean> used = new HashMap<>();

		for (TemplateAnalysis.Column column : analysis.getColumns()) {

			TemplateField suggestion = TemplateField.suggest(column.getHeader());

			if (suggestion != null && !used.containsKey(suggestion)) {
				column.setSuggestedField(suggestion);
				used.put(suggestion, Boolean.TRUE);
			}
		}

		TemplateMapping defaultMapping = TemplateMapping.createDefault();

		for (TemplateField field : TemplateField.values()) {

			if (used.containsKey(field)) {
				continue;
			}

			TemplateColumnMapping defaultColumn = defaultMapping.getColumn(field);

			if (defaultColumn == null) {
				continue;
			}

			TemplateAnalysis.Column column = analysis.getColumn(defaultColumn.getColumnIndex());

			if (column != null && column.getSuggestedField() == null && column.getHeader().isEmpty()) {
				column.setSuggestedField(field);
				used.put(field, Boolean.TRUE);
			}
		}
	}

}
