package join;

import hudson.model.Descriptor;
import hudson.model.FreeStyleProject;
import hudson.model.Saveable;
import hudson.tasks.Publisher;
import hudson.util.DescribableList;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author: <a hef="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class RenameTest extends BasicJoinPluginTest {

    public void testSimpleRenamed() throws Exception {
        addJoinTriggerToSplitProject(splitProject, joinProject);
        joinProject.renameTo("newName");

        JoinTrigger t = splitProject.getPublishersList().get(JoinTrigger.class);
        assertEquals("newName", t.getJoinProjectsValue());
    }

    public void testRenamedInList() throws Exception {
        FreeStyleProject join1 = createFreeStyleProject("join1");
        FreeStyleProject join2 = createFreeStyleProject("join2");
        addJoinTriggerToSplitProject(splitProject, join1, joinProject, join2);
        joinProject.renameTo("newName");

        JoinTrigger t = splitProject.getPublishersList().get(JoinTrigger.class);
        assertEquals("join1,newName,join2", t.getJoinProjectsValue());
    }

    public void testAbsoluteRenamed() throws Exception {
        splitProject.getPublishersList().add(new JoinTrigger(new DescribableList<Publisher, Descriptor<Publisher>>(Saveable.NOOP),
                "/joinProject", "SUCCESS"));
        joinProject.renameTo("newName");

        JoinTrigger t = splitProject.getPublishersList().get(JoinTrigger.class);
        assertEquals("/newName", t.getJoinProjectsValue());
    }

}
