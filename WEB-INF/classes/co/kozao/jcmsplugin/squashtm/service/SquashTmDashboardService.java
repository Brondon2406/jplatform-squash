package co.kozao.jcmsplugin.squashtm.service;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import org.apache.log4j.Logger;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jalios.jcms.Channel;

import co.kozao.jcmsplugin.squashtm.model.dashboard.DashboardFilter;
import co.kozao.jcmsplugin.squashtm.util.SquashTmConstants;
import co.kozao.jcmsplugin.squashtm.util.SquashTmUtils;

/**
 * Service d'agrégation du dashboard Squash TM.
 *
 */
public class SquashTmDashboardService {

	private static final Logger LOGGER = Logger.getLogger(SquashTmDashboardService.class);

	private static final int WORKERS = 6;
	private static final long LOAD_BUDGET_MS = 180000L;
	private static final int DEFAULT_CACHE_MINUTES = 10;
	private static final String UNDEFINED = "UNDEFINED";

	private static final ZoneId ZONE = ZoneId.systemDefault();

	private static final Set<String> PENDING_EXEC = new HashSet<String>(Arrays.asList("READY", "RUNNING", "NOT_RUN"));
	private static final Set<String> SUCCESS_EXEC = new HashSet<String>(Arrays.asList("SUCCESS", "SETTLED"));
	private static final Set<String> FAILED_EXEC = new HashSet<String>(Arrays.asList("FAILURE", "ERROR"));

	private static final Cache<String, Dataset> CACHE = CacheBuilder.newBuilder().maximumSize(60).build();
	private static final ConcurrentMap<String, Object> LOCKS = new ConcurrentHashMap<String, Object>();

	/**
	 * Types de données récupérés depuis Squash TM.
	 *
	 */
	private enum Kind {

		REQUIREMENTS("/requirements", "/projects/%d/requirements", "/requirements/%d",
				"name,path,project[name],current_version[reference,status,criticality,category[code],created_by,created_on,last_modified_by,last_modified_on,verifying_test_cases]"),

		TEST_CASES("/test-cases", "/projects/%d/test-cases", "/test-cases/%d",
				"name,reference,path,project[name],created_by,created_on,last_modified_on,importance,status,nature[code],type[code],automatable,verified_requirements"),

		CAMPAIGNS("/campaigns", "/projects/%d/campaigns", "/campaigns/%d",
				"name,reference,path,project[name],status,created_by,created_on,last_modified_on,scheduled_start_date,scheduled_end_date,actual_start_date,actual_end_date,iterations"),

		REPORTING("", "/projects/%d/reporting-library/content?include=nested", "", null);

		final String flatPath;
		final String projectPath;
		final String detailPath;
		final String fields;

		Kind(String flatPath, String projectPath, String detailPath, String fields) {
			this.flatPath = flatPath;
			this.projectPath = projectPath;
			this.detailPath = detailPath;
			this.fields = fields;
		}
	}

	/**
	 * Une ligne du plan de test d'une itération (exécution).
	 */
	static final class Exec {
		String status;
		Long executedOn;
		String assignee;
		long testCaseId;
	}

	/**
	 * Élément normalisé : exigence, cas de test ou campagne.
	 */
	static final class Item {
		long id;
		long projectId;
		String projectName;
		String name;
		String reference;
		String path;
		String status;
		String author;
		Long createdOn;
		Long modifiedOn;

		// Exigences
		String criticality;
		String category;

		// Cas de test
		String importance;
		String nature;
		String type;
		String automatable;

		// Exigences et cas de test : nombre de liens de couverture
		int links;

		// Campagnes
		Long scheduledStart;
		Long scheduledEnd;
		Long actualStart;
		Long actualEnd;
		int iterations;
		final List<Exec> execs = new ArrayList<Exec>();
	}

	/**
	 * Exécution rattachée à sa campagne (après filtrage).
	 */
	static final class ExecRow {
		final Item campaign;
		final Exec exec;

		ExecRow(Item campaign, Exec exec) {
			this.campaign = campaign;
			this.exec = exec;
		}
	}

	/**
	 * Jeu de données chargé depuis Squash TM.
	 */
	static final class Dataset {
		final List<Item> items;
		final long loadedAt;
		final boolean partial;
		final boolean fromCache;

		Dataset(List<Item> items, long loadedAt, boolean partial, boolean fromCache) {
			this.items = items;
			this.loadedAt = loadedAt;
			this.partial = partial;
			this.fromCache = fromCache;
		}

		Dataset asCached() {
			return new Dataset(items, loadedAt, partial, true);
		}
	}

	private final SquashTmService service;
	private final String owner;

	/**
	 * @param authorizationHeader en-tête Authorization Squash TM (capturé dans la
	 *                            requête JCMS)
	 * @param owner               identifiant du membre JCMS (clé de cache)
	 */
	public SquashTmDashboardService(String authorizationHeader, String owner) {
		this.service = new SquashTmService(authorizationHeader);
		this.owner = owner == null ? "anonymous" : owner;
	}

	/*
	 * ====================================================================== API
	 * PUBLIQUE
	 * ======================================================================
	 */

	/**
	 * Liste des projets accessibles : [{id, name}].
	 */
	public JsonArray getProjects() throws IOException {

		JsonArray result = new JsonArray();

		for (JsonElement element : service.getAllPaged("/projects")) {

			if (!element.isJsonObject()) {
				continue;
			}

			JsonObject project = element.getAsJsonObject();
			long id = longOf(project, "id");

			if (id <= 0) {
				continue;
			}

			JsonObject row = new JsonObject();
			row.addProperty("id", id);
			row.addProperty("name", str(project, "name"));
			result.add(row);
		}

		return result;
	}

