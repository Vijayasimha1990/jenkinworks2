/**
 * Send a message to adidas-coredam slack
 *
 * @param {String} args.message - the message to log
 * @param {?String} args.level - level to use for logging
 */
void notify(Map args) {
    def credentials = 'slack-token'
    def team = 'adidas-productcopy'
    def defaultChannel = 'jenkins'
    def channel = args.channel != null ? args.channel : defaultChannel
    try {
        slack.notify message: args.message,
                credentials: credentials,
                team: team,
                channel: channel,
                level: args.level
    }catch(def e){
        //added capture of exception since slack API lately is presenting some problems and is producing the failure of the
        //pipeline just because notification could not be launched. Ignored to avoid it failing the build. 
    }
}