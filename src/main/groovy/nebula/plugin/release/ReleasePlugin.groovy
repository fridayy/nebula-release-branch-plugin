/*
 * Copyright 2014-2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nebula.plugin.release

import nebula.core.ProjectType
import org.ajoberstar.gradle.git.release.base.BaseReleasePlugin
import org.ajoberstar.gradle.git.release.base.ReleasePluginExtension
import org.ajoberstar.grgit.Grgit
import org.eclipse.jgit.errors.RepositoryNotFoundException
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.ivy.IvyPublication
import org.gradle.api.publish.ivy.plugins.IvyPublishPlugin

class ReleasePlugin implements Plugin<Project> {
    public static final String DISABLE_GIT_CHECKS = 'release.disableGitChecks'
    Project project
    Grgit git
    static Logger logger = Logging.getLogger(ReleasePlugin)

    static final String SNAPSHOT_TASK_NAME = 'snapshot'
    static final String DEV_SNAPSHOT_TASK_NAME = 'devSnapshot'
    static final String CANDIDATE_TASK_NAME = 'candidate'
    static final String FINAL_TASK_NAME = 'final'
    static final String RELEASE_CHECK_TASK_NAME = 'releaseCheck'
    static final String NEBULA_RELEASE_EXTENSION_NAME = 'nebulaRelease'
    static final String GROUP = 'Nebula Release'

    @Override
    void apply(Project project) {
        this.project = project

        def gitRoot = project.hasProperty('git.root') ? project.property('git.root') : project.rootProject.projectDir

        try {
            git = Grgit.open(dir: gitRoot)
        }
        catch (RepositoryNotFoundException e) {
            this.project.version = '0.1.0-dev.0.uncommitted'
            logger.warn("Git repository not found at $gitRoot -- nebula-release tasks will not be available. Use the git.root Gradle property to specify a different directory.")
            return
        }
        checkForBadBranchNames()

        ProjectType type = new ProjectType(project)
        if (type.isRootProject) {
            project.plugins.apply(BaseReleasePlugin)
            ReleasePluginExtension releaseExtension = project.extensions.findByType(ReleasePluginExtension)
            releaseExtension.with {
                versionStrategy new OverrideStrategies.NoCommitStrategy()
                versionStrategy new OverrideStrategies.ReleaseLastTagStrategy(project)
                versionStrategy new OverrideStrategies.GradlePropertyStrategy(project)
                versionStrategy NetflixOssStrategies.SNAPSHOT
                versionStrategy NetflixOssStrategies.DEVELOPMENT
                versionStrategy NetflixOssStrategies.PRE_RELEASE
                versionStrategy NetflixOssStrategies.FINAL
                defaultVersionStrategy = NetflixOssStrategies.DEVELOPMENT
            }

            releaseExtension.with {
                grgit = git
                it.metaClass.tagStrategy = new TagAndBranchStrategy()
                tagStrategy {
                    generateMessage = { version ->
                        StringBuilder builder = new StringBuilder()
                        builder << "Release of ${version.version}\n\n"

                        if (version.previousVersion) {
                            String previousVersion = "v${version.previousVersion}^{commit}"
                            List excludes = []
                            if (tagExists(grgit, previousVersion)) {
                                excludes << previousVersion
                            }

                            grgit.log(
                                    includes: ['HEAD'],
                                    excludes: excludes
                            ).inject(builder) { bldr, commit ->
                                bldr << "- ${commit.id}: ${commit.shortMessage}\n"
                            }
                        }
                        builder.toString()
                    }
                }
            }

            def nebulaReleaseExtension = project.extensions.create(NEBULA_RELEASE_EXTENSION_NAME, ReleaseExtension)
            NetflixOssStrategies.BuildMetadata.nebulaReleaseExtension = nebulaReleaseExtension

            def releaseCheck = project.tasks.create(RELEASE_CHECK_TASK_NAME, ReleaseCheck)
            releaseCheck.group = GROUP
            releaseCheck.grgit = releaseExtension.grgit
            releaseCheck.patterns = nebulaReleaseExtension

            def snapshotTask = project.task(SNAPSHOT_TASK_NAME)
            def devSnapshotTask = project.task(DEV_SNAPSHOT_TASK_NAME)
            def candidateTask = project.task(CANDIDATE_TASK_NAME)
            candidateTask.doLast {
                project.allprojects.each { it.status = 'candidate' }
            }
            def finalTask = project.task(FINAL_TASK_NAME)
            finalTask.doLast {
                project.allprojects.each { it.status = 'release' }
            }

            [snapshotTask, devSnapshotTask, candidateTask, finalTask].each {
                it.group = GROUP
                it.finalizedBy project.tasks.release
                it.dependsOn releaseCheck
            }

            def cliTasks = project.gradle.startParameter.taskNames
            determineStage(cliTasks, releaseCheck)
            checkStateForStage()

            if (shouldSkipGitChecks()) {
                project.tasks.release.deleteAllActions()
                project.tasks.prepare.deleteAllActions()
            }
        } else {
            project.version = project.rootProject.version
        }

        if (type.isLeafProject) {
            project.plugins.withType(JavaPlugin) {
                project.rootProject.tasks.release.dependsOn project.tasks.build
            }
        }
    }

    private void determineStage(List<String> cliTasks, ReleaseCheck releaseCheck) {
        def hasSnapshot = cliTasks.contains(SNAPSHOT_TASK_NAME)
        def hasDevSnapshot = cliTasks.contains(DEV_SNAPSHOT_TASK_NAME)
        def hasCandidate = cliTasks.contains(CANDIDATE_TASK_NAME)
        def hasFinal = cliTasks.contains(FINAL_TASK_NAME)
        if ([hasSnapshot, hasDevSnapshot, hasCandidate, hasFinal].count { it } > 2) {
            throw new GradleException('Only one of snapshot, devSnapshot, candidate, or final can be specified.')
        }

        releaseCheck.isSnapshotRelease = hasSnapshot || hasDevSnapshot || (!hasCandidate && !hasFinal)

        if (hasFinal) {
            setupStatus('release')
            applyReleaseStage('final')
        } else if (hasCandidate) {
            setupStatus('candidate')
            applyReleaseStage('rc')
        } else if (hasSnapshot) {
            applyReleaseStage('SNAPSHOT')
        } else if (hasDevSnapshot) {
            applyReleaseStage('dev')
        }
    }

    private void checkStateForStage() {
        if (!project.tasks.releaseCheck.isSnapshotRelease) {
            def status = git.status()
            def uncommittedChangesFound = [status.staged, status.unstaged].any { it.getAllChanges().size() > 0 }
            if (uncommittedChangesFound) {
                throw new GradleException('Final and candidate builds require all changes to be committed into Git.')
            }
        }
    }

    private boolean shouldSkipGitChecks() {
        (project.hasProperty(DISABLE_GIT_CHECKS) && project.property(DISABLE_GIT_CHECKS) as Boolean) ||
                (project.hasProperty('release.travisci') && project.property('release.travisci').toBoolean())
    }

    void setupStatus(String status) {
        project.plugins.withType(IvyPublishPlugin) {
            project.publishing {
                publications.withType(IvyPublication) {
                    descriptor.status = status
                }
            }
        }
    }

    void applyReleaseStage(String stage) {
        final String releaseStage = 'release.stage'
        project.allprojects.each { it.ext.set(releaseStage, stage) }
    }

    private boolean tagExists(Grgit grgit, String revStr) {
        try {
            grgit.resolve.toCommit(revStr)
            return true
        } catch (e) {
            return false
        }
    }

    boolean isClassPresent(String name) {
        try {
            Class.forName(name)
            return true
        } catch (Throwable ex) {
            logger.debug("Class $name is not present")
            return false
        }
    }

    void checkForBadBranchNames() {
        if (git.branch.current.name ==~ /release\/\d+(\.\d+)?/) {
            throw new GradleException('Branches with pattern release/<version> are used to calculate versions. The version must be of form: <major>.x, <major>.<minor>.x, or <major>.<minor>.<patch>')
        }
    }
}
