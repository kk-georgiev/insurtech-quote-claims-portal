# Quote pricing v1 — demonstration tariff

## Status

`2026.1-demo` is an explicit demonstration tariff for the first vertical slice.
It is not presented as a production insurance formula. The coefficients must be
reviewed with the mentor and later moved to versioned configuration or tariff
tables.

## Inputs

| Input | Accepted values |
|---|---|
| Driver age | 18–100 |
| Driving experience | 0–82 years and not greater than age minus 17 |
| Region | `SOFIA`, `LARGE_CITY`, `OTHER` |
| Vehicle power | 20–500 kW |
| Bonus–malus | `BONUS_20`, `BONUS_10`, `NEUTRAL`, `MALUS_25`, `MALUS_50` |

## Formula

```text
premium = base premium
          × age factor
          × experience factor
          × region factor
          × power factor
          × bonus–malus factor
```

Base premium: `180.00 EUR`. Final premium is rounded to two decimal places and
bounded between `120.00 EUR` and `1500.00 EUR`.

### Age

| Rule | Factor |
|---|---:|
| under 25 | 1.350 |
| 25–29 | 1.150 |
| 30–69 | 1.000 |
| 70+ | 1.250 |

### Driving experience

| Rule | Factor |
|---|---:|
| under 2 years | 1.300 |
| 2–4 years | 1.100 |
| 5+ years | 1.000 |

### Region

| Value | Factor |
|---|---:|
| Sofia | 1.200 |
| Other large city | 1.100 |
| Other region | 1.000 |

### Vehicle power

| Rule | Factor |
|---|---:|
| up to 74 kW | 0.900 |
| 75–110 kW | 1.000 |
| 111–150 kW | 1.150 |
| above 150 kW | 1.350 |

### Bonus–malus

| Value | Factor |
|---|---:|
| Bonus 20% | 0.800 |
| Bonus 10% | 0.900 |
| Neutral | 1.000 |
| Malus 25% | 1.250 |
| Malus 50% | 1.500 |

## Snapshot and validity

Every created quote stores its inputs, every factor, the final premium, tariff
version, creation time and validity end. The current validity period is 30 days.
This snapshot allows a future policy to reference the exact accepted price even
after tariff changes.

## Decisions still required

- authoritative coefficients and base premium;
- product and coverage period represented by the premium;
- currency and rounding rules;
- source of the bonus–malus level;
- tariff administration and effective-date versioning;
- whether expired quotes remain retrievable and how acceptance is authorized.
