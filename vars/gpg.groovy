


def call(String keyfileId, Closure callback) {
    withEnv(["GNUPGHOME=/tmp/gnupg"]) {
        sh "mkdir -p $GNUPGHOME && chmod 700 $GNUPGHOME"
        withCredentials([file(credentialsId: keyfileId, variable: 'GPG_SIGNING_KEY_FILE')]) {
            def keyIdShort = sh(returnStdOut: true, script: 'gpg --allow-secret-key-import --import $GPG_SIGNING_KEY_FILE 2>&1 | grep \'secret key imported\' | cut -d \' \' -f 3 | cut -d \':\' -f 1')
            def keyIdFull = sh(returnStdOut: true, script: "gpg --list-keys | grep '${keyIdShort}'")
            callback.delegate = this
            callback.resolveStrategy = Closure.DELEGATE_FIRST
            callback(keyIdFull)
        }    
    }
}
