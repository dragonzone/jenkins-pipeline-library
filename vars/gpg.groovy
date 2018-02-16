


def call(String keyfileId, Closure callback) {
    withEnv(["GNUPGHOME=/tmp/gnupg"]) {
        sh "mkdir -p $GNUPGHOME && chmod 700 $GNUPGHOME"
        withCredentials([file(credentialsId: keyfileId, variable: 'GPG_SIGNING_KEY_FILE')]) {
            def keyIdLine = sh(returnStdOut: true, script: 'gpg --allow-secret-key-import --import $GPG_SIGNING_KEY_FILE | grep "secret key imported"')
        }    
    }
}
