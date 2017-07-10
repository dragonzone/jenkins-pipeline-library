
def call(Closure body) {
    echo this.class.name
    //echo owner.class.name
    echo delegate.class.name
    
    body()


}
