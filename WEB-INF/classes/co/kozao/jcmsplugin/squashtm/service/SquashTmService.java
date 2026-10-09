package co.kozao.jcmsplugin.squashtm.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.apache.log4j.Logger;

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
	private static final int READ_TIMEOUT = 5000;

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
			 * ============================================================ 1. VALIDATION DU
			 * TOKEN ============================================================
			 */

			String tokenUrl = baseUrl + "/tokens";

			LOGGER.error("========== AUTH TOKEN ==========");
			LOGGER.error("baseUrl = [" + baseUrl + "]");
			LOGGER.error("tokenUrl = [" + tokenUrl + "]");

			String tokensJson = executeGet(tokenUrl, "Bearer " + token);

			LOGGER.error("========== REPONSE /tokens ==========");
			LOGGER.error(tokensJson);

			if (Util.isEmpty(tokensJson)) {

				LOGGER.warn("Réponse vide lors de la validation du token.");

				return null;
			}

			JsonObject root = JsonParser.parseString(tokensJson).getAsJsonObject();

			if (!root.has("_embedded")) {

				LOGGER.warn("La réponse /tokens ne contient pas _embedded.");

				return null;
			}

			JsonObject embedded = root.getAsJsonObject("_embedded");

			if (!embedded.has("api-tokens")) {

				LOGGER.warn("La réponse /tokens ne contient pas api-tokens.");

				return null;
			}

			JsonArray tokens = embedded.getAsJsonArray("api-tokens");

			if (tokens == null || tokens.size() == 0) {

				LOGGER.warn("Aucun token Squash TM trouvé.");

				return null;
			}

			/*
			 * ============================================================ 2. RECUPERATION
			 * DE L'UTILISATEUR ASSOCIE AU TOKEN
			 * ============================================================
			 */

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

			/*
			 * ============================================================ 3. RECUPERATION
			 * DU PROFIL SQUASH TM
			 * ============================================================
			 */

			LOGGER.error("========== TEST CODE VERSION ==========");

			LOGGER.error("!!! NOUVELLE VERSION SquashTmService !!!");

			String userUrl = baseUrl + "/users/" + userId;

			LOGGER.error("========== RECUPERATION PROFIL SQUASH ==========");

			LOGGER.error("userUrl = " + userUrl);

			/*
			 * UNE SEULE requête vers /users/{id}.
			 */
			String userJson = executeGet(userUrl, "Bearer " + token);

			LOGGER.error("========== REPONSE /users/{id} ==========");

			LOGGER.error(userJson);

			/*
			 * ============================================================ 4. CONSTRUCTION
			 * DE LoggedUserInfo
			 * ============================================================
			 */

			LoggedUserInfo info = null;

			if (Util.notEmpty(userJson)) {

				info = buildLoggedUserInfo(userJson);
			}

			/*
			 * Sécurité : si le profil n'a pas pu être construit, on crée quand même un
			 * objet afin de conserver l'id du compte.
			 */
			if (info == null) {

				LOGGER.warn("Impossible de construire LoggedUserInfo depuis /users/" + userId);

				info = new LoggedUserInfo();
			}

			/*
			 * L'id provient directement de Squash TM.
			 */
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

			/*
			 * IMPORTANT :
			 *
			 * On retourne uniquement les informations provenant de Squash TM.
			 *
			 * Le Member JCMS n'est PAS utilisé pour compléter le nom, l'email ou la date de
			 * création.
			 */
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

		/*
		 * ============================================================ 2. URL SQUASH TM
		 * ============================================================
		 */

		String baseUrl = SquashTmUtils.getSquashTmServerUrl();

		LOGGER.error("Base URL Squash TM = [" + baseUrl + "]");

		if (Util.isEmpty(baseUrl)) {

			LOGGER.warn("URL Squash TM non configurée.");

			return null;
		}

		try {

			/*
			 * ======================================================== 3. CREATION
			 * AUTHENTIFICATION BASIC
			 * ========================================================
			 */

			String credentials = username + ":" + password;

			String encodedCredentials = Base64.getEncoder()
					.encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

			String authorization = "Basic " + encodedCredentials;

			/*
			 * ======================================================== 4. RECUPERATION DE
			 * LA LISTE DES UTILISATEURS
			 * ========================================================
			 */

			String usersUrl = baseUrl + "/users?size=1000";

			LOGGER.error("========== RECHERCHE UTILISATEUR SQUASH ==========");
			LOGGER.error("usersUrl = [" + usersUrl + "]");

			String usersJson = executeGet(usersUrl, authorization);

			LOGGER.error("========== REPONSE /users ==========");

			if (Util.isEmpty(usersJson)) {

				LOGGER.warn("Squash TM n'a retourné aucune donnée.");

				return null;
			}

			LOGGER.error("Réponse reçue : " + usersJson);

			/*
			 * ======================================================== 5. PARSING DE LA
			 * COLLECTION ========================================================
			 */

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

			/*
			 * ======================================================== 6. RECHERCHE DU
			 * LOGIN ========================================================
			 */

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

			/*
			 * ======================================================== 7. UTILISATEUR
			 * INTROUVABLE ========================================================
			 */

			if (Util.isEmpty(userId)) {

				LOGGER.warn("Utilisateur Squash TM introuvable : " + username);

				return null;
			}

			/*
			 * ======================================================== 8. RECUPERATION DU
			 * PROFIL COMPLET ========================================================
			 *
			 * /users retourne seulement les informations résumées.
			 *
			 * On appelle donc :
			 *
			 * GET /users/{id}
			 *
			 * pour récupérer :
			 *
			 * - first_name - last_name - email - created_on - etc.
			 */

			String userUrl = baseUrl + "/users/" + userId;

			LOGGER.error("========== RECUPERATION PROFIL SQUASH ==========");

			LOGGER.error("userUrl = [" + userUrl + "]");

			String userJson = executeGet(userUrl, authorization);

			LOGGER.error("========== REPONSE /users/{id} ==========");

			LOGGER.error(userJson);

			/*
			 * ======================================================== 9. VERIFICATION DU
			 * PROFIL ========================================================
			 */

			if (Util.isEmpty(userJson)) {

				LOGGER.warn("Impossible de récupérer le profil complet " + "de l'utilisateur Squash TM : " + userId);

				return null;
			}

			/*
			 * ======================================================== 10. CONSTRUCTION
			 * LoggedUserInfo ========================================================
			 */

			LoggedUserInfo info = buildLoggedUserInfo(userJson);

			if (info == null) {

				LOGGER.warn("Impossible de construire LoggedUserInfo " + "pour l'utilisateur : " + userId);

				return null;
			}

			/*
			 * ======================================================== 11. LOGS FINAUX
			 * ========================================================
			 */

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

			/*
			 * IMPORTANT :
			 *
			 * Le Member JCMS n'est jamais utilisé pour compléter les informations de
			 * l'utilisateur Squash TM.
			 */
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
				connection.setRequestProperty("Authorization", authorization);
				connection.setRequestProperty("Accept", "application/json");
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

		/*
		 * ID
		 */

		if (user.has("id")) {

			info.setId(user.get("id").getAsString());
		}

		/*
		 * Nom
		 */

		String firstName = getString(user, "first_name");

		String lastName = getString(user, "last_name");

		String fullName = ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "")).trim();

		info.setFullName(fullName);

		/*
		 * Email
		 */

		String email = getString(user, "email");

		info.setEmailAddr(email);

		/*
		 * Date création
		 */

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
	 * Exécute une requête HTTP DELETE vers Squash TM.
	 *
	 * @param urlString     URL de la ressource à supprimer
	 * @param authorization en-tête Authorization
	 * @throws IOException si la requête échoue
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

			LOGGER.info("Réponse Squash TM HTTP DELETE = " + responseCode);

			/*
			 * Toutes les réponses 2xx sont considérées comme des réponses de succès.
			 */
			if (responseCode < 200 || responseCode >= 300) {

				throw new IOException(
						"Squash TM a refusé la requête DELETE. " + "HTTP = " + responseCode + " | URL = " + urlString);
			}

		} finally {

			if (connection != null) {
				connection.disconnect();
			}
		}
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

			connection.getOutputStream().write(body);

			int responseCode = connection.getResponseCode();

			LOGGER.info("Réponse Squash TM HTTP POST = " + responseCode);

			if (responseCode < 200 || responseCode >= 300) {
				throw new IOException(
						"Squash TM a refusé la requête POST. HTTP = " + responseCode + " | URL = " + urlString);
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

		String url = baseUrl + "/api/rest/latest/requirement-folders/tree/" + projectId;

		String response = executeGet(url, authorization);

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
}
