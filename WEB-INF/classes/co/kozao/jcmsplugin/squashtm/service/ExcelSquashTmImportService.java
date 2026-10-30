package co.kozao.jcmsplugin.squashtm.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import co.kozao.jcmsplugin.squashtm.model.squashimport.ImportSquashTmAnalysis;
import co.kozao.jcmsplugin.squashtm.model.squashimport.ImportSquashTmData;
import co.kozao.jcmsplugin.squashtm.model.squashimport.ImportSquashTmFeature;
import co.kozao.jcmsplugin.squashtm.model.squashimport.ImportSquashTmModule;
import co.kozao.jcmsplugin.squashtm.model.squashimport.ImportSquashTmSubModule;
import co.kozao.jcmsplugin.squashtm.model.squashimport.ImportSquashTmRequirement;
import co.kozao.jcmsplugin.squashtm.model.squashimport.ImportSquashTmTestCase;
import co.kozao.jcmsplugin.squashtm.model.squashimport.ImportSquashTmTestStep;
import co.kozao.jcmsplugin.squashtm.model.squashimport.ImportSquashTmView;

/**
 * Service responsable de la lecture du fichier Excel utilisé pour l'import
 * Squash TM.
 *
 * Ce service ne communique pas avec Squash TM.
 *
 * Son rôle est uniquement de transformer :
 *
 * Excel ImportData
 */
public class ExcelSquashTmImportService {

	/**
	 * Index des colonnes du template Excel.
	 *
	 * A : Sous-module (géré par la logique existante) B : Vue C : Fonctionnalité D
	 * : Libellé de l'exigence et du cas de test E : Action de l'étape F : Résultat
	 * attendu
	 */
	private static final int COLUMN_SUBMODULE = 0;
	private static final int COLUMN_VIEW = 1;
	private static final int COLUMN_FEATURE = 2;
	private static final int COLUMN_ELEMENT = 3;
	private static final int COLUMN_ACTION = 4;
	private static final int COLUMN_EXPECTED_RESULT = 5;

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

	/**
	 * Identifiants des éléments déjà rencontrés pendant l'analyse.
	 *
	 * La détection tient compte du contexte hiérarchique et du contenu fonctionnel
	 * de la ligne.
	 */
	private final Set<String> seenRows = new HashSet<>();

	/**
	 * Numéros des lignes Excel comportant au moins une anomalie. Permet de ne
	 * compter chaque ligne qu'une seule fois.
	 */
	private final Set<String> anomalousLines = new HashSet<>();

	/**
	 * Incohérences détectées pendant la lecture du fichier Excel.
	 *
	 * Cette liste est uniquement utilisée pendant l'import. Aucune donnée n'est
	 * persistée.
	 */
	private final List<String> inconsistencies = new ArrayList<>();

	/**
	 * Formateur permettant de récupérer proprement le contenu des cellules Excel.
	 */
	private final DataFormatter dataFormatter;

	public ExcelSquashTmImportService() {
		this.dataFormatter = new DataFormatter();
	}

	/**
	 * Ajoute une incohérence détectée dans le fichier Excel.
	 *
	 * @param message description de l'incohérence
	 */
	private void addInconsistency(String message) {

		if (message != null && !message.trim().isEmpty()) {

			inconsistencies.add(message);
		}
	}

	/**
	 * Enregistre une anomalie pour une ligne Excel.
	 *
	 * Plusieurs anomalies peuvent être associées à la même ligne, mais cette ligne
	 * ne sera comptée qu'une seule fois.
	 */
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

	/**
	 * Retourne le nombre de modules retenus.
	 */
	public int getModuleCount() {
		return moduleCount;
	}

	/**
	 * Retourne le nombre de sous-modules créés.
	 */
	public int getSubModuleCount() {
		return subModuleCount;
	}

	/**
	 * Retourne le nombre de vues créées.
	 */
	public int getViewCount() {
		return viewCount;
	}

	/**
	 * Retourne le nombre de fonctionnalités créées.
	 */
	public int getFeatureCount() {
		return featureCount;
	}

	/**
	 * Retourne le nombre d'exigences construites.
	 */
	public int getRequirementCount() {
		return requirementCounter;
	}

	/**
	 * Retourne le nombre de cas de test construits.
	 */
	public int getTestCaseCount() {
		return testCaseCounter;
	}

	/**
	 * Retourne le nombre d'étapes de test construites.
	 */
	public int getTestStepCount() {
		return testStepCount;
	}

	/**
	 * Retourne le nombre de lignes de données détectées.
	 */
	public int getDetectedLineCount() {
		return detectedLineCount;
	}

	/**
	 * Retourne le nombre de lignes valides.
	 */
	public int getValidLineCount() {
		return validLineCount;
	}

	/**
	 * Retourne le nombre de lignes comportant au moins une anomalie.
	 */
	public int getAnomalousLineCount() {
		return anomalousLineCount;
	}

	/**
	 * Retourne le nombre de lignes dupliquées détectées.
	 */
	public int getDuplicateLineCount() {
		return duplicateLineCount;
	}

