package co.kozao.jcmsplugin.squashtm.model.dashboard;

/**
 * Associe une campagne à l'une de ses exécutions pour les traitements du dashboard.
 *
 * @author Severin Kengne
 */
public final class TestExecutionRow {

    private final SquashTestItem campaign;
    private final TestExecution exec;

    public TestExecutionRow(SquashTestItem campaign, TestExecution exec) {
        this.campaign = campaign;
        this.exec = exec;
    }

    public SquashTestItem getCampaign() {
        return campaign;
    }

    public TestExecution getExec() {
        return exec;
    }
}