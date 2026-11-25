package co.kozao.jcmsplugin.squashtm.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jalios.jcms.Member;
import com.jalios.util.Util;

import co.kozao.jcmsplugin.squashtm.model.AuthentificationMode;
import co.kozao.jcmsplugin.squashtm.model.api.LoggedUserInfo;
import co.kozao.jcmsplugin.squashtm.util.SquashTmUtils;

/**
 * @author Severin Kengne
 */
public class SquashTmService {

	private static final Logger LOGGER = Logger.getLogger(SquashTmService.class);

	private static final int DASHBOARD_PAGE_SIZE = 200;
	private static final int DASHBOARD_MAX_PAGES = 500;
	private static final int CONNECT_TIMEOUT = 5000;
	private static final int READ_TIMEOUT = 30000;
	private final String authorizationHeader;

	
	public SquashTmService(String authorizationHeader) {
		this.authorizationHeader = authorizationHeader;
	}

	public SquashTmService() {
		this.authorizationHeader = null;
	}

	
	public LoggedUserInfo authenticate(Member member) {

		if (member == null) {
			LOGGER.warn("Member JCMS null.");
			return null;
		}

		AuthentificationMode mode = SquashTmUtils.getAuthentificationMode();
		LOGGER.debug("========== MODE = " + mode + " ==========");

		if (mode == AuthentificationMode.BASIC) {
			return authenticateWithBasic(member);
		}

		return authenticateWithToken(member);
	}

	private LoggedUserInfo authenticateWithToken(Member member) {

		String token = SquashTmUtils.getSquashTmApiToken(member);

		if (Util.isEmpty(token)) {
			LOGGER.warn("Aucun token Squash TM trouvé.");
			return null;
		}

		String baseUrl = SquashTmUtils.getSquashTmServerUrl();

		if (Util.isEmpty(baseUrl)) {
			LOGGER.warn("URL Squash TM non configurée.");
			return null;
		}

		try {

			String tokenUrl = baseUrl + "/api/rest/latest/tokens";

			LOGGER.debug("baseUrl = [" + baseUrl + "]");
			LOGGER.debug("tokenUrl = [" + tokenUrl + "]");

			String tokensJson = executeGet(tokenUrl, "Bearer " + token);

			if (Util.isEmpty(tokensJson)) {

				LOGGER.warn("Réponse vide lors de la validation du token.");

				return null;
			}

			JsonObject root = JsonParser.parseString(tokensJson).getAsJsonObject();

			if (!root.has("_embedded")) {

				LOGGER.warn("La réponse /api/rest/latest/tokens ne contient pas _embedded.");

				return null;
			}

			JsonObject embedded = root.getAsJsonObject("_embedded");

			if (!embedded.has("api-tokens")) {

				LOGGER.warn("La réponse /api/rest/latest/tokens ne contient pas api-tokens.");

				return null;
			}

			JsonArray tokens = embedded.getAsJsonArray("api-tokens");

			if (tokens == null || tokens.size() == 0) {

				LOGGER.warn("Aucun token Squash TM trouvé.");

				return null;
			}

			JsonObject tokenObject = tokens.get(0).getAsJsonObject();

			if (!tokenObject.has("user")) {

				LOGGER.warn("Le token ne contient aucune référence utilisateur.");

				return null;
			}

			JsonObject userReference = tokenObject.getAsJsonObject("user");

			if (!userReference.has("id")) {

				LOGGER.warn("La référence utilisateur ne contient pas d'id.");

				return null;
			}

			String userId = userReference.get("id").getAsString();

			LOGGER.debug("userId = " + userId);

			String userUrl = baseUrl + "/api/rest/latest/users/" + userId;

			LOGGER.debug("userUrl = " + userUrl);

			String userJson = executeGet(userUrl, "Bearer " + token);
			LOGGER.error("========== REPONSE /api/rest/latest/users/{id} ==========");

			LoggedUserInfo info = null;

			if (Util.notEmpty(userJson)) {

				info = buildLoggedUserInfo(userJson);
			}

			if (info == null) {

				LOGGER.warn("Impossible de construire LoggedUserInfo depuis /api/rest/latest/users/" + userId);

				info = new LoggedUserInfo();
			}

			if (Util.isEmpty(info.getId())) {

				info.setId(userId);
			}

			LOGGER.info("===== UTILISATEUR SQUASH TM AUTHENTIFIE =====");

			LOGGER.info("ID Squash TM    : " + info.getId());

			LOGGER.info("Nom Squash TM   : " + info.getFullName());

			LOGGER.info("Email Squash TM : " + info.getEmailAddr());

			LOGGER.info("Date création   : " + info.getCreatedAt());

			return info;

		} catch (Exception e) {

			LOGGER.error("Erreur pendant l'authentification TOKEN.", e);

			return null;
		}
	}

