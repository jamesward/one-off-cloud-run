One-Off Cloud Run
-----------------

Easily run one-off / admin tasks for Cloud Run services.

# Usage

Run a one-off / admin processes for Cloud Run services in Cloud Shell.  Just click:  
[![One-Off Cloud Run](https://gstatic.com/cloudssh/images/open-btn.svg)](https://ssh.cloud.google.com/?cloudshell_image=gcr.io/jamesward/one-off-cloud-run-cloudshell&shellonly=true)

Run from docker (interactive):
```
export GOOGLE_APPLICATION_CREDENTIALS=YOUR_CREDS_FILE

docker run -it \
  -v$GOOGLE_APPLICATION_CREDENTIALS:/certs/svc_account.json \
  -eCLOUDSDK_AUTH_CREDENTIAL_FILE_OVERRIDE=/certs/svc_account.json \
  gcr.io/jamesward/one-off-cloud-run
```

Run from docker (non-interactive):
```
export GOOGLE_APPLICATION_CREDENTIALS=YOUR_CREDS_FILE

docker run --rm \
  -v$GOOGLE_APPLICATION_CREDENTIALS:/certs/svc_account.json \
  -eCLOUDSDK_AUTH_CREDENTIAL_FILE_OVERRIDE=/certs/svc_account.json \
  -ePROJECT_ID=YOUR_PROJECT_ID \
  -eSERVICE=YOUR_SERVICE \
  -eZONE=YOUR_ZONE \
  -eMACHINE_TYPE=YOUR_MACHINE_TYPE \
  -eENTRYPOINT=YOUR_OPTIONAL_ENTRYPOINT \
  -eARGS=YOUR_OPTIONAL_ARGS \
  gcr.io/jamesward/one-off-cloud-run
```

## Dev Info

### Test

1. `export PROJECT_ID=YOUR_PROJECT_ID`
1. `export REGION_ID=us-central1`

1. If needed, login to `gcloud`

1. Enable apis:
    ```
    gcloud services enable compute.googleapis.com --project=$PROJECT_ID
    gcloud services enable sqladmin.googleapis.com --project=$PROJECT_ID
    gcloud services enable run.googleapis.com --project=$PROJECT_ID
    ```

1. Build test container:
    ```
    docker build -t gcr.io/$PROJECT_ID/one-off-cloud-run-test test-container
    docker push gcr.io/$PROJECT_ID/one-off-cloud-run-test
    ```

1. Create a Cloud SQL instance:
    ```
    export DB_PASS=$(cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 64 | head -n 1)
    export DB_INSTANCE=one-off-cloud-run-test

    gcloud sql instances create $DB_INSTANCE --database-version=POSTGRES_9_6 --tier=db-f1-micro --region=$REGION_ID --project=$PROJECT_ID --root-password=$DB_PASS
    ```

1. Deploy a test Cloud Run service:
    ```
    gcloud run deploy \
        --project=$PROJECT_ID \
        --region=$REGION_ID \
        --platform=managed \
        --allow-unauthenticated \
        --image=gcr.io/$PROJECT_ID/one-off-cloud-run-test \
        --set-env-vars=NAME=world,DB_PASS=$DB_PASS,CLOUD_SQL_CONNECTION_NAME=$PROJECT_ID:$REGION_ID:$DB_INSTANCE \
        --add-cloudsql-instances=$DB_INSTANCE \
        one-off-cloud-run-test
    ```

1. (optional) Create a service account to run tests with:
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

       gcloud projects add-iam-policy-binding $PROJECT_ID \
           --member=serviceAccount:$SERVICE_ACCOUNT \
           --role=roles/logging.logWriter

       # for logging read
       gcloud projects add-iam-policy-binding $PROJECT_ID \
           --member=serviceAccount:$SERVICE_ACCOUNT \
           --role=roles/serviceusage.serviceUsageConsumer

       gcloud projects add-iam-policy-binding $PROJECT_ID \
           --member=serviceAccount:$SERVICE_ACCOUNT \
           --role=roles/cloudsql.client
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
- Quiet fluentd logs
- MockTextTerminal for CLI Test
- VPC
- CI/CD Tests
