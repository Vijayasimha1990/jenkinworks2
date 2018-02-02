import java.text.SimpleDateFormat;
import java.text.DateFormat;

/**
 * This method will create the needed directoy in the destination environment if it doesn't exist.
 * If it exists it will leave things as they are to make sure that we use the data already there.
 *
 * @param {String} args.environment Environment that has to be prepared (test, production)
 * @param {String} args.type Type of environment to prepare (master, slave)
 * @param {String} args.deployToMachine Machine where the environment will be prepared (dev1, dev2, dev3, prod1, prod2, prod3)
 *
 */
void prepareCouchbaseEnvironment(Map args) {
    util.validateArgs args, ['environment', 'type', 'deployToMachine']

    def hostInfo = getHostInfo  machineName:args.deployToMachine
    def directoryToCreate = getDirectoryName environment:args.environment, type:args.type

    sshagent([hostInfo.deployCredentials]) {
        sh """
            ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no ${hostInfo.vmUser}@${hostInfo.vmServer} "mkdir -p ${directoryToCreate}"
           """
    }
}


/**
 * This method will take care of deploying the couchabase instance in the given machine
 *
 * @param {String} args.environment Environment that has to be prepared (test, production)
 * @param {String} args.type Type of environment to prepare (master, slave)
 * @param {String} args.deployToMachine Machine where the environment will be prepared (dev1, dev2, dev3, prod1, prod2, prod3)
 * @param {String?} args.imageToDeploy Tag of the couchbase image to deploy, if not provided 'latest' will be used
 * @param {String?} args.masterHostMachine Machine where is deployed the couchbase master instance (optional - only fo slave deployment)
 *
 */
void deployCouchbaseInstance(Map args){
    def dockerCredentials = 'adidas-docker-registry'
    def dockerRepo = 'tools.adidas-group.com:5000'

    def imageTag = 'latest'
    if(args.imageToDeploy!=null){
        imageTag = args.imageToDeploy
    }

    def dockerImage = "productcopy/product-copy-couchbase:${imageTag}"

    def hostInfo = getHostInfo  machineName:args.deployToMachine
    def instanceInfo
    if (args.type == 'master'){
        instanceInfo = getMasterInstanceInfo environment:args.environment, type: args.type
    }

    if (args.type == 'slave'){
        def masterHostInfo = getHostInfo machineName:args.masterHostMachine
        instanceInfo = getSlaveInstanceInfo environment:args.environment, type: args.type, masterHost: masterHostInfo.vmServer
    }

    dock.runContainerOnRemoteHost dockerCredentials: dockerCredentials,
                                    deployCredentials: hostInfo.deployCredentials,
                                    vmServer: hostInfo.vmServer,
                                    vmUser:hostInfo.vmUser,
                                    containerName: "couchbase-${args.environment}-${args.type}",
                                    portConfig: instanceInfo.portConfig,
                                    envVariables: instanceInfo.envVariables,
                                    repo: dockerRepo,
                                    image: dockerImage
}

/**
 * Will create the needed buckets for the provided environment and deployment machine
 *
 * @param {String} args.bucketsName Sufix that we want to add to the buckets name (dev, sit, staging, production)
 * @param {String} args.environment Environment that has to be prepared (test, production)
 * @param {String} args.deployToMachine Couchbase master host where the buckets will be created (dev1, dev2, dev3, prod1, prod2, prod3)
 */
void createBuckets(Map args) {
    def hostInfo = getHostInfo machineName: args.deployToMachine
    def masterInstanceInfo = getMasterInstanceInfo environment: args.environment, type: 'master'
    def inputServiceBucket = "copy-order-${args.bucketsName}"
    def outputServiceBucket = "product-copy-${args.bucketsName}"
    def metadataServiceBucket = "product-metadata-${args.bucketsName}"
    def adminBucket = "admin-${args.bucketsName}"

    def ramCuota = 500
    if (args.environment == 'test' ) {
        ramCuota = 100
    }

    def replicas = 1

    def bucketNames = [inputServiceBucket, outputServiceBucket, metadataServiceBucket]

    for (bucket in bucketNames) {
        def createBucketCurl = "curl -X POST -u Administrator:b2c1509 -d name=${bucket} -d ramQuotaMB=${ramCuota} -d authType=sasl -d replicaNumber=${replicas} http://${hostInfo.vmServer}:${masterInstanceInfo.masterPort}/pools/default/buckets"
        sshagent([hostInfo.deployCredentials]) {
            sh """
            ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no ${hostInfo.vmUser}@${hostInfo.vmServer} "${createBucketCurl}"
           """
        }
        //we need to add a sleep to make sure buckets are ready before creating the next or later the indexes
        sleep 60
    }


    for (bucket in bucketNames) {
        def createIndexCurl = "curl -v http://${hostInfo.vmServer}:${masterInstanceInfo.indexPort}/query/service -d 'statement=CREATE PRIMARY INDEX \\`#primary\\` ON \\`${bucket}\\`'"
        sshagent([hostInfo.deployCredentials]) {
            sh """
            ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no ${hostInfo.vmUser}@${hostInfo.vmServer} "${createIndexCurl}"
           """
        }
    }

}

