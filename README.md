# Quarkus demo: Infinispan Client

This example showcases how to use the Infinispan client with Quarkus and Red Hat Data Grid

See blog post:

# Setup

To run this demo, you'll need an OpenShift 4.x cluster available, and have `cluster-admin` privileges to be able to install Operators.

## Install the Red Hat Data Grid Operator

Create a new namespace/project in OpenShift, then head over to _Operators > OperatorHub_ and install the _Red Hat Data Grid_ operator into the namespace. Accept the defaults.

## Create a Data Grid Cluster

In the same namespace, navigate to _Operators > Installed Operators_ - click on the newly installed Data Grid Operator, then under _Infinispan Cluster_ click _Create Instance_. You could fill out the form, but easier is to switch to _YAML View_ and use the following example YAML:

```yaml
apiVersion: infinispan.org/v1
kind: Infinispan
metadata:
  name: example-infinispan
spec:
  replicas: 3
  service:
    type: Cache
  autoscale:
    maxMemUsagePercent: 15
    maxReplicas: 10
    minMemUsagePercent: 10
    minReplicas: 3
  container:
    memory: 500Mi
  expose:
    type: LoadBalancer
```

Note the autoscale settings (which only works against the `default` cache inside the cluster). They're pretty low for this demo, production use would clearly have higher thresholds and memory limits.

Click _Create_. This will spin up a new cluster called `example-infinispan`.

## Get the password

Using the _oc_ command line, login with `oc login` and then switch to your demo project e.g. `oc project dgdemo`. Then, you'll need to extract the pre-generated passwors with:

```sh
$ oc get secret/example-infinispan-generated-secret -o template='{{index .data "identities.yaml"}}' | openssl base64 -d -A
```

You'll use the `developer` username and password later.

## Create secrets for the TLS endpoints

Out of the box, Data Grid requires the use of SSL/TLS. Your apps need to be able to validate and connect to the server, so you'll need a certificate available to the app. Here's one way to extract the Data Grid certs and put them in a secret your app pods can use:

```sh
$ oc get secrets/signing-key -n openshift-service-ca -o template='{{index .data "tls.crt"}}' | openssl base64 -d -A > /tmp/server.crt
$ keytool -importcert -keystore /tmp/server.jks -storepass password -file /tmp/server.crt -trustcacerts -noprompt
$ oc create secret generic clientcerts --from-file=clientcerts=/tmp/server.jks
```
This will create a secret called `clientcerts` whose contents come from the JKS cert store, which contains the Data Grid certs. Applications can then mount this secret and read the contents from the mounted file at runtime. Note the use of JKS file format. Ideally we'd use PKCS12 but for some reason I was unable to get it to work for Java apps. If you can do it with this demo, I'd love to see a pull request (and be able to skip the use of keytool altogether!)

## Edit Quarkus & Prometheus config

In this demo, there are two places where the name `example-infinispan` and the developer username and password are used. So you'll need to edit `src/main/resources/application.properties` and `prometheus.yml` (if you're going to use prometheus) and change the passwords to those discovered above for the `developer` user.

## Deploy the app

The sample Quarkus app uses the OpenShift and Infinispan extensions. Given the settings in `application.properties`, run `mvn clean package` and it will compile the app and deploy it to OpenShift. It must be deployed to the same namespace as Data Grid.

## Test the endpoint

Use the following _curl_ command to test that the app is working:

```sh
curl http://$(oc get route/infinispan-client-quickstart -o jsonpath='{.spec.host}')/infinispan
```
You should get `Hello World, Infinispan is up!`, the same string found in `src/main/java/org/acme/infinispan/client/InfinispanGreetingResource.java`.

## Test the Data Grid Web Console

This is a new feature of Data Grid, which exposes an external endpoint for the administrative console. Discover this URL using:

```sh
echo http://$(oc get service/example-infinispan-external -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'):11222
```

If you visit that URL in your browser you can access the admin console - You'll need to login again with `developer` and the same password used before.

## Fill the grid

To test the scaling capability, run the following _curl_ to hit the `/infinispan/fill` endpoint, which as you can see in `InfinispanGreetingResource.java` will just add a bunch of junk, 2MB of it every 2 seconds.

```sh
curl http://$(oc get route/infinispan-client-quickstart -o jsonpath='{.spec.host}')/infinispan/fill
```

After 20-30 seconds, you should see Data Grid start adding cluster nodes.

## Stop the fill

After a minute or so, you can stop (and clear) the grid with:

```sh
curl http://$(oc get route/infinispan-client-quickstart -o jsonpath='{.spec.host}')/infinispan/clear
```

Data Grid should then start to scale back down.

## Use Prometheus/Grafana

If you want to use these, the easiest way is to install their respective container images. From the _Administrator_ perspective in OpenShift, navigate to _Add > From Container Image_, and install both the `grafana/grafana:latest` and `prom/prometheus:latest`.

After deploying both, you'll need to ensure the DG password is set in the `prometheus.yml` file in this repo, and then mount it in the prometheus deployment pod with:

```sh
$ oc create configmap prom --from-file=prometheus.yml=prometheus.yml
$ oc set volume deployment/prometheus --add -t configmap --configmap-name=prom -m /etc/prometheus/prometheus.yml --sub-path=prometheus.yml
```

Then, visit the Grafana dashboard route URL, sign in with `admin/admin`, then add Prometheus as a data source using `http://prometheus:9090` as the URL for the data source.

Finally, import an example Data Grid Grafana dashboard in this repo using the `grafana.json` file.






