// Thin wrapper around the Party v2 REST API.
//
// Backend lives at http://127.0.0.1:18081/api/v1 in dev. Override via env or
// build-time VITE_PARTY_API_BASE.
const BASE = (import.meta.env.VITE_PARTY_API_BASE as string | undefined)
    ?? 'http://127.0.0.1:18081/api/v1';

export interface AdapterHealth {
    tenantId: string;
    type: string;
    status: string;
    target: string | null;
    error: string | null;
}

export interface ValidateRequest {
    tenantId: string;
    login: string;
    password: string;
}

export interface User {
    externalId: string;
    login: string;
    email: string | null;
    displayName: string | null;
    active: boolean;
    roles: string[];
}

export interface ValidateResponse {
    valid: boolean;
    reason: string | null;
    policyName: string | null;
    user: User | null;
}

async function asJson<T>(r: Response): Promise<T> {
    const text = await r.text();
    if (!r.ok) {
        throw new Error(`HTTP ${r.status} ${r.statusText}: ${text}`);
    }
    return text ? JSON.parse(text) as T : (undefined as unknown as T);
}

export const partyApi = {
    base: BASE,

    async adapterHealth(): Promise<AdapterHealth[]> {
        const r = await fetch(`${BASE}/v2/health/adapters`, { method: 'GET' });
        return asJson<AdapterHealth[]>(r);
    },

    async validate(req: ValidateRequest): Promise<ValidateResponse> {
        const r = await fetch(`${BASE}/v2/auth/validate`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(req),
        });
        return asJson<ValidateResponse>(r);
    },
};