	/**
	 * Authentification Basic.
	 *
	 * Le Member JCMS sert uniquement à récupérer les identifiants Squash TM de
	 * l'utilisateur courant.
	 *
	 * Les informations finales de l'utilisateur proviennent exclusivement de Squash
	 * TM.
	 */
	private LoggedUserInfo authenticateWithBasic(Member member) {

		String username = SquashTmUtils.getSquashTmUsername(member);
		String password = SquashTmUtils.getSquashTmPassword(member);

		LOGGER.debug("Password présente ? " + Util.notEmpty(password));

		if (Util.isEmpty(username) || Util.isEmpty(password)) {

			LOGGER.warn("Username ou password Squash TM absent.");

			return null;
		}

		String baseUrl = SquashTmUtils.getSquashTmServerUrl();

		LOGGER.debug("Base URL Squash TM = [" + baseUrl + "]");

		if (Util.isEmpty(baseUrl)) {

			LOGGER.warn("URL Squash TM non configurée.");

			return null;
		}

		try {

			String credentials = username + ":" + password;

			String encodedCredentials = Base64.getEncoder()
					.encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

			String authorization = "Basic " + encodedCredentials;

			String usersUrl = baseUrl + "/api/rest/latest/users?size=1000";

			LOGGER.debug("usersUrl = [" + usersUrl + "]");

			String usersJson = executeGet(usersUrl, authorization);

			if (Util.isEmpty(usersJson)) {

				LOGGER.warn("Squash TM n'a retourné aucune donnée.");

				return null;
			}

			JsonObject root = JsonParser.parseString(usersJson).getAsJsonObject();

			if (!root.has("_embedded") || root.get("_embedded").isJsonNull()) {

				LOGGER.warn("La réponse Squash TM ne contient pas _embedded.");

				return null;
			}

			JsonObject embedded = root.getAsJsonObject("_embedded");

			if (!embedded.has("users") || embedded.get("users").isJsonNull()) {

				LOGGER.warn("La réponse Squash TM ne contient pas users.");

				return null;
			}

			JsonArray users = embedded.getAsJsonArray("users");

			LOGGER.debug("Nombre d'utilisateurs reçus = " + users.size());

			String userId = null;

			for (JsonElement element : users) {

				if (element == null || !element.isJsonObject()) {
					continue;
				}

				JsonObject user = element.getAsJsonObject();

				String login = getString(user, "login");

				if (Util.isEmpty(login)) {
					continue;
				}

				if (username.equalsIgnoreCase(login)) {

					userId = getString(user, "id");

					break;
				}
			}

			if (Util.isEmpty(userId)) {

				LOGGER.warn("Utilisateur Squash TM introuvable : " + username);

				return null;
			}

			String userUrl = baseUrl + "/api/rest/latest/users/" + userId;

			LOGGER.debug("userUrl = [" + userUrl + "]");

			String userJson = executeGet(userUrl, authorization);

			if (Util.isEmpty(userJson)) {

				LOGGER.warn("Impossible de récupérer le profil complet " + "de l'utilisateur Squash TM : " + userId);

				return null;
			}

			LoggedUserInfo info = buildLoggedUserInfo(userJson);

			if (info == null) {

				LOGGER.warn("Impossible de construire LoggedUserInfo " + "pour l'utilisateur : " + userId);

				return null;
			}

			LOGGER.info("===== UTILISATEUR SQUASH TM AUTHENTIFIE EN BASIC =====");

			LOGGER.info("ID Squash TM    : " + info.getId());

			LOGGER.info("Nom Squash TM   : " + info.getFullName());

			LOGGER.info("Email Squash TM : " + info.getEmailAddr());

			LOGGER.info("Date création   : " + info.getCreatedAt());

			return info;

		} catch (Exception e) {

			LOGGER.error("Erreur pendant l'authentification BASIC Squash TM.", e);

			return null;
		}
	}

	/**
	 * Effectue une requête GET authentifiée.
	 */
	private String executeGet(String urlString, String authorization) throws IOException {

		LOGGER.debug("URL reçue = [" + urlString + "]");
		LOGGER.debug("Authorization présente ? " + Util.notEmpty(authorization));

		if (Util.isEmpty(urlString)) {
			return null;
		}

		try {

			URI uri = URI.create(urlString);

			LOGGER.debug("URI créée = [" + uri + "]");
			LOGGER.debug("URI absolue ? " + uri.isAbsolute());

			URL url = uri.toURL();

			LOGGER.debug("URL finale = [" + url + "]");

			HttpURLConnection connection = (HttpURLConnection) url.openConnection();

			try {

				connection.setRequestMethod("GET");
				connection.setUseCaches(false);

				connection.setRequestProperty("Authorization", authorization);
				connection.setRequestProperty("Accept", "application/json");

				connection.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate");
				connection.setRequestProperty("Pragma", "no-cache");
				connection.setRequestProperty("Expires", "0");

				connection.setConnectTimeout(CONNECT_TIMEOUT);
				connection.setReadTimeout(READ_TIMEOUT);

				int responseCode = connection.getResponseCode();

				LOGGER.debug("Réponse Squash TM HTTP = " + responseCode);

				if (responseCode != HttpURLConnection.HTTP_OK) {

					LOGGER.warn("Squash TM a refusé la requête. HTTP = " + responseCode + " | URL = " + urlString);

					return null;
				}

				StringBuilder response = new StringBuilder();

				try (BufferedReader reader = new BufferedReader(
						new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {

					String line;

					while ((line = reader.readLine()) != null) {
						response.append(line);
					}
				}

				return response.toString();

			} finally {

				connection.disconnect();
			}

		} catch (IllegalArgumentException e) {

			LOGGER.error("URL reçue par executeGet() = [" + urlString + "]", e);

			throw e;
		}
	}

	
	private LoggedUserInfo buildLoggedUserInfo(String json) {

		try {

			JsonObject user = JsonParser.parseString(json).getAsJsonObject();

			return buildLoggedUserInfo(user);

		} catch (Exception e) {

			LOGGER.error("Impossible de construire LoggedUserInfo.", e);

			return null;
		}
	}

	/**
	 * Construit LoggedUserInfo depuis un JsonObject.
	 */
	private LoggedUserInfo buildLoggedUserInfo(JsonObject user) {
		LOGGER.info("JSON utilisateur Squash TM : " + user);

		if (user == null) {
			return null;
		}

		LoggedUserInfo info = new LoggedUserInfo();

		if (user.has("id")) {

			info.setId(user.get("id").getAsString());
		}

		String firstName = getString(user, "first_name");

		String lastName = getString(user, "last_name");

		String fullName = ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "")).trim();

		info.setFullName(fullName);

		String email = getString(user, "email");

		info.setEmailAddr(email);

		String createdOn = getString(user, "created_on");

		info.setCreatedAt(createdOn);

		LOGGER.info("Utilisateur Squash TM récupéré : " + info.getFullName());

		return info;
	}

	
	private String getString(JsonObject object, String property) {

		if (object == null || !object.has(property) || object.get(property).isJsonNull()) {

			return null;
		}

		return object.get(property).getAsString();
	}

	
	public boolean testConnection(Member member) {

		return authenticate(member) != null;
	}

	
	public void deleteRequirementFolders(String folderIds) throws IOException {

		if (Util.isEmpty(folderIds)) {
			throw new IOException("Aucun identifiant de dossier d'exigences à supprimer.");
		}

		String baseUrl = SquashTmUtils.getSquashTmServerUrl();

		if (Util.isEmpty(baseUrl)) {
			throw new IOException("L'URL de Squash TM n'est pas configurée.");
		}

		String authorization = getAuthorization();

		String url = baseUrl + "/api/rest/latest/requirement-folders/" + folderIds;

		executeDelete(url, authorization);

		LOGGER.info("Dossiers d'exigences supprimés : " + folderIds);
	}

	
	public void deleteTestCaseFolders(String folderIds) throws IOException {

		if (Util.isEmpty(folderIds)) {
			throw new IOException("Aucun identifiant de dossier de cas de test à supprimer.");
		}

		String baseUrl = SquashTmUtils.getSquashTmServerUrl();

		if (Util.isEmpty(baseUrl)) {
			throw new IOException("L'URL de Squash TM n'est pas configurée.");
		}

		String authorization = getAuthorization();

		String url = baseUrl + "/api/rest/latest/test-case-folders/" + folderIds;

		executeDelete(url, authorization);

		LOGGER.info("Dossiers de cas de test supprimés : " + folderIds);
	}

