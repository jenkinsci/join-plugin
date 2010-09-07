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
import hudson.Functions;
import hudson.Launcher;
import hudson.Plugin;
import hudson.maven.AbstractMavenProject;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.DependencyGraph;
import hudson.model.Descriptor;
import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.Items;
import hudson.model.Project;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.Saveable;
import hudson.model.TaskListener;
import hudson.model.Cause.UpstreamCause;
import hudson.model.DependecyDeclarer;
import hudson.model.DependencyGraph.Dependency;
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

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import join.JoinAction.JoinCause;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;

@Extension
public class JoinTrigger extends Recorder implements DependecyDeclarer {

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

    private String joinProjects;
    
    private DescribableList<Publisher,Descriptor<Publisher>> joinPublishers =
        new DescribableList<Publisher,Descriptor<Publisher>>((Saveable)null);

    private boolean evenIfDownstreamUnstable;
    
    public JoinTrigger() {
        this(new DescribableList<Publisher, Descriptor<Publisher>>((Saveable)null), "", false);
    }
    
    public JoinTrigger(DescribableList<Publisher,Descriptor<Publisher>> publishers,String string, boolean b) {
        this.joinProjects = string;
        this.evenIfDownstreamUnstable = b;
        this.joinPublishers = publishers;
    }

    @Override
    public void buildDependencyGraph(AbstractProject owner, DependencyGraph graph) {
        if (!canDeclare(owner)) {
            return;
        }

        final List<AbstractProject<?,?>> downstreamProjects = getAllDownstream(owner);
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
            return DependencyGraph.class.getClasses().length > 0
                    // See HUDSON-6274 -- currently Maven projects call scheduleProject
                    // directly, so would not get parameters from DependencyGraph.
                    // Remove this condition when HUDSON-6274 is implemented.
                    && !owner.getClass().getName().equals("hudson.maven.MavenModuleSet");
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
                        if (!downStreamProject.isDisabled()) {
                            ret.add(downStreamProject);
                        }
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

    public List<AbstractProject<?,?>> getAllDownstream(AbstractProject<?,?> project) {
        List<AbstractProject<?,?>> downstream = getBuildTriggerDownstream(project);
        downstream.addAll(getParameterizedDownstream(project));
        return downstream;
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
            DescribableList<Publisher,Descriptor<Publisher>> publishers = 
                new DescribableList<Publisher,Descriptor<Publisher>>((Saveable)null);
            
            JSONObject postbuild = formData.optJSONObject("postbuildactions");
            if(postbuild != null) {
                JSONObject joinPublishers = postbuild.optJSONObject("joinPublishers");
                if(joinPublishers != null) {
                    publishers.rebuild(req, joinPublishers, getApplicableDescriptors());
                }
            }

            JSONObject experimentalpostbuild = formData.optJSONObject("experimentalpostbuildactions");
            if(experimentalpostbuild != null) {
                JSONObject joinTriggers = experimentalpostbuild.optJSONObject("joinPublishers");
                if(joinTriggers != null) {
                    LOGGER.finest("EPB: " + joinTriggers.toString() + joinTriggers.isArray());
                    publishers.rebuild(req, joinTriggers, Publisher.all());
                }
            }

            LOGGER.finer("Parsed " + publishers.size() + " publishers");
            return new JoinTrigger(publishers,
                formData.getString("joinProjectsValue"),
                formData.has("evenIfDownstreamUnstable") && formData.getBoolean("evenIfDownstreamUnstable"));
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            boolean freeStyle = FreeStyleProject.class.isAssignableFrom(jobType);
            if(freeStyle) {
                return true;
            }
            Plugin mavenProjectPlugin = Hudson.getInstance().getPlugin("maven-plugin");
            return mavenProjectPlugin != null && AbstractMavenProject.class.isAssignableFrom(jobType);
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
        
        public List<Descriptor<Publisher>> getApplicableExperimentalDescriptors(AbstractProject<?,?> project) {
            List<Descriptor<Publisher>> list = Functions.getPublisherDescriptors(project);
            LOGGER.finest("publisher count before removing publishers: " + list.size());
            // need to prevent infinite recursion, so we remove the Join publisher.
            list.remove(Hudson.getInstance().getDescriptorByType(DescriptorImpl.class));
            list.removeAll(getApplicableDescriptors());
            LOGGER.finest("publisher count after removing publishers: " + list.size());
            return list;
        }
        /**
         * Form validation method.
         */
//        public FormValidation doCheckChildProjectsValue(@QueryParameter String value ) {
//            StringTokenizer tokens = new StringTokenizer(Util.fixNull(value),",");
//            while(tokens.hasMoreTokens()) {
//                String projectName = tokens.nextToken().trim();
//                Item item = Hudson.getInstance().getItemByFullName(projectName,Item.class);
//                if(item==null)
//                    return FormValidation.error(Messages.BuildTrigger_NoSuchProject(projectName,AbstractProject.findNearest(projectName).getName()));
//                if(!(item instanceof AbstractProject))
//                    return FormValidation.error(Messages.BuildTrigger_NotBuildable(projectName));
//            }
//
//            return FormValidation.ok();
//        }

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

    private static final Logger LOGGER = Logger.getLogger(JoinTrigger.class.getName());

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
    
    public boolean useExperimentalPostBuildActions(AbstractProject<?,?> project) {
        return containsAnyDescriptor(DescriptorImpl.DESCRIPTOR.getApplicableExperimentalDescriptors(project));
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
            this.joinPublishers = new DescribableList<Publisher,Descriptor<Publisher>>((Saveable)null);
        }
        return this;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }
}
