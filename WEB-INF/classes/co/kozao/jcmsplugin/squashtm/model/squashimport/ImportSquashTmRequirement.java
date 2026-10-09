package co.kozao.jcmsplugin.squashtm.model.squashimport;

/**
 * Représente une exigence à importer dans Squash TM.
 */
public class ImportSquashTmRequirement {

    private String reference;
    private String name;

    public ImportSquashTmRequirement() {
    }

    public ImportSquashTmRequirement(String reference, String name) {
        this.reference = reference;
        this.name = name;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
};
