package co.kozao.jcmsplugin.squashtm.handler;

import java.io.IOException;

import com.jalios.jcms.handler.JcmsFormHandler;

import co.kozao.jcmsplugin.squashtm.util.SquashTmManager;

/**
 * @author Severin kengne
 *
 */
public class SquashTmLogoutFormHandler extends JcmsFormHandler {
	private Boolean submit = false;


	@Override
	public boolean processAction() throws IOException {
		if (submit) {
			return performSubmit();
		}
		return false;
	}

	public Boolean getSubmit() {
		return submit;
	}

	public void setSubmit(Boolean submit) {
		this.submit = submit;
	}

	public boolean performSubmit() {
		SquashTmManager.getInstance().disconnect();
		return true;

	}

}
