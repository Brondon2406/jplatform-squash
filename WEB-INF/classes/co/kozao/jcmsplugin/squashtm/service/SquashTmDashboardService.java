package co.kozao.jcmsplugin.squashtm.service;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
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
import java.util.concurrent.ExecutionException;
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
import co.kozao.jcmsplugin.squashtm.model.dashboard.DashboardDataSet;
import co.kozao.jcmsplugin.squashtm.model.dashboard.TestExecution;
import co.kozao.jcmsplugin.squashtm.model.dashboard.TestExecutionRow;
import co.kozao.jcmsplugin.squashtm.model.dashboard.SquashTestItem;
import co.kozao.jcmsplugin.squashtm.model.enumkind.Kind;
import co.kozao.jcmsplugin.squashtm.util.SquashTmConstants;
import co.kozao.jcmsplugin.squashtm.util.SquashTmUtils;

/**
 * @author Severin Kengne
 */
public class SquashTmDashboardService {

	private static final Logger LOGGER = Logger.getLogger(SquashTmDashboardService.class);

	private static final int IO_WORKERS = 12;
	private static final long LOAD_BUDGET_MS = 180000L;
	private static final int DEFAULT_CACHE_MINUTES = 10;
	private static final String UNDEFINED = "UNDEFINED";

	private static final Set<String> PENDING_TESTEXECUTION = new HashSet<String>(Arrays.asList("READY", "RUNNING", "NOT_RUN"));
	private static final Set<String> SUCCESS_TESTEXECUTION  = new HashSet<String>(Arrays.asList("SUCCESS", "SETTLED"));
	private static final Set<String> FAILED_TESTEXECUTION  = new HashSet<String>(Arrays.asList("FAILURE", "ERROR"));

	private static final Cache<String, DashboardDataSet> CACHE = CacheBuilder.newBuilder().maximumSize(60).build();
	private static final ConcurrentMap<String, Object> LOCKS = new ConcurrentHashMap<String, Object>();

	private final SquashTmService service;
	private final String owner;
	private JsonArray projectsMemo;

	/**
	 * @param authorizationHeader en-tête Authorization Squash TM (capturé dans la
	 *                            requête JCMS)
	 * @param owner               identifiant du membre JCMS (clé de cache)
	 */
	public SquashTmDashboardService(String authorizationHeader, String owner) {
		this.service = new SquashTmService(authorizationHeader);
		this.owner = owner == null ? "anonymous" : owner;
	}