	/**
	 * Construit l'en-tête Authorization utilisé pour les appels à l'API REST de
	 * Squash TM.
	 * 
	 * @return valeur de l'en-tête Authorization
	 * @throws IOException si les credentials Squash TM sont absents
	 */
	private String getAuthorization() throws IOException {
		if (Util.notEmpty(authorizationHeader)) {
			return authorizationHeader;
		}

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
	 * Récupère les projets accessibles sur Squash TM.
	 *
	 * @return tableau JSON contenant les projets
	 * @throws Exception
	 */
	public JsonArray getProjects() throws IOException {
		String baseUrl = SquashTmUtils.getSquashTmServerUrl();

		if (baseUrl == null || baseUrl.trim().isEmpty()) {
			throw new IOException("L'URL de Squash TM n'est pas configurée.");
		}

		String authorization = getAuthorization();
		String url = baseUrl + "/api/rest/latest/projects";
		String response = executeGet(url, authorization);

		if (Util.isEmpty(response)) {
			throw new IOException("Squash TM n'a retourné aucune réponse lors de la récupération des projets.");
		}

		JsonObject json;

		try {
			json = JsonParser.parseString(response).getAsJsonObject();

		} catch (Exception e) {
			throw new IOException("La réponse de Squash TM pour les projets n'est pas un JSON valide.", e);
		}
		if (!json.has("_embedded")) {
			return new JsonArray();
		}

		JsonObject embedded = json.getAsJsonObject("_embedded");
		if (!embedded.has("projects")) {
			return new JsonArray();
		}

		return embedded.getAsJsonArray("projects");
	}

	/**
	 * Recherche un projet Squash TM par son nom exact.
	 *
	 * @param projectName nom du projet recherché
	 * @return projet trouvé ou null
	 * @throws Exception
	 */
	public JsonObject findProjectByName(String projectName) throws IOException {

		if (projectName == null || projectName.trim().isEmpty()) {
			return null;
		}

		JsonArray projects = getProjects();
		for (JsonElement element : projects) {

			if (!element.isJsonObject()) {
				continue;
			}

			JsonObject project = element.getAsJsonObject();
			if (!project.has("name") || project.get("name").isJsonNull()) {

				continue;
			}

			String name = project.get("name").getAsString();
			if (projectName.trim().equals(name.trim())) {

				return project;
			}
		}

		return null;
	}

	/**
	 * Exécute une requête HTTP POST avec un corps JSON.
	 *
	 * @param urlString     URL de l'API Squash TM
	 * @param authorization en-tête Authorization
	 * @param jsonBody      corps JSON de la requête
	 * @return réponse JSON de Squash TM
	 * @throws IOException en cas d'erreur HTTP ou réseau
	 */

	private String executePost(String urlString, String authorization, String jsonBody) throws IOException {

		HttpURLConnection connection = null;
		long startTime = System.currentTimeMillis();

		try {
			URL url = URI.create(urlString).toURL();
			connection = (HttpURLConnection) url.openConnection();

			connection.setRequestMethod("POST");
			connection.setConnectTimeout(CONNECT_TIMEOUT);
			connection.setReadTimeout(READ_TIMEOUT);

			connection.setRequestProperty("Accept", "application/json");
			connection.setRequestProperty("Content-Type", "application/json");

			if (Util.notEmpty(authorization)) {
				connection.setRequestProperty("Authorization", authorization);
			}

			connection.setDoOutput(true);

			byte[] body = jsonBody.getBytes(StandardCharsets.UTF_8);
			connection.setFixedLengthStreamingMode(body.length);

			
			try (java.io.OutputStream output = connection.getOutputStream()) {
				output.write(body);
			}

			LOGGER.info("POST Squash TM : " + urlString);

			int responseCode = connection.getResponseCode();

			long elapsedTime = System.currentTimeMillis() - startTime;

			LOGGER.info("Réponse Squash TM HTTP POST = " + responseCode + " | durée = " + elapsedTime + " ms"
					+ " | URL = " + urlString);

			if (responseCode < 200 || responseCode >= 300) {

				StringBuilder errorResponse = new StringBuilder();

				java.io.InputStream errorStream = connection.getErrorStream();

				if (errorStream != null) {
					try (BufferedReader reader = new BufferedReader(
							new InputStreamReader(errorStream, StandardCharsets.UTF_8))) {

						String line;

						while ((line = reader.readLine()) != null) {
							errorResponse.append(line);
						}
					}
				}

				LOGGER.error("Erreur POST Squash TM" + " | HTTP = " + responseCode + " | URL = " + urlString
						+ " | Réponse = " + errorResponse);
				throw new IOException("Squash TM a refusé la requête POST." + " HTTP = " + responseCode
						+ " | Réponse = " + errorResponse);
			}

			StringBuilder response = new StringBuilder();

			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {

				String line;

				while ((line = reader.readLine()) != null) {
					response.append(line);
				}
			}

			return response.toString();

		} catch (java.net.SocketTimeoutException e) {

			long elapsedTime = System.currentTimeMillis() - startTime;

			LOGGER.error("Délai d'attente dépassé pendant le POST Squash TM" + " | durée = " + elapsedTime + " ms"
					+ " | URL = " + urlString, e);

			throw e;

		} finally {

			if (connection != null) {
				connection.disconnect();
			}
		}
	}

	/**
	 * Exécute une requête HTTP PATCH avec un corps JSON.
	 *
	 * Utilise le client HTTP standard de Java 17, qui prend en charge PATCH.
	 *
	 * @param urlString     URL de l'API Squash TM
	 * @param authorization en-tête Authorization
	 * @param jsonBody      corps JSON de la requête
	 * @return réponse de Squash TM, éventuellement vide
	 * @throws IOException en cas d'erreur HTTP ou réseau
	 */
	private String executePatch(String urlString, String authorization, String jsonBody) throws IOException {

		if (Util.isEmpty(urlString)) {
			throw new IOException("L'URL de la requête PATCH est vide.");
		}

		try {

			HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(CONNECT_TIMEOUT)).build();

			HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(URI.create(urlString))
					.timeout(Duration.ofMillis(READ_TIMEOUT)).header("Accept", "application/json")
					.header("Content-Type", "application/json").method("PATCH", HttpRequest.BodyPublishers
							.ofString(jsonBody != null ? jsonBody : "", StandardCharsets.UTF_8));

			if (Util.notEmpty(authorization)) {
				requestBuilder.header("Authorization", authorization);
			}

			HttpRequest request = requestBuilder.build();

			long startTime = System.currentTimeMillis();

			HttpResponse<String> response = client.send(request,
					HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

			long elapsedTime = System.currentTimeMillis() - startTime;

			int responseCode = response.statusCode();

			LOGGER.info("PATCH Squash TM : HTTP " + responseCode + " | durée = " + elapsedTime + " ms" + " | URL = "
					+ urlString);

			if (responseCode < 200 || responseCode >= 300) {

				throw new IOException("Squash TM a refusé la requête PATCH." + " HTTP = " + responseCode
						+ " | Réponse = " + response.body());
			}

			return response.body() != null ? response.body() : "";

		} catch (InterruptedException e) {

			Thread.currentThread().interrupt();

			throw new IOException("La requête PATCH vers Squash TM a été interrompue.", e);

		} catch (IllegalArgumentException e) {

			throw new IOException("URL invalide pour la requête PATCH : " + urlString, e);
		}
	}

	/**
	 * Exécute une requête HTTP DELETE.
	 *
	 * @param urlString     URL de l'API Squash TM
	 * @param authorization en-tête Authorization
	 * @throws IOException en cas d'erreur HTTP ou réseau
	 */
	private void executeDelete(String urlString, String authorization) throws IOException {

		HttpURLConnection connection = null;

		try {
			URL url = URI.create(urlString).toURL();
			connection = (HttpURLConnection) url.openConnection();

			connection.setRequestMethod("DELETE");
			connection.setConnectTimeout(CONNECT_TIMEOUT);
			connection.setReadTimeout(READ_TIMEOUT);

			connection.setRequestProperty("Accept", "application/json");

			if (Util.notEmpty(authorization)) {
				connection.setRequestProperty("Authorization", authorization);
			}

			int responseCode = connection.getResponseCode();

			LOGGER.info("DELETE Squash TM : HTTP " + responseCode + " | URL : " + urlString);

			if (responseCode < 200 || responseCode >= 300) {

				StringBuilder errorResponse = new StringBuilder();

				java.io.InputStream errorStream = connection.getErrorStream();

				if (errorStream != null) {
					try (BufferedReader reader = new BufferedReader(
							new InputStreamReader(errorStream, StandardCharsets.UTF_8))) {

						String line;

						while ((line = reader.readLine()) != null) {
							errorResponse.append(line);
						}
					}
				}

				throw new IOException("Squash TM a refusé la requête DELETE. HTTP = " + responseCode + " | Réponse = "
						+ errorResponse);
			}

		} finally {

			if (connection != null) {
				connection.disconnect();
			}
		}
	}

	/**
	 * Crée un nouveau projet dans Squash TM.
	 *
	 * @param projectName nom du projet
	 * @return projet créé
	 * @throws IOException en cas d'erreur de communication ou de création du projet
	 */
	public JsonObject createProject(String projectName) throws IOException {

		if (projectName == null || projectName.trim().isEmpty()) {
			throw new IOException("Le nom du projet Squash TM est vide.");
		}

		String baseUrl = SquashTmUtils.getSquashTmServerUrl();

		if (Util.isEmpty(baseUrl)) {
			throw new IOException("L'URL de Squash TM n'est pas configurée.");
		}

		String authorization = getAuthorization();

		JsonObject project = new JsonObject();

		project.addProperty("_type", "project");

		project.addProperty("name", projectName.trim());

		project.addProperty("label", projectName.trim());

		String url = baseUrl + "/api/rest/latest/projects";

		String response = executePost(url, authorization, project.toString());

		if (Util.isEmpty(response)) {
			throw new IOException("Squash TM a créé le projet mais n'a retourné aucune réponse.");
		}

		JsonObject createdProject;

		try {
			createdProject = JsonParser.parseString(response).getAsJsonObject();

		} catch (Exception e) {

			throw new IOException("La réponse de création du projet Squash TM n'est pas un JSON valide.", e);
		}

		if (!createdProject.has("id") || createdProject.get("id").isJsonNull()) {

			throw new IOException("Le projet Squash TM a été créé mais aucun identifiant n'a été retourné.");
		}

		LOGGER.info("Projet Squash TM créé : " + projectName + " (id=" + createdProject.get("id").getAsString() + ")");

		return createdProject;
	}

	/**
	 * Crée un dossier dans l'espace des exigences Squash TM.
	 *
	 * @param name       nom du dossier
	 * @param parentType type du parent
	 * @param parentId   identifiant du parent
	 * @return dossier créé
	 * @throws IOException en cas d'erreur
	 */
	public JsonObject createRequirementFolder(String name, String parentType, long parentId) throws IOException {

		if (Util.isEmpty(name)) {
			throw new IOException("Le nom du dossier d'exigences est vide.");
		}

		if (Util.isEmpty(parentType)) {
			throw new IOException("Le type du parent du dossier d'exigences est vide.");
		}

		String baseUrl = SquashTmUtils.getSquashTmServerUrl();

		if (Util.isEmpty(baseUrl)) {
			throw new IOException("L'URL de Squash TM n'est pas configurée.");
		}

		String authorization = getAuthorization();

		JsonObject parent = new JsonObject();
		parent.addProperty("_type", parentType);
		parent.addProperty("id", parentId);

		JsonObject folder = new JsonObject();
		folder.addProperty("_type", "requirement-folder");
		folder.addProperty("name", name.trim());
		folder.add("parent", parent);

		String url = baseUrl + "/api/rest/latest/requirement-folders";

		String response = executePost(url, authorization, folder.toString());

		if (Util.isEmpty(response)) {
			throw new IOException(
					"Squash TM n'a retourné aucune réponse " + "lors de la création du dossier d'exigences.");
		}

		try {

			return JsonParser.parseString(response).getAsJsonObject();

		} catch (Exception e) {

			throw new IOException("La réponse de création du dossier " + "d'exigences n'est pas un JSON valide.", e);
		}
	}

	/**
	 * Crée un dossier dans l'espace des cas de test Squash TM.
	 *
	 * @param name       nom du dossier
	 * @param parentType type du parent
	 * @param parentId   identifiant du parent
	 * @return dossier créé
	 * @throws IOException en cas d'erreur
	 */
	public JsonObject createTestCaseFolder(String name, String parentType, long parentId) throws IOException {

		if (Util.isEmpty(name)) {
			throw new IOException("Le nom du dossier de cas de test est vide.");
		}

		if (Util.isEmpty(parentType)) {
			throw new IOException("Le type du parent du dossier de cas de test est vide.");
		}

		String baseUrl = SquashTmUtils.getSquashTmServerUrl();

		if (Util.isEmpty(baseUrl)) {
			throw new IOException("L'URL de Squash TM n'est pas configurée.");
		}

		String authorization = getAuthorization();

		JsonObject parent = new JsonObject();
		parent.addProperty("_type", parentType);
		parent.addProperty("id", parentId);

		JsonObject folder = new JsonObject();
		folder.addProperty("_type", "test-case-folder");
		folder.addProperty("name", name.trim());
		folder.add("parent", parent);

		String url = baseUrl + "/api/rest/latest/test-case-folders";

		String response = executePost(url, authorization, folder.toString());

		if (Util.isEmpty(response)) {
			throw new IOException(
					"Squash TM n'a retourné aucune réponse " + "lors de la création du dossier de cas de test.");
		}

		try {

			return JsonParser.parseString(response).getAsJsonObject();

		} catch (Exception e) {

			throw new IOException("La réponse de création du dossier " + "de cas de test n'est pas un JSON valide.", e);
		}
	}

	/**
	 * Met à jour le nom d'un cas de test existant.
	 *
	 * @param testCaseId identifiant du cas de test
	 * @param name       nouveau nom
	 * @return cas de test mis à jour
	 * @throws IOException si la mise à jour échoue
	 */
	public JsonObject updateTestCase(long testCaseId, String name) throws IOException {

		if (testCaseId <= 0) {
			throw new IOException("L'identifiant du cas de test est invalide.");
		}

		if (Util.isEmpty(name)) {
			throw new IOException("Le nouveau nom du cas de test est vide.");
		}

		String baseUrl = SquashTmUtils.getSquashTmServerUrl();

		if (Util.isEmpty(baseUrl)) {
			throw new IOException("L'URL de Squash TM n'est pas configurée.");
		}

		JsonObject testCase = new JsonObject();
		testCase.addProperty("_type", "test-case");
		testCase.addProperty("name", name.trim());

		String url = baseUrl + "/api/rest/latest/test-cases/" + testCaseId;

		String response = executePatch(url, getAuthorization(), testCase.toString());

		if (Util.isEmpty(response)) {
			LOGGER.info("Cas de test mis à jour avec succès : id=" + testCaseId);
			return new JsonObject();
		}

		try {
			return JsonParser.parseString(response).getAsJsonObject();

		} catch (Exception e) {
			throw new IOException("La réponse de mise à jour du cas de test n'est pas un JSON valide.", e);
		}
	}

	/**
	 * Récupère l'arbre des dossiers d'exigences d'un projet.
	 *
	 * @param projectId identifiant du projet Squash TM
	 * @return arbre des dossiers d'exigences
	 * @throws Exception
	 */
	public JsonElement getRequirementFolderTree(long projectId) throws IOException {

		String baseUrl = SquashTmUtils.getSquashTmServerUrl();

		if (Util.isEmpty(baseUrl)) {
			throw new IOException("L'URL de Squash TM n'est pas configurée.");
		}

		String authorization = getAuthorization();

		String url = baseUrl + "/api/rest/latest/requirement-folders/tree/" + projectId + "?_t="
				+ System.currentTimeMillis();

		String response = executeGet(url, authorization);
		LOGGER.info("Arbre des dossiers d'exigences : " + response);

		if (Util.isEmpty(response)) {
			throw new IOException("Squash TM n'a retourné aucun arbre " + "de dossiers d'exigences.");
		}

		try {

			return JsonParser.parseString(response);

		} catch (Exception e) {

			throw new IOException(
					"L'arbre des dossiers d'exigences " + "retourné par Squash TM n'est pas un JSON valide.", e);
		}
	}

	
	public JsonObject getTestCase(long testCaseId) throws IOException {

		if (testCaseId <= 0) {
			throw new IOException("L'identifiant du cas de test est invalide.");
		}

		String baseUrl = SquashTmUtils.getSquashTmServerUrl();

		if (Util.isEmpty(baseUrl)) {
			throw new IOException("L'URL de Squash TM n'est pas configurée.");
		}

		String url = baseUrl + "/api/rest/latest/test-cases/" + testCaseId;

		return executeGetJson(url, "Impossible de récupérer le cas de test " + testCaseId);
	}

	
	public JsonObject getRequirementFolderContent(long folderId, int page, int size) throws IOException {

		String baseUrl = SquashTmUtils.getSquashTmServerUrl();

		if (Util.isEmpty(baseUrl)) {
			throw new IOException("L'URL de Squash TM n'est pas configurée.");
		}

		String url = baseUrl + "/api/rest/latest/requirement-folders/" + folderId + "/content?page=" + page + "&size="
				+ size + "&_t=" + System.currentTimeMillis();

		return executeGetJson(url, "Impossible de récupérer le contenu du dossier d'exigences.");
	}

	public JsonObject getRequirement(long requirementId) throws IOException {

		if (requirementId <= 0) {
			throw new IOException("L'identifiant de l'exigence est invalide.");
		}

		String baseUrl = SquashTmUtils.getSquashTmServerUrl();

		if (Util.isEmpty(baseUrl)) {
			throw new IOException("L'URL de Squash TM n'est pas configurée.");
		}

		String url = baseUrl + "/api/rest/latest/requirements/" + requirementId;

		return executeGetJson(url, "Impossible de récupérer l'exigence " + requirementId);
	}

	/**
	 * Récupère l'arbre des dossiers de cas de test d'un projet.
	 *
	 * @param projectId identifiant du projet Squash TM
	 * @return arbre des dossiers de cas de test
	 * @throws Exception
	 */
	public JsonElement getTestCaseFolderTree(long projectId) throws IOException {

		String baseUrl = SquashTmUtils.getSquashTmServerUrl();

		if (Util.isEmpty(baseUrl)) {
			throw new IOException("L'URL de Squash TM n'est pas configurée.");
		}

		String authorization = getAuthorization();

		String url = baseUrl + "/api/rest/latest/test-case-folders/tree/" + projectId;

		String response = executeGet(url, authorization);

		LOGGER.info("Arbre des dossiers de cas de test : " + response);

		if (Util.isEmpty(response)) {
			throw new IOException("Squash TM n'a retourné aucun arbre " + "de dossiers de cas de test.");
		}

		try {

			return JsonParser.parseString(response);

		} catch (Exception e) {

			throw new IOException(
					"L'arbre des dossiers de cas de test " + "retourné par Squash TM n'est pas un JSON valide.", e);
		}
	}

	
	public JsonObject getTestCaseFolderContent(long folderId, int page, int size) throws IOException {

		String baseUrl = SquashTmUtils.getSquashTmServerUrl();

		if (Util.isEmpty(baseUrl)) {
			throw new IOException("L'URL de Squash TM n'est pas configurée.");
		}

		String url = baseUrl + "/api/rest/latest/test-case-folders/" + folderId + "/content?page=" + page + "&size="
				+ size;

		return executeGetJson(url, "Impossible de récupérer le contenu du dossier de cas de test.");
	}

	/**
	 * Récupère toutes les étapes d'un cas de test.
	 *
	 * @param testCaseId identifiant du cas de test
	 * @return toutes les étapes du cas de test
	 * @throws IOException si la récupération échoue ou si la réponse est invalide
	 */
	public JsonArray getTestCaseSteps(long testCaseId) throws IOException {

		if (testCaseId <= 0) {
			throw new IOException("Identifiant du cas de test invalide.");
		}

		String baseUrl = SquashTmUtils.getSquashTmServerUrl();

		if (Util.isEmpty(baseUrl)) {
			throw new IOException("L'URL de Squash TM n'est pas configurée.");
		}

		JsonArray allSteps = new JsonArray();

		int page = 0;
		int size = 100;
		int totalPages;

		do {

			String url = baseUrl + "/api/rest/latest/test-cases/" + testCaseId + "/steps?page=" + page + "&size="
					+ size;

			JsonObject response = executeGetJson(url,
					"Impossible de récupérer les étapes du cas de test " + testCaseId);

			if (!response.has("_embedded") || !response.get("_embedded").isJsonObject()) {
				break;
			}

			JsonObject embedded = response.getAsJsonObject("_embedded");

			if (!embedded.has("steps") || !embedded.get("steps").isJsonArray()) {
				break;
			}

			JsonArray steps = embedded.getAsJsonArray("steps");

			for (JsonElement step : steps) {
				allSteps.add(step);
			}

			if (!response.has("page") || !response.get("page").isJsonObject()
					|| !response.getAsJsonObject("page").has("totalPages")
					|| response.getAsJsonObject("page").get("totalPages").isJsonNull()) {
				break;
			}

			totalPages = response.getAsJsonObject("page").get("totalPages").getAsInt();

			page++;

		} while (page < totalPages);

		LOGGER.info("Étapes récupérées pour le cas de test " + testCaseId + " : " + allSteps.size());

		return allSteps;
	}

	/**
	 * Exécute une requête GET et convertit la réponse en JsonObject.
	 */
	private JsonObject executeGetJson(String url, String errorMessage) throws IOException {

		String authorization = getAuthorization();

		String response = executeGet(url, authorization);

		if (Util.isEmpty(response)) {
			throw new IOException(errorMessage + " : réponse vide.");
		}

		try {
			JsonElement json = JsonParser.parseString(response);

			if (!json.isJsonObject()) {
				throw new IOException(errorMessage + " : format JSON inattendu.");
			}

			return json.getAsJsonObject();

		} catch (com.google.gson.JsonParseException e) {
			throw new IOException(errorMessage + " : JSON invalide.", e);
		}
	}

	/**
	 * Supprime tout le contenu de bibliothèque d'un projet existant.
	 *
	 * Le projet lui-même est conservé.
	 *
	 * @param projectId identifiant du projet Squash TM
	 * @throws IOException si une opération échoue
	 */
	public void deleteProjectContent(long projectId) throws IOException {

		LOGGER.info("Suppression du contenu du projet Squash TM id=" + projectId);

		JsonElement requirementTree = getRequirementFolderTree(projectId);

		String requirementFolderIds = extractRootFolderIds(requirementTree, "requirement-folder");

		if (Util.notEmpty(requirementFolderIds)) {

			deleteRequirementFolders(requirementFolderIds);
		}

		JsonElement testCaseTree = getTestCaseFolderTree(projectId);

		String testCaseFolderIds = extractRootFolderIds(testCaseTree, "test-case-folder");

		if (Util.notEmpty(testCaseFolderIds)) {

			deleteTestCaseFolders(testCaseFolderIds);
		}

		LOGGER.info("Contenu du projet Squash TM supprimé. " + "Projet conservé : id=" + projectId);
	}

	/**
	 * Extrait les identifiants des dossiers racines d'un arbre retourné par Squash
	 * TM.
	 *
	 * @param tree       arbre retourné par Squash TM
	 * @param folderType type de dossier recherché
	 * @return liste des identifiants séparés par des virgules
	 */
	private String extractRootFolderIds(JsonElement tree, String folderType) {

		if (tree == null || tree.isJsonNull() || !tree.isJsonArray()) {
			return "";
		}

		JsonArray projects = tree.getAsJsonArray();

		StringBuilder folderIds = new StringBuilder();

		for (JsonElement projectElement : projects) {

			if (!projectElement.isJsonObject()) {
				continue;
			}

			JsonObject project = projectElement.getAsJsonObject();

			if (!project.has("folders") || project.get("folders").isJsonNull()
					|| !project.get("folders").isJsonArray()) {

				continue;
			}

			JsonArray folders = project.getAsJsonArray("folders");

			for (JsonElement folderElement : folders) {

				if (!folderElement.isJsonObject()) {
					continue;
				}

				JsonObject folder = folderElement.getAsJsonObject();

				if (!folder.has("_type") || folder.get("_type").isJsonNull()) {

					continue;
				}

				String type = folder.get("_type").getAsString();

				if (!folderType.equals(type)) {
					continue;
				}

				if (!folder.has("id") || folder.get("id").isJsonNull()) {

					continue;
				}

				if (folderIds.length() > 0) {
					folderIds.append(",");
				}

				folderIds.append(folder.get("id").getAsLong());
			}
		}

		return folderIds.toString();
	}

	/**
	 * Retourne le plus grand numéro déjà utilisé par les références d'un projet
	 * qui suivent le format PREFIXE + nombre (par exemple REQ012 ou TC007).
	 *
	 * @param projectId   identifiant du projet Squash TM
	 * @param prefix      préfixe recherché (REQ ou TC)
	 * @param requirement true pour les exigences, false pour les cas de test
	 * @return le plus grand numéro trouvé, ou 0 si aucune référence ne correspond
	 * @throws IOException si la lecture dans Squash TM échoue
	 */
	public int getMaxReferenceNumber(long projectId, String prefix, boolean requirement) throws IOException {

		String path = "/projects/" + projectId + (requirement ? "/requirements" : "/test-cases");
		String fields = requirement ? "current_version[reference]" : "reference";

		java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
				"^" + java.util.regex.Pattern.quote(prefix) + "(\\d+)$", java.util.regex.Pattern.CASE_INSENSITIVE);

		int max = 0;

		for (JsonElement element : getAllPaged(path + "?fields=" + java.net.URLEncoder.encode(fields, "UTF-8"))) {

			if (!element.isJsonObject()) {
				continue;
			}

			JsonObject item = element.getAsJsonObject();

			if (requirement) {
				item = item.has("current_version") && item.get("current_version").isJsonObject()
						? item.getAsJsonObject("current_version")
						: null;
			}

			if (item == null || !item.has("reference") || item.get("reference").isJsonNull()) {
				continue;
			}

			java.util.regex.Matcher matcher = pattern.matcher(item.get("reference").getAsString().trim());

			if (matcher.matches()) {
				try {
					max = Math.max(max, Integer.parseInt(matcher.group(1)));
				} catch (NumberFormatException ignored) {
					
				}
			}
		}

		return max;
	}

