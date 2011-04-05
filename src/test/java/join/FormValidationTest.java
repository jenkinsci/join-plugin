package join;

import hudson.util.FormValidation;
import org.jvnet.hudson.test.HudsonTestCase;

import java.io.IOException;

/**
 * @author wolfs
 */
public class FormValidationTest extends HudsonTestCase {

    public void testBasicValidation() throws IOException {
        createFreeStyleProject("First");
        createFreeStyleProject("Second");
        JoinTrigger.DescriptorImpl joinTriggerDescriptor = new JoinTrigger.DescriptorImpl();

        FormValidation formValidation = joinTriggerDescriptor.doCheckJoinProjectsValue("");
        assertEquals(FormValidation.Kind.OK, formValidation.kind);

        formValidation = joinTriggerDescriptor.doCheckJoinProjectsValue(null);
        assertEquals(FormValidation.Kind.OK, formValidation.kind);

        formValidation = joinTriggerDescriptor.doCheckJoinProjectsValue("First, Second");
        assertEquals(FormValidation.Kind.OK, formValidation.kind);

        formValidation = joinTriggerDescriptor.doCheckJoinProjectsValue("First, Second, ");
        assertEquals(FormValidation.Kind.OK, formValidation.kind);

        formValidation = joinTriggerDescriptor.doCheckJoinProjectsValue("First, Second,");
        assertEquals(FormValidation.Kind.OK, formValidation.kind);

        formValidation = joinTriggerDescriptor.doCheckJoinProjectsValue("First, ,Second,");
        assertEquals(FormValidation.Kind.OK, formValidation.kind);

        formValidation = joinTriggerDescriptor.doCheckJoinProjectsValue(" ,First,Second,");
        assertEquals(FormValidation.Kind.OK, formValidation.kind);

        formValidation = joinTriggerDescriptor.doCheckJoinProjectsValue("   First,Second,");
        assertEquals(FormValidation.Kind.OK, formValidation.kind);

        formValidation = joinTriggerDescriptor.doCheckJoinProjectsValue("First, Third,Second,");
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
        assertEquals("First, Second", formatted);
    }

}
