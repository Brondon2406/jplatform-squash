package co.kozao.jcmsplugin.squashtm.handler;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.jalios.jcms.Channel;
import com.jalios.jcms.Member;
import com.jalios.jcms.handler.JcmsFormHandler;
import com.jalios.jcms.upload.DocUploadInfo;
import com.jalios.util.Util;

import co.kozao.jcmsplugin.squashtm.model.squashimport.ImportSquashTmData;
import co.kozao.jcmsplugin.squashtm.model.mapping.TemplateAnalysis;
import co.kozao.jcmsplugin.squashtm.model.mapping.TemplateColumnMapping;
import co.kozao.jcmsplugin.squashtm.model.mapping.TemplateField;
import co.kozao.jcmsplugin.squashtm.model.mapping.TemplateMapping;
import co.kozao.jcmsplugin.squashtm.service.ExcelSquashTmImportService;
import co.kozao.jcmsplugin.squashtm.service.mapping.ExcelTemplateInspector;
import co.kozao.jcmsplugin.squashtm.service.mapping.SquashTmTemplateMappingManager;
import co.kozao.jcmsplugin.squashtm.util.SquashTmConstants;
import co.kozao.jcmsplugin.squashtm.util.SquashTmUtils;

/**
 * @author Severin Kengne
 */
public class SquashTmAdminHandler extends JcmsFormHandler {

	private static final Logger LOGGER = Logger.getLogger(SquashTmAdminHandler.class);

	private final SquashTmTemplateMappingManager mappingManager = SquashTmTemplateMappingManager.getInstance();
	private final ExcelTemplateInspector inspector = new ExcelTemplateInspector();

	private boolean opAnalyzeTemplate;
	private boolean opRefreshTemplate;
	private boolean opSaveMapping;
	private boolean opCancelTemplate;
	private boolean opResetMapping;

	private String templateToken;
	private String headerRow;
	private String firstDataRow;
	private String ignoredSheets;
	private String defaultSubModuleName;
	private String defaultViewName;
	private String defaultFeatureName;
	private String[] columnFields;

	private static final long MAX_TEMPLATE_SIZE = 10L * 1024L * 1024L;

	private static final int MAX_NAME_LENGTH = 255;

	private static final int MAX_IGNORED_SHEETS_LENGTH = 1000;

	private static final int MAX_ROW = 1048576;

	private TemplateAnalysis analysis;
	private final List<String> errors = new ArrayList<>();
	private final List<String> warnings = new ArrayList<>();
	private String successMessage;
	private final Map<Integer, String> selections = new HashMap<>();

	public void setOpAnalyzeTemplate(boolean opAnalyzeTemplate) {
		this.opAnalyzeTemplate = opAnalyzeTemplate;
	}

	public boolean isOpAnalyzeTemplate() {
		return opAnalyzeTemplate;
	}

	public void setOpRefreshTemplate(boolean opRefreshTemplate) {
		this.opRefreshTemplate = opRefreshTemplate;
	}

	public boolean isOpRefreshTemplate() {
		return opRefreshTemplate;
	}

	public void setOpSaveMapping(boolean opSaveMapping) {
		this.opSaveMapping = opSaveMapping;
	}

	public boolean isOpSaveMapping() {
		return opSaveMapping;
	}

	public void setOpCancelTemplate(boolean opCancelTemplate) {
		this.opCancelTemplate = opCancelTemplate;
	}

	public boolean isOpCancelTemplate() {
		return opCancelTemplate;
	}

	public void setOpResetMapping(boolean opResetMapping) {
		this.opResetMapping = opResetMapping;
	}

	public boolean isOpResetMapping() {
		return opResetMapping;
	}

	public String getTemplateToken() {
		return templateToken;
	}

	public void setTemplateToken(String templateToken) {
		this.templateToken = templateToken;
	}

	public String getHeaderRow() {
		return headerRow;
	}