	/**
	 * Liste des projets accessibles : [{id, name}].
	 */
	public JsonArray getProjects() throws IOException {

		JsonArray result = new JsonArray();

		for (JsonElement element : allProjects()) {

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

		List<Kind> wanted = new ArrayList<Kind>();

		if (f.wants(DashboardFilter.SCOPE_REQUIREMENTS)) {
			wanted.add(Kind.REQUIREMENTS);
		}
		if (f.wants(DashboardFilter.SCOPE_TEST_CASES)) {
			wanted.add(Kind.TEST_CASES);
		}
		if (f.wants(DashboardFilter.SCOPE_CAMPAIGNS)) {
			wanted.add(Kind.CAMPAIGNS);
		}

		Map<Kind, DashboardDataSet> loaded = loadAll(wanted, f.isRefresh());

		DashboardDataSet requirements = loaded.get(Kind.REQUIREMENTS);
		DashboardDataSet testCases = loaded.get(Kind.TEST_CASES);
		DashboardDataSet campaigns = loaded.get(Kind.CAMPAIGNS);

		JsonObject root = new JsonObject();
		root.addProperty("scope", f.getScope());

		boolean partial = false;
		boolean allFromCache = true;
		long oldest = System.currentTimeMillis();

		for (DashboardDataSet ds : new DashboardDataSet[] { requirements, testCases, campaigns }) {
			if (ds == null) {
				continue;
			}
			partial = partial || ds.isPartial();
			allFromCache = allFromCache && ds.isFromCache();
			oldest = Math.min(oldest, ds.getLoadedAt());
		}

		root.addProperty("partial", partial);
		root.addProperty("fromCache", allFromCache);
		root.addProperty("dataTimestamp", oldest);

		root.add("options", buildOptions(requirements, testCases, campaigns));

		JsonObject kpis = new JsonObject();

		List<SquashTestItem> filteredRequirements = requirements == null ? null
				: filterRequirements(requirements.items, f);
		List<SquashTestItem> filteredTestCases = testCases == null ? null : filterTestCases(testCases.items, f);
		List<SquashTestItem> filteredCampaigns = campaigns == null ? null : filterCampaigns(campaigns.items, f);
		List<TestExecutionRow> filteredExecs = campaigns == null ? null : filterExecs(filteredCampaigns, f);

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
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern(SquashTmConstants.DATE_FORMAT_DASHBOARD_EXPORT)
				.withZone(SquashTmConstants.ZONE_ID);

		if ("requirements".equals(dataset)) {

			csvLine(csv, "ID", "Reference", "Nom", "Projet", "Chemin", "Statut", "Criticite", "Categorie", "Tests lies",
					"Auteur", "Cree le", "Modifie le");

			for (SquashTestItem i : filterRequirements(load(Kind.REQUIREMENTS, f.isRefresh()).items, f)) {
				csvLine(csv, String.valueOf(i.getId()), i.getReference(), i.getName(), i.getProjectName(), i.getPath(),
						i.getStatus(), i.getCriticality(), i.getCategory(), String.valueOf(i.getLinks()), i.getAuthor(),
						fmt(i.getCreatedOn(), formatter), fmt(i.getModifiedOn(), formatter));
			}

		} else if ("testcases".equals(dataset)) {

			csvLine(csv, "ID", "Reference", "Nom", "Projet", "Chemin", "Statut", "Importance", "Nature", "Type",
					"Automatisable", "Exigences liees", "Auteur", "Cree le", "Modifie le");

			for (SquashTestItem i : filterTestCases(load(Kind.TEST_CASES, f.isRefresh()).items, f)) {
				csvLine(csv, String.valueOf(i.getId()), i.getReference(), i.getName(), i.getProjectName(), i.getPath(),
						i.getStatus(), i.getImportance(), i.getNature(), i.getType(), i.getAutomatable(),
						String.valueOf(i.getLinks()), i.getAuthor(), fmt(i.getCreatedOn(), formatter),
						fmt(i.getModifiedOn(), formatter));
			}

		} else if ("campaigns".equals(dataset) || "executions".equals(dataset)) {

			List<SquashTestItem> campaigns = filterCampaigns(load(Kind.CAMPAIGNS, f.isRefresh()).items, f);

			if ("campaigns".equals(dataset)) {

				csvLine(csv, "ID", "Reference", "Nom", "Projet", "Statut", "Iterations", "Executions", "Succes",
						"Echecs", "Bloques", "En attente", "Avancement %", "Succes %", "Debut prevu", "Fin prevue");

				for (SquashTestItem c : campaigns) {
					int[] counts = countExecs(executionsOf(c, f));
					csvLine(csv, String.valueOf(c.getId()), c.getReference(), c.getName(), c.getProjectName(),
							c.getStatus(), String.valueOf(c.getIterations()), String.valueOf(counts[0]),
							String.valueOf(counts[1]), String.valueOf(counts[2]), String.valueOf(counts[3]),
							String.valueOf(counts[4]), String.valueOf(pct(counts[0] - counts[4], counts[0])),
							String.valueOf(pct(counts[1], counts[0])), fmt(c.getScheduledStart(), formatter),
							fmt(c.getScheduledEnd(), formatter));
				}

			} else {

				csvLine(csv, "Campagne", "Projet", "Cas de test (ID)", "Statut", "Execute le", "Assigne a");

				for (TestExecutionRow row : filterExecs(campaigns, f)) {
					csvLine(csv, row.getCampaign().getName(), row.getCampaign().getProjectName(),
							row.getExec().getTestCaseId() > 0 ? String.valueOf(row.getExec().getTestCaseId()) : "",
							row.getExec().getStatus(), fmt(row.getExec().getExecutedOn(), formatter),
							row.getExec().getAssignee());
				}
			}

		} else {
			throw new IOException("Jeu de données d'export inconnu : " + dataset);
		}

		return csv.toString();
	}

	private static boolean isExpired(DashboardDataSet DashboardDataSet) {

		int minutes = DEFAULT_CACHE_MINUTES;

		try {
			minutes = Channel.getChannel().getIntegerProperty(SquashTmConstants.SQUASHTM_DASHBOARD_CACHE_MINUTES,
					DEFAULT_CACHE_MINUTES);
		} catch (Exception e) {
			minutes = DEFAULT_CACHE_MINUTES;
		}

		return System.currentTimeMillis() - DashboardDataSet.getLoadedAt() > TimeUnit.MINUTES
				.toMillis(Math.max(1, minutes));
	}

	private synchronized JsonArray allProjects() throws IOException {

		if (projectsMemo == null) {
			projectsMemo = service.getAllPaged("/projects");
		}

		return projectsMemo;
	}

	/**
	 * Charge plusieurs types de données en parallèle.
	 */
	private Map<Kind, DashboardDataSet> loadAll(List<Kind> kinds, final boolean refresh) throws IOException {

		Map<Kind, DashboardDataSet> result = new EnumMap<Kind, DashboardDataSet>(Kind.class);

		if (kinds.isEmpty()) {
			return result;
		}

		if (kinds.size() == 1) {
			result.put(kinds.get(0), load(kinds.get(0), refresh));
			return result;
		}

		ExecutorService pool = Executors.newFixedThreadPool(kinds.size());

		try {

			Map<Kind, Future<DashboardDataSet>> futures = new EnumMap<Kind, Future<DashboardDataSet>>(Kind.class);

			for (final Kind kind : kinds) {
				futures.put(kind, pool.submit(new Callable<DashboardDataSet>() {
					@Override
					public DashboardDataSet call() throws Exception {
						return load(kind, refresh);
					}
				}));
			}

			for (Map.Entry<Kind, Future<DashboardDataSet>> entry : futures.entrySet()) {

				try {
					result.put(entry.getKey(), entry.getValue().get());

				} catch (ExecutionException e) {

					Throwable cause = e.getCause();

					if (cause instanceof IOException) {
						throw (IOException) cause;
					}

					throw new IOException(cause == null ? e.getMessage() : cause.getMessage(), cause);

				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new IOException("Chargement du dashboard interrompu.", e);
				}
			}

		} finally {
			pool.shutdownNow();
		}

		return result;
	}

	private DashboardDataSet load(Kind kind, boolean refresh) throws IOException {

		long requestedAt = System.currentTimeMillis();

		String key = owner + "|" + kind.name();

		DashboardDataSet cached = CACHE.getIfPresent(key);

		if (!refresh && cached != null && !isExpired(cached)) {
			return cached.asCached();
		}

		Object lock = LOCKS.computeIfAbsent(key, k -> new Object());

		synchronized (lock) {

			cached = CACHE.getIfPresent(key);

			if (!refresh && cached != null && !isExpired(cached)) {
				return cached.asCached();
			}

			if (refresh && cached != null && cached.getLoadedAt() >= requestedAt) {
				return cached;
			}

			DashboardDataSet fresh = doLoad(kind);
			CACHE.put(key, fresh);
			return fresh;
		}
	}

	private DashboardDataSet doLoad(Kind kind) throws IOException {

		long started = System.currentTimeMillis();
		long deadline = started + LOAD_BUDGET_MS;

		JsonArray projects = allProjects();
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

		boolean flatOk = fetchInto(kind.flatPath, kind.fields, 0L, null, raws);

		boolean flatHasProject = flatOk && !raws.isEmpty();

		if (flatHasProject) {
			for (JsonObject raw : raws) {
				if (!hasProjectId(raw)) {
					flatHasProject = false;
					break;
				}
			}
		}

		if (!flatHasProject) {

			LOGGER.warn("Données globales sans rattachement projet pour " + kind + ". Lecture projet par projet.");

			raws.clear();

			collectPerProject(kind, projectNames, raws, deadline);
		}

		long listedAt = System.currentTimeMillis();

		boolean partial = enrich(kind, raws, deadline);

		long enrichedAt = System.currentTimeMillis();

		Map<Long, SquashTestItem> items = new LinkedHashMap<Long, SquashTestItem>();

		for (JsonObject raw : raws) {

			SquashTestItem item = toItem(kind, raw, projectNames);

			if (item != null && !items.containsKey(Long.valueOf(item.getId()))) {
				items.put(Long.valueOf(item.getId()), item);
			}
		}

		List<SquashTestItem> list = new ArrayList<SquashTestItem>(items.values());

		if (kind == Kind.CAMPAIGNS) {
			partial = loadExecutions(list, deadline) || partial;
		}

		long finished = System.currentTimeMillis();

		LOGGER.info("Dashboard Squash TM : " + list.size() + " éléments " + kind + " chargés en " + (finished - started)
				+ " ms [liste = " + (listedAt - started) + " ms, détails = " + (enrichedAt - listedAt)
				+ " ms, exécutions = " + (finished - enrichedAt) + " ms]" + (partial ? " (chargement partiel)" : ""));

		return new DashboardDataSet(list, System.currentTimeMillis(), partial, false);
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

	private boolean hasProjectId(JsonObject raw) {

		JsonObject project = obj(raw, "project");

		if (project != null && longOf(project, "id") > 0) {
			return true;
		}

		return longOf(raw, "__projectId") > 0;
	}

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

		runAll(tasks, IO_WORKERS);

		return partial.get();
	}

	/**
	 * Charge itérations et plans de test de chaque campagne.
	 *
	 * @return true si le délai a été dépassé (données partielles)
	 */
	private boolean loadExecutions(List<SquashTestItem> campaigns, final long deadline) {

		final AtomicBoolean partial = new AtomicBoolean(false);

		final ConcurrentMap<Long, List<Long>> iterationIds = new ConcurrentHashMap<Long, List<Long>>();
		List<Callable<Void>> iterationTasks = new ArrayList<Callable<Void>>();

		for (final SquashTestItem campaign : campaigns) {

			iterationTasks.add(new Callable<Void>() {
				@Override
				public Void call() {

					if (System.currentTimeMillis() > deadline) {
						partial.set(true);
						return null;
					}

					try {

						JsonArray iterations = service.getAllPaged("/campaigns/" + campaign.getId() + "/iterations");
						campaign.setIterations(iterations.size());

						List<Long> ids = new ArrayList<Long>();

						for (JsonElement iterationElement : iterations) {

							if (!iterationElement.isJsonObject()) {
								continue;
							}

							long iterationId = longOf(iterationElement.getAsJsonObject(), "id");

							if (iterationId > 0) {
								ids.add(Long.valueOf(iterationId));
							}
						}

						iterationIds.put(Long.valueOf(campaign.getId()), ids);

					} catch (IOException e) {
						LOGGER.debug("Itérations indisponibles pour la campagne " + campaign.getId() + " : "
								+ e.getMessage());
					}

					return null;
				}
			});
		}

		runAll(iterationTasks, IO_WORKERS);

		List<Callable<Void>> planTasks = new ArrayList<Callable<Void>>();

		for (final SquashTestItem campaign : campaigns) {

			List<Long> ids = iterationIds.get(Long.valueOf(campaign.getId()));

			if (ids == null) {
				continue;
			}

			for (final Long iterationId : ids) {

				planTasks.add(new Callable<Void>() {
					@Override
					public Void call() {

						if (System.currentTimeMillis() > deadline) {
							partial.set(true);
							return null;
						}

						List<TestExecution> found = new ArrayList<TestExecution>();

						try {

							for (JsonElement planElement : service
									.getAllPaged("/iterations/" + iterationId + "/test-plan")) {

								if (!planElement.isJsonObject()) {
									continue;
								}

								JsonObject plan = planElement.getAsJsonObject();
								TestExecution exec = new TestExecution();

								exec.setStatus(
										upper(firstNonEmpty(str(plan, "execution_status"), str(plan, "status"))));
								exec.setExecutedOn(date(plan, "last_executed_on"));
								exec.setAssignee(
										firstNonEmpty(labelOf(plan.get("assigned_to")), labelOf(plan.get("assignee"))));

								JsonObject testCase = obj(plan, "referenced_test_case");
								exec.setTestCaseId(testCase == null ? 0L : longOf(testCase, "id"));

								found.add(exec);
							}

						} catch (IOException e) {
							LOGGER.debug("Plan de test indisponible pour l'itération " + iterationId + " : "
									+ e.getMessage());
						}

						synchronized (campaign.getExecs()) {
							campaign.getExecs().addAll(found);
						}

						return null;
					}
				});
			}
		}

		runAll(planTasks, IO_WORKERS);

		return partial.get();
	}

	private void runAll(List<Callable<Void>> tasks, int workers) {

		ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, Math.min(workers, tasks.size())));

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