	/**
	 * Crée une exigence dans un dossier Squash TM.
	 *
	 * @param name           nom de l'exigence
	 * @param reference      référence générée (envoyée à Squash TM)
	 * @param parentFolderId identifiant du dossier parent
	 * @return exigence créée
	 * @throws IOException si la création échoue
	 */
	public JsonObject createRequirement(String name, String reference, long parentFolderId) throws IOException {

		if (Util.isEmpty(name)) {
			throw new IOException("Le nom de l'exigence est vide.");
		}

		String baseUrl = SquashTmUtils.getSquashTmServerUrl();

		if (Util.isEmpty(baseUrl)) {
			throw new IOException("L'URL de Squash TM n'est pas configurée.");
		}

		String authorization = getAuthorization();

		JsonObject requirement = new JsonObject();

		requirement.addProperty("_type", "requirement");

		JsonObject currentVersion = new JsonObject();

		currentVersion.addProperty("_type", "requirement-version");

		currentVersion.addProperty("name", name.trim());

		
		if (!Util.isEmpty(reference)) {
			currentVersion.addProperty("reference", reference.trim());
		}

		currentVersion.addProperty("criticality", "MINOR");

		JsonObject category = new JsonObject();

		category.addProperty("code", "CAT_USER_STORY");

		currentVersion.add("category", category);

		currentVersion.addProperty("status", "UNDER_REVIEW");

		requirement.add("current_version", currentVersion);

		JsonObject parent = new JsonObject();

		parent.addProperty("_type", "requirement-folder");

		parent.addProperty("id", parentFolderId);

		requirement.add("parent", parent);

		String url = baseUrl + "/api/rest/latest/requirements";

		String response = executePost(url, authorization, requirement.toString());

		if (Util.isEmpty(response)) {
			throw new IOException("Squash TM n'a retourné aucune réponse " + "lors de la création de l'exigence.");
		}

		try {

			JsonObject createdRequirement = JsonParser.parseString(response).getAsJsonObject();

			LOGGER.info("Requirement créée : " + name + " | référence : " + reference);

			return createdRequirement;

		} catch (Exception e) {

			throw new IOException("La réponse de création de l'exigence " + "n'est pas un JSON valide.", e);
		}
	}

