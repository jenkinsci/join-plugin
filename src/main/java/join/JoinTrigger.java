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

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.Plugin;
import hudson.Util;
import hudson.matrix.MatrixAggregatable;
import hudson.matrix.MatrixAggregator;
import hudson.matrix.MatrixBuild;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.AutoCompletionCandidates;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.Cause;
import hudson.model.Cause.UpstreamCause;
import hudson.model.CauseAction;
import hudson.model.DependecyDeclarer;
import hudson.model.DependencyGraph;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Items;
import hudson.model.Project;
import hudson.model.Run;
import hudson.model.Saveable;
import hudson.model.TaskListener;
import hudson.model.listeners.ItemListener;
import hudson.model.listeners.RunListener;
import hudson.plugins.downstream_ext.DownstreamTrigger;
import hudson.plugins.parameterizedtrigger.BuildTriggerConfig;
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
import org.jenkins_ci.plugins.flexible_publish.ConditionalPublisher;
import org.jenkins_ci.plugins.flexible_publish.FlexiblePublisher;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.model.Jenkins;

@Extension
public class JoinTrigger extends Recorder implements DependecyDeclarer, MatrixAggregatable {
    private static final Logger LOGGER = Logger.getLogger(JoinTrigger.class.getName());


    private String joinProjects;

    // https://www.jenkins.io/blog/2018/01/13/jep-200/ Refusing to marshal join.JoinTrigger for security reasons; see https://jenkins.io/redirect/class-filter/
    // resources/META-INF/hudson.remoting.ClassFilter with Entry join.JoinTrigger
    private DescribableList<Publisher,Descriptor<Publisher>> joinPublishers =
        new DescribableList<Publisher,Descriptor<Publisher>>(Saveable.NOOP);

    private transient boolean evenIfDownstreamUnstable;
    private Result resultThreshold;

    public JoinTrigger() {
        this(new DescribableList<Publisher, Descriptor<Publisher>>(Saveable.NOOP), "", "SUCCESS");
    }

    public JoinTrigger(DescribableList<Publisher,Descriptor<Publisher>> publishers,String string, String result) {
        this.joinProjects = string;
        this.resultThreshold = Result.fromString(result);
        this.joinPublishers = publishers;
    }

