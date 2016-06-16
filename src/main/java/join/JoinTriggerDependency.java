package join;

import hudson.model.AbstractProject;
import hudson.model.DependencyGraph;
import hudson.model.Result;

/**
* @author wolfs
*/
class JoinTriggerDependency extends JoinDependency<DependencyGraph.Dependency> {
    Result resultThreshold;
    JoinTriggerDependency(AbstractProject<?, ?> upstream, AbstractProject<?, ?> downstream, AbstractProject<?, ?> splitProject, Result resultThreshold) {
        super(upstream, downstream, splitProject);
        this.resultThreshold = resultThreshold;
        splitDependency = new DependencyGraph.Dependency(splitProject, downstream);
    }

    @Override
    protected boolean conditionIsMet(Result overallResult) {
        if(overallResult == null || this.resultThreshold == null) {
            return false;
        }
        return overallResult.isBetterOrEqualTo(this.resultThreshold);
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
        if (this.resultThreshold != other.resultThreshold) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 71 * hash + super.hashCode();
        if(this.resultThreshold != null) {
            hash = 71 * hash + this.resultThreshold.ordinal + 1;
        }
        return hash;
    }
}