	/**
	 * Construit l'ensemble des données du dashboard pour les filtres donnés.
	 */
	public JsonObject buildDashboard(DashboardFilter f) throws IOException {

		long started = System.currentTimeMillis();

		Dataset requirements = f.wants(DashboardFilter.SCOPE_REQUIREMENTS) ? load(Kind.REQUIREMENTS, f.isRefresh())
				: null;
		Dataset testCases = f.wants(DashboardFilter.SCOPE_TEST_CASES) ? load(Kind.TEST_CASES, f.isRefresh()) : null;
		Dataset campaigns = f.wants(DashboardFilter.SCOPE_CAMPAIGNS) ? load(Kind.CAMPAIGNS, f.isRefresh()) : null;
		Dataset reporting = f.wants(DashboardFilter.SCOPE_REPORTING) ? load(Kind.REPORTING, f.isRefresh()) : null;

		JsonObject root = new JsonObject();
		root.addProperty("scope", f.getScope());

		boolean partial = false;
		boolean allFromCache = true;
		long oldest = System.currentTimeMillis();

		for (Dataset ds : new Dataset[] { requirements, testCases, campaigns, reporting }) {
			if (ds == null) {
				continue;
			}
			partial = partial || ds.partial;
			allFromCache = allFromCache && ds.fromCache;
			oldest = Math.min(oldest, ds.loadedAt);
		}

		root.addProperty("partial", partial);
		root.addProperty("fromCache", allFromCache);
		root.addProperty("dataTimestamp", oldest);

		root.add("options", buildOptions(requirements, testCases, campaigns, reporting));

		JsonObject kpis = new JsonObject();

		List<Item> filteredRequirements = requirements == null ? null : filterRequirements(requirements.items, f);
		List<Item> filteredTestCases = testCases == null ? null : filterTestCases(testCases.items, f);
		List<Item> filteredCampaigns = campaigns == null ? null : filterCampaigns(campaigns.items, f);
		List<ExecRow> filteredExecs = campaigns == null ? null : filterExecs(filteredCampaigns, f);

		if (filteredRequirements != null) {
			JsonObject section = buildRequirements(filteredRequirements, f);
			root.add("requirements", section);
			kpis.addProperty("requirements", filteredRequirements.size());
			kpis.add("requirementCoverage", section.get("coverageRate"));
		}

		if (filteredTestCases != null) {
			JsonObject section = buildTestCases(filteredTestCases, f);
			root.add("testCases", section);
			kpis.addProperty("testCases", filteredTestCases.size());
			kpis.add("testCaseLinkedRate", section.get("linkedRate"));
		}

		if (filteredCampaigns != null) {
			JsonObject section = buildCampaigns(filteredCampaigns, filteredExecs, f);
			root.add("campaigns", section);
			kpis.addProperty("campaigns", filteredCampaigns.size());
			kpis.addProperty("executions", filteredExecs.size());
			kpis.add("successRate", section.get("successRate"));
			kpis.add("progressRate", section.get("progressRate"));
		}

		if (reporting != null) {
			List<Item> filteredReporting = filterReporting(reporting.items, f);
			root.add("reporting", buildReporting(filteredReporting, f));
			root.addProperty("squashUrl", SquashTmUtils.getSquashTmServerUrl());
			kpis.addProperty("reportingItems", filteredReporting.size());
		}

		if (filteredTestCases != null && filteredExecs != null) {
			Integer never = countNeverExecuted(filteredTestCases, campaigns.items);
			if (never != null) {
				kpis.addProperty("neverExecutedTestCases", never.intValue());
			}
		}

		root.add("kpis", kpis);
		root.addProperty("durationMs", System.currentTimeMillis() - started);

		return root;
	}

	/**
	 * Export CSV des lignes filtrées. dataset : requirements, testcases, campaigns
	 * ou executions.
	 */
	public String buildCsv(DashboardFilter f, String dataset) throws IOException {

		StringBuilder csv = new StringBuilder("\uFEFF");
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZONE);

		if ("requirements".equals(dataset)) {

			csvLine(csv, "ID", "Reference", "Nom", "Projet", "Chemin", "Statut", "Criticite", "Categorie", "Tests lies",
					"Auteur", "Cree le", "Modifie le");

			for (Item i : filterRequirements(load(Kind.REQUIREMENTS, f.isRefresh()).items, f)) {
				csvLine(csv, String.valueOf(i.id), i.reference, i.name, i.projectName, i.path, i.status, i.criticality,
						i.category, String.valueOf(i.links), i.author, fmt(i.createdOn, formatter),
						fmt(i.modifiedOn, formatter));
			}

		} else if ("testcases".equals(dataset)) {

			csvLine(csv, "ID", "Reference", "Nom", "Projet", "Chemin", "Statut", "Importance", "Nature", "Type",
					"Automatisable", "Exigences liees", "Auteur", "Cree le", "Modifie le");

			for (Item i : filterTestCases(load(Kind.TEST_CASES, f.isRefresh()).items, f)) {
				csvLine(csv, String.valueOf(i.id), i.reference, i.name, i.projectName, i.path, i.status, i.importance,
						i.nature, i.type, i.automatable, String.valueOf(i.links), i.author, fmt(i.createdOn, formatter),
						fmt(i.modifiedOn, formatter));
			}

		} else if ("reporting".equals(dataset)) {

			csvLine(csv, "ID", "Nom", "Projet", "Type");

			for (Item i : filterReporting(load(Kind.REPORTING, f.isRefresh()).items, f)) {
				csvLine(csv, String.valueOf(i.id), i.name, i.projectName, i.type);
			}

		} else if ("campaigns".equals(dataset) || "executions".equals(dataset)) {

			List<Item> campaigns = filterCampaigns(load(Kind.CAMPAIGNS, f.isRefresh()).items, f);

			if ("campaigns".equals(dataset)) {

				csvLine(csv, "ID", "Reference", "Nom", "Projet", "Statut", "Iterations", "Executions", "Succes",
						"Echecs", "Bloques", "En attente", "Avancement %", "Succes %", "Debut prevu", "Fin prevue");

				for (Item c : campaigns) {
					int[] counts = countExecs(executionsOf(c, f));
					csvLine(csv, String.valueOf(c.id), c.reference, c.name, c.projectName, c.status,
							String.valueOf(c.iterations), String.valueOf(counts[0]), String.valueOf(counts[1]),
							String.valueOf(counts[2]), String.valueOf(counts[3]), String.valueOf(counts[4]),
							String.valueOf(pct(counts[0] - counts[4], counts[0])),
							String.valueOf(pct(counts[1], counts[0])), fmt(c.scheduledStart, formatter),
							fmt(c.scheduledEnd, formatter));
				}

			} else {

				csvLine(csv, "Campagne", "Projet", "Cas de test (ID)", "Statut", "Execute le", "Assigne a");

				for (ExecRow row : filterExecs(campaigns, f)) {
					csvLine(csv, row.campaign.name, row.campaign.projectName,
							row.exec.testCaseId > 0 ? String.valueOf(row.exec.testCaseId) : "", row.exec.status,
							fmt(row.exec.executedOn, formatter), row.exec.assignee);
				}
			}

		} else {
			throw new IOException("Jeu de données d'export inconnu : " + dataset);
		}

