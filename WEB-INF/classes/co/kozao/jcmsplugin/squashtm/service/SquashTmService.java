package co.kozao.jcmsplugin.squashtm.service;

import java.io.BufferedReader;
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
	private String executeGet(String urlString, String authorization) throws Exception {

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
}
