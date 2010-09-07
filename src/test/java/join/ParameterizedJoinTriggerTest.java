package join;

import hudson.model.Cause.UserCause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.StringParameterValue;
import hudson.plugins.parameterizedtrigger.CurrentBuildParameters;
import hudson.plugins.parameterizedtrigger.PredefinedBuildParameters;
import hudson.plugins.parameterizedtrigger.ResultCondition;
import java.util.List;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;

/**
 *
 * @author wolfs
 */
public class ParameterizedJoinTriggerTest extends BasicJoinPluginTest {

    public void testParametrizedJoinDependencyGetsBuilt() throws Exception {
        final CaptureEnvironmentBuilder builder = new CaptureEnvironmentBuilder();
        joinProject.getBuildersList().add(builder);

        addParameterizedJoinTriggerToProject(splitProject,joinProject, new PredefinedBuildParameters("KEY=value"));

        FreeStyleProject intermediateProject =
                createFreeStyleProject();
        addProjectToSplitProject(splitProject, intermediateProject);

        hudson.rebuildDependencyGraph();

        final ParametersAction buildParameters = new ParametersAction(new StringParameterValue("KEY", "value"));
        final FreeStyleBuild splitBuild = splitProject.scheduleBuild2(0, new UserCause(), buildParameters).get();
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
                createFreeStyleProject();
        addProjectToSplitProject(splitProject, intermediateProject);

        hudson.rebuildDependencyGraph();

        final FreeStyleBuild splitBuild = splitProject.scheduleBuild2(0, new UserCause(), new ParametersAction(new StringParameterValue("KEY", "value"))).get();
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

        final FreeStyleBuild splitBuild = splitProject.scheduleBuild2(0, new UserCause(), new ParametersAction(new StringParameterValue("KEY", "value"))).get();
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

        final FreeStyleBuild splitBuild = splitProject.scheduleBuild2(0, new UserCause(), new ParametersAction(new StringParameterValue("KEY", "value"))).get();
        waitUntilNoActivity();
        final List<FreeStyleBuild> intermediateBuilds = JoinTriggerTest.<FreeStyleProject, FreeStyleBuild>getUniqueBuilds(intermediateProjects);
        final FreeStyleBuild joinBuild = getUniqueBuild(joinProject);
        assertInSequence(splitBuild, intermediateBuilds, joinBuild);
        assertNotNull("Builder should capture environment", builder.getEnvVars());
        assertEquals("value", builder.getEnvVars().get("KEY"));
    }
}
