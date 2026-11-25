package co.kozao.jcmsplugin.squashtm.service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jalios.jcms.Channel;

import co.kozao.jcmsplugin.squashtm.model.mapping.TemplateColumnMapping;
import co.kozao.jcmsplugin.squashtm.model.mapping.TemplateField;
import co.kozao.jcmsplugin.squashtm.model.mapping.TemplateMapping;
import co.kozao.jcmsplugin.squashtm.service.mapping.SquashTmTemplateMappingManager;
import co.kozao.jcmsplugin.squashtm.util.SquashTmConstants;
import co.kozao.jcmsplugin.squashtm.util.SquashTmUtils;

/**
 * @author Severin Kengne
 */
public class SquashTmExportService {

	private static final Logger LOGGER = Logger.getLogger(SquashTmExportService.class);

	private static final int PAGE_SIZE = 100;

	
	private static final int FEATURE_DEPTH = 4;

	private static final int MAX_SHEET_NAME_LENGTH = 31;
	private static final int MAX_CELL_LENGTH = 32000;

	private static final TemplateField[] RUN_LEVELS = { TemplateField.SUBMODULE, TemplateField.VIEW,
			TemplateField.FEATURE, TemplateField.ELEMENT };

	private final SquashTmService squashTmService;

	
	public SquashTmExportService() {
		this.squashTmService = new SquashTmService();
	}

	public SquashTmExportService(String authorizationHeader) {
		this.squashTmService = new SquashTmService(authorizationHeader);
	}

	
	public static String buildFileName(String projectName) {

		String base = SquashTmUtils.isBlank(projectName) ? "projet" : projectName.trim().replaceAll("[^\\p{L}\\p{N}._-]+", "_");

		String stamp = new SimpleDateFormat(SquashTmConstants.DATE_FORMAT_FILE_STAMP).format(new Date());

		return "SquashTM_" + base + "_" + stamp + ".xlsx";
	}

	/**
	 * Génère le fichier Excel de la version à jour d'un projet Squash TM.
	 *
	 * @throws IOException si le projet est introuvable ou si Squash TM est inaccessible
	 */
	public byte[] exportProject(String projectName) throws IOException {

		if (SquashTmUtils.isBlank(projectName)) {
			throw new IOException("Le nom du projet est obligatoire.");
		}

		String cleanProjectName = projectName.trim();

		LOGGER.info("[EXPORT][DEBUT] Projet : " + cleanProjectName);

		JsonObject project = squashTmService.findProjectByName(cleanProjectName);

		if (project == null) {
			throw new IOException("Le projet '" + cleanProjectName + "' est introuvable dans Squash TM.");
		}

		if (!project.has("id") || project.get("id").isJsonNull()) {
			throw new IOException("L'identifiant du projet Squash TM est absent.");
		}

		long projectId = project.get("id").getAsLong();

		Node root = new Node("");

		
		collectTestCases(squashTmService.getTestCaseFolderTree(projectId), root);

		
		collectRequirements(squashTmService.getRequirementFolderTree(projectId), root);

		if (root.children.isEmpty()) {
			throw new IOException("Aucun module n'a été trouvé dans le projet '" + cleanProjectName + "' sur Squash TM.");
		}

		byte[] content = buildWorkbook(root, cleanProjectName);

		LOGGER.info("[EXPORT][FIN] Projet : " + cleanProjectName + " (" + content.length + " octets)");

		return content;
	}


	private void collectTestCases(JsonElement tree, Node root) throws IOException {

		Map<Long, List<String>> folders = new LinkedHashMap<>();

		collectFolders(tree, new ArrayList<String>(), folders);

		for (Map.Entry<Long, List<String>> entry : folders.entrySet()) {

			List<String> segments = entry.getValue();

			Node node = ensureNode(root, segments);

			if (node == null) {
				LOGGER.warn("[EXPORT] Dossier de cas de test ignoré (profondeur non gérée) : " + segments);
				continue;
			}

			
			if (segments.size() != FEATURE_DEPTH) {
				continue;
			}

			for (JsonObject content : loadFolderContent(entry.getKey(), false)) {

				String type = getString(content, "_type");

				if (type.endsWith("-folder")) {
					continue;
				}

				long id = content.has("id") && !content.get("id").isJsonNull() ? content.get("id").getAsLong() : 0L;

				String name = getString(content, "name").trim();

				if (name.isEmpty() && id > 0) {
					name = getString(squashTmService.getTestCase(id), "name").trim();
				}

				if (name.isEmpty()) {
					continue;
				}

				Element element = node.elements.get(name);

				if (element == null) {
					element = new Element(name);
					node.elements.put(name, element);
				}

				
				if ("test-case".equals(type) && id > 0) {
					readSteps(id, element);
				}
			}
		}
	}

