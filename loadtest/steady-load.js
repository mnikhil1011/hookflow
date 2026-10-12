// Sends events to Hookflow at a steady rate, spread evenly over a set of endpoints.
//
// With everything running in Docker Compose (profiles "app" and "loadtest"):
//
//   docker run --rm -i --network hookflow_default
//     -e BASE_URL=http://app:8080 -e RECEIVER_URL=http://mock-receiver:9000
//     grafana/k6 run - < loadtest/steady-load.js      (all on one line)
//
// With Hookflow and the mock receiver running directly on your machine:
//
//   docker run --rm -i --add-host=host.docker.internal:host-gateway
//     -e BASE_URL=http://host.docker.internal:8080
//     grafana/k6 run - < loadtest/steady-load.js      (all on one line)
//
// Settings (environment variables):
//   BASE_URL      where k6 reaches Hookflow                      default http://localhost:8080
//   RECEIVER_URL  where Hookflow reaches the mock receiver       default http://localhost:9000
//   ADMIN_TOKEN   Hookflow's admin token                         default dev-admin-token
//   ENDPOINTS     number of endpoints to spread events over      default 20
//   RATE          events per second                              default 100
//   DURATION      how long to run                                default 1m
import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const RECEIVER_URL = __ENV.RECEIVER_URL || 'http://localhost:9000';
const ADMIN_TOKEN = __ENV.ADMIN_TOKEN || 'dev-admin-token';
const ENDPOINTS = Number(__ENV.ENDPOINTS || 20);
const RATE = Number(__ENV.RATE || 100);
const DURATION = __ENV.DURATION || '1m';

const JSON_HEADERS = { 'Content-Type': 'application/json' };

export const options = {
  scenarios: {
    steady: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: 50,
      maxVUs: 500,
    },
  },
  thresholds: {
    // The run fails if Hookflow rejects events or takes too long to accept them.
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(99)<500'],
  },
};

// Runs once: creates one tenant per endpoint, so each event goes to exactly one endpoint.
export function setup() {
  const tenants = [];
  for (let i = 1; i <= ENDPOINTS; i++) {
    const tenant = http.post(`${BASE_URL}/v1/tenants`, JSON.stringify({ name: `load-test-${i}` }), {
      headers: { ...JSON_HEADERS, 'X-Admin-Token': ADMIN_TOKEN },
    });
    if (tenant.status !== 201) {
      throw new Error(`Could not create a tenant: ${tenant.status} ${tenant.body}`);
    }
    const apiKey = tenant.json('apiKey');

    const endpoint = http.post(`${BASE_URL}/v1/endpoints`, JSON.stringify({ url: `${RECEIVER_URL}/hook/${i}` }), {
      headers: { ...JSON_HEADERS, Authorization: `Bearer ${apiKey}` },
    });
    if (endpoint.status !== 201) {
      throw new Error(`Could not create an endpoint: ${endpoint.status} ${endpoint.body}`);
    }
    tenants.push({ apiKey, endpointId: endpoint.json('id') });
  }

  // Printed so you can watch endpoint 1's limit while you slow down /hook/1 on the mock receiver.
  console.log(`Endpoint 1: curl -H "Authorization: Bearer ${tenants[0].apiKey}" ${BASE_URL}/v1/endpoints/${tenants[0].endpointId}/flow`);
  return { tenants };
}

// Runs RATE times per second: sends one event for a randomly chosen tenant.
export default function (data) {
  const tenant = data.tenants[Math.floor(Math.random() * data.tenants.length)];
  const event = { type: 'order.paid', payload: { orderId: Math.floor(Math.random() * 1e9), amount: 4999 } };

  const response = http.post(`${BASE_URL}/v1/events`, JSON.stringify(event), {
    headers: { ...JSON_HEADERS, Authorization: `Bearer ${tenant.apiKey}` },
  });
  check(response, { 'event accepted': (r) => r.status === 202 });
}
