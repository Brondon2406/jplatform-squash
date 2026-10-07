package co.kozao.jcmsplugin.squashtm.handler;

import java.io.IOException;

import org.apache.log4j.Logger;

import com.jalios.jcms.Channel;
import com.jalios.jcms.Member;
import com.jalios.jcms.handler.JcmsFormHandler;
import com.jalios.util.Util;

import co.kozao.jcmsplugin.squashtm.SquashTmManager;
import co.kozao.jcmsplugin.squashtm.util.SquashTmUtils;

/**
 * Form handler responsable de l'authentification Squash TM avec un token API.
 *
 * Le fonctionnement est basé sur le même principe que AuthenticationHandler du
 * plugin Nuxeo.
 */
public class SquashTmTokenFormHandler extends JcmsFormHandler {

	private static final Logger LOGGER = Logger.getLogger(SquashTmTokenFormHandler.class);

	private boolean opValidateToken = false;
	private String token;

	public String getAppUrl() {
		return "/jcms/plugins/SquashTmPlugin/jsp/app/squashTm.jsp";
	}

	public boolean isOpValidateToken() {
		return opValidateToken;
	}
	
	public void setOpValidateToken(boolean opValidateToken) {
		this.opValidateToken = opValidateToken;
	}

	public String getToken() {
		return token;
	}

	public void setToken(String token) {

		LOGGER.error("========== setToken() APPELE ==========");
		LOGGER.error("Token reçu ? " + Util.notEmpty(token));
		this.token = token;
	}
	
	@Override
	public boolean processAction() throws IOException {
		LOGGER.error("========== processAction() APPELE ==========");
		LOGGER.error("opValidateToken = " + opValidateToken);
		
		if (opValidateToken && validateToken()) {
			return doPerformToken();
		}
		return super.processAction();
	}

	public boolean validateToken() {
		LOGGER.error("========== validateToken() APPELE ==========");
		if (Util.isEmpty(token)) {
			LOGGER.error("========== TOKEN VIDE ==========");
			setErrorMsg("jcmsplugin.squashtm.token-form.error.empty");
			return false;
		}

		LOGGER.error("========== TOKEN PRESENT ==========");
		return true;
	}

	private boolean doPerformToken() {
		LOGGER.error("========== doPerformToken() APPELE ==========");
		
		Member member = Channel.getChannel().getCurrentLoggedMember();
		
		if (member == null) {
			LOGGER.error("========== MEMBER NULL ==========");

			setErrorMsg("jcmsplugin.squashtm.token-form.error.member");
			return false;
		}
		LOGGER.error("========== MEMBER PRESENT ==========");

		SquashTmUtils.setSquashTmApiToken(token);
		LOGGER.error("========== TOKEN SAUVEGARDE ==========");

		boolean connected = SquashTmManager.getInstance().connect(member);

		LOGGER.error("========== RESULTAT CONNECT ========== " + connected);

		if (!connected) {

			SquashTmUtils.setSquashTmApiToken("");

			setErrorMsg("jcmsplugin.squashtm.token-form.error.invalid");
			LOGGER.warn("Échec de connexion avec le Token Squash TM.");
			return false;
		}

		LOGGER.info("Authentification Token Squash TM réussie.");
		return true;
	}
}