/*
 * Copyright 2018 Bryan Harclerode
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

def call(Closure config) {
    node('docker') {
        stage("Checkout SCM") {
            checkout scm
        }

        def hash = gitHash().take(6)
        def version = "${env.BRANCH_NAME}-${env.BUILD_NUMBER}-${hash}"
        def org = env.JOB_NAME.split('/')[0]
        def repo = env.JOB_NAME.split('/')[1]
        def imageName = "${org}/${repo}:${version}"

        def image

        stage("Build Docker Image") {
            docker.withRegistry('https://docker.dragon.zone:10080', 'jenkins-nexus') {
                image = docker.build(imageName)
            }
        }

        stage("Push Docker Image") {
            docker.withRegistry('https://docker.dragon.zone:10081', 'jenkins-nexus') {
                image.push()
                image.push(env.BRANCH_NAME)
            }
        }
    }
}
