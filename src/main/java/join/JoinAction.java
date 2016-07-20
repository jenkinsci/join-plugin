package join;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Cause.UpstreamCause;
import hudson.model.Descriptor;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Publisher;
import hudson.util.DescribableList;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.HashMap;

public class JoinAction implements Action {
    private List<String> pendingDownstreamProjects;
    private HashMap<String, List<Result>> completedDownstreamProjects;
    private List<String> consideredBuilds;
    private transient String joinProjects;
    private DescribableList<Publisher, Descriptor<Publisher>> joinPublishers;
    private Result resultThreshold;
    private Result overallResult;

    public JoinAction(JoinTrigger joinTrigger, List<AbstractProject<?,?>> downstream) {
        this.pendingDownstreamProjects = new LinkedList<String>();
        this.completedDownstreamProjects = new HashMap<String,List<Result>>();
        for(AbstractProject<?,?> project : downstream) {
            if(!project.isDisabled()) {
                this.pendingDownstreamProjects.add(project.getFullName());
                this.completedDownstreamProjects.put(project.getFullName(),new LinkedList<Result>());
            }
        }
        this.joinProjects = joinTrigger.getJoinProjectsValue();
        this.joinPublishers = joinTrigger.getJoinPublishers();
        this.resultThreshold = joinTrigger.getResultThreshold();
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
            String finishedBuildProjectName = finishedBuild.getProject().getFullName();
            if (!this.completedDownstreamProjects.get(finishedBuildProjectName).contains(finishedBuild.getResult())) { 
                List<Result> withNewResult = this.completedDownstreamProjects.get(finishedBuildProjectName);
                withNewResult.add(finishedBuild.getResult());
                this.completedDownstreamProjects.put(finishedBuildProjectName, withNewResult);
                pendingDownstreamProjects.remove(finishedBuildProjectName);
                checkPendingDownstream(upstreamBuild, listener);
            } else {
                listener.getLogger().println("[Join] Job List does not contain " + finishedBuildProjectName);
            }
            try {
                upstreamBuild.save();
            } catch (java.io.IOException e) {
                listener.getLogger().printf("Unable to save upstream build.");
            }
        }
        return pendingDownstreamProjects.isEmpty();
    }

    public Result getOverallResult() {
        return overallResult;
    }

    public synchronized void checkPendingDownstream(AbstractBuild<?,?> owner, TaskListener listener) {
        if(pendingDownstreamProjects.isEmpty()) {
            Result finalResult = this.overallResult;
            for(String buildName : this.completedDownstreamProjects.keySet()) {
                List<Result> resultList = this.completedDownstreamProjects.get(buildName);
                if (!resultList.contains(Result.SUCCESS)) {
                    pendingDownstreamProjects.add(buildName);
                    Result buildResult = resultList.get(0);
                    for ( Result result : resultList ) {
                        if (result.isBetterOrEqualTo(buildResult)) {
                            buildResult = result;
                        }
                    }
                    finalResult = finalResult.combine(buildResult);
                }
            }
            listener.getLogger().println("All downstream projects complete!");
            if(finalResult.isWorseThan(this.resultThreshold)) {
                listener.getLogger().println("Minimum result threshold not met for join project");
            } else {
                final Launcher launcher = null;

                for(Publisher pub : this.joinPublishers) {
                    try {
                        pub.perform(owner, launcher, (BuildListener)listener);
                    } catch (InterruptedException e) {
                        listener.getLogger().print(e.toString());
                    } catch (IOException e) {
                        listener.getLogger().print(e.toString());
                    }
                }
            }
        } else {
            listener.getLogger().println("Project " + owner.getProject().getName() + " still waiting for " + pendingDownstreamProjects.toString() + " builds to complete");
        }
    }

    public class JoinCause extends UpstreamCause {

        public JoinCause(Run<?, ?> arg0) {
            super(arg0);
        }

    }
}
