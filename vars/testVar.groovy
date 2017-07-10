
def call(Closure body) {
    echo this.class.name
    
    def config = [:]
    body.delegate = config
    body.resolveStrategy = Closure.DELEGATE_FIRST
    
    body()


}
