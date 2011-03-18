/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Brian Westrich, Martin Eigenbrodt
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package join;

import hudson.Extension;
import hudson.Launcher;
import hudson.Plugin;
import hudson.Util;
import hudson.matrix.MatrixAggregatable;
import hudson.matrix.MatrixAggregator;
import hudson.matrix.MatrixBuild;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.AutoCompletionCandidates;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.Cause.UpstreamCause;
import hudson.model.CauseAction;
import hudson.model.DependecyDeclarer;
import hudson.model.DependencyGraph;
import hudson.model.DependencyGraph.Dependency;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.Items;
import hudson.model.Project;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.Saveable;
import hudson.model.TaskListener;
import hudson.model.listeners.ItemListener;
import hudson.model.listeners.RunListener;
import hudson.plugins.parameterizedtrigger.BuildTriggerConfig;
import hudson.plugins.parameterizedtrigger.ParameterizedDependency;
import hudson.plugins.parameterizedtrigger.ResultCondition;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.BuildTrigger;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import join.JoinAction.JoinCause;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class JoinTrigger extends Recorder implements DependecyDeclarer, MatrixAggregatable {
    private static final Logger LOGGER = Logger.getLogger(JoinTrigger.class.getName());


    private String joinProjects;

    private DescribableList<Publisher,Descriptor<Publisher>> joinPublishers =
        new DescribableList<Publisher,Descriptor<Publisher>>(Saveable.NOOP);

    private boolean evenIfDownstreamUnstable;

    public JoinTrigger() {
        this(new DescribableList<Publisher, Descriptor<Publisher>>(Saveable.NOOP), "", false);
    }

    public JoinTrigger(DescribableList<Publisher,Descriptor<Publisher>> publishers,String string, boolean b) {
        this.joinProjects = string;
        this.evenIfDownstreamUnstable = b;
        this.joinPublishers = publishers;
    }

    @Override
    public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
        for( BuildStep bs : joinPublishers )
            if(!bs.prebuild(build,listener))
                return false;
        return true;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
            BuildListener listener) throws InterruptedException, IOException {
        BuildTrigger buildTrigger = build.getProject().getPublishersList().get(BuildTrigger.class);
        JoinAction joinAction = new JoinAction(this, buildTrigger, tryGetParameterizedDownstreamNames(build));
        build.addAction(joinAction);
        joinAction.checkPendingDownstream(build, listener);
        return true;
    }

    private ArrayList<String> tryGetParameterizedDownstreamNames(AbstractBuild<?, ?> build) {
        ArrayList<String> ret = new ArrayList<String>();
        Plugin parameterizedTrigger = Hudson.getInstance().getPlugin("parameterized-trigger");
        if (parameterizedTrigger != null) {
            hudson.plugins.parameterizedtrigger.BuildTrigger buildTrigger =
                build.getProject().getPublishersList().get(hudson.plugins.parameterizedtrigger.BuildTrigger.class);
            if (buildTrigger != null) {
                for(hudson.plugins.parameterizedtrigger.BuildTriggerConfig config : buildTrigger.getConfigs()) {
                    for(AbstractProject project : config.getProjectList()) {
                        if (!project.isDisabled()) {
                        ret.add(project.getName());
                    }
                }
            }
        }
        }
        return ret;
    }

    @Override
    public void buildDependencyGraph(AbstractProject owner, DependencyGraph graph) {
        if (!canDeclare(owner)) {
            return;
        }

        final List<AbstractProject<?,?>> downstreamProjects = getAllDownstream(owner);
        // If there is no intermediate project add the split project and use it as
        // the one triggering the downstream build
        if (downstreamProjects.isEmpty()) {
            downstreamProjects.add(owner);
        }
        Plugin parameterizedTrigger = Hudson.getInstance().getPlugin("parameterized-trigger");
        for (AbstractProject<?,?> downstreamProject: downstreamProjects) {
            if (parameterizedTrigger != null) {
                hudson.plugins.parameterizedtrigger.BuildTrigger paramBt =
                        joinPublishers.get(hudson.plugins.parameterizedtrigger.BuildTrigger.class);
                if (paramBt != null) {
                    for (BuildTriggerConfig config : paramBt.getConfigs()) {
                        for (AbstractProject<?,?> joinProject : config.getProjectList()) {
                            ParameterizedJoinDependency dependency =
                                new ParameterizedJoinDependency(downstreamProject, joinProject, owner, config);
                            graph.addDependency(dependency);
                        }
                    }
                }
            }

            for (AbstractProject<?,?> joinProject : getJoinProjects()) {
                JoinTriggerDependency dependency =
                        new JoinTriggerDependency(downstreamProject, joinProject, owner, evenIfDownstreamUnstable);
                graph.addDependency(dependency);
            }
        }
    }

    static boolean canDeclare(AbstractProject<?,?> owner) {
            // Inner class added in Hudson 1.341
            return DependencyGraph.class.getClasses().length > 0;
    }


    public List<AbstractProject<?,?>> getAllDownstream(AbstractProject<?,?> project) {
        List<AbstractProject<?,?>> downstream = getBuildTriggerDownstream(project);
        downstream.addAll(getParameterizedDownstream(project));
        return downstream;
    }

    public List<AbstractProject<?,?>> getParameterizedDownstream(AbstractProject<?,?> project) {
        ArrayList<AbstractProject<?,?>> ret = new ArrayList<AbstractProject<?,?>>();
        Plugin parameterizedTrigger = Hudson.getInstance().getPlugin("parameterized-trigger");
        if (parameterizedTrigger != null) {
            hudson.plugins.parameterizedtrigger.BuildTrigger buildTrigger =
                project.getPublishersList().get(hudson.plugins.parameterizedtrigger.BuildTrigger.class);
            if (buildTrigger != null) {
                for(hudson.plugins.parameterizedtrigger.BuildTriggerConfig config : buildTrigger.getConfigs()) {
                    for(AbstractProject<?,?> downStreamProject : config.getProjectList()) {
                        ret.add(downStreamProject);
                    }
                }
            }
        }
        return ret;
    }

    public List<AbstractProject<?,?>> getBuildTriggerDownstream(AbstractProject<?,?> project) {
        ArrayList<AbstractProject<?,?>> ret = new ArrayList<AbstractProject<?,?>>();
        BuildTrigger buildTrigger = project.getPublishersList().get(BuildTrigger.class);
        if (buildTrigger != null) {
            for (AbstractProject<?,?> childProject : buildTrigger.getChildProjects()) {
                ret.add(childProject);
            }
        }
        return ret;
    }

    @Override
    public MatrixAggregator createAggregator(MatrixBuild build, Launcher launcher, BuildListener listener) {
        return new MatrixAggregator(build, launcher, listener) {
            @Override
            public boolean endBuild() throws InterruptedException, IOException {
                return perform(build,launcher,listener);
            }
        };
    }

    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        @Extension
        public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

        public String getDisplayName() {
            return "Join Trigger";
        }

        @Override
        public String getHelpFile() {
            return "/plugin/join/help/joinTrigger.html";
        }

        @Override
        public JoinTrigger newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            LOGGER.finer(formData.toString());

            // Rebuild triggers save (even though java doc says otherwise)
            // => first build list, then call constructor
            List<Publisher> newList = new ArrayList<Publisher>();

            JSONObject postbuild = formData.optJSONObject("postbuildactions");
            if(postbuild != null) {
                JSONObject joinPublishers = postbuild.optJSONObject("joinPublishers");
                if(joinPublishers != null) {
                    extractAndAddPublisher(joinPublishers, getApplicableDescriptors(), newList, req);
                }
            }

            DescribableList<Publisher,Descriptor<Publisher>> publishers =
                new DescribableList<Publisher,Descriptor<Publisher>>(Saveable.NOOP, newList);

            LOGGER.finer("Parsed " + publishers.size() + " publishers");

            // Remove trailing "," inserted by YUI autocompletion
            String joinProjectsValue = Util.fixNull(formData.getString("joinProjectsValue")).trim();
            if (joinProjectsValue.endsWith(",")) {
                joinProjectsValue = joinProjectsValue.substring(0, joinProjectsValue.length()-1).trim();
            }
            return new JoinTrigger(publishers,
                    joinProjectsValue,
                formData.has("evenIfDownstreamUnstable") && formData.getBoolean("evenIfDownstreamUnstable"));
        }

        private void extractAndAddPublisher(JSONObject json, List<Descriptor<Publisher>> applicableDescriptors, List<Publisher> newList, StaplerRequest req) throws FormException {
            for (Descriptor<Publisher> d : applicableDescriptors) {
                String name = d.getJsonSafeClassName();
                if (json.has(name)) {
                    Publisher instance = d.newInstance(req, json.getJSONObject(name));
                    newList.add(instance);
                }
            }
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            // We can add this to any project!
            return true;
        }

        public boolean showEvenIfUnstableOption(Class<? extends AbstractProject> jobType) {
            // UGLY: for promotion process, this option doesn't make sense.
            return !jobType.getName().contains("PromotionProcess");
        }

        public List<Descriptor<Publisher>> getApplicableDescriptors() {
            ArrayList<Descriptor<Publisher>> list = new ArrayList<Descriptor<Publisher>>();
            Plugin parameterizedTrigger = Hudson.getInstance().getPlugin("parameterized-trigger");
            if (parameterizedTrigger != null) {
                list.add(Hudson.getInstance().getDescriptorByType(hudson.plugins.parameterizedtrigger.BuildTrigger.DescriptorImpl.class));
            }
            Plugin copyArchiver = Hudson.getInstance().getPlugin("copyarchiver");
            if (copyArchiver != null) {
                list.add(Hudson.getInstance().getDescriptorByType(com.thalesgroup.hudson.plugins.copyarchiver.CopyArchiverPublisher.CopyArchiverDescriptor.class));
            }
            // See issue 4384.  Supporting the mailer here breaks its use as the regular post-build action.
            //list.add(Hudson.getInstance().getDescriptorByType(hudson.tasks.Mailer.DescriptorImpl.class));
            return list;
        }

        /**
         * Form validation method.
         */
        public FormValidation doCheckJoinProjectsValue(@QueryParameter String value ) {
            StringTokenizer tokens = new StringTokenizer(Util.fixNull(value),",");
            while(tokens.hasMoreTokens()) {
                String projectName = tokens.nextToken().trim();
                if (StringUtils.isNotEmpty(projectName)) {
                    Item item = Hudson.getInstance().getItemByFullName(projectName,Item.class);
                    if(item==null)
                        return FormValidation.error("No such project");
                    if(!(item instanceof AbstractProject))
                        return FormValidation.error("Not buildable");
                }
            }

            return FormValidation.ok();
        }

        public AutoCompletionCandidates doAutoCompleteJoinProjectsValue(@QueryParameter String value) {
            AutoCompletionCandidates candidates = new AutoCompletionCandidates();
            List<AbstractProject> projects = Hudson.getInstance().getItems(AbstractProject.class);
            for (AbstractProject project : projects) {
                if (project.getFullName().startsWith(value)) {
                    candidates.add(project.getFullName());
                }
            }
            return candidates;
        }

        @Extension
        public static class RunListenerImpl extends RunListener<Run> {
            public RunListenerImpl() {
                super(Run.class);
            }

            @Override
            public void onCompleted(Run run, TaskListener listener) {
                if(!(run instanceof AbstractBuild)) {
                    return;
                }
                AbstractBuild<?,?> abstractBuild = (AbstractBuild<?,?>)run;

                listener.getLogger().println("Notifying upstream projects of job completion");
                String upstreamProject = null;
                int upstreamJobNumber = 0;
                CauseAction ca = run.getAction(CauseAction.class);
                if(ca == null) {
                    listener.getLogger().println("Join notifier requires a CauseAction");
                    return;
                }
                for(Cause c : ca.getCauses()) {
                    if(!(c instanceof UpstreamCause)) continue;
                    if(c instanceof JoinCause) continue;
                    UpstreamCause uc = (UpstreamCause)c;
                    notifyJob(abstractBuild, listener, uc.getUpstreamProject(), uc.getUpstreamBuild());
                }
                return;
            }

            private void notifyJob(AbstractBuild<?,?> abstractBuild, TaskListener listener, String upstreamProjectName,
                    int upstreamJobNumber) {
                List<AbstractProject> upstreamList = Items.fromNameList(upstreamProjectName,AbstractProject.class);
                if(upstreamList.size() != 1) {
                    listener.getLogger().println("Join notifier cannot find upstream project: " + upstreamProjectName);
                    return;
                }
                AbstractProject<?,?> upstreamProject = upstreamList.get(0);
                Run upstreamRun = upstreamProject.getBuildByNumber(upstreamJobNumber);

                if(upstreamRun == null) {
                    listener.getLogger().println("Join notifier cannot find upstream run: " + upstreamProjectName + " number " + upstreamJobNumber);
                    return;
                }
                if(!(upstreamRun instanceof AbstractBuild)) {
                    LOGGER.fine("Upstream run is not an AbstractBuild: " + upstreamProjectName + " number " + upstreamJobNumber);
                    return;
                }

                AbstractBuild<?,?> upstreamBuild = (AbstractBuild<?,?>)upstreamRun;
                JoinAction ja = upstreamRun.getAction(JoinAction.class);
                if(ja == null) {
                    // does not go in the build log, since this is normal for any downstream project that
                    // runs without the join plugin enabled
                    LOGGER.finer("Join notifier cannot find upstream JoinAction: " + upstreamProjectName + " number " + upstreamJobNumber);
                    return;
                }
                listener.getLogger().println("Notifying upstream of completion: " + upstreamProjectName + " #" + upstreamJobNumber);
                ja.downstreamFinished(upstreamBuild, abstractBuild, listener);
            }

        }

        @Extension
        public static class ItemListenerImpl extends ItemListener {
            @Override
            public void onRenamed(Item item, String oldName, String newName) {
                // update BuildTrigger of other projects that point to this object.
                // can't we generalize this?
                for( Project<?,?> p : Hudson.getInstance().getProjects() ) {
                    BuildTrigger t = p.getPublishersList().get(BuildTrigger.class);
                    if(t!=null) {
                        if(t.onJobRenamed(oldName,newName)) {
                            try {
                                p.save();
                            } catch (IOException e) {
                                LOGGER.log(Level.WARNING, "Failed to persist project setting during rename from "+oldName+" to "+newName,e);
                            }
                        }
                    }
                }
            }
        }
    }

    public boolean usePostBuildActions() {
        return containsAnyDescriptor(DescriptorImpl.DESCRIPTOR.getApplicableDescriptors());
    }

    private boolean containsAnyDescriptor(
            List<Descriptor<Publisher>> applicableDescriptors) {
        for(Descriptor<Publisher> descriptor : applicableDescriptors) {
           if(joinPublishers.contains(descriptor)) {
               return true;
           }
        }
        return false;
    }

    public String getJoinProjectsValue() {
        return joinProjects;
    }

    public List<AbstractProject> getJoinProjects() {
        List<AbstractProject> list;
        if (joinProjects == null) {
            list = new ArrayList<AbstractProject>();
        } else {
            list = Items.fromNameList(joinProjects, AbstractProject.class);
        }
        return list;
    }

    public DescribableList<Publisher, Descriptor<Publisher>> getJoinPublishers() {
        return joinPublishers;
    }

    public boolean getEvenIfDownstreamUnstable() {
        return this.evenIfDownstreamUnstable;
    }

    private Object readResolve() {
        if(this.joinPublishers == null) {
            this.joinPublishers = new DescribableList<Publisher,Descriptor<Publisher>>(Saveable.NOOP);
        }
        return this;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    private static class JoinDependency<DEP extends Dependency> extends Dependency {
                private AbstractProject<?,?> splitProject;
        protected DEP splitDependency;

        protected JoinDependency(AbstractProject<?,?> upstream, AbstractProject<?,?> downstream, AbstractProject<?,?> splitProject) {
            super(upstream, downstream);
            this.splitProject = splitProject;
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
                if (cause instanceof UpstreamCause) {
                    UpstreamCause uc = (UpstreamCause) cause;
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

    private static class ParameterizedJoinDependency extends JoinDependency<ParameterizedDependency> {
        private BuildTriggerConfig config;

        private ParameterizedJoinDependency(AbstractProject<?,?> upstream, AbstractProject<?,?> downstream, AbstractProject<?,?> splitProject,BuildTriggerConfig config) {
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
                Logger.getLogger(ParameterizedJoinDependency.class.getName()).log(Level.SEVERE, null, ex);
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

    private static class JoinTriggerDependency extends JoinDependency<Dependency> {
        boolean evenIfDownstreamUnstable;
        JoinTriggerDependency(AbstractProject<?,?> upstream, AbstractProject<?,?> downstream, AbstractProject<?,?> splitProject,boolean evenIfDownstreamUnstable) {
            super(upstream, downstream, splitProject);
            this.evenIfDownstreamUnstable = evenIfDownstreamUnstable;
            splitDependency = new Dependency(splitProject, downstream);
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
}
