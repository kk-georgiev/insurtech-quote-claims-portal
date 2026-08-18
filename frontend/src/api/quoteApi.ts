export type RegionRisk = 'SOFIA' | 'LARGE_CITY' | 'OTHER';
export type BonusMalusLevel = 'BONUS_20' | 'BONUS_10' | 'NEUTRAL' | 'MALUS_25' | 'MALUS_50';

export type CreateQuoteRequest = {
  driverAge: number;
  drivingExperienceYears: number;
  region: RegionRisk;
  vehiclePowerKw: number;
  bonusMalusLevel: BonusMalusLevel;
};

export type Quote = {
  id: string;
  status: 'CREATED' | 'ACCEPTED' | 'EXPIRED';
  input: CreateQuoteRequest;
  breakdown: {
    basePremium: number;
    ageFactor: number;
    experienceFactor: number;
    regionFactor: number;
    powerFactor: number;
    bonusMalusFactor: number;
  };
  premium: number;
  currency: string;
  pricingVersion: string;
  createdAt: string;
  validUntil: string;
};

type ApiError = {
  message?: string;
  fieldErrors?: Record<string, string>;
};

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? '';

export async function createQuote(payload: CreateQuoteRequest): Promise<Quote> {
  const response = await fetch(`${apiBaseUrl}/api/v1/quotes`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiError;
    const firstFieldError = error.fieldErrors ? Object.values(error.fieldErrors)[0] : undefined;
    throw new Error(firstFieldError ?? error.message ?? `Quote request failed with HTTP ${response.status}`);
  }

  return response.json() as Promise<Quote>;
}
