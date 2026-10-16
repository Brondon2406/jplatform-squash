package co.kozao.jcmsplugin.squashtm.model.squashimport;

/**
 * Représente l'état d'avancement d'une importation Squash TM.
 *
 * Cet objet est consulté pendant que l'importation est en cours.
 */
public class ImportSquashTmProgress {

	private volatile int percentage;
	private volatile String stage;
	private volatile String message;
	private volatile boolean completed;
	private volatile boolean success;
	private volatile long startTime;
	private volatile long endTime;

	/**
	 * Initialise le suivi de l'importation.
	 */
	public ImportSquashTmProgress() {
		this.percentage = 0;
		this.stage = "INITIALISATION";
		this.message = "Préparation de l'importation...";
		this.completed = false;
		this.success = false;
		this.startTime = System.currentTimeMillis();
		this.endTime = 0L;
	}

	public synchronized void update(int percentage, String stage, String message) {
		if (completed) {
			return;
		}

		this.percentage = Math.max(0, Math.min(99, percentage));
		this.stage = stage;
		this.message = message;
	}

	public synchronized void finish(boolean success, String message) {
		if (completed) {
			return;
		}

		this.percentage = 100;
		this.success = success;
		this.message = message;
		this.completed = true;
		this.endTime = System.currentTimeMillis();

		if (success) {
			this.stage = "TERMINE";
		} else {
			this.stage = "TERMINE_AVEC_ERREURS";
		}
	}

	public int getPercentage() {
		return percentage;
	}

	public String getStage() {
		return stage;
	}

	public String getMessage() {
		return message;
	}

	public boolean isCompleted() {
		return completed;
	}

	public boolean isSuccess() {
		return success;
	}

	public long getElapsedTime() {
		long end = completed ? endTime : System.currentTimeMillis();
		return Math.max(0L, end - startTime);
	}
}