package co.kozao.jcmsplugin.squashtm.handler;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

import org.apache.log4j.Logger;

import com.jalios.jcms.handler.JcmsFormHandler;
import com.jalios.jcms.upload.DocUploadInfo;

import co.kozao.jcmsplugin.squashtm.model.AuthentificationMode;
import co.kozao.jcmsplugin.squashtm.util.SquashTmUtils;
import co.kozao.jcmsplugin.squashtm.model.squashimport.ImportSquashTmResult;
import co.kozao.jcmsplugin.squashtm.service.SquashTmImportJobManager;
import co.kozao.jcmsplugin.squashtm.service.SquashTmImportManager;

/**
 * Handler responsable du lancement de l'import d'un fichier Excel vers Squash
 * TM.
 *
 * <p>
 * Le Handler récupère les données provenant de la JSP puis délègue toute la
 * logique d'import au {@link SquashTmImportManager}.
 * </p>
 */
public class SquashTmImportHandler extends JcmsFormHandler {

	private static final Logger LOGGER = Logger.getLogger(SquashTmImportHandler.class);

	/**
	 * Fichier Excel sélectionné par l'utilisateur.
	 */
	// private File excelFile;
	private String projectName;
	private boolean opImport;

	private final SquashTmImportJobManager importJobManager = SquashTmImportJobManager.getInstance();

	/**
	 * Identifiant de l'importation en cours.
	 */
	private String importJobId;

	/**
	 * Résultat du dernier import exécuté.
	 *
	 * <p>
	 * Cet objet est uniquement utilisé pendant la requête afin de transmettre le
	 * résultat à la JSP.
	 * </p>
	 */
	private ImportSquashTmResult importResult;

	/*
	 * public File getExcelFile() { return excelFile; }
	 * 
	 * public void setExcelFile(File excelFile) { this.excelFile = excelFile; }
	 */

	public String getProjectName() {
		return projectName;
	}

	public String getImportJobId() {
		return importJobId;
	}

	public void setImportJobId(String importJobId) {
		this.importJobId = importJobId;
	}

	public void setProjectName(String projectName) {
		this.projectName = projectName;
	}

	public ImportSquashTmResult getImportResult() {
		return importResult;
	}

	public boolean isOpImport() {
		return opImport;
	}

	public void setOpImport(boolean opImport) {
		this.opImport = opImport;
	}

	/**
	 * Prépare l'en-tête d'authentification avant le lancement de l'importation en
	 * arrière-plan.
	 *
	 * @return l'en-tête Authorization à utiliser pour les appels REST
	 * @throws IOException si les informations d'authentification sont absentes
	 */
	private String prepareAuthorizationHeader() throws IOException {

		AuthentificationMode mode = SquashTmUtils.getAuthentificationMode();

		if (mode == null) {
			throw new IOException("Le mode d'authentification Squash TM n'est pas configuré.");
		}

		// Authentification par token
		if (AuthentificationMode.TOKEN.equals(mode)) {

			String token = SquashTmUtils.getSquashTmApiToken();

			if (token == null || token.trim().isEmpty()) {
				throw new IOException("Le token Squash TM est absent.");
			}

			return "Bearer " + token;
		}

		// Authentification par identifiant et mot de passe
		if (AuthentificationMode.BASIC.equals(mode)) {

			String username = SquashTmUtils.getSquashTmUsername();
			String password = SquashTmUtils.getSquashTmPassword();

			if (username == null || username.trim().isEmpty()) {
				throw new IOException("Le nom d'utilisateur Squash TM est absent.");
			}

			if (password == null) {
				throw new IOException("Le mot de passe Squash TM est absent.");
			}

			String credentials = username + ":" + password;

			String encodedCredentials = Base64.getEncoder()
					.encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

			return "Basic " + encodedCredentials;
		}

		throw new IOException("Mode d'authentification Squash TM inconnu : " + mode);
	}

