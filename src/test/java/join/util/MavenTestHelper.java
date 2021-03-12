package join.util;

import hudson.FilePath;
import hudson.Functions;
import hudson.Launcher;
import hudson.tasks.Maven;
import hudson.util.StreamTaskListener;
import hudson.util.jna.GNUCLibrary;
import jenkins.model.Jenkins;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MavenTestHelper {

    private static final Logger LOGGER = Logger.getLogger(MavenTestHelper.class.getName());

    /**
     * Returns the older default Maven, while still allowing specification of
     * other bundled Mavens.
     */
    public static Maven.MavenInstallation configureDefaultMaven() throws Exception {
        return configureDefaultMaven("apache-maven-2.2.1", Maven.MavenInstallation.MAVEN_20);
    }

    /**
     * Locates Maven and configure that as the only Maven in the system.
     *
     * @param mavenVersion desired maven version (e.g. {@code apache-maven-3.5.0})
     * @param mavenReqVersion minimum maven version defined using the constants {@link Maven.MavenInstallation#MAVEN_20},
     *    {@link Maven.MavenInstallation#MAVEN_21} and {@link Maven.MavenInstallation#MAVEN_30}
     */
    public static Maven.MavenInstallation configureDefaultMaven(String mavenVersion, int mavenReqVersion) throws Exception {
        // first if we are running inside Maven, pick that Maven, if it meets the criteria we require..
        File buildDirectory = new File(System.getProperty("buildDirectory", "target")); // TODO relative path
        File mvnHome = new File(buildDirectory, mavenVersion);
        if (mvnHome.exists()) {
            Maven.MavenInstallation mavenInstallation = new Maven.MavenInstallation("default", mvnHome.getAbsolutePath(), JenkinsRule.NO_PROPERTIES);
            Jenkins.get().getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(mavenInstallation);
            return mavenInstallation;
        }

        // Does maven.home point to a Maven installation which satisfies mavenReqVersion?
        String home = System.getProperty("maven.home");
        if (home != null) {
            Maven.MavenInstallation mavenInstallation = new Maven.MavenInstallation("default", home, JenkinsRule.NO_PROPERTIES);
            if (mavenInstallation.meetsMavenReqVersion(new Launcher.LocalLauncher(StreamTaskListener.fromStdout()), mavenReqVersion)) {
                Jenkins.get().getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(mavenInstallation);
                return mavenInstallation;
            }
        }

        // otherwise extract the copy we have.
        // this happens when a test is invoked from an IDE, for example.
        LOGGER.log(Level.WARNING,"Extracting a copy of Maven bundled in the test harness into {0}. "
                + "To avoid a performance hit, set the system property ''maven.home'' to point to a Maven installation.", mvnHome);
        FilePath mvn = Jenkins.get().getRootPath().createTempFile("maven", "zip");
        mvn.copyFrom(JenkinsRule.class.getClassLoader().getResource(mavenVersion + "-bin.zip"));
        mvn.unzip(new FilePath(buildDirectory));
        // TODO: switch to tar that preserves file permissions more easily
        if (Functions.isGlibcSupported()) {
                GNUCLibrary.LIBC.chmod(new File(mvnHome, "bin/mvn").getPath(), 0755);
        }

        Maven.MavenInstallation mavenInstallation = new Maven.MavenInstallation("default",
                mvnHome.getAbsolutePath(), JenkinsRule.NO_PROPERTIES);
        Jenkins.get().getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(mavenInstallation);
        return mavenInstallation;
    }
}
