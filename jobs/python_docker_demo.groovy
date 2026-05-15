pipelineJob('python-docker-demo') {
    description('Baut ein Python-Image und führt es aus — komplett über die Docker-Pipeline-API (docker.build / image.inside). Setzt Docker-outside-of-Docker via /var/run/docker.sock voraus.')
    definition {
        cps {
            sandbox(true)
            script('''
                pipeline {
                    agent any
                    options {
                        timestamps()
                        timeout(time: 10, unit: 'MINUTES')
                    }
                    parameters {
                        string(name: 'IMAGE_TAG', defaultValue: 'jenkins-python-demo:latest', description: 'Tag des zu bauenden Python-Images')
                        string(name: 'GREETING', defaultValue: 'Hallo aus dem Python-Container', description: 'Text, den das Demo-Skript ausgibt')
                    }
                    environment {
                        IMAGE_TAG = "${params.IMAGE_TAG ?: 'jenkins-python-demo:latest'}"
                        GREETING  = "${params.GREETING  ?: 'Hallo aus dem Python-Container'}"
                    }
                    stages {
                        stage('Verify Docker') {
                            steps {
                                sh 'docker version'
                            }
                        }
                        stage('Prepare files') {
                            steps {
                                script {
                                    writeFile file: 'Dockerfile', text: [
                                        'FROM python:3.12-slim',
                                        'WORKDIR /app',
                                        'COPY requirements.txt ./',
                                        'RUN pip install --no-cache-dir -r requirements.txt',
                                        'COPY hello.py ./',
                                        'CMD [\\"python\\", \\"hello.py\\"]',
                                        ''
                                    ].join('\\n')

                                    writeFile file: 'requirements.txt', text: 'requests==2.32.3\\n'

                                    writeFile file: 'hello.py', text: [
                                        'import os',
                                        'import sys',
                                        'import platform',
                                        'import requests',
                                        '',
                                        'greeting = os.environ.get(\\"GREETING\\", \\"Hello from Python\\")',
                                        'print(f\\"{greeting}!\\")',
                                        'print(f\\"Python  : {sys.version.split()[0]}\\")',
                                        'print(f\\"Platform: {platform.platform()}\\")',
                                        'print(f\\"requests: {requests.__version__}\\")',
                                        ''
                                    ].join('\\n')
                                }
                                sh 'ls -la'
                            }
                        }
                        stage('Build image (Docker Pipeline API)') {
                            steps {
                                script {
                                    // Docker Pipeline API: docker.build(tag, context)
                                    docker.build(env.IMAGE_TAG, '.')
                                }
                            }
                        }
                        stage('Run container (Docker Pipeline API)') {
                            steps {
                                script {
                                    // Docker Pipeline API: image.inside { ... } führt Befehle im Container aus.
                                    // Workspace wird automatisch gemountet; das Image-CMD wird übersteuert.
                                    docker.image(env.IMAGE_TAG).inside("-e GREETING='${env.GREETING}'") {
                                        sh 'python /app/hello.py'
                                    }
                                }
                            }
                        }
                    }
                    post {
                        always {
                            script {
                                sh "docker image rm ${env.IMAGE_TAG} || true"
                            }
                            cleanWs()
                        }
                    }
                }
            '''.stripIndent())
        }
    }
}
