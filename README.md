One-Off Cloud Run
-----------------

Easily run one-off / admin tasks for Cloud Run services.

# Usage

Run one-off / admin processes for Cloud Run services.




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

## TODO
- MockTextTerminal for CLI Test
- Cloud SQL
- VPC
