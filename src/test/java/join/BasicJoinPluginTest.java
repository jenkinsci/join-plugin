package join;

import hudson.model.*;
import hudson.plugins.parameterizedtrigger.AbstractBuildParameters;
import hudson.plugins.parameterizedtrigger.BuildTriggerConfig;
import hudson.plugins.parameterizedtrigger.ResultCondition;
import hudson.tasks.BuildTrigger;
import hudson.tasks.Publisher;
import hudson.util.DescribableList;
import org.apache.commons.lang.StringUtils;
import org.jvnet.hudson.test.HudsonTestCase;

import java.util.*;

/**
 *
 * @author wolfs
 */
public abstract class BasicJoinPluginTest extends HudsonTestCase {
    protected FreeStyleProject splitProject;
    protected FreeStyleProject joinProject;

    public BasicJoinPluginTest() {}
    public BasicJoinPluginTest(String name) {
        super(name);
    }

    public static void assertInSequence(AbstractBuild<?,?>... builds) {
        AbstractBuild<?,?> previousBuild = null;
        for (AbstractBuild<?, ?> build : builds) {
            if (previousBuild != null) {
                assertFinished(previousBuild).beforeStarted(build);
            }
            previousBuild = build;
        }
    }

    public static <T extends AbstractBuild<?,?>>
            void assertInSequence(AbstractBuild<?,?> firstBuild, List<T> intermediateBuilds, AbstractBuild<?,?> lastBuild) {
        assertFinished(firstBuild).beforeStarted(intermediateBuilds);
        assertStarted(lastBuild).afterFinished(intermediateBuilds);
    }

    public static <ProjectT extends AbstractProject<ProjectT,BuildT>, BuildT extends AbstractBuild<ProjectT, BuildT>>
            BuildT getUniqueBuild(AbstractProject<ProjectT, BuildT> project) {
        final List<BuildT> builds = project.getBuilds();
        assertTrue("Project " + project + " should have been built exactly once but was triggered " + builds.size() + " times!",
                builds.size() == 1);
        final BuildT build = builds.get(0);
        return build;
    }

    public static <ProjectT extends AbstractProject<ProjectT,BuildT>, BuildT extends AbstractBuild<ProjectT, BuildT>>
            BuildT getLastBuild(AbstractProject<ProjectT, BuildT> project) {
        BuildT build = project.getLastBuild();
        assertNotNull("Project " + project + " should have been built at least once but was not triggered at all!",
                build);
        return build;
    }

    public static void assertNotBuilt(AbstractProject<?, ?> project) {
        final List<?> builds = project.getBuilds();
        assertTrue("Project " + project + " should not have been built!", builds.isEmpty());
    }

    public static <T extends AbstractProject<?, ?>> void assertNotBuilt(List<T> projects) {
        for (AbstractProject<?, ?> project : projects) {
            assertNotBuilt(project);
        }
    }

    public static <ProjectT extends AbstractProject<ProjectT,BuildT>, BuildT extends AbstractBuild<ProjectT, BuildT>>
            List<BuildT> getUniqueBuilds(List<ProjectT> projects) {
        final List<BuildT> buildList = new ArrayList<BuildT>();
        for (AbstractProject<ProjectT, BuildT> project : projects) {
            buildList.add(getUniqueBuild(project));
        }
        return buildList;
    }

    public static <ProjectT extends AbstractProject<ProjectT,BuildT>, BuildT extends AbstractBuild<ProjectT, BuildT>>
            List<BuildT> getLastBuilds(List<ProjectT> projects) {
        final List<BuildT> buildList = new ArrayList<BuildT>();
        for (AbstractProject<ProjectT, BuildT> project : projects) {
            buildList.add(getLastBuild(project));
        }
        return buildList;
    }

    protected FreeStyleProject createFailingFreeStyleProject() throws Exception {
        final FreeStyleProject project = createFreeStyleProjectWithNoQuietPeriod();
        project.getPublishersList().add(ResultSetter.FAILURE());
        return project;
    }

    protected FreeStyleProject createUnstableFreeStyleProject() throws Exception {
        final FreeStyleProject project = createFreeStyleProjectWithNoQuietPeriod();
        project.getPublishersList().add(ResultSetter.UNSTABLE());
        return project;
    }

    protected List<FreeStyleProject> createFreeStyleProjects(int number) throws Exception {
        List<FreeStyleProject> createdProjects = new ArrayList<FreeStyleProject>();
        for (int i=0; i<number; i++) {
            createdProjects.add(createFreeStyleProjectWithNoQuietPeriod());
        }
        return createdProjects;
    }

