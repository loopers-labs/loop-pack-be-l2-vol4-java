import http from "k6/http";
import { check } from "k6";
import { Counter, Rate, Trend } from "k6/metrics";

// Keep this harness independent from the Gradle build so it can run on a load-generator host.
const PERIODS = ["daily", "weekly", "monthly"];
const BASE_URL = normalizeBaseUrl(__ENV.BASE_URL || "http://localhost:8080");
const TARGET_DATE = __ENV.TARGET_DATE || "";
const DURATION = __ENV.DURATION || "2m";
const PAGE = positiveInteger(__ENV.PAGE, 1, "PAGE");
const SIZE = positiveInteger(__ENV.SIZE, 20, "SIZE");
const REQUIRE_DATA = booleanValue(__ENV.REQUIRE_DATA, true, "REQUIRE_DATA");
const SUMMARY_PATH = __ENV.SUMMARY_PATH || "ranking-api-summary.json";

const workload = {
  daily: {
    date: __ENV.DAILY_DATE || TARGET_DATE,
    rate: positiveInteger(__ENV.DAILY_RPS, 14, "DAILY_RPS"),
    preAllocatedVUs: positiveInteger(__ENV.DAILY_VUS, 20, "DAILY_VUS"),
    expectedTotalCount: optionalNonNegativeInteger(
      __ENV.EXPECTED_DAILY_TOTAL_COUNT,
      "EXPECTED_DAILY_TOTAL_COUNT",
    ),
  },
  weekly: {
    date: __ENV.WEEKLY_DATE || TARGET_DATE,
    rate: positiveInteger(__ENV.WEEKLY_RPS, 4, "WEEKLY_RPS"),
    preAllocatedVUs: positiveInteger(__ENV.WEEKLY_VUS, 10, "WEEKLY_VUS"),
    expectedTotalCount: optionalNonNegativeInteger(
      __ENV.EXPECTED_WEEKLY_TOTAL_COUNT,
      "EXPECTED_WEEKLY_TOTAL_COUNT",
    ),
  },
  monthly: {
    date: __ENV.MONTHLY_DATE || TARGET_DATE,
    rate: positiveInteger(__ENV.MONTHLY_RPS, 2, "MONTHLY_RPS"),
    preAllocatedVUs: positiveInteger(__ENV.MONTHLY_VUS, 10, "MONTHLY_VUS"),
    expectedTotalCount: optionalNonNegativeInteger(
      __ENV.EXPECTED_MONTHLY_TOTAL_COUNT,
      "EXPECTED_MONTHLY_TOTAL_COUNT",
    ),
  },
};

const rankingRequests = new Counter("ranking_api_requests");
const rankingFailures = new Rate("ranking_api_failures");
const rankingDuration = new Trend("ranking_api_duration", true);

export const options = {
  scenarios: {
    ranking_daily: scenario("daily"),
    ranking_weekly: scenario("weekly"),
    ranking_monthly: scenario("monthly"),
  },
  thresholds: periodThresholds(),
  summaryTrendStats: ["min", "med", "avg", "p(90)", "p(95)", "p(99)", "max"],
};

export function setup() {
  if (!/^https?:\/\//.test(BASE_URL)) {
    throw new Error(`BASE_URL must start with http:// or https://: ${BASE_URL}`);
  }

  if (!/^\d+(ms|s|m|h)$/.test(DURATION)) {
    throw new Error(`DURATION must be a k6 duration such as 30s or 2m: ${DURATION}`);
  }

  for (const period of PERIODS) {
    assertDate(workload[period].date, `${period.toUpperCase()}_DATE`);
  }

  return {
    baseUrl: BASE_URL,
    page: PAGE,
    size: SIZE,
    requireData: REQUIRE_DATA,
    workload,
  };
}

export function dailyRanking(config) {
  requestRanking("daily", config);
}

export function weeklyRanking(config) {
  requestRanking("weekly", config);
}

export function monthlyRanking(config) {
  requestRanking("monthly", config);
}

