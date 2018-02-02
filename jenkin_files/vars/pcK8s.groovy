/**
 * Method that will excute the requested deployment on the <OnPrem k8s Test cluster
 *
 * @param {String} args.deploymentName - name that will be used for the deployemnt, service, ingress,...
 * @param {String} args.deployImage - docker image to use for the current deployment
 * @param {String?} args.kubernetesExecutionEnv - execution environment, needed for the service to retrieve the configuration (optional)
 * @param {String?} args.deployHostPrefixForIngress - prefix that will be attached to the base cluster URL for this particular deployment (optional)
 * @param {String?} args.couchbaseMasterNode - couchbase master node service (optional)
 * @param {String?} args.deploymentYaml - route to deployment yaml file, if not provided it will default to 'deploy/k8sDeployment.yaml'
 * @param {String?} args.k8sCluster - (playground, develop, staging, production) - If no environmnet is provided 'dev' will be used as default (optional)
 *
 * @return {String} will return the access URL for the performed deployment
 */
def deployToOnPremK8sTestCluster(Map args){
    util.validateArgs args, ['deploymentName', 'deployImage']

    def imagePullSecret = 'tools.adidas-group.com'
    def k8sConfigFile = getK8sConfigFile k8sCluster:args.k8sCluster
    def clusterBaseUrl = getK8sClusterBaseUrl k8sCluster:args.k8sCluster

    def deployServiceHostname = "${args.deployHostPrefixForIngress}.${clusterBaseUrl}"

    deployToK8sCluster k8sConfigFile: k8sConfigFile,
                    deploymentName: args.deploymentName,
                    deployImage: args.deployImage,
                    imagePullSecret: imagePullSecret,
                    deployServiceHostname: deployServiceHostname,
                    couchbaseMasterNode: args.couchbaseMasterNode,
                    deploymentYaml: args.deploymentYaml,
                    kubernetesExecutionEnv: args.kubernetesExecutionEnv

    return deployServiceHostname;
}

/**
 * Method that will excute the requested deployment in the requested cluster
 *
 * @param {String} args.deploymentName - name that will be used for the deployemnt, service, ingress,...
 * @param {String} args.deployImage - docker image to use for the current deployment
 * @param {String?} args.kubernetesExecutionEnv - execution environment, needed for the service to retrieve the configuration (optional)
 * @param {String?} args.deployHostPrefixForIngress - prefix that will be attached to the base cluster URL for this particular deployment (optional)
 * @param {String?} args.couchbaseMasterNode - couchbase master node service (optional)
 * @param {String?} args.deploymentYaml - route to deployment yaml file, if not provided it will default to 'deploy/k8sDeployment.yaml'
 *
 * @return {String} will return the access URL for the performed deployment
 */
def deployToAWSk8sCluster(Map args){
    util.validateArgs args, ['deploymentName', 'deployImage']

    def k8sConfigFile = '9rb6k-GSDTest-kubeconfig'
    def imagePullSecret = 'adidas-gsdk8spull-pull-secret'
    def clusterBaseUrl = "9rb6k.g8s.eu-west-1.adidas.aws.gigantic.io"

    def deployServiceHostname = "${args.deployHostPrefixForIngress}.${clusterBaseUrl}"

    deployToK8sCluster k8sConfigFile: k8sConfigFile,
                    deploymentName: args.deploymentName,
                    deployImage: args.deployImage,
                    imagePullSecret: imagePullSecret,
                    deployServiceHostname: deployServiceHostname,
                    couchbaseMasterNode: args.couchbaseMasterNode,
                    deploymentYaml: args.deploymentYaml,
                    kubernetesExecutionEnv: args.kubernetesExecutionEnv

    return deployServiceHostname;
}