	private SquashTestItem toItem(Kind kind, JsonObject o, Map<Long, String> projectNames) {

		long id = longOf(o, "id");

		if (id <= 0) {
			return null;
		}

		SquashTestItem SquashTestItem = new SquashTestItem();
		SquashTestItem.setId(id);

		JsonObject project = obj(o, "project");

		if (project != null && longOf(project, "id") > 0) {
			SquashTestItem.setProjectId(longOf(project, "id"));
			SquashTestItem.setProjectName(str(project, "name"));
		} else {
			SquashTestItem.setProjectId(longOf(o, "__projectId"));
			SquashTestItem.setProjectName(str(o, "__projectName"));
		}

		if (SquashTmUtils.isBlank(SquashTestItem.getProjectName())
				&& projectNames.containsKey(Long.valueOf(SquashTestItem.getProjectId()))) {
			SquashTestItem.setProjectName(projectNames.get(Long.valueOf(SquashTestItem.getProjectId())));
		}

		if (SquashTmUtils.isBlank(SquashTestItem.getProjectName())) {
			SquashTestItem.setProjectName("-");
		}

		SquashTestItem.setPath(str(o, "path"));

		if (kind == Kind.REQUIREMENTS) {

			JsonObject version = obj(o, "current_version");
			JsonObject v = version != null ? version : o;

			SquashTestItem.setName(firstNonEmpty(str(o, "name"), str(v, "name")));
			SquashTestItem.setReference(firstNonEmpty(str(v, "reference"), str(o, "reference")));
			SquashTestItem.setStatus(upper(firstNonEmpty(str(v, "status"), str(o, "status"))));
			SquashTestItem
					.setCriticality(upper(firstNonEmpty(codeOf(v.get("criticality")), codeOf(o.get("criticality")))));
			SquashTestItem.setCategory(upper(firstNonEmpty(codeOf(v.get("category")), codeOf(o.get("category")))));
			SquashTestItem.setAuthor(firstNonEmpty(str(v, "created_by"), str(o, "created_by")));
			SquashTestItem.setCreatedOn(firstDate(date(v, "created_on"), date(o, "created_on")));
			SquashTestItem.setModifiedOn(firstDate(date(v, "last_modified_on"), date(o, "last_modified_on")));
			SquashTestItem.setLinks(Math.max(sizeOf(v, "verifying_test_cases"), sizeOf(o, "verifying_test_cases")));

		} else if (kind == Kind.TEST_CASES) {

			SquashTestItem.setName(str(o, "name"));
			SquashTestItem.setReference(str(o, "reference"));
			SquashTestItem.setStatus(upper(str(o, "status")));
			SquashTestItem.setImportance(upper(str(o, "importance")));
			SquashTestItem.setNature(upper(codeOf(o.get("nature"))));
			SquashTestItem.setType(upper(codeOf(o.get("type"))));
			SquashTestItem.setAutomatable(upper(str(o, "automatable")));
			SquashTestItem.setAuthor(str(o, "created_by"));
			SquashTestItem.setCreatedOn(date(o, "created_on"));
			SquashTestItem.setModifiedOn(date(o, "last_modified_on"));
			SquashTestItem.setLinks(sizeOf(o, "verified_requirements"));

		} else {

			SquashTestItem.setName(str(o, "name"));
			SquashTestItem.setReference(str(o, "reference"));
			SquashTestItem.setStatus(upper(firstNonEmpty(str(o, "status"), str(o, "progress_status"))));
			SquashTestItem.setAuthor(str(o, "created_by"));
			SquashTestItem.setCreatedOn(date(o, "created_on"));
			SquashTestItem.setModifiedOn(date(o, "last_modified_on"));
			SquashTestItem.setScheduledStart(date(o, "scheduled_start_date"));
			SquashTestItem.setScheduledEnd(date(o, "scheduled_end_date"));
			SquashTestItem.setActualStart(date(o, "actual_start_date"));
			SquashTestItem.setActualEnd(date(o, "actual_end_date"));
			SquashTestItem.setIterations(sizeOf(o, "iterations"));
		}

		if (SquashTmUtils.isBlank(SquashTestItem.getName())) {
			SquashTestItem.setName("#" + SquashTestItem.getId());
		}

		return SquashTestItem;
	}

