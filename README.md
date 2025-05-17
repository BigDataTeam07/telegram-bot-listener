# telegram-bot-listener

This project implements a Telegram bot that listens to messages in group chats and forwards them to a Kafka topic. It also provides HTTP endpoints to control the bot's status (start/stop).

## Features

*   Listens to Telegram group messages.
*   Filters out messages from other bots and direct messages to the bot.
*   Sends processed messages to a configurable Kafka topic.
*   Provides HTTP endpoints for starting and stopping the bot.
*   Uses AWS Secrets Manager for sensitive configuration.
*   Includes a GitHub Actions workflow for CI/CD (build, push to ECR, deploy to Kubernetes).

## Prerequisites

*   Java 22
*   Gradle
*   Docker
*   Kubernetes cluster (e.g., EKS)
*   AWS ECR repository
*   AWS Secrets Manager for storing credentials
*   Kafka cluster

## Configuration

The application is configured using environment variables. These are typically injected via Kubernetes secrets.

| Environment Variable      | Description                                                                 | Default Value        | Required |
| :------------------------ | :-------------------------------------------------------------------------- | :------------------- | :------- |
| `TELEGRAM_BOT_TOKEN`      | The token for your Telegram bot.                                            | -                    | Yes      |
| `TELEGRAM_BOT_USERNAME`   | The username of your Telegram bot.                                          | -                    | Yes      |
| `KAFKA_BOOTSTRAP_SERVERS` | Comma-separated list of Kafka broker addresses.                             | `localhost:9092`     | No       |
| `KAFKA_TOPIC`             | The Kafka topic to which messages will be sent.                             | `social-media-topic` | No       |
| `KAFKA_SASL_USERNAME`     | The username for Kafka SASL authentication.                                 | -                    | Yes      |
| `KAFKA_SASL_PASSWORD`     | The password for Kafka SASL authentication.                                 | -                    | Yes      |

## Building the Project

1.  **Clone the repository:**
    ```bash
    git clone <repository-url>
    cd telegram-bot-listener
    ```
2.  **Build with Gradle:**
    ```bash
    ./gradlew build
    ```
    This will compile the code, run tests, and create a JAR file in `build/libs/`.

## Running the Application

### Docker

The project includes a `Dockerfile` to build a Docker image.

1.  **Build the Docker image:**
    ```bash
    docker build -t telegram-bot-listener:latest .
    ```
2.  **Run the Docker container:**
    Make sure to provide the necessary environment variables.
    ```bash
    docker run -e TELEGRAM_BOT_TOKEN="your_token" \
               -e TELEGRAM_BOT_USERNAME="your_bot_username" \
               -e KAFKA_SASL_USERNAME="your_kafka_username" \
               -e KAFKA_SASL_PASSWORD="your_kafka_password" \
               -e KAFKA_BOOTSTRAP_SERVERS="your_kafka_brokers" \
               -p 8080:8080 \
               telegram-bot-listener:latest
    ```

### Kubernetes

A Kubernetes deployment manifest (`k8s/telegram-bot-listener-deployment.yaml`) is provided.

1.  **Prerequisites:**
    *   Ensure your Kubernetes cluster is configured.
    *   Create Kubernetes secrets for `telegram-bot-credentials`, `kafka-sasl-secret`, and `kafka-bootstrap-servers` in the `service-ns` namespace with the required keys as defined in the deployment YAML.
2.  **Deploy to Kubernetes:**
    ```bash
    kubectl apply -f ./k8s/telegram-bot-listener-deployment.yaml
    ```
    The deployment uses the image `119002862962.dkr.ecr.ap-southeast-1.amazonaws.com/amazon-music-review/telegram-bot-listener:latest`. The CI/CD pipeline pushes to this ECR repository.

## HTTP Control Endpoints

The application exposes HTTP endpoints on port `8080` for controlling the bot:

*   **`GET /start`**: Starts the Telegram bot listener if it's not already running.
    *   Response: `"Bot started"` or `"Bot is already started"`
*   **`GET /shutdown`**: Stops the Telegram bot listener if it's running.
    *   Response: `"Bot stopped"` or `"Bot is already stopped"`

These endpoints are exposed as a `ClusterIP` service in Kubernetes, meaning they are only accessible from within the cluster.

## CI/CD Pipeline

The project includes a GitHub Actions workflow defined in `.github/workflows/build.yaml`.

*   **On Pull Request (to `main` branch):**
    *   Checks out code.
    *   Sets up JDK.
    *   Caches Gradle and Docker dependencies.
    *   Builds the Gradle project.
    *   Sets up Docker Buildx.
    *   Configures AWS credentials (using OIDC).
    *   Logs in to Amazon ECR.
    *   Builds and pushes the Docker image to ECR, tagged with the commit SHA and `latest`.
*   **On Push (to `main` branch):**
    *   All steps from the pull request workflow.
    *   Additionally, deploys the application to Kubernetes:
        *   Configures AWS credentials (using access keys).
        *   Installs `kubectl` and `aws-iam-authenticator`.
        *   Updates `kubeconfig` using a GitHub secret.
        *   Applies the Kubernetes deployment manifest.

### Required GitHub Secrets for CI/CD:

*   `AWS_OIDC_ROLE_ARN`: ARN of the IAM role for OIDC authentication with AWS (for the build job).
*   `AWS_ACCESS_KEY_ID`: AWS Access Key ID (for the deploy job).
*   `AWS_SECRET_ACCESS_KEY`: AWS Secret Access Key (for the deploy job).
*   `KUBE_CONFIG`: Base64 encoded content of your Kubernetes configuration file.

## Project Structure

*   `src/main/java/org/iss/bigdata/practice/`: Main application code.
    *   `TelegramBotListenerApp.java`: Main application entry point.
    *   `TelegramBotListener.java`: Implements the Telegram bot logic and Kafka producer interaction.
    *   `TelegramBotSessionManager.java`: Manages the lifecycle of the Telegram bot session.
    *   `HTTPEndpointListener.java`: Sets up HTTP endpoints for bot control.
    *   `Config.java`: Handles application configuration from environment variables.
    *   `ProjectKafkaProducer.java`: Wrapper for Kafka producer setup.
*   `Dockerfile`: Defines the Docker image for the application.
*   `build.gradle.kts`: Gradle build script.
*   `settings.gradle.kts`: Gradle settings script.
*   `k8s/telegram-bot-listener-deployment.yaml`: Kubernetes deployment and service manifest.
*   `.github/workflows/build.yaml`: GitHub Actions workflow for CI/CD.