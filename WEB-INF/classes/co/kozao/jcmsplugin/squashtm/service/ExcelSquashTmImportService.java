package co.kozao.jcmsplugin.squashtm.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import co.kozao.jcmsplugin.squashtm.model.mapping.TemplateColumnMapping;
import co.kozao.jcmsplugin.squashtm.model.mapping.TemplateField;
import co.kozao.jcmsplugin.squashtm.model.mapping.TemplateMapping;
import co.kozao.jcmsplugin.squashtm.model.squashimport.ImportSquashTmAnalysis;
import co.kozao.jcmsplugin.squashtm.model.squashimport.ImportSquashTmData;
import co.kozao.jcmsplugin.squashtm.model.squashimport.ImportSquashTmFeature;
import co.kozao.jcmsplugin.squashtm.model.squashimport.ImportSquashTmModule;
import co.kozao.jcmsplugin.squashtm.model.squashimport.ImportSquashTmProgress;
import co.kozao.jcmsplugin.squashtm.model.squashimport.ImportSquashTmSubModule;
import co.kozao.jcmsplugin.squashtm.model.squashimport.ImportSquashTmRequirement;
import co.kozao.jcmsplugin.squashtm.model.squashimport.ImportSquashTmTestCase;
import co.kozao.jcmsplugin.squashtm.model.squashimport.ImportSquashTmTestStep;
import co.kozao.jcmsplugin.squashtm.model.squashimport.ImportSquashTmView;
import co.kozao.jcmsplugin.squashtm.service.mapping.SquashTmTemplateMappingManager;
import co.kozao.jcmsplugin.squashtm.util.SquashTmUtils;

/**
 * @author Severin Kengne
 */
public class ExcelSquashTmImportService {

	private TemplateMapping mapping;

	private final TemplateMapping injectedMapping;

	private int requirementCounter;
	private int testCaseCounter;

	private int moduleCount;
	private int subModuleCount;
	private int viewCount;
	private int featureCount;
	private int testStepCount;

	private int detectedLineCount;
	private int validLineCount;
	private int anomalousLineCount;

	private int duplicateLineCount;

	
	private final Set<String> seenRows = new HashSet<>();

	
	private final Set<String> anomalousLines = new HashSet<>();

	
	private final List<String> inconsistencies = new ArrayList<>();

	
	private final DataFormatter dataFormatter;

	public ExcelSquashTmImportService() {
		this(null);
	}

	
	public ExcelSquashTmImportService(TemplateMapping injectedMapping) {
		this.dataFormatter = new DataFormatter();
		this.injectedMapping = injectedMapping;
	}

	
	private void addInconsistency(String message) {

		if (message != null && !message.trim().isEmpty()) {

			inconsistencies.add(message);
		}
	}

	
	private void addLineInconsistency(Sheet sheet, Row row, String message) {

		addInconsistency(message);

		String lineKey = sheet.getSheetName() + ":" + (row.getRowNum() + 1);

		if (anomalousLines.add(lineKey)) {
			anomalousLineCount++;
		}
	}

