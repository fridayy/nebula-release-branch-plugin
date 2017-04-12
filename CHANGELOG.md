4.2.0 / 2017-03-31
==================

* Calculate version in repository with no commits
* Allow pushing tags from detached branch
* Better handle branch patterns that would error out semver when put in dev version

4.0.1 / 2016-02-05
==================

* Fix tasks task so it can run on multiprojects

4.0.0 / 2016-02-04
==================

* Potential BREAKING change: Removing assumptions on whether to enable bintray and artifactory publishes based on whether a user uses the `devSnapshot`, `snapshot`, `candidate`, or `final` tasks. These should be on more opinionated plugins.

3.2.0 / 2016-02-01
==================

* Use specific versions of dependencies to prevent dynamic versions in the gradle plugin portal
* upgrade nebula-bintray-plugin 3.2.0 -> 3.3.1
* upgrade gradle-gt plugin 1.3.2 -> 1.4.1

3.1.3 / 2016-01-28
==================

* Remove need for initial tag
* Better error message for release/[invalid semver pattern]

3.1.2 / 2015-12-09
==================

* Better error reporting for missing init tag and uncommitted changes

3.1.1 / 2015-12-08
==================

* Allow to customize the location of Git root 

3.1.0 / 2015-10-26
==================

* Update ivy status and project.status on publish
    * `devSnapshot` and `snapshot` will leave status at integration
    * `candidate` will set ivy and project status to candidate
    * `final` will set ivy and project status to release
* Also depend on artifactory tasks if creating devSnapshot
* Warn rather than fail when the project contains no git repository or the git repository has no commits.

3.0.5 / 2015-10-19
==================

* Republish correctly

3.0.4 / 2015-10-19
==================

* gradle-git to 1.3.2 // publishing issue

3.0.3 / 2015-10-06
==================

* gradle-git to 1.3.1

3.0.2 / 2015-09-18
==================

* BUGFIX: Allow release from rc tag

3.0.1 / 2015-09-09
==================

* BUGFIX: fix ordering so we don't release if tests are broken

3.0.0 / 2015-09-04
==================

* Move to gradle-git 1.3.0
* Plugin built against gradle 2.6
* New travis release process

2.2.7 / 2015-07-14
==================

* Move to gradle-git 1.2.0
* Only calculate version once for multiprojects

2.2.6 / 2015-06-18
==================

* Move to gradle-git 1.1.0

2.2.5 / 2015-02-09
==================

* Add ability to use major.minor.x branches along with major.x branches.
* Update nebula dependencies to newest releases on 2.2.x branches.

2.2.4 / 2015-01-19
==================

* Modify -Prelease.useLastTag so that it doesn't attempt to push tags to the remote

2.2.3 / 2014-12-11
==================

* Fix to still have `devSnapshot` task work if a user changes the default versioning strategy

2.2.2 / 2014-12-05
==================

* Minor change to allow users to configure the default versioning scheme via gradle-git's release extension

2.2.1 / 2014-12-05
==================

* Add nebula-release properties file so this can be used as a plugin
* rename package from nebula.plugins.release to nebula.plugin.release

2.2.0 / 2014-12-04 (removed from jcenter)
=========================================

* does not include META-INF/gradle-plugins properties file
* Initial release
* Skip straight to 2.2.x to show built and compatible with gradle 2.2
