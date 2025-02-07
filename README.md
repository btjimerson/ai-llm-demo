# Introduction

This application is designed to demonstrate the developer experience with Gloo AI Gateway. Use this README to walk through the installation.

# AI Gateway

<aside>
💡

Make sure you’re using a non-ARM cluster. AI gateway extension isn’t built for ARM.

</aside>


# Gloo Gateway

Set the following environment variables:

```bash
export GLOO_GATEWAY_LICENSE_KEY=<license-key>

export OPENAI_API_KEY=<openai-api-key>
```

Install the Kubernetes Gateway CRDs:

```bash
kubectl apply -f https://github.com/kubernetes-sigs/gateway-api/releases/download/v1.2.0/standard-install.yaml
```

Install Gloo Gateway with Helm:

```bash
helm install -n gloo-system gloo gloo-ee \
--repo=https://storage.googleapis.com/gloo-ee-helm \
--create-namespace \
--version 1.18.3 \
--set-string license_key=$GLOO_GATEWAY_LICENSE_KEY \
-f- <<EOF
gloo:
  discovery:
    enabled: false
  gatewayProxies:
    gatewayProxy:
      disabled: true
  kubeGateway:
    enabled: true
  gloo:
    disableLeaderElection: true
gloo-fed:
  enabled: false
  glooFedApiserver:
    enable: false
grafana:
  defaultInstallationEnabled: false
observability:
  enabled: false
prometheus:
  enabled: false
EOF
```

Wait for gloo to become available:

```bash
kubectl rollout status -n gloo-system deployments/gloo
```

# HTTP Gateways

Create 2 gateways with HTTP listeners:

```bash
kubectl apply -n gloo-system -f- <<EOF
kind: Gateway
apiVersion: gateway.networking.k8s.io/v1
metadata:
  name: http-9080
spec:
  gatewayClassName: gloo-gateway
  listeners:
  - protocol: HTTP
    port: 9080
    name: http
    allowedRoutes:
      namespaces:
        from: All
---
kind: Gateway
apiVersion: gateway.networking.k8s.io/v1
metadata:
  name: http-9090
spec:
  gatewayClassName: gloo-gateway
  listeners:
  - protocol: HTTP
    port: 9090
    name: http
    allowedRoutes:
      namespaces:
        from: All
EOF
```

Verify they were was created:

```bash
kubectl rollout status -n gloo-system deployments/gloo-proxy-http-9080
kubectl rollout status -n gloo-system deployments/gloo-proxy-http-9090
```

Get the endpoint for the gateway. Alternatively you can create a CNAME record that points to the ingress gateway and use that record for AI_RAG_DEMO_HOSTNAME:

```bash
export AI_LLM_DEMO_HOSTNAME_9080=$(kubectl get svc -n gloo-system gloo-proxy-http-9080 -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')
export AI_LLM_DEMO_HOSTNAME_9090=$(kubectl get svc -n gloo-system gloo-proxy-http-9090 -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')

echo "gateway-9080 = $AI_LLM_DEMO_HOSTNAME_9080"
echo "gateway-9090 = $AI_LLM_DEMO_HOSTNAME_9090"
```

# AI Gateway

## Installation

Set up gateway parameters to enable AI extensions, and create AI gateway with access logging

```bash
kubectl apply -f- <<EOF
apiVersion: gateway.gloo.solo.io/v1alpha1
kind: GatewayParameters
metadata:
  name: gloo-gateway-override
  namespace: gloo-system
spec:
  kube:
    aiExtension:
      enabled: true
    service:
      type: ClusterIP
---
kind: Gateway
apiVersion: gateway.networking.k8s.io/v1
metadata:
  name: ai-gateway
  namespace: gloo-system
  annotations:
    gateway.gloo.solo.io/gateway-parameters-name: gloo-gateway-override
spec:
  gatewayClassName: gloo-gateway
  listeners:
  - protocol: HTTP
    port: 8080
    name: http
    allowedRoutes:
      namespaces:
        from: All
---
apiVersion: gateway.solo.io/v1
kind: ListenerOption
metadata:
  name: ai-gateway-log-provider
  namespace: gloo-system
spec:
  options:
    accessLoggingService:
      accessLog:
      - fileSink:
          jsonFormat:
            httpMethod: '%REQ(:METHOD)%'
            path: '%REQ(X-ENVOY-ORIGINAL-PATH?:PATH)%'
            requestId: '%REQ(X-REQUEST-ID)%'
            responseCode: '%RESPONSE_CODE%'
            systemTime: '%START_TIME%'
            targetDuration: '%RESPONSE_DURATION%'
            upstreamName: '%UPSTREAM_CLUSTER%'
            downstreamIp: '%DOWNSTREAM_LOCAL_ADDRESS%'
          path: /dev/stdout
  targetRefs:
  - group: gateway.networking.k8s.io
    kind: Gateway
    name: ai-gateway
EOF
```

