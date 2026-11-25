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
 * @author Severin Kengne
 */
public class SquashTmBasicFormHandler extends JcmsFormHandler {

	private static final Logger LOGGER = Logger.getLogger(SquashTmBasicFormHandler.class);
	
	private boolean opSign = false;
	private String username;
	private String password;

	public String getAppUrl() {
		return "/jcms/plugins/SquashTmPlugin/jsp/app/squashTm.jsp";
	}

	
	@Override
	public boolean processAction() throws IOException {

		LOGGER.debug("opSign = " + opSign);

		if (opSign && validateBasic()) {
			return true;
		}
		return super.processAction();
	}

	/**
	 * Valide les informations du formulaire Basic et tente l'authentification
	 * auprès de Squash TM.
	 */
	public boolean validateBasic() {

		if (Util.isEmpty(username)) {

			setErrorMsg(SquashTmConstants.I18N_BASIC_FORM_ERROR_USERNAME);

			return false;
		}

		if (Util.isEmpty(password)) {

			setErrorMsg(SquashTmConstants.I18N_BASIC_FORM_ERROR_PASSWORD);

			return false;
		}

		
		Member member = Channel.getChannel().getCurrentLoggedMember();

		if (member == null) {

			setErrorMsg(SquashTmConstants.I18N_BASIC_FORM_ERROR_MEMBER);

			return false;
		}

		SquashTmUtils.setSquashTmUsername(username);
		SquashTmUtils.setSquashTmPassword(password);

		LOGGER.info("Credentials Squash TM reçus pour l'authentification Basic.");

		boolean connected = SquashTmManager.getInstance().connect(member);

		LOGGER.info("Résultat authentification Basic : " + connected);

		if (!connected) {

			SquashTmUtils.setSquashTmUsername("");
			SquashTmUtils.setSquashTmPassword("");

			setErrorMsg(SquashTmConstants.I18N_BASIC_FORM_ERROR_INVALID);

			LOGGER.warn("Échec de connexion Basic Squash TM.");

			return false;
		}

		
		LOGGER.info("Authentification Basic Squash TM réussie.");

		return true;
	}

	public boolean isOpSign() {
		return opSign;
	}

	public void setOpSign(boolean opSign) {
		this.opSign = opSign;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}
}