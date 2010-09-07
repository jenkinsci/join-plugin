package join;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import java.io.IOException;

@Extension
public class ResultSetter extends Notifier {
    private final Result result;

    public ResultSetter() {
        this.result = Result.FAILURE;
    }

    public ResultSetter(Result result) {
        this.result = result;
    }

    public static ResultSetter FAILURE() {
        return new ResultSetter(Result.FAILURE);
    }

    public static ResultSetter UNSTABLE() {
        return new ResultSetter(Result.UNSTABLE);
    }

    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        @Extension
        public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "FailPublisher";
        }
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        build.setResult(result);
        return true;
    }
}
