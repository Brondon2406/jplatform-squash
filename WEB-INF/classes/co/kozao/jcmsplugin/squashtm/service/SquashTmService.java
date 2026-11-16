package co.kozao.jcmsplugin.squashtm.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
 * Service chargé des appels REST vers Squash TM.
 */
public class SquashTmService {

	private static final Logger LOGGER = Logger.getLogger(SquashTmService.class);

	private static final int CONNECT_TIMEOUT = 5000;
	private static final int READ_TIMEOUT = 30000;
	private final String authorizationHeader;

	/**
	 * Constructeur utilisé par les traitements en arrière-plan.
	 *
	 * @param authorizationHeader en-tête Authorization préparé avant le lancement
	 *                            du thread
	 */
	public SquashTmService(String authorizationHeader) {
		this.authorizationHeader = authorizationHeader;
	}

	/**
	 * Constructeur par défaut conservé pour les autres utilisations du service.
	 */
	public SquashTmService() {
		this.authorizationHeader = null;
	}

	/**
	 * Authentifie un utilisateur JCMS auprès de Squash TM.
	 *
	 * @param member membre JCMS courant
	 * @return informations utilisateur Squash TM ou null
	 */
	public LoggedUserInfo authenticate(Member member) {

		LOGGER.error("========== SquashTmService.authenticate() APPELE ==========");

		if (member == null) {
			LOGGER.warn("Member JCMS null.");
			return null;
		}

		AuthentificationMode mode = SquashTmUtils.getAuthentificationMode();
		LOGGER.error("========== MODE = " + mode + " ==========");

		// LOGGER.info("Mode d'authentification Squash TM : " + mode);

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

			/*
			 * ======================== 1. VALIDATION DU TOKEN
			 * =====================================
			 */

			String tokenUrl = baseUrl + "/api/rest/latest/tokens";

			LOGGER.error("========== AUTH TOKEN ==========");
			LOGGER.error("baseUrl = [" + baseUrl + "]");
			LOGGER.error("tokenUrl = [" + tokenUrl + "]");

			String tokensJson = executeGet(tokenUrl, "Bearer " + token);

			LOGGER.error("========== REPONSE /api/rest/latest/tokens ==========");
			LOGGER.error(tokensJson);

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

			LOGGER.error("========== USER ID DU TOKEN ==========");

			LOGGER.error("userId = " + userId);

			LOGGER.error("========== TEST CODE VERSION ==========");

			LOGGER.error("!!! NOUVELLE VERSION SquashTmService !!!");

			String userUrl = baseUrl + "/api/rest/latest/users/" + userId;

			LOGGER.error("========== RECUPERATION PROFIL SQUASH ==========");

			LOGGER.error("userUrl = " + userUrl);

			String userJson = executeGet(userUrl, "Bearer " + token);

			LOGGER.error("========== REPONSE /api/rest/latest/users/{id} ==========");

			LOGGER.error(userJson);

			/*
			 * ==================== DE LoggedUserInfo ======================
			 */

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

			/*
			 * ============================================================ 5. LOGS FINAUX
			 * ============================================================
			 */

			LOGGER.error("========== LOGGED USER INFO FINAL ==========");

			LOGGER.error("ID       = " + info.getId());

			LOGGER.error("FullName = " + info.getFullName());

			LOGGER.error("Email    = " + info.getEmailAddr());

			LOGGER.error("Created  = " + info.getCreatedAt());

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

		LOGGER.error("========== AUTHENTIFICATION BASIC ==========");

		/*
		 * ============================================================ 1. RECUPERATION
		 * DES IDENTIFIANTS SQUASH TM
		 * ============================================================
		 */

		String username = SquashTmUtils.getSquashTmUsername(member);
		String password = SquashTmUtils.getSquashTmPassword(member);

		LOGGER.error("Username Squash TM = [" + username + "]");
		LOGGER.error("Password présente ? " + Util.notEmpty(password));

		if (Util.isEmpty(username) || Util.isEmpty(password)) {

			LOGGER.warn("Username ou password Squash TM absent.");

			return null;
		}

		String baseUrl = SquashTmUtils.getSquashTmServerUrl();

		LOGGER.error("Base URL Squash TM = [" + baseUrl + "]");

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

			LOGGER.error("========== RECHERCHE UTILISATEUR SQUASH ==========");
			LOGGER.error("usersUrl = [" + usersUrl + "]");

			String usersJson = executeGet(usersUrl, authorization);

			LOGGER.error("========== REPONSE /users ==========");

			if (Util.isEmpty(usersJson)) {

				LOGGER.warn("Squash TM n'a retourné aucune donnée.");

				return null;
			}

			LOGGER.error("Réponse reçue : " + usersJson);

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

			LOGGER.error("Nombre d'utilisateurs reçus = " + users.size());

			String userId = null;

			for (JsonElement element : users) {

				if (element == null || !element.isJsonObject()) {
					continue;
				}

				JsonObject user = element.getAsJsonObject();

				String login = getString(user, "login");

				LOGGER.error("Utilisateur Squash trouvé : login = [" + login + "]");

				if (Util.isEmpty(login)) {
					continue;
				}

				if (username.equalsIgnoreCase(login)) {

					userId = getString(user, "id");

					LOGGER.error("========== UTILISATEUR BASIC TROUVE ==========");

					LOGGER.error("Login = " + login);
					LOGGER.error("ID    = " + userId);

					break;
				}
			}

			if (Util.isEmpty(userId)) {

				LOGGER.warn("Utilisateur Squash TM introuvable : " + username);

				return null;
			}

			String userUrl = baseUrl + "/api/rest/latest/users/" + userId;

			LOGGER.error("========== RECUPERATION PROFIL SQUASH ==========");

			LOGGER.error("userUrl = [" + userUrl + "]");

			String userJson = executeGet(userUrl, authorization);

			LOGGER.error("========== REPONSE /users/{id} ==========");

			LOGGER.error(userJson);

			if (Util.isEmpty(userJson)) {

				LOGGER.warn("Impossible de récupérer le profil complet " + "de l'utilisateur Squash TM : " + userId);

				return null;
			}

			LoggedUserInfo info = buildLoggedUserInfo(userJson);

			if (info == null) {

				LOGGER.warn("Impossible de construire LoggedUserInfo " + "pour l'utilisateur : " + userId);

				return null;
			}

			LOGGER.error("========== LOGGED USER INFO BASIC ==========");

			LOGGER.error("ID       = " + info.getId());
			LOGGER.error("FullName = " + info.getFullName());
			LOGGER.error("Email    = " + info.getEmailAddr());
			LOGGER.error("Created  = " + info.getCreatedAt());

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

		LOGGER.error("========== executeGet() APPELE ==========");
		LOGGER.error("URL reçue = [" + urlString + "]");
		LOGGER.error("Authorization présente ? " + Util.notEmpty(authorization));

		if (Util.isEmpty(urlString)) {
			LOGGER.error("========== URL VIDE ==========");
			return null;
		}

		try {

			URI uri = URI.create(urlString);

			LOGGER.error("URI créée = [" + uri + "]");
			LOGGER.error("URI absolue ? " + uri.isAbsolute());

			URL url = uri.toURL();

			LOGGER.error("URL finale = [" + url + "]");

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

				LOGGER.error("========== ENVOI REQUETE GET ==========");

				int responseCode = connection.getResponseCode();

				LOGGER.error("Réponse Squash TM HTTP = " + responseCode);

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

				LOGGER.error("========== REPONSE RECUE ==========");

				return response.toString();

			} finally {

				connection.disconnect();
			}

		} catch (IllegalArgumentException e) {

			LOGGER.error("========== URI INVALIDE ==========");
			LOGGER.error("URL reçue par executeGet() = [" + urlString + "]", e);

			throw e;
		}
	}

	/**
	 * Construit LoggedUserInfo depuis un JSON utilisateur.
	 */
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

	/**
	 * Lecture sécurisée d'une propriété JSON.
	 */
	private String getString(JsonObject object, String property) {

		if (object == null || !object.has(property) || object.get(property).isJsonNull()) {

			return null;
		}

		return object.get(property).getAsString();
	}

	/**
	 * Test de connexion.
	 */
	public boolean testConnection(Member member) {

		return authenticate(member) != null;
	}

	/**
	 * Supprime un ou plusieurs dossiers d'exigences.
	 *
	 * Les identifiants sont séparés par des virgules.
	 *
	 * @param folderIds identifiants des dossiers à supprimer
	 * @throws IOException si la suppression échoue
	 */
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

	/**
	 * Supprime un ou plusieurs dossiers de cas de test.
	 *
	 * Les identifiants sont séparés par des virgules.
	 *
	 * @param folderIds identifiants des dossiers à supprimer
	 * @throws IOException si la suppression échoue
	 */
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
	 * <p>
	 * L'authentification concerne le compte Squash TM configuré dans le connecteur.
	 * </p>
	 *
	 * @return valeur de l'en-tête Authorization
	 * @throws IOException si les credentials Squash TM sont absents
	 */
	private String getAuthorization() throws IOException {
		// En arrière-plan, utiliser l'en-tête capturé pendant la requête JCMS.
		if (Util.notEmpty(authorizationHeader)) {
			return authorizationHeader;
		}

		AuthentificationMode mode = SquashTmUtils.getAuthentificationMode();
		if (mode == null) {
			throw new IOException("Le mode d'authentification Squash TM n'est pas configuré.");
		}

		/*
		 * ====== TOKEN ===========
		 */
		if (AuthentificationMode.TOKEN.equals(mode)) {
			String token = SquashTmUtils.getSquashTmApiToken();

			if (token == null || token.trim().isEmpty()) {
				throw new IOException("Le token Squash TM est absent.");
			}

			return "Bearer " + token;
		}

		/*
		 * ============ BASIC ================
		 */
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

			// Envoyer le corps JSON et fermer le flux de sortie.
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

	/**
	 * Récupère les détails d'un cas de test Squash TM.
	 *
	 * @param testCaseId identifiant du cas de test
	 * @return détails du cas de test
	 * @throws IOException si la récupération échoue
	 */
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

	/**
	 * Récupère le contenu paginé d'un dossier d'exigences.
	 */
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

	/**
	 * Récupère le contenu paginé d'un dossier de cas de test.
	 */
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

			// Un cas de test sans étape renvoie une réponse sans _embedded.
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

			// Pagination : si elle est absente, il n'y a qu'une seule page.
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

		/*
		 * 1. Récupération de l'arbre des exigences.
		 */
		JsonElement requirementTree = getRequirementFolderTree(projectId);

		/*
		 * 2. Suppression des dossiers racines d'exigences.
		 */
		String requirementFolderIds = extractRootFolderIds(requirementTree, "requirement-folder");

		if (Util.notEmpty(requirementFolderIds)) {

			deleteRequirementFolders(requirementFolderIds);
		}

		/*
		 * 3. Récupération de l'arbre des cas de test.
		 */
		JsonElement testCaseTree = getTestCaseFolderTree(projectId);

		/*
		 * 4. Suppression des dossiers racines de cas de test.
		 */
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
	 * Crée une exigence dans un dossier Squash TM.
	 *
	 * @param name           nom de l'exigence
	 * @param reference      référence générée localement
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

		/*
		 * Requirement
		 */
		JsonObject requirement = new JsonObject();

		requirement.addProperty("_type", "requirement");

		/*
		 * Version courante de la requirement.
		 */
		JsonObject currentVersion = new JsonObject();

		currentVersion.addProperty("_type", "requirement-version");

		currentVersion.addProperty("name", name.trim());

		/*
		 * Paramètres minimaux nécessaires.
		 */
		currentVersion.addProperty("criticality", "MINOR");

		JsonObject category = new JsonObject();

		category.addProperty("code", "CAT_USER_STORY");

		currentVersion.add("category", category);

		currentVersion.addProperty("status", "UNDER_REVIEW");

		requirement.add("current_version", currentVersion);

		/*
		 * Parent = dossier de fonctionnalités.
		 */
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

			LOGGER.info("Requirement créée : " + name + " | référence locale : " + reference);

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
	 * @param reference      référence générée localement
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

		/*
		 * Parent = dossier de fonctionnalités.
		 */
		JsonObject parent = new JsonObject();

		parent.addProperty("_type", "test-case-folder");

		parent.addProperty("id", parentFolderId);

		testCase.add("parent", parent);

		/*
		 * Paramètres standards.
		 */
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

			LOGGER.info("Cas de test créé : " + name + " | référence locale : " + reference);

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

}