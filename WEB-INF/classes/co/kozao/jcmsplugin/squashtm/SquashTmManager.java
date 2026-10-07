package co.kozao.jcmsplugin.squashtm;

import org.apache.log4j.Logger;

import com.jalios.jcms.Member;
import com.jalios.jcms.plugin.PluginManager;
import com.jalios.util.Util;

import co.kozao.jcmsplugin.squashtm.model.api.LoggedUserInfo;
import co.kozao.jcmsplugin.squashtm.service.SquashTmService;
import co.kozao.jcmsplugin.squashtm.util.SquashTmConstants;
import co.kozao.jcmsplugin.squashtm.util.SquashTmUtils;

/**
 * Manager principal du connecteur Squash TM.
 *
 * <p>
 * Cette classe centralise les opérations de connexion, de déconnexion et de
 * récupération des informations de l'utilisateur Squash TM.
 * </p>
 */
public class SquashTmManager {

	private static final Logger LOGGER = Logger.getLogger(SquashTmManager.class);

	private static SquashTmManager SINGLETON;

	private final SquashTmService service;

	/**
	 * Constructeur privé du Singleton.
	 */
	private SquashTmManager() {
		service = new SquashTmService();
	}

	/**
	 * Retourne l'instance unique du Manager.
	 *
	 * @return instance du SquashTmManager
	 */
	public static synchronized SquashTmManager getInstance() {

		if (SINGLETON == null) {
			SINGLETON = new SquashTmManager();
		}

		return SINGLETON;
	}

	/**
	 * Vérifie que le plugin Squash TM est actif.
	 *
	 * @param member membre JCMS courant
	 * @return true si le plugin est actif
	 */
	public boolean isPluginActive(Member member) {

		if (member == null) {
			return false;
		}

		return PluginManager.getInstance().isPluginActive(SquashTmConstants.PLUGIN_NAME);
	}

	/**
	 * Vérifie que le connecteur peut communiquer avec Squash TM.
	 *
	 * @param member membre JCMS courant
	 * @return true si le plugin est actif et que l'URL Squash TM est configurée
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
	 * Vérifie si un utilisateur Squash TM est actuellement connecté pour le membre
	 * JCMS courant.
	 *
	 * @return true si les informations Squash TM sont présentes
	 */
	public boolean isConnect() {

		LoggedUserInfo userInfo = SquashTmUtils
				.getSquashTmRemoteUserInfos(com.jalios.jcms.Channel.getChannel().getCurrentLoggedMember());

		return userInfo != null;
	}

	/**
	 * Retourne les informations de l'utilisateur Squash TM actuellement connecté.
	 *
	 * <p>
	 * Cette méthode sans paramètre est conservée car les JSP du plugin l'utilisent
	 * directement.
	 * </p>
	 *
	 * @return informations de l'utilisateur Squash TM ou null
	 */
	public LoggedUserInfo getLoggedUserInfo() {

		Member member = com.jalios.jcms.Channel.getChannel().getCurrentLoggedMember();

		return SquashTmUtils.getSquashTmRemoteUserInfos(member);
	}

	/**
	 * Retourne les informations de l'utilisateur Squash TM associé à un membre JCMS
	 * donné.
	 *
	 * @param member membre JCMS
	 * @return informations Squash TM ou null
	 */
	public LoggedUserInfo getLoggedUserInfo(Member member) {

		if (member == null) {
			return null;
		}

		return SquashTmUtils.getSquashTmRemoteUserInfos(member);
	}

	/**
	 * Lance une authentification auprès de Squash TM.
	 *
	 * <p>
	 * Le mode TOKEN ou BASIC est déterminé par le Service.
	 * </p>
	 *
	 * <p>
	 * Le Member JCMS sert uniquement de contexte pour récupérer les credentials
	 * Squash TM associés à cet utilisateur.
	 * </p>
	 *
	 * @param member membre JCMS courant
	 * @return true si l'authentification réussit
	 */
	public boolean connect(Member member) {

		if (member == null) {

			LOGGER.warn("Member JCMS null.");

			return false;
		}

		LOGGER.info("========== CONNEXION SQUASH TM ==========");

		LOGGER.info("Member JCMS = " + member.getLogin());

		LoggedUserInfo userInfo = service.authenticate(member);

		/*
		 * L'authentification a échoué.
		 */
		if (userInfo == null) {

			LOGGER.warn("Authentification Squash TM échouée.");

			LOGGER.error("Aucune information utilisateur Squash TM n'a été retournée.");

			return false;
		}

		/*
		 * L'authentification est réussie.
		 *
		 * IMPORTANT : userInfo contient uniquement les informations provenant de Squash
		 * TM.
		 *
		 * On ne complète pas ces informations avec le Member JCMS.
		 */
		SquashTmUtils.setSquashTmRemoteUserInfos(userInfo);

		/*
		 * Vérification des données réellement sauvegardées.
		 */
		LoggedUserInfo savedInfo = SquashTmUtils.getSquashTmRemoteUserInfos(member);

		LOGGER.error("========== INFOS SQUASH TM SAUVEGARDEES ==========");

		LOGGER.error("Saved ID       = " + (savedInfo != null ? savedInfo.getId() : "NULL"));

		LOGGER.error("Saved FullName = " + (savedInfo != null ? savedInfo.getFullName() : "NULL"));

		LOGGER.error("Saved Email    = " + (savedInfo != null ? savedInfo.getEmailAddr() : "NULL"));

		LOGGER.error("Saved Created  = " + (savedInfo != null ? savedInfo.getCreatedAt() : "NULL"));

		LOGGER.info("Connexion Squash TM réussie : " + userInfo.getFullName());

		return true;
	}

	/**
	 * Teste la connexion à Squash TM.
	 *
	 * @param member membre JCMS courant
	 * @return true si Squash TM répond correctement
	 */
	public boolean testConnection(Member member) {

		if (member == null) {
			return false;
		}

		return service.testConnection(member);
	}

	/**
     * Déconnecte le compte Squash TM associé au membre courant.
     */
    public void disconnect() {

        SquashTmUtils.setSquashTmApiToken("");
        SquashTmUtils.setSquashTmUsername("");
        SquashTmUtils.setSquashTmPassword("");

        SquashTmUtils.deleteSquashTmRemoteUserInfos();

        LOGGER.info(
                "Déconnexion Squash TM effectuée.");
    }
}
