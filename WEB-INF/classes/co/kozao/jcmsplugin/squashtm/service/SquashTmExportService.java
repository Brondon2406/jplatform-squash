package co.kozao.jcmsplugin.squashtm.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Reconstruit, à partir de Squash TM, la version à jour des modules d'un projet
 * sous la forme d'un fichier Excel au format du gabarit d'import.
 *
 * Ce service ne crée, ne modifie et ne supprime aucune donnée dans Squash TM.
 */
public class SquashTmExportService {

	private static final Logger LOGGER = Logger.getLogger(SquashTmExportService.class);

	private static final int PAGE_SIZE = 100;

	/** Module / Sous-module / Vue / Fonctionnalité. */
	private static final int FEATURE_DEPTH = 4;

	private static final int MAX_SHEET_NAME_LENGTH = 31;
	private static final int MAX_CELL_LENGTH = 32000;

	/** Mêmes index de colonnes que ExcelSquashTmImportService. */
	private static final int COLUMN_SUBMODULE = 0;
	private static final int COLUMN_VIEW = 1;
	private static final int COLUMN_FEATURE = 2;
	private static final int COLUMN_ELEMENT = 3;
	private static final int COLUMN_ACTION = 4;
	private static final int COLUMN_EXPECTED_RESULT = 5;

	private static final int TITLE_ROW = 0;
	private static final int HEADER_ROW = 2;
	private static final int FIRST_DATA_ROW = 3;

	private final SquashTmService squashTmService;

	/** Utilisé dans une requête JCMS : l'authentification du membre connecté est utilisée. */
	public SquashTmExportService() {
		this.squashTmService = new SquashTmService();
	}

	public SquashTmExportService(String authorizationHeader) {
		this.squashTmService = new SquashTmService(authorizationHeader);
	}

	/** Exemple : SquashTM_MonProjet_20261111_1530.xlsx */
	public static String buildFileName(String projectName) {

		String base = isEmpty(projectName) ? "projet" : projectName.trim().replaceAll("[^\\p{L}\\p{N}._-]+", "_");

		String stamp = new SimpleDateFormat("yyyyMMdd_HHmm").format(new Date());

		return "SquashTM_" + base + "_" + stamp + ".xlsx";
	}

	/**
	 * Génère le fichier Excel de la version à jour d'un projet Squash TM.
	 *
	 * @throws IOException si le projet est introuvable ou si Squash TM est inaccessible
	 */
	public byte[] exportProject(String projectName) throws IOException {

		if (isEmpty(projectName)) {
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

		// Cas de test et étapes.
		collectTestCases(squashTmService.getTestCaseFolderTree(projectId), root);

		// Exigences (celles qui n'ont pas de cas de test portant le même nom).
		collectRequirements(squashTmService.getRequirementFolderTree(projectId), root);

		if (root.children.isEmpty()) {
			throw new IOException("Aucun module n'a été trouvé dans le projet '" + cleanProjectName + "' sur Squash TM.");
		}

		byte[] content = buildWorkbook(root, cleanProjectName);

		LOGGER.info("[EXPORT][FIN] Projet : " + cleanProjectName + " (" + content.length + " octets)");

		return content;
	}

	/*
	 * ==================== LECTURE DE SQUASH TM ====================
	 */

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

			// Seuls les dossiers de fonctionnalité contiennent des éléments.
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

				// Les étapes n'existent que pour les cas de test classiques.
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

			String action = htmlToText(getString(step, "action"));
			String expectedResult = htmlToText(getString(step, "expected_result"));

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

			// Un dossier vide est retourné sans "_embedded" : ce n'est pas une erreur.
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

	/*
	 * ==================== CONSTRUCTION DU FICHIER EXCEL ====================
	 */

	private byte[] buildWorkbook(Node root, String projectName) throws IOException {

		try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {

			Font boldFont = workbook.createFont();
			boldFont.setBold(true);

			CellStyle headerStyle = workbook.createCellStyle();
			headerStyle.setFont(boldFont);
			headerStyle.setWrapText(true);

			CellStyle textStyle = workbook.createCellStyle();
			textStyle.setWrapText(true);

			Set<String> usedNames = new HashSet<>();

			String title = "Export Squash TM - Projet : " + projectName + " - "
					+ new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date());

			for (Node module : root.children.values()) {

				Sheet sheet = workbook.createSheet(uniqueSheetName(module.name, usedNames));

				writeSheet(sheet, module, title, headerStyle, textStyle);
			}

			workbook.write(output);

			return output.toByteArray();
		}
	}

