/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Brian Westrich, Martin Eigenbrodt
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package join;

import hudson.model.Cause.UserCause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Project;
import hudson.plugins.parameterizedtrigger.BuildTriggerConfig;
import hudson.plugins.parameterizedtrigger.PredefinedBuildParameters;
import hudson.plugins.parameterizedtrigger.ResultCondition;
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

        FreeStyleProject intermediateProject = createFreeStyleProject();
        addProjectToSplitProject(splitProject, intermediateProject);

        hudson.rebuildDependencyGraph();
        
        final FreeStyleBuild splitBuild = splitProject.scheduleBuild2(0, new UserCause()).get();
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
        final FreeStyleBuild splitBuild = splitProject.scheduleBuild2(0, new UserCause()).get();
        waitUntilNoActivity();
        final List<FreeStyleBuild> intermediateBuilds = JoinTriggerTest.<FreeStyleProject,FreeStyleBuild>getUniqueBuilds(intermediateProjects);
        assertFinished(splitBuild).beforeStarted(intermediateBuilds);
        assertNotBuilt(joinProject);
    }

    public void testRunNonSplitProject() throws Exception {
        List<FreeStyleProject> intermediateProjects = createFreeStyleProjects(2);
        FreeStyleProject otherIntermediateProject = createFreeStyleProject();
        final ArrayList<FreeStyleProject> allIntermediateProjects = new ArrayList<FreeStyleProject>();
        allIntermediateProjects.addAll(intermediateProjects);
        allIntermediateProjects.add(otherIntermediateProject);
        addJoinTriggerToSplitProject(splitProject, joinProject);
        addProjectsToSplitProject(splitProject, allIntermediateProjects);
        hudson.rebuildDependencyGraph();
        final FreeStyleBuild intermediateBuild = otherIntermediateProject.scheduleBuild2(0, new UserCause()).get();
        waitUntilNoActivity();
        assertNotBuilt(joinProject);
        assertNotBuilt(splitProject);
        assertNotBuilt(intermediateProjects);
        getUniqueBuild(otherIntermediateProject);
    }

    public void testTwoJoinProjects() throws Exception {
        List<FreeStyleProject> intermediateProjects = createFreeStyleProjects(2);
        final FreeStyleProject otherJoinProject = createFreeStyleProject();
        addJoinTriggerToSplitProject(splitProject, joinProject, otherJoinProject);
        addProjectsToSplitProject(splitProject, intermediateProjects);
        hudson.rebuildDependencyGraph();
        final FreeStyleBuild splitBuild = splitProject.scheduleBuild2(0, new UserCause()).get();
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
        final FreeStyleBuild splitBuild = splitProject.scheduleBuild2(0, new UserCause()).get();
        waitUntilNoActivity();
        final List<FreeStyleBuild> intermediateBuilds = JoinTriggerTest.<FreeStyleProject,FreeStyleBuild>getUniqueBuilds(intermediateProjects);
        final FreeStyleBuild joinBuild = getUniqueBuild(joinProject);
        assertInSequence(splitBuild, intermediateBuilds, joinBuild);
    }

    public void testOneIntermediateParameterizedProject() throws Exception {
        addJoinTriggerToSplitProject(splitProject, joinProject);

        Project<FreeStyleProject,FreeStyleBuild> intermediateProject =
                createFreeStyleProject();
        final CaptureEnvironmentBuilder builder = new CaptureEnvironmentBuilder();
        intermediateProject.getBuildersList().add(builder);
        BuildTriggerConfig buildTriggerConfig =
                new BuildTriggerConfig(intermediateProject.getName(),
                        ResultCondition.SUCCESS,
                        new PredefinedBuildParameters("KEY=value"));
        splitProject.getPublishersList().add(
                new hudson.plugins.parameterizedtrigger.BuildTrigger(buildTriggerConfig));

        hudson.rebuildDependencyGraph();
        final FreeStyleBuild splitBuild = splitProject.scheduleBuild2(0, new UserCause()).get();
        waitUntilNoActivity();
        final FreeStyleBuild intermediateBuild = getUniqueBuild(intermediateProject);
        final FreeStyleBuild joinBuild = getUniqueBuild(joinProject);
        assertInSequence(splitBuild, intermediateBuild, joinBuild);
        assertNotNull("Builder should capture environment", builder.getEnvVars());
        assertEquals("value", builder.getEnvVars().get("KEY"));
    }

}