	public void setHeaderRow(String headerRow) {
		this.headerRow = headerRow;
	}

	public String getFirstDataRow() {
		return firstDataRow;
	}

	public void setFirstDataRow(String firstDataRow) {
		this.firstDataRow = firstDataRow;
	}

	public String getIgnoredSheets() {
		return ignoredSheets;
	}

	public void setIgnoredSheets(String ignoredSheets) {
		this.ignoredSheets = ignoredSheets;
	}

	public String getDefaultSubModuleName() {
		return defaultSubModuleName;
	}

	public void setDefaultSubModuleName(String defaultSubModuleName) {
		this.defaultSubModuleName = defaultSubModuleName;
	}

	public String getDefaultViewName() {
		return defaultViewName;
	}

	public void setDefaultViewName(String defaultViewName) {
		this.defaultViewName = defaultViewName;
	}

	public String getDefaultFeatureName() {
		return defaultFeatureName;
	}

	public void setDefaultFeatureName(String defaultFeatureName) {
		this.defaultFeatureName = defaultFeatureName;
	}

	public String[] getColumnFields() {
		return columnFields;
	}

	public void setColumnFields(String[] columnFields) {
		this.columnFields = columnFields;
	}

	public TemplateAnalysis getAnalysis() {
		return analysis;
	}

	public List<String> getErrors() {
		return errors;
	}

	public List<String> getWarnings() {
		return warnings;
	}

	public String getSuccessMessage() {
		return successMessage;
	}

	public String getSelectedField(TemplateAnalysis.Column column) {

		if (column == null) {
			return "";
		}

		if (selections.containsKey(column.getIndex())) {
			return selections.get(column.getIndex());
		}

		return column.getSuggestedFieldName();
	}

	public TemplateMapping getActiveMapping() {
		return mappingManager.getActiveMapping();
	}

	public boolean isCustomTemplateActive() {
		return mappingManager.isCustomMappingActive();
	}


	@Override
	public boolean processAction() throws IOException {

		if (!opAnalyzeTemplate && !opRefreshTemplate && !opSaveMapping && !opCancelTemplate && !opResetMapping) {
			return false;
		}

		if (!SquashTmUtils.canAdministrationView()) {
			errors.add("Accès refusé : seuls les administrateurs peuvent modifier le template.");
			return false;
		}

		try {

			if (opResetMapping) {

				resetMapping();

			} else if (opCancelTemplate) {

				cancelTemplate();

			} else if (opAnalyzeTemplate) {

				analyzeUploadedTemplate();

			} else if (opRefreshTemplate) {

				refreshTemplate();

			} else if (opSaveMapping) {

				saveMapping();
			}

		} catch (IOException | RuntimeException e) {

			LOGGER.error("[TEMPLATE][ERREUR] Erreur pendant le traitement.", e);

			errors.add(e.getMessage() != null ? e.getMessage()
					: "Une erreur technique est survenue : " + e.getClass().getSimpleName());

			restoreAnalysisIfNeeded();
		}

		return false;
	}

	
	private void restoreAnalysisIfNeeded() {

		if (analysis != null || opCancelTemplate || opResetMapping || Util.isEmpty(templateToken)) {
			return;
		}

		try {

			File pendingFile = mappingManager.getPendingTemplateFile(templateToken);

			if (pendingFile != null) {
				analysis = inspector.inspect(pendingFile, -1, 0);
			}

		} catch (IOException | RuntimeException e) {
			LOGGER.warn("[TEMPLATE] Impossible de restaurer l'aperçu des colonnes.", e);
		}
	}

