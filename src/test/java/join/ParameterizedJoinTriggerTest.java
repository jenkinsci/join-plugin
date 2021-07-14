package join;

import hudson.model.*;
import hudson.model.Cause.UserIdCause;
import hudson.plugins.parameterizedtrigger.CurrentBuildParameters;
import hudson.plugins.parameterizedtrigger.PredefinedBuildParameters;
import hudson.plugins.parameterizedtrigger.ResultCondition;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;

import java.util.List;

/**
 *
 * @author wolfs
 */
public class ParameterizedJoinTriggerTest extends BasicJoinPluginTest {

    @Override
    public void setUp() throws Exception {
        super.setUp();
        // https://www.jenkins.io/blog/2016/05/11/security-update/
        ParameterDefinition paramDef = new StringParameterDefinition("KEY", "value", "https://www.jenkins.io/blog/2016/05/11/security-update/");
        ParametersDefinitionProperty paramsDef = new ParametersDefinitionProperty(paramDef);
        joinProject.addProperty(paramsDef);
    }

    public void testParametrizedJoinDependencyGetsBuilt() throws Exception {
        final CaptureEnvironmentBuilder builder = new CaptureEnvironmentBuilder();
        joinProject.getBuildersList().add(builder);

        addParameterizedJoinTriggerToProject(splitProject,joinProject, new PredefinedBuildParameters("KEY=value"));

        FreeStyleProject intermediateProject =
                createFreeStyleProjectWithNoQuietPeriod();
        addProjectToSplitProject(splitProject, intermediateProject);

        hudson.rebuildDependencyGraph();

        // final ParametersAction buildParameters = new ParametersAction(new StringParameterValue("KEY", "value"));
        FreeStyleBuild splitBuild = buildAndAssertSuccess(splitProject);
        waitUntilNoActivity();

        final FreeStyleBuild intermediateBuild = getUniqueBuild(intermediateProject);
        final FreeStyleBuild joinBuild = getUniqueBuild(joinProject);
        assertInSequence(splitBuild, intermediateBuild, joinBuild);
        assertNotNull("Builder should capture environment", builder.getEnvVars());
        assertEquals("value", builder.getEnvVars().get("KEY"));

    }

    public void testParametrizedJoinDependencyCurrentBuildParams() throws Exception {
        final CaptureEnvironmentBuilder builder = new CaptureEnvironmentBuilder();
        joinProject.getBuildersList().add(builder);

        addParameterizedJoinTriggerToProject(splitProject,joinProject, new CurrentBuildParameters());

        FreeStyleProject intermediateProject =
                createFreeStyleProjectWithNoQuietPeriod();
        addProjectToSplitProject(splitProject, intermediateProject);

        hudson.rebuildDependencyGraph();

        final FreeStyleBuild splitBuild = splitProject.scheduleBuild2(0, new UserIdCause(), new ParametersAction(new StringParameterValue("KEY", "value"))).get();
        waitUntilNoActivity();

        final FreeStyleBuild intermediateBuild = getUniqueBuild(intermediateProject);
        final FreeStyleBuild joinBuild = getUniqueBuild(joinProject);
        assertInSequence(splitBuild, intermediateBuild, joinBuild);
        assertNotNull("Builder should capture environment", builder.getEnvVars());
        assertEquals("value", builder.getEnvVars().get("KEY"));

    }

    public void testParametrizedJoinDependencyUnstable() throws Exception {
        final CaptureEnvironmentBuilder builder = new CaptureEnvironmentBuilder();
        joinProject.getBuildersList().add(builder);
        addParameterizedJoinTriggerToProject(splitProject,joinProject, new CurrentBuildParameters());

        final List<FreeStyleProject> intermediateProjects = createFreeStyleProjects(2);
        FreeStyleProject unstableIntermediateProject =
                createUnstableFreeStyleProject();
        intermediateProjects.add(unstableIntermediateProject);

        addProjectsToSplitProject(splitProject, intermediateProjects);

        hudson.rebuildDependencyGraph();

        final FreeStyleBuild splitBuild = splitProject.scheduleBuild2(0, new UserIdCause(), new ParametersAction(new StringParameterValue("KEY", "value"))).get();
        waitUntilNoActivity();
        final List<FreeStyleBuild> intermediateBuilds = ParameterizedJoinTriggerTest.<FreeStyleProject, FreeStyleBuild>getUniqueBuilds(intermediateProjects);
        assertFinished(splitBuild).beforeStarted(intermediateBuilds);
        assertNotBuilt(joinProject);

    }

    public void testParametrizedJoinDependencyTriggerEvenWhenUnstable() throws Exception {
        final CaptureEnvironmentBuilder builder = new CaptureEnvironmentBuilder();
        joinProject.getBuildersList().add(builder);
        addParameterizedJoinTriggerToProject(splitProject,joinProject, ResultCondition.UNSTABLE_OR_BETTER, new CurrentBuildParameters());

        final List<FreeStyleProject> intermediateProjects = createFreeStyleProjects(2);
        FreeStyleProject unstableIntermediateProject =
                createUnstableFreeStyleProject();
        intermediateProjects.add(unstableIntermediateProject);

        addProjectsToSplitProject(splitProject, intermediateProjects);

        hudson.rebuildDependencyGraph();

        final FreeStyleBuild splitBuild = splitProject.scheduleBuild2(0, new UserIdCause(), new ParametersAction(new StringParameterValue("KEY", "value"))).get();
        waitUntilNoActivity();
        final List<FreeStyleBuild> intermediateBuilds = JoinTriggerTest.<FreeStyleProject, FreeStyleBuild>getUniqueBuilds(intermediateProjects);
        final FreeStyleBuild joinBuild = getUniqueBuild(joinProject);
        assertInSequence(splitBuild, intermediateBuilds, joinBuild);
        assertNotNull("Builder should capture environment", builder.getEnvVars());
        assertEquals("value", builder.getEnvVars().get("KEY"));
    }
}