	private void collectRequirements(JsonElement tree, Node root) throws IOException {

		Map<Long, List<String>> folders = new LinkedHashMap<>();

		collectFolders(tree, new ArrayList<String>(), folders);

		for (Map.Entry<Long, List<String>> entry : folders.entrySet()) {

			List<String> segments = entry.getValue();

			Node node = ensureNode(root, segments);

			if (node == null) {
				LOGGER.warn("[EXPORT] Dossier d'exigences ignoré (profondeur non gérée) : " + segments);
				continue;
			}

			if (segments.size() != FEATURE_DEPTH) {
				continue;
			}

			for (JsonObject content : loadFolderContent(entry.getKey(), true)) {

				String type = getString(content, "_type");

				if (type.endsWith("-folder")) {
					continue;
				}

				long id = content.has("id") && !content.get("id").isJsonNull() ? content.get("id").getAsLong() : 0L;

				String name = getString(content, "name").trim();

				if (name.isEmpty() && id > 0) {
					name = getString(squashTmService.getRequirement(id), "name").trim();
				}

				if (!name.isEmpty() && !node.elements.containsKey(name)) {
					node.elements.put(name, new Element(name));
				}
			}
		}
	}

	private void readSteps(long testCaseId, Element element) throws IOException {

		JsonArray steps = squashTmService.getTestCaseSteps(testCaseId);

		for (JsonElement stepElement : steps) {

			if (stepElement == null || !stepElement.isJsonObject()) {
				continue;
			}

			JsonObject step = stepElement.getAsJsonObject();

			String type = getString(step, "_type");

			if (!type.isEmpty() && !"action-step".equals(type)) {
				continue;
			}

			String action = SquashTmUtils.htmlToText(getString(step, "action"));
			String expectedResult = SquashTmUtils.htmlToText(getString(step, "expected_result"));

			if (action.isEmpty() && expectedResult.isEmpty()) {
				continue;
			}

			element.steps.add(new String[] { action, expectedResult });
		}
	}