	private boolean matchesCommon(SquashTestItem i, DashboardFilter f) {

		if (!f.getProjectIds().isEmpty() && !f.getProjectIds().contains(Long.valueOf(i.getProjectId()))) {
			return false;
		}

		if (!f.getAuthors().isEmpty() && (i.getAuthor() == null || !f.getAuthors().contains(i.getAuthor()))) {
			return false;
		}

		if (!f.getPath().isEmpty() && lower(i.getPath()).indexOf(f.getPath()) < 0) {
			return false;
		}

		if (!f.getSearch().isEmpty()) {

			String haystack = lower(i.getName()) + "\n" + lower(i.getReference()) + "\n" + lower(i.getPath()) + "\n"
					+ i.getId();

			if (haystack.indexOf(f.getSearch()) < 0) {
				return false;
			}
		}

		return true;
	}

	private static boolean inSet(Set<String> allowed, String value) {
		return allowed.isEmpty() || allowed.contains(value == null ? UNDEFINED : value);
	}

	private static Long dateOf(SquashTestItem i, DashboardFilter f) {
		return DashboardFilter.DATE_MODIFIED.equals(f.getDateField()) ? i.getModifiedOn() : i.getCreatedOn();
	}

	private List<SquashTestItem> filterRequirements(List<SquashTestItem> source, DashboardFilter f) {

		List<SquashTestItem> result = new ArrayList<SquashTestItem>();

		for (SquashTestItem i : source) {

			if (!matchesCommon(i, f) || !f.inPeriod(dateOf(i, f))) {
				continue;
			}

			if (!inSet(f.getRequirementStatuses(), i.getStatus()) || !inSet(f.getCriticalities(), i.getCriticality())
					|| !inSet(f.getCategories(), i.getCategory())) {
				continue;
			}

			if (DashboardFilter.COVERAGE_COVERED.equals(f.getRequirementCoverage()) && i.getLinks() == 0) {
				continue;
			}

			if (DashboardFilter.COVERAGE_UNCOVERED.equals(f.getRequirementCoverage()) && i.getLinks() > 0) {
				continue;
			}

			result.add(i);
		}

		return result;
	}

