package co.kozao.jcmsplugin.squashtm.model.enumkind;

public enum Kind {

	REQUIREMENTS("/requirements", "/projects/%d/requirements", "/requirements/%d",
			"name,path,project[name],current_version[reference,status,criticality,category[code],created_by,created_on,last_modified_by,last_modified_on,verifying_test_cases]"),

	TEST_CASES("/test-cases", "/projects/%d/test-cases", "/test-cases/%d",
			"name,reference,path,project[name],created_by,created_on,last_modified_on,importance,status,nature[code],type[code],automatable,verified_requirements"),

	CAMPAIGNS("/campaigns", "/projects/%d/campaigns", "/campaigns/%d",
			"name,reference,path,project[name],status,created_by,created_on,last_modified_on,scheduled_start_date,scheduled_end_date,actual_start_date,actual_end_date,iterations");

	public final String flatPath;
	public final String projectPath;
	public final String detailPath;
	public final String fields;

	Kind(String flatPath, String projectPath, String detailPath, String fields) {
		this.flatPath = flatPath;
		this.projectPath = projectPath;
		this.detailPath = detailPath;
		this.fields = fields;
	}
}