    @Override
    public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
        for( BuildStep bs : joinPublishers ) {
            if(!bs.prebuild(build,listener)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
            BuildListener listener) throws InterruptedException, IOException {
        BuildTrigger buildTrigger = build.getProject().getPublishersList().get(BuildTrigger.class);
        JoinAction joinAction = new JoinAction(this, getAllDownstream(build.getProject(), build.getEnvironment(listener)));
        build.addAction(joinAction);
        joinAction.checkPendingDownstream(build, listener);
        return true;
    }

    @Override
    public void buildDependencyGraph(AbstractProject owner, DependencyGraph graph) {

        final List<AbstractProject<?,?>> downstreamProjects = getAllDownstream(owner, null);
        // If there is no intermediate project add the split project and use it as
        // the one triggering the downstream build
        if (downstreamProjects.isEmpty()) {
            downstreamProjects.add(owner);
        }

        for (AbstractProject<?,?> downstreamProject: downstreamProjects) {
            for (BuildTriggerConfig config : getBuildTriggerConfigs(joinPublishers)) {
                for (AbstractProject<?,?> joinProject : config.getProjectList(owner.getParent(), null)) {
                    ParameterizedJoinDependency dependency =
                            new ParameterizedJoinDependency(downstreamProject, joinProject, owner, config);
                    graph.addDependency(dependency);
                }
            }

            for (AbstractProject<?,?> joinProject : getJoinProjects(owner.getParent())) {
                JoinTriggerDependency dependency =
                        new JoinTriggerDependency(downstreamProject, joinProject, owner, resultThreshold);
                graph.addDependency(dependency);
            }
        }
    }

    private List<String> tryGetParameterizedDownstreamNames(AbstractBuild<?, ?> build, BuildListener listener) {
        ArrayList<String> ret = new ArrayList<String>();
        EnvVars env = null;
        try {
            env = build.getEnvironment(listener);
        } catch (Exception e) {
            listener.getLogger().print(e);
        }

        for (AbstractProject<?,?> project :
                getParameterizedDownstreamProjects(build.getProject().getParent(), build.getProject().getPublishersList(), env)) {
            if (!project.isDisabled()) {
                ret.add(project.getName());
            }
        }
        return ret;
    }

    private List<AbstractProject<?,?>> getParameterizedDownstreamProjects(
            ItemGroup context,
            DescribableList<Publisher,Descriptor<Publisher>> publishers, EnvVars env) {
        List<AbstractProject<?,?>> ret = new ArrayList<AbstractProject<?,?>>();
        for(hudson.plugins.parameterizedtrigger.BuildTriggerConfig config :
                getBuildTriggerConfigs(publishers)) {
            for (AbstractProject<?,?> project : config.getProjectList(context, env)) {
                ret.add(project);
            }
        }
        return ret;
    }

    private List<BuildTriggerConfig> getBuildTriggerConfigs(
            DescribableList<Publisher,Descriptor<Publisher>> publishers) {
        List<BuildTriggerConfig> ret = new ArrayList<BuildTriggerConfig>();
        Plugin parameterizedTrigger = Jenkins.getInstance().getPlugin("parameterized-trigger");
        if (parameterizedTrigger != null) {
            hudson.plugins.parameterizedtrigger.BuildTrigger buildTrigger =
                publishers.get(hudson.plugins.parameterizedtrigger.BuildTrigger.class);
            if (buildTrigger != null) {
                for(hudson.plugins.parameterizedtrigger.BuildTriggerConfig config : buildTrigger.getConfigs()) {
                    ret.add(config);
                }
            }
        }
        return ret;
    }

    private List<AbstractProject<?,?>> getDownstreamExtDownstream(
            ItemGroup context,
            DescribableList<Publisher,Descriptor<Publisher>> publishers) {
        List<AbstractProject<?,?>> ret = new ArrayList<AbstractProject<?, ?>>();
        Plugin extDownstream = Jenkins.getInstance().getPlugin("downstream-ext");
        if (extDownstream != null) {
            DownstreamTrigger buildTrigger =
                publishers.get(DownstreamTrigger.class);
            if (buildTrigger != null) {
                for (AbstractProject<?,?> project : buildTrigger.getChildProjects(context)) {
                    ret.add(project);
                }
            }
        }
        return ret;
    }

    private Collection<? extends AbstractProject<?, ?>> getParameterizedDownstreamInFlexiblePublisher(
        AbstractProject<?, ?> project) {
        
        DescribableList<Publisher, Descriptor<Publisher>> publishers = project.getPublishersList();
        List<AbstractProject<?, ?>> ret = new ArrayList<AbstractProject<?, ?>>();
        Plugin flexiblePublisherPlugin = Jenkins.getInstance().getPlugin("flexible-publish");
        Plugin parameterizedTrigger = Jenkins.getInstance().getPlugin("parameterized-trigger");
        
        if (flexiblePublisherPlugin == null || parameterizedTrigger == null) {
        	return ret;
        }
        
        FlexiblePublisher flexiblePublisher = publishers.get(FlexiblePublisher.class);
        
        if (flexiblePublisher != null ) {
        
            for (ConditionalPublisher conditionalPublisher : flexiblePublisher.getPublishers()) {
                for (BuildStep buildStep : conditionalPublisher.getPublisherList()) {
                    if (buildStep instanceof hudson.plugins.parameterizedtrigger.BuildTrigger) {
                    
                        ret.addAll(GetProjectFromBuildTriggerConfigs(buildStep));
                    }
                }
            }
        }
        
        return ret;
    }

    private Collection<? extends AbstractProject<?, ?>> GetProjectFromBuildTriggerConfigs(
            Object buildTrigger) {
        
        List<AbstractProject<?, ?>> ret = new ArrayList<AbstractProject<?, ?>>();
        
        for (hudson.model.Project p : Hudson.getInstance().getProjects()) {
    
            for (BuildTriggerConfig config : ((hudson.plugins.parameterizedtrigger.BuildTrigger )buildTrigger).getConfigs()) {
                if (p.getName().equals(config.getProjects())) {
                    ret.add(p);
                }
            }
        }
        
        return ret;
    }

    static boolean canDeclare(AbstractProject<?,?> owner) {
            // Inner class added in Hudson 1.341
            return true;
    }


    public List<AbstractProject<?,?>> getAllDownstream(AbstractProject<?,?> project, EnvVars env) {
        List<AbstractProject<?,?>> downstream = getBuildTriggerDownstream(project);
        downstream.addAll(getParameterizedDownstreamInFlexiblePublisher(project));
        downstream.addAll(getParameterizedDownstreamProjects(project.getParent(), project.getPublishersList(), env));
        downstream.addAll(getDownstreamExtDownstream(project.getParent(), project.getPublishersList()));
        return downstream;
    }
    

    public List<AbstractProject<?,?>> getBuildTriggerDownstream(AbstractProject<?,?> project) {
        ArrayList<AbstractProject<?,?>> ret = new ArrayList<AbstractProject<?,?>>();
        BuildTrigger buildTrigger = project.getPublishersList().get(BuildTrigger.class);
        if (buildTrigger != null) {
            for (AbstractProject<?,?> childProject : buildTrigger.getChildProjects(project.getParent())) {
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

    public boolean onJobRenamed(ItemGroup parent, String oldName, String newName) {
        String newJoin = join.Items.rename(oldName, newName, joinProjects, parent);
        boolean updated = !joinProjects.equals(newJoin);
        joinProjects = newJoin;
        return updated;
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
            String joinProjectsValue = reformatJoinProjectsValue(formData.getString("joinProjectsValue"));
            return new JoinTrigger(publishers,
                    joinProjectsValue,
                    formData.getString("resultThreshold"));
        }

        public String reformatJoinProjectsValue(String joinProjectsValue) {
            String[] tokens = Util.fixNull(joinProjectsValue).split(",");
            List<String> verified = new ArrayList<String>();
            for (String token : tokens) {
                String projectName = token.trim();
                if (StringUtils.isNotEmpty(projectName)) {
                    // can't really test the validity of the value since we don't know the current context ItemParent
                    // Item item = Hudson.getInstance().getItemByFullName(projectName,Item.class);
                    verified.add(projectName);
                }

            }
            return Util.join(verified,", ");
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

        public boolean showResultThresholdOption(Class<? extends AbstractProject> jobType) {
            if(jobType == null) {
                return true;
            }
            // UGLY: for promotion process, this option doesn't make sense.
            return !jobType.getName().contains("PromotionProcess");
        }

        public List<Descriptor<Publisher>> getApplicableDescriptors() {
            ArrayList<Descriptor<Publisher>> list = new ArrayList<Descriptor<Publisher>>();
            Plugin parameterizedTrigger = Jenkins.getInstance().getPlugin("parameterized-trigger");
            if (parameterizedTrigger != null) {
                list.add(Jenkins.getInstance().getDescriptorByType(hudson.plugins.parameterizedtrigger.BuildTrigger.DescriptorImpl.class));
            }
            list.add(Jenkins.getInstance().getDescriptorByType(hudson.tasks.Mailer.DescriptorImpl.class));
            return list;
        }

        /**
         * Form validation method.
         */
        public FormValidation doCheckJoinProjectsValue(@AncestorInPath AbstractProject context, @QueryParameter String value) {
            String[] tokens = Util.fixNull(value).split(",");
            for (String token : tokens) {
                String projectName = token.trim();
                if (StringUtils.isNotEmpty(projectName)) {
                    Item item = Jenkins.getInstance().getItem(projectName,context,Item.class);
                    if(item==null) {
                        return FormValidation.error("No such project: "+projectName);
                    }
                    if(!(item instanceof AbstractProject)) {
                        return FormValidation.error("Not buildable: "+projectName);
                    }
                }
            }

            return FormValidation.ok();
        }

        public AutoCompletionCandidates doAutoCompleteJoinProjectsValue(@QueryParameter String value) {
            String prefix = Util.fixNull(value);
            List<AbstractProject> projects = Jenkins.getInstance().getItems(AbstractProject.class);
            List<String> candidates = new ArrayList<String>();
            List<String> lowPrioCandidates = new ArrayList<String>();
            for (AbstractProject project : projects) {
                if (project.getFullName().startsWith(prefix)) {
                    candidates.add(project.getFullName());
                } else if (project.getFullName().toLowerCase().startsWith(prefix.toLowerCase())) {
                    lowPrioCandidates.add(project.getFullName());
                }
            }
            AutoCompletionCandidates autoCand = new AutoCompletionCandidates();
            autoCand.add(candidates.toArray(new String[candidates.size()]));
            autoCand.add(lowPrioCandidates.toArray(new String[candidates.size()]));
            return autoCand;
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
                CauseAction ca = run.getAction(CauseAction.class);
                if(ca == null) {
                    listener.getLogger().println("Join notifier requires a CauseAction");
                    return;
                }
                for(Cause c : ca.getCauses()) {
                    if (!(c instanceof UpstreamCause)) { continue; }
                    if (c instanceof JoinCause) { continue; }
                    UpstreamCause uc = (UpstreamCause)c;
                    notifyJob(abstractBuild, listener, uc.getUpstreamProject(), uc.getUpstreamBuild());
                }
                return;
            }

            private void notifyJob(AbstractBuild<?,?> abstractBuild, TaskListener listener, String upstreamProjectName,
                    int upstreamJobNumber) {
                List<AbstractProject> upstreamList = Items.fromNameList(abstractBuild.getProject().getParent(), upstreamProjectName,AbstractProject.class);
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
                for( Project<?,?> p : Jenkins.get().getProjects() ) {
                    BuildTrigger t = p.getPublishersList().get(BuildTrigger.class);
                    if ((t!=null) && (t.onJobRenamed(oldName,newName))) {
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

    /**
     * @deprecated as of 1.14
     */
    public List<AbstractProject> getJoinProjects() {
        return getJoinProjects(null);
    }

    public List<AbstractProject> getJoinProjects(ItemGroup context) {
        List<AbstractProject> list;
        if (joinProjects == null) {
            list = new ArrayList<AbstractProject>();
        } else {
            list = Items.fromNameList(context, joinProjects, AbstractProject.class);
        }
        return list;
    }

    public DescribableList<Publisher, Descriptor<Publisher>> getJoinPublishers() {
        return joinPublishers;
    }

    public Result getResultThreshold() {
        return this.resultThreshold;
    }

    private Object readResolve() {
        if(this.joinPublishers == null) {
            this.joinPublishers = new DescribableList<Publisher,Descriptor<Publisher>>(Saveable.NOOP);
        }
        if(this.resultThreshold == null) {
            this.resultThreshold = this.evenIfDownstreamUnstable ? Result.UNSTABLE : Result.SUCCESS;
        }
        return this;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

}