/**
 * Method that will actually execute the deployment in the requested cluster.
 *
 * It sets-up all the default values for the CoreDAM project but still need some arguments to finalize the configuration needed
 * for deployment.
 *
 * Initially this method should be used only from the other methods in this library that provide more default values for the CoreDAM
 * project but if needed can be used directly if all parameters below are provided.
 *
 * @param {String} args.k8sConfigFile - kubeconfig file to use for the particular cluster where the service is deployed
 * @param {String} args.deploymentName - name that will be used for the deployemnt, service, ingress,...
 * @param {String} args.deployImage - docker image to use for the current deployment
 * @param {String} args.imagePullSecret - secret that must be used to download the image to deploy
 * @param {String} args.deployServiceHostname - url that will be made public as the entry point to the service
 * @param {String?} args.kubernetesExecutionEnv - execution environment, needed for the service to retrieve the configuration (optional)
 * @param {String?} args.couchbaseMasterNode - couchbase master node service (optional)
 * @param {String?} args.deploymentYaml - route to deployment yaml file, if not provided it will default to 'deploy/k8sDeployment.yaml'
 *
 */
void deployToK8sCluster(Map args) {
    util.validateArgs args, ['k8sConfigFile', 'deploymentName', 'deployImage', 'imagePullSecret', 'deployServiceHostname']

    def k8sDeploymentYaml = "deploy/k8sDeployment.yaml"
    def k8sNamespace = "product-copy"
    def configServer = 'http://product-copy-configuration-service:8888'

    def configRepo = 'https://tools.adidas-group.com/bitbucket/scm/prdc/product-copy-configuration-service.git'

    if(args.deploymentYaml != null){
        k8sDeploymentYaml = args.deploymentYaml
    }

    checkoutKubernetesConfiguration()
    def kubeconfigFile = "kubernetesConfiguration/config/${args.k8sConfigFile}"

    def substitutionVariables = [
            "DEPLOY_NAME=${args.deploymentName}",
            "DEPLOY_IMAGE=${args.deployImage}",
            "IMAGE_PULL_SECRET=${args.imagePullSecret}",
            "DEPLOY_SERVICE_HOSTNAME=${args.deployServiceHostname}",
            "K8S_NAMESPACE=${k8sNamespace}",
            "EXECUTION_ENVIRONMENT=${args.kubernetesExecutionEnv}",
            "COUCHBASE_MASTER_NODE=${args.couchbaseMasterNode}",
            "CONFIG_SERVER_URI=${configServer}",
            "PRODUCT_COPY_CONFIG_REPO=${configRepo}"
    ]

    kubectl.updateOrCreateK8sDeployment substitutionVariables: substitutionVariables,
                                        k8sDeploymentYaml: k8sDeploymentYaml,
                                        pathToKubeconfigFile: kubeconfigFile,
                                        namespace: k8sNamespace
}

/**
 * Deletes the given deployment from the OnPrem k8s Test cluster
 *
 * @param {String} args.deploymentName - name that will be used for the deployemnt, service, ingress,...
 * @param {String?} args.k8sCluster - (playground, develop, staging, production) - If no environmnet is provided 'dev' will be used as default (optional)
 */
void undeployFromOnPremK8sTestCluster(Map args){
    util.validateArgs args, ['deploymentName']

    def k8sConfigFile = getK8sConfigFile k8sCluster:args.k8sCluster

    undeployFromK8sCluster k8sConfigFile: k8sConfigFile,
                                    deploymentName: args.deploymentName
}

/**
 * Method that will excute the requested command on the OnPrem k8s Test cluster indicated by the k8sCluster parameter
 *
 * @param {String} args.command - command to execute in the k8s cluster
 * @param {String?} args.namespace - namespace where we must execute the command (optional)
 * @param {String?} args.k8sCluster - (playground, develop, staging, production) - If no environment is provided 'playground' will be used as default (optional)
 *
 * @return {String} will return the access URL for the performed deployment
 */
void executeOnPremK8sTestCluster(Map args){
    util.validateArgs args, ['command']

    def k8sConfigFile = getK8sConfigFile k8sCluster:args.k8sCluster

    checkoutKubernetesConfiguration()
    def kubeconfigFile = "kubernetesConfiguration/config/${k8sConfigFile}"

    def myKubectl = "kubectl --kubeconfig=${kubeconfigFile}"
    if (args.namespace != null) {
        myKubectl = "kubectl --kubeconfig=${kubeconfigFile} -n ${args.namespace}"
    }

    sh "${myKubectl} ${args.command}"
}

