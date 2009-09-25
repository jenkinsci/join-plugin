package join;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildTrigger;
import hudson.tasks.Publisher;
import hudson.util.DescribableList;
import hudson.model.Items;
import hudson.model.Cause.UpstreamCause;

public class JoinAction implements Action {
    private List<String> pendingDownstreamProjects;
    private List<String> completedDownstreamProjects;
    private transient String joinProjects;
    private DescribableList<Publisher, Descriptor<Publisher>> joinPublishers;
    private boolean evenIfDownstreamUnstable;
    private Result overallResult;
    
    public JoinAction(JoinTrigger joinTrigger, BuildTrigger buildTrigger, ArrayList<String> otherDownstream) {
        String[] downstreamProjects = buildTrigger==null ? 
                new String[0] : buildTrigger.getChildProjectsValue().split(",");
        this.pendingDownstreamProjects = new LinkedList<String>();
        for(String proj : otherDownstream) {
            this.pendingDownstreamProjects.add(proj.trim());
        }
        for(String proj : downstreamProjects) {
            this.pendingDownstreamProjects.add(proj.trim());
        }
        this.joinProjects = joinTrigger.getJoinProjectsValue();
        this.joinPublishers = joinTrigger.getJoinPublishers();
        this.evenIfDownstreamUnstable = joinTrigger.getEvenIfDownstreamUnstable();
        this.completedDownstreamProjects = new LinkedList<String>();
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
    public void downstreamFinished(AbstractBuild<?,?> upstreamBuild, AbstractBuild<?,?> finishedBuild, TaskListener listener) {
        String finishedBuildProjectName = finishedBuild.getProject().getName();
        if(pendingDownstreamProjects.remove(finishedBuildProjectName)) {
            this.overallResult = this.overallResult.combine(finishedBuild.getResult());
            completedDownstreamProjects.add(finishedBuildProjectName);
            checkPendingDownstream(upstreamBuild, listener);
        } else {
            listener.getLogger().println("[Join] Pending does not contain " + finishedBuildProjectName);
        }
    }

    public void checkPendingDownstream(AbstractBuild<?,?> owner, TaskListener listener) {
        if(pendingDownstreamProjects.isEmpty()) {
            listener.getLogger().println("All downstream projects complete!");
            Result threshold = this.evenIfDownstreamUnstable ? Result.UNSTABLE : Result.SUCCESS;
            if(this.overallResult.isWorseThan(threshold)) {
                listener.getLogger().println("Minimum result threshold not met for join project");
            } else {
                for(Publisher pub : this.joinPublishers) {
                    try {
                        pub.perform(owner, null, (BuildListener)listener);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                List<AbstractProject> projects = 
                    Items.fromNameList(joinProjects, AbstractProject.class);
                for(AbstractProject project : projects) {
                    listener.getLogger().println("Scheduling join project: " + project.getName());
                    project.scheduleBuild(new JoinCause(owner));
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
}
