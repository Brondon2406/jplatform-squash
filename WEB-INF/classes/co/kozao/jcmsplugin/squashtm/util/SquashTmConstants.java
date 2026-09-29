
package co.kozao.jcmsplugin.squashtm;

/**
 * Constantes utilisées par le plugin Squash TM.
 *
 * <p>
 * Cette classe centralise les noms des propriétés du plugin ainsi que les
 * préférences utilisateur JCMS.
 */
public final class SquashTmConstants {

	/**
	 * Constructeur privé.
	 *
	 * <p>
	 * Cette classe ne doit pas être instanciée.
	 */
	private SquashTmConstants() {
	}

	/*
	 * ============================================================ NOM DU PLUGIN====================================================
	 */

	public static final String PLUGIN_NAME = "SquashTmPlugin";

	/*
	 * ============================================== PROPRIETES DUPLUGIN ============================================================
	 */

	/**
	 * URL du serveur Squash TM.
	 */
	public static final String SQUASHTM_REMOTE_SERVER_URL = "jcmsplugin.squashtm.provider.app.auth.domain-name.url";

	/**
	 * Mode d'authentification utilisé par le connecteur.
	 */
	public static final String SQUASHTM_API_AUTHENTICATION_MODE = "jcmsplugin.squashtm.provider.authentication-mode";

	/*
	 * ============================================================ VALEURS DES MODES D'AUTHENTIFICATION=========================================================
	 */

	public static final String SQUASHTM_API_AUTHENTICATION_MODE_TOKEN = "TOKEN";

	public static final String SQUASHTM_API_AUTHENTICATION_MODE_BASIC = "BASIC";

	/*
	 * ============================================================ PREFERENCES JCMS MEMBER ============================================================
	 */

	/**
	 * Token API Squash TM du membre courant.
	 */
	public static final String SQUASHTM_MEMBER_API_TOKEN = "squashtm.api.token";

	/**
	 * Nom d'utilisateur Squash TM.
	 */
	public static final String SQUASHTM_MEMBER_USERNAME = "squashtm.username";

	/**
	 * Mot de passe Squash TM.
	 */
	public static final String SQUASHTM_MEMBER_PASSWORD = "squashtm.password";

	/**
	 * Informations de l'utilisateur Squash TM connecté.
	 */
	public static final String SQUASHTM_REMOTE_APP_USER_INFO = "squashtm.remote.user.info";
	
	/**
	 * Icon App Sidebar Property
	 */
	public static final String SQUASHTM_ICON_SIDEBAR_APP = "jcmsplugin.squashtm.properties.app.sidebar.icon.chooser-image";
}