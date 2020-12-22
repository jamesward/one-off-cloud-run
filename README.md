One-Off Cloud Run
-----------------

Easily run one-off / admin tasks for Cloud Run services.

# Usage

Run one-off / admin processes for Cloud Run services.  Just click:  
[![One-Off Cloud Run](https://gstatic.com/cloudssh/images/open-btn.svg)](https://ssh.cloud.google.com/?cloudshell_image=gcr.io/jamesward/one-off-cloud-run&shellonly=true)


## Dev Info

### Test

1. `export PROJECT_ID=YOUR_PROJECT_ID`
1. If needed, login to `gcloud`
1. `gcloud services enable compute.googleapis.com`
1. Build test container:
    ```
    docker build -t gcr.io/$PROJECT_ID/one-off-cloud-run-test test-container
    docker push gcr.io/$PROJECT_ID/one-off-cloud-run-test
    ```
1. Deploy a test Cloud Run service:
    ```
    gcloud run deploy \
        --project=$PROJECT_ID \
        --region=us-central1 \
        --platform=managed \
        --allow-unauthenticated \
        --image=gcr.io/$PROJECT_ID/one-off-cloud-run-test \
        --set-env-vars=NAME=world \
        one-off-cloud-run-test
    ```
1. (optional) Create a service account:
    1. ```
       export SANAME=one-off-cloud-run
       ```
    1. ```
       gcloud iam service-accounts create $SANAME \
           --display-name="One-Off Cloud Run" \
           --project=$PROJECT_ID
       ```
    1. ```
       export SERVICE_ACCOUNT=$SANAME@$PROJECT_ID.iam.gserviceaccount.com
       ```
    1. ```
       gcloud projects add-iam-policy-binding $PROJECT_ID \
           --member=serviceAccount:$SERVICE_ACCOUNT \
           --role=roles/compute.instanceAdmin

       gcloud projects add-iam-policy-binding $PROJECT_ID \
           --member=serviceAccount:$SERVICE_ACCOUNT \
           --role=roles/run.viewer
       
       gcloud projects add-iam-policy-binding $PROJECT_ID \
           --member=serviceAccount:$SERVICE_ACCOUNT \
           --role=roles/iam.serviceAccountUser

       gcloud projects add-iam-policy-binding $PROJECT_ID \
           --member=serviceAccount:$SERVICE_ACCOUNT \
           --role=roles/logging.viewer

       # for logging read
       gcloud projects add-iam-policy-binding $PROJECT_ID \
           --member=serviceAccount:$SERVICE_ACCOUNT \
           --role=roles/serviceusage.serviceUsageConsumer
       ```

1. Run the tests: `./gradlew test`

1. Continuously run a specific test: `./gradlew -t test  --tests 'CloudRunSpec'`

1. Run the CLI via Gradle: `./gradlew run -q --console=plain`

1. Run an "installed" app:
    ```
    ./gradlew installDist
    build/install/one-off-cloud-run/bin/one-off-cloud-run
    ```

### Test Cloud Shell Image

1. Build the container:
   ```
   docker build -t one-off-cloud-run .
   ```

1. Set an env var pointing to the Service Account's JSON file:

    ```
    export KEY_FILE=PATH_TO_YOUR_SERVICE_ACCOUNT_KEY_FILE
    ```

1. Run via Docker:
    ```
    docker run -it -v /var/run/docker.sock:/var/run/docker.sock \
      -v $KEY_FILE:/root/user.json \
      -e GOOGLE_APPLICATION_CREDENTIALS=/root/user.json \
      -e TRUSTED_ENVIRONMENT=true \
      --entrypoint=/bin/sh one-off-cloud-run -c \
      "gcloud auth activate-service-account --key-file=/root/user.json -q && /bin/cloudshell_open"
    ```

## TODO
- MockTextTerminal for CLI Test
- Cloud SQL
- VPC