	/**
	 * Met à jour le nom d'une exigence existante.
	 *
	 * @param requirementId identifiant de l'exigence
	 * @param name          nouveau nom
	 * @return exigence mise à jour
	 * @throws IOException si la mise à jour échoue
	 */
	public JsonObject updateRequirement(long requirementId, String name) throws IOException {

		if (requirementId <= 0) {
			throw new IOException("L'identifiant de l'exigence est invalide.");
		}

		if (Util.isEmpty(name)) {
			throw new IOException("Le nouveau nom de l'exigence est vide.");
		}

		String baseUrl = SquashTmUtils.getSquashTmServerUrl();

		if (Util.isEmpty(baseUrl)) {
			throw new IOException("L'URL de Squash TM n'est pas configurée.");
		}

		JsonObject currentVersion = new JsonObject();
		currentVersion.addProperty("_type", "requirement-version");
		currentVersion.addProperty("name", name.trim());

		JsonObject requirement = new JsonObject();
		requirement.addProperty("_type", "requirement");
		requirement.add("current_version", currentVersion);

		String url = baseUrl + "/api/rest/latest/requirements/" + requirementId;

		String response = executePatch(url, getAuthorization(), requirement.toString());

		if (Util.isEmpty(response)) {
			LOGGER.info("Exigence mise à jour avec succès : id=" + requirementId);
			return new JsonObject();
		}

		try {
			return JsonParser.parseString(response).getAsJsonObject();

		} catch (Exception e) {
			throw new IOException("La réponse de mise à jour de l'exigence n'est pas un JSON valide.", e);
		}
	}