	private List<JsonObject> loadFolderContent(long folderId, boolean requirementFolder) throws IOException {

		List<JsonObject> result = new ArrayList<>();

		int page = 0;
		int totalPages = 1;

		do {

			JsonObject response = requirementFolder
					? squashTmService.getRequirementFolderContent(folderId, page, PAGE_SIZE)
					: squashTmService.getTestCaseFolderContent(folderId, page, PAGE_SIZE);

			if (response == null) {
				throw new IOException("Réponse vide pour le dossier Squash TM " + folderId + ".");
			}

			
			if (response.has("_embedded") && response.get("_embedded").isJsonObject()) {

				JsonObject embedded = response.getAsJsonObject("_embedded");

				if (embedded.has("content") && embedded.get("content").isJsonArray()) {

					for (JsonElement element : embedded.getAsJsonArray("content")) {

						if (element != null && element.isJsonObject()) {
							result.add(element.getAsJsonObject());
						}
					}
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

		return result;
	}

	/**
	 * Parcourt l'arbre de dossiers renvoyé par Squash TM (sous-dossiers dans
	 * "folders" pour le projet et "children" pour un dossier).
	 */
	private void collectFolders(JsonElement element, List<String> parentSegments, Map<Long, List<String>> folders) {

		if (element == null || element.isJsonNull()) {
			return;
		}

		if (element.isJsonArray()) {

			for (JsonElement child : element.getAsJsonArray()) {
				collectFolders(child, parentSegments, folders);
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

				List<String> currentSegments = new ArrayList<>(parentSegments);
				currentSegments.add(node.get("name").getAsString().trim());

				if (!folders.containsKey(folderId)) {
					folders.put(folderId, currentSegments);
				}

				collectFolders(node, currentSegments, folders);
			}
		}
	}

	private Node ensureNode(Node root, List<String> segments) {

		if (segments.isEmpty() || segments.size() > FEATURE_DEPTH) {
			return null;
		}

		Node current = root;

		for (String segment : segments) {

			Node child = current.children.get(segment);

			if (child == null) {
				child = new Node(segment);
				current.children.put(segment, child);
			}

			current = child;
		}

		return current;
	}


	private byte[] buildWorkbook(Node root, String projectName) throws IOException {

		
		TemplateMapping mapping = SquashTmTemplateMappingManager.getInstance().getActiveMapping();

		int headerIndex = Math.max(0, mapping.getHeaderRow() - 1);
		int firstDataIndex = Math.max(headerIndex + 1, mapping.getFirstDataRow() - 1);

		try (Workbook workbook = openModelWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {

			Font boldFont = workbook.createFont();
			boldFont.setBold(true);

			CellStyle headerStyle = workbook.createCellStyle();
			headerStyle.setFont(boldFont);
			headerStyle.setWrapText(true);

			CellStyle textStyle = workbook.createCellStyle();
			textStyle.setWrapText(true);

			
			List<String> templateOrder = new ArrayList<>();

			for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
				templateOrder.add(workbook.getSheetName(i).trim().toLowerCase());
			}

			
			int modelIndex = -1;

			for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
				if (!mapping.isSheetIgnored(workbook.getSheetName(i))) {
					modelIndex = i;
					break;
				}
			}

			Sheet modelSheet;

			if (modelIndex < 0) {
				modelSheet = createFallbackModelSheet(workbook, mapping, headerIndex, headerStyle);
			} else {
				modelSheet = workbook.getSheetAt(modelIndex);
			}

			
			for (int i = workbook.getNumberOfSheets() - 1; i >= 0; i--) {

				Sheet sheet = workbook.getSheetAt(i);

				if (sheet != modelSheet && !mapping.isSheetIgnored(sheet.getSheetName())) {
					workbook.removeSheetAt(i);
				}
			}

			workbook.setSheetName(workbook.getSheetIndex(modelSheet), SquashTmConstants.MODEL_SHEET_NAME);

			
			Set<String> usedNames = new HashSet<>();
			List<String> sheetsAfterModel = new ArrayList<>();
			boolean afterModel = false;

			for (int i = 0; i < workbook.getNumberOfSheets(); i++) {

				Sheet sheet = workbook.getSheetAt(i);

				if (sheet == modelSheet) {
					afterModel = true;
					continue;
				}

				usedNames.add(sheet.getSheetName().toLowerCase());

				if (afterModel) {
					sheetsAfterModel.add(sheet.getSheetName());
				}
			}

			
			if (modelSheet.getRow(headerIndex) == null) {
				createFallbackHeader(modelSheet, mapping, headerIndex, headerStyle);
			}

			Map<TemplateField, Integer> columns = resolveColumns(modelSheet, mapping, headerIndex);

			
			Map<TemplateField, CellStyle> columnStyles = readColumnStyles(modelSheet, columns, firstDataIndex);

			
			clearDataRows(modelSheet, firstDataIndex);

			String title = "Export Squash TM - Projet : " + projectName + " - "
					+ new SimpleDateFormat(SquashTmConstants.DATE_FORMAT_DISPLAY).format(new Date());

			writeTitle(modelSheet, headerIndex, title, headerStyle);

			List<Node> modules = new ArrayList<>(root.children.values());

			Collections.sort(modules, moduleComparator(templateOrder));

			int modelPosition = workbook.getSheetIndex(modelSheet);

			for (Node module : modules) {

				Sheet sheet = workbook.cloneSheet(modelPosition);

				workbook.setSheetName(workbook.getSheetIndex(sheet), uniqueSheetName(module.name, usedNames));

				writeSheet(sheet, module, columns, columnStyles, textStyle, firstDataIndex);
			}

			workbook.removeSheetAt(workbook.getSheetIndex(modelSheet));

			
			for (String name : sheetsAfterModel) {
				workbook.setSheetOrder(name, workbook.getNumberOfSheets() - 1);
			}

			workbook.write(output);

			return output.toByteArray();
		}
	}

	/**
	 * Ouvre le modèle actif : le modèle personnalisé s'il existe, sinon le modèle
	 * historique du plugin. En dernier recours, un classeur vide est utilisé.
	 */
	private Workbook openModelWorkbook() {

		File templateFile = SquashTmTemplateMappingManager.getInstance().getCustomTemplateFile();

		if (templateFile == null) {

			String path = Channel.getChannel().getRealPath(SquashTmConstants.DEFAULT_TEMPLATE_PATH);

			if (!SquashTmUtils.isBlank(path) && new File(path).isFile()) {
				templateFile = new File(path);
			}
		}

		if (templateFile == null) {
			LOGGER.warn("[EXPORT] Modèle introuvable : export avec un classeur vierge.");
			return new XSSFWorkbook();
		}

		try (InputStream input = new FileInputStream(templateFile)) {

			return WorkbookFactory.create(input);

		} catch (IOException | RuntimeException e) {

			LOGGER.warn("[EXPORT] Modèle illisible (" + templateFile.getAbsolutePath()
					+ ") : export avec un classeur vierge.", e);

			return new XSSFWorkbook();
		}
	}

	private Sheet createFallbackModelSheet(Workbook workbook, TemplateMapping mapping, int headerIndex,
			CellStyle headerStyle) {

		Sheet sheet = workbook.createSheet(SquashTmConstants.MODEL_SHEET_NAME);

		createFallbackHeader(sheet, mapping, headerIndex, headerStyle);

		return sheet;
	}

	private void createFallbackHeader(Sheet sheet, TemplateMapping mapping, int headerIndex, CellStyle headerStyle) {

		Row headerRow = sheet.createRow(headerIndex);

		for (TemplateColumnMapping column : mapping.getColumns()) {

			TemplateField field = column.getTemplateField();

			if (field == null || column.getColumnIndex() < 0) {
				continue;
			}

			String label = SquashTmUtils.isBlank(column.getHeaderLabel()) ? field.getDefaultLabel() : column.getHeaderLabel();

			setCell(headerRow, column.getColumnIndex(), label, headerStyle);

			sheet.setColumnWidth(column.getColumnIndex(), 40 * 256);
		}
	}

	/**
	 * Trouve la colonne de chaque rôle dans l'onglet type, comme le fait l'import :
	 * d'abord par le libellé de l'en-tête, sinon par l'index enregistré dans le mapping.
	 */
	private Map<TemplateField, Integer> resolveColumns(Sheet sheet, TemplateMapping mapping, int headerIndex) {

		Map<TemplateField, Integer> columns = new EnumMap<>(TemplateField.class);

		Map<String, Integer> headerIndexes = new HashMap<>();
		DataFormatter formatter = new DataFormatter();

		Row headerRow = sheet.getRow(headerIndex);

		if (headerRow != null) {

			for (int index = 0; index < Math.max(headerRow.getLastCellNum(), 0); index++) {

				Cell cell = headerRow.getCell(index);

				String normalized = cell == null ? "" : TemplateField.normalize(formatter.formatCellValue(cell));

				if (!normalized.isEmpty() && !headerIndexes.containsKey(normalized)) {
					headerIndexes.put(normalized, index);
				}
			}
		}

		Set<Integer> usedColumns = new HashSet<>();

		for (TemplateColumnMapping column : mapping.getColumns()) {

			TemplateField field = column.getTemplateField();

			if (field == null) {
				continue;
			}

			String expectedLabel = TemplateField.normalize(column.getHeaderLabel());

			Integer index = expectedLabel.isEmpty() ? null : headerIndexes.get(expectedLabel);

			if (index == null && column.getColumnIndex() >= 0) {
				index = column.getColumnIndex();
			}

			if (index != null && usedColumns.add(index)) {
				columns.put(field, index);
			}
		}

		return columns;
	}

	private Map<TemplateField, CellStyle> readColumnStyles(Sheet sheet, Map<TemplateField, Integer> columns,
			int firstDataIndex) {

		Map<TemplateField, CellStyle> styles = new EnumMap<>(TemplateField.class);

		Row row = sheet.getRow(firstDataIndex);

		if (row == null) {
			return styles;
		}

		for (Map.Entry<TemplateField, Integer> entry : columns.entrySet()) {

			Cell cell = row.getCell(entry.getValue());

			if (cell != null && cell.getCellStyle() != null) {
				styles.put(entry.getKey(), cell.getCellStyle());
			}
		}

		return styles;
	}

	
	private void clearDataRows(Sheet sheet, int firstDataIndex) {

		for (int i = sheet.getNumMergedRegions() - 1; i >= 0; i--) {
			if (sheet.getMergedRegion(i).getLastRow() >= firstDataIndex) {
				sheet.removeMergedRegion(i);
			}
		}

		for (int rowIndex = sheet.getLastRowNum(); rowIndex >= firstDataIndex; rowIndex--) {

			Row row = sheet.getRow(rowIndex);

			if (row != null) {
				sheet.removeRow(row);
			}
		}
	}

	
	private void writeTitle(Sheet sheet, int headerIndex, String title, CellStyle style) {

		DataFormatter formatter = new DataFormatter();

		for (int rowIndex = 0; rowIndex < headerIndex; rowIndex++) {

			Row row = sheet.getRow(rowIndex);

			if (row == null) {
				row = sheet.createRow(rowIndex);
			}

			Cell cell = row.getCell(0);

			if (cell == null || formatter.formatCellValue(cell).trim().isEmpty()) {
				setCell(row, 0, title, style);
				return;
			}
		}
	}

	/**
	 * Ordre des onglets : d'abord les modules qui portent le nom d'un onglet du
	 * modèle (dans l'ordre du modèle), puis les autres par ordre alphabétique.
	 * L'ordre ne dépend donc plus de l'ordre renvoyé par l'API Squash TM.
	 */
	private Comparator<Node> moduleComparator(final List<String> templateOrder) {

		return new Comparator<Node>() {

			@Override
			public int compare(Node first, Node second) {

				int firstRank = templateOrder.indexOf(first.name.trim().toLowerCase());
				int secondRank = templateOrder.indexOf(second.name.trim().toLowerCase());

				if (firstRank < 0) {
					firstRank = Integer.MAX_VALUE;
				}

				if (secondRank < 0) {
					secondRank = Integer.MAX_VALUE;
				}

				if (firstRank != secondRank) {
					return firstRank < secondRank ? -1 : 1;
				}

				return String.CASE_INSENSITIVE_ORDER.compare(first.name, second.name);
			}
		};
	}

	private void writeSheet(Sheet sheet, Node module, Map<TemplateField, Integer> columns,
			Map<TemplateField, CellStyle> columnStyles, CellStyle defaultStyle, int firstDataIndex) {

		SheetWriter writer = new SheetWriter(sheet, columns, columnStyles, defaultStyle, firstDataIndex);

		for (Node subModule : module.children.values()) {

			writer.pendingSubModule = subModule.name;
			writer.pendingView = null;
			writer.pendingFeature = null;

			if (subModule.children.isEmpty()) {
				writer.writeRow(null, null, null);
				continue;
			}

			for (Node view : subModule.children.values()) {

				writer.pendingView = view.name;
				writer.pendingFeature = null;

				if (view.children.isEmpty()) {
					writer.writeRow(null, null, null);
					continue;
				}

				for (Node feature : view.children.values()) {

					writer.pendingFeature = feature.name;

					if (feature.elements.isEmpty()) {
						writer.writeRow(null, null, null);
						continue;
					}

					for (Element element : feature.elements.values()) {

						if (element.steps.isEmpty()) {
							writer.writeRow(element.name, null, null);
							continue;
						}

						boolean firstStep = true;

						for (String[] step : element.steps) {
							writer.writeRow(firstStep ? element.name : null, step[0], step[1]);
							firstStep = false;
						}
					}
				}
			}
		}

		writer.finish();
	}

	private String uniqueSheetName(String rawName, Set<String> usedNames) {

		String base = rawName == null ? "" : rawName.replaceAll("[\\\\/*?:\\[\\]]", "_").trim();

		base = base.replaceAll("^'+|'+$", "");

		if (base.isEmpty()) {
			base = "Module";
		}

		if (base.length() > MAX_SHEET_NAME_LENGTH) {
			base = base.substring(0, MAX_SHEET_NAME_LENGTH);
		}

		String candidate = base;
		int suffix = 2;

		while (!usedNames.add(candidate.toLowerCase())) {

			String suffixText = " (" + suffix++ + ")";
			int maxBaseLength = MAX_SHEET_NAME_LENGTH - suffixText.length();

			candidate = (base.length() > maxBaseLength ? base.substring(0, maxBaseLength) : base) + suffixText;
		}

		return candidate;
	}

	
	private static void setCell(Row row, int column, String value, CellStyle style) {

		if (SquashTmUtils.isBlank(value)) {
			return;
		}

		Cell cell = row.createCell(column);

		cell.setCellValue(value.length() > MAX_CELL_LENGTH ? value.substring(0, MAX_CELL_LENGTH) : value);
		cell.setCellStyle(style);
	}

	
	private static void setDataCell(Row row, int column, String value, CellStyle style) {

		Cell cell = row.createCell(column);

		if (!SquashTmUtils.isBlank(value)) {
			cell.setCellValue(value.length() > MAX_CELL_LENGTH ? value.substring(0, MAX_CELL_LENGTH) : value);
		}

		cell.setCellStyle(style);
	}


	private String getString(JsonObject object, String property) {

		if (object == null || !object.has(property) || object.get(property).isJsonNull()
				|| !object.get(property).isJsonPrimitive()) {
			return "";
		}

		return object.get(property).getAsString();
	}


	private static class Node {

		private final String name;
		private final Map<String, Node> children = new LinkedHashMap<>();
		private final Map<String, Element> elements = new LinkedHashMap<>();

		private Node(String name) {
			this.name = name;
		}
	}

	
	private static class Element {

		private final String name;
		private final List<String[]> steps = new ArrayList<>();

		private Element(String name) {
			this.name = name;
		}
	}

	/**
	 * Écrit les lignes d'un onglet. Sous-module, vue et fonctionnalité ne sont
	 * renseignés qu'à la première ligne où ils apparaissent, comme dans le modèle,
	 * et leurs cellules sont fusionnées sur toutes les lignes qu'ils couvrent.
	 */
	private static class SheetWriter {

		private final Sheet sheet;
		private final Map<TemplateField, Integer> columns;
		private final Map<TemplateField, CellStyle> columnStyles;
		private final CellStyle defaultStyle;
		private final Map<TemplateField, Integer> runStarts = new EnumMap<>(TemplateField.class);
		private int rowIndex;

		private String pendingSubModule;
		private String pendingView;
		private String pendingFeature;

		private SheetWriter(Sheet sheet, Map<TemplateField, Integer> columns,
				Map<TemplateField, CellStyle> columnStyles, CellStyle defaultStyle, int firstDataIndex) {
			this.sheet = sheet;
			this.columns = columns;
			this.columnStyles = columnStyles;
			this.defaultStyle = defaultStyle;
			this.rowIndex = firstDataIndex;
		}

		private void writeRow(String element, String action, String expectedResult) {

			int currentIndex = rowIndex++;

			Row row = sheet.createRow(currentIndex);

			if (pendingSubModule != null) {
				startRun(TemplateField.SUBMODULE, currentIndex);
			}

			if (pendingView != null) {
				startRun(TemplateField.VIEW, currentIndex);
			}

			if (pendingFeature != null) {
				startRun(TemplateField.FEATURE, currentIndex);
			}

			if (element != null) {
				startRun(TemplateField.ELEMENT, currentIndex);
			}

			writeCell(row, TemplateField.SUBMODULE, pendingSubModule);
			writeCell(row, TemplateField.VIEW, pendingView);
			writeCell(row, TemplateField.FEATURE, pendingFeature);
			writeCell(row, TemplateField.ELEMENT, element);
			writeCell(row, TemplateField.ACTION, action);
			writeCell(row, TemplateField.EXPECTED_RESULT, expectedResult);

			pendingSubModule = null;
			pendingView = null;
			pendingFeature = null;
		}

		
		private void finish() {
			closeRuns(TemplateField.SUBMODULE, rowIndex - 1);
		}

		private void writeCell(Row row, TemplateField field, String value) {

			Integer column = columns.get(field);

			if (column == null) {
				return;
			}

			CellStyle style = columnStyles.get(field);

			setDataCell(row, column, value, style != null ? style : defaultStyle);
		}

		
		private void startRun(TemplateField field, int startRow) {
			closeRuns(field, startRow - 1);
			runStarts.put(field, startRow);
		}

		private void closeRuns(TemplateField fromField, int endRow) {

			boolean reached = false;

			for (TemplateField level : RUN_LEVELS) {

				if (level == fromField) {
					reached = true;
				}

				if (!reached) {
					continue;
				}

				Integer start = runStarts.remove(level);
				Integer column = columns.get(level);

				if (start != null && column != null && endRow > start) {
					sheet.addMergedRegion(new CellRangeAddress(start, endRow, column, column));
				}
			}
		}
	}
}
