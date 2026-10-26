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
 * Handler du formulaire d'authentification Basic.
 */
public class SquashTmBasicFormHandler extends JcmsFormHandler {

	private static final Logger LOGGER = Logger.getLogger(SquashTmBasicFormHandler.class);
	
	private boolean opSign = false;
	private String username;
	private String password;

	public String getAppUrl() {
		return "/jcms/plugins/SquashTmPlugin/jsp/app/squashTm.jsp";
	}

	/**
	 * Traite l'action envoyée par le formulaire.
	 */
	@Override
	public boolean processAction() throws IOException {

		LOGGER.error("========== SquashTmBasicFormHandler.processAction() ==========");
		LOGGER.error("opSign = " + opSign);

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

		LOGGER.error("========== validateBasic() ==========");

		if (Util.isEmpty(username)) {

			setErrorMsg("jcmsplugin.squashtm.basic-form.error.username");

			return false;
		}

		if (Util.isEmpty(password)) {

			setErrorMsg("jcmsplugin.squashtm.basic-form.error.password");

			return false;
		}

		/*
		 * Récupération du membre JCMS connecté.
		 */
		Member member = Channel.getChannel().getCurrentLoggedMember();

		if (member == null) {

			setErrorMsg("jcmsplugin.squashtm.basic-form.error.member");

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

			setErrorMsg("jcmsplugin.squashtm.basic-form.error.invalid");

			LOGGER.warn("Échec de connexion Basic Squash TM.");

			return false;
		}

		/*
		 * Authentification réussie.
		 */
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