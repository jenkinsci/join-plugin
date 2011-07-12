package join;

import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.plugins.parameterizedtrigger.BuildTriggerConfig;
import hudson.plugins.parameterizedtrigger.ParameterizedDependency;
import hudson.plugins.parameterizedtrigger.ResultCondition;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
* @author wolfs
*/
class ParameterizedJoinDependency extends JoinDependency<ParameterizedDependency> {
    private static final Logger LOGGER = Logger.getLogger(ParameterizedJoinDependency.class.getName());
    private BuildTriggerConfig config;

    ParameterizedJoinDependency(AbstractProject<?, ?> upstream, AbstractProject<?, ?> downstream, AbstractProject<?, ?> splitProject, BuildTriggerConfig config) {
        super(upstream, downstream,splitProject);
        splitDependency = new ParameterizedDependency(splitProject, downstream, config);
        this.config = config;
    }

    @Override
    protected boolean conditionIsMet(Result overallResult) {
        // This is bad but sadly the method is package-private and not public!
        try {
            ResultCondition condition = config.getCondition();
            Method isMetMethod = condition.getClass().getDeclaredMethod("isMet", Result.class);
            isMetMethod.setAccessible(true);
            return (Boolean)isMetMethod.invoke(condition, overallResult);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
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
        final ParameterizedJoinDependency other = (ParameterizedJoinDependency) obj;
        if (this.config != other.config && (this.config == null || !this.config.equals(other.config))) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 61 * hash + super.hashCode();
        hash = 61 * hash + (this.config != null ? this.config.hashCode() : 0);
        return hash;
    }


}
