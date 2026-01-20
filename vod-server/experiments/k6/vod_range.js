import http from 'k6/http';
import {check, sleep} from 'k6';
import {randomIntBetween} from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

export const options = {
    stages: [
        { duration: '10s', target: __ENV.VUS },
        { duration: '20s', target: __ENV.VUS },
    ],
};

const FILE = 'sample.mp4';
const CHUNK = __ENV.CHUNK_SIZE
    ? parseInt(__ENV.CHUNK_SIZE)
    : 256 * 1024;

export default function () {
    const start = randomIntBetween(0, 10_000_000);
    const end = start + CHUNK;

    const res = http.get(`http://localhost:8080/api/v1/stream/v/${FILE}`, {
        headers: {
            Range: `bytes=${start}-${end}`,
        },
    });

    check(res, {
        'status is 206': (r) => r.status === 206,
    });

    sleep(0.1);
}
