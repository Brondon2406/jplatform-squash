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
import co.kozao.jcmsplugin.squashtm.model.squashimport.ImportSquashTmAnalysis;
import co.kozao.jcmsplugin.squashtm.model.squashimport.ImportSquashTmResult;
import co.kozao.jcmsplugin.squashtm.service.SquashTmImportJobManager;

/**
 * Handler responsable du lancement de l'import d'un fichier Excel vers Squash
 * TM.
 *
 */
public class SquashTmImportHandler extends JcmsFormHandler {

	private String projectName;
	private boolean opAnalyze;
	private boolean opImport;
	private boolean opCancelAnalysis;
	private String analysisId;
	private String importJobId;
	private ImportSquashTmAnalysis importAnalysis;

	private final SquashTmImportJobManager importJobManager = SquashTmImportJobManager.getInstance();
	private static final Logger LOGGER = Logger.getLogger(SquashTmImportHandler.class);

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

	public boolean isOpAnalyze() {
		return opAnalyze;
	}

	public void setOpAnalyze(boolean opAnalyze) {
		this.opAnalyze = opAnalyze;
	}

	public boolean isOpCancelAnalysis() {
		return opCancelAnalysis;
	}

	public void setOpCancelAnalysis(boolean opCancelAnalysis) {
		this.opCancelAnalysis = opCancelAnalysis;
	}

	public String getAnalysisId() {
		return analysisId;
	}

	public void setAnalysisId(String analysisId) {
		this.analysisId = analysisId;
	}

	public ImportSquashTmAnalysis getImportAnalysis() {
		return importAnalysis;
	}

	private void setImportError(String message) {

		importResult = new ImportSquashTmResult();
		importResult.setProjectName(projectName);
		importResult.addTechnicalError(message);
	}
	
	
	/**
	 * Retourne le taux de validité formaté avec deux décimales.
	 *
	 * @return le taux formaté selon les conventions françaises
	 */
	public String getFormattedValidityRate() {
	    if (importAnalysis == null) {
	        return "0,00";
	    }

	    return String.format(
	        java.util.Locale.FRANCE,
	        "%.2f",
	        importAnalysis.getValidityRate()
	    );
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

		if (AuthentificationMode.TOKEN.equals(mode)) {

			String token = SquashTmUtils.getSquashTmApiToken();

			if (token == null || token.trim().isEmpty()) {
				throw new IOException("Le token Squash TM est absent.");
			}

			return "Bearer " + token;
		}

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

		LOGGER.info("[HANDLER 1] processAction() appelée.");
		LOGGER.info("[HANDLER 2] opAnalyze = " + opAnalyze);
		LOGGER.info("[HANDLER 3] opImport = " + opImport);
		LOGGER.info("[HANDLER 4] opCancelAnalysis = " + opCancelAnalysis);
		LOGGER.info("[HANDLER 5] projectName = " + projectName);
		LOGGER.info("[HANDLER 6] analysisId = " + analysisId);

		try {

			if (opAnalyze) {

				LOGGER.info("[HANDLER 7] Action détectée : ANALYSE.");

				if (projectName == null || projectName.trim().isEmpty()) {

					LOGGER.warn("[HANDLER 8] Arrêt : nom du projet vide.");

					setImportError("Le nom du projet Squash TM est obligatoire.");

					return false;
				}

				LOGGER.info("[HANDLER 9] Récupération du fichier uploadé.");

				List<DocUploadInfo> uploadedFiles = getUploadedFiles("excelFile");

				LOGGER.info("[HANDLER 10] Nombre de fichiers reçus : "
						+ (uploadedFiles == null ? "null" : uploadedFiles.size()));

				if (uploadedFiles == null || uploadedFiles.isEmpty()) {

					LOGGER.warn("[HANDLER 11] Arrêt : aucun fichier reçu.");

					setImportError("Aucun fichier Excel n'a été reçu.");

					return false;
				}

				File uploadedFile = uploadedFiles.get(0).getFile();

				LOGGER.info("[HANDLER 12] Fichier reçu : "
						+ (uploadedFile != null ? uploadedFile.getAbsolutePath() : "null"));

				if (uploadedFile == null || !uploadedFile.isFile()) {

					LOGGER.warn("[HANDLER 13] Arrêt : fichier introuvable.");

					setImportError("Le fichier Excel uploadé est introuvable.");

					return false;
				}

				LOGGER.info("[HANDLER 14] Appel de analyzeImport().");

				analysisId = importJobManager.analyzeImport(uploadedFile, projectName.trim());

				LOGGER.info("[HANDLER 15] Analyse terminée. ID : " + analysisId);

				importAnalysis = importJobManager.getAnalysis(analysisId);

				LOGGER.info("[HANDLER 16] Récupération de la synthèse terminée.");
				LOGGER.info("[HANDLER 17] importAnalysis = " + (importAnalysis != null ? "disponible" : "null"));

				if (importAnalysis == null) {

					LOGGER.error("[HANDLER 18] Arrêt : synthèse introuvable.");

					importJobManager.cancelAnalysis(analysisId);
					analysisId = null;

					setImportError("Impossible de récupérer la synthèse de l'analyse.");

					return false;
				}

				LOGGER.info("[HANDLER 19] Analyse prête à être affichée.");

				return false;
			}
			
			if (opCancelAnalysis) {

				LOGGER.info("[HANDLER 20] Action détectée : ANNULATION.");
				LOGGER.info("[HANDLER 21] Analyse à annuler : " + analysisId);

				boolean cancelled = importJobManager.cancelAnalysis(analysisId);

				LOGGER.info("[HANDLER 22] Résultat de l'annulation : " + cancelled);

				analysisId = null;
				importAnalysis = null;
				importJobId = null;
				importResult = null;

				LOGGER.info("[HANDLER 23] Données de l'analyse réinitialisées.");

				return false;
			}

			if (opImport) {

				LOGGER.info("[HANDLER 24] Action détectée : CONFIRMATION.");
				LOGGER.info("[HANDLER 25] Vérification de l'identifiant d'analyse.");

				if (analysisId == null || analysisId.trim().isEmpty()) {

					LOGGER.error("[HANDLER 26] Arrêt : identifiant d'analyse absent.");

					setImportError("Aucune analyse à confirmer.");

					return false;
				}

				LOGGER.info("[HANDLER 27] Préparation de l'authentification.");

				String authorizationHeader = prepareAuthorizationHeader();

				LOGGER.info("[HANDLER 28] Authentification préparée.");
				LOGGER.info("[HANDLER 29] Appel de confirmImport().");

				importJobId = importJobManager.confirmImport(analysisId, authorizationHeader);

				LOGGER.info("[HANDLER 30] Importation démarrée. ID : " + importJobId);

				analysisId = null;
				importAnalysis = null;
				importResult = null;

				LOGGER.info("[HANDLER 31] Données de l'analyse réinitialisées.");

				return false;
			}

			LOGGER.warn("[HANDLER 32] Arrêt : aucune action reconnue.");

			return false;

		} catch (IOException e) {

			LOGGER.error("[HANDLER 33] Erreur pendant le traitement.", e);

			if (analysisId != null && !analysisId.trim().isEmpty()) {

				importAnalysis = importJobManager.getAnalysis(analysisId);

				if (importAnalysis == null) {
					analysisId = null;
				}
			}

			setImportError(e.getMessage());

			return false;

		} finally {

			LOGGER.info("[HANDLER 34] Fin de processAction().");
			LOGGER.info("[HANDLER][FIN] Fin de processAction().");
		}
	}
}
