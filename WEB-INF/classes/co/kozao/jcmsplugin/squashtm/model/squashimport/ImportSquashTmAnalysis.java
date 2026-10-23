
package co.kozao.jcmsplugin.squashtm.model.squashimport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Contient les résultats de l'analyse préalable d'un fichier Excel.
 *
 * Cette classe ne communique pas avec Squash TM.
 */
public class ImportSquashTmAnalysis {

    private final String projectName;
    private final int detectedLineCount;
    private final int validLineCount;
    private final int anomalousLineCount;
    private final int duplicateLineCount;
    private final List<String> inconsistencies;

    public ImportSquashTmAnalysis(
            String projectName,
            int detectedLineCount,
            int validLineCount,
            int anomalousLineCount,
            int duplicateLineCount,
            List<String> inconsistencies) {

        this.projectName = projectName;
        this.detectedLineCount = detectedLineCount;
        this.validLineCount = validLineCount;
        this.anomalousLineCount = anomalousLineCount;
        this.duplicateLineCount = duplicateLineCount;

        this.inconsistencies = inconsistencies == null
                ? new ArrayList<>()
                : new ArrayList<>(inconsistencies);
    }

    public String getProjectName() {
        return projectName;
    }

    public int getDetectedLineCount() {
        return detectedLineCount;
    }

    public int getValidLineCount() {
        return validLineCount;
    }

    public int getAnomalousLineCount() {
        return anomalousLineCount;
    }

    public int getDuplicateLineCount() {
        return duplicateLineCount;
    }

    public List<String> getInconsistencies() {
        return Collections.unmodifiableList(inconsistencies);
    }

    public double getValidityRate() {
        if (detectedLineCount == 0) {
            return 0.0;
        }

        return (validLineCount * 100.0) / detectedLineCount;
    }

    public boolean hasAnomalies() {
        return anomalousLineCount > 0;
    }
}
