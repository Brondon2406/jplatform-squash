package co.kozao.jcmsplugin.squashtm.handler;

import java.io.IOException;

import org.apache.log4j.Logger;

import com.jalios.jcms.Channel;
import com.jalios.jcms.Member;
import com.jalios.jcms.handler.JcmsFormHandler;
import com.jalios.util.Util;

import co.kozao.jcmsplugin.squashtm.SquashTmManager;
import co.kozao.jcmsplugin.squashtm.util.SquashTmConstants;
import co.kozao.jcmsplugin.squashtm.util.SquashTmUtils;

/**
 * Form handler responsable de l'authentification Squash TM avec un token API.
 *
 * Le fonctionnement est basé sur le même principe que AuthenticationHandler du
 * plugin Squash.
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

		LOGGER.debug("Token reçu ? " + Util.notEmpty(token));
		this.token = token;
	}
	
	@Override
	public boolean processAction() throws IOException {
		LOGGER.debug("opValidateToken = " + opValidateToken);
		
		if (opValidateToken && validateToken()) {
			return doPerformToken();
		}
		return super.processAction();
	}

	public boolean validateToken() {
		if (Util.isEmpty(token)) {
			setErrorMsg(SquashTmConstants.I18N_TOKEN_FORM_ERROR_EMPTY);
			return false;
		}

		return true;
	}

	private boolean doPerformToken() {
		
		Member member = Channel.getChannel().getCurrentLoggedMember();
		
		if (member == null) {

			setErrorMsg(SquashTmConstants.I18N_TOKEN_FORM_ERROR_MEMBER);
			return false;
		}

		SquashTmUtils.setSquashTmApiToken(token);
		boolean connected = SquashTmManager.getInstance().connect(member);

		if (!connected) {
			SquashTmUtils.setSquashTmApiToken("");
			setErrorMsg(SquashTmConstants.I18N_TOKEN_FORM_ERROR_INVALID);
			return false;
		}

		LOGGER.info("Authentification Token Squash TM réussie.");
		return true;
	}
}