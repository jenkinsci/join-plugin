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

import hudson.Launcher;
import hudson.Extension;
import hudson.tasks.Messages;
import hudson.Util;
import hudson.security.AccessControlled;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildTrigger;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.matrix.MatrixAggregatable;
import hudson.matrix.MatrixAggregator;
import hudson.matrix.MatrixBuild;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.DependecyDeclarer;
import hudson.model.DependencyGraph;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.Items;
import hudson.model.Job;
import hudson.model.Project;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.Cause.UpstreamCause;
import hudson.model.listeners.ItemListener;
import hudson.model.listeners.RunListener;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class JoinTrigger extends Recorder {
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
    private boolean evenIfDownstreamUnstable;
    
    public JoinTrigger() {
        this("", false);
    }
    
    public JoinTrigger(String string, boolean b) {
        this.joinProjects = string;
        this.evenIfDownstreamUnstable = b;
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        public String getDisplayName() {
            return "Join Trigger";
        }

        public String getHelpFile() {
            return "/plugin/join/help/joinTrigger.html";
        }

        public JoinTrigger newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return new JoinTrigger(
                formData.getString("joinProjectsValue"),
                formData.has("evenIfDownstreamUnstable") && formData.getBoolean("evenIfDownstreamUnstable"));
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        public boolean showEvenIfUnstableOption(Class<? extends AbstractProject> jobType) {
            // UGLY: for promotion process, this option doesn't make sense. 
            return !jobType.getName().contains("PromotionProcess");
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

            public void onCompleted(Run run, TaskListener listener) {
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
                    UpstreamCause uc = (UpstreamCause)c;
                    notifyJob(run, listener, uc.getUpstreamProject(), uc.getUpstreamBuild());
                }
                return;
            }
            
            private void notifyJob(Run run, TaskListener listener, String upstreamProject,
                    int upstreamJobNumber) {
                List<AbstractProject> upstreamList = Items.fromNameList(upstreamProject,AbstractProject.class);
                if(upstreamList.size() != 1) {
                    listener.getLogger().println("Join notifier cannot find upstream project: " + upstreamProject);
                    return;
                };
                AbstractProject<?,?> upstream = upstreamList.get(0);
                Run r = upstream.getBuildByNumber(upstreamJobNumber);
                
                if(r == null) {
                    listener.getLogger().println("Join notifier cannot find upstream build: " + upstreamProject + " number " + upstreamJobNumber);
                    return;
                };
                JoinAction ja = r.getAction(JoinAction.class);
                if(ja == null) {
                    listener.getLogger().println("Join notifier cannot find upstream JoinAction: " + upstreamProject + " number " + upstreamJobNumber);
                    return;            
                }
                listener.getLogger().println("Notifying upstream of completion: " + upstreamProject + " #" + upstreamJobNumber);
                ja.downstreamFinished(run.getParent().getName(), run, listener);
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

    private static final Logger LOGGER = Logger.getLogger(BuildTrigger.class.getName());

    
    public String getJoinProjectsValue() {
        return joinProjects;
    }

    public List<AbstractProject> getJoinProjects() {
        return Items.fromNameList(joinProjects,AbstractProject.class);
    }
    
    public boolean getEvenIfDownstreamUnstable() {
        return this.evenIfDownstreamUnstable;
    }
}