Verify that it was created:

```bash
kubectl rollout status -n gloo-system deployments/gloo-proxy-ai-gateway
```

## OpenAI Configuration

Create a secret with the OpenAI API Key:

```bash
kubectl create secret generic openai-secret -n gloo-system --from-literal="Authorization=Bearer $OPENAI_API_KEY"
```

Create an Upstream for OpenAI with the API key:

```bash
kubectl apply -f- <<EOF
apiVersion: gloo.solo.io/v1
kind: Upstream
metadata:
  labels:
    app: gloo
  name: openai
  namespace: gloo-system
spec:
  ai:
    openai:
      model: "gpt-4o"
      authToken:
        secretRef:
          name: openai-secret
          namespace: gloo-system
EOF

kubectl describe upstream -n gloo-system openai
```

Create an HTTP route that maps the /openai path to OpenAI:

```bash
kubectl apply -f- <<EOF
apiVersion: gateway.networking.k8s.io/v1
kind: HTTPRoute
metadata:
  name: openai
  namespace: gloo-system
spec:
  parentRefs:
    - name: ai-gateway
      namespace: gloo-system
  rules:
  - matches:
    - path:
        type: PathPrefix
        value: /openai
    backendRefs:
    - name: openai
      namespace: gloo-system
      group: gloo.solo.io
      kind: Upstream
EOF
```

# AI LLM Demo Apps

Install 2 versions of the AI demo app, 1 that uses embeddings and 1 that doesn’t:

```bash
kubectl apply -f- <<EOF
---
apiVersion: v1
kind: Namespace
metadata:
  name: ai-llm-demo
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: pgvector
  namespace: ai-llm-demo
  labels:
    app: pgvector
spec:
  replicas: 1
  selector:
    matchLabels:
      app: pgvector
  template:
    metadata:
      labels:
        app: pgvector
    spec:
      containers:
      - name: pgvector
        image: pgvector/pgvector:pg16
        ports:
        - containerPort: 5432
        env:
          - name: POSTGRES_DB
            value: ai-llm-demo
          - name: POSTGRES_USER
            value: postgres
          - name: POSTGRES_PASSWORD
            value: postgres
---
apiVersion: v1
kind: Service
metadata:
  name: pgvector
  namespace: ai-llm-demo
  labels:
    app: pgvector
    service: pgvector
spec:
  ports:
  - port: 5432
    name: postgres
  selector:
    app: pgvector
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: llm-with-embeddings
  namespace: ai-llm-demo
  labels:
    app: ai-llm-demo
spec:
  replicas: 1
  selector:
    matchLabels:
      app: llm-with-embeddings
  template:
    metadata:
      labels:
        app: llm-with-embeddings
    spec:
      containers:
      - name: llm-with-embeddings
        image: btjimerson/ai-llm-demo:0.0.1-SNAPSHOT
        imagePullPolicy: Always
        ports:
        - containerPort: 8080
        env:
          - name: SPRING_PROFILES_ACTIVE
            value: nodocker
          - name: LLM_USE_EMBEDDINGS
            value: "true"
          - name: OPENAI_CHAT_BASE_URL
            value: http://gloo-proxy-ai-gateway.gloo-system.svc:8080/openai
          - name: OPENAI_API_KEY
            value: $OPENAI_API_KEY
          - name: PG_VECTOR_URL
            value: jdbc:postgresql://pgvector.ai-llm-demo.svc:5432/ai-llm-demo
          - name: PG_VECTOR_USERNAME
            value: postgres
          - name: PG_VECTOR_PASSWORD
            value: postgres
---
apiVersion: v1
kind: Service
metadata:
  name: llm-with-embeddings
  namespace: ai-llm-demo
  labels:
    app: llm-with-embeddings
    service: llm-with-embeddings
spec:
  ports:
  - port: 8080
    name: http
  selector:
    app: llm-with-embeddings
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: llm-no-embeddings
  namespace: ai-llm-demo
  labels:
    app: ai-llm-demo
spec:
  replicas: 1
  selector:
    matchLabels:
      app: llm-no-embeddings
  template:
    metadata:
      labels:
        app: llm-no-embeddings
    spec:
      containers:
      - name: llm-no-embeddings
        image: btjimerson/ai-llm-demo:0.0.1-SNAPSHOT
        imagePullPolicy: Always
        ports:
        - containerPort: 8080
        env:
          - name: SPRING_PROFILES_ACTIVE
            value: nodocker
          - name: LLM_USE_EMBEDDINGS
            value: "false"
          - name: OPENAI_CHAT_BASE_URL
            value: http://gloo-proxy-ai-gateway.gloo-system.svc:8080/openai
          - name: OPENAI_API_KEY
            value: $OPENAI_API_KEY
          - name: PG_VECTOR_URL
            value: jdbc:postgresql://pgvector.ai-llm-demo.svc:5432/ai-llm-demo
          - name: PG_VECTOR_USERNAME
            value: postgres
          - name: PG_VECTOR_PASSWORD
            value: postgres
---
apiVersion: v1
kind: Service
metadata:
  name: llm-no-embeddings
  namespace: ai-llm-demo
  labels:
    app: llm-no-embeddings
    service: llm-no-embeddings
spec:
  ports:
  - port: 8080
    name: http
  selector:
    app: llm-no-embeddings
EOF

```