    public static void addJoinTriggerToSplitProject(AbstractProject<?,?> splitProject, AbstractProject<?,?>... joinProjects) throws Exception {
        List<String> projects = new ArrayList<String>(joinProjects.length);
        for (AbstractProject<?, ?> project : joinProjects) {
            projects.add(project.getName());
        }
        splitProject.getPublishersList().add(new JoinTrigger(new DescribableList<Publisher, Descriptor<Publisher>>(
                Saveable.NOOP), StringUtils.join(projects,","), "SUCCESS"));
    }

    public static void addParameterizedJoinTriggerToProject(AbstractProject<?,?> splitProject, AbstractProject<?,?> joinProject, AbstractBuildParameters... params) throws Exception {
        addParameterizedJoinTriggerToProject(splitProject, joinProject, ResultCondition.SUCCESS, params);
    }

    public static void addParameterizedJoinTriggerToProject(AbstractProject<?,?> splitProject, AbstractProject<?,?> joinProject, ResultCondition condition, AbstractBuildParameters... params) throws Exception {
        final BuildTriggerConfig config = new hudson.plugins.parameterizedtrigger.BuildTriggerConfig(joinProject.getName(), condition, params);
        splitProject.getPublishersList().add(new JoinTrigger(new DescribableList<Publisher, Descriptor<Publisher>>(
                Saveable.NOOP, Collections.singletonList(new hudson.plugins.parameterizedtrigger.BuildTrigger(config))),"","SUCCESS"));
    }

    public static void addProjectToSplitProject(AbstractProject<?,?> splitProject, AbstractProject<?,?> projectToAdd) throws Exception {
        splitProject.getPublishersList().add(new BuildTrigger(projectToAdd.getName(), false));
    }

    public static void addProjectToSplitProject(AbstractProject<?,?> splitProject, String projectToAdd) throws Exception {
        splitProject.getPublishersList().add(new BuildTrigger(projectToAdd, false));
    }

    public static <T extends AbstractProject<?,?>> void addProjectsToSplitProject(AbstractProject<?,?> splitProject, List<T> projectsToAdd) throws Exception {
        List<String> projects = new ArrayList<String>(projectsToAdd.size());
        for (AbstractProject<?, ?> project : projectsToAdd) {
            projects.add(project.getName());
        }
        addProjectToSplitProject(splitProject, StringUtils.join(projects, ","));
    }

    public static BuildTimeConstraint assertFinished(AbstractBuild<?,?> build) {
        return BuildTimeConstraint.finished(build);
    }

    public static BuildTimeConstraint assertStarted(AbstractBuild<?,?> build) {
        return BuildTimeConstraint.started(build);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        splitProject = createFreeStyleProject("splitProject");
        splitProject.setQuietPeriod(0);
        joinProject = createFreeStyleProject("joinProject");
        joinProject.setQuietPeriod(0);
    }

    public static class BuildTimeConstraint {
        private final long millis;
        private final String buildName;
        private final String state;

        public static BuildTimeConstraint finished(AbstractBuild<?,?> build) {
            final long started = build.getTimestamp().getTimeInMillis();
            final long duration = build.getDuration();
            final long finished = started + duration;
            return new BuildTimeConstraint(build.toString(), "finished", finished);
        }

        public static BuildTimeConstraint started(AbstractBuild<?,?> build) {
            final long started = build.getTimestamp().getTimeInMillis();
            return new BuildTimeConstraint(build.toString(), "started", started);
        }

        BuildTimeConstraint(String buildName, String state, long millis) {
            this.millis = millis;
            this.buildName = buildName;
            this.state = state;
        }

        public void beforeStarted(AbstractBuild<?,?> build) {
            final long started = build.getTimestamp().getTimeInMillis();
            assertTrue(String.format("%s not %s before %s started!", buildName, state, build.toString()),
                    millis < started);
        }

        public <T extends AbstractBuild<?,?>> void beforeStarted(List<T> builds) {
            for (T build : builds) {
                beforeStarted(build);
            }
        }


        public void afterFinished(AbstractBuild<?,?> build) {
            final long started = build.getTimestamp().getTimeInMillis();
            final long duration = build.getDuration();
            final long finished = started + duration;
            assertTrue(String.format("%s not %s after %s finished!", buildName, state, build.toString()),
                    millis > finished);
        }

        public <T extends AbstractBuild<?,?>> void afterFinished(List<T> builds) {
            for (T build : builds) {
                afterFinished(build);
            }
        }
    }

    public FreeStyleProject createFreeStyleProjectWithNoQuietPeriod() throws Exception {
        final FreeStyleProject freestyleProject = createFreeStyleProject("FreeStyle_" + UUID.randomUUID());
        freestyleProject.setQuietPeriod(0);
        return freestyleProject;
    }

}