		return csv.toString();
	}

	/*
	 * ======================================================================
	 * CHARGEMENT ET CACHE
	 * ======================================================================
	 */

	private static boolean isExpired(Dataset dataset) {

		int minutes = DEFAULT_CACHE_MINUTES;

		try {
			minutes = Channel.getChannel().getIntegerProperty(SquashTmConstants.SQUASHTM_DASHBOARD_CACHE_MINUTES,
					DEFAULT_CACHE_MINUTES);
		} catch (Exception e) {
			minutes = DEFAULT_CACHE_MINUTES;
		}

		return System.currentTimeMillis() - dataset.loadedAt > TimeUnit.MINUTES.toMillis(Math.max(1, minutes));
	}

	private Dataset load(Kind kind, boolean refresh) throws IOException {

		String key = owner + "|" + kind.name();

		Dataset cached = CACHE.getIfPresent(key);

		if (!refresh && cached != null && !isExpired(cached)) {
			return cached.asCached();
		}

		Object lock = LOCKS.computeIfAbsent(key, k -> new Object());

		synchronized (lock) {

			cached = CACHE.getIfPresent(key);

			if (!refresh && cached != null && !isExpired(cached)) {
				return cached.asCached();
			}

			Dataset fresh = doLoad(kind);
			CACHE.put(key, fresh);
			return fresh;
		}
	}

	private Dataset doLoad(Kind kind) throws IOException {

		if (kind == Kind.REPORTING) {
			return doLoadReporting();
		}

		long started = System.currentTimeMillis();
		long deadline = started + LOAD_BUDGET_MS;

		JsonArray projects = service.getAllPaged("/projects");
		Map<Long, String> projectNames = new LinkedHashMap<Long, String>();

		for (JsonElement element : projects) {
			if (element.isJsonObject()) {
				long id = longOf(element.getAsJsonObject(), "id");
				if (id > 0) {
					projectNames.put(Long.valueOf(id), str(element.getAsJsonObject(), "name"));
				}
			}
		}

		List<JsonObject> raws = new ArrayList<JsonObject>();

		// 1. Liste globale paginée (méthode la plus rapide).
		boolean flatOk = fetchInto(kind.flatPath, kind.fields, 0L, null, raws);

		// 2. Repli : liste de chaque projet.
		if (!flatOk) {
			LOGGER.warn("Liste globale indisponible pour " + kind + ", lecture projet par projet.");
			collectPerProject(kind, projectNames, raws, deadline);
		}

		boolean partial = enrich(kind, raws, deadline);

		Map<Long, Item> items = new LinkedHashMap<Long, Item>();

		for (JsonObject raw : raws) {

			Item item = toItem(kind, raw, projectNames);

			if (item != null && !items.containsKey(Long.valueOf(item.id))) {
				items.put(Long.valueOf(item.id), item);
			}
		}

		List<Item> list = new ArrayList<Item>(items.values());

		if (kind == Kind.CAMPAIGNS) {
			partial = loadExecutions(list, deadline) || partial;
		}

		LOGGER.info("Dashboard Squash TM : " + list.size() + " éléments " + kind + " chargés en "
				+ (System.currentTimeMillis() - started) + " ms" + (partial ? " (chargement partiel)" : ""));

		return new Dataset(list, System.currentTimeMillis(), partial, false);
	}

	/**
	 * Lit une ressource paginée. Une première tentative demande uniquement les
	 * champs utiles ("fields") ; si le serveur la refuse, la liste simple est lue.
	 *
	 * @return false si la ressource est inaccessible
	 */
	private boolean fetchInto(String path, String fields, long projectId, String projectName, List<JsonObject> out) {

		String[] attempts = fields == null ? new String[] { path }
				: new String[] { path + (path.indexOf('?') >= 0 ? "&" : "?") + "fields=" + encodeFields(fields), path };

		for (String url : attempts) {

			try {

				List<JsonObject> page = new ArrayList<JsonObject>();

				for (JsonElement element : service.getAllPaged(url)) {

					if (!element.isJsonObject()) {
						continue;
					}

					JsonObject object = element.getAsJsonObject();

					if (projectId > 0) {
						object.addProperty("__projectId", projectId);
						object.addProperty("__projectName", projectName);
					}

					page.add(object);
				}

				out.addAll(page);
				return true;

			} catch (IOException e) {
				LOGGER.debug("Lecture impossible : " + url + " (" + e.getMessage() + ")");
			}
		}

		return false;
	}

	/**
	 * Les crochets du paramètre "fields" doivent être encodés dans l'URL.
	 */
	private static String encodeFields(String fields) {
		return fields.replace("[", "%5B").replace("]", "%5D");
	}

	private void collectPerProject(Kind kind, Map<Long, String> projectNames, List<JsonObject> out, long deadline) {

		for (Map.Entry<Long, String> project : projectNames.entrySet()) {

			if (System.currentTimeMillis() > deadline) {
				LOGGER.warn("Délai de chargement dépassé pendant la lecture des projets.");
				return;
			}

			long projectId = project.getKey().longValue();

			fetchInto(String.format(Locale.ROOT, kind.projectPath, projectId), kind.fields, projectId,
					project.getValue(), out);
		}
	}

	/**
	 * Charge la bibliothèque de tableaux de bord, graphiques, rapports et exports
	 * personnalisés de chaque projet.
	 */
	private Dataset doLoadReporting() throws IOException {

		long started = System.currentTimeMillis();
		final List<Item> items = Collections.synchronizedList(new ArrayList<Item>());
		List<Callable<Void>> tasks = new ArrayList<Callable<Void>>();

		for (JsonElement element : service.getAllPaged("/projects")) {

			if (!element.isJsonObject()) {
				continue;
			}

			final long projectId = longOf(element.getAsJsonObject(), "id");
			final String projectName = str(element.getAsJsonObject(), "name");

			if (projectId <= 0) {
				continue;
			}

			tasks.add(new Callable<Void>() {
				@Override
				public Void call() {

					try {

						for (JsonElement content : service
								.getAllPaged(String.format(Locale.ROOT, Kind.REPORTING.projectPath, projectId))) {

							if (!content.isJsonObject()) {
								continue;
							}

							JsonObject object = content.getAsJsonObject();
							String type = upper(str(object, "entity_type"));
							long id = longOf(object, "id");

							if (type == null || "FOLDER".equals(type) || id <= 0) {
								continue;
							}

							Item item = new Item();
							item.id = id;
							item.projectId = projectId;
							item.projectName = isEmpty(projectName) ? "-" : projectName;
							item.name = firstNonEmpty(str(object, "name"), "#" + id);
							item.type = type;
							items.add(item);
						}

					} catch (IOException e) {
						LOGGER.debug("Bibliothèque de rapports indisponible pour le projet " + projectId + " : "
								+ e.getMessage());
					}

					return null;
				}
			});
		}

		runAll(tasks);

		List<Item> list = new ArrayList<Item>(items);

		Collections.sort(list, new Comparator<Item>() {
			@Override
			public int compare(Item a, Item b) {
				int byProject = lower(a.projectName).compareTo(lower(b.projectName));
				return byProject != 0 ? byProject : lower(a.name).compareTo(lower(b.name));
			}
		});

		LOGGER.info("Dashboard Squash TM : " + list.size() + " tableaux, graphiques ou rapports chargés en "
				+ (System.currentTimeMillis() - started) + " ms");

		return new Dataset(list, System.currentTimeMillis(), false, false);
	}

	private static boolean needsDetail(Kind kind, JsonObject o) {

		switch (kind) {
		case REQUIREMENTS:
			return !(o.has("current_version") || o.has("status"));
		case TEST_CASES:
			return !(o.has("status") || o.has("importance"));
		default:
			return !(o.has("status") || o.has("created_on") || o.has("scheduled_start_date"));
		}
	}

	/**
	 * Complète les éléments dont la liste ne fournit pas les attributs nécessaires
	 * aux statistiques.
	 *
	 * @return true si le délai a été dépassé (données partielles)
	 */
	private boolean enrich(final Kind kind, final List<JsonObject> raws, final long deadline) {

		final List<Integer> todo = new ArrayList<Integer>();

		for (int i = 0; i < raws.size(); i++) {
			if (needsDetail(kind, raws.get(i))) {
				todo.add(Integer.valueOf(i));
			}
		}

		if (todo.isEmpty()) {
			return false;
		}

		final AtomicBoolean partial = new AtomicBoolean(false);
		List<Callable<Void>> tasks = new ArrayList<Callable<Void>>();

		for (final Integer index : todo) {

			tasks.add(new Callable<Void>() {
				@Override
				public Void call() {

					if (System.currentTimeMillis() > deadline) {
						partial.set(true);
						return null;
					}

					JsonObject raw = raws.get(index.intValue());
					long id = longOf(raw, "id");

					if (id <= 0) {
						return null;
					}

					try {

						JsonObject detail = service.getResource(String.format(Locale.ROOT, kind.detailPath, id));

						if (raw.has("__projectId")) {
							detail.add("__projectId", raw.get("__projectId"));
							detail.add("__projectName", raw.get("__projectName"));
						}

						raws.set(index.intValue(), detail);

					} catch (IOException e) {
						LOGGER.debug("Détail indisponible pour " + kind + " " + id + " : " + e.getMessage());
					}

					return null;
				}
			});
		}

		runAll(tasks);

		return partial.get();
	}

	/**
	 * Charge itérations et plans de test de chaque campagne.
	 *
	 * @return true si le délai a été dépassé (données partielles)
	 */
	private boolean loadExecutions(List<Item> campaigns, final long deadline) {

		final AtomicBoolean partial = new AtomicBoolean(false);
		List<Callable<Void>> tasks = new ArrayList<Callable<Void>>();

		for (final Item campaign : campaigns) {

			tasks.add(new Callable<Void>() {
				@Override
				public Void call() {

					if (System.currentTimeMillis() > deadline) {
						partial.set(true);
						return null;
					}

					try {

						JsonArray iterations = service.getAllPaged("/campaigns/" + campaign.id + "/iterations");
						campaign.iterations = iterations.size();

						for (JsonElement iterationElement : iterations) {

							if (!iterationElement.isJsonObject()) {
								continue;
							}

							long iterationId = longOf(iterationElement.getAsJsonObject(), "id");

							if (iterationId <= 0) {
								continue;
							}

							for (JsonElement planElement : service
									.getAllPaged("/iterations/" + iterationId + "/test-plan")) {

								if (!planElement.isJsonObject()) {
									continue;
								}

								JsonObject plan = planElement.getAsJsonObject();
								Exec exec = new Exec();

								exec.status = upper(firstNonEmpty(str(plan, "execution_status"), str(plan, "status")));
								exec.executedOn = date(plan, "last_executed_on");
								exec.assignee = firstNonEmpty(labelOf(plan.get("assigned_to")),
										labelOf(plan.get("assignee")));

								JsonObject testCase = obj(plan, "referenced_test_case");
								exec.testCaseId = testCase == null ? 0L : longOf(testCase, "id");

								campaign.execs.add(exec);
							}
						}

					} catch (IOException e) {
						LOGGER.debug(
								"Exécutions indisponibles pour la campagne " + campaign.id + " : " + e.getMessage());
					}

					return null;
				}
			});
		}

		runAll(tasks);

		return partial.get();
	}

	private void runAll(List<Callable<Void>> tasks) {

		ExecutorService pool = Executors.newFixedThreadPool(WORKERS);

		try {

			List<Future<Void>> futures = new ArrayList<Future<Void>>();

			for (Callable<Void> task : tasks) {
				futures.add(pool.submit(task));
			}

			for (Future<Void> future : futures) {
				try {
					future.get();
				} catch (Exception e) {
					LOGGER.debug("Tâche de chargement interrompue : " + e.getMessage());
				}
			}

		} finally {
			pool.shutdown();
		}
	}

	/*
	 * ======================================================================
	 * CONVERSION JSON -> ELEMENTS
	 * ======================================================================
	 */

	private Item toItem(Kind kind, JsonObject o, Map<Long, String> projectNames) {

		long id = longOf(o, "id");

		if (id <= 0) {
			return null;
		}

		Item item = new Item();
		item.id = id;

		JsonObject project = obj(o, "project");

		if (project != null && longOf(project, "id") > 0) {
			item.projectId = longOf(project, "id");
			item.projectName = str(project, "name");
		} else {
			item.projectId = longOf(o, "__projectId");
			item.projectName = str(o, "__projectName");
		}

		if (isEmpty(item.projectName) && projectNames.containsKey(Long.valueOf(item.projectId))) {
			item.projectName = projectNames.get(Long.valueOf(item.projectId));
		}

		if (isEmpty(item.projectName)) {
			item.projectName = "-";
		}

		item.path = str(o, "path");

		if (kind == Kind.REQUIREMENTS) {

			JsonObject version = obj(o, "current_version");
			JsonObject v = version != null ? version : o;

			item.name = firstNonEmpty(str(o, "name"), str(v, "name"));
			item.reference = firstNonEmpty(str(v, "reference"), str(o, "reference"));
			item.status = upper(firstNonEmpty(str(v, "status"), str(o, "status")));
			item.criticality = upper(firstNonEmpty(codeOf(v.get("criticality")), codeOf(o.get("criticality"))));
			item.category = upper(firstNonEmpty(codeOf(v.get("category")), codeOf(o.get("category"))));
			item.author = firstNonEmpty(str(v, "created_by"), str(o, "created_by"));
			item.createdOn = firstDate(date(v, "created_on"), date(o, "created_on"));
			item.modifiedOn = firstDate(date(v, "last_modified_on"), date(o, "last_modified_on"));
			item.links = Math.max(sizeOf(v, "verifying_test_cases"), sizeOf(o, "verifying_test_cases"));

		} else if (kind == Kind.TEST_CASES) {

			item.name = str(o, "name");
			item.reference = str(o, "reference");
			item.status = upper(str(o, "status"));
			item.importance = upper(str(o, "importance"));
			item.nature = upper(codeOf(o.get("nature")));
			item.type = upper(codeOf(o.get("type")));
			item.automatable = upper(str(o, "automatable"));
			item.author = str(o, "created_by");
			item.createdOn = date(o, "created_on");
			item.modifiedOn = date(o, "last_modified_on");
			item.links = sizeOf(o, "verified_requirements");

		} else {

			item.name = str(o, "name");
			item.reference = str(o, "reference");
			item.status = upper(firstNonEmpty(str(o, "status"), str(o, "progress_status")));
			item.author = str(o, "created_by");
			item.createdOn = date(o, "created_on");
			item.modifiedOn = date(o, "last_modified_on");
			item.scheduledStart = date(o, "scheduled_start_date");
			item.scheduledEnd = date(o, "scheduled_end_date");
			item.actualStart = date(o, "actual_start_date");
			item.actualEnd = date(o, "actual_end_date");
			item.iterations = sizeOf(o, "iterations");
		}

		if (isEmpty(item.name)) {
			item.name = "#" + item.id;
		}

		return item;
	}

	/*
	 * ======================================================================
	 * FILTRAGE
	 * ======================================================================
	 */

	private boolean matchesCommon(Item i, DashboardFilter f) {

		if (!f.getProjectIds().isEmpty() && !f.getProjectIds().contains(Long.valueOf(i.projectId))) {
			return false;
		}

		if (!f.getAuthors().isEmpty() && (i.author == null || !f.getAuthors().contains(i.author))) {
			return false;
		}

		if (!f.getPath().isEmpty() && lower(i.path).indexOf(f.getPath()) < 0) {
			return false;
		}

		if (!f.getSearch().isEmpty()) {

			String haystack = lower(i.name) + "\n" + lower(i.reference) + "\n" + lower(i.path) + "\n" + i.id;

			if (haystack.indexOf(f.getSearch()) < 0) {
				return false;
			}
		}

		return true;
	}

	private static boolean inSet(Set<String> allowed, String value) {
		return allowed.isEmpty() || allowed.contains(value == null ? UNDEFINED : value);
	}

	private static Long dateOf(Item i, DashboardFilter f) {
		return DashboardFilter.DATE_MODIFIED.equals(f.getDateField()) ? i.modifiedOn : i.createdOn;
	}

	private List<Item> filterRequirements(List<Item> source, DashboardFilter f) {

		List<Item> result = new ArrayList<Item>();

		for (Item i : source) {

			if (!matchesCommon(i, f) || !f.inPeriod(dateOf(i, f))) {
				continue;
			}

			if (!inSet(f.getRequirementStatuses(), i.status) || !inSet(f.getCriticalities(), i.criticality)
					|| !inSet(f.getCategories(), i.category)) {
				continue;
			}

			if (DashboardFilter.COVERAGE_COVERED.equals(f.getRequirementCoverage()) && i.links == 0) {
				continue;
			}

			if (DashboardFilter.COVERAGE_UNCOVERED.equals(f.getRequirementCoverage()) && i.links > 0) {
				continue;
			}

			result.add(i);
		}

		return result;
	}

	private List<Item> filterTestCases(List<Item> source, DashboardFilter f) {

		List<Item> result = new ArrayList<Item>();

		for (Item i : source) {

			if (!matchesCommon(i, f) || !f.inPeriod(dateOf(i, f))) {
				continue;
			}

			if (!inSet(f.getTestCaseStatuses(), i.status) || !inSet(f.getImportances(), i.importance)
					|| !inSet(f.getNatures(), i.nature) || !inSet(f.getTypes(), i.type)) {
				continue;
			}

			if (!"ALL".equals(f.getAutomatable()) && !f.getAutomatable().equals(i.automatable)) {
				continue;
			}

			if (DashboardFilter.COVERAGE_COVERED.equals(f.getTestCaseCoverage()) && i.links == 0) {
				continue;
			}

			if (DashboardFilter.COVERAGE_UNCOVERED.equals(f.getTestCaseCoverage()) && i.links > 0) {
				continue;
			}

			result.add(i);
		}

		return result;
	}

	/**
	 * Une campagne est retenue pour une période si elle a été créée ou modifiée
	 * pendant la période, si l'une de ses exécutions a eu lieu pendant la période,
	 * ou si sa planification chevauche la période.
	 */
	private List<Item> filterCampaigns(List<Item> source, DashboardFilter f) {

		List<Item> result = new ArrayList<Item>();

		for (Item i : source) {

			if (!matchesCommon(i, f) || !inSet(f.getCampaignStatuses(), i.status)) {
				continue;
			}

			if (f.hasPeriod() && !campaignActiveInPeriod(i, f)) {
				continue;
			}

			result.add(i);
		}

		return result;
	}

	private boolean campaignActiveInPeriod(Item c, DashboardFilter f) {

		if (f.inPeriod(dateOf(c, f))) {
			return true;
		}

		for (Exec e : c.execs) {
			if (f.inPeriod(e.executedOn)) {
				return true;
			}
		}

		Long start = c.actualStart != null ? c.actualStart : c.scheduledStart;
		Long end = c.actualEnd != null ? c.actualEnd : c.scheduledEnd;

		if (start == null && end == null) {
			return false;
		}

		long from = f.getDateFrom() == null ? Long.MIN_VALUE : f.getDateFrom().longValue();
		long to = f.getDateTo() == null ? Long.MAX_VALUE : f.getDateTo().longValue();
		long s = start == null ? Long.MIN_VALUE : start.longValue();
		long e = end == null ? Long.MAX_VALUE : end.longValue();

		return s <= to && e >= from;
	}

	private List<Exec> executionsOf(Item campaign, DashboardFilter f) {

		List<Exec> result = new ArrayList<Exec>();

		for (Exec e : campaign.execs) {

			if (!inSet(f.getExecutionStatuses(), e.status)) {
				continue;
			}

			if (f.hasPeriod() && !f.inPeriod(e.executedOn)) {
				continue;
			}

			result.add(e);
		}

		return result;
	}

	private List<ExecRow> filterExecs(List<Item> campaigns, DashboardFilter f) {

		List<ExecRow> result = new ArrayList<ExecRow>();

		for (Item c : campaigns) {
			for (Exec e : executionsOf(c, f)) {
				result.add(new ExecRow(c, e));
			}
		}

		return result;
	}

	/*
	 * ======================================================================
	 * STATISTIQUES
	 * ======================================================================
	 */

	/**
	 * Tableaux de bord, graphiques, rapports et exports Squash TM : seuls le
	 * projet, le type et le nom sont filtrables (l'API n'expose ni dates ni
	 * auteur).
	 */
	private List<Item> filterReporting(List<Item> source, DashboardFilter f) {

		List<Item> result = new ArrayList<Item>();

		for (Item i : source) {

			if (!f.getProjectIds().isEmpty() && !f.getProjectIds().contains(Long.valueOf(i.projectId))) {
				continue;
			}

			if (!inSet(f.getReportTypes(), i.type)) {
				continue;
			}

			if (!f.getSearch().isEmpty() && (lower(i.name) + "\n" + i.id).indexOf(f.getSearch()) < 0) {
				continue;
			}

			result.add(i);
		}

		return result;
	}

	private JsonObject buildReporting(List<Item> list, DashboardFilter f) {

		JsonObject o = new JsonObject();

		o.addProperty("total", list.size());
		o.add("byType", distribution(list, i -> i.type));
		o.add("byProject", distribution(list, i -> i.projectName));

		JsonArray rows = new JsonArray();

		for (Item item : limit(list, f.getLimit())) {
			JsonObject r = new JsonObject();
			r.addProperty("id", item.id);
			r.addProperty("name", item.name);
			r.addProperty("project", item.projectName);
			r.addProperty("type", orUndefined(item.type));
			rows.add(r);
		}

		o.add("rows", rows);
		o.addProperty("truncated", list.size() > f.getLimit());

		return o;
	}

	private JsonObject buildRequirements(List<Item> list, DashboardFilter f) {

		JsonObject o = new JsonObject();
		int total = list.size();
		int covered = 0;

		for (Item item : list) {
			if (item.links > 0) {
				covered++;
			}
		}

		o.addProperty("total", total);
		o.addProperty("covered", covered);
		o.addProperty("uncovered", total - covered);
		o.addProperty("coverageRate", pct(covered, total));

		o.add("byStatus", distribution(list, i -> i.status));
		o.add("byCriticality", distribution(list, i -> i.criticality));
		o.add("byCategory", distribution(list, i -> i.category));
		o.add("byProject", distribution(list, i -> i.projectName));
		o.add("timeline", timeline(list, f));

		JsonArray rows = new JsonArray();

		for (Item item : limit(sortByModified(list), f.getLimit())) {
			JsonObject r = baseRow(item);
			r.addProperty("status", orUndefined(item.status));
			r.addProperty("criticality", orUndefined(item.criticality));
			r.addProperty("category", orUndefined(item.category));
			r.addProperty("links", item.links);
			rows.add(r);
		}

		o.add("rows", rows);
		o.addProperty("truncated", total > f.getLimit());

		return o;
	}

	private JsonObject buildTestCases(List<Item> list, DashboardFilter f) {

		JsonObject o = new JsonObject();
		int total = list.size();
		int linked = 0;

		for (Item item : list) {
			if (item.links > 0) {
				linked++;
			}
		}

		o.addProperty("total", total);
		o.addProperty("linked", linked);
		o.addProperty("notLinked", total - linked);
		o.addProperty("linkedRate", pct(linked, total));

		o.add("byStatus", distribution(list, i -> i.status));
		o.add("byImportance", distribution(list, i -> i.importance));
		o.add("byNature", distribution(list, i -> i.nature));
		o.add("byType", distribution(list, i -> i.type));
		o.add("byAutomation", distribution(list, i -> i.automatable));
		o.add("byProject", distribution(list, i -> i.projectName));
		o.add("timeline", timeline(list, f));

		JsonArray rows = new JsonArray();

		for (Item item : limit(sortByModified(list), f.getLimit())) {
			JsonObject r = baseRow(item);
			r.addProperty("status", orUndefined(item.status));
			r.addProperty("importance", orUndefined(item.importance));
			r.addProperty("nature", orUndefined(item.nature));
			r.addProperty("type", orUndefined(item.type));
			r.addProperty("links", item.links);
			rows.add(r);
		}

		o.add("rows", rows);
		o.addProperty("truncated", total > f.getLimit());

		return o;
	}

	private JsonObject buildCampaigns(List<Item> campaigns, List<ExecRow> execs, final DashboardFilter f) {

		JsonObject o = new JsonObject();

		Map<String, Integer> byStatus = new LinkedHashMap<String, Integer>();
		List<Exec> flat = new ArrayList<Exec>();

		for (ExecRow row : execs) {
			add(byStatus, orUndefined(row.exec.status));
			flat.add(row.exec);
		}

		int[] totals = countExecs(flat);

		o.addProperty("total", campaigns.size());
		o.addProperty("executionTotal", totals[0]);
		o.addProperty("successRate", pct(totals[1], totals[0]));
		o.addProperty("progressRate", pct(totals[0] - totals[4], totals[0]));

		o.add("byStatus", distribution(campaigns, i -> i.status));
		o.add("byProject", distribution(campaigns, i -> i.projectName));
		o.add("executionsByStatus", toDistribution(byStatus));
		o.add("executionsByProject", distributionOfExecs(execs));
		o.add("executionTimeline", executionTimeline(execs, f));

		JsonArray rows = new JsonArray();
		List<Item> sorted = new ArrayList<Item>(campaigns);

		Collections.sort(sorted, new Comparator<Item>() {
			@Override
			public int compare(Item a, Item b) {
				return Long.compare(lastExecution(b, f), lastExecution(a, f));
			}
		});

		for (Item c : limit(sorted, f.getLimit())) {

			List<Exec> own = executionsOf(c, f);
			int[] counts = countExecs(own);
			JsonObject r = baseRow(c);

			r.addProperty("status", orUndefined(c.status));
			r.addProperty("iterations", c.iterations);
			r.addProperty("executions", counts[0]);
			r.addProperty("success", counts[1]);
			r.addProperty("failure", counts[2]);
			r.addProperty("pending", counts[4]);
			r.addProperty("progress", pct(counts[0] - counts[4], counts[0]));
			r.addProperty("successRate", pct(counts[1], counts[0]));

			long last = lastExecution(c, f);
			if (last > 0) {
				r.addProperty("lastExecutedOn", last);
			}
			if (c.scheduledStart != null) {
				r.addProperty("scheduledStart", c.scheduledStart);
			}
			if (c.scheduledEnd != null) {
				r.addProperty("scheduledEnd", c.scheduledEnd);
			}

			rows.add(r);
		}

		o.add("rows", rows);
		o.addProperty("truncated", campaigns.size() > f.getLimit());

		return o;
	}

	private JsonObject buildOptions(Dataset requirements, Dataset testCases, Dataset campaigns, Dataset reporting) {

		JsonObject o = new JsonObject();
		Set<String> authors = new TreeSet<String>();

		if (requirements != null) {
			o.add("requirementStatuses", distinct(requirements.items, i -> i.status));
			o.add("criticalities", distinct(requirements.items, i -> i.criticality));
			o.add("categories", distinct(requirements.items, i -> i.category));
			collectAuthors(requirements.items, authors);
		}

		if (testCases != null) {
			o.add("testCaseStatuses", distinct(testCases.items, i -> i.status));
			o.add("importances", distinct(testCases.items, i -> i.importance));
			o.add("natures", distinct(testCases.items, i -> i.nature));
			o.add("types", distinct(testCases.items, i -> i.type));
			collectAuthors(testCases.items, authors);
		}

		if (campaigns != null) {

			o.add("campaignStatuses", distinct(campaigns.items, i -> i.status));
			collectAuthors(campaigns.items, authors);

			Set<String> execStatuses = new TreeSet<String>();
			for (Item c : campaigns.items) {
				for (Exec e : c.execs) {
					execStatuses.add(orUndefined(e.status));
				}
			}

			JsonArray array = new JsonArray();
			for (String s : execStatuses) {
				array.add(s);
			}
			o.add("executionStatuses", array);
		}

		if (reporting != null) {
			o.add("reportTypes", distinct(reporting.items, i -> i.type));
		}

		JsonArray authorArray = new JsonArray();
		for (String a : authors) {
			authorArray.add(a);
		}
		o.add("authors", authorArray);

		return o;
	}

	private void collectAuthors(List<Item> items, Set<String> authors) {
		for (Item i : items) {
			if (!isEmpty(i.author)) {
				authors.add(i.author);
			}
		}
	}

	/**
	 * Nombre de cas de test (filtrés) qui n'apparaissent dans aucun plan de test.
	 * Retourne null si aucune exécution ne référence de cas de test.
	 */
	private Integer countNeverExecuted(List<Item> testCases, List<Item> allCampaigns) {

		Set<Long> executed = new HashSet<Long>();

		for (Item c : allCampaigns) {
			for (Exec e : c.execs) {
				if (e.testCaseId > 0) {
					executed.add(Long.valueOf(e.testCaseId));
				}
			}
		}

		if (executed.isEmpty()) {
			return null;
		}

		int never = 0;

		for (Item tc : testCases) {
			if (!executed.contains(Long.valueOf(tc.id))) {
				never++;
			}
		}

		return Integer.valueOf(never);
	}

	/*
	 * ----------------------------------------------------------------------
	 * Agrégations
	 * ----------------------------------------------------------------------
	 */

	/**
	 * @return [total, succès, échecs, bloqués, en attente]
	 */
	private static int[] countExecs(List<Exec> execs) {

		int[] counts = new int[5];

		for (Exec e : execs) {

			String status = orUndefined(e.status);

			counts[0]++;

			if (SUCCESS_EXEC.contains(status)) {
				counts[1]++;
			} else if (FAILED_EXEC.contains(status)) {
				counts[2]++;
			} else if ("BLOCKED".equals(status)) {
				counts[3]++;
			} else if (PENDING_EXEC.contains(status)) {
				counts[4]++;
			}
		}

		return counts;
	}

	private static long lastExecution(Item campaign, DashboardFilter f) {

		long last = 0L;

		for (Exec e : campaign.execs) {
			if (e.executedOn != null && f.inPeriod(e.executedOn)) {
				last = Math.max(last, e.executedOn.longValue());
			}
		}

		return last;
	}

	private JsonArray distribution(List<Item> list, Function<Item, String> key) {

		Map<String, Integer> counts = new LinkedHashMap<String, Integer>();

		for (Item item : list) {
			add(counts, orUndefined(key.apply(item)));
		}

		return toDistribution(counts);
	}

	private JsonArray distributionOfExecs(List<ExecRow> execs) {

		Map<String, Integer> counts = new LinkedHashMap<String, Integer>();

		for (ExecRow row : execs) {
			add(counts, orUndefined(row.campaign.projectName));
		}

		return toDistribution(counts);
	}

	private JsonArray distinct(List<Item> list, Function<Item, String> key) {

		Set<String> values = new TreeSet<String>();

		for (Item item : list) {
			values.add(orUndefined(key.apply(item)));
		}

		JsonArray array = new JsonArray();

		for (String value : values) {
			array.add(value);
		}

		return array;
	}

	private static void add(Map<String, Integer> counts, String key) {
		Integer current = counts.get(key);
		counts.put(key, Integer.valueOf(current == null ? 1 : current.intValue() + 1));
	}

	private static JsonArray toDistribution(Map<String, Integer> counts) {

		List<Map.Entry<String, Integer>> entries = new ArrayList<Map.Entry<String, Integer>>(counts.entrySet());

		Collections.sort(entries, new Comparator<Map.Entry<String, Integer>>() {
			@Override
			public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
				return b.getValue().compareTo(a.getValue());
			}
		});

		JsonArray array = new JsonArray();

		for (Map.Entry<String, Integer> entry : entries) {
			JsonObject row = new JsonObject();
			row.addProperty("key", entry.getKey());
			row.addProperty("count", entry.getValue());
			array.add(row);
		}

		return array;
	}

	/*
	 * ----------------------------------------------------------------------
	 * Chronologie
	 * ----------------------------------------------------------------------
	 */

	private JsonObject timeline(List<Item> list, DashboardFilter f) {

		List<Long> stamps = new ArrayList<Long>();

		for (Item item : list) {
			Long ts = dateOf(item, f);
			if (ts != null) {
				stamps.add(ts);
			}
		}

		String group = resolveGroup(f, stamps);
		Map<String, Integer> buckets = new TreeMap<String, Integer>();

		for (Long ts : stamps) {
			add(buckets, bucketKey(ts.longValue(), group));
		}

		JsonArray points = new JsonArray();

		for (Map.Entry<String, Integer> entry : buckets.entrySet()) {
			JsonObject p = new JsonObject();
			p.addProperty("period", entry.getKey());
			p.addProperty("count", entry.getValue());
			points.add(p);
		}

		JsonObject o = new JsonObject();
		o.addProperty("group", group);
		o.add("points", points);

		return o;
	}

	private JsonObject executionTimeline(List<ExecRow> execs, DashboardFilter f) {

		List<Long> stamps = new ArrayList<Long>();

		for (ExecRow row : execs) {
			if (row.exec.executedOn != null) {
				stamps.add(row.exec.executedOn);
			}
		}

		String group = resolveGroup(f, stamps);
		Map<String, int[]> buckets = new TreeMap<String, int[]>();

		for (ExecRow row : execs) {

			if (row.exec.executedOn == null) {
				continue;
			}

			String key = bucketKey(row.exec.executedOn.longValue(), group);
			int[] values = buckets.get(key);

			if (values == null) {
				values = new int[3];
				buckets.put(key, values);
			}

			String status = orUndefined(row.exec.status);

			values[0]++;

			if (SUCCESS_EXEC.contains(status)) {
				values[1]++;
			} else if (FAILED_EXEC.contains(status)) {
				values[2]++;
			}
		}

		JsonArray points = new JsonArray();

		for (Map.Entry<String, int[]> entry : buckets.entrySet()) {
			JsonObject p = new JsonObject();
			p.addProperty("period", entry.getKey());
			p.addProperty("count", entry.getValue()[0]);
			p.addProperty("success", entry.getValue()[1]);
			p.addProperty("failure", entry.getValue()[2]);
			points.add(p);
		}

		JsonObject o = new JsonObject();
		o.addProperty("group", group);
		o.add("points", points);

		return o;
	}

	private static String resolveGroup(DashboardFilter f, List<Long> stamps) {

		if (!DashboardFilter.GROUP_AUTO.equals(f.getGroupBy())) {
			return f.getGroupBy();
		}

		long min = f.getDateFrom() != null ? f.getDateFrom().longValue() : Long.MAX_VALUE;
		long max = f.getDateTo() != null ? f.getDateTo().longValue() : Long.MIN_VALUE;

		for (Long ts : stamps) {
			if (f.getDateFrom() == null) {
				min = Math.min(min, ts.longValue());
			}
			if (f.getDateTo() == null) {
				max = Math.max(max, ts.longValue());
			}
		}

		if (min == Long.MAX_VALUE || max == Long.MIN_VALUE) {
			return DashboardFilter.GROUP_MONTH;
		}

		long days = (max - min) / 86400000L;

		if (days <= 45) {
			return DashboardFilter.GROUP_DAY;
		}

		return days <= 200 ? DashboardFilter.GROUP_WEEK : DashboardFilter.GROUP_MONTH;
	}

	private static String bucketKey(long timestamp, String group) {

		LocalDate day = Instant.ofEpochMilli(timestamp).atZone(ZONE).toLocalDate();

		if (DashboardFilter.GROUP_DAY.equals(group)) {
			return day.toString();
		}

		if (DashboardFilter.GROUP_WEEK.equals(group)) {
			return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();
		}

		return String.format(Locale.ROOT, "%04d-%02d", day.getYear(), day.getMonthValue());
	}

	/*
	 * ---------------------------------------------------------------------- Lignes
	 * de tableaux
	 * ----------------------------------------------------------------------
	 */

	private static JsonObject baseRow(Item i) {

		JsonObject r = new JsonObject();
		r.addProperty("id", i.id);
		r.addProperty("reference", i.reference == null ? "" : i.reference);
		r.addProperty("name", i.name);
		r.addProperty("project", i.projectName);
		r.addProperty("path", i.path == null ? "" : i.path);

		if (i.createdOn != null) {
			r.addProperty("createdOn", i.createdOn);
		}
		if (i.modifiedOn != null) {
			r.addProperty("modifiedOn", i.modifiedOn);
		}

		return r;
	}

	private static List<Item> sortByModified(List<Item> list) {

		List<Item> sorted = new ArrayList<Item>(list);

		Collections.sort(sorted, new Comparator<Item>() {
			@Override
			public int compare(Item a, Item b) {
				long ta = a.modifiedOn != null ? a.modifiedOn.longValue()
						: (a.createdOn != null ? a.createdOn.longValue() : 0L);
				long tb = b.modifiedOn != null ? b.modifiedOn.longValue()
						: (b.createdOn != null ? b.createdOn.longValue() : 0L);
				return Long.compare(tb, ta);
			}
		});

		return sorted;
	}

	private static <T> List<T> limit(List<T> list, int max) {
		return list.size() <= max ? list : list.subList(0, max);
	}

	/*
	 * ---------------------------------------------------------------------- CSV
	 * ----------------------------------------------------------------------
	 */

	private static void csvLine(StringBuilder csv, String... cells) {

		for (int i = 0; i < cells.length; i++) {

			if (i > 0) {
				csv.append(';');
			}

			String value = cells[i] == null ? "" : cells[i];

			// Neutralise les formules interprétées par les tableurs.
			if (!value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0 && !isNumber(value)) {
				value = "'" + value;
			}

			csv.append('"').append(value.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ")).append('"');
		}

		csv.append("\r\n");
	}

	private static boolean isNumber(String value) {
		try {
			Double.parseDouble(value);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	private static String fmt(Long timestamp, DateTimeFormatter formatter) {
		return timestamp == null ? "" : formatter.format(Instant.ofEpochMilli(timestamp.longValue()));
	}

	/*
	 * ====================================================================== OUTILS
	 * JSON / TEXTE
	 * ======================================================================
	 */

	private static double pct(int part, int total) {

		if (total <= 0) {
			return 0d;
		}

		return Math.round(1000.0d * part / total) / 10.0d;
	}

	private static boolean isEmpty(String value) {
		return value == null || value.trim().isEmpty();
	}

	private static String lower(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT);
	}

	private static String upper(String value) {
		return isEmpty(value) ? null : value.trim().toUpperCase(Locale.ROOT);
	}

	private static String orUndefined(String value) {
		return isEmpty(value) ? UNDEFINED : value;
	}

	private static String firstNonEmpty(String a, String b) {
		return !isEmpty(a) ? a : b;
	}

	private static Long firstDate(Long a, Long b) {
		return a != null ? a : b;
	}

	private static JsonObject obj(JsonObject parent, String property) {

		if (parent == null || !parent.has(property) || !parent.get(property).isJsonObject()) {
			return null;
		}

		return parent.getAsJsonObject(property);
	}

	private static String str(JsonObject o, String property) {

		if (o == null || !o.has(property) || o.get(property).isJsonNull() || !o.get(property).isJsonPrimitive()) {
			return null;
		}

		return o.get(property).getAsString();
	}

	private static long longOf(JsonObject o, String property) {

		if (o == null || !o.has(property) || o.get(property).isJsonNull() || !o.get(property).isJsonPrimitive()) {
			return 0L;
		}

		try {
			return o.get(property).getAsLong();
		} catch (NumberFormatException e) {
			return 0L;
		}
	}

	private static int sizeOf(JsonObject o, String property) {

		if (o == null || !o.has(property) || !o.get(property).isJsonArray()) {
			return 0;
		}

		return o.getAsJsonArray(property).size();
	}

	/**
	 * Valeur texte d'un champ qui peut être une chaîne ou un objet {code, label}.
	 */
	private static String codeOf(JsonElement element) {

		if (element == null || element.isJsonNull()) {
			return null;
		}

		if (element.isJsonPrimitive()) {
			return element.getAsString();
		}

		if (element.isJsonObject()) {
			JsonObject o = element.getAsJsonObject();
			return firstNonEmpty(str(o, "code"), firstNonEmpty(str(o, "label"), str(o, "name")));
		}

		return null;
	}

	private static String labelOf(JsonElement element) {

		if (element == null || element.isJsonNull()) {
			return null;
		}

		if (element.isJsonPrimitive()) {
			return element.getAsString();
		}

		if (element.isJsonObject()) {
			JsonObject o = element.getAsJsonObject();
			return firstNonEmpty(str(o, "login"), firstNonEmpty(str(o, "name"), str(o, "last_name")));
		}

		return null;
	}

	private static Long date(JsonObject o, String property) {
		return parseDate(str(o, property));
	}

	private static Long parseDate(String value) {

		if (isEmpty(value)) {
			return null;
		}

		String s = value.trim();

		try {
			return Long.valueOf(OffsetDateTime.parse(s).toInstant().toEpochMilli());
		} catch (DateTimeParseException e) {
			// format suivant
		}

		try {
			return Long.valueOf(LocalDateTime.parse(s.length() > 19 ? s.substring(0, 19) : s).atZone(ZONE).toInstant()
					.toEpochMilli());
		} catch (DateTimeParseException e) {
			// format suivant
		}

		try {
			return Long.valueOf(LocalDate.parse(s.substring(0, Math.min(10, s.length()))).atStartOfDay(ZONE).toInstant()
					.toEpochMilli());
		} catch (DateTimeParseException e) {
			return null;
		}
	}
}
