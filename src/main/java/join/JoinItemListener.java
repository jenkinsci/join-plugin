package join;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.Project;
import hudson.model.listeners.ItemListener;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author: <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
@Extension
public class JoinItemListener extends ItemListener {

    @Override
    public void onRenamed(Item item, String oldName, String newName) {

        String context =  item.getParent().getFullName();
        if (context.length() > 0) context = context + "/";

        for( Project<?,?> p : Jenkins.getInstance().getAllItems(Project.class) ) {
            JoinTrigger t = p.getPublishersList().get(JoinTrigger.class);
            if(t!=null) {
                if(t.onJobRenamed(p.getParent(), Items.getCanonicalName(item.getParent(), oldName),
                                                 Items.getCanonicalName(item.getParent(), newName))) {
                    try {
                        p.save();
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Failed to persist project setting during rename from "+oldName+" to "+newName,e);
                    }
                }
            }
        }
    }

    private static final Logger LOGGER = Logger.getLogger(JoinItemListener.class.getName());
}
