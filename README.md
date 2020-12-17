One-Off Cloud Run
-----------------


# Test

1. If needed, login to `gcloud`
1. `gcloud services enable compute.googleapis.com`
1. `export PROJECT_ID=YOUR_PROJECT_ID`
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
       --role=roles/iam.serviceAccountUser
       ```

1. `./gradlew test`

# TODO
- Env Vars
- Cloud SQL
- VPC