export function handleSummary(data) {
  const periods = {};
  for (const period of PERIODS) {
    periods[period] = periodSummary(data, period);
  }

  const summary = {
    schemaVersion: 1,
    generatedAt: new Date().toISOString(),
    passed: allThresholdsPassed(data),
    test: {
      baseUrl: BASE_URL,
      duration: DURATION,
      page: PAGE,
      size: SIZE,
      requireData: REQUIRE_DATA,
      workload,
    },
    periods,
    droppedIterations: metricValue(data, "dropped_iterations", "count"),
    k6: data,
  };

  return {
    stdout: textSummary(summary),
    [SUMMARY_PATH]: `${JSON.stringify(summary, null, 2)}\n`,
  };
}

function requestRanking(period, config) {
  const tags = { endpoint: "rankings", period };
  const date = config.workload[period].date;
  const url =
    `${config.baseUrl}/api/v1/rankings` +
    `?period=${period}&date=${date}&page=${config.page}&size=${config.size}`;
  const response = http.get(url, { tags });

  let payload = null;
  try {
    payload = response.json();
  } catch (_) {
    // The response contract check below records malformed JSON as a failure.
  }

  const statusOk = response.status === 200;
  const contractOk = isRankingResponse(payload, config.page, config.size);
  const hasData = contractOk && payload.data.totalCount > 0 && payload.data.items.length > 0;
  const expectedTotalCount = config.workload[period].expectedTotalCount;
  const totalCountOk =
    expectedTotalCount === null ||
    (contractOk && payload.data.totalCount === expectedTotalCount);
  const dataRequirementOk =
    !config.requireData || expectedTotalCount === 0 || hasData;
  const successful = statusOk && contractOk && totalCountOk && dataRequirementOk;

  check(
    response,
    {
      "ranking status is 200": () => statusOk,
      "ranking response contract is valid": () => contractOk,
      "ranking totalCount matches expectation": () => totalCountOk,
      "ranking result is non-empty when required": () => dataRequirementOk,
    },
    tags,
  );

  rankingRequests.add(1, tags);
  rankingFailures.add(!successful, tags);
  rankingDuration.add(response.timings.duration, tags);
}

function scenario(period) {
  const config = workload[period];
  return {
    executor: "constant-arrival-rate",
    exec: `${period}Ranking`,
    rate: config.rate,
    timeUnit: "1s",
    duration: DURATION,
    preAllocatedVUs: config.preAllocatedVUs,
    maxVUs: config.preAllocatedVUs * 2,
    gracefulStop: "30s",
    tags: { endpoint: "rankings", period },
  };
}

function periodThresholds() {
  const thresholds = {
    dropped_iterations: ["count==0"],
  };

  for (const period of PERIODS) {
    const selector = `{period:${period}}`;
    thresholds[`ranking_api_requests${selector}`] = ["count>0"];
    thresholds[`ranking_api_failures${selector}`] = ["rate<0.001"];
    thresholds[`ranking_api_duration${selector}`] = ["p(95)<100", "p(99)<250"];
    thresholds[`checks${selector}`] = ["rate>0.999"];
  }

  return thresholds;
}

function isRankingResponse(payload, expectedPage, expectedSize) {
  if (payload === null || typeof payload !== "object") {
    return false;
  }

  const meta = payload.meta;
  const data = payload.data;
  if (
    meta === null ||
    typeof meta !== "object" ||
    meta.result !== "SUCCESS" ||
    data === null ||
    typeof data !== "object" ||
    !Array.isArray(data.items) ||
    data.page !== expectedPage ||
    data.size !== expectedSize ||
    !isNonNegativeInteger(data.totalCount) ||
    !isNonNegativeInteger(data.totalPages)
  ) {
    return false;
  }

  const expectedTotalPages = Math.ceil(data.totalCount / expectedSize);
  return (
    data.items.length <= expectedSize &&
    data.totalPages === expectedTotalPages &&
    data.items.every(
      (item) => isRankingItem(item) && item.rank <= data.totalCount,
    )
  );
}

function isRankingItem(item) {
  if (item === null || typeof item !== "object") {
    return false;
  }

  return (
    Number.isInteger(item.rank) &&
    item.rank >= 1 &&
    typeof item.score === "number" &&
    Number.isFinite(item.score) &&
    isProductDetail(item.product)
  );
}

function isProductDetail(product) {
  if (product === null || typeof product !== "object") {
    return false;
  }

  return (
    Number.isInteger(product.id) &&
    product.id >= 1 &&
    Number.isInteger(product.brandId) &&
    product.brandId >= 1 &&
    (product.brandName === undefined ||
      product.brandName === null ||
      typeof product.brandName === "string") &&
    typeof product.name === "string" &&
    typeof product.description === "string" &&
    isNonNegativeInteger(product.price) &&
    isNonNegativeInteger(product.stock) &&
    isNonNegativeInteger(product.likeCount)
  );
}

