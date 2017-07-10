
def call(Closure body) {
    echo this.class.name
    
    def config = [:]
    
    config.preBuild = { config.preBuildClosure = it }
    
    body.delegate = config
    body.resolveStrategy = Closure.DELEGATE_FIRST
    
    body()
    
    echo config.someValue
    
    def preBuildClosure = config.preBuildClosure
    preBuildClosure()


}
