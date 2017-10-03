package join;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author: <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class ItemsTest {

    @Test
    public void getCanonicalName() {
        assertEquals("foo", Items.getCanonicalName("", "foo"));
        assertEquals("foo", Items.getCanonicalName("", "/foo"));
        assertEquals("foo/bar", Items.getCanonicalName("", "foo/bar"));
        assertEquals("foo/bar", Items.getCanonicalName("foo", "bar"));
        assertEquals("bar", Items.getCanonicalName("foo", "/bar"));
        assertEquals("bar", Items.getCanonicalName("foo", "../bar"));
        assertEquals("foo/bar", Items.getCanonicalName("foo", "./bar"));
        assertEquals("foo/bar/baz/qux", Items.getCanonicalName("foo/bar", "baz/qux"));
        assertEquals("foo/baz/qux", Items.getCanonicalName("foo/bar", "../baz/qux"));
    }

    @Test
    public void rename() {
        assertEquals("bar", Items.rename("foo", "qux", "bar"));
        assertEquals("qux", Items.rename("foo", "qux", "foo"));
        assertEquals("/qux", Items.rename("foo", "qux", "/foo"));
        assertEquals("qux", Items.rename("foo/bar", "foo/qux", "bar"));
        assertEquals("../qux", Items.rename("foo/bar", "foo/qux", "../bar"));
        assertEquals("../foo/qux", Items.rename("foo/bar", "foo/qux", "../foo/bar"));
        assertEquals("bar", Items.rename("foo/bar", "qux/bar", "bar"));
        assertEquals("../qux/bar", Items.rename("foo/bar", "qux/bar", "../foo/bar"));
    }

}
