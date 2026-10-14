package co.kozao.jcmsplugin.squashtm.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

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
 * Excel ↓ ImportData
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

	/**
	 * Compteur des références d'exigences.
	 */
	private int requirementCounter;

	/**
	 * Compteur des références de cas de test.
	 */
	private int testCaseCounter;

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

	public List<String> getInconsistencies() {
		return new ArrayList<>(inconsistencies);
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
		inconsistencies.clear();

		ImportSquashTmData importData = new ImportSquashTmData(excelFile.getName(), projectName.trim());

		try (FileInputStream inputStream = new FileInputStream(excelFile);
				Workbook workbook = WorkbookFactory.create(inputStream)) {

			for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {

				Sheet sheet = workbook.getSheetAt(sheetIndex);

				parseSheet(sheet, importData);
			}
		}

		return importData;
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
		 * La première ligne est la ligne d'en-tête.
		 */
		int firstDataRow = 1;

		for (int rowIndex = firstDataRow; rowIndex <= sheet.getLastRowNum(); rowIndex++) {

			Row row = sheet.getRow(rowIndex);

			if (row == null) {
				continue;
			}

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

					addInconsistency("Onglet '" + sheet.getSheetName() + "', ligne " + (row.getRowNum() + 1)
							+ " : vue sans sous-module valide.");

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

					addInconsistency("Onglet '" + sheet.getSheetName() + "', ligne " + (row.getRowNum() + 1)
							+ " : élément sans fonctionnalité valide.");

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

					addInconsistency("Onglet '" + sheet.getSheetName() + "', ligne " + (row.getRowNum() + 1)
							+ " : action ou résultat attendu présent " + "sans cas de test valide.");

				} else if (isEmpty(action)) {

					addInconsistency("Onglet '" + sheet.getSheetName() + "', ligne " + (row.getRowNum() + 1)
							+ " : action manquante pour le cas de test '" + currentTestCase.getName() + "'.");

				} else if (isEmpty(expectedResult)) {

					addInconsistency("Onglet '" + sheet.getSheetName() + "', ligne " + (row.getRowNum() + 1)
							+ " : résultat attendu manquant pour le cas de test '" + currentTestCase.getName() + "'.");

				} else {

					ImportSquashTmTestStep step = new ImportSquashTmTestStep(action.trim(), expectedResult.trim());

					currentTestCase.addStep(step);
				}
			}
		}
		/*
		 * Le module est ajouté s'il contient au moins un sous-module.
		 */
		if (!module.getSubModules().isEmpty()) {
		    importData.addModule(module);
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
