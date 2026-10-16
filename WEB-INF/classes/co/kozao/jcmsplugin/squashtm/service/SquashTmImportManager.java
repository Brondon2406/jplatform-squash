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
import co.kozao.jcmsplugin.squashtm.model.squashimport.ImportSquashTmProgress;

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
	 * Constructeur utilisé lorsque l'importation est exécutée en arrière-plan.
	 *
	 * @param authorizationHeader en-tête d'authentification Squash TM
	 */
	public SquashTmImportManager(String authorizationHeader) {
		this.excelImportService = new ExcelSquashTmImportService();
		this.squashTmService = new SquashTmService(authorizationHeader);
	}

	/**
	 * Constructeur par défaut conservé pour assurer la compatibilité avec les
	 * autres utilisations du manager.
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
	public ImportSquashTmResult importExcel(File excelFile, String projectName, ImportSquashTmProgress progress) {

		ImportSquashTmResult result = new ImportSquashTmResult();

		if (progress == null) {
			throw new IllegalArgumentException("L'objet de progression ne doit pas être null.");
		}

		progress.update(2, "INITIALISATION", "Préparation de l'importation...");

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
			progress.update(5, "LECTURE_EXCEL", "Lecture et analyse du fichier Excel...");

			ImportSquashTmData importData = excelImportService.parse(excelFile, projectName);
			progress.update(15, "LECTURE_TERMINEE", "Fichier Excel analysé. Préparation des données...");

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

			int[] totals = countImportItems(importData);

			LOGGER.info("Total des exigences : " + totals[0]);
			LOGGER.info("Total des cas de test : " + totals[1]);
			LOGGER.info("Total des étapes : " + totals[2]);

			int totalFolders = countFolderItems(importData);

			LOGGER.info("Total des dossiers à créer : " + totalFolders);

			/*
			 * ============================================================ 3. IMPORT VERS
			 * SQUASH TM ============================================================
			 */

			progress.update(18, "IMPORT_SQUASH_TM", "Préparation du projet Squash TM...");

			importToSquashTm(importData, result, progress, totals, totalFolders);

		} catch (Exception e) {

			LOGGER.error("Erreur technique pendant l'import Squash TM.", e);

			result.addTechnicalError("Erreur technique pendant l'import : " + e.getMessage());
		}

		/*
		 * ================================================================ 4.
		 * FINALISATION ================================================================
		 */

		result.setSuccess(result.getTechnicalErrors().isEmpty());
		if (result.isSuccess()) {
			progress.finish(true, "Importation terminée avec succès.");
		} else {
			progress.finish(false, "Importation terminée avec des erreurs.");
		}

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
	private void importToSquashTm(ImportSquashTmData importData, ImportSquashTmResult result,
			ImportSquashTmProgress progress, int[] totals, int totalFolders) throws Exception {

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

		progress.update(20, "PROJET", "Recherche du projet dans Squash TM...");

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

		progress.update(25, "DOSSIERS", "Création des dossiers du projet...");

		for (ImportSquashTmModule module : importData.getModules()) {

			if (module == null || isEmpty(module.getName())) {
				continue;
			}

			String moduleName = module.getName().trim();
			if (!isValidFolderName(moduleName, "module", moduleName, result)) {
				continue;
			}

			LOGGER.info("Création du module : " + moduleName);

			Long requirementModuleId = null;

			try {
				JsonObject requirementModule = squashTmService.createRequirementFolder(moduleName, "project",
						projectId);

				if (requirementModule == null || !requirementModule.has("id")
						|| requirementModule.get("id").isJsonNull()) {
					throw new IOException("L'identifiant du dossier n'a pas été retourné.");
				}

				requirementModuleId = requirementModule.get("id").getAsLong();

				result.setFolderCount(result.getFolderCount() + 1);

			} catch (Exception e) {
				String message = "Impossible de créer le dossier Requirement du module '" + moduleName + "' : "
						+ e.getMessage();

				LOGGER.error(message, e);
				result.addTechnicalError(message);
			}
			result.setFolderCount(result.getFolderCount() + 1);
			progress.update(calculateFolderProgress(result.getFolderCount(), totalFolders), "DOSSIERS",
					"Création des dossiers : " + result.getFolderCount() + "/" + totalFolders);

			// Dossier racine des cas de test pour le module.
			Long testCaseModuleId = null;

			try {
				JsonObject testCaseModule = squashTmService.createTestCaseFolder(moduleName, "project", projectId);

				if (testCaseModule == null || !testCaseModule.has("id") || testCaseModule.get("id").isJsonNull()) {
					throw new IOException("L'identifiant du dossier n'a pas été retourné.");
				}

				testCaseModuleId = testCaseModule.get("id").getAsLong();

				result.setFolderCount(result.getFolderCount() + 1);

			} catch (Exception e) {
				String message = "Impossible de créer le dossier Test Case du module '" + moduleName + "' : "
						+ e.getMessage();

				LOGGER.error(message, e);
				result.addTechnicalError(message);
			}
			result.setFolderCount(result.getFolderCount() + 1);
			progress.update(calculateFolderProgress(result.getFolderCount(), totalFolders), "DOSSIERS",
					"Création des dossiers : " + result.getFolderCount() + "/" + totalFolders);

			// Parcours des sous-modules du module.
			for (ImportSquashTmSubModule subModule : module.getSubModules()) {

				if (subModule == null || isEmpty(subModule.getName())) {
					continue;
				}

				String subModuleName = subModule.getName().trim();
				if (!isValidFolderName(subModuleName, "sous-module", moduleName + " / " + subModuleName, result)) {
					continue;
				}

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
				progress.update(calculateFolderProgress(result.getFolderCount(), totalFolders), "DOSSIERS",
						"Création des dossiers : " + result.getFolderCount() + "/" + totalFolders);

				// Dossier du sous-module pour les cas de test.
				JsonObject testCaseSubModule = squashTmService.createTestCaseFolder(subModuleName, "test-case-folder",
						testCaseModuleId);

				if (testCaseSubModule == null || !testCaseSubModule.has("id")
						|| testCaseSubModule.get("id").isJsonNull()) {
					throw new IOException("Impossible de créer le sous-module Test Case : " + subModuleName);
				}

				long testCaseSubModuleId = testCaseSubModule.get("id").getAsLong();
				result.setFolderCount(result.getFolderCount() + 1);
				progress.update(calculateFolderProgress(result.getFolderCount(), totalFolders), "DOSSIERS",
						"Création des dossiers : " + result.getFolderCount() + "/" + totalFolders);

				// Parcours des vues du sous-module.
				for (ImportSquashTmView view : subModule.getViews()) {

					if (view == null || isEmpty(view.getName())) {
						continue;
					}

					String viewName = view.getName().trim();
					if (!isValidFolderName(viewName, "vue", moduleName + " / " + subModuleName + " / " + viewName,
							result)) {
						continue;
					}

					// Création du dossier de vue pour les exigences.
					JsonObject requirementView = squashTmService.createRequirementFolder(viewName, "requirement-folder",
							requirementSubModuleId);

					if (requirementView == null || !requirementView.has("id")
							|| requirementView.get("id").isJsonNull()) {
						throw new IOException("Impossible de créer la vue Requirement : " + viewName);
					}

					long requirementViewId = requirementView.get("id").getAsLong();
					result.setFolderCount(result.getFolderCount() + 1);
					progress.update(calculateFolderProgress(result.getFolderCount(), totalFolders), "DOSSIERS",
							"Création des dossiers : " + result.getFolderCount() + "/" + totalFolders);

					// Création du dossier de vue pour les cas de test.
					JsonObject testCaseView = squashTmService.createTestCaseFolder(viewName, "test-case-folder",
							testCaseSubModuleId);

					if (testCaseView == null || !testCaseView.has("id") || testCaseView.get("id").isJsonNull()) {
						throw new IOException("Impossible de créer la vue Test Case : " + viewName);
					}

					long testCaseViewId = testCaseView.get("id").getAsLong();
					result.setFolderCount(result.getFolderCount() + 1);
					progress.update(calculateFolderProgress(result.getFolderCount(), totalFolders), "DOSSIERS",
							"Création des dossiers : " + result.getFolderCount() + "/" + totalFolders);

					// Parcours des fonctionnalités de la vue.
					for (ImportSquashTmFeature feature : view.getFeatures()) {

						if (feature == null || isEmpty(feature.getName())) {
							continue;
						}

						String featureName = feature.getName().trim();
						if (!isValidFolderName(featureName, "fonctionnalité",
								moduleName + " / " + subModuleName + " / " + viewName + " / " + featureName, result)) {
							continue;
						}

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
						progress.update(calculateFolderProgress(result.getFolderCount(), totalFolders), "DOSSIERS",
								"Création des dossiers : " + result.getFolderCount() + "/" + totalFolders);

						// Dossier de fonctionnalité pour les cas de test.
						JsonObject testCaseFeature = squashTmService.createTestCaseFolder(featureName,
								"test-case-folder", testCaseViewId);

						if (testCaseFeature == null || !testCaseFeature.has("id")
								|| testCaseFeature.get("id").isJsonNull()) {
							throw new IOException("Impossible de créer la fonctionnalité Test Case : " + featureName);
						}

						long testCaseFeatureId = testCaseFeature.get("id").getAsLong();
						result.setFolderCount(result.getFolderCount() + 1);
						progress.update(calculateFolderProgress(result.getFolderCount(), totalFolders), "DOSSIERS",
								"Création des dossiers : " + result.getFolderCount() + "/" + totalFolders);

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
		progress.update(60, "DONNEES", "Importation des exigences et des cas de test...");
		int completedDataItems = 0;
		int totalDataItems = totals[0] + totals[1] + totals[2];

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
								completedDataItems++;

								progress.update(calculateDataProgress(completedDataItems, totalDataItems), "DONNEES",
										"Exigences importées : " + result.getRequirementCount() + " / " + totals[0]);

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
								completedDataItems++;

								progress.update(calculateDataProgress(completedDataItems, totalDataItems), "DONNEES",
										"Cas de test importés : " + result.getTestCaseCount() + " / " + totals[1]);

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
										completedDataItems++;
										progress.update(Math.min(98, 60 + result.getTestStepCount()), "DONNEES",
												"Étapes importées : " + result.getTestStepCount());

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

	/**
	 * Vérifie que le nom d'un dossier respecte la limite de Squash TM.
	 *
	 * @param name   nom du dossier à vérifier
	 * @param type   type de dossier (Requirement ou Test Case)
	 * @param path   emplacement du dossier dans la hiérarchie
	 * @param result résultat de l'import pour enregistrer les erreurs
	 * @return true si le nom est valide, false sinon
	 */
	private boolean isValidFolderName(String name, String type, String path, ImportSquashTmResult result) {

		if (name != null && !name.trim().isEmpty() && name.trim().length() <= 255) {
			return true;
		}

		int length = name == null ? 0 : name.trim().length();

		String message = "Création impossible pour le dossier " + type + " : nom vide ou trop long (" + length
				+ " caractères, maximum 255). Emplacement : " + path;

		result.addTechnicalError(message);
		LOGGER.warn(message);

		return false;
	}

	/**
	 * Compte les exigences, les cas de test et les étapes présents dans les données
	 * à importer.
	 *
	 * @param importData données extraites du fichier Excel
	 * @return tableau contenant : [0] le nombre d'exigences, [1] le nombre de cas
	 *         de test, [2] le nombre d'étapes
	 */
	private int[] countImportItems(ImportSquashTmData importData) {

		int requirementCount = 0;
		int testCaseCount = 0;
		int testStepCount = 0;

		if (importData == null || importData.getModules() == null) {
			return new int[] { 0, 0, 0 };
		}

		for (ImportSquashTmModule module : importData.getModules()) {

			if (module == null || module.getSubModules() == null) {
				continue;
			}

			for (ImportSquashTmSubModule subModule : module.getSubModules()) {

				if (subModule == null || subModule.getViews() == null) {
					continue;
				}

				for (ImportSquashTmView view : subModule.getViews()) {

					if (view == null || view.getFeatures() == null) {
						continue;
					}

					for (ImportSquashTmFeature feature : view.getFeatures()) {

						if (feature == null) {
							continue;
						}

						if (feature.getRequirements() != null) {
							for (ImportSquashTmRequirement requirement : feature.getRequirements()) {

								if (requirement != null && requirement.getName() != null
										&& !requirement.getName().trim().isEmpty()) {
									requirementCount++;
								}
							}
						}

						if (feature.getTestCases() != null) {
							for (ImportSquashTmTestCase testCase : feature.getTestCases()) {

								if (testCase == null || testCase.getName() == null
										|| testCase.getName().trim().isEmpty()) {
									continue;
								}

								testCaseCount++;

								if (testCase.getSteps() != null) {
									for (ImportSquashTmTestStep step : testCase.getSteps()) {

										if (step != null && step.getAction() != null
												&& !step.getAction().trim().isEmpty()
												&& step.getExpectedResult() != null
												&& !step.getExpectedResult().trim().isEmpty()) {
											testStepCount++;
										}
									}
								}
							}
						}
					}
				}
			}
		}

		return new int[] { requirementCount, testCaseCount, testStepCount };
	}

	/**
	 * Calcule la progression de l'import des données.
	 *
	 * La progression est comprise entre 60 et 98 %.
	 *
	 * @param completed nombre d'éléments importés avec succès
	 * @param total     nombre total d'éléments prévus
	 * @return pourcentage de progression
	 */
	private int calculateDataProgress(int completed, int total) {

		if (total <= 0) {
			return 60;
		}

		int percentage = 60 + (int) (38.0 * completed / total);

		return Math.max(60, Math.min(98, percentage));
	}

	/**
	 * Calcule la progression de la création des dossiers.
	 *
	 * La progression des dossiers est comprise entre 25 et 59 %.
	 *
	 * @param completed nombre de dossiers créés
	 * @param total     nombre total de dossiers prévus
	 * @return pourcentage de progression
	 */
	private int calculateFolderProgress(int completed, int total) {

		if (total <= 0) {
			return 59;
		}

		int percentage = 25 + (int) (34.0 * completed / total);

		return Math.max(25, Math.min(59, percentage));
	}

	/**
	 * Compte le nombre total de dossiers à créer dans Squash TM. Chaque élément
	 * valide génère deux dossiers : Requirements et Test Cases.
	 */
	private int countFolderItems(ImportSquashTmData importData) {

		int folderCount = 0;

		if (importData == null || importData.getModules() == null) {
			return 0;
		}

		for (ImportSquashTmModule module : importData.getModules()) {

			if (module == null || isEmpty(module.getName())) {
				continue;
			}

			folderCount += 2;

			if (module.getSubModules() == null) {
				continue;
			}

			for (ImportSquashTmSubModule subModule : module.getSubModules()) {

				if (subModule == null || isEmpty(subModule.getName())) {
					continue;
				}

				folderCount += 2;

				if (subModule.getViews() == null) {
					continue;
				}

				for (ImportSquashTmView view : subModule.getViews()) {

					if (view == null || isEmpty(view.getName())) {
						continue;
					}

					folderCount += 2;

					if (view.getFeatures() == null) {
						continue;
					}

					for (ImportSquashTmFeature feature : view.getFeatures()) {

						if (feature == null || isEmpty(feature.getName())) {
							continue;
						}

						folderCount += 2;
					}
				}
			}
		}

		return folderCount;
	}

}