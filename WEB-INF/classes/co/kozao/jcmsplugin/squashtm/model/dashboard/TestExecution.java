package co.kozao.jcmsplugin.squashtm.model.dashboard;

/**
 * Représente une exécution d'un cas de test dans Squash TM.
 *
 * @author Severin Kengne
 */
public final class TestExecution {

	private String status;
	private Long executedOn;
	private String assignee;
	private long testCaseId;

	public TestExecution() {
	}

	public TestExecution(String status, Long executedOn, String assignee, long testCaseId) {
		this.status = status;
		this.executedOn = executedOn;
		this.assignee = assignee;
		this.testCaseId = testCaseId;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public Long getExecutedOn() {
		return executedOn;
	}

	public void setExecutedOn(Long executedOn) {
		this.executedOn = executedOn;
	}

	public String getAssignee() {
		return assignee;
	}

	public void setAssignee(String assignee) {
		this.assignee = assignee;
	}

	public long getTestCaseId() {
		return testCaseId;
	}

	public void setTestCaseId(long testCaseId) {
		this.testCaseId = testCaseId;
	}
}