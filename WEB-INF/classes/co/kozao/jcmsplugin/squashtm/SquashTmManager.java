package co.kozao.jcmsplugin.squashtm.util;

import org.apache.log4j.Logger;

import com.jalios.jcms.Member;
import com.jalios.jcms.plugin.PluginManager;
import com.jalios.util.Util;

import co.kozao.jcmsplugin.squashtm.SquashTmConstants;
import co.kozao.jcmsplugin.squashtm.model.api.LoggedUserInfo;
import co.kozao.jcmsplugin.squashtm.service.SquashTmService;

/**
 * Manager principal du connecteur Squash TM.
 */
public class SquashTmManager {

	private static final Logger LOGGER = Logger.getLogger(SquashTmManager.class);

	private static SquashTmManager SINGLETON;

	private final SquashTmService service;

	private SquashTmManager() {

		service = new SquashTmService();
	}

	/**
	 * Retourne l'instance unique du Manager.
	 */
	public static synchronized SquashTmManager getInstance() {

		if (SINGLETON == null) {

			SINGLETON = new SquashTmManager();
		}

		return SINGLETON;
	}

	/**
	 * Vérifie que le plugin est actif.
	 */
	public boolean isPluginActive(Member member) {

		if (member == null) {
			return false;
		}

		return PluginManager.getInstance().isPluginActive(SquashTmConstants.PLUGIN_NAME);
	}

	/**
	 * Vérifie que le plugin peut communiquer avec Squash TM.
	 */
	public boolean canConnectToSquashTm(Member member) {

		if (member == null) {
			return false;
		}

		if (!isPluginActive(member)) {

			LOGGER.warn("SquashTmPlugin n'est pas actif.");

			return false;
		}

		String serverUrl = SquashTmUtils.getSquashTmServerUrl();

		boolean configured = Util.notEmpty(serverUrl);

		LOGGER.info("URL Squash TM configurée : " + configured);

		return configured;
	}

	/**
	 * Vérifie si l'utilisateur est actuellement connecté.
	 */
	public boolean isConnect() {

		LoggedUserInfo userInfo = SquashTmUtils.getSquashTmRemoteUserInfos();

		return userInfo != null;
	}

	/**
	 * Retourne l'utilisateur Squash TM connecté.
	 */
	public LoggedUserInfo getLoggedUserInfo() {

		return SquashTmUtils.getSquashTmRemoteUserInfos();
	}

	/**
	 * Lance une authentification.
	 *
	 * Le mode TOKEN ou BASIC est choisi par le Service.
	 */
	public boolean connect(Member member) {

		if (member == null) {

			LOGGER.warn("Member JCMS null.");

			return false;
		}

		LoggedUserInfo userInfo = service.authenticate(member);

		if (userInfo == null) {

			LOGGER.warn("Authentification Squash TM échouée.");

			return false;
		}

		/*
		 * L'authentification est réussie.
		 *
		 * Les credentials sont déjà présents dans les préférences du Member.
		 *
		 * On sauvegarde maintenant les informations de l'utilisateur Squash TM.
		 */

		SquashTmUtils.setSquashTmRemoteUserInfos(userInfo);

		LOGGER.info("Connexion Squash TM réussie : " + userInfo.getFullName());

		return true;
	}

	/**
	 * Teste la connexion Squash TM.
	 */
	public boolean testConnection(Member member) {

		if (member == null) {
			return false;
		}

		return service.testConnection(member);
	}

	/**
	 * Déconnecte le compte Squash TM du Member courant.
	 */
	public void disconnect() {

		SquashTmUtils.setSquashTmApiToken("");
		SquashTmUtils.setSquashTmUsername("");
		SquashTmUtils.setSquashTmPassword("");

		SquashTmUtils.deleteSquashTmRemoteUserInfos();

		LOGGER.info("Déconnexion Squash TM effectuée.");
	}
}