	/**
	 * Démarre l'importation du fichier Excel en arrière-plan.
	 *
	 * @param uploadedFile fichier reçu par le formulaire
	 * @return true si l'importation a été démarrée
	 */
	public boolean performImport(File uploadedFile) {

		LOGGER.info("[IMPORT][DEBUT] Demande de lancement.");
		LOGGER.info("[IMPORT][PROJET] " + projectName);
		LOGGER.info("[IMPORT][FICHIER] " + (uploadedFile != null ? uploadedFile.getAbsolutePath() : "null"));

		try {

			String authorizationHeader = prepareAuthorizationHeader();

			importJobId = importJobManager.startImport(uploadedFile, projectName, authorizationHeader);

			LOGGER.info("[IMPORT][JOB] Importation démarrée. ID : " + importJobId);

			// Le résultat sera disponible après la fin du traitement.
			importResult = null;

			return true;

		} catch (IOException e) {

			LOGGER.error("[IMPORT][ERREUR] Impossible de démarrer l'importation.", e);

			importResult = new ImportSquashTmResult();
			importResult.setProjectName(projectName);
			importResult.addTechnicalError("Impossible de démarrer l'importation : " + e.getMessage());

			return false;

		} finally {

			LOGGER.info("[IMPORT][FIN] Fin de la demande de lancement.");
		}
	}

	@Override
	public boolean processAction() throws IOException {

		LOGGER.info("[HANDLER][DEBUT] processAction() appelée.");

		LOGGER.error("[HANDLER 1] processAction() appelée.");
		LOGGER.error("[HANDLER 2] opImport = " + opImport);
		LOGGER.error("[HANDLER 3] projectName = " + projectName);

		try {

			if (!opImport) {
				LOGGER.error("[HANDLER 4] Arrêt : opImport vaut false.");
				return false;
			}

			if (projectName == null || projectName.trim().isEmpty()) {

				LOGGER.warn("Le nom du projet est vide.");

				importResult = new ImportSquashTmResult();
				importResult.setProjectName(projectName);
				importResult.addTechnicalError("Le nom du projet Squash TM est obligatoire.");

				return false;

			}

			LOGGER.info("Action d'import demandée.");

			List<DocUploadInfo> uploadedFiles = getUploadedFiles("excelFile");

			LOGGER.error("[HANDLER 5] getUploadedFiles() exécutée.");
			LOGGER.error("[HANDLER 6] Nombre de fichiers reçus : "
					+ (uploadedFiles == null ? "null" : uploadedFiles.size()));

			if (uploadedFiles == null || uploadedFiles.isEmpty()) {

				LOGGER.warn("Aucun fichier Excel reçu.");
				importResult = new ImportSquashTmResult();
				importResult.setProjectName(projectName);
				importResult.addTechnicalError("Aucun fichier Excel n'a été reçu.");

				return false;
			}

			DocUploadInfo uploadInfo = uploadedFiles.get(0);

			File uploadedFile = uploadInfo.getFile();

			if (uploadedFile == null || !uploadedFile.exists()) {

				LOGGER.warn("Le fichier uploadé est introuvable.");

				importResult = new ImportSquashTmResult();

				importResult.setProjectName(projectName);
				importResult.addTechnicalError("Le fichier Excel uploadé est introuvable.");

				return false;
			}
			LOGGER.info("Fichier Excel reçu : " + uploadedFile.getName());

			LOGGER.error("[HANDLER 7] Fichier : " + uploadedFile.getAbsolutePath());

			boolean importExecuted = performImport(uploadedFile);
			LOGGER.info("[DEBUG IMPORT] importExecuted = " + importExecuted);
			LOGGER.info("[DEBUG IMPORT] importJobId = " + importJobId);

			LOGGER.error("[HANDLER 8] Retour de performImport().");
			LOGGER.error("[HANDLER 9] importExecuted = " + importExecuted);
			LOGGER.error("[HANDLER 10] importResult = " + importResult);

			if (!importExecuted) {
				return false;
			}

			return false;

		} finally {

			LOGGER.info("[HANDLER][FIN] Fin de processAction().");
		}

	}
}