package join;

import hudson.model.FreeStyleProject;
import hudson.util.FormValidation;
import org.jvnet.hudson.test.HudsonTestCase;

import java.io.IOException;

/**
 * @author wolfs
 */
public class FormValidationTest extends HudsonTestCase {

    public void testBasicValidation() throws IOException {
        FreeStyleProject first = createFreeStyleProject("First");
        FreeStyleProject second = createFreeStyleProject("Second");
        JoinTrigger.DescriptorImpl joinTriggerDescriptor = new JoinTrigger.DescriptorImpl();

        FormValidation formValidation = joinTriggerDescriptor.doCheckJoinProjectsValue(first,"");
        assertEquals(FormValidation.Kind.OK, formValidation.kind);

        formValidation = joinTriggerDescriptor.doCheckJoinProjectsValue(first,null);
        assertEquals(FormValidation.Kind.OK, formValidation.kind);

        formValidation = joinTriggerDescriptor.doCheckJoinProjectsValue(first,"First, Second");
        assertEquals(FormValidation.Kind.OK, formValidation.kind);

        formValidation = joinTriggerDescriptor.doCheckJoinProjectsValue(first,"First, Second, ");
        assertEquals(FormValidation.Kind.OK, formValidation.kind);

        formValidation = joinTriggerDescriptor.doCheckJoinProjectsValue(first,"First, Second,");
        assertEquals(FormValidation.Kind.OK, formValidation.kind);

        formValidation = joinTriggerDescriptor.doCheckJoinProjectsValue(first,"First, ,Second,");
        assertEquals(FormValidation.Kind.OK, formValidation.kind);

        formValidation = joinTriggerDescriptor.doCheckJoinProjectsValue(first," ,First,Second,");
        assertEquals(FormValidation.Kind.OK, formValidation.kind);

        formValidation = joinTriggerDescriptor.doCheckJoinProjectsValue(first,"   First,Second,");
        assertEquals(FormValidation.Kind.OK, formValidation.kind);

        formValidation = joinTriggerDescriptor.doCheckJoinProjectsValue(first,"First, Third,Second,");
        assertEquals(FormValidation.Kind.ERROR, formValidation.kind);
    }

    public void testReformatJoinProjectValue() throws IOException {
        createFreeStyleProject("First");
        createFreeStyleProject("Second");
        JoinTrigger.DescriptorImpl joinTriggerDescriptor = new JoinTrigger.DescriptorImpl();

        String formatted = joinTriggerDescriptor.reformatJoinProjectsValue("");
        assertEquals("", formatted);

        formatted = joinTriggerDescriptor.reformatJoinProjectsValue(null);
        assertEquals("", formatted);

        formatted = joinTriggerDescriptor.reformatJoinProjectsValue("First, Second");
        assertEquals("First, Second", formatted);

        formatted = joinTriggerDescriptor.reformatJoinProjectsValue(" ,First,Second,");
        assertEquals("First, Second", formatted);

        formatted = joinTriggerDescriptor.reformatJoinProjectsValue(" ,First,,, , ,Second,");
        assertEquals("First, Second", formatted);

        formatted = joinTriggerDescriptor.reformatJoinProjectsValue("First, Third,Second,");
        assertEquals("First, Third, Second", formatted);
    }

}
