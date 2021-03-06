package zone.dragon.jenkins.pipeline

class MavenDSL implements Serializable {
    private String someValue
    private Closure preBuildClosure
    
    def getSomeValue() {
        return someValue
    }
    
    def setSomeValue(value) {
        this.someValue = value
    }
    
    def preBuild(Closure value) {
        this.preBuildClosure = value
    }
    
    def getPreBuildClosure() {
        return preBuildClosure
    }

}
