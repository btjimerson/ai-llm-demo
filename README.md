# Introduction

This application is designed to demonstrate the developer experience with Gloo AI Gateway. Use this README to walk through the installation.

# Walk through

## Flow

![Flow](images/flow.png)

# AI Gateway

<aside>
💡

Make sure you’re using a non-ARM cluster. AI gateway extension isn’t built for ARM.

</aside>

# Build Docker image

Build and push the docker image for the app

```bash
docker buildx build . --platform linux/amd64,linux/arm64 -t btjimerson/ai-llm-demo:0.0.1-SNAPSHOT

docker push btjimerson/ai-llm-demo:0.0.1-SNAPSHOT
```

# Auth0 Setup

Domain: dev-phabkp71kmbjfe8n.us.auth0.com

Client ID: hvhbpxSFSfIJV85lowYoFZfmSDoDO0Ik

Client secret: rqjxShtckJ8JX7F3RfCDcAIbPc78YMq5MeNCAwr4Vljf7Bi51OJB_csrgiskhBc4

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

kubectl rollout status -n gloo-system deployments/gloo
```

# HTTP Gateway

Create a gateway with an HTTP listener:

```bash
kubectl apply -f- <<EOF
kind: Gateway
apiVersion: gateway.networking.k8s.io/v1
metadata:
  name: http
  namespace: gloo-system
spec:
  gatewayClassName: gloo-gateway
  listeners:
  - protocol: HTTP
    port: 9080
    name: http
    allowedRoutes:
      namespaces:
        from: All
EOF

