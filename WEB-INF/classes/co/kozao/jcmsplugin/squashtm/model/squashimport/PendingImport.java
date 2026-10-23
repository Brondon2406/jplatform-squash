package co.kozao.jcmsplugin.squashtm.model.squashimport;

import java.io.File;

/**
 * Représente un import Excel analysé, mais pas encore confirmé.
 *
 * Les données sont conservées temporairement jusqu'à la confirmation
 * ou l'annulation de l'importation.
 */
public class PendingImport {

    private final File temporaryFile;
    private final String projectName;
    private final ImportSquashTmAnalysis analysis;
    private final long createdAt;

    public PendingImport(
            File temporaryFile,
            String projectName,
            ImportSquashTmAnalysis analysis) {

        this.temporaryFile = temporaryFile;
        this.projectName = projectName;
        this.analysis = analysis;
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
}