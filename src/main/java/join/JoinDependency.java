package join;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.DependencyGraph;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
* @author wolfs
*/
public class JoinDependency<DEP extends DependencyGraph.Dependency> extends DependencyGraph.Dependency {
    private static final Logger LOGGER = Logger.getLogger(JoinDependency.class.getName());

    private AbstractProject<?,?> splitProject;
    protected DEP splitDependency;

    JoinDependency(AbstractProject<?, ?> upstream, AbstractProject<?, ?> downstream, AbstractProject<?, ?> splitProject) {
        super(upstream, downstream);
        this.splitProject = splitProject;
    }

    /**
     * @return true if this JoinDependency and the one passed as argument share a common ancestor split project, i.e.
     * both dependencies where introduces in DependencyGraph from the same job
     */
    public boolean fromSameSplitProject(JoinDependency<?> other) {
        return this.splitProject.equals(other.splitProject);
    }

    @Override
    public boolean shouldTriggerBuild(AbstractBuild build, TaskListener listener, List<Action> actions) {
        AbstractBuild<?,?> splitBuild = getSplitBuild(build);
        if (splitBuild != null) {
            final JoinAction joinAction = splitBuild.getAction(JoinAction.class);
            if(joinAction != null) {
                listener.getLogger().println("Notifying upstream build " + splitBuild + " of job completion");
                boolean joinDownstreamFinished = joinAction.downstreamFinished(splitBuild, build, listener);
                joinDownstreamFinished = joinDownstreamFinished &&
                        conditionIsMet(joinAction.getOverallResult()) &&
                            splitDependency.shouldTriggerBuild(splitBuild, listener, actions);
                return joinDownstreamFinished;
            }
        }
        // does not go in the build log, since this is normal for any downstream project that
        // runs without the join plugin enabled
        LOGGER.log(Level.FINER, "Join notifier cannot find upstream JoinAction: {0}", splitBuild);
        return false;
    }

    private AbstractBuild<?,?> getSplitBuild(AbstractBuild<?,?> build) {
        final List<Cause> causes = build.getCauses();
        AbstractBuild<?,?> splitBuild = null;
        // If there is no intermediate project this will happen
        if (splitProject.getName().equals(build.getProject().getName())) {
            splitBuild = build;
        }
        for (Cause cause : causes) {
            if (cause instanceof JoinAction.JoinCause) {
                continue;
            }
            if (cause instanceof Cause.UpstreamCause) {
                Cause.UpstreamCause uc = (Cause.UpstreamCause) cause;
                final int upstreamBuildNum = uc.getUpstreamBuild();
                final String upstreamProject = uc.getUpstreamProject();
                if (splitProject.getName().equals(upstreamProject)) {
                    final Run<?,?> upstreamRun = splitProject.getBuildByNumber(upstreamBuildNum);
                    if (upstreamRun instanceof AbstractBuild<?,?>) {
                        splitBuild = (AbstractBuild<?,?>) upstreamRun;
                        break;
                    }
                }
            }
        }
        return splitBuild;
    }

    protected boolean conditionIsMet(Result overallResult) {
        return true;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        if (!super.equals(obj)) {
            return false;
        }
        final JoinDependency<DEP> other = (JoinDependency<DEP>) obj;
        if (this.splitProject != other.splitProject && (this.splitProject == null || !this.splitProject.equals(other.splitProject))) {
            return false;
        }
        if (this.splitDependency != other.splitDependency && (this.splitDependency == null || !this.splitDependency.equals(other.splitDependency))) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 79 * hash + super.hashCode();
        hash = 79 * hash + (this.splitProject != null ? this.splitProject.hashCode() : 0);
        hash = 79 * hash + (this.splitDependency != null ? this.splitDependency.hashCode() : 0);
        return hash;
    }



}
