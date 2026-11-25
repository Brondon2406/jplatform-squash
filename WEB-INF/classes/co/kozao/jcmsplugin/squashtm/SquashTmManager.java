package co.kozao.jcmsplugin.squashtm;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.time.StopWatch;
import org.apache.log4j.Logger;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.jalios.jcms.Channel;
import com.jalios.jcms.Member;
import com.jalios.jcms.plugin.PluginManager;
import com.jalios.util.Util;

import co.kozao.jcmsplugin.squashtm.model.api.LoggedUserInfo;
import co.kozao.jcmsplugin.squashtm.service.SquashTmDashboardService;
import co.kozao.jcmsplugin.squashtm.service.SquashTmService;
import co.kozao.jcmsplugin.squashtm.util.SquashTmConstants;
import co.kozao.jcmsplugin.squashtm.util.SquashTmUtils;

/**
 * @author Severin Kengne
 */
public class SquashTmManager {

	private static final Logger LOGGER = Logger.getLogger(SquashTmManager.class);

	private static SquashTmManager SINGLETON;
	private final SquashTmService service;

	private final Cache<String, String> API_CACHE = CacheBuilder.newBuilder().expireAfterWrite(20, TimeUnit.MINUTES)
			.maximumSize(10).build();

	private SquashTmManager() {
		service = new SquashTmService();
	}

	
	public static synchronized SquashTmManager getInstance() {

		if (SINGLETON == null) {
			SINGLETON = new SquashTmManager();
		}

		return SINGLETON;
	}

	public static boolean isCacheEnable() {
		return Channel.getChannel().getBooleanProperty(SquashTmConstants.SQUASHTM_CACHE_ENABLED, false);
	}

	public boolean isSquashTmApiReachable() {
		StopWatch stopWatch = new StopWatch();
		if (LOGGER.isDebugEnabled()) {
			stopWatch.start();
		}
		boolean apiReachable = false;
		if (isCacheEnable()) {
			String lastAccessTime = API_CACHE.getIfPresent(SquashTmConstants.SQUASHTM_CACHE_KEY_API_REACHABLE);
			if (Util.notEmpty(lastAccessTime)) {
				if (LOGGER.isDebugEnabled()) {
					LOGGER.debug(
							"Last time the API was successfully accessed (read from cache) : '" + lastAccessTime + "'");
				}
				apiReachable = true;
			} else {
				apiReachable = checkSquashTmApiAccess();
				if (apiReachable) {
					Date now = new Date();
					API_CACHE.put(SquashTmConstants.SQUASHTM_CACHE_KEY_API_REACHABLE, now.toString());
					if (LOGGER.isDebugEnabled()) {
						LOGGER.debug("Time of last to API added in cache : '" + lastAccessTime + "'");
					}
				}
			}
		} else {
			apiReachable = checkSquashTmApiAccess();
		}

		if (LOGGER.isDebugEnabled()) {
			stopWatch.stop();
			LOGGER.debug(stopWatch.getTime(TimeUnit.MILLISECONDS)
					+ " ms time elapsed for executing 'isSquashTmApiReachable()'");
		}
		return apiReachable;
	}

	private boolean checkSquashTmApiAccess() {
		boolean answer = false;
		String urlRemoteSquashTm = SquashTmUtils.getSquashTmServerUrl();
		try {
			URL u = URI.create(urlRemoteSquashTm).toURL();
			URLConnection conn = u.openConnection();
			conn.connect();
			conn.getInputStream().close();
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("Internet connection established");
			}
			answer = true;
		} catch (Exception e) {
			answer = false;
			LOGGER.info("No Internet Connection available, please connect with internet");
		}
		return answer;
	}

	
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

	
	public boolean isConnect() {

		LoggedUserInfo userInfo = SquashTmUtils
				.getSquashTmRemoteUserInfos(Channel.getChannel().getCurrentLoggedMember());

		return userInfo != null;
	}

	
	public LoggedUserInfo getLoggedUserInfo() {
		Member member = Channel.getChannel().getCurrentLoggedMember();
		return SquashTmUtils.getSquashTmRemoteUserInfos(member);
	}

	
	public LoggedUserInfo getLoggedUserInfo(Member member) {

		if (member == null) {
			return null;
		}

		return SquashTmUtils.getSquashTmRemoteUserInfos(member);
	}

	/**
	 * Lance une authentification auprès de Squash TM.
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

		if (userInfo == null) {

			LOGGER.warn("Authentification Squash TM échouée.");

			LOGGER.warn("Aucune information utilisateur Squash TM n'a été retournée.");

			return false;
		}

		
		SquashTmUtils.setSquashTmRemoteUserInfos(userInfo);

		LOGGER.info("Connexion Squash TM réussie : " + userInfo.getFullName());

		return true;
	}

	
	public SquashTmDashboardService newDashboardService(Member member) throws IOException {

		if (member == null) {
			throw new IOException("Aucun membre JCMS connecté.");
		}

		return new SquashTmDashboardService(service.resolveAuthorization(), member.getId());
	}

	
	public boolean testConnection(Member member) {

		if (member == null) {
			return false;
		}

		return service.testConnection(member);
	}

	
	public void disconnect() {

		SquashTmUtils.setSquashTmApiToken("");
		SquashTmUtils.setSquashTmUsername("");
		SquashTmUtils.setSquashTmPassword("");

		SquashTmUtils.deleteSquashTmRemoteUserInfos();

		LOGGER.info("Déconnexion Squash TM effectuée.");
	}
}
