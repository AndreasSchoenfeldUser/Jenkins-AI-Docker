pipelineJob('seed-demo') {
    description('Seeded by JCasC + Job-DSL — Demo eines weiteren Pipeline-Jobs')
    definition {
        cps {
            sandbox(true)
            script('''
                pipeline {
                    agent any
                    parameters {
                        string(name: 'NAME', defaultValue: 'World', description: 'Wen begrüßen?')
                    }
                    stages {
                        stage('Greet') {
                            steps {
                                echo "Hello, ${params.NAME}!"
                            }
                        }
                        stage('List workspace') {
                            steps {
                                sh 'ls -la'
                            }
                        }
                    }
                }
            '''.stripIndent())
        }
    }
}
