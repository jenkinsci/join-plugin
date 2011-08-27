package join;

import com.google.common.collect.ImmutableList;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.plugins.downstream_ext.DownstreamTrigger;

import java.io.IOException;

/**
 * @author wolfs
 */
public class DownstreamExtTest extends BasicJoinPluginTest {

    public void testDownstreamExtTrigger() throws Exception {
        FreeStyleProject intermediateProject = createFreeStyleProjectWithNoQuietPeriod();
        addDownstreamExtTrigger(intermediateProject);
        addJoinTriggerToSplitProject(splitProject,joinProject);

        hudson.rebuildDependencyGraph();

        FreeStyleBuild splitBuild = buildAndAssertSuccess(splitProject);
        waitUntilNoActivity();

        final FreeStyleBuild intermediateBuild = getUniqueBuild(intermediateProject);
        final FreeStyleBuild joinBuild = getUniqueBuild(joinProject);
        assertInSequence(splitBuild, intermediateBuild, joinBuild);
    }

    public void testDownstreamExtWithOtherIntermediateProject() throws Exception {
        FreeStyleProject intermediateProject1 = createFreeStyleProjectWithNoQuietPeriod();
        FreeStyleProject intermediateProject2 = createFreeStyleProjectWithNoQuietPeriod();
        addDownstreamExtTrigger(intermediateProject1);
        addProjectToSplitProject(splitProject, intermediateProject2);
        addJoinTriggerToSplitProject(splitProject, joinProject);

        hudson.rebuildDependencyGraph();

        final FreeStyleBuild splitBuild = buildAndAssertSuccess(splitProject);
        waitUntilNoActivity();
        final FreeStyleBuild intermediateBuild1 = JoinTriggerTest.getUniqueBuild(intermediateProject1);
        final FreeStyleBuild intermediateBuild2 = JoinTriggerTest.getUniqueBuild(intermediateProject2);
        final FreeStyleBuild joinBuild = getUniqueBuild(joinProject);
        assertInSequence(splitBuild, ImmutableList.of(intermediateBuild1, intermediateBuild2), joinBuild);
    }

    private void addDownstreamExtTrigger(FreeStyleProject intermediateProject) throws IOException {
        splitProject.getPublishersList().add(new DownstreamTrigger(intermediateProject.getName(),
                Result.SUCCESS, false, DownstreamTrigger.Strategy.AND_LOWER, null));
    }

}
