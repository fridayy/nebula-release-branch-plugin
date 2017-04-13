package nebula.plugin.release

import org.ajoberstar.gradle.git.release.base.ReleaseVersion
import org.ajoberstar.gradle.git.release.base.TagStrategy
import org.ajoberstar.grgit.Grgit
import org.ajoberstar.grgit.operation.BranchAddOp

/**
 * @author bnjm@harmless.ninja - 4/13/17.
 */
class TagAndBranchStrategy extends TagStrategy {

    private final String BRANCH_POSTIFX = "branch"

    @Override
    String maybeCreateTag(Grgit grgit, ReleaseVersion version) {
        if(version.createTag) {
            String name = toTagString(version.version)
            grgit.branch.add(name: "${name}-${BRANCH_POSTIFX}",
                    startPoint: "origin/master",
                    mode: BranchAddOp.Mode.TRACK)
            grgit.push(all: true)

        }
        return super.maybeCreateTag(grgit, version)
    }
}
