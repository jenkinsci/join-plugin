package join;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import hudson.matrix.MatrixProject;
import hudson.maven.MavenModuleSet;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.FreeStyleProject;
import jenkins.model.Jenkins;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.ToolInstallations;

import java.util.*;
import java.util.logging.Logger;

/**
 * @author wolfs
 */
@RunWith(Parameterized.class)
public class JoinTriggerAllCombinationsTest extends BasicJoinPluginTest {
    Logger LOG = Logger.getLogger(JoinTriggerAllCombinationsTest.class.getName());

    public static Map<Class<? extends AbstractProject<?,?>>, Function<JoinTriggerAllCombinationsTest,AbstractProject<?,?>>> projectType2Supplier =
            ImmutableMap.<Class<? extends AbstractProject<?,?>>, Function<JoinTriggerAllCombinationsTest,AbstractProject<?,?>>>of(
                    FreeStyleProject.class, new Function<JoinTriggerAllCombinationsTest,AbstractProject<?, ?>>() {
                        @Override
                        public AbstractProject<?, ?> apply(JoinTriggerAllCombinationsTest from) {
                            try {
                                return from.createFreeStyleProjectWithNoQuietPeriod();
                            } catch (Exception e) {
                                fail();
                            }
                            return null;
                        }
                    },
                    MavenModuleSet.class, new Function<JoinTriggerAllCombinationsTest,AbstractProject<?, ?>>() {
                        @Override
                        public AbstractProject<?, ?> apply(JoinTriggerAllCombinationsTest from) {
                            try {
                                MavenModuleSet mavenProject = from.jenkins.createProject(MavenModuleSet.class, "Maven_" + UUID.randomUUID());
                                mavenProject.setQuietPeriod(0);
                                mavenProject.setScm(new ExtractResourceSCM(getClass().getResource("maven-empty-mod.zip")));

                                return mavenProject;
                            } catch (Exception e) {
                                fail();
                            }
                            return null;
                        }
                    },
                    MatrixProject.class, new Function<JoinTriggerAllCombinationsTest,AbstractProject<?, ?>>() {
                        @Override
                        public AbstractProject<?, ?> apply(JoinTriggerAllCombinationsTest from) {
                            try {
                                MatrixProject matrixProject = from.jenkins.createProject(MatrixProject.class, "Matrix_" + UUID.randomUUID());
                                matrixProject.setQuietPeriod(0);
                                return matrixProject;
                            } catch (Exception e) {
                                e.printStackTrace();
                                fail();
                            }
                            return null;
                        }
                    }
            );

    private Class<?> splitProjClass;
    private Class<?> intProjClass;
    private Class<?> joinProjClass;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        List<Object[]> parameters = new ArrayList<Object[]>();
        for (Class<? extends AbstractProject<?, ?>> splitProjClass : projectType2Supplier.keySet()) {
            for (Class<? extends AbstractProject<?, ?>> intProjClass : projectType2Supplier.keySet()) {
                for (Class<? extends AbstractProject<?, ?>> joinProjClass : projectType2Supplier.keySet()) {
                    parameters.add(new Class<?>[] {splitProjClass, intProjClass, joinProjClass});
                }
            }
        }
        return parameters;
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        LOG.info("Starting test with parameters :" + Joiner.on(", ").join(splitProjClass, intProjClass, joinProjClass));
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();    //To change body of overridden methods use File | Settings | File Templates.
    }

    public JoinTriggerAllCombinationsTest(Class<?> splitProjClass, Class<?> intProjClass, Class<?> joinProjClass) {
        super(JoinTriggerAllCombinationsTest.class.getName());
        this.splitProjClass = splitProjClass;
        this.intProjClass = intProjClass;
        this.joinProjClass = joinProjClass;
    }

    @Test
    public void joinProjectShouldBeTriggered() throws Exception {
        assertNotNull(splitProject);
        ToolInstallations.configureDefaultMaven();
        AbstractProject<?,?> splitProject = projectType2Supplier.get(splitProjClass).apply(this);
        AbstractProject<?,?> intProject = projectType2Supplier.get(intProjClass).apply(this);
        AbstractProject<?,?> joinProject = projectType2Supplier.get(joinProjClass).apply(this);
        addProjectToSplitProject(splitProject, intProject);
        addJoinTriggerToSplitProject(splitProject, joinProject);
        Jenkins.get().rebuildDependencyGraph();
        final AbstractBuild<?,?> splitBuild = splitProject.scheduleBuild2(0, new Cause.UserIdCause()).get();
        waitUntilNoActivityUpTo(120*1000);
        AbstractBuild<?, ?> intBuild = getUniqueBuild(intProject);
        AbstractBuild<?, ?> joinBuild = getUniqueBuild(joinProject);
        assertBuildStatusSuccess(intBuild);
        assertBuildStatusSuccess(joinBuild);
        assertInSequence(splitBuild,intBuild,joinBuild);
    }

}