	private List<SquashTestItem> filterTestCases(List<SquashTestItem> source, DashboardFilter f) {

		List<SquashTestItem> result = new ArrayList<SquashTestItem>();

		for (SquashTestItem i : source) {

			if (!matchesCommon(i, f) || !f.inPeriod(dateOf(i, f))) {
				continue;
			}

			if (!inSet(f.getTestCaseStatuses(), i.getStatus()) || !inSet(f.getImportances(), i.getImportance())
					|| !inSet(f.getNatures(), i.getNature()) || !inSet(f.getTypes(), i.getType())) {
				continue;
			}

			if (!"ALL".equals(f.getAutomatable()) && !f.getAutomatable().equals(i.getAutomatable())) {
				continue;
			}

			if (DashboardFilter.COVERAGE_COVERED.equals(f.getTestCaseCoverage()) && i.getLinks() == 0) {
				continue;
			}

			if (DashboardFilter.COVERAGE_UNCOVERED.equals(f.getTestCaseCoverage()) && i.getLinks() > 0) {
				continue;
			}

			result.add(i);
		}

		return result;
	}

	private List<SquashTestItem> filterCampaigns(List<SquashTestItem> source, DashboardFilter f) {

		List<SquashTestItem> result = new ArrayList<SquashTestItem>();

		for (SquashTestItem i : source) {

			if (!matchesCommon(i, f) || !inSet(f.getCampaignStatuses(), i.getStatus())) {
				continue;
			}

			if (f.hasPeriod() && !campaignActiveInPeriod(i, f)) {
				continue;
			}

			result.add(i);
		}

		return result;
	}

	private boolean campaignActiveInPeriod(SquashTestItem c, DashboardFilter f) {

		if (f.inPeriod(dateOf(c, f))) {
			return true;
		}

		for (TestExecution e : c.getExecs()) {
			if (f.inPeriod(e.getExecutedOn())) {
				return true;
			}
		}

		Long start = c.getActualStart() != null ? c.getActualStart() : c.getScheduledStart();
		Long end = c.getActualEnd() != null ? c.getActualEnd() : c.getScheduledEnd();

		if (start == null && end == null) {
			return false;
		}

		long from = f.getDateFrom() == null ? Long.MIN_VALUE : f.getDateFrom().longValue();
		long to = f.getDateTo() == null ? Long.MAX_VALUE : f.getDateTo().longValue();
		long s = start == null ? Long.MIN_VALUE : start.longValue();
		long e = end == null ? Long.MAX_VALUE : end.longValue();

		return s <= to && e >= from;
	}

