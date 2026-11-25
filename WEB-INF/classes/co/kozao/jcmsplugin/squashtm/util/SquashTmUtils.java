package co.kozao.jcmsplugin.squashtm.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.log4j.Logger;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;

import com.jalios.jcms.Channel;
import com.jalios.jcms.Member;
import com.jalios.util.Util;

import co.kozao.jcmsplugin.squashtm.model.AuthentificationMode;
import co.kozao.jcmsplugin.squashtm.model.api.LoggedUserInfo;

/**
 * @author Severin Kengne
 */
public final class SquashTmUtils {

	private static final Logger LOGGER = Logger.getLogger(SquashTmUtils.class);

	private static final Channel channel = Channel.getChannel();

	private SquashTmUtils() {
	}


	public static String getSquashTmServerUrl() {

		String url = channel.getProperty(SquashTmConstants.SQUASHTM_REMOTE_SERVER_URL);

		if (Util.isEmpty(url)) {
			return null;
		}

		return url.replaceAll("/+$", "");
	}

	public static String getAppSidebarIcon() {
		String iconApp = channel.getProperty(SquashTmConstants.SQUASHTM_ICON_CHOOSER_IMAGE_SIDEBAR_APP);
		if (Util.isEmpty(iconApp)) {
			iconApp = channel.getProperty(SquashTmConstants.SQUASHTM_ICON_SIDEBAR_APP);
		}

		return iconApp;
	}


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


	public static boolean canAdministrateTemplate() {
		Member member = channel.getCurrentLoggedMember();

		return member != null && member.isAdmin();
	}


	public static String escapeHtml(String value) {

		if (value == null) {
			return "";
		}

		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
				.replace("'", "&#39;");
	}

	
	public static String htmlToText(String text) {

		if (text == null) {
			return "";
		}

		String value = text.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("(?i)</p>", "\n").replaceAll("<[^>]*>", "");

		value = value.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
				.replace("&#39;", "'").replace("&amp;", "&");

		value = value.replace('\u00A0', ' ').replaceAll("[ \\t]+", " ").replaceAll(" ?\\n ?", "\n")
				.replaceAll("\\n{3,}", "\n\n");

		return value.trim();
	}

	
	public static String abbreviate(String value, int maxLength) {

		return value.length() <= maxLength ? value : value.substring(0, maxLength - 1) + "…";
	}

	
	public static boolean isBlank(String value) {

		return value == null || value.trim().isEmpty();
	}


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


	public static String getCellValue(Row row, int columnIndex, DataFormatter formatter) {

		Cell cell = row.getCell(columnIndex);

		if (cell == null) {
			return "";
		}

		return formatter.formatCellValue(cell).trim();
	}
}