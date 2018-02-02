/**
 * Publish maven artifacts
 *
 * @param {String} args.goal - maven goal to execute
 */
void publishMaven(Map args) {
    util.validateArgs args, ['goal']

    def credentials = 'artifactory-credentials'
    def releaseRepo = 'product-copy'
    def snapshotRepo = 'product-copy'

    artifactory.mavenDeploy credentials: credentials,
            goal: args.goal,
            releaseRepo: releaseRepo,
            snapshotRepo: snapshotRepo
}