	private List<TestExecution> executionsOf(SquashTestItem campaign, DashboardFilter f) {

		List<TestExecution> result = new ArrayList<TestExecution>();

		for (TestExecution e : campaign.getExecs()) {

			if (!inSet(f.getExecutionStatuses(), e.getStatus())) {
				continue;
			}

			if (f.hasPeriod() && !f.inPeriod(e.getExecutedOn())) {
				continue;
			}

			result.add(e);
		}

		return result;
	}

	private List<TestExecutionRow> filterExecs(List<SquashTestItem> campaigns, DashboardFilter f) {

		List<TestExecutionRow> result = new ArrayList<TestExecutionRow>();

		for (SquashTestItem c : campaigns) {
			for (TestExecution e : executionsOf(c, f)) {
				result.add(new TestExecutionRow(c, e));
			}
		}

		return result;
	}

	private JsonObject buildRequirements(List<SquashTestItem> list, DashboardFilter f) {

		JsonObject o = new JsonObject();
		int total = list.size();
		int covered = 0;

		for (SquashTestItem SquashTestItem : list) {
			if (SquashTestItem.getLinks() > 0) {
				covered++;
			}
		}

		o.addProperty("total", total);
		o.addProperty("covered", covered);
		o.addProperty("uncovered", total - covered);
		o.addProperty("coverageRate", pct(covered, total));

		o.add("byStatus", distribution(list, i -> i.getStatus()));
		o.add("byCriticality", distribution(list, i -> i.getCriticality()));
		o.add("byCategory", distribution(list, i -> i.getCategory()));
		o.add("byProject", distribution(list, i -> i.getProjectName()));
		o.add("timeline", timeline(list, f));

		JsonArray rows = new JsonArray();

		for (SquashTestItem SquashTestItem : limit(sortByModified(list), f.getLimit())) {
			JsonObject r = baseRow(SquashTestItem);
			r.addProperty("status", orUndefined(SquashTestItem.getStatus()));
			r.addProperty("criticality", orUndefined(SquashTestItem.getCriticality()));
			r.addProperty("category", orUndefined(SquashTestItem.getCategory()));
			r.addProperty("links", SquashTestItem.getLinks());
			rows.add(r);
		}

		o.add("rows", rows);
		o.addProperty("truncated", total > f.getLimit());

		return o;
	}

	private JsonObject buildTestCases(List<SquashTestItem> list, DashboardFilter f) {

		JsonObject o = new JsonObject();
		int total = list.size();
		int linked = 0;

		for (SquashTestItem SquashTestItem : list) {
			if (SquashTestItem.getLinks() > 0) {
				linked++;
			}
		}

		o.addProperty("total", total);
		o.addProperty("linked", linked);
		o.addProperty("notLinked", total - linked);
		o.addProperty("linkedRate", pct(linked, total));

		o.add("byStatus", distribution(list, i -> i.getStatus()));
		o.add("byImportance", distribution(list, i -> i.getImportance()));
		o.add("byNature", distribution(list, i -> i.getNature()));
		o.add("byType", distribution(list, i -> i.getType()));
		o.add("byAutomation", distribution(list, i -> i.getAutomatable()));
		o.add("byProject", distribution(list, i -> i.getProjectName()));
		o.add("timeline", timeline(list, f));

		JsonArray rows = new JsonArray();

		for (SquashTestItem SquashTestItem : limit(sortByModified(list), f.getLimit())) {
			JsonObject r = baseRow(SquashTestItem);
			r.addProperty("status", orUndefined(SquashTestItem.getStatus()));
			r.addProperty("importance", orUndefined(SquashTestItem.getImportance()));
			r.addProperty("nature", orUndefined(SquashTestItem.getNature()));
			r.addProperty("type", orUndefined(SquashTestItem.getType()));
			r.addProperty("links", SquashTestItem.getLinks());
			rows.add(r);
		}

		o.add("rows", rows);
		o.addProperty("truncated", total > f.getLimit());

		return o;
	}

	private JsonObject buildCampaigns(List<SquashTestItem> campaigns, List<TestExecutionRow> execs,
			final DashboardFilter f) {

		JsonObject o = new JsonObject();

		Map<String, Integer> byStatus = new LinkedHashMap<String, Integer>();
		List<TestExecution> flat = new ArrayList<TestExecution>();

		for (TestExecutionRow row : execs) {
			add(byStatus, orUndefined(row.getExec().getStatus()));
			flat.add(row.getExec());
		}

		int[] totals = countExecs(flat);

		o.addProperty("total", campaigns.size());
		o.addProperty("executionTotal", totals[0]);
		o.addProperty("successRate", pct(totals[1], totals[0]));
		o.addProperty("progressRate", pct(totals[0] - totals[4], totals[0]));

		o.add("byStatus", distribution(campaigns, i -> i.getStatus()));
		o.add("byProject", distribution(campaigns, i -> i.getProjectName()));
		o.add("executionsByStatus", toDistribution(byStatus));
		o.add("executionsByProject", distributionOfExecs(execs));
		o.add("executionTimeline", executionTimeline(execs, f));

		JsonArray rows = new JsonArray();
		List<SquashTestItem> sorted = new ArrayList<SquashTestItem>(campaigns);

		Collections.sort(sorted, new Comparator<SquashTestItem>() {
			@Override
			public int compare(SquashTestItem a, SquashTestItem b) {
				return Long.compare(lastExecution(b, f), lastExecution(a, f));
			}
		});

		for (SquashTestItem c : limit(sorted, f.getLimit())) {

			List<TestExecution> own = executionsOf(c, f);
			int[] counts = countExecs(own);
			JsonObject r = baseRow(c);

			r.addProperty("status", orUndefined(c.getStatus()));
			r.addProperty("iterations", c.getIterations());
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
			if (c.getScheduledStart() != null) {
				r.addProperty("scheduledStart", c.getScheduledStart());
			}
			if (c.getScheduledEnd() != null) {
				r.addProperty("scheduledEnd", c.getScheduledEnd());
			}

			rows.add(r);
		}

		o.add("rows", rows);
		o.addProperty("truncated", campaigns.size() > f.getLimit());

		return o;
	}