	/**
	 * Crée un cas de test standard dans un dossier Squash TM.
	 *
	 * @param name           nom du cas de test
	 * @param reference      référence générée (envoyée à Squash TM)
	 * @param parentFolderId identifiant du dossier parent
	 * @return cas de test créé
	 * @throws IOException si la création échoue
	 */
	public JsonObject createTestCase(String name, String reference, long parentFolderId) throws IOException {

		if (Util.isEmpty(name)) {
			throw new IOException("Le nom du cas de test est vide.");
		}

		String baseUrl = SquashTmUtils.getSquashTmServerUrl();

		if (Util.isEmpty(baseUrl)) {
			throw new IOException("L'URL de Squash TM n'est pas configurée.");
		}

		String authorization = getAuthorization();

		JsonObject testCase = new JsonObject();

		testCase.addProperty("_type", "test-case");

		testCase.addProperty("name", name.trim());

		
		if (!Util.isEmpty(reference)) {
			testCase.addProperty("reference", reference.trim());
		}

		JsonObject parent = new JsonObject();

		parent.addProperty("_type", "test-case-folder");

		parent.addProperty("id", parentFolderId);

		testCase.add("parent", parent);

		testCase.addProperty("importance", "MEDIUM");

		testCase.addProperty("status", "UNDER_REVIEW");

		JsonObject nature = new JsonObject();

		nature.addProperty("code", "NAT_FUNCTIONAL_TESTING");

		testCase.add("nature", nature);

		JsonObject type = new JsonObject();

		type.addProperty("code", "TYP_COMPLIANCE_TESTING");

		testCase.add("type", type);

		String url = baseUrl + "/api/rest/latest/test-cases";

		String response = executePost(url, authorization, testCase.toString());

		if (Util.isEmpty(response)) {
			throw new IOException("Squash TM n'a retourné aucune réponse " + "lors de la création du cas de test.");
		}

		try {

			JsonObject createdTestCase = JsonParser.parseString(response).getAsJsonObject();

			LOGGER.info("Cas de test créé : " + name + " | référence : " + reference);

			return createdTestCase;

		} catch (Exception e) {

			throw new IOException("La réponse de création du cas de test " + "n'est pas un JSON valide.", e);
		}
	}

