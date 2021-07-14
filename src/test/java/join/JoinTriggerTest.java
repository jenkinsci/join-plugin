package join;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.*;
import hudson.model.Cause.UserIdCause;
import hudson.plugins.parameterizedtrigger.BuildTriggerConfig;
import hudson.plugins.parameterizedtrigger.PredefinedBuildParameters;
import hudson.plugins.parameterizedtrigger.ResultCondition;
import hudson.slaves.DumbSlave;
import hudson.tasks.Publisher;
import hudson.util.DescribableList;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;

/**
 *
 * @author wolfs
 */
public class JoinTriggerTest extends BasicJoinPluginTest {

    public void testOneIntermediateProject() throws Exception {
        addJoinTriggerToSplitProject(splitProject, joinProject);

        FreeStyleProject intermediateProject = createFreeStyleProjectWithNoQuietPeriod();
        addProjectToSplitProject(splitProject, intermediateProject);

        hudson.rebuildDependencyGraph();
        
        final FreeStyleBuild splitBuild = splitProject.scheduleBuild2(0, new UserIdCause()).get();
        waitUntilNoActivity();
        final FreeStyleBuild intermediateBuild = getUniqueBuild(intermediateProject);
        final FreeStyleBuild joinBuild = getUniqueBuild(joinProject);
        assertInSequence(splitBuild, intermediateBuild, joinBuild);
    }

    public void testTwoIntermediateProjects() throws Exception {
        runTestForNProjects(2);
    }

    public void testManyIntermediateProjects() throws Exception {
        runTestForNProjects(20);
    }

    public void testForOneProjectUnstable() throws Exception {
        List<FreeStyleProject> intermediateProjects = createFreeStyleProjects(2);
        intermediateProjects.add(createUnstableFreeStyleProject());
        addJoinTriggerToSplitProject(splitProject, joinProject);
        addProjectsToSplitProject(splitProject, intermediateProjects);
        hudson.rebuildDependencyGraph();
        final FreeStyleBuild splitBuild = splitProject.scheduleBuild2(0, new UserIdCause()).get();
        waitUntilNoActivity();
        final List<FreeStyleBuild> intermediateBuilds = JoinTriggerTest.<FreeStyleProject,FreeStyleBuild>getUniqueBuilds(intermediateProjects);
        assertFinished(splitBuild).beforeStarted(intermediateBuilds);
        assertNotBuilt(joinProject);
    }

    public void testIntermediateProjectDisabled() throws Exception {
        FreeStyleProject intermediateProject = createFreeStyleProjectWithNoQuietPeriod();
        
        FreeStyleProject disabledProject = createFreeStyleProjectWithNoQuietPeriod();
        disabledProject.disable();

        List<FreeStyleProject> intermediateProjects = new ArrayList<FreeStyleProject>();
        intermediateProjects.add(intermediateProject);
        intermediateProjects.add(disabledProject);
        addProjectsToSplitProject(splitProject, intermediateProjects);

        addJoinTriggerToSplitProject(splitProject, joinProject);

        hudson.rebuildDependencyGraph();

        FreeStyleBuild splitBuild = splitProject.scheduleBuild2(0, new UserIdCause()).get();
        waitUntilNoActivity();
        FreeStyleBuild intermediateBuild = getUniqueBuild(intermediateProject);
        assertNotBuilt(disabledProject);
        FreeStyleBuild joinBuild = getUniqueBuild(joinProject);
        assertInSequence(splitBuild, intermediateBuild, joinBuild);

        // Enable the project again and trigger a build
        disabledProject.enable();
        splitBuild = splitProject.scheduleBuild2(0, new UserIdCause()).get();
        waitUntilNoActivity();
        List<FreeStyleBuild> intermediateBuilds = JoinTriggerTest.<FreeStyleProject,FreeStyleBuild>getLastBuilds(intermediateProjects);
        joinBuild = getLastBuild(joinProject);
        assertInSequence(splitBuild, intermediateBuilds, joinBuild);
    }
    
