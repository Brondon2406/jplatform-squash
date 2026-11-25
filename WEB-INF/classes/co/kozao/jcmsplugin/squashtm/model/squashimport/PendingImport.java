package co.kozao.jcmsplugin.squashtm.model.squashimport;

import java.io.File;

/**
 * @author Severin Kengne
 */
public class PendingImport {

	private final File temporaryFile;
	private final String projectName;
	private final ImportSquashTmAnalysis analysis;
	private final long createdAt;
	private final ImportSquashTmData importData;

	public PendingImport(File temporaryFile, String projectName, ImportSquashTmAnalysis analysis,
			ImportSquashTmData importData) {

		this.temporaryFile = temporaryFile;
		this.projectName = projectName;
		this.analysis = analysis;
		this.importData = importData;
		this.createdAt = System.currentTimeMillis();
	}

	public File getTemporaryFile() {
		return temporaryFile;
	}

	public String getProjectName() {
		return projectName;
	}

	public ImportSquashTmAnalysis getAnalysis() {
		return analysis;
	}

	public long getCreatedAt() {
		return createdAt;
	}

	public ImportSquashTmData getImportData() {
		return importData;
	}

}