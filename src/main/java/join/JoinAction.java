package join;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Cause.UpstreamCause;
import hudson.model.Descriptor;
import hudson.model.Items;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.tasks.Publisher;
import hudson.util.DescribableList;

import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class JoinAction implements Action {
    private List<String> pendingDownstreamProjects;
    private List<String> completedDownstreamProjects;
    private List<String> consideredBuilds;
    private transient String joinProjects;
    private DescribableList<Publisher, Descriptor<Publisher>> joinPublishers;
    private boolean evenIfDownstreamUnstable;
    private Result overallResult;

    public JoinAction(JoinTrigger joinTrigger, List<AbstractProject<?,?>> downstream) {
        this.pendingDownstreamProjects = new LinkedList<String>();
        for(AbstractProject<?,?> project : downstream) {
            if(!project.isDisabled()) {
                this.pendingDownstreamProjects.add(project.getName());
            }
        }
        this.joinProjects = joinTrigger.getJoinProjectsValue();
        this.joinPublishers = joinTrigger.getJoinPublishers();
        this.evenIfDownstreamUnstable = joinTrigger.getEvenIfDownstreamUnstable();
        this.completedDownstreamProjects = new LinkedList<String>();
        this.consideredBuilds = new LinkedList<String>();
        this.overallResult = Result.SUCCESS;
    }

    public String getDisplayName() {
        return null;
    }

    public String getIconFileName() {
        return null;
    }

    public String getUrlName() {
        return "join";
    }

    // upstreamBuild is the build that contains this JoinAction.
    public synchronized boolean downstreamFinished(AbstractBuild<?,?> upstreamBuild, AbstractBuild<?,?> finishedBuild, TaskListener listener) {
        if (!consideredBuilds.contains(finishedBuild.toString())) {
            consideredBuilds.add(finishedBuild.toString());
            String finishedBuildProjectName = finishedBuild.getProject().getName();
            if(pendingDownstreamProjects.remove(finishedBuildProjectName)) {
                this.overallResult = this.overallResult.combine(finishedBuild.getResult());
                completedDownstreamProjects.add(finishedBuildProjectName);
                checkPendingDownstream(upstreamBuild, listener);
            } else {
                listener.getLogger().println("[Join] Pending does not contain " + finishedBuildProjectName);
            }
        }
        return pendingDownstreamProjects.isEmpty();
    }

    public Result getOverallResult() {
        return overallResult;
    }

    public synchronized void checkPendingDownstream(AbstractBuild<?,?> owner, TaskListener listener) {
        if(pendingDownstreamProjects.isEmpty()) {
            listener.getLogger().println("All downstream projects complete!");
            Result threshold = this.evenIfDownstreamUnstable ? Result.UNSTABLE : Result.SUCCESS;
            if(this.overallResult.isWorseThan(threshold)) {
                listener.getLogger().println("Minimum result threshold not met for join project");
            } else {
                // Construct a launcher since CopyArchiver wants to get the
                // channel from it. We use the channel of the node where the
                // splitProject was built on.
                final Launcher launcher = new NoopLauncher(listener, owner);

                for(Publisher pub : this.joinPublishers) {
                    try {
                        pub.perform(owner, launcher, (BuildListener)listener);
                    } catch (InterruptedException e) {
                        listener.getLogger().print(e.toString());
                    } catch (IOException e) {
                        listener.getLogger().print(e.toString());
                    }
                }
                if (!JoinTrigger.canDeclare(owner.getProject())) {
                    List<AbstractProject> projects =
                        Items.fromNameList(joinProjects, AbstractProject.class);
                    for(AbstractProject project : projects) {
                        listener.getLogger().println("Scheduling join project: " + project.getName());
                        project.scheduleBuild(new JoinCause(owner));
                    }
                }
            }
        } else {
            listener.getLogger().println("Project " + owner.getProject().getName() + " still waiting for " + pendingDownstreamProjects.size() + " builds to complete");
        }
    }

    public class JoinCause extends UpstreamCause {

        public JoinCause(Run<?, ?> arg0) {
            super(arg0);
        }

    }

    private static class NoopLauncher extends Launcher {

        public NoopLauncher(TaskListener listener, AbstractBuild<?,?> build) {
            super(listener, build.getBuiltOn().getChannel());
        }

        @Override
        public Proc launch(ProcStarter starter) throws IOException {
            throw new UnsupportedOperationException("Not supported.");
        }

        @Override
        public Channel launchChannel(String[] cmd, OutputStream out, FilePath workDir, Map<String, String> envVars) throws IOException, InterruptedException {
            throw new UnsupportedOperationException("Not supported.");
        }

        @Override
        public void kill(Map<String, String> modelEnvVars) throws IOException, InterruptedException {
            throw new UnsupportedOperationException("Not supported.");
        }

    }

}
