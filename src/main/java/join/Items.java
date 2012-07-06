package join;

import hudson.model.ItemGroup;
import org.apache.commons.lang.StringUtils;

import java.util.*;

/**
 * @author: <a hef="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class Items {

    /**
     * Computes the canonical full name of a relative path in an {@link ItemGroup} context, handling relative
     * positions ".." and "." as absolute path starting with "/". The resulting name is the item fullName from Jenkins
     * root.
     */
    public static String getCanonicalName(ItemGroup context, String path) {
        return getCanonicalName(context.getFullName(), path);
    }

    /* package */ static String getCanonicalName(String context, String path) {
        String[] c = context.split("/");
        String[] p = path.split("/");

        Stack name = new Stack();
        for (int i=0; i<c.length;i++) {
            if (i==0 && c[i].equals("")) continue;
            name.push(c[i]);
        }
        for (int i=0; i<p.length;i++) {
            if (i==0 && p[i].equals("")) {
                // Absolute path starting with a "/"
                name.clear();
                continue;
            }
            if (p[i].equals("..")) {
                name.pop();
                continue;
            }
            if (p[i].equals(".")) {
                continue;
            }
            name.push(p[i]);
        }
        return StringUtils.join(name, '/');
    }

    /**
     * Compute the relative name of an item after a rename occurred. Used to manage job references as names in
     * plugins to support {@link hudson.model.listeners.ItemListener#onRenamed(hudson.model.Item, String, String)}.
     * <p>
     * In a hierarchical context, when a plugin has a reference to a job as <code>../foo/bar</code> this method will
     * handle the relative path as "foo" is renamed to "zot" to compute <code>../zot/bar</code>
     *
     * @param oldFullName the old full name of the item
     * @param newFullName the new full name of the item
     * @param relativeNames coma separated list of Item relative names
     * @param context the {link ItemGroup} relative names refer to
     * @return relative name for the renamed item, based on the same ItemGroup context
     */
    public static String rename(String oldFullName, String newFullName, String relativeNames, ItemGroup context) {

        StringTokenizer tokens = new StringTokenizer(relativeNames,",");
        List<String> newValue = new ArrayList<String>();
        while(tokens.hasMoreTokens()) {
            String relativeName = tokens.nextToken().trim();
            String canonicalName = join.Items.getCanonicalName(context, relativeName);
            if (canonicalName.startsWith(oldFullName)) {
                String newCanonicalName = newFullName + canonicalName.substring(oldFullName.length());
                // relative name points to the renamed item, let's compute the new relative name
                newValue.add( rename(canonicalName, newCanonicalName, relativeName) );
            } else {
                newValue.add(relativeName);
            }
        }
        return StringUtils.join(newValue, ",");
    }

    /**
     * Compute the relative name of an Item after renaming
     */
    /* package */ static String rename(String oldFullName, String newFullName, String relativeName) {

        String[] a = oldFullName.split("/");
        String[] n = newFullName.split("/");
        assert a.length == n.length;
        String[] r = relativeName.split("/");

        int j = a.length-1;
        for(int i=r.length-1;i>=0;i--) {
            String part = r[i];
            if (part.equals("") && i==0) {
                continue;
            }
            if (part.equals(".")) {
                continue;
            }
            if (part.equals("..")) {
                j--;
                continue;
            }
            if (part.equals(a[j])) {
                r[i] = n[j];
                j--;
                continue;
            }
        }
        return StringUtils.join(r, '/');
    }

}