kubectl rollout status -n gloo-system deployments/gloo-proxy-http
```

Get the endpoint for the gateway. Alternatively you can create a CNAME record that points to the ingress gateway and use that record for AI_LLM_DEMO_HOSTNAME:

```bash
export AI_LLM_DEMO_HOSTNAME=$(kubectl get svc -n gloo-system gloo-proxy-http -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')

echo "Gateway Hostname = $AI_LLM_DEMO_HOSTNAME"
```

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

Create an HTTP route that maps the /openai path to the OpenAI upstream:

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
    **- name: openai
      namespace: gloo-system
      group: gloo.solo.io
      kind: Upstream
---
apiVersion: gateway.networking.k8s.io/v1beta1
kind: ReferenceGrant
metadata:
  name: openai-grant
  namespace: gloo-system
spec:
  from:
  - group: gateway.networking.k8s.io
    kind: HTTPRoute
    namespace: gloo-system
  to:
  - group: ""
    kind: Service
EOF
```

# AI LLM Demo App

Deploy the AI LLM Demo application:

```bash
kubectl apply -f- <<EOF
---
apiVersion: v1
kind: Namespace
metadata:
  name: ai-llm-demo
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
  name: ai-llm-demo
  namespace: ai-llm-demo
  labels:
    app: ai-llm-demo
spec:
  replicas: 1
  selector:
    matchLabels:
      app: ai-llm-demo
  template:
    metadata:
      labels:
        app: ai-llm-demo
    spec:
      containers:
      - name: ai-llm-demo
        image: btjimerson/ai-llm-demo:0.0.1-SNAPSHOT
        imagePullPolicy: Always
        ports:
        - containerPort: 8080
        env:
          - name: OPENAI_API_KEY
            value: abcd1234
          - name: OPENAI_BASE_URL
            value: http://gloo-proxy-ai-gateway.gloo-system.svc:8080/openai
          - name: OPENAI_BASE_URL_OVERRIDE
            value: https://api.openai.com
          - name: OLLAMA_BASE_URL
            value: ollama-qwen.ollama.svc.cluster.local:11434
---
apiVersion: v1
kind: Service
metadata:
  name: ai-llm-demo
  namespace: ai-llm-demo
  labels:
    app: ai-llm-demo
    service: ai-llm-demo
spec:
  ports:
  - port: 8080
    name: http
  selector:
    app: ai-llm-demo
EOF

kubectl rollout status -n ai-llm-demo deployments/ai-llm-demo
```

# HTTP Route

Create an HTTP route for the demo app:

```bash
kubectl apply -f- <<EOF
apiVersion: gateway.networking.k8s.io/v1
kind: HTTPRoute
metadata:
  name: ai-llm-demo-route
  namespace: ai-llm-demo
  labels:
    app: ai-llm-demo
spec:
  parentRefs:
    - name: http
      namespace: gloo-system
  hostnames:
    - $AI_LLM_DEMO_HOSTNAME
  rules:
    - matches:
      - path:
          type: PathPrefix
          value: /
      backendRefs:
        - name: ai-llm-demo
          port: 8080
EOF
```

Make sure you can access the pages:

```bash
open $(echo http://$AI_LLM_DEMO_HOSTNAME:9080)
```

# JWT Propagation

Create 2 VirtualHostOptions (1 for the HTTP gateway and 1 for the AI gateway), that sets up the JWT provider:

```bash
kubectl apply -f- <<EOF
apiVersion: gateway.solo.io/v1
kind: VirtualHostOption
metadata:
  name: http-jwt-provider
  namespace: gloo-system
spec:
  targetRefs:
  - group: gateway.networking.k8s.io
    kind: Gateway
    name: http
  options:
    jwt:
      providers:
        selfminted:
          issuer: solo.io
          keepToken: true
          jwks:
            local:
              key: |
                -----BEGIN PUBLIC KEY-----
                MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAzgEPg3jVs5HPICKB2fz2
                wUkfMIMD7GYaBhrHAQlMccneU0PWkPOctJyziMPwZTdSPIKQpZhkIa+z1FP29bbn
                hpsW0GgTLowraelvXop06IqFbHL6vHL4rewyBOV9mbQJ2NbJDYXUpk3vXgLW2mpb
                T5LAs3HzMtQmp6RMFgBjRUQmZUQI99Vx5OjnoZOEMStOzgrdhacCqvfbCrVSaYF4
                X15Hfh4A9TKQSrQhHrScWHRDYWhqVjX0dP/h7yMKrA65cjwyoPiDcP8+9PJkjU7t
                hhmly+OT46l/a/fyeqxWBe0N8SKBPyhBPbOYzDY0fsYLVl6IBGISwp50ah2ICTVS
                GQIDAQAB
                -----END PUBLIC KEY-----
---
apiVersion: gateway.solo.io/v1
kind: VirtualHostOption
metadata:
  name: ai-gateway-jwt-provider
  namespace: gloo-system
spec:
  targetRefs:
  - group: gateway.networking.k8s.io
    kind: Gateway
    name: ai-gateway
  options:
    jwt:
      providers:
        selfminted:
          issuer: solo.io
          keepToken: true
          jwks:
            local:
              key: |
                -----BEGIN PUBLIC KEY-----
                MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAzgEPg3jVs5HPICKB2fz2
                wUkfMIMD7GYaBhrHAQlMccneU0PWkPOctJyziMPwZTdSPIKQpZhkIa+z1FP29bbn
                hpsW0GgTLowraelvXop06IqFbHL6vHL4rewyBOV9mbQJ2NbJDYXUpk3vXgLW2mpb
                T5LAs3HzMtQmp6RMFgBjRUQmZUQI99Vx5OjnoZOEMStOzgrdhacCqvfbCrVSaYF4
                X15Hfh4A9TKQSrQhHrScWHRDYWhqVjX0dP/h7yMKrA65cjwyoPiDcP8+9PJkjU7t
                hhmly+OT46l/a/fyeqxWBe0N8SKBPyhBPbOYzDY0fsYLVl6IBGISwp50ah2ICTVS
                GQIDAQAB
                -----END PUBLIC KEY----- 
EOF
```

Test the application with no JWT token:

```bash
curl -ik http://$AI_LLM_DEMO_HOSTNAME:9080/
```

Set JWT token environment variables:

```bash
export ALICE_TOKEN="eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiAic29sby5pbyIsIm9yZyI6ICJzb2xvLmlvIiwic3ViIjogImFsaWNlIiwidGVhbSI6ICJkZXYifQ.qSCARI8Xf-7D5hWdHT71Rov-4zQGMjUz9HV04OctS6oWpTrmVBKb0JcMiiDf2rpI5NQXXk5SdLTdokC-_VXXE67EwAKMYwa4qaSFcrJIfwkOb_gSV3KqMYYYKQCCxYeHOuGaR4xdqFdMAoeGFTa7BmKWq2ZLY6c3-uWPFuW2MX1Y6SCFJXAI803FMInZcTvvjRka3WejlI-CHUw_2ZESXUf6MA0shY9aoICPjI_TrukUVoxRzu6oc0JjvcHJuqRxY-MoGberBYqWezIFlOGjWnfqvAEEp0VI-g-dMNZ7_eBFathSKD3Em7gt33T3OIDKuqkZ8i4W7WzhMIhNlSFWlA"
export BOB_TOKEN="eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiAic29sby5pbyIsIm9yZyI6ICJzb2xvLmlvIiwic3ViIjogImJvYiIsInRlYW0iOiAib3BzIn0.M681liG4wW1DYmVwyjvAUIr4yJqZSaqODoWDSGd3egt5tuWN9ZBZLHh5odU-Y5EK8Nfq3fVzLSJtizVUWXtvMNAUUpzlfGHd99m6xdvZN9tkBWHXKTnT1vGnJ0Z9TRAlNvenSd2FZDChz7k2HW0E8IBvxMTtgPq-pMBEum2zWZIW1Bs9d8hWbEysYng7C-LdrBTj82dTps-FdPLNofigELozm8S2GQoZ5_2e42cBgngtYIcHpGJKPckPm_ZdMIujdN-5PxhLy91UX7dEI6B-O7tQyWxXV9quMEoAic67T1Np_b6ApnSXPkDspDZwUKhM6_ToiQhZqC2SwA4il9h62Q"

```

Test the application with a JWT token:

```bash
curl -ik http://$AI_LLM_DEMO_HOSTNAME:9080/ -H "Authorization: Bearer $ALICE_TOKEN"
```

## Create Route Option for JWT RBAC

Create a Route Option to require a JWT token for RBAC to the AI gateway

```bash
kubectl apply -f- <<EOF
apiVersion: gateway.solo.io/v1
kind: RouteOption
metadata:
  name: openai-rbac-route-option
  namespace: gloo-system
spec:
  targetRefs:
  - group: gateway.networking.k8s.io
    kind: HTTPRoute
    name: openai
    namespace: gloo-system
  options:
    rbac:
      policies:
        viewer:
          nestedClaimDelimiter: .
          principals:
          - jwtPrincipal:
              claims:
                "org": "solo.io"
                "team": "dev"
EOF
```

Verify that Alice’s token works, but Bob’s doesn’t:

```bash
curl -ik http://$AI_LLM_DEMO_HOSTNAME:9080/chat -H "Authorization: Bearer $ALICE_TOKEN" -d "prompt=How+is+your+day%3F"

curl -ik http://$AI_LLM_DEMO_HOSTNAME:9080/chat -H "Authorization: Bearer $BOB_TOKEN" -d "prompt=How+is+your+day%3F"
```

# Clean up

Delete all created resources:

```bash
kubectl delete routeoption -n gloo-system openai-rbac-route-option
kubectl delete virtualhostoption -n gloo-system ai-gateway-jwt-provider
kubectl delete virtualhostoption -n gloo-system http-jwt-provider
kubectl delete httproute -n ai-llm-demo ai-llm-demo-route
kubectl delete service -n ai-llm-demo ai-llm-demo
kubectl delete deployment -n ai-llm-demo ai-llm-demo
kubectl delete service -n ai-llm-demo pgvector
kubectl delete deployment -n ai-llm-demo pgvector
kubectl delete namespace ai-llm-demo
kubectl delete referencegrant -n gloo-system openai-grant
kubectl delete httproute -n gloo-system openai
kubectl delete upstream -n gloo-system openai
kubectl delete secret -n gloo-system openai-secret
kubectl delete listeneroption -n gloo-system ai-gateway-log-provider
kubectl delete gateway -n gloo-system ai-gateway
kubectl delete gatewayparameters -n gloo-system gloo-gateway-override
kubectl delete gateway -n gloo-system http

helm uninstall -n gloo-system gloo
kubectl get crd -A | grep 'solo' | xargs kubectl delete crd
kubectl delete namespace gloo-system

unset AI_LLM_DEMO_HOSTNAME
unset ALICE_TOKEN
unset BOB_TOKEN
```