package co.kozao.jcmsplugin.squashtm.model.squashimport;

	/**
	 * Représente une étape d'un cas de test.
	 *
	 * Une étape possède une action et un résultat attendu.
	 */
	public class ImportSquashTmTestStep {

	    private String action;
	    private String expectedResult;

	    public ImportSquashTmTestStep() {
	    }

	    public ImportSquashTmTestStep(String action, String expectedResult) {
	        this.action = action;
	        this.expectedResult = expectedResult;
	    }

	    public String getAction() {
	        return action;
	    }

	    public void setAction(String action) {
	        this.action = action;
	    }

	    public String getExpectedResult() {
	        return expectedResult;
	    }

	    public void setExpectedResult(String expectedResult) {
	        this.expectedResult = expectedResult;
	    }
	}