	public List<String> getInconsistencies() {
		return new ArrayList<>(inconsistencies);
	}

	
	public int getModuleCount() {
		return moduleCount;
	}

	
	public int getSubModuleCount() {
		return subModuleCount;
	}

	
	public int getViewCount() {
		return viewCount;
	}

	
	public int getFeatureCount() {
		return featureCount;
	}

	
	public int getRequirementCount() {
		return requirementCounter;
	}

	
	public int getTestCaseCount() {
		return testCaseCounter;
	}

	
	public int getTestStepCount() {
		return testStepCount;
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

	
	public ImportSquashTmAnalysis buildAnalysis(String projectName) {

		return new ImportSquashTmAnalysis(projectName, detectedLineCount, validLineCount, anomalousLineCount,
				duplicateLineCount, getInconsistencies());
	}

	
	public ImportSquashTmData parse(File excelFile, String projectName) throws IOException {
		return parse(excelFile, projectName, null);
	}

	public ImportSquashTmData parse(File excelFile, String projectName, ImportSquashTmProgress progress)
			throws IOException {

		if (excelFile == null) {
			throw new IllegalArgumentException("Le fichier Excel est obligatoire.");
		}

		if (!excelFile.exists()) {
			throw new IllegalArgumentException("Le fichier Excel n'existe pas : " + excelFile.getAbsolutePath());
		}

		if (projectName == null || projectName.trim().isEmpty()) {
			throw new IllegalArgumentException("Le nom du projet est obligatoire.");
		}

		
		mapping = injectedMapping != null ? injectedMapping
				: SquashTmTemplateMappingManager.getInstance().getActiveMapping();

		
		requirementCounter = 0;
		testCaseCounter = 0;

		moduleCount = 0;
		subModuleCount = 0;
		viewCount = 0;
		featureCount = 0;
		testStepCount = 0;

		detectedLineCount = 0;
		validLineCount = 0;
		anomalousLineCount = 0;
		duplicateLineCount = 0;

		anomalousLines.clear();
		seenRows.clear();
		inconsistencies.clear();

		ImportSquashTmData importData = new ImportSquashTmData(excelFile.getName(), projectName.trim());

		try (FileInputStream inputStream = new FileInputStream(excelFile);
				Workbook workbook = WorkbookFactory.create(inputStream)) {

			int sheetCount = workbook.getNumberOfSheets();

			for (int sheetIndex = 0; sheetIndex < sheetCount; sheetIndex++) {

				Sheet sheet = workbook.getSheetAt(sheetIndex);

				if (progress != null) {
					progress.update(10 + (30 * sheetIndex) / Math.max(1, sheetCount), "ANALYSE_FICHIER",
							"Analyse de l'onglet " + (sheetIndex + 1) + "/" + sheetCount + " : "
									+ sheet.getSheetName());
				}

				parseSheet(sheet, importData);

			}
		}
		
		validLineCount = detectedLineCount - anomalousLineCount;

		return importData;
	}

	
	private String buildDuplicateKey(String subModuleName, String viewName, String featureName, String elementValue,
			String action, String expectedResult, ImportSquashTmSubModule currentSubModule,
			ImportSquashTmView currentView, ImportSquashTmFeature currentFeature) {
		String subModule = !SquashTmUtils.isBlank(subModuleName) ? subModuleName.trim()
				: currentSubModule == null ? "" : currentSubModule.getName();
		String view = !SquashTmUtils.isBlank(viewName) ? viewName.trim()
				: currentView == null ? "" : currentView.getName();
		String feature = !SquashTmUtils.isBlank(featureName) ? featureName.trim()
				: currentFeature == null ? "" : currentFeature.getName();
		return String.join("|", subModule, view, feature, elementValue == null ? "" : elementValue.trim(),
				action == null ? "" : action.trim(), expectedResult == null ? "" : expectedResult.trim());
	}

	/**
	 * Analyse un onglet Excel.
	 *
	 * Chaque onglet représente un module.
	 */
	private void parseSheet(Sheet sheet, ImportSquashTmData importData) {

		if (sheet == null) {
			return;
		}

		String moduleName = sheet.getSheetName();

		if (SquashTmUtils.isBlank(moduleName)) {
			return;
		}

		
		if (mapping.isSheetIgnored(moduleName)) {
			return;
		}

		Map<TemplateField, Integer> columns = resolveColumns(sheet);

		if (columns == null) {
			return;
		}

		ImportSquashTmModule module = new ImportSquashTmModule(moduleName.trim());

		
		ImportSquashTmSubModule currentSubModule = null;
		ImportSquashTmView currentView = null;
		ImportSquashTmFeature currentFeature = null;
		ImportSquashTmTestCase currentTestCase = null;

		int firstDataRow = Math.max(0, mapping.getFirstDataRow() - 1);

		for (int rowIndex = firstDataRow; rowIndex <= sheet.getLastRowNum(); rowIndex++) {

			Row row = sheet.getRow(rowIndex);

			if (row == null) {
				continue;
			}


			String subModuleName = getCellValue(row, columns, TemplateField.SUBMODULE);
			String viewName = getCellValue(row, columns, TemplateField.VIEW);
			String featureName = getCellValue(row, columns, TemplateField.FEATURE);
			String elementValue = getCellValue(row, columns, TemplateField.ELEMENT);
			String action = getCellValue(row, columns, TemplateField.ACTION);
			String expectedResult = getCellValue(row, columns, TemplateField.EXPECTED_RESULT);

			
			if (SquashTmUtils.isBlank(subModuleName) && SquashTmUtils.isBlank(viewName)
					&& SquashTmUtils.isBlank(featureName) && SquashTmUtils.isBlank(elementValue)
					&& SquashTmUtils.isBlank(action) && SquashTmUtils.isBlank(expectedResult)) {

				continue;
			}

			detectedLineCount++;

			
			String duplicateKey = buildDuplicateKey(subModuleName, viewName, featureName, elementValue, action,
					expectedResult, currentSubModule, currentView, currentFeature);


			if (!seenRows.add(duplicateKey)) {

				duplicateLineCount++;

				addLineInconsistency(sheet, row, "Onglet '" + sheet.getSheetName() + "', ligne " + (row.getRowNum() + 1)
						+ " : ligne dupliquée détectée.");
			}

			
			String effectiveSubModuleName = subModuleName;

			if (SquashTmUtils.isBlank(effectiveSubModuleName) && currentSubModule == null
					&& !columns.containsKey(TemplateField.SUBMODULE)) {

				effectiveSubModuleName = mapping.getDefaultName(TemplateField.SUBMODULE);
			}

			if (!SquashTmUtils.isBlank(effectiveSubModuleName)) {

				currentSubModule = findOrCreateSubModule(module, effectiveSubModuleName);

				currentView = null;
				currentFeature = null;
				currentTestCase = null;
			}


			String effectiveViewName = viewName;

			if (SquashTmUtils.isBlank(effectiveViewName) && currentView == null && currentSubModule != null
					&& !columns.containsKey(TemplateField.VIEW)) {

				effectiveViewName = mapping.getDefaultName(TemplateField.VIEW);
			}

			if (!SquashTmUtils.isBlank(effectiveViewName)) {

				if (currentSubModule == null) {

					addLineInconsistency(sheet, row, "Onglet '" + sheet.getSheetName() + "', ligne "
							+ (row.getRowNum() + 1) + " : vue sans sous-module valide.");
					currentView = null;
					currentFeature = null;
					currentTestCase = null;

					continue;
				}

				currentView = findOrCreateView(currentSubModule, effectiveViewName);

				currentFeature = null;
				currentTestCase = null;
			}

			
			String effectiveFeatureName = featureName;

			if (SquashTmUtils.isBlank(effectiveFeatureName) && currentFeature == null && currentView != null
					&& !columns.containsKey(TemplateField.FEATURE)) {

				effectiveFeatureName = mapping.getDefaultName(TemplateField.FEATURE);
			}

			if (!SquashTmUtils.isBlank(effectiveFeatureName)) {

				if (currentView == null) {
					continue;
				}

				currentFeature = findOrCreateFeature(currentView, effectiveFeatureName);

				
				currentTestCase = null;
			}

			
			if (!SquashTmUtils.isBlank(elementValue)) {

				if (currentFeature == null) {

					addLineInconsistency(sheet, row, "Onglet '" + sheet.getSheetName() + "', ligne "
							+ (row.getRowNum() + 1) + " : élément sans fonctionnalité valide.");
					currentTestCase = null;

				} else {

					String elementName = elementValue.trim();


					ImportSquashTmRequirement requirement = new ImportSquashTmRequirement(null, elementName);
					requirementCounter++;

					currentFeature.addRequirement(requirement);

					
					ImportSquashTmTestCase testCase = new ImportSquashTmTestCase(null, elementName);
					testCaseCounter++;

					currentFeature.addTestCase(testCase);

					
					currentTestCase = testCase;
				}
			}

			
			if (!SquashTmUtils.isBlank(action) || !SquashTmUtils.isBlank(expectedResult)) {

				if (currentTestCase == null) {

					addLineInconsistency(sheet, row, "Onglet '" + sheet.getSheetName() + "', ligne "
							+ (row.getRowNum() + 1) + " : action ou résultat attendu présent sans cas de test valide.");

				} else if (SquashTmUtils.isBlank(action)) {

					addLineInconsistency(sheet, row,
							"Onglet '" + sheet.getSheetName() + "', ligne " + (row.getRowNum() + 1)
									+ " : action manquante pour le cas de test '" + currentTestCase.getName() + "'.");

				} else if (SquashTmUtils.isBlank(expectedResult)) {

					addLineInconsistency(sheet, row,
							"Onglet '" + sheet.getSheetName() + "', ligne " + (row.getRowNum() + 1)
									+ " : résultat attendu manquant pour le cas de test '" + currentTestCase.getName()
									+ "'.");

				} else {

					ImportSquashTmTestStep step = new ImportSquashTmTestStep(action.trim(), expectedResult.trim());

					currentTestCase.addStep(step);
					testStepCount++;
				}
			}
		}
		
		if (!module.getSubModules().isEmpty()) {
			importData.addModule(module);
			moduleCount++;
		}
	}

	
	private ImportSquashTmSubModule findOrCreateSubModule(ImportSquashTmModule module, String subModuleName) {

		for (ImportSquashTmSubModule subModule : module.getSubModules()) {

			if (subModule.getName().equals(subModuleName.trim())) {
				return subModule;
			}
		}

		ImportSquashTmSubModule subModule = new ImportSquashTmSubModule(subModuleName.trim());

		module.addSubModule(subModule);
		subModuleCount++;

		return subModule;
	}

	
	private ImportSquashTmView findOrCreateView(ImportSquashTmSubModule subModule, String viewName) {

		for (ImportSquashTmView view : subModule.getViews()) {

			if (view.getName().equals(viewName.trim())) {
				return view;
			}
		}

		ImportSquashTmView view = new ImportSquashTmView(viewName.trim());

		subModule.addView(view);
		viewCount++;

		return view;
	}

	
	private ImportSquashTmFeature findOrCreateFeature(ImportSquashTmView view, String featureName) {

		for (ImportSquashTmFeature feature : view.getFeatures()) {

			if (feature.getName().equals(featureName.trim())) {
				return feature;
			}
		}

		ImportSquashTmFeature feature = new ImportSquashTmFeature(featureName.trim());

		view.addFeature(feature);
		featureCount++;

		return feature;
	}

	/**
	 * Détermine, pour un onglet, la colonne de chaque élément du mapping.
	 *
	 * Une colonne est d'abord recherchée par le texte de son en-tête : si le
	 * template a été réorganisé, le mapping continue de s'appliquer. À défaut
	 * (en-tête absent ou modifié), la position enregistrée dans le mapping est
	 * utilisée.
	 *
	 * @return les colonnes résolues, ou null si l'onglet ne peut pas être lu
	 */
	private Map<TemplateField, Integer> resolveColumns(Sheet sheet) {

		Map<TemplateField, Integer> columns = new EnumMap<>(TemplateField.class);

		Row headerRow = mapping.getHeaderRow() > 0 ? sheet.getRow(mapping.getHeaderRow() - 1) : null;

		
		Map<String, Integer> headerIndexes = new HashMap<>();

		if (headerRow != null) {

			for (int columnIndex = 0; columnIndex < Math.max(headerRow.getLastCellNum(), 0); columnIndex++) {

				String normalizedHeader = TemplateField
						.normalize(SquashTmUtils.getCellValue(headerRow, columnIndex, dataFormatter));

				if (!normalizedHeader.isEmpty() && !headerIndexes.containsKey(normalizedHeader)) {
					headerIndexes.put(normalizedHeader, columnIndex);
				}
			}
		}

		Set<Integer> usedColumns = new HashSet<>();

		for (TemplateColumnMapping column : mapping.getColumns()) {

			TemplateField field = column.getTemplateField();

			if (field == null) {
				continue;
			}

			String expectedLabel = TemplateField.normalize(column.getHeaderLabel());

			Integer index = expectedLabel.isEmpty() ? null : headerIndexes.get(expectedLabel);

			if (index == null && column.getColumnIndex() >= 0) {

				index = column.getColumnIndex();

				checkHeaderConformity(sheet, headerRow, column, expectedLabel, index);
			}

			if (index == null) {
				continue;
			}

			if (!usedColumns.add(index)) {

				addInconsistency("Onglet '" + sheet.getSheetName() + "' : la colonne "
						+ TemplateColumnMapping.columnLetter(index)
						+ " est associée à plusieurs éléments du mapping. L'onglet est ignoré. Vérifiez le mapping dans l'administration.");

				return null;
			}

			columns.put(field, index);
		}

		for (TemplateField field : TemplateField.values()) {

			if (field.isRequired() && !columns.containsKey(field)) {

				addInconsistency("Onglet '" + sheet.getSheetName() + "' : la colonne « " + field.getDefaultLabel()
						+ " » est introuvable avec le mapping du template. L'onglet est ignoré.");

				return null;
			}
		}

		return columns;
	}

	
	private void checkHeaderConformity(Sheet sheet, Row headerRow, TemplateColumnMapping column, String expectedLabel,
			int index) {

		if (headerRow == null || expectedLabel.isEmpty()) {
			return;
		}

		String foundHeader = SquashTmUtils.getCellValue(headerRow, index, dataFormatter);
		String normalizedFound = TemplateField.normalize(foundHeader);

		if (normalizedFound.isEmpty() || normalizedFound.contains(expectedLabel)
				|| expectedLabel.contains(normalizedFound)) {
			return;
		}

		addInconsistency("Onglet '" + sheet.getSheetName() + "' : l'en-tête de la colonne "
				+ TemplateColumnMapping.columnLetter(index) + " est « " + foundHeader
				+ " » alors que le mapping attend « " + column.getHeaderLabel() + " » pour « "
				+ column.getTemplateField().getDefaultLabel() + " ». Vérifiez le mapping dans l'administration.");
	}

	
	private String getCellValue(Row row, Map<TemplateField, Integer> columns, TemplateField field) {

		Integer columnIndex = columns.get(field);

		if (columnIndex == null) {
			return "";
		}

		return SquashTmUtils.getCellValue(row, columnIndex.intValue(), dataFormatter);
	}

}