	/**
	 * Étape 1 : réception du template et détection des colonnes.
	 */
	private void analyzeUploadedTemplate() throws IOException {

		List<DocUploadInfo> uploadedFiles = getUploadedFiles("templateFile");

		if (!checkUpload(uploadedFiles, errors)) {
			return;
		}

		File uploadedFile = uploadedFiles.get(0).getFile();

		TemplateAnalysis detected = inspector.inspect(uploadedFile, -1, 0);

		mappingManager.discardPendingTemplate(templateToken);

		templateToken = mappingManager.storePendingTemplate(uploadedFile);

		analysis = detected;

		clearPostedValues();

		LOGGER.info("[TEMPLATE] Template analysé : " + detected.getColumns().size() + " colonne(s) détectée(s).");
	}

	
	private void refreshTemplate() throws IOException {

		File pendingFile = getPendingFileOrError();

		if (pendingFile == null) {
			return;
		}

		int requestedHeaderRow = checkRow(headerRow, -1, 0, "La ligne d'en-tête", errors);
		int requestedFirstDataRow = checkRow(firstDataRow, 0, 1, "La première ligne de données", errors);

		if (!errors.isEmpty()) {
			return;
		}

		analysis = inspector.inspect(pendingFile, requestedHeaderRow, requestedFirstDataRow);

		clearPostedValues();
	}

	/**
	 * Étape 3 : enregistrement du mapping et du template.
	 */
	private void saveMapping() throws IOException {

		File pendingFile = getPendingFileOrError();

		if (pendingFile == null) {
			return;
		}

		int requestedHeaderRow = checkRow(headerRow, -1, 0, "La ligne d'en-tête", errors);
		int requestedFirstDataRow = checkRow(firstDataRow, 0, 1, "La première ligne de données", errors);

		if (!errors.isEmpty()) {
			return;
		}

		checkIgnoredSheets(ignoredSheets, errors);
		checkName(defaultSubModuleName, "Le nom par défaut du sous-module", errors);
		checkName(defaultViewName, "Le nom par défaut de la vue", errors);
		checkName(defaultFeatureName, "Le nom par défaut de la fonctionnalité", errors);

		analysis = inspector.inspect(pendingFile, requestedHeaderRow, requestedFirstDataRow);

		TemplateMapping newMapping = new TemplateMapping();

		newMapping.setHeaderRow(analysis.getHeaderRow());
		newMapping.setFirstDataRow(analysis.getFirstDataRow());
		newMapping.setIgnoredSheetsFromText(ignoredSheets);
		newMapping.setDefaultSubModuleName(trim(defaultSubModuleName));
		newMapping.setDefaultViewName(trim(defaultViewName));
		newMapping.setDefaultFeatureName(trim(defaultFeatureName));

		selections.clear();
		selections.putAll(checkSelections(analysis, columnFields, errors));

		for (TemplateAnalysis.Column column : analysis.getColumns()) {

			TemplateField field = TemplateField.fromName(selections.get(column.getIndex()));

			if (field != null && newMapping.getColumn(field) == null) {
				newMapping.setColumn(new TemplateColumnMapping(field, column.getIndex(), column.getHeader()));
			}
		}

		checkIntegrity(newMapping, errors);

		if (!errors.isEmpty()) {
			return;
		}

		ExcelSquashTmImportService checkService = new ExcelSquashTmImportService(newMapping);

		ImportSquashTmData checkData = checkService.parse(pendingFile, "Contrôle du template");

		warnings.addAll(checkService.getInconsistencies());

		Member member = Channel.getChannel().getCurrentLoggedMember();

		mappingManager.saveMapping(newMapping, pendingFile, member != null ? member.getLogin() : "");

		mappingManager.discardPendingTemplate(templateToken);

		templateToken = null;
		analysis = null;
		selections.clear();
		clearPostedValues();

		successMessage = "Le template et son mapping ont été enregistrés. Contrôle effectué sur le fichier : "
				+ checkService.getModuleCount() + " onglet(s), " + checkService.getRequirementCount() + " exigence(s), "
				+ checkService.getTestCaseCount() + " cas de test et " + checkService.getTestStepCount()
				+ " étape(s) lus"
				+ (checkData != null && checkData.getModules().isEmpty()
						? " (le fichier ne contient pas de données d'exemple)"
						: "")
				+ ".";
	}

