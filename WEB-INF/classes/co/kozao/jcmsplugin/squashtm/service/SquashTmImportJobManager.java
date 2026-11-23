package co.kozao.jcmsplugin.squashtm.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.log4j.Logger;

import co.kozao.jcmsplugin.squashtm.model.squashimport.ImportSquashTmProgress;
import co.kozao.jcmsplugin.squashtm.model.squashimport.ImportSquashTmResult;
import co.kozao.jcmsplugin.squashtm.model.squashimport.ImportSquashTmAnalysis;
import co.kozao.jcmsplugin.squashtm.model.squashimport.ImportSquashTmComparisonItem;
import co.kozao.jcmsplugin.squashtm.model.squashimport.ImportSquashTmData;
import co.kozao.jcmsplugin.squashtm.model.squashimport.PendingImport;
import co.kozao.jcmsplugin.squashtm.util.SquashTmConstants;

/**
 * Gère les importations Squash TM exécutées en arrière-plan.
 *
 * Chaque importation possède un identifiant, un état de progression et un
 * résultat final.
 */
public class SquashTmImportJobManager {

	private static final Logger LOGGER = Logger.getLogger(SquashTmImportJobManager.class);

	private static final SquashTmImportJobManager INSTANCE = new SquashTmImportJobManager();

	private final ConcurrentHashMap<String, ImportJob> jobs = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, PendingImport> pendingImports = new ConcurrentHashMap<>();