    public void testRunNonSplitProject() throws Exception {
        List<FreeStyleProject> intermediateProjects = createFreeStyleProjects(2);
        FreeStyleProject otherIntermediateProject = createFreeStyleProjectWithNoQuietPeriod();
        final ArrayList<FreeStyleProject> allIntermediateProjects = new ArrayList<FreeStyleProject>();
        allIntermediateProjects.addAll(intermediateProjects);
        allIntermediateProjects.add(otherIntermediateProject);
        addJoinTriggerToSplitProject(splitProject, joinProject);
        addProjectsToSplitProject(splitProject, allIntermediateProjects);
        hudson.rebuildDependencyGraph();
        final FreeStyleBuild intermediateBuild = otherIntermediateProject.scheduleBuild2(0, new UserIdCause()).get();
        waitUntilNoActivity();
        assertNotBuilt(joinProject);
        assertNotBuilt(splitProject);
        assertNotBuilt(intermediateProjects);
        getUniqueBuild(otherIntermediateProject);
    }

    public void testTwoJoinProjects() throws Exception {
        List<FreeStyleProject> intermediateProjects = createFreeStyleProjects(2);
        final FreeStyleProject otherJoinProject = createFreeStyleProjectWithNoQuietPeriod();
        addJoinTriggerToSplitProject(splitProject, joinProject, otherJoinProject);
        addProjectsToSplitProject(splitProject, intermediateProjects);
        hudson.rebuildDependencyGraph();
        final FreeStyleBuild splitBuild = splitProject.scheduleBuild2(0, new UserIdCause()).get();
        waitUntilNoActivity();
        final List<FreeStyleBuild> intermediateBuilds = JoinTriggerTest.<FreeStyleProject,FreeStyleBuild>getUniqueBuilds(intermediateProjects);
        final FreeStyleBuild joinBuild = getUniqueBuild(joinProject);
        final FreeStyleBuild otherJoinBuild = getUniqueBuild(otherJoinProject);
        assertInSequence(splitBuild, intermediateBuilds, joinBuild);
        assertInSequence(splitBuild, intermediateBuilds, otherJoinBuild);
    }

    private void runTestForNProjects(int n) throws InterruptedException, Exception, ExecutionException {
        List<FreeStyleProject> intermediateProjects = createFreeStyleProjects(n);
        addJoinTriggerToSplitProject(splitProject, joinProject);
        addProjectsToSplitProject(splitProject, intermediateProjects);
        hudson.rebuildDependencyGraph();
        final FreeStyleBuild splitBuild = splitProject.scheduleBuild2(0, new UserIdCause()).get();
        waitUntilNoActivity();
        final List<FreeStyleBuild> intermediateBuilds = JoinTriggerTest.<FreeStyleProject,FreeStyleBuild>getUniqueBuilds(intermediateProjects);
        final FreeStyleBuild joinBuild = getUniqueBuild(joinProject);
        assertInSequence(splitBuild, intermediateBuilds, joinBuild);
    }

    public void testOneIntermediateParameterizedProject() throws Exception {
        addJoinTriggerToSplitProject(splitProject, joinProject);

        Project<FreeStyleProject,FreeStyleBuild> intermediateProject =
                createFreeStyleProjectWithNoQuietPeriod();
        // https://www.jenkins.io/blog/2016/05/11/security-update/
        ParameterDefinition paramDef = new StringParameterDefinition("KEY", "value", "https://www.jenkins.io/blog/2016/05/11/security-update/");
        ParametersDefinitionProperty paramsDef = new ParametersDefinitionProperty(paramDef);
        intermediateProject.addProperty(paramsDef);
        final CaptureEnvironmentBuilder builder = new CaptureEnvironmentBuilder();
        intermediateProject.getBuildersList().add(builder);
        BuildTriggerConfig buildTriggerConfig =
                new BuildTriggerConfig(intermediateProject.getName(),
                        ResultCondition.SUCCESS,
                        new PredefinedBuildParameters("KEY=value"));
        splitProject.getPublishersList().add(
                new hudson.plugins.parameterizedtrigger.BuildTrigger(buildTriggerConfig));

        hudson.rebuildDependencyGraph();
        final FreeStyleBuild splitBuild = splitProject.scheduleBuild2(0, new UserIdCause()).get();
        waitUntilNoActivity();
        final FreeStyleBuild intermediateBuild = getUniqueBuild(intermediateProject);
        final FreeStyleBuild joinBuild = getUniqueBuild(joinProject);
        assertInSequence(splitBuild, intermediateBuild, joinBuild);
        assertNotNull("Builder should capture environment", builder.getEnvVars());
        assertEquals("value", builder.getEnvVars().get("KEY"));
    }