	private void writeSheet(Sheet sheet, Node module, String title, CellStyle headerStyle, CellStyle textStyle) {

		sheet.setColumnWidth(COLUMN_SUBMODULE, 22 * 256);
		sheet.setColumnWidth(COLUMN_VIEW, 26 * 256);
		sheet.setColumnWidth(COLUMN_FEATURE, 32 * 256);
		sheet.setColumnWidth(COLUMN_ELEMENT, 45 * 256);
		sheet.setColumnWidth(COLUMN_ACTION, 45 * 256);
		sheet.setColumnWidth(COLUMN_EXPECTED_RESULT, 45 * 256);

		// Les lignes 1 et 2 ne sont pas lues par l'import.
		Row titleRow = sheet.createRow(TITLE_ROW);
		setCell(titleRow, COLUMN_SUBMODULE, title, headerStyle);

		Row headerRow = sheet.createRow(HEADER_ROW);
		setCell(headerRow, COLUMN_SUBMODULE, "Sous-modules", headerStyle);
		setCell(headerRow, COLUMN_VIEW, "Vues", headerStyle);
		setCell(headerRow, COLUMN_FEATURE, "Les fonctionnalités", headerStyle);
		setCell(headerRow, COLUMN_ELEMENT, "Libellé de l'exigence et du Cas de test", headerStyle);
		setCell(headerRow, COLUMN_ACTION, "Actions", headerStyle);
		setCell(headerRow, COLUMN_EXPECTED_RESULT, "Résultats attendus", headerStyle);

		SheetWriter writer = new SheetWriter(sheet, textStyle);

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

		if (isEmpty(value)) {
			return;
		}

		Cell cell = row.createCell(column);

		cell.setCellValue(value.length() > MAX_CELL_LENGTH ? value.substring(0, MAX_CELL_LENGTH) : value);
		cell.setCellStyle(style);
	}

	/*
	 * ==================== OUTILS ====================
	 */

	/** Squash TM stocke du texte riche : le HTML est converti en texte simple. */
	private String htmlToText(String text) {

		if (text == null) {
			return "";
		}

		String value = text.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("(?i)</p>", "\n").replaceAll("<[^>]*>", "");

		value = value.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
				.replace("&#39;", "'").replace("&amp;", "&");

		value = value.replace('\u00A0', ' ').replaceAll("[ \\t]+", " ").replaceAll(" ?\\n ?", "\n")
				.replaceAll("\\n{3,}", "\n\n");

		return value.trim();
	}

	private String getString(JsonObject object, String property) {

		if (object == null || !object.has(property) || object.get(property).isJsonNull()
				|| !object.get(property).isJsonPrimitive()) {
			return "";
		}

		return object.get(property).getAsString();
	}

	private static boolean isEmpty(String value) {
		return value == null || value.trim().isEmpty();
	}

	/*
	 * ==================== STRUCTURES INTERNES ====================
	 */

	/** Noeud de la hiérarchie Module / Sous-module / Vue / Fonctionnalité. */
	private static class Node {

		private final String name;
		private final Map<String, Node> children = new LinkedHashMap<>();
		private final Map<String, Element> elements = new LinkedHashMap<>();

		private Node(String name) {
			this.name = name;
		}
	}

	/** Exigence / cas de test portant le même libellé, avec ses étapes. */
	private static class Element {

		private final String name;
		private final List<String[]> steps = new ArrayList<>();

		private Element(String name) {
			this.name = name;
		}
	}

	/**
	 * Écrit les lignes d'un onglet. Sous-module, vue et fonctionnalité ne sont
	 * renseignés qu'à la première ligne où ils apparaissent, comme dans le gabarit.
	 */
	private static class SheetWriter {

		private final Sheet sheet;
		private final CellStyle style;
		private int rowIndex = FIRST_DATA_ROW;

		private String pendingSubModule;
		private String pendingView;
		private String pendingFeature;

		private SheetWriter(Sheet sheet, CellStyle style) {
			this.sheet = sheet;
			this.style = style;
		}

		private void writeRow(String element, String action, String expectedResult) {

			Row row = sheet.createRow(rowIndex++);

			setCell(row, COLUMN_SUBMODULE, pendingSubModule, style);
			setCell(row, COLUMN_VIEW, pendingView, style);
			setCell(row, COLUMN_FEATURE, pendingFeature, style);
			setCell(row, COLUMN_ELEMENT, element, style);
			setCell(row, COLUMN_ACTION, action, style);
			setCell(row, COLUMN_EXPECTED_RESULT, expectedResult, style);

			pendingSubModule = null;
			pendingView = null;
			pendingFeature = null;
		}
	}
}