Wait for the app to become available:

```bash
kubectl rollout status -n ai-llm-demo deployments/llm-no-embeddings
```

# HTTP Routes

Create HTTP routes for the demo apps:

```bash
kubectl apply -f- <<EOF
apiVersion: gateway.networking.k8s.io/v1
kind: HTTPRoute
metadata:
  name: llm-no-embeddings-route
  namespace: ai-llm-demo
  labels:
    app: llm-no-embeddings
spec:
  parentRefs:
    - name: http-9080
      namespace: gloo-system
  hostnames:
    - $AI_LLM_DEMO_HOSTNAME_9080
  rules:
    - matches:
      - path:
          type: PathPrefix
          value: /
      backendRefs:
        - name: llm-no-embeddings
          port: 8080
---
apiVersion: gateway.networking.k8s.io/v1
kind: HTTPRoute
metadata:
  name: llm-with-embeddings-route
  namespace: ai-llm-demo
  labels:
    app: llm-with-embeddings
spec:
  parentRefs:
    - name: http-9090
      namespace: gloo-system
  hostnames:
    - $AI_LLM_DEMO_HOSTNAME_9090
  rules:
    - matches:
      - path:
          type: PathPrefix
          value: /
      backendRefs:
        - name: llm-with-embeddings
          port: 8080
EOF
```

Make sure you can access the pages:

```bash
open $(echo http://$AI_LLM_DEMO_HOSTNAME_9080:9080)
open $(echo http://$AI_LLM_DEMO_HOSTNAME_9090:9090)
```

# AI Gateway RAG

Create a Route Option with the connection to the vector database:

```bash
kubectl apply -f - <<EOF
apiVersion: gateway.solo.io/v1
kind: RouteOption
metadata:
  name: openai-route-option
  namespace: gloo-system
spec:
  targetRefs:
  - group: gateway.networking.k8s.io
    kind: HTTPRoute
    name: openai
  options:
    ai:
      rag:
        datastore:
          postgres:
            connectionString: postgresql+psycopg://postgres:postgres@pgvector.ai-llm-demo.svc.cluster.local:5432/ai-llm-demo
            collectionName: default
        embedding:
          openai:
            authToken:
              secretRef:
                name: openai-secret
                namespace: gloo-system
    timeout: "0"
EOF
```