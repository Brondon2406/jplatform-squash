package co.kozao.jcmsplugin.squashtm.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.log4j.Logger;

import co.kozao.jcmsplugin.squashtm.model.squashimport.ImportSquashTmProgress;
import co.kozao.jcmsplugin.squashtm.model.squashimport.ImportSquashTmResult;
import co.kozao.jcmsplugin.squashtm.model.squashimport.ImportSquashTmAnalysis;
import co.kozao.jcmsplugin.squashtm.model.squashimport.PendingImport;

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
	public String analyzeImport(File uploadedFile, String projectName) throws IOException {

		if (uploadedFile == null || !uploadedFile.isFile()) {
			throw new IOException("Le fichier Excel est introuvable.");
		}

		if (projectName == null || projectName.trim().isEmpty()) {
			throw new IOException("Le nom du projet est obligatoire.");
		}

		File temporaryFile = Files.createTempFile("squashtm-pending-", ".xlsx").toFile();

		try {
			Files.copy(uploadedFile.toPath(), temporaryFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

			ExcelSquashTmImportService analysisService = new ExcelSquashTmImportService();

			analysisService.parse(temporaryFile, projectName.trim());

			ImportSquashTmAnalysis analysis = analysisService.buildAnalysis(projectName.trim());

			String analysisId = UUID.randomUUID().toString();

			PendingImport pendingImport = new PendingImport(temporaryFile, projectName.trim(), analysis);

			pendingImports.put(analysisId, pendingImport);

			LOGGER.info("[IMPORT ANALYSIS] Analyse terminée : " + analysisId);

			return analysisId;

		} catch (Exception e) {

			Files.deleteIfExists(temporaryFile.toPath());

			if (e instanceof IOException) {
				throw (IOException) e;
			}

			throw new IOException("Impossible d'analyser le fichier Excel.", e);
		}
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
	 * Confirme une analyse et démarre l'importation en arrière-plan.
	 *
	 * Une analyse ne peut être confirmée qu'une seule fois.
	 */
	public String confirmImport(String analysisId, String authorizationHeader) throws IOException {

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

		if (!pendingImports.remove(analysisId, pending)) {
			throw new IOException("Cette analyse a déjà été confirmée ou annulée.");
		}

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

		PendingImport pending = pendingImports.remove(analysisId);

		if (pending == null) {
			return false;
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

	private static class ImportJob {

		private final ImportSquashTmProgress progress = new ImportSquashTmProgress();

		private volatile ImportSquashTmResult result;
	}

}