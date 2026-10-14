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
import co.kozao.jcmsplugin.squashtm.model.squashimport.ImportSquashTmSubModule;
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
			LOGGER.error("[DIAGNOSTIC] Lecture Excel terminée.");

			if (importData == null) {
				LOGGER.error("[DIAGNOSTIC] importData est null.");
			} else {
				LOGGER.error("[DIAGNOSTIC] Nombre de modules : " + importData.getModules().size());
			}

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
		 * LA HIÉRARCHIE Projet -> Module -> Sous-module -> Vue -> Fonctionnalité
		 * ============================================================
		 */

		LOGGER.info("Création de la hiérarchie des dossiers Squash TM...");

		for (ImportSquashTmModule module : importData.getModules()) {

			if (module == null || isEmpty(module.getName())) {
				continue;
			}

			String moduleName = module.getName().trim();

			LOGGER.info("Création du module : " + moduleName);

			// Dossier racine des exigences pour le module.
			JsonObject requirementModule = squashTmService.createRequirementFolder(moduleName, "project", projectId);

			if (requirementModule == null || !requirementModule.has("id") || requirementModule.get("id").isJsonNull()) {
				throw new IOException("Impossible de créer le dossier Requirement du module : " + moduleName);
			}

			long requirementModuleId = requirementModule.get("id").getAsLong();
			result.setFolderCount(result.getFolderCount() + 1);

			// Dossier racine des cas de test pour le module.
			JsonObject testCaseModule = squashTmService.createTestCaseFolder(moduleName, "project", projectId);

			if (testCaseModule == null || !testCaseModule.has("id") || testCaseModule.get("id").isJsonNull()) {
				throw new IOException("Impossible de créer le dossier Test Case du module : " + moduleName);
			}

			long testCaseModuleId = testCaseModule.get("id").getAsLong();
			result.setFolderCount(result.getFolderCount() + 1);

			// Parcours des sous-modules du module.
			for (ImportSquashTmSubModule subModule : module.getSubModules()) {

				if (subModule == null || isEmpty(subModule.getName())) {
					continue;
				}

				String subModuleName = subModule.getName().trim();

				LOGGER.info("Création du sous-module : " + moduleName + " / " + subModuleName);

				// Dossier du sous-module pour les exigences.
				JsonObject requirementSubModule = squashTmService.createRequirementFolder(subModuleName,
						"requirement-folder", requirementModuleId);

				if (requirementSubModule == null || !requirementSubModule.has("id")
						|| requirementSubModule.get("id").isJsonNull()) {
					throw new IOException("Impossible de créer le sous-module Requirement : " + subModuleName);
				}

				long requirementSubModuleId = requirementSubModule.get("id").getAsLong();
				result.setFolderCount(result.getFolderCount() + 1);

				// Dossier du sous-module pour les cas de test.
				JsonObject testCaseSubModule = squashTmService.createTestCaseFolder(subModuleName, "test-case-folder",
						testCaseModuleId);

				if (testCaseSubModule == null || !testCaseSubModule.has("id")
						|| testCaseSubModule.get("id").isJsonNull()) {
					throw new IOException("Impossible de créer le sous-module Test Case : " + subModuleName);
				}

				long testCaseSubModuleId = testCaseSubModule.get("id").getAsLong();
				result.setFolderCount(result.getFolderCount() + 1);

				// Parcours des vues du sous-module.
				for (ImportSquashTmView view : subModule.getViews()) {

					if (view == null || isEmpty(view.getName())) {
						continue;
					}

					String viewName = view.getName().trim();

					// Création du dossier de vue pour les exigences.
					JsonObject requirementView = squashTmService.createRequirementFolder(viewName, "requirement-folder",
							requirementSubModuleId);

					if (requirementView == null || !requirementView.has("id")
							|| requirementView.get("id").isJsonNull()) {
						throw new IOException("Impossible de créer la vue Requirement : " + viewName);
					}

					long requirementViewId = requirementView.get("id").getAsLong();
					result.setFolderCount(result.getFolderCount() + 1);

					// Création du dossier de vue pour les cas de test.
					JsonObject testCaseView = squashTmService.createTestCaseFolder(viewName, "test-case-folder",
							testCaseSubModuleId);

					if (testCaseView == null || !testCaseView.has("id") || testCaseView.get("id").isJsonNull()) {
						throw new IOException("Impossible de créer la vue Test Case : " + viewName);
					}

					long testCaseViewId = testCaseView.get("id").getAsLong();
					result.setFolderCount(result.getFolderCount() + 1);

					// Parcours des fonctionnalités de la vue.
					for (ImportSquashTmFeature feature : view.getFeatures()) {

						if (feature == null || isEmpty(feature.getName())) {
							continue;
						}

						String featureName = feature.getName().trim();

						LOGGER.info("Création de la fonctionnalité : " + featureName + " | Module : " + moduleName
								+ " | Sous-module : " + subModuleName + " | Vue : " + viewName);

						// Dossier de fonctionnalité pour les exigences.
						JsonObject requirementFeature = squashTmService.createRequirementFolder(featureName,
								"requirement-folder", requirementViewId);

						if (requirementFeature == null || !requirementFeature.has("id")
								|| requirementFeature.get("id").isJsonNull()) {
							throw new IOException("Impossible de créer la fonctionnalité Requirement : " + featureName);
						}

						long requirementFeatureId = requirementFeature.get("id").getAsLong();
						result.setFolderCount(result.getFolderCount() + 1);

						// Dossier de fonctionnalité pour les cas de test.
						JsonObject testCaseFeature = squashTmService.createTestCaseFolder(featureName,
								"test-case-folder", testCaseViewId);

						if (testCaseFeature == null || !testCaseFeature.has("id")
								|| testCaseFeature.get("id").isJsonNull()) {
							throw new IOException("Impossible de créer la fonctionnalité Test Case : " + featureName);
						}

						long testCaseFeatureId = testCaseFeature.get("id").getAsLong();
						result.setFolderCount(result.getFolderCount() + 1);

						// Le chemin complet distingue les sous-modules.
						String folderPath = buildFolderPath(moduleName, subModuleName, viewName, featureName);

						requirementFolderIds.put(folderPath, requirementFeatureId);

						testCaseFolderIds.put(folderPath, testCaseFeatureId);
					}
				}
			}
		}

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

			for (ImportSquashTmSubModule subModule : module.getSubModules()) {

				if (subModule == null || isEmpty(subModule.getName())) {
					continue;
				}

				String subModuleName = subModule.getName().trim();

				for (ImportSquashTmView view : subModule.getViews()) {

					if (view == null || isEmpty(view.getName())) {
						continue;
					}

					String viewName = view.getName().trim();

					for (ImportSquashTmFeature feature : view.getFeatures()) {

						if (feature == null || isEmpty(feature.getName())) {
							continue;
						}

						String featureName = feature.getName().trim();

						String folderPath = buildFolderPath(moduleName, subModuleName, viewName, featureName);

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

						// Création des exigences.
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

								result.addTechnicalError("Impossible de créer le requirement '" + requirementName
										+ "' : " + e.getMessage());
							}
						}

						// Création des cas de test et de leurs étapes.
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

								for (ImportSquashTmTestStep step : testCase.getSteps()) {

									if (step == null) {
										continue;
									}

									String action = step.getAction();
									String expectedResult = step.getExpectedResult();

									if (isEmpty(action) || isEmpty(expectedResult)) {

										result.addInconsistency("Étape invalide pour le cas de test '" + testCaseName
												+ "' dans " + folderPath + " : action ou résultat attendu manquant.");

										continue;
									}

									try {

										squashTmService.createTestStep(testCaseId, action.trim(),
												expectedResult.trim());

										result.setTestStepCount(result.getTestStepCount() + 1);

									} catch (Exception e) {

										LOGGER.error("Erreur lors de la création d'une étape " + "du cas de test : "
												+ testCaseName, e);

										result.addTechnicalError("Impossible de créer une étape du cas de test '"
												+ testCaseName + "' : " + e.getMessage());
									}
								}

							} catch (Exception e) {

								LOGGER.error("Erreur lors de la création du cas de test : " + testCaseName, e);

								result.addTechnicalError("Impossible de créer le cas de test '" + testCaseName + "' : "
										+ e.getMessage());
							}
						}
					}
				}
			}
		}

		LOGGER.info("Requirements, cas de test et étapes créés.");

	}

	/**
	 * Construit le chemin complet d'une fonctionnalité.
	 *
	 * Exemple : Module / Sous-module / Vue / Fonctionnalité
	 */
	private String buildFolderPath(String moduleName, String subModuleName, String viewName, String featureName) {

		StringBuilder path = new StringBuilder();

		if (!isEmpty(moduleName)) {
			path.append(moduleName.trim());
		}

		if (!isEmpty(subModuleName)) {
			if (path.length() > 0) {
				path.append("/");
			}
			path.append(subModuleName.trim());
		}

		if (!isEmpty(viewName)) {
			if (path.length() > 0) {
				path.append("/");
			}
			path.append(viewName.trim());
		}

		if (!isEmpty(featureName)) {
			if (path.length() > 0) {
				path.append("/");
			}
			path.append(featureName.trim());
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