	private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
		Thread thread = new Thread(runnable, "squashtm-import");
		thread.setDaemon(true);
		return thread;
	});

	public static SquashTmImportJobManager getInstance() {
		return INSTANCE;
	}

	/**
	 * Prépare et démarre une importation en arrière-plan.
	 *
	 * Le fichier est copié dans un fichier temporaire afin de ne pas dépendre de la
	 * durée de vie du fichier reçu par le formulaire.
	 *
	 * @param uploadedFile fichier reçu par le formulaire
	 * @param projectName  nom du projet Squash TM
	 * @return identifiant unique de l'importation
	 * @throws IOException si le fichier ne peut pas être préparé
	 */
	public String startImport(File uploadedFile, String projectName, String authorizationHeader) throws IOException {

		if (authorizationHeader == null || authorizationHeader.trim().isEmpty()) {
			throw new IOException("Les informations d'authentification Squash TM sont absentes.");
		}

		if (uploadedFile == null || !uploadedFile.isFile()) {
			throw new IOException("Le fichier Excel est introuvable.");
		}

		if (projectName == null || projectName.trim().isEmpty()) {
			throw new IOException("Le nom du projet Squash TM est obligatoire.");
		}

		File temporaryFile = Files.createTempFile("squashtm-import-", ".xlsx").toFile();

		try {
			Files.copy(uploadedFile.toPath(), temporaryFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			temporaryFile.delete();
			throw e;
		}

		String jobId = UUID.randomUUID().toString();
		ImportJob job = new ImportJob();

		jobs.put(jobId, job);

		try {
			executor.submit(() -> {
				try {
					LOGGER.info("[IMPORT JOB] Début : " + jobId);

					SquashTmImportManager importManager = new SquashTmImportManager(authorizationHeader);

					ImportSquashTmResult result = importManager.importExcel(temporaryFile, projectName, job.progress);

					job.result = result;

					if (result != null && result.isSuccess()) {
						job.progress.finish(true, "Importation terminée avec succès.");
					} else {
						job.progress.finish(false, "L'importation est terminée avec des erreurs.");
					}

					LOGGER.info("[IMPORT JOB] Fin : " + jobId);

				} catch (Exception e) {
					LOGGER.error("[IMPORT JOB] Erreur pendant l'importation : " + jobId, e);

					ImportSquashTmResult failedResult = new ImportSquashTmResult();
					failedResult.setSuccess(false);
					failedResult.setProjectName(projectName);
					failedResult.setFileName(uploadedFile.getName());
					failedResult.addTechnicalError("Une erreur technique est survenue pendant l'importation : "
							+ (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));

					// Publier le résultat avant de terminer la progression.
					job.result = failedResult;

					job.progress.finish(false, "Une erreur technique est survenue pendant l'importation.");

				} finally {
					try {
						Files.deleteIfExists(temporaryFile.toPath());
					} catch (IOException e) {
						LOGGER.warn("Impossible de supprimer le fichier temporaire.", e);
					}
				}
			});
		} catch (RuntimeException e) {
			jobs.remove(jobId);
			Files.deleteIfExists(temporaryFile.toPath());
			throw new IOException("Impossible de démarrer l'importation.", e);
		}

		return jobId;
	}

	/**
	 * Analyse un fichier Excel sans effectuer d'opération dans Squash TM.
	 *
	 * Le fichier est conservé temporairement pour permettre son importation après
	 * confirmation de l'utilisateur.
	 *
	 * @param uploadedFile fichier reçu du formulaire
	 * @param projectName  nom du projet
	 * @return identifiant de l'analyse en attente
	 * @throws IOException si le fichier ne peut pas être analysé
	 */
	public String analyzeImport(File uploadedFile, String projectName, String authorizationHeader) throws IOException {

		if (uploadedFile == null || !uploadedFile.isFile()) {
			throw new IOException("Le fichier Excel est introuvable.");
		}

		if (projectName == null || projectName.trim().isEmpty()) {
			throw new IOException("Le nom du projet est obligatoire.");
		}

		if (authorizationHeader == null || authorizationHeader.trim().isEmpty()) {
			throw new IOException("Les informations d'authentification Squash TM sont absentes.");
		}

		File temporaryFile = Files.createTempFile("squashtm-pending-", ".xlsx").toFile();

		try {
			Files.copy(uploadedFile.toPath(), temporaryFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

			return runAnalysis(temporaryFile, projectName.trim(), authorizationHeader, null);

		} catch (Exception e) {

			Files.deleteIfExists(temporaryFile.toPath());

			if (e instanceof IOException) {
				throw (IOException) e;
			}

			throw new IOException("Impossible d'analyser le fichier Excel.", e);
		}
	}

	/**
	 * Exécute l'analyse (lecture du fichier puis comparaison avec Squash TM). La
	 * progression est facultative.
	 */
	private String runAnalysis(File temporaryFile, String projectName, String authorizationHeader,
			ImportSquashTmProgress progress) throws IOException {

		ExcelSquashTmImportService analysisService = new ExcelSquashTmImportService();

		if (progress != null) {
			progress.update(10, "ANALYSE_FICHIER", "Lecture du fichier Excel...");
		}

		ImportSquashTmData importData = analysisService.parse(temporaryFile, projectName, progress);

		if (progress != null) {
			progress.update(40, "ANALYSE_FICHIER", "Fichier Excel analysé.");
		}

		ImportSquashTmAnalysis analysis = analysisService.buildAnalysis(projectName);

		SquashTmImportManager importManager = new SquashTmImportManager(authorizationHeader);

		importManager.compareExistingProject(importData, analysis, progress);

		if (analysis.isExistingProject() && analysis.getExistingProjectId() <= 0) {
			throw new IOException("Le projet existe, mais son identifiant est introuvable.");
		}

		if (analysis.getComparisonItems() == null) {
			throw new IOException("La comparaison n'a produit aucun résultat exploitable.");
		}

		if (progress != null) {
			progress.update(95, "SYNTHESE", "Préparation de la synthèse...");
		}

		String analysisId = UUID.randomUUID().toString();

		pendingImports.put(analysisId, new PendingImport(temporaryFile, projectName, analysis, importData));

		LOGGER.info("[IMPORT ANALYSIS] Projet existant : " + analysis.isExistingProject());
		LOGGER.info("[IMPORT ANALYSIS] Nombre d'éléments comparés : " + analysis.getComparisonItems().size());
		LOGGER.info("[IMPORT ANALYSIS] Analyse terminée : " + analysisId);

		return analysisId;
	}

	/**
	 * Démarre l'analyse en arrière-plan. Le fichier est copié avant le retour de la
	 * méthode, car le fichier du formulaire disparaît à la fin de la requête.
	 *
	 * @return identifiant du travail, à utiliser avec getProgress()
	 */
	public String startAnalysis(File uploadedFile, String projectName, String authorizationHeader) throws IOException {

		if (uploadedFile == null || !uploadedFile.isFile()) {
			throw new IOException("Le fichier Excel est introuvable.");
		}

		if (projectName == null || projectName.trim().isEmpty()) {
			throw new IOException("Le nom du projet est obligatoire.");
		}

		if (authorizationHeader == null || authorizationHeader.trim().isEmpty()) {
			throw new IOException("Les informations d'authentification Squash TM sont absentes.");
		}

		final String cleanProjectName = projectName.trim();

		final File temporaryFile = Files.createTempFile("squashtm-pending-", ".xlsx").toFile();

		try {
			Files.copy(uploadedFile.toPath(), temporaryFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			temporaryFile.delete();
			throw e;
		}

		final String jobId = UUID.randomUUID().toString();
		final ImportJob job = new ImportJob();

		jobs.put(jobId, job);

		job.progress.update(5, "PREPARATION", "Fichier reçu, préparation de l'analyse...");

		try {
			executor.submit(() -> {
				try {
					// L'identifiant est publié avant la fin de la progression.
					job.analysisId = runAnalysis(temporaryFile, cleanProjectName, authorizationHeader, job.progress);

					job.progress.finish(true, "Analyse terminée.");

				} catch (Exception e) {

					LOGGER.error("[IMPORT ANALYSIS] Erreur pendant l'analyse : " + jobId, e);

					try {
						Files.deleteIfExists(temporaryFile.toPath());
					} catch (IOException deleteException) {
						LOGGER.warn("Impossible de supprimer le fichier temporaire.", deleteException);
					}

					job.errorMessage = e.getMessage() != null ? e.getMessage()
							: "Une erreur technique est survenue pendant l'analyse.";

					job.progress.finish(false, "L'analyse a échoué.");
				}
			});
		} catch (RuntimeException e) {
			jobs.remove(jobId);
			Files.deleteIfExists(temporaryFile.toPath());
			throw new IOException("Impossible de démarrer l'analyse.", e);
		}

		return jobId;
	}

	/**
	 * Identifiant de l'analyse produite par un travail (null tant qu'elle n'est pas
	 * prête).
	 */
	public String getJobAnalysisId(String jobId) {
		ImportJob job = jobs.get(jobId);
		return job == null ? null : job.analysisId;
	}

	/** Message d'erreur d'un travail d'analyse échoué (null sinon). */
	public String getJobError(String jobId) {
		ImportJob job = jobs.get(jobId);
		return job == null ? null : job.errorMessage;
	}

	/**
	 * Retourne la synthèse d'une analyse en attente.
	 *
	 * @param analysisId identifiant de l'analyse
	 * @return synthèse ou null si l'identifiant est inconnu
	 */
	public ImportSquashTmAnalysis getAnalysis(String analysisId) {
		cleanupExpiredAnalyses();

		PendingImport pending = pendingImports.get(analysisId);

		if (pending == null) {
			return null;
		}

		return pending.getAnalysis();
	}

	/**
	 * Confirme une analyse et démarre le traitement en arrière-plan.
	 *
	 * Nouveau projet : exécute le processus d'importation habituel. Projet existant
	 * : applique uniquement les décisions sélectionnées.
	 */
	public String confirmImport(String analysisId, String authorizationHeader, String selectedComparisonIndexes)
			throws IOException {

		if (analysisId == null || analysisId.trim().isEmpty()) {
			throw new IOException("L'identifiant de l'analyse est absent.");
		}

		if (authorizationHeader == null || authorizationHeader.trim().isEmpty()) {
			throw new IOException("Les informations d'authentification Squash TM sont absentes.");
		}

		cleanupExpiredAnalyses();

		PendingImport pending = pendingImports.get(analysisId);

		if (pending == null) {
			throw new IOException("Cette analyse n'existe plus ou a déjà été utilisée.");
		}

		synchronized (pending) {

			// Vérifier que l'analyse est toujours disponible.
			if (pendingImports.get(analysisId) != pending) {
				throw new IOException("Cette analyse a déjà été confirmée ou annulée.");
			}

			// Pour un projet existant, appliquer les sélections.
			if (pending.getAnalysis().isExistingProject()) {

				if (!pending.getAnalysis().hasActionsToPerform()) {
					throw new IOException(SquashTmConstants.ALREADY_UP_TO_DATE_MESSAGE);
				}

				applySelectedComparisonIndexes(pending.getAnalysis(), selectedComparisonIndexes);

				validateSelectedActions(pending.getAnalysis());

				if (!hasSelectedAction(pending.getAnalysis())) {
					throw new IOException(SquashTmConstants.NO_SELECTION_MESSAGE);
				}
			}

			// Retirer l'analyse pour empêcher une deuxième confirmation.
			if (!pendingImports.remove(analysisId, pending)) {
				throw new IOException("Cette analyse a déjà été confirmée ou annulée.");
			}
		}

		// CAS 1 : le projet existe déjà dans Squash TM.
		if (pending.getAnalysis().isExistingProject()) {

			try {
				return startExistingProjectImport(pending, authorizationHeader);

			} catch (IOException e) {

				try {
					Files.deleteIfExists(pending.getTemporaryFile().toPath());
				} catch (IOException deleteException) {
					LOGGER.warn("Impossible de supprimer le fichier temporaire.", deleteException);
				}

				throw e;
			}
		}

		// CAS 2 : le projet n'existe pas encore.
		// On conserve le processus d'importation actuel.
		try {
			return startImport(pending.getTemporaryFile(), pending.getProjectName(), authorizationHeader);

		} finally {

			try {
				Files.deleteIfExists(pending.getTemporaryFile().toPath());
			} catch (IOException e) {
				LOGGER.warn("Impossible de supprimer le fichier temporaire de l'analyse.", e);
			}
		}
	}

	/**
	 * Annule une analyse en attente et supprime son fichier temporaire.
	 *
	 * @return true si l'analyse a été annulée, false si elle n'existait plus
	 */
	public boolean cancelAnalysis(String analysisId) {

		if (analysisId == null || analysisId.trim().isEmpty()) {
			return false;
		}

		PendingImport pending = pendingImports.get(analysisId);

		if (pending == null) {
			return false;
		}

		synchronized (pending) {

			if (!pendingImports.remove(analysisId, pending)) {
				return false;
			}
		}

		try {
			Files.deleteIfExists(pending.getTemporaryFile().toPath());
		} catch (IOException e) {
			LOGGER.warn("Impossible de supprimer le fichier temporaire de l'analyse annulée.", e);
		}

		LOGGER.info("[IMPORT ANALYSIS] Analyse annulée : " + analysisId);

		return true;
	}

	/**
	 * Supprime les analyses en attente depuis plus de 30 minutes.
	 */
	private void cleanupExpiredAnalyses() {

		final long expirationDelay = 30L * 60L * 1000L;

		final long currentTime = System.currentTimeMillis();

		for (String analysisId : pendingImports.keySet()) {

			PendingImport pending = pendingImports.get(analysisId);

			if (pending == null) {
				continue;
			}

			synchronized (pending) {

				// L'analyse a peut-être déjà été confirmée ou annulée.
				if (pendingImports.get(analysisId) != pending) {
					continue;
				}

				long age = currentTime - pending.getCreatedAt();

				if (age >= expirationDelay && pendingImports.remove(analysisId, pending)) {

					try {
						Files.deleteIfExists(pending.getTemporaryFile().toPath());
					} catch (IOException e) {
						LOGGER.warn("Impossible de supprimer le fichier d'analyse expirée.", e);
					}

					LOGGER.info("[IMPORT ANALYSIS] Analyse expirée : " + analysisId);
				}
			}
		}
	}

	/**
	 * Retourne la progression d'une importation.
	 */
	public ImportSquashTmProgress getProgress(String jobId) {
		ImportJob job = jobs.get(jobId);
		return job == null ? null : job.progress;
	}

	/**
	 * Retourne le résultat final, disponible lorsque l'importation a terminé son
	 * traitement.
	 */
	public ImportSquashTmResult getResult(String jobId) {
		ImportJob job = jobs.get(jobId);
		return job == null ? null : job.result;
	}

	/**
	 * Démarre l'application des décisions sur un projet Squash TM existant.
	 */
	private String startExistingProjectImport(PendingImport pending, String authorizationHeader) throws IOException {

		String jobId = UUID.randomUUID().toString();

		ImportJob job = new ImportJob();

		jobs.put(jobId, job);

		try {
			executor.submit(() -> {

				try {
					LOGGER.info("[IMPORT JOB] Application des décisions : " + jobId);

					SquashTmImportManager importManager = new SquashTmImportManager(authorizationHeader);

					ImportSquashTmResult result = importManager.applySelectedActions(pending.getImportData(),
							pending.getAnalysis(), job.progress);

					job.result = result;

					if (result != null && result.isSuccess()) {
						job.progress.finish(true, "Traitement terminé avec succès.");
					} else {
						job.progress.finish(false, "Le traitement est terminé avec des erreurs.");
					}

					LOGGER.info("[IMPORT JOB] Fin : " + jobId);

				} catch (Exception e) {

					LOGGER.error("[IMPORT JOB] Erreur pendant l'application des décisions : " + jobId, e);

					ImportSquashTmResult failedResult = new ImportSquashTmResult();
					failedResult.setSuccess(false);
					failedResult.setProjectName(pending.getProjectName());
					failedResult.setFileName(pending.getTemporaryFile().getName());
					failedResult.setExistingProjectReused(true);

					failedResult.addTechnicalError("Une erreur technique est survenue : "
							+ (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));

					job.result = failedResult;

					job.progress.finish(false, "Une erreur technique est survenue.");

				} finally {

					try {
						Files.deleteIfExists(pending.getTemporaryFile().toPath());
					} catch (IOException e) {
						LOGGER.warn("Impossible de supprimer le fichier temporaire.", e);
					}
				}
			});

		} catch (RuntimeException e) {

			jobs.remove(jobId);

			throw new IOException("Impossible de démarrer le traitement du projet existant.", e);
		}

		return jobId;
	}

	private void applySelectedComparisonIndexes(ImportSquashTmAnalysis analysis, String selectedComparisonIndexes)
			throws IOException {

		if (analysis == null || analysis.getComparisonItems() == null) {
			throw new IOException("Analyse d'importation invalide.");
		}

		List<ImportSquashTmComparisonItem> items = analysis.getComparisonItems();

		// Toutes les actions sont ignorées par défaut.
		for (ImportSquashTmComparisonItem item : items) {
			item.setSelectedAction(ImportSquashTmComparisonItem.ACTION_IGNORE);
		}

		// Une sélection vide est autorisée.
		if (selectedComparisonIndexes == null || selectedComparisonIndexes.trim().isEmpty()) {
			return;
		}

		Set<Integer> selectedIndexes = new LinkedHashSet<>();

		// -1 permet de détecter également les éléments vides en fin de chaîne.
		String[] values = selectedComparisonIndexes.split(",", -1);

		for (String value : values) {

			if (value == null || value.trim().isEmpty()) {
				throw new IOException("La liste des éléments sélectionnés est invalide.");
			}

			final int index;

			try {
				index = Integer.parseInt(value.trim());
			} catch (NumberFormatException e) {
				throw new IOException("Indice de sélection invalide : " + value);
			}

			if (index < 0 || index >= items.size()) {
				throw new IOException("Indice de sélection hors limites : " + index);
			}

			if (!selectedIndexes.add(index)) {
				throw new IOException("Indice de sélection répété : " + index);
			}
		}

		// Attribution des actions autorisées.
		for (Integer index : selectedIndexes) {

			ImportSquashTmComparisonItem item = items.get(index);

			if (ImportSquashTmComparisonItem.STATUS_EXCEL_ONLY.equals(item.getStatus())) {

				item.setSelectedAction(ImportSquashTmComparisonItem.ACTION_CREATE);

			} else if (ImportSquashTmComparisonItem.STATUS_RENAME.equals(item.getStatus())) {

				item.setSelectedAction(ImportSquashTmComparisonItem.ACTION_UPDATE);

			} else {

				throw new IOException("Cet élément ne peut pas être sélectionné : " + item.getElementName());
			}
		}
	}

	private boolean hasSelectedAction(ImportSquashTmAnalysis analysis) {

		if (analysis == null || analysis.getComparisonItems() == null) {
			return false;
		}

		for (ImportSquashTmComparisonItem item : analysis.getComparisonItems()) {

			if (item == null) {
				continue;
			}

			String action = item.getSelectedAction();

			if (ImportSquashTmComparisonItem.ACTION_CREATE.equals(action)
					|| ImportSquashTmComparisonItem.ACTION_UPDATE.equals(action)) {
				return true;
			}
		}

		return false;
	}

	private void validateSelectedActions(ImportSquashTmAnalysis analysis) throws IOException {

		if (analysis == null || analysis.getComparisonItems() == null) {
			throw new IOException("Analyse d'importation invalide.");
		}

		for (ImportSquashTmComparisonItem item : analysis.getComparisonItems()) {

			String status = item.getStatus();
			String action = item.getSelectedAction();

			if (action == null || ImportSquashTmComparisonItem.ACTION_IGNORE.equals(action)) {
				continue;
			}

			if (ImportSquashTmComparisonItem.STATUS_EXCEL_ONLY.equals(status)) {

				if (!ImportSquashTmComparisonItem.ACTION_CREATE.equals(action)) {
					throw new IOException("Action non autorisée pour : " + item.getElementName());
				}

			} else if (ImportSquashTmComparisonItem.STATUS_RENAME.equals(status)) {

				if (!ImportSquashTmComparisonItem.ACTION_UPDATE.equals(action)) {
					throw new IOException("Action non autorisée pour : " + item.getElementName());
				}

				if (item.getSquashId() <= 0) {
					throw new IOException(
							"Identifiant Squash TM manquant pour le renommage de : " + item.getElementName());
				}

			} else {
				throw new IOException("Une action non autorisée a été détectée pour : " + item.getElementName());
			}
		}
	}

	private static class ImportJob {

		private final ImportSquashTmProgress progress = new ImportSquashTmProgress();

		private volatile ImportSquashTmResult result;

		private volatile String analysisId;

		private volatile String errorMessage;
	}
}