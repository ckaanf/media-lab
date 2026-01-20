import http from 'vod-server/experiments/k6/http';
import {check, sleep} from 'k6';
import {randomIntBetween} from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

export const options = {
    stages: [
        {duration: '5s', target: 50},
        {duration: '20s', target: 100},
        {duration: '5s', target: 0},
    ],
    thresholds: {
        'http_req_duration{type:java_v1}': ['p(95)<200'],
        'http_req_duration{type:cpp_v2}': ['p(95)<50'],
    },
};

const JAVA_HOST = 'http://localhost:8080';
const CPP_HOST = 'http://localhost:8081';

const FILE_NAME = 'sample.mp4';
const CHUNK_SIZE = 256 * 1024;

export default function () {
    let start = randomIntBetween(0, 10000000);
    let end = start + CHUNK_SIZE;

    let params = {
        headers: {
            'Range': `bytes=${start}-${end}`,
        },
        tags: {type: 'java_v1'},
    };

    let resJava = http.get(`${JAVA_HOST}/api/v1/stream/v/${FILE_NAME}`, params);

    check(resJava, {
        'Java Status 206': (r) => r.status === 206,
    });

    params.tags.type = 'cpp_v2';
    let resCpp = http.get(`${CPP_HOST}/api/v2/stream/v/${FILE_NAME}`, params);

    check(resCpp, {
        'C++ Status 206': (r) => r.status === 206,
    });

    sleep(0.1);
}