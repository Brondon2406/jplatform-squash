package co.kozao.jcmsplugin.squashtm.service;

import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonObject;

import co.kozao.jcmsplugin.squashtm.model.squashimport.ImportSquashTmData;
import co.kozao.jcmsplugin.squashtm.model.squashimport.ImportSquashTmFeature;
import co.kozao.jcmsplugin.squashtm.model.squashimport.ImportSquashTmModule;
import co.kozao.jcmsplugin.squashtm.model.squashimport.ImportSquashTmRequirement;
import co.kozao.jcmsplugin.squashtm.model.squashimport.ImportSquashTmResult;
import co.kozao.jcmsplugin.squashtm.model.squashimport.ImportSquashTmTestCase;
import co.kozao.jcmsplugin.squashtm.model.squashimport.ImportSquashTmTestStep;
import co.kozao.jcmsplugin.squashtm.model.squashimport.ImportSquashTmView;

/**
 * Manager chargé d'orchestrer l'import d'un fichier Excel vers Squash TM.
 *
 * <p>
 * Ce manager ne conserve aucune donnée après l'import. Les objets utilisés
 * pendant l'import sont uniquement temporaires.
 * </p>
 */
public class SquashTmImportManager {

	private static final Logger LOGGER = Logger.getLogger(SquashTmImportManager.class);

	private final ExcelSquashTmImportService excelImportService;
	private final SquashTmService squashTmService;

	/**
	 * Constructeur.
	 */
	public SquashTmImportManager() {
		this.excelImportService = new ExcelSquashTmImportService();
		this.squashTmService = new SquashTmService();
	}

	/**
	 * Lance l'import complet d'un fichier Excel vers Squash TM.
	 *
	 * @param excelFile   fichier Excel à importer
	 * @param projectName nom du projet Squash TM
	 * @return résultat final de l'import
	 */
	public ImportSquashTmResult importExcel(java.io.File excelFile, String projectName) {

		ImportSquashTmResult result = new ImportSquashTmResult();

		/*
		 * Informations générales du résultat.
		 */
		result.setProjectName(projectName);

		if (excelFile != null) {
			result.setFileName(excelFile.getName());
		}

		LOGGER.info("========== DEBUT IMPORT SQUASH TM ==========");

		LOGGER.info("Projet : " + projectName);

		LOGGER.info("Fichier : " + (excelFile != null ? excelFile.getName() : "null"));

		try {

			/*
			 * ============================================================ 1. VALIDATION
			 * ============================================================
			 */

			if (excelFile == null) {

				result.addTechnicalError("Aucun fichier Excel n'a été fourni.");

				return result;
			}

			if (!excelFile.exists() || !excelFile.isFile()) {

				result.addTechnicalError("Le fichier Excel fourni est introuvable.");

				return result;
			}

			if (projectName == null || projectName.trim().isEmpty()) {

				result.addTechnicalError("Le nom du projet Squash TM est vide.");

				return result;
			}

			/*
			 * ============================================================ 2. LECTURE DU
			 * FICHIER EXCEL ============================================================
			 */

			LOGGER.info("Lecture du fichier Excel...");

			ImportSquashTmData importData = excelImportService.parse(excelFile, projectName);

			/*
			 * Récupération des incohérences détectées pendant la lecture du fichier Excel.
			 */
			for (String inconsistency : excelImportService.getInconsistencies()) {

				result.addInconsistency(inconsistency);
			}

			if (importData == null) {

				result.addTechnicalError("Impossible de lire les données du fichier Excel.");

				return result;
			}

			/*
			 * ============================================================ 3. IMPORT VERS
			 * SQUASH TM ============================================================
			 */

			importToSquashTm(importData, result);

		} catch (Exception e) {

			LOGGER.error("Erreur technique pendant l'import Squash TM.", e);

			result.addTechnicalError("Erreur technique pendant l'import : " + e.getMessage());
		}

		/*
		 * ================================================================ 4.
		 * FINALISATION ================================================================
		 */

		result.setSuccess(result.getTechnicalErrors().isEmpty());

		LOGGER.info("========== FIN IMPORT SQUASH TM ==========");

		LOGGER.info("Succès : " + result.isSuccess());

		LOGGER.info("Exigences : " + result.getRequirementCount());

		LOGGER.info("Cas de test : " + result.getTestCaseCount());

		LOGGER.info("Étapes : " + result.getTestStepCount());

		return result;
	}