	private void cancelTemplate() {

		mappingManager.discardPendingTemplate(templateToken);

		templateToken = null;
		analysis = null;
		selections.clear();
		clearPostedValues();
	}

	private void resetMapping() throws IOException {

		mappingManager.discardPendingTemplate(templateToken);

		templateToken = null;
		analysis = null;
		selections.clear();
		clearPostedValues();

		mappingManager.resetToDefault();

		successMessage = "Le template par défaut est de nouveau utilisé.";
	}


	private File getPendingFileOrError() {

		File pendingFile = mappingManager.getPendingTemplateFile(templateToken);

		if (pendingFile == null) {
			templateToken = null;
			analysis = null;
			errors.add(
					"Le fichier téléversé n'est plus disponible (délai de 30 minutes dépassé). Téléversez-le de nouveau.");
		}

		return pendingFile;
	}


	/**
	 * Contrôle le fichier téléversé : présence, extension, taille.
	 */
	private boolean checkUpload(List<DocUploadInfo> uploadedFiles, List<String> errors) {

		if (uploadedFiles == null || uploadedFiles.isEmpty() || uploadedFiles.get(0) == null) {
			errors.add("Aucun fichier n'a été reçu. Sélectionnez un template Excel.");
			return false;
		}

		DocUploadInfo info = uploadedFiles.get(0);
		File file = info.getFile();

		if (file == null || !file.isFile()) {
			errors.add("Le fichier téléversé est introuvable.");
			return false;
		}

		if (file.length() > 0 && !isExcelContent(file)) {
			errors.add("Le fichier doit être un classeur Excel (.xlsx ou .xls).");
			return false;
		}

		if (file.length() == 0) {
			errors.add("Le fichier téléversé est vide.");
			return false;
		}

		if (file.length() > MAX_TEMPLATE_SIZE) {
			errors.add("Le fichier dépasse la taille maximale autorisée (" + (MAX_TEMPLATE_SIZE / (1024 * 1024))
					+ " Mo).");
			return false;
		}

		return true;
	}

	/**
	 * Convertit un numéro de ligne saisi.
	 *
	 * @param value        saisie de l'administrateur
	 * @param valueIfEmpty valeur retournée si la saisie est vide
	 * @param label        nom du champ, utilisé dans le message d'erreur
	 * @param minimum      plus petite valeur acceptée
	 * @return le numéro, ou valueIfEmpty ; en cas d'erreur, valueIfEmpty et un
	 *         message est ajouté
	 */
	private int checkRow(String value, int valueIfEmpty, int minimum, String label, List<String> errors) {

		if (value == null || value.trim().isEmpty()) {
			return valueIfEmpty;
		}

		int row;

		try {
			row = Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			errors.add(label + " doit être un nombre entier : " + SquashTmUtils.abbreviate(value.trim(), SquashTmConstants.MAX_NAME_PREVIEW_LENGTH));
			return valueIfEmpty;
		}

		if (row < minimum) {
			errors.add(label + " doit être supérieure ou égale à " + minimum + ".");
			return valueIfEmpty;
		}

		if (row > MAX_ROW) {
			errors.add(label + " ne peut pas dépasser " + MAX_ROW + ".");
			return valueIfEmpty;
		}

		return row;
	}

	
	private boolean checkName(String value, String label, List<String> errors) {

		if (value == null) {
			return true;
		}

		String trimmed = value.trim();

		if (trimmed.length() > MAX_NAME_LENGTH) {
			errors.add(label + " ne peut pas dépasser " + MAX_NAME_LENGTH + " caractères.");
			return false;
		}

		if (hasControlCharacter(trimmed)) {
			errors.add(label + " contient des caractères non autorisés.");
			return false;
		}

		return true;
	}

	
	private boolean checkIgnoredSheets(String value, List<String> errors) {

		if (value == null || value.trim().isEmpty()) {
			return true;
		}

		if (value.length() > MAX_IGNORED_SHEETS_LENGTH) {
			errors.add("La liste des onglets ignorés est trop longue.");
			return false;
		}

		if (hasControlCharacter(value)) {
			errors.add("La liste des onglets ignorés contient des caractères non autorisés.");
			return false;
		}

		return true;
	}

