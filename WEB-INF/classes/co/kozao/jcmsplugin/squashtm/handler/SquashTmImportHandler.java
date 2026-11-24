package co.kozao.jcmsplugin.squashtm.handler;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

import com.google.gson.JsonObject;
import com.jalios.jcms.handler.JcmsFormHandler;
import com.jalios.jcms.upload.DocUploadInfo;

import co.kozao.jcmsplugin.squashtm.model.AuthentificationMode;
import co.kozao.jcmsplugin.squashtm.util.SquashTmConstants;
import co.kozao.jcmsplugin.squashtm.util.SquashTmUtils;
import co.kozao.jcmsplugin.squashtm.model.squashimport.ImportSquashTmAnalysis;
import co.kozao.jcmsplugin.squashtm.model.squashimport.ImportSquashTmResult;
import co.kozao.jcmsplugin.squashtm.service.SquashTmExportService;
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
	private boolean opDownloadReport;
	private String analysisId;
	private String importJobId;
	private String selectedComparisonIndexes;
	private ImportSquashTmAnalysis importAnalysis;
	private ImportSquashTmResult importResult;
	private String selectionError;
	private boolean comparisonSelectionError;
	private boolean opAnalyzeAsync;
	private boolean opLoadAnalysis;

	private final SquashTmImportJobManager importJobManager = SquashTmImportJobManager.getInstance();
	private static final Logger LOGGER = Logger.getLogger(SquashTmImportHandler.class);

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

	public boolean isOpAnalyzeAsync() {
		return opAnalyzeAsync;
	}

	public void setOpAnalyzeAsync(boolean opAnalyzeAsync) {
		this.opAnalyzeAsync = opAnalyzeAsync;
	}

	public boolean isOpLoadAnalysis() {
		return opLoadAnalysis;
	}

	public void setOpLoadAnalysis(boolean opLoadAnalysis) {
		this.opLoadAnalysis = opLoadAnalysis;
	}

	public boolean isOpCancelAnalysis() {
		return opCancelAnalysis;
	}

	public void setOpCancelAnalysis(boolean opCancelAnalysis) {
		this.opCancelAnalysis = opCancelAnalysis;
	}

	public boolean isOpDownloadReport() {
		return opDownloadReport;
	}

	public void setOpDownloadReport(boolean opDownloadReport) {
		this.opDownloadReport = opDownloadReport;
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

	public String getSelectedComparisonIndexes() {
		return selectedComparisonIndexes;
	}

	public void setSelectedComparisonIndexes(String selectedComparisonIndexes) {
		this.selectedComparisonIndexes = selectedComparisonIndexes;
	}

	private void setImportError(String message) {

		comparisonSelectionError = false;

		if (SquashTmConstants.NO_SELECTION_MESSAGE.equals(message)) {
			comparisonSelectionError = true;
			return;
		}

		importResult = new ImportSquashTmResult();
		importResult.setProjectName(projectName);
		importResult.addTechnicalError(message);
	}

	/**
	 * Construit un message d'erreur lisible pour l'échec du téléchargement de la
	 * version à jour du module.
	 */
	private String buildDownloadErrorMessage(Throwable e) {

		if (e == null) {
			return "Impossible de télécharger le fichier depuis Squash TM. (erreur inconnue)";
		}

		String detail = e.getMessage();
		if (detail == null || detail.trim().isEmpty()) {
			detail = e.getClass().getSimpleName();
		}

		String lower = detail.toLowerCase();
		if (lower.contains("404") || lower.contains("not found") || lower.contains("introuvable")) {
			return "Impossible de télécharger le fichier depuis Squash TM. (HTTP 404 — projet ou ressource introuvable)";
		}

		return "Impossible de télécharger le fichier depuis Squash TM. (" + detail + ")";
	}

	public String getSelectionError() {
		return selectionError;
	}

	public void setSelectionError(String selectionError) {
		this.selectionError = selectionError;
	}

	public boolean isComparisonSelectionError() {
		return comparisonSelectionError;
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

		return String.format(Locale.FRANCE, "%.2f", importAnalysis.getValidityRate());
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

	/**
	 * Démarre l'analyse en arrière-plan et retourne le résultat sous forme JSON
	 * (jobId ou error). Appelée par startAnalysis.jsp.
	 */
	public JsonObject startAsyncAnalysis() {

		JsonObject json = new JsonObject();

		try {
			if (projectName == null || projectName.trim().isEmpty()) {
				json.addProperty("error", "Le nom du projet Squash TM est obligatoire.");
				return json;
			}

			List<DocUploadInfo> files = getUploadedFiles("excelFile");

			File file = (files == null || files.isEmpty()) ? null : files.get(0).getFile();

			if (file == null || !file.isFile()) {
				json.addProperty("error", "Aucun fichier Excel n'a été reçu.");
				return json;
			}

			String jobId = importJobManager.startAnalysis(file, projectName.trim(), prepareAuthorizationHeader());

			json.addProperty("jobId", jobId);

		} catch (IOException | RuntimeException e) {

			LOGGER.error("[HANDLER][ASYNC] Impossible de démarrer l'analyse.", e);

			json.addProperty("error", e.getMessage() != null ? e.getMessage() : "Impossible de démarrer l'analyse.");
		}

		return json;
	}

	@Override
	public boolean processAction() throws IOException {

		try {

			if (opLoadAnalysis) {

				if (analysisId == null || analysisId.trim().isEmpty()) {
					setImportError("Aucune analyse à afficher.");
					return false;
				}

				importAnalysis = importJobManager.getAnalysis(analysisId);

				if (importAnalysis == null) {
					analysisId = null;
					setImportError("Cette analyse n'existe plus ou a expiré. Relancez l'import.");
				}

				return false;
			}

			if (opAnalyze) {

				if (projectName == null || projectName.trim().isEmpty()) {
					setImportError("Le nom du projet Squash TM est obligatoire.");
					return false;
				}

				List<DocUploadInfo> uploadedFiles = getUploadedFiles("excelFile");

				if (uploadedFiles == null || uploadedFiles.isEmpty()) {
					return false;
				}

				File uploadedFile = uploadedFiles.get(0).getFile();

				if (uploadedFile == null || !uploadedFile.isFile()) {
					setImportError("Le fichier Excel uploadé est introuvable.");

					return false;
				}

				String authorizationHeader = prepareAuthorizationHeader();

				analysisId = importJobManager.analyzeImport(uploadedFile, projectName.trim(), authorizationHeader);
				importAnalysis = importJobManager.getAnalysis(analysisId);

				if (importAnalysis == null) {

					importJobManager.cancelAnalysis(analysisId);
					analysisId = null;

					setImportError("Impossible de récupérer la synthèse de l'analyse.");

					return false;
				}

				return false;
			}

			if (opCancelAnalysis) {

				boolean cancelled = importJobManager.cancelAnalysis(analysisId);
				LOGGER.info("Résultat de l'annulation : " + cancelled);

				analysisId = null;
				importAnalysis = null;
				importJobId = null;
				importResult = null;

				return false;
			}

			if (opDownloadReport) {

				if (projectName == null || projectName.trim().isEmpty()) {
					setImportError("Le nom du projet Squash TM est obligatoire pour le téléchargement.");
					return false;
				}

				String cleanProjectName = projectName.trim();
				LOGGER.info("[HANDLER][DOWNLOAD] Projet : " + cleanProjectName);

				try {
					String authorizationHeader = prepareAuthorizationHeader();
					SquashTmExportService exportService = new SquashTmExportService(authorizationHeader);

					// Génération complète avant d'écrire la réponse HTTP
					byte[] content = exportService.exportProject(cleanProjectName);

					if (content == null || content.length == 0) {
						setImportError("Impossible de télécharger le fichier depuis Squash TM. (fichier vide)");
						return false;
					}

					String fileName = SquashTmExportService.buildFileName(cleanProjectName);

					HttpServletResponse response = getResponse();
					response.reset();
					response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
					response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
					response.setContentLength(content.length);
					response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
					response.setHeader("Pragma", "no-cache");
					response.setDateHeader("Expires", 0);

					try (OutputStream out = response.getOutputStream()) {
						out.write(content);
						out.flush();
					}

					LOGGER.info("Fichier envoyé : " + fileName + " (" + content.length + " octets)");

				} catch (IOException e) {
					LOGGER.error("[HANDLER][DOWNLOAD] Échec I/O du téléchargement depuis Squash TM.", e);
					setImportError(buildDownloadErrorMessage(e));
					return false;
				} catch (RuntimeException e) {
					LOGGER.error("[HANDLER][DOWNLOAD] Erreur runtime pendant le téléchargement.", e);
					setImportError(buildDownloadErrorMessage(e));
					return false;
				} catch (Exception e) {
					LOGGER.error("[HANDLER][DOWNLOAD] Erreur inattendue pendant le téléchargement.", e);
					setImportError(buildDownloadErrorMessage(e));
					return false;
				}
			}

			if (opImport) {

				if (analysisId == null || analysisId.trim().isEmpty()) {
					setImportError("Aucune analyse à confirmer.");

					return false;
				}

				String authorizationHeader = prepareAuthorizationHeader();

				importJobId = importJobManager.confirmImport(analysisId, authorizationHeader,
						selectedComparisonIndexes);
				analysisId = null;
				importAnalysis = null;
				importResult = null;
				selectedComparisonIndexes = null;

				return false;
			}

			return false;

		} catch (IOException | RuntimeException e) {

			if (analysisId != null && !analysisId.trim().isEmpty()) {

				try {
					importAnalysis = importJobManager.getAnalysis(analysisId);
					if (importAnalysis == null) {
						analysisId = null;
					}
				} catch (Exception recoveryEx) {
					LOGGER.warn("[HANDLER] Impossible de récupérer l'analyse après erreur.", recoveryEx);
					analysisId = null;
					importAnalysis = null;
				}
			}

			String message = e.getMessage();
			if (message == null || message.trim().isEmpty()) {
				message = "Une erreur technique est survenue : " + e.getClass().getSimpleName();
			}
			setImportError(message);

			return false;

		} finally {
			LOGGER.info("[HANDLER][FIN] Fin de processAction().");
		}
	}
}