	/**
	 * Effectue l'import des données déjà analysées vers Squash TM.
	 *
	 * @param importData données provenant du fichier Excel
	 * @param result     résultat de l'import
	 * @throws Exception si une erreur technique survient
	 */
	private void importToSquashTm(ImportSquashTmData importData, ImportSquashTmResult result) throws Exception {

		LOGGER.info("========== IMPORT VERS SQUASH TM ==========");

		/*
		 * 1. Recherche du projet.
		 */
		LOGGER.info("Recherche du projet : " + importData.getProjectName());

		/*
		 * ============================================================ 1. RECHERCHE DU
		 * PROJET ============================================================
		 */

		String projectName = importData.getProjectName();

		LOGGER.info("Recherche du projet Squash TM : " + projectName);

		JsonObject existingProject = squashTmService.findProjectByName(projectName);

		long projectId;

		/*
		 * ============================================================ 2. PROJET
		 * EXISTANT ============================================================
		 */

		if (existingProject != null) {

			if (!existingProject.has("id") || existingProject.get("id").isJsonNull()) {

				throw new IOException("Le projet Squash TM existe mais son identifiant " + "n'a pas été retourné.");
			}

			projectId = existingProject.get("id").getAsLong();

			LOGGER.info("Projet Squash TM existant trouvé : " + projectName + " (id=" + projectId + ")");

			/*
			 * Le projet lui-même est conservé.
			 *
			 * Seul son contenu est supprimé afin de pouvoir recréer le projet à partir du
			 * nouveau fichier Excel.
			 */
			LOGGER.info("Suppression de l'ancien contenu du projet...");

			squashTmService.deleteProjectContent(projectId);

			LOGGER.info("Ancien contenu supprimé.");

		} else {

			/*
			 * ======================================================== 3. PROJET INEXISTANT
			 * ========================================================
			 */

			LOGGER.info("Aucun projet existant trouvé.");

			LOGGER.info("Création du projet Squash TM : " + projectName);

			JsonObject createdProject = squashTmService.createProject(projectName);

			if (createdProject == null || !createdProject.has("id") || createdProject.get("id").isJsonNull()) {

				throw new IOException("Le projet Squash TM a été créé " + "mais son identifiant n'a pas été retourné.");
			}

			projectId = createdProject.get("id").getAsLong();

			LOGGER.info("Projet Squash TM créé avec succès : " + projectName + " (id=" + projectId + ")");
		}

		/*
		 * ============================================================ 4. MAPPINGS
		 * TEMPORAIRES DES DOSSIERS
		 * ============================================================
		 *
		 * Ces mappings servent uniquement pendant l'import.
		 *
		 * Ils permettent de retrouver l'ID Squash TM d'un dossier à partir de son
		 * chemin logique.
		 *
		 * Exemple :
		 *
		 * Gestion des documents / Vue moderne / Ajout
		 *
		 * sera associé à un ID de dossier Requirements et à un autre ID de dossier Test
		 * Cases.
		 *
		 * Rien n'est conservé après l'import.
		 */
		Map<String, Long> requirementFolderIds = new HashMap<>();

		Map<String, Long> testCaseFolderIds = new HashMap<>();

		/*
		 * ============================================================ 5. CRÉATION DE
		 * LA HIÉRARCHIE ============================================================
		 */

		LOGGER.info("Création de la hiérarchie des dossiers Squash TM...");

		for (ImportSquashTmModule module : importData.getModules()) {

			if (module == null || isEmpty(module.getName())) {
				continue;
			}

			String moduleName = module.getName().trim();

			LOGGER.info("Création du module : " + moduleName);

			/*
			 * -------------------------------------------------------- MODULE -
			 * REQUIREMENTS --------------------------------------------------------
			 */

			JsonObject requirementModule = squashTmService.createRequirementFolder(moduleName, "project", projectId);

			if (requirementModule == null || !requirementModule.has("id")) {

				throw new IOException("Impossible de créer le dossier Requirement " + "du module : " + moduleName);
			}

			long requirementModuleId = requirementModule.get("id").getAsLong();

			/*
			 * -------------------------------------------------------- MODULE - TEST CASES
			 * --------------------------------------------------------
			 */

			JsonObject testCaseModule = squashTmService.createTestCaseFolder(moduleName, "project", projectId);

			if (testCaseModule == null || !testCaseModule.has("id")) {

				throw new IOException("Impossible de créer le dossier Test Case " + "du module : " + moduleName);
			}

			long testCaseModuleId = testCaseModule.get("id").getAsLong();

			/*
			 * -------------------------------------------------------- VUES
			 * --------------------------------------------------------
			 */

			for (ImportSquashTmView view : module.getViews()) {

				if (view == null || isEmpty(view.getName())) {
					continue;
				}

				String viewName = view.getName().trim();

				String requirementViewPath = buildFolderPath(moduleName, viewName, null);

				String testCaseViewPath = buildFolderPath(moduleName, viewName, null);

				/*
				 * Vue Requirements
				 */

				JsonObject requirementView = squashTmService.createRequirementFolder(viewName, "requirement-folder",
						requirementModuleId);

				if (requirementView == null || !requirementView.has("id")) {

					throw new IOException("Impossible de créer le dossier Requirement " + "de la vue : " + viewName);
				}

				long requirementViewId = requirementView.get("id").getAsLong();

				requirementFolderIds.put(requirementViewPath, requirementViewId);

				/*
				 * Vue Test Cases
				 */

				JsonObject testCaseView = squashTmService.createTestCaseFolder(viewName, "test-case-folder",
						testCaseModuleId);

				if (testCaseView == null || !testCaseView.has("id")) {

					throw new IOException("Impossible de créer le dossier Test Case " + "de la vue : " + viewName);
				}

				long testCaseViewId = testCaseView.get("id").getAsLong();

				testCaseFolderIds.put(testCaseViewPath, testCaseViewId);

				/*
				 * ---------------------------------------------------- FONCTIONNALITÉS
				 * ----------------------------------------------------
				 */

				for (ImportSquashTmFeature feature : view.getFeatures()) {

					if (feature == null || isEmpty(feature.getName())) {

						continue;
					}

					String featureName = feature.getName().trim();

					String requirementFeaturePath = buildFolderPath(moduleName, viewName, featureName);

					String testCaseFeaturePath = buildFolderPath(moduleName, viewName, featureName);

					/*
					 * Feature Requirements
					 */

					JsonObject requirementFeature = squashTmService.createRequirementFolder(featureName,
							"requirement-folder", requirementViewId);

					if (requirementFeature == null || !requirementFeature.has("id")) {

						throw new IOException("Impossible de créer le dossier Requirement " + "de la fonctionnalité : "
								+ featureName);
					}

					long requirementFeatureId = requirementFeature.get("id").getAsLong();

					requirementFolderIds.put(requirementFeaturePath, requirementFeatureId);

					/*
					 * Feature Test Cases
					 */

					JsonObject testCaseFeature = squashTmService.createTestCaseFolder(featureName, "test-case-folder",
							testCaseViewId);

					if (testCaseFeature == null || !testCaseFeature.has("id")) {

						throw new IOException(
								"Impossible de créer le dossier Test Case " + "de la fonctionnalité : " + featureName);
					}

					long testCaseFeatureId = testCaseFeature.get("id").getAsLong();

					testCaseFolderIds.put(testCaseFeaturePath, testCaseFeatureId);
				}
			}
		}

		LOGGER.info("Hiérarchie Squash TM créée avec succès.");
		/*
		 * ============================================================ 6. CRÉATION DES
		 * REQUIREMENTS ET DES TEST CASES
		 * ============================================================
		 */

		LOGGER.info("Création des requirements et des cas de test...");

		for (ImportSquashTmModule module : importData.getModules()) {

			if (module == null || isEmpty(module.getName())) {
				continue;
			}

			String moduleName = module.getName().trim();

			for (ImportSquashTmView view : module.getViews()) {

				if (view == null || isEmpty(view.getName())) {
					continue;
				}

				String viewName = view.getName().trim();

				for (ImportSquashTmFeature feature : view.getFeatures()) {

					if (feature == null || isEmpty(feature.getName())) {

						continue;
					}

					String featureName = feature.getName().trim();

					/*
					 * Chemins permettant de retrouver les IDs des deux dossiers Squash TM.
					 */
					String folderPath = buildFolderPath(moduleName, viewName, featureName);

					Long requirementFolderId = requirementFolderIds.get(folderPath);

					Long testCaseFolderId = testCaseFolderIds.get(folderPath);

					if (requirementFolderId == null) {

						result.addTechnicalError("Dossier Requirement introuvable : " + folderPath);

						continue;
					}

					if (testCaseFolderId == null) {

						result.addTechnicalError("Dossier Test Case introuvable : " + folderPath);

						continue;
					}

					/*
					 * ================================================= REQUIREMENTS
					 * =================================================
					 */

					for (ImportSquashTmRequirement requirement : feature.getRequirements()) {

						if (requirement == null) {
							continue;
						}

						if (isEmpty(requirement.getName())) {

							result.addInconsistency("Requirement sans nom dans : " + folderPath);

							continue;
						}

						String requirementName = requirement.getName().trim();

						String reference = requirement.getReference();

						try {

							LOGGER.info("Création du requirement : " + reference + " - " + requirementName);

							squashTmService.createRequirement(requirementName, reference, requirementFolderId);

							result.setRequirementCount(result.getRequirementCount() + 1);

						} catch (Exception e) {

							LOGGER.error("Erreur lors de la création du requirement : " + requirementName, e);

							result.addTechnicalError(
									"Impossible de créer le requirement '" + requirementName + "' : " + e.getMessage());
						}
					}

					/*
					 * ================================================= TEST CASES
					 * =================================================
					 */

					for (ImportSquashTmTestCase testCase : feature.getTestCases()) {

						if (testCase == null) {
							continue;
						}

						if (isEmpty(testCase.getName())) {

							result.addInconsistency("Cas de test sans nom dans : " + folderPath);

							continue;
						}

						String testCaseName = testCase.getName().trim();

						String reference = testCase.getReference();

						try {

							LOGGER.info("Création du cas de test : " + reference + " - " + testCaseName);

							JsonObject createdTestCase = squashTmService.createTestCase(testCaseName, reference,
									testCaseFolderId);

							if (createdTestCase == null || !createdTestCase.has("id")
									|| createdTestCase.get("id").isJsonNull()) {

								throw new IOException("L'ID du cas de test n'a pas été retourné.");
							}

							long testCaseId = createdTestCase.get("id").getAsLong();

							result.setTestCaseCount(result.getTestCaseCount() + 1);

							/*
							 * ========================================= STEPS DU TEST CASE
							 * =========================================
							 */

							for (ImportSquashTmTestStep step : testCase.getSteps()) {

								if (step == null) {
									continue;
								}

								String action = step.getAction();

								String expectedResult = step.getExpectedResult();

								/*
								 * Une étape doit obligatoirement posséder une action ET un résultat attendu.
								 */
								if (isEmpty(action) || isEmpty(expectedResult)) {

									result.addInconsistency("Étape invalide pour le cas de test '" + testCaseName
											+ "' dans " + folderPath + " : action ou résultat attendu manquant.");

									continue;
								}

								try {

									squashTmService.createTestStep(testCaseId, action.trim(), expectedResult.trim());

									result.setTestStepCount(result.getTestStepCount() + 1);

								} catch (Exception e) {

									LOGGER.error("Erreur lors de la création " + "d'une étape du cas de test : "
											+ testCaseName, e);

									result.addTechnicalError("Impossible de créer une étape " + "du cas de test '"
											+ testCaseName + "' : " + e.getMessage());
								}
							}

						} catch (Exception e) {

							LOGGER.error("Erreur lors de la création du cas de test : " + testCaseName, e);

							result.addTechnicalError(
									"Impossible de créer le cas de test '" + testCaseName + "' : " + e.getMessage());
						}
					}
				}
			}
		}

		LOGGER.info("Requirements, cas de test et étapes créés.");
	}

	/**
	 * Construit une clé unique représentant le chemin hiérarchique d'un dossier.
	 *
	 * Exemple :
	 *
	 * Module / Vue / Fonctionnalité
	 *
	 * devient :
	 *
	 * Module/Vue/Fonctionnalité
	 */
	private String buildFolderPath(String moduleName, String viewName, String featureName) {

		StringBuilder path = new StringBuilder();

		if (!isEmpty(moduleName)) {
			path.append(moduleName.trim());
		}

		if (!isEmpty(viewName)) {
			path.append("/").append(viewName.trim());
		}

		if (!isEmpty(featureName)) {
			path.append("/").append(featureName.trim());
		}

		return path.toString();
	}

	/**
	 * Vérifie si une chaîne est nulle ou vide.
	 */
	private boolean isEmpty(String value) {

		return value == null || value.trim().isEmpty();
	}
}