/**
 * Will create the needed buckets for the provided environment and deployment machine
 *
 * @param {String} args.environment Environment that has to be prepared (test, production)
 * @param {String} args.deployToMachine Machine where the environment will be prepared (dev1, dev2, dev3, prod1, prod2, prod3)
 */
void setAutoFailover(Map args){
    def hostInfo = getHostInfo  machineName:args.deployToMachine
    def masterInstanceInfo = getMasterInstanceInfo environment: args.environment, type: 'master'
    def setAutoFailoverCurl = "curl -X POST -u Administrator:b2c1509 http://${hostInfo.vmServer}:${masterInstanceInfo.masterPort}/settings/autoFailover -d 'enabled=true&timeout=120'"
    sshagent([hostInfo.deployCredentials]) {
        sh """
            ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no ${hostInfo.vmUser}@${hostInfo.vmServer} "${setAutoFailoverCurl}"
           """
    }
}

/**
 * This method will take care of deploying the couchabase instance in the given machine
 *
 * @param {String} args.environment Environment that has to be restarted (test, production)
 * @param {String} args.type Type of environment to restart (master, slave)
 * @param {String} args.deployToMachine Machine where the environment will be restarted (dev1, dev2, dev3, prod1, prod2, prod3)
 */
def restartCouchbaseInstance(Map args){
    def containerName = "couchbase-${args.environment}-${args.type}"
    def hostInfo = getHostInfo  machineName:args.deployToMachine
    sshagent([hostInfo.deployCredentials]) {
        //Since new VMs have problems with the first request to requestart we are at least trying to re-try once
        try {
            sh """
                ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no ${hostInfo.vmUser}@${hostInfo.vmServer} sudo docker restart "${containerName}"
               """
        } catch(def e){
            sh """
                ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no ${hostInfo.vmUser}@${hostInfo.vmServer} sudo docker restart "${containerName}"
               """
        }
    }
}

/**
 * This method will return all then needed info for deployment in the passed machine
 *
 * @param {String} args.environment Environment that has to be prepared (test, production)
 * @param {String} args.type Type of environment to prepare (master, slave)
 *
 * @return Map with the needed data
 *      info.portConfig
 *      info.envVariables+
 *      info.masterPort
 *      info.indexPort
 */
def getMasterInstanceInfo(Map args){
    util.validateArgs args, ['environment', 'type']

    def instanceData
    def directoryName = getDirectoryName environment:args.environment, type:args.type

    switch(args.environment) {
        case 'test':
            instanceData = ['portConfig': '--net=host',
                            'envVariables': "-e TYPE=MASTER -v ${directoryName}:/opt/couchbase/var",
                            'masterPort': '8091',
                            'indexPort': '8093']
            break
        case 'production':
            instanceData = ['portConfig': '--net=host',
                            'envVariables': "-e TYPE=MASTER -v ${directoryName}:/opt/couchbase/var",
                            'masterPort': '8091',
                            'indexPort': '8093']
            break
        default:
            throw new Exception("${args.environment} is not a valid value!")
    }

    return instanceData
}


/**
 * This method will return all then needed info for deployment in the passed machine
 *
 * @param {String} args.environment Environment that has to be prepared (test, production)
 * @param {String} args.type Type of environment to prepare (master, slave)
 * @param {String} args.masterHost enpoint of the
 *
 * @return Map with the needed data
 *      info.portConfig
 *      info.envVariables+
 */
def getSlaveInstanceInfo(Map args){
    util.validateArgs args, ['environment', 'type']

    def instanceData
    def directoryName = getDirectoryName environment:args.environment, type:args.type

    switch(args.environment) {
        case 'test':
            instanceData = ['portConfig': '--net=host',
                            'envVariables': "-e TYPE=WORKER -e COUCHBASE_MASTER=${args.masterHost} -v ${directoryName}:/opt/couchbase/var"]
            break
        case 'production':
            instanceData = ['portConfig': '--net=host',
                            'envVariables': "-e TYPE=WORKER -e COUCHBASE_MASTER=${args.masterHost} -v ${directoryName}:/opt/couchbase/var"]
            break
        default:
            throw new Exception("${args.environment} is not a valid value!")
    }

    return instanceData
}