	/**
	 * Contrôle les rôles choisis pour les colonnes et retourne, pour chaque colonne
	 * de l'analyse, le rôle choisi (chaîne vide si la colonne n'est pas utilisée).
	 *
	 * Contrôles : le nombre de valeurs reçues, la validité de chaque rôle, un rôle
	 * choisi une seule fois, les rôles obligatoires présents. Les rôles
	 * obligatoires manquants sont aussi détectés par TemplateMapping.validate().
	 */
	private Map<Integer, String> checkSelections(TemplateAnalysis analysis, String[] columnFields,
			List<String> errors) {

		Map<Integer, String> selections = new HashMap<>();
		Map<String, String> firstColumnByField = new HashMap<>();

		if (columnFields != null && columnFields.length > analysis.getColumns().size()) {
			errors.add("Le formulaire contient plus de colonnes que le template analysé. Actualisez la page.");
		}

		for (TemplateAnalysis.Column column : analysis.getColumns()) {

			String selected = "";

			if (columnFields != null && column.getIndex() < columnFields.length
					&& columnFields[column.getIndex()] != null) {
				selected = columnFields[column.getIndex()].trim();
			}

			selections.put(column.getIndex(), selected);

			if (selected.isEmpty()) {
				continue;
			}

			TemplateField field = TemplateField.fromName(selected);

			if (field == null) {
				errors.add("Le rôle choisi pour la colonne " + column.getLetter() + " est inconnu : "
						+ SquashTmUtils.abbreviate(selected.trim(), SquashTmConstants.MAX_NAME_PREVIEW_LENGTH));
				continue;
			}

			String firstColumn = firstColumnByField.putIfAbsent(field.name(), column.getLetter());

			if (firstColumn != null) {
				errors.add("« " + field.getDefaultLabel() + " » est choisi pour plusieurs colonnes (" + firstColumn
						+ " et " + column.getLetter() + ").");
			}
		}

		return selections;
	}

	
	private boolean checkIntegrity(TemplateMapping mapping, List<String> errors) {

		List<String> mappingErrors = mapping.validate();

		for (String mappingError : mappingErrors) {
			if (!errors.contains(mappingError)) {
				errors.add(mappingError);
			}
		}

		return mappingErrors.isEmpty();
	}

	
	private boolean isExcelContent(File file) {

		byte[] header = new byte[4];

		try (java.io.InputStream in = new java.io.FileInputStream(file)) {

			if (in.read(header) < 4) {
				return false;
			}

		} catch (java.io.IOException e) {
			return false;
		}

		boolean xlsx = header[0] == 'P' && header[1] == 'K';
		boolean xls = (header[0] & 0xFF) == 0xD0 && (header[1] & 0xFF) == 0xCF && (header[2] & 0xFF) == 0x11
				&& (header[3] & 0xFF) == 0xE0;

		return xlsx || xls;
	}

	private boolean hasControlCharacter(String value) {

		for (int i = 0; i < value.length(); i++) {

			char c = value.charAt(i);

			if (Character.isISOControl(c)) {
				return true;
			}
		}

		return false;
	}

	
	private void clearPostedValues() {

		headerRow = null;
		firstDataRow = null;
		ignoredSheets = null;
		defaultSubModuleName = null;
		defaultViewName = null;
		defaultFeatureName = null;
		columnFields = null;
	}

	private String trim(String value) {
		return value == null ? "" : value.trim();
	}
}