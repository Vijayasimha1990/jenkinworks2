/**
 * Method that will promote a service from one environment to the next
 *
 * @param {String} args.projectName - Service name to promote
 * @param {String} args.envToPromote - Environment to be promoted (develop, staging)
 * @param {String} args.destinationEnvironment - Environment to be promoted to (staging, production)
 * @param {?String} args.k8sCluster - destination k8s cluster, if not specified will be deployed in the one corresponging to the destination environment (optional)
 *
 */
def promoteService(Map args){
    util.validateArgs args, ['projectName', 'envToPromote', 'destinationEnvironment']


    // pipeline config
    def javaAgent = 'build && java'
    def dockerAgent = 'docker-2'
    def kubectlAgent = 'deploy && datacenter && linux'

    // project config
    def projectName
    def deployTag
    def commit
    def branch
    def repo

    // git config
    def gitCredentials = 'git-access'

    // docker config
    def dockerCredentials = 'adidas-docker-registry'
    def dockerRepo = 'tools.adidas-group.com:5000'
    def imagePrefix = 'productcopy'

    // kubernetes config
    def deployServiceHostname
    def k8sCluster

    node(javaAgent) {

        try {
            stage('Collect info') {
                checkout scm

                projectName = args.projectName
                branch = env.BRANCH_NAME
                commit = git.getCommitId()
                repo = git.getOriginUrl()
                deployTag = args.destinationEnvironment + commit[-6..-1]

                k8sCluster = args.destinationEnvironment
                if(args.k8sCluster != null){
                    k8sCluster = args.k8sCluster
                }

                stash 'workspace'

                pcSlack.notify message: "Promoting ${projectName} - ${args.envToPromote} to ${args.destinationEnvironment} environment"
            }


            // We need to prepare a temporal tag for deployment to make sure that the apply changes the image and foreces the image donwload and refresh
            stage('Prepare image'){
                node(dockerAgent) {
                    dock.retagImageAndPush repo: dockerRepo,
                            originalImage: "${imagePrefix}/${projectName}:${args.envToPromote}",
                            newImage: "${imagePrefix}/${projectName}:${deployTag}",
                            credentials: dockerCredentials
                }
            }

            stage('Deploy-k8s') {
                node(kubectlAgent) {
                    unstash 'workspace'
                    deployServiceHostname = pcK8s.deployToOnPremK8sTestCluster deploymentName: "${args.destinationEnvironment}-${projectName}",
                            deployImage: "${dockerRepo}/${imagePrefix}/${projectName}:${deployTag}",
                            kubernetesExecutionEnv: "${args.destinationEnvironment}",
                            deployHostPrefixForIngress: "${args.destinationEnvironment}.${projectName}",
                            k8sCluster: k8sCluster
                }
            }

            stage("Test"){
                echo "Placeholder for automation test execution -> label = regression"
            }

            //If the test has been successful we will re-tag the image as :staging
            stage("Promote image") {
                node(dockerAgent) {
                    dock.retagImageAndPush repo: dockerRepo,
                            originalImage: "${imagePrefix}/${projectName}:${args.envToPromote}",
                            newImage: "${imagePrefix}/${projectName}:${args.destinationEnvironment}",
                            credentials: dockerCredentials
                }
            }

            pcSlack.notify message: "Promotion to ${args.destinationEnvironment} success! - Service available in http://${deployServiceHostname}/swagger-ui.html"

        } catch (def e) {
            stage('Roll-back') {
                node(kubectlAgent) {
                    unstash 'workspace'
                    pcK8s.deployToOnPremK8sTestCluster deploymentName: "${args.destinationEnvironment}-${projectName}",
                            deployImage: "${dockerRepo}/${imagePrefix}/${projectName}:${args.destinationEnvironment}",
                            kubernetesExecutionEnv: "${args.destinationEnvironment}",
                            deployHostPrefixForIngress: "${args.destinationEnvironment}.${projectName}",
                            k8sCluster: k8sCluster
                }
            }

            pcSlack.notify message: "Promotion to ${args.destinationEnvironment} failed! - Rolled back to original version", level: 'error'
            currentBuild.result = 'FAILURE'
        }
    }
}

return this