	private JsonObject buildOptions(DashboardDataSet requirements, DashboardDataSet testCases,
			DashboardDataSet campaigns) {

		JsonObject o = new JsonObject();
		Set<String> authors = new TreeSet<String>();

		if (requirements != null) {
			o.add("requirementStatuses", distinct(requirements.items, i -> i.getStatus()));
			o.add("criticalities", distinct(requirements.items, i -> i.getCriticality()));
			o.add("categories", distinct(requirements.items, i -> i.getCategory()));
			collectAuthors(requirements.items, authors);
		}

		if (testCases != null) {
			o.add("testCaseStatuses", distinct(testCases.items, i -> i.getStatus()));
			o.add("importances", distinct(testCases.items, i -> i.getImportance()));
			o.add("natures", distinct(testCases.items, i -> i.getNature()));
			o.add("types", distinct(testCases.items, i -> i.getType()));
			collectAuthors(testCases.items, authors);
		}

		if (campaigns != null) {

			o.add("campaignStatuses", distinct(campaigns.items, i -> i.getStatus()));
			collectAuthors(campaigns.items, authors);

			Set<String> execStatuses = new TreeSet<String>();
			for (SquashTestItem c : campaigns.items) {
				for (TestExecution e : c.getExecs()) {
					execStatuses.add(orUndefined(e.getStatus()));
				}
			}

			JsonArray array = new JsonArray();
			for (String s : execStatuses) {
				array.add(s);
			}
			o.add("executionStatuses", array);
		}

		JsonArray authorArray = new JsonArray();
		for (String a : authors) {
			authorArray.add(a);
		}
		o.add("authors", authorArray);

		return o;
	}

	private void collectAuthors(List<SquashTestItem> items, Set<String> authors) {
		for (SquashTestItem i : items) {
			if (!SquashTmUtils.isBlank(i.getAuthor())) {
				authors.add(i.getAuthor());
			}
		}
	}

	/**
	 * Nombre de cas de test (filtrés) qui n'apparaissent dans aucun plan de test.
	 * Retourne null si aucune exécution ne référence de cas de test.
	 */
	private Integer countNeverExecuted(List<SquashTestItem> testCases, List<SquashTestItem> allCampaigns) {

		Set<Long> executed = new HashSet<Long>();

		for (SquashTestItem c : allCampaigns) {
			for (TestExecution e : c.getExecs()) {
				if (e.getTestCaseId() > 0) {
					executed.add(Long.valueOf(e.getTestCaseId()));
				}
			}
		}

		if (executed.isEmpty()) {
			return null;
		}

		int never = 0;

		for (SquashTestItem tc : testCases) {
			if (!executed.contains(Long.valueOf(tc.getId()))) {
				never++;
			}
		}

		return Integer.valueOf(never);
	}

	/**
	 * @return [total, succès, échecs, bloqués, en attente]
	 */
	private static int[] countExecs(List<TestExecution> execs) {

		int[] counts = new int[5];

		for (TestExecution e : execs) {

			String status = orUndefined(e.getStatus());

			counts[0]++;

			if (SUCCESS_TESTEXECUTION.contains(status)) {
				counts[1]++;
			} else if (FAILED_TESTEXECUTION.contains(status)) {
				counts[2]++;
			} else if ("BLOCKED".equals(status)) {
				counts[3]++;
			} else if (PENDING_TESTEXECUTION.contains(status)) {
				counts[4]++;
			}
		}

		return counts;
	}

	private static long lastExecution(SquashTestItem campaign, DashboardFilter f) {

		long last = 0L;

		for (TestExecution e : campaign.getExecs()) {
			if (e.getExecutedOn() != null && f.inPeriod(e.getExecutedOn())) {
				last = Math.max(last, e.getExecutedOn().longValue());
			}
		}

		return last;
	}

	private JsonArray distribution(List<SquashTestItem> list, Function<SquashTestItem, String> key) {

		Map<String, Integer> counts = new LinkedHashMap<String, Integer>();

		for (SquashTestItem SquashTestItem : list) {
			add(counts, orUndefined(key.apply(SquashTestItem)));
		}

		return toDistribution(counts);
	}

