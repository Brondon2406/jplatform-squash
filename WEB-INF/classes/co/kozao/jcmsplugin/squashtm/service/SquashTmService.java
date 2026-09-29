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
			 * Construction des informations utilisateur.
			 */
			LoggedUserInfo info = new LoggedUserInfo();

			info.setId(userId);

			/*
			 * /tokens ne fournit pas les informations personnelles de l'utilisateur.
			 *
			 * On utilise donc les informations du membre JCMS.
			 */
			String fullName = member.getFullName();

			if (Util.isEmpty(fullName)) {
				fullName = member.getName();
			}

			String email = member.getEmail();

			info.setFullName(fullName);
			info.setEmailAddr(email);

			/*
			 * Ne pas utiliser createdOn comme date de création de l'utilisateur : il s'agit
			 * de la date du token.
			 */

			LOGGER.info("Utilisateur Squash TM authentifié par Token. ID = " + info.getId() + " | Nom = "
					+ info.getFullName() + " | Email = " + info.getEmailAddr());

			return info;

		} catch (Exception e) {

			LOGGER.error("Erreur pendant l'authentification TOKEN.", e);

			return null;
		}
	}

	/**
	 * Authentification Basic.
	 */
	private LoggedUserInfo authenticateWithBasic(Member member) {

		String username = SquashTmUtils.getSquashTmUsername(member);

		String password = SquashTmUtils.getSquashTmPassword(member);

		if (Util.isEmpty(username) || Util.isEmpty(password)) {

			LOGGER.warn("Username ou password Squash TM absent.");

			return null;
		}

		String baseUrl = SquashTmUtils.getSquashTmServerUrl();

		if (Util.isEmpty(baseUrl)) {

			LOGGER.warn("URL Squash TM non configurée.");

			return null;
		}

		try {

			String credentials = username + ":" + password;

			String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

			String usersJson = executeGet(baseUrl + "/users?size=1000", "Basic " + encoded);

			if (Util.isEmpty(usersJson)) {
				return null;
			}

			JsonObject root = JsonParser.parseString(usersJson).getAsJsonObject();

			if (!root.has("_embedded")) {
				return null;
			}

			JsonObject embedded = root.getAsJsonObject("_embedded");

			if (!embedded.has("users")) {
				return null;
			}

			JsonArray users = embedded.getAsJsonArray("users");

			for (JsonElement element : users) {

				JsonObject user = element.getAsJsonObject();

				if (!user.has("login")) {
					continue;
				}

				String login = user.get("login").getAsString();

				if (username.equalsIgnoreCase(login)) {

					return buildLoggedUserInfo(user);
				}
			}

			LOGGER.warn("Utilisateur Squash TM introuvable : " + username);

			return null;

		} catch (Exception e) {

			LOGGER.error("Erreur pendant l'authentification BASIC.", e);

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
