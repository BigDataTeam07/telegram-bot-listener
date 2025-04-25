pipeline {
    agent any

    parameters {
        string(name: 'IMAGE_TAG', defaultValue: 'latest', description: 'Docker image tag to deploy')
    }

    environment {
        AWS_REGION = 'ap-southeast-1'
        ECR_REPOSITORY = 'amazon-music-review/telegram-bot-listener'
        ECR_REGISTRY = '119002862962.dkr.ecr.ap-southeast-1.amazonaws.com'
        KUBECONFIG = '/var/jenkins_home/.kube/config'
        DEPLOYMENT_NAMESPACE = 'telegram-bot-listener'
    }

    stages {
        stage('Checkout') {
            steps {
                // Checkout the code repository containing the Kubernetes manifests
                checkout scm
            }
        }

        stage('Prepare Deployment Files') {
            steps {
                // Create a temporary deployment file with the updated image tag
                sh '''
                cat k8s/telegram-bot-listener-deployment.yaml | \
                sed "s|\\\${DOCKER_IMAGE_TAG}|${ECR_REGISTRY}/${ECR_REPOSITORY}:${params.IMAGE_TAG}|g" > k8s/deployment-temp.yaml
                cat k8s/deployment-temp.yaml
                '''
            }
        }

        stage('Deploy to EKS') {
            steps {
                // Use the AWS credentials configured in Jenkins
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding',
                                credentialsId: 'aws-credentials',
                                accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                                secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                    // Update kubeconfig and apply the deployment
                    sh '''
                    aws eks update-kubeconfig --region ${AWS_REGION} --name amazon-music-review-cluster
                    kubectl apply -f k8s/deployment-temp.yaml
                    '''
                }
            }
        }

        stage('Verify Deployment') {
            steps {
                // Wait for the deployment to complete and verify it's running
                sh '''
                kubectl rollout status deployment/telegram-bot-listener -n ${DEPLOYMENT_NAMESPACE} --timeout=300s
                kubectl get pods -n ${DEPLOYMENT_NAMESPACE} -l app=telegram-bot-listener
                '''
            }
        }
    }

    post {
        success {
            echo "Deployment successful"
        }
        failure {
            echo "Deployment failed"
        }
        always {
            // Clean up temporary files
            sh 'rm -f k8s/deployment-temp.yaml'
        }
    }
}