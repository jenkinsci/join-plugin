package join;

import hudson.model.AbstractProject;
import hudson.model.DependencyGraph;
import hudson.model.Result;

/**
* @author wolfs
*/
class JoinTriggerDependency extends JoinDependency<DependencyGraph.Dependency> {
    boolean evenIfDownstreamUnstable;
    JoinTriggerDependency(AbstractProject<?, ?> upstream, AbstractProject<?, ?> downstream, AbstractProject<?, ?> splitProject, boolean evenIfDownstreamUnstable) {
        super(upstream, downstream, splitProject);
        this.evenIfDownstreamUnstable = evenIfDownstreamUnstable;
        splitDependency = new DependencyGraph.Dependency(splitProject, downstream);
    }

    @Override
    protected boolean conditionIsMet(Result overallResult) {
        Result threshold = this.evenIfDownstreamUnstable ? Result.UNSTABLE : Result.SUCCESS;
        return overallResult.isBetterOrEqualTo(threshold);
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
        final JoinTriggerDependency other = (JoinTriggerDependency) obj;
        if (this.evenIfDownstreamUnstable != other.evenIfDownstreamUnstable) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 71 * hash + super.hashCode();
        hash = 71 * hash + (this.evenIfDownstreamUnstable ? 1 : 0);
        return hash;
    }
}