	/**
	 * Ajoute une étape d'action à un cas de test.
	 *
	 * @param testCaseId     identifiant du cas de test
	 * @param action         action à effectuer
	 * @param expectedResult résultat attendu
	 * @return étape créée
	 * @throws IOException si la création échoue
	 */
	public JsonObject createTestStep(long testCaseId, String action, String expectedResult) throws IOException {

		if (Util.isEmpty(action) && Util.isEmpty(expectedResult)) {
			throw new IOException("L'action et le résultat attendu du test step sont vides.");
		}

		if (Util.isEmpty(action)) {
			throw new IOException("L'action du test step est vide.");
		}

		if (Util.isEmpty(expectedResult)) {
			throw new IOException("Le résultat attendu du test step est vide.");
		}

		String baseUrl = SquashTmUtils.getSquashTmServerUrl();

		if (Util.isEmpty(baseUrl)) {
			throw new IOException("L'URL de Squash TM n'est pas configurée.");
		}

		String authorization = getAuthorization();

		JsonObject step = new JsonObject();

		step.addProperty("_type", "action-step");

		step.addProperty("action", action.trim());

		step.addProperty("expected_result", expectedResult.trim());

		String url = baseUrl + "/api/rest/latest/test-cases/" + testCaseId + "/steps";

		String response = executePost(url, authorization, step.toString());

		if (Util.isEmpty(response)) {
			throw new IOException("Squash TM n'a retourné aucune réponse " + "lors de la création du test step.");
		}

		try {

			JsonObject createdStep = JsonParser.parseString(response).getAsJsonObject();

			LOGGER.info("Test step créé pour le cas de test id=" + testCaseId);

			return createdStep;

		} catch (Exception e) {

			throw new IOException("La réponse de création du test step " + "n'est pas un JSON valide.", e);
		}
	}