/**
 * Deletes the given deployment from the OnPrem k8s Test cluster
 *
 * @param {String} args.deploymentName - name that will be used for the deployemnt, service, ingress,...
 */
void undeployFromAWSk8sCluster(Map args){
    util.validateArgs args, ['deploymentName']

    def k8sConfigFile = '9rb6k-GSDTest-kubeconfig'

    undeployFromK8sCluster k8sConfigFile: k8sConfigFile,
                                    deploymentName: args.deploymentName
}

/**
 * Deletes the given deployment from the k8s that can be connected by the given kubeconfig file
 *
 * Initially this method should be used only from the other methods in this library that provide more default values for the CoreDAM
 * project but if needed can be used directly if all parameters below are provided.
 *
 * @param {String} args.k8sConfigFile - kubeconfig file to use for the particular cluster where the service is deployed
 * @param {String} args.deploymentName - name that will be used for the undeployemnt, service, ingress,...
 */
void undeployFromK8sCluster(Map args){
    util.validateArgs args, ['k8sConfigFile', 'deploymentName']

    checkoutKubernetesConfiguration()

    def kubeconfigFile = "kubernetesConfiguration/config/${args.k8sConfigFile}"
    def k8sNamespace = "product-copy"

    kubectl.completelyRemoveBranchDeployment deploymentName: args.deploymentName,
                                            pathToKubeconfigFile: kubeconfigFile,
                                            namespace: k8sNamespace
}

/**
 * Auxiliary method to do the checkout of the CoreDAM k8s configuration repo into the 'kubernetesConfiguration' folder
 */
void checkoutKubernetesConfiguration(){
    def kubernetesConfigRepo = "https://tools.adidas-group.com/bitbucket/scm/prdc/product-copy-k8s-configuration.git"
    def gitCredentials = 'git-access'

    dir('kubernetesConfiguration') {
        checkout([$class: 'GitSCM', branches: [[name: '*/develop']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: "${gitCredentials}", url: "${kubernetesConfigRepo}"]]])
    }
}


/**
 * Methd that will provide the kubeconfig file name to use corresponding to a particular cluster.
 *
 * @param {String?} args.k8sCluster - (develop, staging, production) - If no environment is provided 'develop' will be used as default (optional)
 *
 * @return {String} will return the config file name to use
 */
def getK8sConfigFile(Map args){
    def k8sConfigFile = 'kubeconfig_product-copy-dev'

    if(args.k8sCluster == 'staging') {
        k8sConfigFile = 'kubeconfig_product-copy-staging'
    }

    if(args.k8sCluster == 'production') {
        k8sConfigFile = 'kubeconfig_product-copy-prod'
    }

    if(args.k8sCluster == 'develop-old') {
        k8sConfigFile = 'kubeconfig-k8s-onprem-Develop'
    }

    if(args.k8sCluster == 'staging-old') {
        k8sConfigFile = 'kubeconfig-k8s-onprem-Staging'
    }

    return k8sConfigFile
}

/**
 * Method that will provide the kubeconfig cluster base url to use corresponding to a particular cluster and to be used to create the ingress url
 *
 * @param {String?} args.k8sCluster - (develop, staging, production) - If no environment is provided 'develop' will be used as default (optional)
 *
 * @return {String} will return the cluster base url file name to use
 */
def getK8sClusterBaseUrl(Map args) {
    def clusterBaseUrl = 'dev.he.k8s.emea.adsint.biz'

    if (args.k8sCluster == 'staging') {
        clusterBaseUrl = 'staging.he.k8s.emea.adsint.biz'
    }

    if (args.k8sCluster == 'production') {
        clusterBaseUrl = 'prod.he.k8s.emea.adsint.biz'
    }

    if (args.k8sCluster == 'develop-old') {
        clusterBaseUrl = 'dev.he.k8s.emea.adsint.biz'
    }

    if (args.k8sCluster == 'staging-old') {
        clusterBaseUrl = 'staging.he.k8s.emea.adsint.biz'
    }

    return clusterBaseUrl
}

return this