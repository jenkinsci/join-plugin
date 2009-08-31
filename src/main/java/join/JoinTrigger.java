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
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Descriptor;
import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.Items;
import hudson.model.Project;
import hudson.model.Run;
import hudson.model.Saveable;
import hudson.model.TaskListener;
import hudson.model.Cause.UpstreamCause;
import hudson.model.listeners.ItemListener;
import hudson.model.listeners.RunListener;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildTrigger;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.DescribableList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import join.JoinAction.JoinCause;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;

@Extension
public class JoinTrigger extends Recorder {
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
        JoinAction joinAction = new JoinAction(this, buildTrigger);
        build.addAction(joinAction);
        joinAction.checkPendingDownstream(build, listener);
        return true;
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

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

        public String getDisplayName() {
            return "Join Trigger";
        }

        public String getHelpFile() {
            return "/plugin/join/help/joinTrigger.html";
        }

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
            return FreeStyleProject.class.isAssignableFrom(jobType);
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
            list.add(Hudson.getInstance().getDescriptorByType(hudson.tasks.Mailer.DescriptorImpl.class));
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
                };
                AbstractProject<?,?> upstreamProject = upstreamList.get(0);
                Run upstreamRun = upstreamProject.getBuildByNumber(upstreamJobNumber);
                
                if(upstreamRun == null) {
                    listener.getLogger().println("Join notifier cannot find upstream run: " + upstreamProjectName + " number " + upstreamJobNumber);
                    return;
                };
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
        return Items.fromNameList(joinProjects,AbstractProject.class);
    }

    public DescribableList<Publisher, Descriptor<Publisher>> getJoinPublishers() {
        return joinPublishers;
    }

    public boolean getEvenIfDownstreamUnstable() {
        return this.evenIfDownstreamUnstable;
    }
}