	/**
	 * Met à jour une étape existante d'un cas de test.
	 *
	 * @param stepId         identifiant de l'étape
	 * @param action         nouvelle action
	 * @param expectedResult nouveau résultat attendu
	 * @return étape mise à jour
	 * @throws IOException si la mise à jour échoue
	 */
	public JsonObject updateTestStep(long stepId, String action, String expectedResult) throws IOException {

		if (stepId <= 0) {
			throw new IOException("L'identifiant de l'étape est invalide.");
		}

		if (Util.isEmpty(action)) {
			throw new IOException("L'action de l'étape est vide.");
		}

		if (Util.isEmpty(expectedResult)) {
			throw new IOException("Le résultat attendu de l'étape est vide.");
		}

		String baseUrl = SquashTmUtils.getSquashTmServerUrl();

		if (Util.isEmpty(baseUrl)) {
			throw new IOException("L'URL de Squash TM n'est pas configurée.");
		}

		JsonObject step = new JsonObject();
		step.addProperty("_type", "action-step");
		step.addProperty("action", action.trim());
		step.addProperty("expected_result", expectedResult.trim());

		String url = baseUrl + "/api/rest/latest/test-steps/" + stepId;

		String response = executePatch(url, getAuthorization(), step.toString());

		if (Util.isEmpty(response)) {
			LOGGER.info("Étape de test mise à jour avec succès : id=" + stepId);
			return new JsonObject();
		}

		try {
			return JsonParser.parseString(response).getAsJsonObject();

		} catch (Exception e) {
			throw new IOException("La réponse de mise à jour de l'étape n'est pas un JSON valide.", e);
		}
	}

	/**
	 * Supprime plusieurs étapes de test à partir de leurs identifiants.
	 *
	 * @param stepIds identifiants des étapes à supprimer
	 * @throws IOException si la suppression échoue
	 */
	public void deleteTestSteps(Set<Long> stepIds) throws IOException {

		if (stepIds == null || stepIds.isEmpty()) {
			return;
		}

		StringBuilder ids = new StringBuilder();

		for (Long stepId : stepIds) {

			if (stepId == null || stepId <= 0) {
				throw new IOException("Un identifiant d'étape à supprimer est invalide.");
			}

			if (ids.length() > 0) {
				ids.append(",");
			}

			ids.append(stepId);
		}

		String baseUrl = SquashTmUtils.getSquashTmServerUrl();

		if (Util.isEmpty(baseUrl)) {
			throw new IOException("L'URL de Squash TM n'est pas configurée.");
		}

		String url = baseUrl + "/api/rest/latest/test-steps/" + ids;

		executeDelete(url, getAuthorization());

		LOGGER.info("Étapes Squash TM supprimées : " + ids);
	}

	
	public String resolveAuthorization() throws IOException {
		return getAuthorization();
	}

	private String buildApiUrl(String apiPath) throws IOException {

		String baseUrl = SquashTmUtils.getSquashTmServerUrl();

		if (Util.isEmpty(baseUrl)) {
			throw new IOException("L'URL du serveur Squash TM n'est pas configurée.");
		}

		return baseUrl + "/api/rest/latest" + apiPath;
	}

	
	public JsonObject getResource(String apiPath) throws IOException {
		return executeGetJson(buildApiUrl(apiPath), "Impossible de récupérer " + apiPath);
	}

	/**
	 * Lit toutes les pages d'une ressource paginée et retourne les éléments
	 * regroupés dans un seul tableau.
	 *
	 * @param apiPath chemin relatif à /api/rest/latest, avec éventuellement des
	 *                paramètres (fields, include...), par exemple /test-cases
	 */
	public JsonArray getAllPaged(String apiPath) throws IOException {

		JsonArray all = new JsonArray();
		String separator = apiPath.indexOf('?') >= 0 ? "&" : "?";
		int page = 0;
		int totalPages = 1;

		do {

			String url = buildApiUrl(apiPath + separator + "page=" + page + "&size=" + DASHBOARD_PAGE_SIZE);
			JsonObject response = executeGetJson(url, "Impossible de récupérer " + apiPath);

			if (!response.has("_embedded") || !response.get("_embedded").isJsonObject()) {
				break;
			}

			JsonArray items = null;

			for (Map.Entry<String, JsonElement> entry : response.getAsJsonObject("_embedded").entrySet()) {
				if (entry.getValue().isJsonArray()) {
					items = entry.getValue().getAsJsonArray();
					break;
				}
			}

			if (items == null) {
				break;
			}

			for (JsonElement item : items) {
				all.add(item);
			}

			if (response.has("page") && response.get("page").isJsonObject()
					&& response.getAsJsonObject("page").has("totalPages")) {
				totalPages = response.getAsJsonObject("page").get("totalPages").getAsInt();
			}

			page++;

		} while (page < totalPages && page < DASHBOARD_MAX_PAGES);

		return all;
	}
}