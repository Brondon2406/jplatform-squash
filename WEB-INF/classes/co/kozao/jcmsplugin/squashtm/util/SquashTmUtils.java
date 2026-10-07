package co.kozao.jcmsplugin.squashtm.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.log4j.Logger;

import com.jalios.jcms.Channel;
import com.jalios.jcms.Member;
import com.jalios.util.Util;

import co.kozao.jcmsplugin.squashtm.model.AuthentificationMode;
import co.kozao.jcmsplugin.squashtm.model.api.LoggedUserInfo;

/**
 * Utilitaires du plugin Squash TM.
 *
 * <p>
 * Cette classe centralise :
 * <ul>
 * <li>les propriétés du plugin ;</li>
 * <li>les préférences du Member JCMS ;</li>
 * <li>le mode d'authentification ;</li>
 * <li>les informations utilisateur Squash TM.</li>
 * </ul>
 */
public final class SquashTmUtils {

	private static final Logger LOGGER = Logger.getLogger(SquashTmUtils.class);

	private static final Channel channel = Channel.getChannel();

	private SquashTmUtils() {
	}

	/*
	 * ============================================================ SERVEUR SQUASH
	 * TM ============================================================
	 */

	public static String getSquashTmServerUrl() {

		String url = channel.getProperty(SquashTmConstants.SQUASHTM_REMOTE_SERVER_URL);

		if (Util.isEmpty(url)) {
			return null;
		}

		return url.replaceAll("/+$", "");
	}

	public static String getAppSidebarIcon() {
		String iconApp = channel.getProperty(SquashTmConstants.SQUASHTM_ICON_SIDEBAR_APP);
		if (Util.isEmpty(iconApp)) {
			iconApp = channel.getProperty("icon.jcmsplugin.box.box-app-sidebar");
		}

		return iconApp;
	}

	/*
	 * ============================================================ MODE
	 * AUTHENTIFICATION ============================================================
	 */

	public static AuthentificationMode getAuthentificationMode() {
		Channel channel = Channel.getChannel();

		String authMode = channel.getProperty(SquashTmConstants.SQUASHTM_API_AUTHENTICATION_MODE);

		if (authMode == null || authMode.trim().isEmpty()) {
			return AuthentificationMode.BASIC;
		}

		try {
			return AuthentificationMode.valueOf(authMode.trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			return AuthentificationMode.BASIC;
		}
	}

	/*
	 * ============================================================ TOKEN
	 * ============================================================
	 */

	public static String getSquashTmApiToken(Member member) {

		if (member == null) {
			return null;
		}

		return member.getPreference(SquashTmConstants.SQUASHTM_MEMBER_API_TOKEN);
	}

	public static String getSquashTmApiToken() {

		Member member = channel.getCurrentLoggedMember();

		return getSquashTmApiToken(member);
	}

	public static void setSquashTmApiToken(String token) {

		Member member = channel.getCurrentLoggedMember();

		if (member == null) {
			return;
		}

		member.savePreference(SquashTmConstants.SQUASHTM_MEMBER_API_TOKEN, Util.notEmpty(token) ? token : "");
	}

	/*
	 * ============================================================ BASIC USERNAME
	 * ============================================================
	 */

	public static String getSquashTmUsername(Member member) {

		if (member == null) {
			return null;
		}

		return member.getPreference(SquashTmConstants.SQUASHTM_MEMBER_USERNAME);
	}

	public static String getSquashTmUsername() {

		Member member = channel.getCurrentLoggedMember();

		return getSquashTmUsername(member);
	}

	public static void setSquashTmUsername(String username) {

		Member member = channel.getCurrentLoggedMember();

		if (member == null) {
			return;
		}

		member.savePreference(SquashTmConstants.SQUASHTM_MEMBER_USERNAME, Util.notEmpty(username) ? username : "");
	}

	/*
	 * ============================================================ BASIC PASSWORD
	 * ============================================================
	 */

	public static String getSquashTmPassword(Member member) {

		if (member == null) {
			return null;
		}

		return member.getPreference(SquashTmConstants.SQUASHTM_MEMBER_PASSWORD);
	}

	public static String getSquashTmPassword() {

		Member member = channel.getCurrentLoggedMember();

		return getSquashTmPassword(member);
	}

	public static void setSquashTmPassword(String password) {

		Member member = channel.getCurrentLoggedMember();

		if (member == null) {
			return;
		}

		member.savePreference(SquashTmConstants.SQUASHTM_MEMBER_PASSWORD, Util.notEmpty(password) ? password : "");
	}

	/*
	 * ============================================================ UTILISATEUR
	 * SQUASH TM ============================================================
	 */

	public static LoggedUserInfo getSquashTmRemoteUserInfos(Member member) {
		if (member == null) {
			return null;
		}

		String json = member.getPreference(SquashTmConstants.SQUASHTM_REMOTE_APP_USER_INFO);

		if (Util.isEmpty(json)) {
			return null;
		}

		return LoggedUserInfo.fromJson(json);
	}

	public static void setSquashTmRemoteUserInfos(LoggedUserInfo userInfo) {

		Member member = channel.getCurrentLoggedMember();

		if (member == null) {
			return;
		}

		if (userInfo == null) {

			member.savePreference(SquashTmConstants.SQUASHTM_REMOTE_APP_USER_INFO, "");

			return;
		}

		member.savePreference(SquashTmConstants.SQUASHTM_REMOTE_APP_USER_INFO, userInfo.toJson());
	}

	public static void deleteSquashTmRemoteUserInfos() {

		Member member = channel.getCurrentLoggedMember();

		if (member == null) {
			return;
		}

		member.savePreference(SquashTmConstants.SQUASHTM_REMOTE_APP_USER_INFO, "");
	}

	/*
	 * ============================================================ DATE
	 * ================================================
	 */

	public static Date getStandardStringToDate(String value) {

		if (Util.isEmpty(value)) {
			return null;
		}

		try {

			SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

			return format.parse(value);

		} catch (ParseException e) {

			LOGGER.warn("Impossible de convertir la date Squash TM : " + value);

			return null;
		}
	}
}
