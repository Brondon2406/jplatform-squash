package co.kozao.jcmsplugin.squashtm.service;

import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jalios.util.Util;

import co.kozao.jcmsplugin.squashtm.model.squashimport.ImportSquashTmAnalysis;
import co.kozao.jcmsplugin.squashtm.model.squashimport.ImportSquashTmComparisonItem;
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
	private int createdFolderCount;
	private final Set<String> createdFolderPaths = new LinkedHashSet<>();

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
			 * =============== 1. VALIDATION ==========================
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
			 * ======================= FICHIER EXCEL =================================
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
			 * =================== SQUASH TM ====================================
			 */

			progress.update(18, "IMPORT_SQUASH_TM", "Préparation du projet Squash TM...");

			importToSquashTm(importData, result, progress, totals, totalFolders);

		} catch (Exception e) {

			LOGGER.error("Erreur technique pendant l'import Squash TM.", e);

			result.addTechnicalError("Erreur technique pendant l'import : " + e.getMessage());
		}

		/*
		 * ========================== FINALISATION =================================
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
	 * Retourne le noeud racine (le projet) d'un arbre de dossiers Squash TM.
	 *
	 * @param tree arbre retourné par l'API
	 * @return le noeud racine
	 * @throws IOException si l'arbre est invalide
	 */
	private JsonObject getTreeRoot(JsonElement tree) throws IOException {

		if (tree == null || tree.isJsonNull()) {
			throw new IOException("Arborescence Squash TM invalide.");
		}

		if (tree.isJsonArray()) {

			JsonArray roots = tree.getAsJsonArray();

			if (roots.size() == 0 || !roots.get(0).isJsonObject()) {
				throw new IOException("Racine de l'arborescence introuvable.");
			}

			return roots.get(0).getAsJsonObject();
		}

		if (tree.isJsonObject()) {
			return tree.getAsJsonObject();
		}

		throw new IOException("Format de l'arborescence Squash TM invalide.");
	}

	/**
	 * Recherche un sous-dossier direct par son nom.
	 *
	 * Selon le noeud, Squash TM place les sous-dossiers dans "folders" ou dans
	 * "children" : les deux propriétés sont donc examinées.
	 *
	 * @param parent     noeud parent (projet ou dossier)
	 * @param folderName nom recherché
	 * @return le dossier trouvé ou null
	 */
	private JsonObject findChildFolder(JsonObject parent, String folderName) {

		for (String property : new String[] { "folders", "children" }) {

			if (!parent.has(property) || !parent.get(property).isJsonArray()) {
				continue;
			}

			for (JsonElement element : parent.getAsJsonArray(property)) {

				if (!element.isJsonObject()) {
					continue;
				}

				JsonObject folder = element.getAsJsonObject();

				if (folder.has("name") && !folder.get("name").isJsonNull()
						&& folderName.equals(folder.get("name").getAsString().trim())) {

					return folder;
				}
			}
		}

		return null;
	}

	/**
	 * Retourne le tableau dans lequel ajouter un sous-dossier nouvellement créé.
	 */
	private JsonArray getWritableChildren(JsonObject parent) {

		if (parent.has("children") && parent.get("children").isJsonArray()) {
			return parent.getAsJsonArray("children");
		}

		if (parent.has("folders") && parent.get("folders").isJsonArray()) {
			return parent.getAsJsonArray("folders");
		}

		JsonArray children = new JsonArray();

		parent.add("children", children);

		return children;
	}

	/**
	 * Retourne l'identifiant du dossier ciblé en créant les dossiers manquants.
	 *
	 * Le premier niveau est rattaché au projet ("project"). Les niveaux suivants
	 * doivent être rattachés au dossier parent (type "requirement-folder" ou
	 * "test-case-folder"), sinon Squash TM cherche un projet portant l'identifiant
	 * du dossier parent.
	 */
	private long ensureTargetedFolder(JsonElement tree, String folderPath, String folderType) throws IOException {

		if (tree == null || tree.isJsonNull() || isEmpty(folderPath)) {
			throw new IOException("Arborescence ou chemin de dossier invalide.");
		}

		JsonObject currentParent = getTreeRoot(tree);

		if (!currentParent.has("id") || currentParent.get("id").isJsonNull()) {
			throw new IOException("Identifiant du projet introuvable dans l'arborescence.");
		}

		long parentId = currentParent.get("id").getAsLong();
		String parentType = "project";
		int walkedDepth = 0;
		String walkedPath = "";

		for (String rawName : folderPath.split("/")) {

			String folderName = rawName.trim();

			if (folderName.isEmpty()) {
				continue;
			}

			walkedDepth++;
			walkedPath = walkedPath.isEmpty() ? folderName : walkedPath + "/" + folderName;

			JsonObject targetFolder = findChildFolder(currentParent, folderName);

			if (targetFolder == null) {

				if ("requirement-folder".equals(folderType)) {

					targetFolder = squashTmService.createRequirementFolder(folderName, parentType, parentId);

				} else if ("test-case-folder".equals(folderType)) {

					targetFolder = squashTmService.createTestCaseFolder(folderName, parentType, parentId);

				} else {

					throw new IOException("Type de dossier inconnu : " + folderType);
				}

				if (targetFolder == null || !targetFolder.has("id") || targetFolder.get("id").isJsonNull()) {

					throw new IOException("Impossible de créer le dossier : " + folderName);
				}

				createdFolderCount++;
				createdFolderPaths.add(walkedDepth + "|" + walkedPath);

				if (!targetFolder.has("name") || targetFolder.get("name").isJsonNull()) {
					targetFolder.addProperty("name", folderName);
				}

				getWritableChildren(currentParent).add(targetFolder);
			}

			parentId = targetFolder.get("id").getAsLong();
			parentType = folderType;
			currentParent = targetFolder;
		}

		return parentId;
	}

	private long findTargetedFolder(JsonElement tree, String folderPath) throws IOException {

		if (tree == null || tree.isJsonNull() || isEmpty(folderPath)) {
			throw new IOException("Arbre ou chemin de dossier invalide.");
		}

		JsonObject currentFolder = getTreeRoot(tree);

		if (!currentFolder.has("id") || currentFolder.get("id").isJsonNull()) {
			throw new IOException("Identifiant du projet introuvable.");
		}

		long currentId = currentFolder.get("id").getAsLong();

		for (String rawName : folderPath.split("/")) {

			String folderName = rawName.trim();

			if (folderName.isEmpty()) {
				continue;
			}

			JsonObject foundFolder = findChildFolder(currentFolder, folderName);

			if (foundFolder == null || !foundFolder.has("id") || foundFolder.get("id").isJsonNull()) {

				throw new IOException("Le dossier parent n'existe pas ou n'a pas été sélectionné : " + folderName);
			}

			currentId = foundFolder.get("id").getAsLong();
			currentFolder = foundFolder;
		}

		return currentId;
	}

	/**
	 * Vérifie que chaque dossier manquant dans un chemin possède une action CREATE
	 * explicitement sélectionnée.
	 */
	private void validateSelectedFolderParents(JsonElement tree, String folderPath, String folderType,
			String projectName, Map<String, ImportSquashTmComparisonItem> decisions) throws IOException {

		String[] folders = folderPath.split("/");
		StringBuilder currentPath = new StringBuilder();

		for (String folder : folders) {

			folder = folder.trim();

			if (folder.isEmpty()) {
				continue;
			}

			if (currentPath.length() > 0) {
				currentPath.append("/");
			}

			currentPath.append(folder);

			try {

				findTargetedFolder(tree, currentPath.toString());

			} catch (IOException e) {

				String key = buildComparisonKey(folderType, projectName.trim() + "/" + currentPath.toString());

				ImportSquashTmComparisonItem decision = decisions.get(key);

				if (decision == null
						|| !ImportSquashTmComparisonItem.ACTION_CREATE.equals(decision.getSelectedAction())) {

					throw new IOException("Création impossible : le dossier parent '" + currentPath
							+ "' n'existe pas et n'a pas été sélectionné.");
				}
			}
		}
	}

	private void updateExistingImportProgress(ImportSquashTmProgress progress, int completed, int total) {

		if (progress == null || total <= 0) {
			return;
		}

		int percentage = 60 + (int) ((38.0 * completed) / total);

		percentage = Math.min(98, percentage);

		progress.update(percentage, "DONNEES", completed + " / " + total + " action(s) traitée(s).");
	}

	public ImportSquashTmResult applySelectedActions(ImportSquashTmData importData, ImportSquashTmAnalysis analysis,
			ImportSquashTmProgress progress) throws IOException {

		if (importData == null || analysis == null) {
			throw new IOException("Les données de comparaison sont indisponibles.");
		}

		if (!analysis.isExistingProject()) {
			throw new IOException("Cette opération est réservée aux projets existants.");
		}

		long projectId = analysis.getExistingProjectId();

		if (projectId <= 0) {
			throw new IOException("Identifiant du projet Squash TM invalide.");
		}

		ImportSquashTmResult result = new ImportSquashTmResult();

		result.setProjectName(importData.getProjectName());
		result.setExistingProjectReused(true);
		createdFolderPaths.clear();
		createdFolderCount = 0;

		JsonElement requirementTree = squashTmService.getRequirementFolderTree(projectId);

		JsonElement testCaseTree = squashTmService.getTestCaseFolderTree(projectId);

		Map<String, ImportSquashTmComparisonItem> decisions = new LinkedHashMap<>();

		for (ImportSquashTmComparisonItem item : analysis.getComparisonItems()) {
			if (item != null && item.getComparisonKey() != null) {
				decisions.put(item.getComparisonKey(), item);
			}
		}

		int totalActions = 0;

		for (ImportSquashTmComparisonItem item : decisions.values()) {

			String action = item.getSelectedAction();

			if (ImportSquashTmComparisonItem.ACTION_CREATE.equals(action)
					|| ImportSquashTmComparisonItem.ACTION_UPDATE.equals(action)) {

				totalActions++;
			}
		}

		int completedActions = 0;

		if (progress != null) {
			progress.update(60, "DONNEES", "Préparation des créations dans le projet existant...");
		}

		for (ImportSquashTmComparisonItem item : decisions.values()) {

			if (!ImportSquashTmComparisonItem.ACTION_CREATE.equals(item.getSelectedAction())) {
				continue;
			}

			String type = item.getElementType();

			if (ImportSquashTmComparisonItem.TYPE_REQUIREMENT_FOLDER.equals(type)) {

				String folderPath = removeProjectRoot(item.getElementPath(), importData.getProjectName());

				validateSelectedFolderParents(requirementTree, folderPath,
						ImportSquashTmComparisonItem.TYPE_REQUIREMENT_FOLDER, importData.getProjectName(), decisions);

				ensureTargetedFolder(requirementTree, folderPath, "requirement-folder");

			} else if (ImportSquashTmComparisonItem.TYPE_TEST_CASE_FOLDER.equals(type)) {

				String folderPath = removeProjectRoot(item.getElementPath(), importData.getProjectName());

				validateSelectedFolderParents(testCaseTree, folderPath,
						ImportSquashTmComparisonItem.TYPE_TEST_CASE_FOLDER, importData.getProjectName(), decisions);

				ensureTargetedFolder(testCaseTree, folderPath, "test-case-folder");

			} else {

				continue;
			}

			completedActions++;

			updateExistingImportProgress(progress, completedActions, totalActions);
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

						String featurePath = importData.getProjectName().trim() + "/" + buildFolderPath(
								module.getName(), subModule.getName(), view.getName(), feature.getName());

						if (feature.getRequirements() != null) {

							for (ImportSquashTmRequirement requirement : feature.getRequirements()) {

								if (requirement == null || isEmpty(requirement.getName())) {
									continue;
								}

								String elementPath = featurePath + "/" + requirement.getName().trim();

								String comparisonKey = buildComparisonKey(ImportSquashTmComparisonItem.TYPE_REQUIREMENT,
										elementPath);

								ImportSquashTmComparisonItem decision = decisions.get(comparisonKey);

								if (decision == null) {
									continue;
								}

								String action = decision.getSelectedAction();

								try {

									if (ImportSquashTmComparisonItem.ACTION_CREATE.equals(action)) {

										String relativeFeaturePath = removeProjectRoot(featurePath,
												importData.getProjectName());
										long folderId = ensureTargetedFolder(requirementTree, relativeFeaturePath,
												"requirement-folder");

										squashTmService.createRequirement(requirement.getName().trim(), "", folderId);

										result.setRequirementCount(result.getRequirementCount() + 1);
										completedActions++;
										updateExistingImportProgress(progress, completedActions, totalActions);

									} else if (ImportSquashTmComparisonItem.ACTION_UPDATE.equals(action)) {

										if (decision.getSquashId() <= 0) {
											throw new IOException(
													"Identifiant Squash TM absent pour le renommage de l'exigence : "
															+ requirement.getName());
										}

										squashTmService.updateRequirement(decision.getSquashId(),
												requirement.getName());

										result.setRequirementCount(result.getRequirementCount() + 1);
										completedActions++;
										updateExistingImportProgress(progress, completedActions, totalActions);
									}

								} catch (IOException | RuntimeException e) {
									recordActionError(result,
											"Exigence '" + requirement.getName().trim() + "' (" + featurePath + ")", e);
									completedActions++;
									updateExistingImportProgress(progress, completedActions, totalActions);
								}
							}
						}

						if (feature.getTestCases() != null) {

							for (ImportSquashTmTestCase testCase : feature.getTestCases()) {

								if (testCase == null || isEmpty(testCase.getName())) {
									continue;
								}

								String elementPath = featurePath + "/" + testCase.getName().trim();

								String comparisonKey = buildComparisonKey(ImportSquashTmComparisonItem.TYPE_TEST_CASE,
										elementPath);

								ImportSquashTmComparisonItem decision = decisions.get(comparisonKey);

								String action = decision != null ? decision.getSelectedAction() : null;

								long testCaseId = 0;
								boolean createdWithAllSteps = false;
								boolean testCaseFailed = false;

								try {

									if (ImportSquashTmComparisonItem.ACTION_CREATE.equals(action)) {

										String relativeFeaturePath = removeProjectRoot(featurePath,
												importData.getProjectName());
										long folderId = ensureTargetedFolder(testCaseTree, relativeFeaturePath,
												"test-case-folder");

										JsonObject createdTestCase = squashTmService
												.createTestCase(testCase.getName().trim(), "", folderId);

										if (createdTestCase == null || !createdTestCase.has("id")
												|| createdTestCase.get("id").isJsonNull()) {
											throw new IOException(
													"Impossible de récupérer l'identifiant du cas de test : "
															+ testCase.getName());
										}

										testCaseId = createdTestCase.get("id").getAsLong();

										result.setTestCaseCount(result.getTestCaseCount() + 1);

										if (testCase.getSteps() != null) {

											int stepPosition = 0;

											for (ImportSquashTmTestStep step : testCase.getSteps()) {

												stepPosition++;

												if (step == null || isEmpty(step.getAction())
														|| isEmpty(step.getExpectedResult())) {
													continue;
												}

												try {

													squashTmService.createTestStep(testCaseId, step.getAction().trim(),
															step.getExpectedResult().trim());

													result.setTestStepCount(result.getTestStepCount() + 1);

												} catch (IOException | RuntimeException stepError) {

													recordActionError(result,
															"Étape " + stepPosition + " du cas de test '"
																	+ testCase.getName().trim() + "' (" + featurePath
																	+ ")",
															stepError);
												}
											}
										}

										createdWithAllSteps = true;

										completedActions++;

										updateExistingImportProgress(progress, completedActions, totalActions);

									} else if (ImportSquashTmComparisonItem.ACTION_UPDATE.equals(action)) {

										if (decision.getSquashId() <= 0) {
											throw new IOException(
													"Identifiant Squash TM absent pour le renommage du cas de test : "
															+ testCase.getName());
										}

										testCaseId = decision.getSquashId();

										squashTmService.updateTestCase(testCaseId, testCase.getName());

										result.setTestCaseCount(result.getTestCaseCount() + 1);

										completedActions++;

										updateExistingImportProgress(progress, completedActions, totalActions);
									}

								} catch (IOException | RuntimeException e) {

									testCaseFailed = true;

									recordActionError(result,
											"Cas de test '" + testCase.getName().trim() + "' (" + featurePath + ")", e);

									completedActions++;

									updateExistingImportProgress(progress, completedActions, totalActions);
								}

								if (testCaseFailed && testCaseId <= 0) {
									continue;
								}

								int stepActions = applySelectedTestSteps(testCase, elementPath, featurePath, testCaseId,
										createdWithAllSteps, decisions, testCaseTree, result,
										importData.getProjectName());

								if (stepActions > 0) {
									completedActions += stepActions;
									updateExistingImportProgress(progress, completedActions, totalActions);
								}
							}
						}
					}
				}
			}
		}

		if (progress != null)

		{
			progress.update(98, "TERMINE", "Les actions sélectionnées ont été exécutées.");
		}

		result.setFolderCount(createdFolderCount);
		result.setModuleCount(countCreatedFolders(1));
		result.setSubModuleCount(countCreatedFolders(2));
		result.setViewCount(countCreatedFolders(3));
		result.setFeatureCount(countCreatedFolders(4));
		result.setSuccess(result.getTechnicalErrors().isEmpty());

		return result;
	}

	/**
	 * Enregistre l'échec d'une action dans le rapport sans interrompre les
	 * suivantes.
	 */
	private void recordActionError(ImportSquashTmResult result, String target, Exception e) {

		String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();

		LOGGER.error("[APPLICATION] Échec : " + target, e);

		result.addTechnicalError(target + " : " + detail);
	}

	/**
	 * Nombre de dossiers distincts créés à un niveau donné (1 = module, 2 =
	 * sous-module, 3 = vue, 4 = fonctionnalité).
	 */
	private int countCreatedFolders(int depth) {

		int count = 0;
		String prefix = depth + "|";

		for (String key : createdFolderPaths) {
			if (key.startsWith(prefix)) {
				count++;
			}
		}

		return count;
	}

	private String removeProjectRoot(String path, String projectName) {

		if (isEmpty(path) || isEmpty(projectName)) {
			return path;
		}

		String normalizedPath = normalizeComparisonPath(path);
		String normalizedProjectName = projectName.trim();

		if (normalizedPath.equals(normalizedProjectName)) {
			return "";
		}

		if (normalizedPath.startsWith(normalizedProjectName + "/")) {
			return normalizedPath.substring(normalizedProjectName.length() + 1);
		}

		return normalizedPath;
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

		if (existingProject != null) {

			/*
			 * ============================================================ PROJET EXISTANT
			 * : NOUVEAU FONCTIONNEMENT
			 * ============================================================
			 */

			result.setExistingProjectReused(true);

			if (!existingProject.has("id") || existingProject.get("id").isJsonNull()) {

				throw new IOException("Le projet Squash TM existe mais son identifiant n'a pas été retourné.");
			}

			projectId = existingProject.get("id").getAsLong();

			LOGGER.info("Projet Squash TM existant trouvé : " + projectName + " (id=" + projectId + ")");

			/*
			 * IMPORTANT : Le contenu du projet existant ne doit jamais être supprimé.
			 *
			 * La comparaison avec Excel sera effectuée dans le nouveau traitement dédié aux
			 * projets existants.
			 */

			throw new IOException("Le projet existe déjà. La comparaison avec son contenu "
					+ "doit être effectuée avant de lancer l'importation.");

		} else {

			/*
			 * ============================================================ PROJET
			 * INEXISTANT : ANCIEN FONCTIONNEMENT
			 * ============================================================
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

			} catch (Exception e) {
				String message = "Impossible de créer le dossier Requirement du module '" + moduleName + "' : "
						+ e.getMessage();

				LOGGER.error(message, e);
				result.addTechnicalError(message);
			}

			progress.update(calculateFolderProgress(result.getFolderCount(), totalFolders), "DOSSIERS",
					"Création des dossiers : " + result.getFolderCount() + "/" + totalFolders);

			Long testCaseModuleId = null;

			try {
				JsonObject testCaseModule = squashTmService.createTestCaseFolder(moduleName, "project", projectId);

				if (testCaseModule == null || !testCaseModule.has("id") || testCaseModule.get("id").isJsonNull()) {
					throw new IOException("L'identifiant du dossier n'a pas été retourné.");
				}

				testCaseModuleId = testCaseModule.get("id").getAsLong();

			} catch (Exception e) {
				String message = "Impossible de créer le dossier Test Case du module '" + moduleName + "' : "
						+ e.getMessage();

				LOGGER.error(message, e);
				result.addTechnicalError(message);
			}

			if (requirementModuleId == null || testCaseModuleId == null) {
				LOGGER.warn("Module ignoré car ses deux dossiers n'ont pas été créés : " + moduleName);
				continue;
			}

			result.setModuleCount(result.getModuleCount() + 1);
			result.setFolderCount(result.getFolderCount() + 1);
			progress.update(calculateFolderProgress(result.getFolderCount(), totalFolders), "DOSSIERS",
					"Création des dossiers : " + result.getFolderCount() + "/" + totalFolders);

			for (ImportSquashTmSubModule subModule : module.getSubModules()) {

				if (subModule == null || isEmpty(subModule.getName())) {
					continue;
				}

				String subModuleName = subModule.getName().trim();
				if (!isValidFolderName(subModuleName, "sous-module", moduleName + " / " + subModuleName, result)) {
					continue;
				}

				LOGGER.info("Création du sous-module : " + moduleName + " / " + subModuleName);

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
				result.setSubModuleCount(result.getSubModuleCount() + 1);

				for (ImportSquashTmView view : subModule.getViews()) {

					if (view == null || isEmpty(view.getName())) {
						continue;
					}

					String viewName = view.getName().trim();
					if (!isValidFolderName(viewName, "vue", moduleName + " / " + subModuleName + " / " + viewName,
							result)) {
						continue;
					}

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

					JsonObject testCaseView = squashTmService.createTestCaseFolder(viewName, "test-case-folder",
							testCaseSubModuleId);

					if (testCaseView == null || !testCaseView.has("id") || testCaseView.get("id").isJsonNull()) {
						throw new IOException("Impossible de créer la vue Test Case : " + viewName);
					}

					long testCaseViewId = testCaseView.get("id").getAsLong();
					result.setFolderCount(result.getFolderCount() + 1);
					progress.update(calculateFolderProgress(result.getFolderCount(), totalFolders), "DOSSIERS",
							"Création des dossiers : " + result.getFolderCount() + "/" + totalFolders);
					result.setViewCount(result.getViewCount() + 1);

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
						result.setFeatureCount(result.getFeatureCount() + 1);

						String folderPath = buildFolderPath(moduleName, subModuleName, viewName, featureName);

						requirementFolderIds.put(folderPath, requirementFeatureId);
						testCaseFolderIds.put(folderPath, testCaseFeatureId);
					}
				}
			}
		}

		/*
		 * ======== 6. CRÉATION DES REQUIREMENTS ET DES TEST CASES ============
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
	 * @return [0] exigences, [1] cas de test, [2] étapes
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

								if (requirement != null && !isEmpty(requirement.getName())) {
									requirementCount++;
								}
							}
						}

						if (feature.getTestCases() != null) {

							for (ImportSquashTmTestCase testCase : feature.getTestCases()) {

								if (testCase == null || isEmpty(testCase.getName())) {
									continue;
								}

								testCaseCount++;

								if (testCase.getSteps() != null) {

									for (ImportSquashTmTestStep step : testCase.getSteps()) {

										if (step == null || isEmpty(step.getAction())
												|| isEmpty(step.getExpectedResult())) {
											continue;
										}

										testStepCount++;
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

	/**
	 * Recherche un projet existant dans Squash TM.
	 *
	 * Cette méthode ne crée, ne modifie et ne supprime aucune donnée.
	 *
	 * @param projectName nom du projet recherché
	 * @return projet trouvé ou null si le projet n'existe pas
	 * @throws IOException si la recherche échoue
	 */
	public JsonObject findExistingProject(String projectName) throws IOException {

		if (projectName == null || projectName.trim().isEmpty()) {
			throw new IOException("Le nom du projet est obligatoire.");
		}

		LOGGER.info("[PROJECT CHECK] Recherche du projet : " + projectName);

		JsonObject project = squashTmService.findProjectByName(projectName.trim());

		if (project != null) {
			LOGGER.info("[PROJECT CHECK] Projet existant trouvé.");
		} else {
			LOGGER.info("[PROJECT CHECK] Projet inexistant.");
		}

		return project;
	}

	public JsonObject inspectExistingProject(String projectName) throws IOException {

		if (isEmpty(projectName)) {
			throw new IOException("Le nom du projet est obligatoire.");
		}

		LOGGER.info("[PROJECT INSPECTION] Recherche : " + projectName);

		JsonObject project = squashTmService.findProjectByName(projectName.trim());

		if (project == null) {
			LOGGER.info("[PROJECT INSPECTION] Projet inexistant.");
			return null;
		}

		if (!project.has("id") || project.get("id").isJsonNull()) {
			throw new IOException("L'identifiant du projet est absent.");
		}

		long projectId = project.get("id").getAsLong();

		LOGGER.info("[PROJECT INSPECTION] Projet trouvé : id=" + projectId);

		JsonElement requirementTree = squashTmService.getRequirementFolderTree(projectId);
		LOGGER.info("========== JSON ARBRE EXIGENCES ==========");
		LOGGER.info(requirementTree != null ? requirementTree.toString() : "ARBRE EXIGENCES NULL");

		JsonElement testCaseTree = squashTmService.getTestCaseFolderTree(projectId);
		LOGGER.info("========== JSON ARBRE CAS DE TEST ==========");
		LOGGER.info(testCaseTree != null ? testCaseTree.toString() : "ARBRE CAS DE TEST NULL");

		if (requirementTree == null || requirementTree.isJsonNull()) {
			throw new IOException("Impossible de récupérer l'arborescence des exigences du projet.");
		}

		if (testCaseTree == null || testCaseTree.isJsonNull()) {
			throw new IOException("Impossible de récupérer l'arborescence des cas de test du projet.");
		}

		JsonObject inspection = new JsonObject();

		inspection.add("project", project);
		inspection.add("requirementTree", requirementTree);
		inspection.add("testCaseTree", testCaseTree);

		inspection.add("requirementFolders", inspectFolderContents(requirementTree, true));

		inspection.add("testCaseFolders", inspectFolderContents(testCaseTree, false));

		LOGGER.info("[PROJECT INSPECTION] Inspection terminée.");

		return inspection;
	}

	/**
	 * Compare les éléments Excel avec le contenu d'un projet Squash TM existant.
	 *
	 * La comparaison repose sur le type et le chemin hiérarchique complet.
	 *
	 * Les éléments identiques et les éléments uniquement présents dans Squash TM ne
	 * sont pas transmis à l'interface.
	 */
	public void compareExistingProject(ImportSquashTmData importData, ImportSquashTmAnalysis analysis)
			throws IOException {
		compareExistingProject(importData, analysis, null);
	}

	public void compareExistingProject(ImportSquashTmData importData, ImportSquashTmAnalysis analysis,
			ImportSquashTmProgress progress) throws IOException {

		if (importData == null || analysis == null) {
			throw new IOException("Les données nécessaires à la comparaison sont absentes.");
		}
		
		if (progress != null) {
			progress.update(45, "COMPARAISON", "Recherche du projet dans Squash TM...");
		}

		JsonObject existingProjectData = inspectExistingProject(importData.getProjectName());

		if (existingProjectData == null) {
			analysis.setExistingProject(false);
			return;
		}

		JsonObject project = existingProjectData.getAsJsonObject("project");

		if (project == null || !project.has("id") || project.get("id").isJsonNull()) {
			throw new IOException("Impossible de récupérer l'identifiant du projet Squash TM.");
		}

		long projectId = project.get("id").getAsLong();

		analysis.setExistingProject(true);
		analysis.setExistingProjectId(projectId);
		
		if (progress != null) {
			progress.update(60, "COMPARAISON", "Projet trouvé, lecture des arborescences...");
		}

		Map<String, ImportSquashTmComparisonItem> excelItems = new LinkedHashMap<>();
		Map<String, ImportSquashTmComparisonItem> squashItems = new LinkedHashMap<>();

		collectExcelComparisonItems(importData, excelItems);

		String projectName = importData.getProjectName().trim();

		collectSquashFolderItems(existingProjectData.get("requirementTree"), projectName,
				ImportSquashTmComparisonItem.TYPE_REQUIREMENT_FOLDER, squashItems);

		collectSquashFolderItems(existingProjectData.get("testCaseTree"), projectName,
				ImportSquashTmComparisonItem.TYPE_TEST_CASE_FOLDER, squashItems);

		collectSquashContentItems(existingProjectData.getAsJsonArray("requirementFolders"), true,
				importData.getProjectName(), squashItems);

		collectSquashContentItems(existingProjectData.getAsJsonArray("testCaseFolders"), false,
				importData.getProjectName(), squashItems);
		
		if (progress != null) {
			progress.update(75, "COMPARAISON", "Comparaison du fichier avec Squash TM...");
		}

		LOGGER.error("========== DEBUT DIAGNOSTIC COMPARAISON ==========");

		LOGGER.error("[DIAGNOSTIC] Nombre total Excel : " + excelItems.size());
		LOGGER.error("[DIAGNOSTIC] Nombre total Squash : " + squashItems.size());

		for (Map.Entry<String, ImportSquashTmComparisonItem> entry : excelItems.entrySet()) {

			ImportSquashTmComparisonItem item = entry.getValue();

			if (ImportSquashTmComparisonItem.TYPE_REQUIREMENT_FOLDER.equals(item.getElementType())) {

				LOGGER.error("[EXCEL REQUIREMENT FOLDER] " + entry.getKey());
			}
		}

		for (Map.Entry<String, ImportSquashTmComparisonItem> entry : squashItems.entrySet()) {

			ImportSquashTmComparisonItem item = entry.getValue();

			if (ImportSquashTmComparisonItem.TYPE_REQUIREMENT_FOLDER.equals(item.getElementType())) {

				LOGGER.error("[SQUASH REQUIREMENT FOLDER] " + entry.getKey());
			}
		}

		LOGGER.error("========== FIN DIAGNOSTIC COMPARAISON ==========");

		Map<String, Long> existingTestCaseIds = new HashMap<>();

		for (Map.Entry<String, ImportSquashTmComparisonItem> entry : squashItems.entrySet()) {

			if (ImportSquashTmComparisonItem.TYPE_TEST_CASE.equals(entry.getValue().getElementType())) {
				existingTestCaseIds.put(entry.getKey(), entry.getValue().getSquashId());
			}
		}

		List<ImportSquashTmComparisonItem> excelOnly = new ArrayList<>();

		for (Map.Entry<String, ImportSquashTmComparisonItem> entry : excelItems.entrySet()) {

			ImportSquashTmComparisonItem excelItem = entry.getValue();

			String comparisonKey = entry.getKey();

			ImportSquashTmComparisonItem squashItem = squashItems.remove(comparisonKey);

			if (squashItem == null) {

				excelItem.setStatus(ImportSquashTmComparisonItem.STATUS_EXCEL_ONLY);

				excelOnly.add(excelItem);
			}
		}

		addRenameCandidates(excelOnly, new ArrayList<>(squashItems.values()), analysis);

		for (ImportSquashTmComparisonItem item : excelOnly) {

			analysis.addComparisonItem(item);
		}

		for (ImportSquashTmComparisonItem item : analysis.getComparisonItems()) {

			if (ImportSquashTmComparisonItem.TYPE_TEST_CASE.equals(item.getElementType())
					&& ImportSquashTmComparisonItem.STATUS_RENAME.equals(item.getStatus()) && item.getSquashId() > 0) {

				existingTestCaseIds.put(item.getComparisonKey(), item.getSquashId());
			}
		}

		collectStepComparisonItems(importData, existingTestCaseIds, analysis);
		
		if (progress != null) {
			progress.update(90, "COMPARAISON", "Comparaison des étapes terminée.");
		}

		LOGGER.info("[COMPARAISON] Projet : " + importData.getProjectName());
		LOGGER.info("[COMPARAISON] Éléments Excel : " + excelItems.size());
		LOGGER.info("[COMPARAISON] Opérations proposées : " + analysis.getComparisonItems().size());
		LOGGER.info("[COMPARAISON] Comparaison terminée.");
	}

	/**
	 * Construit la clé de comparaison d'une étape (position dans le cas de test).
	 */
	private String buildStepKey(String testCasePath, int stepNumber) {

		return buildComparisonKey(ImportSquashTmComparisonItem.TYPE_TEST_STEP,
				testCasePath + "/" + buildStepName(stepNumber));
	}

	private String buildStepName(int stepNumber) {

		return "Étape " + stepNumber;
	}

	/**
	 * Normalise un texte d'étape pour la comparaison : suppression du HTML (Squash
	 * TM stocke du texte riche), des entités et des espaces multiples.
	 */
	private String normalizeStepText(String text) {

		if (text == null) {
			return "";
		}

		String value = text.replaceAll("(?i)<br\\s*/?>", " ").replaceAll("(?i)</p>", " ").replaceAll("<[^>]*>", "");

		value = value.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
				.replace("&#39;", "'").replace("&amp;", "&");

		value = value.replace(' ', ' ').replaceAll("\\s+", " ").trim();

		return value;
	}

	private String readStepField(JsonObject step, String field) {

		if (step.has(field) && !step.get(field).isJsonNull()) {
			return step.get(field).getAsString();
		}

		return "";
	}

	/**
	 * Compare les étapes Excel avec celles de Squash TM et ajoute au résultat : les
	 * étapes absentes (création) et les étapes dont l'action ou le résultat attendu
	 * diffère (modification).
	 */
	private void collectStepComparisonItems(ImportSquashTmData importData, Map<String, Long> existingTestCaseIds,
			ImportSquashTmAnalysis analysis) throws IOException {

		if (importData.getModules() == null) {
			return;
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

						if (feature == null || feature.getTestCases() == null) {
							continue;
						}

						String featurePath = importData.getProjectName().trim() + "/" + buildFolderPath(
								module.getName(), subModule.getName(), view.getName(), feature.getName());

						for (ImportSquashTmTestCase testCase : feature.getTestCases()) {

							if (testCase == null || isEmpty(testCase.getName()) || testCase.getSteps() == null
									|| testCase.getSteps().isEmpty()) {
								continue;
							}

							String testCasePath = featurePath + "/" + testCase.getName().trim();

							String testCaseKey = buildComparisonKey(ImportSquashTmComparisonItem.TYPE_TEST_CASE,
									testCasePath);

							Long testCaseId = existingTestCaseIds.get(testCaseKey);

							List<JsonObject> squashSteps = new ArrayList<>();

							if (testCaseId != null && testCaseId > 0) {

								for (JsonElement element : squashTmService.getTestCaseSteps(testCaseId)) {

									if (element == null || !element.isJsonObject()) {
										continue;
									}

									JsonObject squashStep = element.getAsJsonObject();

									if (squashStep.has("_type") && !squashStep.get("_type").isJsonNull()
											&& !"action-step".equals(squashStep.get("_type").getAsString())) {
										continue;
									}

									squashSteps.add(squashStep);
								}
							}

							List<ImportSquashTmTestStep> excelSteps = testCase.getSteps();

							for (int i = 0; i < excelSteps.size(); i++) {

								ImportSquashTmTestStep excelStep = excelSteps.get(i);

								if (excelStep == null || isEmpty(excelStep.getAction())
										|| isEmpty(excelStep.getExpectedResult())) {
									continue;
								}

								int stepNumber = i + 1;

								String stepName = buildStepName(stepNumber);
								String stepPath = testCasePath + "/" + stepName;
								String stepKey = buildStepKey(testCasePath, stepNumber);

								ImportSquashTmComparisonItem item = new ImportSquashTmComparisonItem(stepKey,
										ImportSquashTmComparisonItem.TYPE_TEST_STEP, stepName, stepPath,
										ImportSquashTmComparisonItem.STATUS_EXCEL_ONLY, 0);

								item.setParentSquashId(testCaseId != null ? testCaseId : 0);

								if (i < squashSteps.size()) {

									JsonObject squashStep = squashSteps.get(i);

									String squashAction = readStepField(squashStep, "action");
									String squashExpected = readStepField(squashStep, "expected_result");

									boolean sameAction = normalizeStepText(squashAction)
											.equals(normalizeStepText(excelStep.getAction()));
									boolean sameExpected = normalizeStepText(squashExpected)
											.equals(normalizeStepText(excelStep.getExpectedResult()));

									if (sameAction && sameExpected) {
										continue;
									}

									item.setStatus(ImportSquashTmComparisonItem.STATUS_RENAME);
									item.setOriginalName(stepName);
									item.setSquashId(squashStep.has("id") && !squashStep.get("id").isJsonNull()
											? squashStep.get("id").getAsLong()
											: 0);

									if (item.getSquashId() <= 0) {
										continue;
									}

									if (!sameAction) {
										item.addDifference("Action (Squash TM) : " + normalizeStepText(squashAction));
										item.addDifference("Action (Excel) : " + excelStep.getAction().trim());
									}

									if (!sameExpected) {
										item.addDifference(
												"Résultat attendu (Squash TM) : " + normalizeStepText(squashExpected));
										item.addDifference(
												"Résultat attendu (Excel) : " + excelStep.getExpectedResult().trim());
									}

								} else {

									item.addDifference("Action : " + excelStep.getAction().trim());
									item.addDifference("Résultat attendu : " + excelStep.getExpectedResult().trim());
								}

								analysis.addComparisonItem(item);
							}
						}
					}
				}
			}
		}
	}

	/**
	 * Applique les étapes cochées d'un cas de test. Si le cas de test n'existe pas
	 * encore, ses dossiers puis le cas de test lui-même sont créés d'abord.
	 *
	 * @return le nombre d'actions traitées
	 */
	private int applySelectedTestSteps(ImportSquashTmTestCase testCase, String testCasePath, String featurePath,
			long knownTestCaseId, boolean createdWithAllSteps, Map<String, ImportSquashTmComparisonItem> decisions,
			JsonElement testCaseTree, ImportSquashTmResult result, String projectName) throws IOException {

		if (testCase.getSteps() == null) {
			return 0;
		}

		int done = 0;
		long testCaseId = knownTestCaseId;

		List<ImportSquashTmTestStep> steps = testCase.getSteps();

		for (int i = 0; i < steps.size(); i++) {

			ImportSquashTmTestStep step = steps.get(i);

			if (step == null) {
				continue;
			}

			ImportSquashTmComparisonItem decision = decisions.get(buildStepKey(testCasePath, i + 1));

			if (decision == null) {
				continue;
			}

			String action = decision.getSelectedAction();

			if (!ImportSquashTmComparisonItem.ACTION_CREATE.equals(action)
					&& !ImportSquashTmComparisonItem.ACTION_UPDATE.equals(action)) {
				continue;
			}

			if (createdWithAllSteps) {
				done++;
				continue;
			}

			try {

				if (ImportSquashTmComparisonItem.ACTION_CREATE.equals(action)) {

					if (testCaseId <= 0) {
						String relativeFeaturePath = removeProjectRoot(featurePath,
								featurePath.contains("/") ? featurePath.substring(0, featurePath.indexOf('/'))
										: featurePath);

						long folderId = ensureTargetedFolder(testCaseTree, relativeFeaturePath, "test-case-folder");

						JsonObject createdTestCase = squashTmService.createTestCase(testCase.getName().trim(), "",
								folderId);

						if (createdTestCase == null || !createdTestCase.has("id")
								|| createdTestCase.get("id").isJsonNull()) {
							throw new IOException(
									"Impossible de récupérer l'identifiant du cas de test : " + testCase.getName());
						}

						testCaseId = createdTestCase.get("id").getAsLong();

						result.setTestCaseCount(result.getTestCaseCount() + 1);
					}

				} else {

					if (decision.getSquashId() <= 0) {
						throw new IOException("Identifiant Squash TM absent pour la modification de l'étape : "
								+ decision.getElementPath());
					}

					squashTmService.updateTestStep(decision.getSquashId(), step.getAction().trim(),
							step.getExpectedResult().trim());

					result.setTestStepCount(result.getTestStepCount() + 1);
				}

			} catch (IOException | RuntimeException e) {

				recordActionError(result, "Étape " + (i + 1) + " du cas de test '" + testCase.getName().trim() + "' ("
						+ testCasePath + ")", e);
			}

			done++;
		}

		return done;
	}

	/**
	 * Propose un renommage uniquement lorsqu'un seul élément Excel et un seul
	 * élément Squash TM sont absents au même niveau hiérarchique.
	 *
	 * Les dossiers ne sont pas renommés par cette méthode. Seuls les noms des
	 * exigences et des cas de test sont concernés.
	 */
	private void addRenameCandidates(List<ImportSquashTmComparisonItem> excelOnly,
			List<ImportSquashTmComparisonItem> squashOnly, ImportSquashTmAnalysis analysis) {

		for (int i = 0; i < excelOnly.size();) {

			ImportSquashTmComparisonItem excelItem = excelOnly.get(i);

			String type = excelItem.getElementType();

			if (!ImportSquashTmComparisonItem.TYPE_REQUIREMENT.equals(type)
					&& !ImportSquashTmComparisonItem.TYPE_TEST_CASE.equals(type)) {

				i++;
				continue;
			}

			String excelParentPath = getParentPath(excelItem.getElementPath());

			ImportSquashTmComparisonItem matchingSquashItem = null;

			int excelSiblingCount = 0;
			int squashSiblingCount = 0;

			for (ImportSquashTmComparisonItem candidate : excelOnly) {

				if (candidate.getElementType().equals(type)
						&& getParentPath(candidate.getElementPath()).equals(excelParentPath)) {

					excelSiblingCount++;
				}
			}

			for (ImportSquashTmComparisonItem candidate : squashOnly) {

				if (candidate.getElementType().equals(type)
						&& getParentPath(candidate.getElementPath()).equals(excelParentPath)) {

					squashSiblingCount++;
					matchingSquashItem = candidate;
				}
			}

			if (excelSiblingCount == 1 && squashSiblingCount == 1) {

				excelItem.setStatus(ImportSquashTmComparisonItem.STATUS_RENAME);
				excelItem.setOriginalName(matchingSquashItem.getElementName());
				excelItem.setSquashId(matchingSquashItem.getSquashId());

				analysis.addComparisonItem(excelItem);
				excelOnly.remove(i);
				squashOnly.remove(matchingSquashItem);

			} else {
				i++;
			}
		}
	}

	/**
	 * Retourne le chemin du dossier parent d'un élément.
	 */
	private String getParentPath(String path) {

		if (path == null || path.trim().isEmpty()) {
			return "";
		}

		int lastSlash = path.lastIndexOf('/');

		if (lastSlash < 0) {
			return "";
		}

		return path.substring(0, lastSlash);
	}

	private void collectSquashFolderItems(JsonElement tree, String projectName, String folderType,
			Map<String, ImportSquashTmComparisonItem> items) {

		Map<Long, JsonObject> folders = new LinkedHashMap<>();

		collectFolders(tree, "", folders);

		for (JsonObject folder : folders.values()) {

			String folderName = folder.get("name").getAsString();

			String path = projectName.trim() + "/" + normalizeComparisonPath(folder.get("path").getAsString());

			String key = buildComparisonKey(folderType, path);

			items.put(key, new ImportSquashTmComparisonItem(key, folderType, folderName, path,
					ImportSquashTmComparisonItem.STATUS_SQUASH_ONLY, folder.get("id").getAsLong()));
		}
	}

	private void collectSquashContentItems(JsonArray folders, boolean requirementFolder, String projectName,
			Map<String, ImportSquashTmComparisonItem> items) throws IOException {

		if (folders == null) {
			return;
		}

		for (JsonElement folderElement : folders) {

			if (folderElement == null || !folderElement.isJsonObject()) {
				throw new IOException("Structure de dossier Squash TM invalide.");
			}

			JsonObject folder = folderElement.getAsJsonObject();

			if (!folder.has("content") || !folder.get("content").isJsonArray()) {
				throw new IOException("Contenu introuvable pour un dossier Squash TM.");
			}

			String folderPath = folder.has("path") && !folder.get("path").isJsonNull()
					? normalizeComparisonPath(folder.get("path").getAsString())
					: "";

			JsonArray content = folder.getAsJsonArray("content");

			for (JsonElement contentElement : content) {

				if (contentElement == null || !contentElement.isJsonObject()) {
					throw new IOException("Un élément du contenu Squash TM est invalide.");
				}

				JsonObject contentObject = contentElement.getAsJsonObject();

				if (!contentObject.has("_type") || contentObject.get("_type").isJsonNull()) {
					throw new IOException("Type d'un élément Squash TM introuvable.");
				}

				String squashType = contentObject.get("_type").getAsString();

				if (squashType.endsWith("-folder")) {
					continue;
				}

				if (requirementFolder && !"requirement".equals(squashType)
						&& !"requirement-version".equals(squashType)) {
					LOGGER.warn("[COMPARAISON] Type inattendu dans un dossier d'exigences (ignoré) : " + squashType);
					continue;
				}

				if (!requirementFolder && !("test-case".equals(squashType) || "scripted-test-case".equals(squashType)
						|| "keyword-test-case".equals(squashType))) {
					throw new IOException("Type inattendu dans un dossier de cas de test : " + squashType);
				}

				if (!contentObject.has("id") || contentObject.get("id").isJsonNull()) {
					throw new IOException("Identifiant d'un élément Squash TM introuvable.");
				}

				long elementId = contentObject.get("id").getAsLong();

				String elementType = requirementFolder ? ImportSquashTmComparisonItem.TYPE_REQUIREMENT
						: ImportSquashTmComparisonItem.TYPE_TEST_CASE;

				String elementName = "";

				if (contentObject.has("name") && !contentObject.get("name").isJsonNull()) {
					elementName = contentObject.get("name").getAsString();
				}

				JsonObject detail = requirementFolder ? squashTmService.getRequirement(elementId)
						: squashTmService.getTestCase(elementId);

				if (Util.isEmpty(elementName) && detail != null && detail.has("name")
						&& !detail.get("name").isJsonNull()) {
					elementName = detail.get("name").getAsString();
				}

				if (Util.isEmpty(elementName)) {
					continue;
				}

				elementName = elementName.trim();

				String squashPath;

				if (requirementFolder) {

					if (detail != null && detail.has("path") && !detail.get("path").isJsonNull()) {
						String fullPath = detail.get("path").getAsString();
						// On force le préfixe projet même si l'API le renvoie déjà
						squashPath = projectName.trim() + "/" + normalizeSquashPath(fullPath, projectName);
					} else {
						squashPath = projectName.trim() + (folderPath.isEmpty() ? "" : "/" + folderPath) + "/"
								+ elementName;
					}

				} else {
					String rawSquashPath = folderPath.isEmpty() ? elementName : folderPath + "/" + elementName;
					squashPath = projectName.trim() + "/" + normalizeComparisonPath(rawSquashPath);
				}

				if (Util.isEmpty(squashPath)) {
					throw new IOException("Impossible de construire le chemin de l'élément Squash TM : " + elementId);
				}

				String key = buildComparisonKey(elementType, squashPath);

				items.put(key, new ImportSquashTmComparisonItem(key, elementType, elementName, squashPath,
						ImportSquashTmComparisonItem.STATUS_SQUASH_ONLY, elementId));
			}
		}
	}

	private String normalizeSquashPath(String path, String projectName) {

		if (Util.isEmpty(path)) {
			return "";
		}

		String normalizedPath = path.replace("\\", "/").trim();
		normalizedPath = normalizedPath.replaceAll("^/+|/+$", "");

		String[] segments = normalizedPath.split("/");
		StringBuilder result = new StringBuilder();

		int startIndex = 0;

		if (segments.length > 0 && !Util.isEmpty(projectName)
				&& segments[0].trim().equalsIgnoreCase(projectName.trim())) {
			startIndex = 1;
		}

		for (int i = startIndex; i < segments.length; i++) {

			String segment = segments[i].trim();

			if (segment.isEmpty()) {
				continue;
			}

			if (result.length() > 0) {
				result.append("/");
			}

			result.append(segment);
		}

		return result.toString();
	}

	/**
	 * Construit la liste des éléments attendus à partir du fichier Excel.
	 */
	private void collectExcelComparisonItems(ImportSquashTmData importData,
			Map<String, ImportSquashTmComparisonItem> items) {

		if (importData == null || importData.getModules() == null) {
			return;
		}

		String projectName = importData.getProjectName().trim();

		for (ImportSquashTmModule module : importData.getModules()) {

			if (module == null || isEmpty(module.getName())) {
				continue;
			}

			String moduleName = module.getName().trim();
			String modulePath = projectName + "/" + moduleName;

			addExcelFolder(items, modulePath, ImportSquashTmComparisonItem.TYPE_REQUIREMENT_FOLDER);
			LOGGER.error("[EXCEL DEBUG] MODULE / REQUIREMENTS = " + modulePath);

			addExcelFolder(items, modulePath, ImportSquashTmComparisonItem.TYPE_TEST_CASE_FOLDER);
			LOGGER.error("[EXCEL DEBUG] MODULE / TEST CASES = " + modulePath);

			if (module.getSubModules() == null) {
				continue;
			}

			for (ImportSquashTmSubModule subModule : module.getSubModules()) {

				if (subModule == null || isEmpty(subModule.getName())) {
					continue;
				}

				String subModuleName = subModule.getName().trim();
				String subModulePath = projectName + "/" + buildFolderPath(moduleName, subModuleName, null, null);

				addExcelFolder(items, subModulePath, ImportSquashTmComparisonItem.TYPE_REQUIREMENT_FOLDER);
				LOGGER.error("[EXCEL DEBUG] SOUS-MODULE / REQUIREMENTS = " + subModulePath);

				addExcelFolder(items, subModulePath, ImportSquashTmComparisonItem.TYPE_TEST_CASE_FOLDER);
				LOGGER.error("[EXCEL DEBUG] SOUS-MODULE / TEST CASES = " + subModulePath);

				if (subModule.getViews() == null) {
					continue;
				}

				for (ImportSquashTmView view : subModule.getViews()) {

					if (view == null || isEmpty(view.getName())) {
						continue;
					}

					String viewPath = projectName + "/"
							+ buildFolderPath(moduleName, subModuleName, view.getName(), null);

					addExcelFolder(items, viewPath, ImportSquashTmComparisonItem.TYPE_REQUIREMENT_FOLDER);
					LOGGER.error("[EXCEL DEBUG] VUE / REQUIREMENTS = " + viewPath);

					addExcelFolder(items, viewPath, ImportSquashTmComparisonItem.TYPE_TEST_CASE_FOLDER);
					LOGGER.error("[EXCEL DEBUG] VUE / TEST CASES = " + viewPath);

					if (view.getFeatures() == null) {
						continue;
					}

					for (ImportSquashTmFeature feature : view.getFeatures()) {

						if (feature == null || isEmpty(feature.getName())) {
							continue;
						}

						String featurePath = projectName + "/"
								+ buildFolderPath(moduleName, subModuleName, view.getName(), feature.getName());

						addExcelFolder(items, featurePath, ImportSquashTmComparisonItem.TYPE_REQUIREMENT_FOLDER);
						LOGGER.error("[EXCEL DEBUG] FONCTIONNALITE / REQUIREMENTS = " + featurePath);

						addExcelFolder(items, featurePath, ImportSquashTmComparisonItem.TYPE_TEST_CASE_FOLDER);
						LOGGER.error("[EXCEL DEBUG] FONCTIONNALITE / TEST CASES = " + featurePath);

						if (feature.getRequirements() != null) {

							for (ImportSquashTmRequirement requirement : feature.getRequirements()) {

								if (requirement == null || isEmpty(requirement.getName())) {
									continue;
								}

								String requirementName = requirement.getName().trim();
								String path = featurePath + "/" + requirementName;
								LOGGER.error("[EXCEL DEBUG] EXIGENCE = " + path);

								addExcelElement(items, ImportSquashTmComparisonItem.TYPE_REQUIREMENT, requirementName,
										path);
							}
						}

						if (feature.getTestCases() != null) {

							for (ImportSquashTmTestCase testCase : feature.getTestCases()) {

								if (testCase == null || isEmpty(testCase.getName())) {
									continue;
								}

								String testCaseName = testCase.getName().trim();
								String path = featurePath + "/" + testCaseName;
								LOGGER.error("[EXCEL DEBUG] CAS DE TEST = " + path);

								addExcelElement(items, ImportSquashTmComparisonItem.TYPE_TEST_CASE, testCaseName, path);
							}
						}
					}
				}
			}
		}
	}

	/**
	 * Ajoute un dossier attendu dans la comparaison.
	 */
	private void addExcelFolder(Map<String, ImportSquashTmComparisonItem> items, String path, String folderType) {

		String name = path.substring(path.lastIndexOf('/') + 1);

		addExcelElement(items, folderType, name, path);
	}

	/**
	 * Ajoute un élément Excel dans la liste.
	 */
	private void addExcelElement(Map<String, ImportSquashTmComparisonItem> items, String type, String name,
			String path) {

		String key = buildComparisonKey(type, path);

		ImportSquashTmComparisonItem item = new ImportSquashTmComparisonItem(key, type, name, path,
				ImportSquashTmComparisonItem.STATUS_EXCEL_ONLY, 0);

		items.put(key, item);

		LOGGER.info("[EXCEL ELEMENT PATH] Type=" + type + " | Nom=" + name + " | Chemin=" + path + " | Clé=" + key);
	}

	private String buildComparisonKey(String type, String path) {

		return type + "|" + normalizeComparisonPath(path);
	}

	private JsonArray inspectFolderContents(JsonElement folderTree, boolean requirementFolder) throws IOException {

		JsonArray inspectedFolders = new JsonArray();

		Map<Long, JsonObject> folders = new LinkedHashMap<>();

		collectFolders(folderTree, "", folders);

		LOGGER.info("[FOLDER INSPECTION] Nombre de dossiers détectés : " + folders.size());

		for (Map.Entry<Long, JsonObject> entry : folders.entrySet()) {

			long folderId = entry.getKey();
			JsonObject folder = entry.getValue();

			JsonArray allContent = new JsonArray();

			int page = 0;
			int totalPages = 1;

			do {

				JsonObject response;

				if (requirementFolder) {
					response = squashTmService.getRequirementFolderContent(folderId, page, 100);
				} else {
					response = squashTmService.getTestCaseFolderContent(folderId, page, 100);
				}

				LOGGER.info("[FOLDER INSPECTION] Dossier " + folderId + " | Page " + page + " | Type : "
						+ (requirementFolder ? "REQUIREMENTS" : "TEST CASES"));

				LOGGER.info(response != null ? response.toString() : "CONTENU NULL");

				if (response == null) {
					throw new IOException("Réponse vide pour le dossier " + folderId);
				}

				if (response.has("_embedded") && response.get("_embedded").isJsonObject()) {

					JsonObject embedded = response.getAsJsonObject("_embedded");

					if (embedded.has("content") && embedded.get("content").isJsonArray()) {
						allContent.addAll(embedded.getAsJsonArray("content"));
					}
				}

				if (response.has("page") && response.get("page").isJsonObject()) {

					JsonObject pageInfo = response.getAsJsonObject("page");

					if (pageInfo.has("totalPages") && !pageInfo.get("totalPages").isJsonNull()) {
						totalPages = pageInfo.get("totalPages").getAsInt();
					}
				}

				page++;

			} while (page < totalPages);

			JsonObject inspectedFolder = new JsonObject();

			inspectedFolder.addProperty("id", folderId);

			if (folder.has("path")) {
				inspectedFolder.add("path", folder.get("path"));
			}

			if (folder.has("name")) {
				inspectedFolder.add("name", folder.get("name"));
			}

			inspectedFolder.add("content", allContent);

			inspectedFolders.add(inspectedFolder);

			LOGGER.info("[FOLDER INSPECTION] Dossier " + folderId + " : " + allContent.size() + " éléments récupérés.");
		}

		return inspectedFolders;
	}

	private void collectFolders(JsonElement element, String parentPath, Map<Long, JsonObject> folders) {

		if (element == null || element.isJsonNull()) {
			return;
		}

		if (element.isJsonArray()) {

			for (JsonElement child : element.getAsJsonArray()) {
				collectFolders(child, parentPath, folders);
			}

			return;
		}

		if (!element.isJsonObject()) {
			return;
		}

		JsonObject parent = element.getAsJsonObject();

		for (String property : new String[] { "folders", "children" }) {

			if (!parent.has(property) || !parent.get(property).isJsonArray()) {
				continue;
			}

			for (JsonElement childElement : parent.getAsJsonArray(property)) {

				if (!childElement.isJsonObject()) {
					continue;
				}

				JsonObject node = childElement.getAsJsonObject();

				if (!node.has("id") || node.get("id").isJsonNull() || !node.has("name")
						|| node.get("name").isJsonNull()) {
					continue;
				}

				if (node.has("_type") && !node.get("_type").isJsonNull()
						&& !node.get("_type").getAsString().toLowerCase().contains("folder")) {
					continue;
				}

				long folderId = node.get("id").getAsLong();
				String folderName = node.get("name").getAsString().trim();
				String currentPath = parentPath.isEmpty() ? folderName : parentPath + "/" + folderName;

				if (!folders.containsKey(folderId)) {

					JsonObject folderInfo = new JsonObject();

					folderInfo.addProperty("id", folderId);
					folderInfo.addProperty("name", folderName);
					folderInfo.addProperty("path", currentPath);

					folders.put(folderId, folderInfo);
				}

				collectFolders(node, currentPath, folders);
			}
		}
	}

	/**
	 * Normalise un chemin utilisé dans les comparaisons.
	 */
	private String normalizeComparisonPath(String path) {

		if (isEmpty(path)) {
			return "";
		}

		String[] segments = path.replace("\\", "/").split("/");
		StringBuilder normalized = new StringBuilder();

		for (String segment : segments) {

			String value = segment.trim();

			if (value.isEmpty()) {
				continue;
			}

			if (normalized.length() > 0) {
				normalized.append("/");
			}

			normalized.append(value);
		}

		return normalized.toString();
	}

}