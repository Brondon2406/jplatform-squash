package co.kozao.jcmsplugin.squashtm.model.squashimport;

import java.util.ArrayList;
import java.util.List;

public class ImportSquashTmData {

    private String fileName;
    private String projectName;

    private List<ImportSquashTmModule> modules;

    public ImportSquashTmData() {
        this.modules = new ArrayList<>();
    }

    public ImportSquashTmData(String fileName, String projectName) {
        this.fileName = fileName;
        this.projectName = projectName;
        this.modules = new ArrayList<>();
    }

    public void addModule(ImportSquashTmModule module) {
        if (module != null) {
            modules.add(module);
        }
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public List<ImportSquashTmModule> getModules() {
        return modules;
    }

    public void setModules(List<ImportSquashTmModule> modules) {
        this.modules = modules;
    }
}