	/**
	 * Construit l'objet de synthèse après l'analyse du fichier.
	 *
	 * @param projectName nom du projet demandé
	 * @return les statistiques et les anomalies détectées
	 */
	public ImportSquashTmAnalysis buildAnalysis(String projectName) {

		return new ImportSquashTmAnalysis(projectName, detectedLineCount, validLineCount, anomalousLineCount,
				duplicateLineCount, getInconsistencies());
	}

	/**
	 * Lit un fichier Excel et construit les données nécessaires à l'import Squash
	 * TM.
	 *
	 * @param excelFile   fichier Excel
	 * @param projectName nom du projet Squash TM
	 *
	 * @return les données extraites du fichier
	 *
	 * @throws IOException si le fichier ne peut pas être lu
	 */
	public ImportSquashTmData parse(File excelFile, String projectName) throws IOException {

		if (excelFile == null) {
			throw new IllegalArgumentException("Le fichier Excel est obligatoire.");
		}

		if (!excelFile.exists()) {
			throw new IllegalArgumentException("Le fichier Excel n'existe pas : " + excelFile.getAbsolutePath());
		}

		if (projectName == null || projectName.trim().isEmpty()) {
			throw new IllegalArgumentException("Le nom du projet est obligatoire.");
		}

		/*
		 * Les compteurs sont réinitialisés pour chaque nouvel import.
		 */
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

			for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {

				Sheet sheet = workbook.getSheetAt(sheetIndex);

				parseSheet(sheet, importData);
			}
		}
		// Une ligne valide est une ligne détectée sans anomalie.
		validLineCount = detectedLineCount - anomalousLineCount;