    public void testNoIntermediateProject() throws Exception {
        addJoinTriggerToSplitProject(splitProject, joinProject);
        hudson.rebuildDependencyGraph();
        final FreeStyleBuild splitBuild = splitProject.scheduleBuild2(0, new UserIdCause()).get();
        waitUntilNoActivity();
        final FreeStyleBuild joinBuild = getUniqueBuild(joinProject);
        assertFinished(splitBuild).beforeStarted(joinBuild);
    }

    public void testSlave() throws Exception {
        List<FreeStyleProject> intermediateProjects = createFreeStyleProjects(2);

        FreeStyleProject slaveProject = createFreeStyleProjectWithNoQuietPeriod();
        final DumbSlave slave = createOnlineSlave();
        slaveProject.setAssignedNode(slave);

        final ArrayList<FreeStyleProject> allIntermediateProjects = new ArrayList<FreeStyleProject>();
        allIntermediateProjects.addAll(intermediateProjects);
        allIntermediateProjects.add(slaveProject);
        addJoinTriggerToSplitProject(splitProject, joinProject);
        addProjectsToSplitProject(splitProject, allIntermediateProjects);
        hudson.rebuildDependencyGraph();

        final FreeStyleBuild splitBuild = splitProject.scheduleBuild2(0, new UserIdCause()).get();
        waitUntilNoActivity();

        final List<FreeStyleBuild> intermediateBuilds = JoinTriggerTest.<FreeStyleProject,FreeStyleBuild>getUniqueBuilds(intermediateProjects);
        final FreeStyleBuild slaveBuild = getUniqueBuild(slaveProject);
        final FreeStyleBuild joinBuild = getUniqueBuild(joinProject);
        assertInSequence(splitBuild,intermediateBuilds,joinBuild);
        assertInSequence(splitBuild,slaveBuild,joinBuild);
        assertEquals(slave, slaveBuild.getBuiltOn());
        hudson.removeNode(slave);
    }

    public void testRoundTrip() throws Exception {
        final JoinTrigger before = new JoinTrigger(
                new DescribableList<Publisher, Descriptor<Publisher>>(Saveable.NOOP),
                joinProject.getName(),
                "SUCCESS");
        splitProject.getPublishersList().add(before);
        final WebClient webClient = createWebClient();
        // webClient.setThrowExceptionOnFailingAjax(false); // from htmlunit
        final HtmlPage configPage = webClient.getPage(splitProject, "configure");

        submit(configPage.getFormByName("config"));
        final JoinTrigger after = splitProject.getPublishersList().get(JoinTrigger.class);

        assertEquals(before.getJoinProjectsValue(),after.getJoinProjectsValue());
        assertEquals(before.getResultThreshold(), after.getResultThreshold());
        assertEquals(0, after.getJoinPublishers().size());
    }

    public void testRoundTripWithPublishers() throws Exception {
        addParameterizedJoinTriggerToProject(splitProject,joinProject, new PredefinedBuildParameters("KEY=value"));

        final JoinTrigger before = splitProject.getPublishersList().get(JoinTrigger.class);
        final WebClient webClient = createWebClient();
        // webClient.setThrowExceptionOnFailingAjax(false); // from htmlunit
        final HtmlPage configPage = webClient.getPage(splitProject, "configure");

        submit(configPage.getFormByName("config"));
        final JoinTrigger after = splitProject.getPublishersList().get(JoinTrigger.class);

        assertEquals(before.getJoinProjectsValue(),after.getJoinProjectsValue());
        assertEquals(before.getResultThreshold(), after.getResultThreshold());
        assertEquals(1, after.getJoinPublishers().size());
        final hudson.plugins.parameterizedtrigger.BuildTrigger paramBtBefore = before.getJoinPublishers().get(hudson.plugins.parameterizedtrigger.BuildTrigger.class);
        final hudson.plugins.parameterizedtrigger.BuildTrigger paramBtAfter = after.getJoinPublishers().get(hudson.plugins.parameterizedtrigger.BuildTrigger.class);
        final List<BuildTriggerConfig> configsBefore = paramBtBefore.getConfigs();
        final List<BuildTriggerConfig> configsAfter = paramBtAfter.getConfigs();
        assertEquals(1, configsAfter.size());
        final BuildTriggerConfig configBefore = configsBefore.iterator().next();
        final BuildTriggerConfig configAfter = configsAfter.iterator().next();
        assertEqualBeans(configBefore, configAfter, "projects,condition");
    }

}
