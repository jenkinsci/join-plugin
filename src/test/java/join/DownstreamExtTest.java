package join;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.plugins.downstream_ext.DownstreamTrigger;

/**
 * @author wolfs
 */
public class DownstreamExtTest extends BasicJoinPluginTest {

    public void testDownstreamExtTrigger() throws Exception {
        FreeStyleProject intermediateProject = createFreeStyleProjectWithNoQuietPeriod();
        splitProject.getPublishersList().add(new DownstreamTrigger(intermediateProject.getName(),
                Result.SUCCESS, false, DownstreamTrigger.Strategy.AND_LOWER,null));
        addJoinTriggerToSplitProject(splitProject,joinProject);

        hudson.rebuildDependencyGraph();

        FreeStyleBuild splitBuild = buildAndAssertSuccess(splitProject);
        waitUntilNoActivity();

        final FreeStyleBuild intermediateBuild = getUniqueBuild(intermediateProject);
        final FreeStyleBuild joinBuild = getUniqueBuild(joinProject);
        assertInSequence(splitBuild, intermediateBuild, joinBuild);
    }

}