		return importData;
	}

	/**
	 * Construit une clé de comparaison tenant compte du contexte hiérarchique.
	 *
	 * @param subModuleName    sous-module présent sur la ligne
	 * @param viewName         vue présente sur la ligne
	 * @param featureName      fonctionnalité présente sur la ligne
	 * @param elementValue     exigence ou cas de test
	 * @param action           action de test
	 * @param expectedResult   résultat attendu
	 * @param currentSubModule sous-module courant
	 * @param currentView      vue courante
	 * @param currentFeature   fonctionnalité courante
	 * @return clé de comparaison de la ligne
	 */
	private String buildDuplicateKey(String subModuleName, String viewName, String featureName, String elementValue, String action, String expectedResult, ImportSquashTmSubModule currentSubModule,
			ImportSquashTmView currentView, ImportSquashTmFeature currentFeature) {
		String subModule = !isEmpty(subModuleName) ? subModuleName.trim()
				: currentSubModule == null ? "" : currentSubModule.getName();
		String view = !isEmpty(viewName) ? viewName.trim() : currentView == null ? "" : currentView.getName();
		String feature = !isEmpty(featureName) ? featureName.trim()
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

		if (isEmpty(moduleName)) {
			return;
		}

		ImportSquashTmModule module = new ImportSquashTmModule(moduleName.trim());

		/*
		 * Contexte courant.
		 *
		 * Une cellule vide dans les colonnes hiérarchiques signifie que l'on reste dans
		 * le contexte précédent.
		 */
		ImportSquashTmSubModule currentSubModule = null;
		ImportSquashTmView currentView = null;
		ImportSquashTmFeature currentFeature = null;
		ImportSquashTmTestCase currentTestCase = null;

		/*
		 * La troisième ligne est la ligne d'en-tête.
		 */
		int firstDataRow = 3;

		for (int rowIndex = firstDataRow; rowIndex <= sheet.getLastRowNum(); rowIndex++) {

			Row row = sheet.getRow(rowIndex);

			if (row == null) {
				continue;
			}

			// Les lignes vides et la ligne d'en-tête ne sont pas comptées.

			String subModuleName = getCellValue(row, COLUMN_SUBMODULE);

			String viewName = getCellValue(row, COLUMN_VIEW);

			String featureName = getCellValue(row, COLUMN_FEATURE);

			String elementValue = getCellValue(row, COLUMN_ELEMENT);

			String action = getCellValue(row, COLUMN_ACTION);

			String expectedResult = getCellValue(row, COLUMN_EXPECTED_RESULT);

			/*
			 * Une ligne totalement vide est ignorée.
			 */
			if (isEmpty(subModuleName) && isEmpty(viewName) && isEmpty(featureName) && isEmpty(elementValue)
					&& isEmpty(action) && isEmpty(expectedResult)) {

				continue;
			}

			detectedLineCount++;

			// Construction d'une clé tenant compte de la hiérarchie.
			String duplicateKey = buildDuplicateKey(subModuleName, viewName, featureName, elementValue, action,
					expectedResult, currentSubModule, currentView, currentFeature);

			// Une ligne est signalée si son contenu et son contexte
			// correspondent à une ligne déjà rencontrée.
			if (!seenRows.add(duplicateKey)) {

				duplicateLineCount++;

				addLineInconsistency(sheet, row, "Onglet '" + sheet.getSheetName() + "', ligne " + (row.getRowNum() + 1)
						+ " : ligne dupliquée détectée.");
			}

			/*
			 * -------------------------------------------------- SOUS-MODULE
			 * --------------------------------------------------
			 */
			if (!isEmpty(subModuleName)) {

				currentSubModule = findOrCreateSubModule(module, subModuleName);

				// Une nouvelle branche de la hiérarchie commence.
				currentView = null;
				currentFeature = null;
				currentTestCase = null;
			}

			/*
			 * -------------------------------------------------- VUE
			 * --------------------------------------------------
			 */

			if (!isEmpty(viewName)) {

				if (currentSubModule == null) {

					addLineInconsistency(sheet, row, "Onglet '" + sheet.getSheetName() + "', ligne "
							+ (row.getRowNum() + 1) + " : vue sans sous-module valide.");
					currentView = null;
					currentFeature = null;
					currentTestCase = null;

					continue;
				}

				currentView = findOrCreateView(currentSubModule, viewName);

				currentFeature = null;
				currentTestCase = null;
			}

			/*
			 * -------------------------------------------------- FONCTIONNALITÉ
			 * --------------------------------------------------
			 */
			if (!isEmpty(featureName)) {

				if (currentView == null) {
					continue;
				}

				currentFeature = findOrCreateFeature(currentView, featureName);

				/*
				 * Une nouvelle fonctionnalité signifie que le cas de test courant est
				 * réinitialisé.
				 */
				currentTestCase = null;
			}

			/*
			 * -------------------------------------------------- EXIGENCE ET CAS DE TEST
			 * --------------------------------------------------
			 */
			if (!isEmpty(elementValue)) {

				if (currentFeature == null) {

					addLineInconsistency(sheet, row, "Onglet '" + sheet.getSheetName() + "', ligne "
							+ (row.getRowNum() + 1) + " : élément sans fonctionnalité valide.");
					currentTestCase = null;

				} else {

					String elementName = elementValue.trim();

					// Création de l'exigence.
					ImportSquashTmRequirement requirement = new ImportSquashTmRequirement(
							generateRequirementReference(), elementName);

					currentFeature.addRequirement(requirement);

					// Création du cas de test portant le même nom.
					ImportSquashTmTestCase testCase = new ImportSquashTmTestCase(generateTestCaseReference(),
							elementName);

					currentFeature.addTestCase(testCase);

					// Les actions suivantes seront rattachées à ce cas de test.
					currentTestCase = testCase;
				}
			}

			/*
			 * -------------------------------------------------- ACTION + RESULTAT ATTENDU
			 * --------------------------------------------------
			 *
			 * Les actions appartiennent uniquement au cas de test courant.
			 */
			if (!isEmpty(action) || !isEmpty(expectedResult)) {

				if (currentTestCase == null) {

					addLineInconsistency(sheet, row, "Onglet '" + sheet.getSheetName() + "', ligne "
							+ (row.getRowNum() + 1) + " : action ou résultat attendu présent sans cas de test valide.");

				} else if (isEmpty(action)) {

					addLineInconsistency(sheet, row,
							"Onglet '" + sheet.getSheetName() + "', ligne " + (row.getRowNum() + 1)
									+ " : action manquante pour le cas de test '" + currentTestCase.getName() + "'.");

				} else if (isEmpty(expectedResult)) {

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
		/*
		 * Le module est ajouté s'il contient au moins un sous-module.
		 */
		if (!module.getSubModules().isEmpty()) {
			importData.addModule(module);
			moduleCount++;
		}
	}

	/**
	 * Recherche un sous-module existant dans le module.
	 *
	 * Si aucun sous-module ne porte ce nom, il est créé.
	 */
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

	/**
	 * Recherche une vue existante dans le sous-module.
	 *
	 * Si elle n'existe pas, elle est créée.
	 */
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

	/**
	 * Recherche une fonctionnalité existante dans une vue.
	 *
	 * Si elle n'existe pas, elle est créée.
	 */
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
	 * Génère une référence d'exigence.
	 *
	 * Exemple : REQ001 REQ002 REQ003
	 */
	private String generateRequirementReference() {

		requirementCounter++;

		return String.format("REQ%03d", requirementCounter);
	}

	/**
	 * Génère une référence de cas de test.
	 *
	 * Exemple : TC001 TC002 TC003
	 */
	private String generateTestCaseReference() {

		testCaseCounter++;

		return String.format("TC%03d", testCaseCounter);
	}

	/**
	 * Récupère la valeur d'une cellule.
	 */
	private String getCellValue(Row row, int columnIndex) {

		Cell cell = row.getCell(columnIndex);

		if (cell == null) {
			return "";
		}

		return dataFormatter.formatCellValue(cell).trim();
	}

	/**
	 * Vérifie si une chaîne est vide.
	 */
	private boolean isEmpty(String value) {

		return value == null || value.trim().isEmpty();
	}
}
