# Spring Cloud Deployer Kubernetes
A [Spring Cloud Deployer](https://github.com/spring-cloud/spring-cloud-deployer) implementation for deploying long-lived streaming applications and short-lived tasks to Kubernetes.

## Kubernetes Compatibilit

| Deployer \ Kubernetes | 1.18 | 1.19 | 1.20 | 1.21 | 1.22 | 1.23 |
|-----------------------|------|------|------|------|------|------|
| **2.6.x**             | `✓`  | `✕`  | `✕`  | `✕`  | `✕`  | `✕`  |
| **2.7.x**             | `✓`  | `✓`  | `✓`  | `✓`  | `?`  | `?`  |
| **MAIN**              | `✕`  | `✓`  | `✓`  | `✓`  | `✓`  | `✓`  |

- `✓` Fully supported vers
- `?` Due to breaking chans might not work _(e.g., ABAC vs RBAC)_. Also, we haven't thoroughly tested against this version.
- `✕` Unsupported version.

## Building

Build the project without running tests using:

```
./mvnw clean install -DskipTests
```

## Integration tests

The integration tests require a running Kubernetes cluster. A couple of options are listed below.

### Minkube
[Minikube](https://github.com/kubernetes/minikube) is a tool that makes it easy to run Kubernetes locally. It runs a single-node Kubernetes cluster inside a VM on your laptop for users looking to try out Kubernetes or develop with it day-to-day. 

Follow the [getting started](https://minikube.sigs.k8s.io/docs/start/) guide to install Minikube.

1. Start Minikube
   ```shell
   minkube start
   ```
2. Run the tests
   ```shell
   ./mvnw clean test
   ```
3. Stop Minikube
   ```shell
   minkube stop
   ```


### Google Container Engine
While Minikube is very easy to run and test against, it is preferred to test against a GKE cluster. Minikube is not as useful since we test some parts of the external IP features that a LoadBalancer service provides.

Create a test cluster and target it using something like (use your own project name, substitute --zone if needed):

```
gcloud container --project {your-project-name} clusters create "spring-test" --zone "us-central1-b" --machine-type "n1-highcpu-2" --scopes "https://www.googleapis.com/auth/compute","https://www.googleapis.com/auth/devstorage.read_only","https://www.googleapis.com/auth/logging.write" --network "default" --enable-cloud-logging --enable-cloud-monitoring
gcloud config set container/cluster spring-test
gcloud config set compute/zone us-central1-b
gcloud container clusters get-credentials spring-test
kubectl version
```
> :information_source: the last command causes the access token to be generated and saved to the kubeconfig file - it can be any valid kubectl command

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


