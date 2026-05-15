pipelineJob('python-docker-demo') {
    description('Baut ein Python-Image (mit requirements.txt) und führt darin ein Demo-Skript aus. Setzt Docker-outside-of-Docker via /var/run/docker.sock voraus.')
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
                        // Defensiv: auch beim allerersten Run (parameters noch nicht registriert) ist IMAGE_TAG gesetzt.
                        IMAGE_TAG = "${params.IMAGE_TAG ?: 'jenkins-python-demo:latest'}"
                        GREETING  = "${params.GREETING  ?: 'Hallo aus dem Python-Container'}"
                    }
                    stages {
                        stage('Verify Docker') {
                            steps {
                                sh 'docker version'
                                sh 'docker info | head -n 20'
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
                                sh 'ls -la && echo --- && cat Dockerfile && echo --- && cat requirements.txt && echo --- && cat hello.py'
                            }
                        }
                        stage('Build image') {
                            steps {
                                sh 'docker build -t "${IMAGE_TAG}" .'
                            }
                        }
                        stage('Run script') {
                            steps {
                                sh 'docker run --rm -e GREETING="${GREETING}" "${IMAGE_TAG}"'
                            }
                        }
                    }
                    post {
                        always {
                            sh 'docker image rm "${IMAGE_TAG}" || true'
                            cleanWs()
                        }
                    }
                }
            '''.stripIndent())
        }
    }
}
