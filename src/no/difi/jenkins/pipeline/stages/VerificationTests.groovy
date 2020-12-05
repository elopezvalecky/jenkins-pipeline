package no.difi.jenkins.pipeline.stages

import no.difi.jenkins.pipeline.Docker
import no.difi.jenkins.pipeline.Git
import no.difi.jenkins.pipeline.Jira
import no.difi.jenkins.pipeline.Maven
import no.difi.jenkins.pipeline.VerificationTestResult

Jira jira
Git git
Docker dockerClient
Maven maven


void script(def params) {
    git.checkoutVerificationBranch()
    if (maven.verificationTestsSupported(params.verificationEnvironment)) {
        VerificationTestResult result = maven.runVerificationTests params.verificationEnvironment, env.stackName
        jira.addComment(
                "Verifikasjonstester utført: [Rapport|${result.reportUrl()}] og [byggstatus|${env.BUILD_URL}]",
        )
        if (!result.success())
            error 'Verification tests failed'
    }
    if (dockerClient.apiTestsSupported(params.verificationEnvironment)) {
        String url= dockerClient.runAPIVerificationTests params.verificationEnvironment, env.stackName
        httpRequest outputFile: 'apitest/results.xml', responseHandle: 'NONE', url: "http://${url}/results.xml"
        junit allowEmptyResults: true, healthScaleFactor: 0.0, testResults: 'apitest/results.xml'
        httpRequest outputFile: 'apitest/results.html', responseHandle: 'NONE', url: "http://${url}/results.html"
        publishHTML([allowMissing: true, alwaysLinkToLastBuild: false, keepAll: false, reportDir: 'apitest', reportFiles: 'results.html', reportName: 'Api Tests', reportTitles: ''])
    }

    if (dockerClient.codeceptTestsSupported(params.verificationEnvironment)) {
        String url= dockerClient.runCodeceptVerificationTests params.verificationEnvironment, env.stackName, env.version

        httpRequest outputFile: 'codecepttest/results.xml', responseHandle: 'NONE', url: "http://${url}/output/results.xml"
        junit allowEmptyResults: true, healthScaleFactor: 0.0, testResults: 'codecepttest/results.xml'

        httpRequest outputFile: 'codecepttest/results.html', responseHandle: 'NONE', url: "http://${url}/output/results.html"
        sh """#!/usr/bin/env bash        
        for screenshotfile in `grep -oP '[^;]([a-zA-Z0-9._-]*\\\\.png)' codecepttest/results.html`; do
        httpRequest outputFile: 'codecepttest/\${screenshotfile}', responseHandle: 'NONE', url: "http://${url}/output/\${screenshotfile}"
        done
        """
        publishHTML([allowMissing: true, alwaysLinkToLastBuild: false, keepAll: false, reportDir: 'codecepttest', reportFiles: 'results.html', reportName: 'Codecept Tests', reportTitles: '', includes: '*/**'])

    }

}

void failureScript(def params) {
    cleanup(params)
    jira.addFailureComment()
}

void abortedScript(def params) {
    cleanup(params)
    jira.addAbortedComment()
}

private void cleanup(def params) {
    git.deleteVerificationBranch()
    dockerClient.deletePublished params.verificationEnvironment, env.version
    if (maven.isMavenProject())
        maven.deletePublished params.verificationEnvironment, env.version
    jira.resumeWork()
}


