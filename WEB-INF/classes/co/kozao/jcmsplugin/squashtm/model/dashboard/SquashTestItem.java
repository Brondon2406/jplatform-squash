package co.kozao.jcmsplugin.squashtm.model.dashboard;

import java.util.ArrayList;
import java.util.List;

/**
 * Élément métier utilisé par le dashboard Squash TM : exigence, cas de test, campagne ou rapport.
 *
 * @author Severin Kengne
 */
public final class SquashTestItem {

    private long id;
    private long projectId;
    private String projectName;
    private String name;
    private String reference;
    private String path;
    private String status;
    private String author;
    private Long createdOn;
    private Long modifiedOn;
    private String criticality;
    private String category;
    private String importance;
    private String nature;
    private String type;
    private String automatable;
    private int links;
    private Long scheduledStart;
    private Long scheduledEnd;
    private Long actualStart;
    private Long actualEnd;
    private int iterations;
    private final List<TestExecution> execs = new ArrayList<TestExecution>();
    
    

    public SquashTestItem(long id, long projectId, String projectName, String name, String reference, String path, String status,
			String author, Long createdOn, Long modifiedOn, String criticality, String category, String importance,
			String nature, String type, String automatable, int links, Long scheduledStart, Long scheduledEnd,
			Long actualStart, Long actualEnd, int iterations) {
		super();
		this.id = id;
		this.projectId = projectId;
		this.projectName = projectName;
		this.name = name;
		this.reference = reference;
		this.path = path;
		this.status = status;
		this.author = author;
		this.createdOn = createdOn;
		this.modifiedOn = modifiedOn;
		this.criticality = criticality;
		this.category = category;
		this.importance = importance;
		this.nature = nature;
		this.type = type;
		this.automatable = automatable;
		this.links = links;
		this.scheduledStart = scheduledStart;
		this.scheduledEnd = scheduledEnd;
		this.actualStart = actualStart;
		this.actualEnd = actualEnd;
		this.iterations = iterations;
	}

	public SquashTestItem() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getProjectId() {
        return projectId;
    }

    public void setProjectId(long projectId) {
        this.projectId = projectId;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public Long getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(Long createdOn) {
        this.createdOn = createdOn;
    }

    public Long getModifiedOn() {
        return modifiedOn;
    }

    public void setModifiedOn(Long modifiedOn) {
        this.modifiedOn = modifiedOn;
    }

    public String getCriticality() {
        return criticality;
    }

    public void setCriticality(String criticality) {
        this.criticality = criticality;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getImportance() {
        return importance;
    }

    public void setImportance(String importance) {
        this.importance = importance;
    }

    public String getNature() {
        return nature;
    }

    public void setNature(String nature) {
        this.nature = nature;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getAutomatable() {
        return automatable;
    }

    public void setAutomatable(String automatable) {
        this.automatable = automatable;
    }

    public int getLinks() {
        return links;
    }

    public void setLinks(int links) {
        this.links = links;
    }

    public Long getScheduledStart() {
        return scheduledStart;
    }

    public void setScheduledStart(Long scheduledStart) {
        this.scheduledStart = scheduledStart;
    }

    public Long getScheduledEnd() {
        return scheduledEnd;
    }

    public void setScheduledEnd(Long scheduledEnd) {
        this.scheduledEnd = scheduledEnd;
    }

    public Long getActualStart() {
        return actualStart;
    }

    public void setActualStart(Long actualStart) {
        this.actualStart = actualStart;
    }

    public Long getActualEnd() {
        return actualEnd;
    }

    public void setActualEnd(Long actualEnd) {
        this.actualEnd = actualEnd;
    }

    public int getIterations() {
        return iterations;
    }

    public void setIterations(int iterations) {
        this.iterations = iterations;
    }
    
    public List<TestExecution> getExecs() {
        return execs;
    }
}