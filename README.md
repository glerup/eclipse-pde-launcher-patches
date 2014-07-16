Patches for Eclipse PDE feature-based launch configurations [![Build Status](https://travis-ci.org/glerup/eclipse-pde-launcher-patches.svg?branch=patch%2Fpde-feature-launch)](https://travis-ci.org/glerup/eclipse-pde-launcher-patches)
===========================================================

This project provides a Feature Patch that solves two annoyances when using feature-based
launch configurations in the Eclipse Plug-in Development Environment.

The two annoyances are:

 1. Eclipse PDE adds optional dependencies and fragments in the workspace even if they are
    not included in any of the features in the launch configuration.

    This behavior prevents using feature-based launches for many use cases where you need
    control over which plug-ins/bundles are included.

    See: https://bugs.eclipse.org/bugs/show_bug.cgi?id=338078

    This feature patch adds an option to the launch configuration page to control this
    behavior. The option is labelled "Add additional dependencies automatically prior to
    launching".

 2. Eclipse PDE does not apply the platform filters that can be specified in `feature.xml`
    files.

    This behavior results in unnecessary warnings when launching.

    This feature patch applies the platform filter in the `feature.xml` to exclude
    plug-ins that do not match the target platform.

Building the feature patch
--------------------------

The following tools are required to build the feature patch:

 * A JDK
 * Maven: http://maven.apache.org/

You may use the [sparse
checkout](http://jasonkarns.com/blog/subdirectory-checkouts-with-git-sparse-checkout/)
feature in Git to only check out the modified projects. Include these paths in the
`sparse-checkout` file:

    ui/org.eclipse.pde.launching
    ui/org.eclipse.pde.ui
    com.deltek.pde.launching.feature.patch
    com.deltek.pde.launching.parent
    com.deltek.pde.launching.updatesite

Build the project `com.deltek.pde.launching.updatesite`:

    $ cd com.deltek.pde.launching.updatesite
    $ mvn clean verify

This will produce a p2 repository containing the feature patch:

    $ tree target/repository
    target/repository
    ├── artifacts.jar
    ├── content.jar
    ├── features
    │   └── com.deltek.pde.launching.feature.patch_1.0.44.jar
    └── plugins
        ├── org.eclipse.pde.launching_3.6.200.v20140716-1223.jar
        └── org.eclipse.pde.ui_3.8.100.v20140716-1223.jar