/**
 * This method will return all then needed info for deployment in the passed machine
 *
 * @param {String} args.machineName Machine that has to be prepared (dev1, dev2, dev3, prod1, prod2, prod3)
 *
 * @return Map with the needed data
 *      info.vmServer
 *      info.deployCredentials
 *      info.vmUser
 */
def getHostInfo(Map args){
    util.validateArgs args, ['machineName']

    def vmUser = 'hlp_alzorign1@emea.adsint.biz'
    def hostData
    switch(args.machineName) {
        case 'dev1':
            hostData = ['vmServer': 'deheremap7682.linux.adsint.biz',
                       'deployCredentials': 'docker-machine-pk-7682',
                       'vmUser': vmUser]
            break
        case 'dev2':
            hostData = ['vmServer': 'deheremap7686.linux.adsint.biz',
                       'deployCredentials': 'docker-machine-pk-7686',
                       'vmUser': vmUser]
            break
        case 'dev3':
            hostData = ['vmServer': 'deheremap7738.linux.adsint.biz',
                        'deployCredentials': 'docker-machine-pk-7738',
                        'vmUser': vmUser]
            break
        case 'prod1':
            hostData = ['vmServer': 'deheremap7683.linux.adsint.biz',
                       'deployCredentials': 'docker-machine-pk-7683',
                       'vmUser': vmUser]
            break
        case 'prod2':
            hostData = ['vmServer': 'deheremap7684.linux.adsint.biz',
                       'deployCredentials': 'docker-machine-pk-7684',
                       'vmUser': vmUser]
            break
        case 'prod3':
            hostData = ['vmServer': 'deheremap7715.linux.adsint.biz',
                        'deployCredentials': 'docker-machine-pk-7715',
                        'vmUser': vmUser]
            break
        default:
            throw new Exception("${args.machineName} is not a valid value!")
    }

    return hostData
}

/**
 * Return the name of the directory to be used for the deployments
 *
 * @param {String} args.environment Environment that has to be prepared (test, production)
 * @param {String} args.type Type of environment to prepare (master, slave)
 */
def getDirectoryName(Map args){
    util.validateArgs args, ['environment', 'type']
    def directoryName = "/opt/dockerfiles/${args.environment}-couchbase/${args.type}"
    return directoryName
}

/**
 * Will do backup the cocuchbase data for the provided environment and deployment machine
 *
 * @param {String} args.bucketsName Suffix that we want to add to the buckets name (dev, sit, staging, production)
 * @param {String} args.environment Environment that has to be prepared (test, production)
 */
void backup(Map args) {
    def hostInfo = getHostInfo machineName: args.deployToMachine
    def masterInstanceInfo = getMasterInstanceInfo environment: args.environment, type: 'master'
	def NFSPATH= getBackupPath environment:args.environment, type:args.type
	def username = args.username;
	def password= args.password;
	DateFormat df = new SimpleDateFormat("yyyy-MM-dd") ;
    def getDate=df.format(new Date());
    def backupcmd = "cbbackup http://localhost:8091 ${NFSPATH} -u ${username} -p ${password} -m full --single-node"
	backupcmd.execute()
	
}
/**
 * Return the backup file path to be used for Backup/Restore
 *
 * @param {String} args.environment Environment that has to be prepared (test, production)
 * @param {String} args.type Type of environment to prepare (master, slave)
 */
def getBackupPath(Map args){
    util.validateArgs args, ['environment', 'type']
	DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
    def getDate=df.format(new Date());
    def NFSPATH = "D:\couchbasebackup\couchbase_backup_${getDate}"
    return NFSPATH
}

/**
 * Will do restore the couchbase data for the provided environment and deployment machine
 *
 * @param {String} args.bucketsName Sufix that we want to add to the buckets name (dev, sit, staging, production)
 * @param {String} args.environment Environment that has to be prepared (test, production)
 * @param {String} args.deployToMachine Couchbase master host where the buckets will be created (dev1, dev2, dev3, prod1, prod2, prod3)
 */
void restore(Map args) {
    def hostInfo = getHostInfo machineName: args.deployToMachine
    def masterInstanceInfo = getMasterInstanceInfo environment: args.environment, type: 'master'
	def username = args.username;
	def password= args.password;
    def inputServiceBucket = "copy-order-${args.bucketsName}"
    def outputServiceBucket = "product-copy-${args.bucketsName}"
    def metadataServiceBucket = "product-metadata-${args.bucketsName}"
    def adminBucket = "admin-${args.bucketsName}"
    def NFSPATH= getBackupPath environment:args.environment, type:args.type
    def bucketNames = [inputServiceBucket, outputServiceBucket, metadataServiceBucket]
	for (bucket in bucketNames) {
    	def restorecmd = "cbrestore ${NFSPATH} http://localhost:8091 -u ${username} -p ${password} -b ${bucket}"
		restorecmd.execute()
    }
}