function isNonNegativeInteger(value) {
  return Number.isInteger(value) && value >= 0;
}

function periodSummary(data, period) {
  const selector = `{period:${period}}`;
  const durationValues = metricValues(data, `ranking_api_duration${selector}`);
  const failureValues = metricValues(data, `ranking_api_failures${selector}`);
  const checkValues = metricValues(data, `checks${selector}`);

  return {
    date: workload[period].date,
    configuredRps: workload[period].rate,
    requests: metricValue(data, `ranking_api_requests${selector}`, "count"),
    failureRate: nullableValue(failureValues.rate),
    checkRate: nullableValue(checkValues.rate),
    durationMs: {
      average: nullableValue(durationValues.avg),
      p95: nullableValue(durationValues["p(95)"]),
      p99: nullableValue(durationValues["p(99)"]),
      max: nullableValue(durationValues.max),
    },
  };
}

function textSummary(summary) {
  const lines = [
    "",
    "Ranking API performance summary",
    `result=${summary.passed ? "PASS" : "FAIL"} duration=${summary.test.duration}`,
  ];

  for (const period of PERIODS) {
    const result = summary.periods[period];
    lines.push(
      `${period.padEnd(7)} requests=${format(result.requests)} ` +
        `failures=${formatPercent(result.failureRate)} ` +
        `p95=${formatMilliseconds(result.durationMs.p95)} ` +
        `p99=${formatMilliseconds(result.durationMs.p99)}`,
    );
  }

  lines.push(
    `dropped_iterations=${format(summary.droppedIterations)}`,
    `summary=${SUMMARY_PATH}`,
    "",
  );
  return lines.join("\n");
}

function allThresholdsPassed(data) {
  for (const metric of Object.values(data.metrics)) {
    if (!metric.thresholds) {
      continue;
    }
    for (const threshold of Object.values(metric.thresholds)) {
      if (threshold.ok === false) {
        return false;
      }
    }
  }
  return true;
}

function metricValues(data, name) {
  return data.metrics[name] ? data.metrics[name].values : {};
}

function metricValue(data, name, value) {
  return nullableValue(metricValues(data, name)[value]);
}

function nullableValue(value) {
  return value === undefined ? null : value;
}

function format(value) {
  return value === null ? "n/a" : String(value);
}

function formatPercent(value) {
  return value === null ? "n/a" : `${(value * 100).toFixed(3)}%`;
}

function formatMilliseconds(value) {
  return value === null ? "n/a" : `${value.toFixed(2)}ms`;
}

function normalizeBaseUrl(value) {
  return value.replace(/\/+$/, "");
}

function positiveInteger(value, fallback, name) {
  const parsed = value === undefined || value === "" ? fallback : Number(value);
  if (!Number.isInteger(parsed) || parsed <= 0) {
    throw new Error(`${name} must be a positive integer: ${value}`);
  }
  return parsed;
}

function booleanValue(value, fallback, name) {
  if (value === undefined || value === "") {
    return fallback;
  }
  if (value === "true") {
    return true;
  }
  if (value === "false") {
    return false;
  }
  throw new Error(`${name} must be true or false: ${value}`);
}

function optionalNonNegativeInteger(value, name) {
  if (value === undefined || value === "") {
    return null;
  }
  const parsed = Number(value);
  if (!Number.isInteger(parsed) || parsed < 0) {
    throw new Error(`${name} must be a non-negative integer: ${value}`);
  }
  return parsed;
}

function assertDate(value, name) {
  if (!/^\d{8}$/.test(value)) {
    throw new Error(`${name} must use yyyyMMdd: ${value}`);
  }

  const year = Number(value.slice(0, 4));
  const month = Number(value.slice(4, 6));
  const day = Number(value.slice(6, 8));
  const date = new Date(Date.UTC(year, month - 1, day));
  const valid =
    date.getUTCFullYear() === year &&
    date.getUTCMonth() === month - 1 &&
    date.getUTCDate() === day;
  if (!valid) {
    throw new Error(`${name} is not a valid calendar date: ${value}`);
  }
}
