# Spring Cloud Deployer Kubernetes
A [Spring Cloud Deployer](https://github.com/spring-cloud/spring-cloud-deployer) implementation for deploying long-lived streaming applications and short-lived tasks to Kubernetes.

## Kubernetes Compatibility

| Deployer \ Kubernetes | 1.11 | 1.12 | 1.13 | 1.14 | 1.15 | 1.16 | 1.17 | 1.18 |
|-----------------------|------|------|------|------|------|------|------|------|
| **1.3.x**             | ✓    | ✓    | ✓    | ✕    | ✕    | ✕    | ✕    | ✕    |
| **2.0.x**             | ✓    | ✓    | ✓    | ✕    | ✕    | ✕    | ✕    | ✕    |
| **2.1.x**             | ✓    | ✓    | ✓    | ✕    | ✕    | ✕    | ✕    | ✕    |
| **2.2.x**             | ✕    | ✕    | ✓    | ✓    | ✓    | ✕    | ✕    | ✕    |
| **2.3.x**             | ✕    | ✕    | ✓    | ✓    | ✓    | ✓    | ✓    | ✓    |
| **2.4.x**             | ✕    | ✕    | ✓    | ✓    | ✓    | ✓    | ✓    | ✓    |
| **2.5.x**             | ✕    | ✕    | ✓    | ✓    | ✓    | ✓    | ✓    | ✓    |
| **2.6.x**             | ✕    | ✕    | ✕    | ✕    | ✕    | ✓    | ✓    | ✓    |
| **MAIN**              | ✕    | ✕    | ✕    | ✕    | ✕    | ✓    | ✓    | ✓    |

- `✓` Fully supported version.
- `?` Due to breaking changes between Kubernetes API versions, some features might not work _(e.g., ABAC vs RBAC)_. Also, we haven't thoroughly tested against this version.
- `✕` Unsupported version.

## Building

Build the project without running tests using:

```
./mvnw clean install -DskipTests
```

## Integration tests

All testing is curently done against a GKE cluster. Minikube is no longer useful since we test some parts of the external IP features that a LoadBalancer service provides.

### Google Container Engine

Create a test cluster and target it using something like (use your own project name, substitute --zone if needed):

```
gcloud container --project {your-project-name} clusters create "spring-test" --zone "us-central1-b" --machine-type "n1-highcpu-2" --scopes "https://www.googleapis.com/auth/compute","https://www.googleapis.com/auth/devstorage.read_only","https://www.googleapis.com/auth/logging.write" --network "default" --enable-cloud-logging --enable-cloud-monitoring
gcloud config set container/cluster spring-test
gcloud config set compute/zone us-central1-b
gcloud container clusters get-credentials spring-test
```

#### Running the tests

Once the test cluster has been created, you can run all integration tests.

As long as your `kubectl` config files are set to point to your cluster, you should be able to just run the tests. Verify your config using `kubectl config get-contexts` and check that your test cluster is the current context.

Now run the tests:

```
$ ./mvnw test
```

NOTE: if you get authentication errors, try setting basic auth credentials:

Navigate to your project and cluster on https://console.cloud.google.com/  and click on `show credentials`

```bash
$export KUBERNETES_AUTH_BASIC_PASSWORD=
$export KUBERNETES_AUTH_BASIC_USERNAME=
```


