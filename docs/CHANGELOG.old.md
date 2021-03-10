## Changelog

#### Version 1.16 - August 2, 2015

-   Removed integration with deprecated [CopyArchiver
    Plugin](https://wiki.jenkins.io/display/JENKINS/CopyArchiver+Plugin)
    (pull \#6)
-   Pick up job renames properly
-   JENKINS-16201 - Handle cache reloads correctly.  Previous behavior
    may not see all downstream jobs as completed, and so would never
    start the join job
-   JENKINS-25710 - Work with folders

#### Version 1.15 - May 3, 2012

-   Supported hierarchical projects (even more)

#### Version 1.14 - April 5, 2012

-   Supported hierarchical projects

#### Version 1.13 - September 18, 2011

-   Add a method fromSameSplitProject to JoinDependency for use in other
    plugins - e.g. the Build Pipeline View.

#### Version 1.12 - August 28, 2011

-   Add support for downstream-ext plugin
-   Fix [9903](https://issues.jenkins-ci.org/browse/JENKINS-9903):
    Downstream projects include the "join" project when using the
    downstream-ext plugin

#### Version 1.11 - July 11, 2011

-   Fix [10301](https://issues.jenkins-ci.org/browse/JENKINS-10301):
    Jenkins does not start when the parameterized trigger plugin with
    version 2.10 and the join plugin with version 1.10.1 are installed.
    Join Plugin does not work with versions of the [Parameterized
    Trigger
    Plugin](https://wiki.jenkins.io/display/JENKINS/Parameterized+Trigger+Plugin)
    prior to 2.10.

#### Version 1.10.1 - April 11, 2011

-   Fix [8443](https://issues.jenkins-ci.org/browse/JENKINS-8443)
-   Added autocompletion and form validation to join projects text field
    Jobs in the join projects field which don't exist will be pruned on
    save

#### Version 1.10 - April 11, 2011

-   Failure when publishing artifacts

#### Version 1.9 - September 13, 2010

-   Fix NPE on newer versions of Hudson when adding a post-build action
    like the copy-archiver or the parameterized-trigger plugin
    ([7344](https://issues.jenkins-ci.org/browse/JENKINS-7344))
-   Run parametrized-trigger after join should work again on Hudson
    version newer than 1.341
    ([5602](https://issues.jenkins-ci.org/browse/JENKINS-5602))
-   Respect disabled projects: Start join projects when all non-disabled
    downstream projects are finished
    ([5972](https://issues.jenkins-ci.org/browse/JENKINS-5972)).

#### Version 1.7 - January 16, 2010

-   Avoid error if
    [parameterized-trigger](https://wiki.jenkins.io/display/JENKINS/Parameterized+Trigger+Plugin)
    plugin is installed, but current project doesn't use a parameterized
    BuildTrigger.
    ([5159](https://issues.jenkins-ci.org/browse/JENKINS-5159))

#### Version 1.6 - September 30, 2009

-   The join plugin will now wait for downstream builds triggered by the
    parameterized-trigger plugin, in addition to the built-in downstream
    projects, before performing the join actions.
-   Implement the `getRequiredMonitorService` method to indicate no
    dependency on the previous build. This should allow more parallelism
    when using concurrent builds.

#### Version 1.5 - September 18, 2009

-   Fix problem where email recipients were cleared on job save
    ([4384](https://hudson.dev.java.net/issues/show_bug.cgi?id=4384))

#### Version 1.4 - September 2, 2009

-   Fix NPE for builds that are automatically upgraded from version 1.2
    or earlier
    ([4370](https://hudson.dev.java.net/issues/show_bug.cgi?id=4370))
-   Re-add Maven projects as applicable for the Join plugin. Matrix
    (multi-config) projects remain incompatible.  Feedback of using this
    plugin with Maven projects is sought.

#### Version 1.3 - August 31, 2009

-   Remove console log warnings from builds that are not using the join
    plugin
    ([report](http://www.nabble.com/Join-notifier-cannot-find-upstream-JoinAction-tt25077029.html))
-   Provide initial support for running arbitrary post-build actions as
    part of the join process. The parameterized-build plugin is the
    first candidate
    ([3959](https://hudson.dev.java.net/issues/show_bug.cgi?id=3959))
-   Only offer Join plugin with Freestyle builds, due to report of
    Matrix build incompatibility.
    ([report](http://www.nabble.com/Regarding-build-td24848107.html#a24868203))

#### Version 1.2 - June 28, 2009

-   Downstream failure detection was broken previous to this version.
    Previously, the join projects were started no matter what the result
    of the downstream builds. With this fix, failed downstream builds
    block the join projects from being started
    ([report](http://www.nabble.com/Join-plugin-1.1-released-td23796412.html#a23872626))

#### Version 1.1 - May 30, 2009

-   Fix a NPE that occurs when the join plugin is enabled, but no
    downstream jobs are specified
    ([report](http://www.nabble.com/Join-plugin-1.0-released-td23680165.html#a23741501))
-   Start the join projects immediately if there are no downstream jobs
    specified.

#### Version 1.0 - May 23, 2009

-   Basic support for joining. After the downstream jobs finish, a comma
    separated list of jobs can be started as the join jobs.
