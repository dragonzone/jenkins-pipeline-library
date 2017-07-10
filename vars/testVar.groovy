
import zone.dragon.jenkins.pipeline.MavenDSL

def call(Closure body) {
    echo this.class.name

    def config = new MavenDSL()

    body.delegate = config
    body.resolveStrategy = Closure.DELEGATE_FIRST

    body()

    echo config.someValue

    def preBuildClosure = config.preBuildClosure
    preBuildClosure.delegate = this
    preBuildClosure.resolveStrategy = Closure.DELEGATE_FIRST
    preBuildClosure()


}