	private JsonArray distributionOfExecs(List<TestExecutionRow> execs) {

		Map<String, Integer> counts = new LinkedHashMap<String, Integer>();

		for (TestExecutionRow row : execs) {
			add(counts, orUndefined(row.getCampaign().getProjectName()));
		}

		return toDistribution(counts);
	}

	private JsonArray distinct(List<SquashTestItem> list, Function<SquashTestItem, String> key) {

		Set<String> values = new TreeSet<String>();

		for (SquashTestItem item : list) {
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

	private JsonObject timeline(List<SquashTestItem> list, DashboardFilter f) {

		List<Long> stamps = new ArrayList<Long>();

		for (SquashTestItem item : list) {
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

	private JsonObject executionTimeline(List<TestExecutionRow> execs, DashboardFilter f) {

		List<Long> stamps = new ArrayList<Long>();

		for (TestExecutionRow row : execs) {
			if (row.getExec().getExecutedOn() != null) {
				stamps.add(row.getExec().getExecutedOn());
			}
		}

		String group = resolveGroup(f, stamps);
		Map<String, int[]> buckets = new TreeMap<String, int[]>();

		for (TestExecutionRow row : execs) {

			if (row.getExec().getExecutedOn() == null) {
				continue;
			}

			String key = bucketKey(row.getExec().getExecutedOn().longValue(), group);
			int[] values = buckets.get(key);

			if (values == null) {
				values = new int[3];
				buckets.put(key, values);
			}

			String status = orUndefined(row.getExec().getStatus());

			values[0]++;

			if (SUCCESS_TESTEXECUTION.contains(status)) {
				values[1]++;
			} else if (FAILED_TESTEXECUTION.contains(status)) {
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

		LocalDate day = Instant.ofEpochMilli(timestamp).atZone(SquashTmConstants.ZONE_ID).toLocalDate();

		if (DashboardFilter.GROUP_DAY.equals(group)) {
			return day.toString();
		}

		if (DashboardFilter.GROUP_WEEK.equals(group)) {
			return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();
		}

		return String.format(Locale.ROOT, "%04d-%02d", day.getYear(), day.getMonthValue());
	}

	private static JsonObject baseRow(SquashTestItem i) {

		JsonObject r = new JsonObject();
		r.addProperty("id", i.getId());
		r.addProperty("reference", i.getReference() == null ? "" : i.getReference());
		r.addProperty("name", i.getName());
		r.addProperty("project", i.getProjectName());
		r.addProperty("path", i.getPath() == null ? "" : i.getPath());

		if (i.getCreatedOn() != null) {
			r.addProperty("createdOn", i.getCreatedOn());
		}
		if (i.getModifiedOn() != null) {
			r.addProperty("modifiedOn", i.getModifiedOn());
		}

		return r;
	}

	private static List<SquashTestItem> sortByModified(List<SquashTestItem> list) {

		List<SquashTestItem> sorted = new ArrayList<SquashTestItem>(list);

		Collections.sort(sorted, new Comparator<SquashTestItem>() {
			@Override
			public int compare(SquashTestItem a, SquashTestItem b) {
				long ta = a.getModifiedOn() != null ? a.getModifiedOn().longValue()
						: (a.getCreatedOn() != null ? a.getCreatedOn().longValue() : 0L);
				long tb = b.getModifiedOn() != null ? b.getModifiedOn().longValue()
						: (b.getCreatedOn() != null ? b.getCreatedOn().longValue() : 0L);
				return Long.compare(tb, ta);
			}
		});

		return sorted;
	}

	private static <T> List<T> limit(List<T> list, int max) {
		return list.size() <= max ? list : list.subList(0, max);
	}

	private static void csvLine(StringBuilder csv, String... cells) {

		for (int i = 0; i < cells.length; i++) {

			if (i > 0) {
				csv.append(';');
			}

			String value = cells[i] == null ? "" : cells[i];

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

	private static double pct(int part, int total) {

		if (total <= 0) {
			return 0d;
		}

		return Math.round(1000.0d * part / total) / 10.0d;
	}

	private static String lower(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT);
	}

	private static String upper(String value) {
		return SquashTmUtils.isBlank(value) ? null : value.trim().toUpperCase(Locale.ROOT);
	}

	private static String orUndefined(String value) {
		return SquashTmUtils.isBlank(value) ? UNDEFINED : value;
	}

	private static String firstNonEmpty(String a, String b) {
		return !SquashTmUtils.isBlank(a) ? a : b;
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

		if (SquashTmUtils.isBlank(value)) {
			return null;
		}

		String s = value.trim();

		try {
			return Long.valueOf(OffsetDateTime.parse(s).toInstant().toEpochMilli());
		} catch (DateTimeParseException e) {

		}

		try {
			return Long.valueOf(LocalDateTime.parse(s.length() > 19 ? s.substring(0, 19) : s)
					.atZone(SquashTmConstants.ZONE_ID).toInstant().toEpochMilli());
		} catch (DateTimeParseException e) {

		}

		try {
			return Long.valueOf(LocalDate.parse(s.substring(0, Math.min(10, s.length())))
					.atStartOfDay(SquashTmConstants.ZONE_ID).toInstant().toEpochMilli());
		} catch (DateTimeParseException e) {
			return null;
		}
	}
}