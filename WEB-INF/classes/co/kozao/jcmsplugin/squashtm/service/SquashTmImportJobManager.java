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

	private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
		Thread thread = new Thread(runnable, "squashtm-import");
		thread.setDaemon(true);
		return thread;
	});

	private SquashTmImportJobManager() {
	}

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