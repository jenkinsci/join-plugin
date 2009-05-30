package join;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Hudson;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildTrigger;
import hudson.model.Items;
import hudson.model.Cause.UpstreamCause;

public class JoinAction implements Action {
    private List<String> pendingDownstreamProjects;
    private List<String> completedDownstreamProjects;
    private String joinProjects;
    private boolean evenIfDownstreamUnstable;
    private Result overallResult;
    
    public JoinAction(JoinTrigger joinTrigger, BuildTrigger buildTrigger) {
        String[] split = buildTrigger==null ? 
                new String[0] : buildTrigger.getChildProjectsValue().split(",");
        this.pendingDownstreamProjects = new LinkedList<String>();
        for(String proj : split) {
            this.pendingDownstreamProjects.add(proj.trim());
        }
        this.joinProjects = joinTrigger.getJoinProjectsValue();
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

    public void downstreamFinished(String name, Run r, TaskListener listener) {
        if(pendingDownstreamProjects.remove(name)) {
            this.overallResult = this.overallResult.combine(r.getResult());
            completedDownstreamProjects.add(name);
            checkPendingDownstream(r, listener);
        }
    }

    public void checkPendingDownstream(Run r, TaskListener listener) {
        if(pendingDownstreamProjects.isEmpty()) {
            listener.getLogger().println("All downstream projects complete!");
            Result threshold = this.evenIfDownstreamUnstable ? Result.UNSTABLE : Result.SUCCESS;
            if(this.overallResult.isWorseThan(threshold)) {
                listener.getLogger().println("Minimum result threshold not met for join project");
            } else {
                List<AbstractProject> projects = 
                    Items.fromNameList(joinProjects, AbstractProject.class);
                for(AbstractProject project : projects) {
                    listener.getLogger().println("Scheduling join project: " + project.getName());
                    project.scheduleBuild(new JoinCause(r));
                }
            }
        }
    }

    public class JoinCause extends UpstreamCause {

        public JoinCause(Run<?, ?> arg0) {
            super(arg0);
        }
        
    }
}
