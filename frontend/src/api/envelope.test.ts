import { describe, expect, it } from 'vitest';
import axios from 'axios';
import { readApiError, unwrapApiSuccess } from './envelope';

describe('envelope', () => {
    it('readApiError extracts message and code from standard envelope', () => {
        const err = {
            isAxiosError: true,
            message: 'Request failed with status code 404',
            response: {
                data: {
                    success: false,
                    errors: { code: 'RESOURCE_NOT_FOUND', message: 'Alarm bulunamadı' },
                },
            },
        };
        Object.setPrototypeOf(err, axios.AxiosError.prototype);

        const parsed = readApiError(err);
        expect(parsed.code).toBe('RESOURCE_NOT_FOUND');
        expect(parsed.message).toBe('Alarm bulunamadı');
    });

    it('readApiError falls back to legacy root message', () => {
        const err = {
            isAxiosError: true,
            message: 'Network Error',
            response: { data: { message: 'Eski format' } },
        };
        Object.setPrototypeOf(err, axios.AxiosError.prototype);

        expect(readApiError(err).message).toBe('Eski format');
    });

    it('readApiError reads suspended user from code field', () => {
        const err = {
            isAxiosError: true,
            message: 'Forbidden',
            response: {
                status: 403,
                data: {
                    success: false,
                    errors: {
                        code: 'USER_LOGIN_SUSPENDED',
                        message: 'Hesabınız askıya alındı.',
                    },
                },
            },
        };
        Object.setPrototypeOf(err, axios.AxiosError.prototype);

        const parsed = readApiError(err);
        expect(parsed.code).toBe('USER_LOGIN_SUSPENDED');
        expect(parsed.message).toContain('askıya');
    });

    it('unwrapApiSuccess returns data payload', () => {
        const payload = { success: true, data: { id: 1 }, errors: null };
        expect(unwrapApiSuccess<{ id: number }>(payload)).toEqual({ id: 1 });
    });
});
