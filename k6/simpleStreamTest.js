import http from 'k6/http';

export const options = {
    vus: 205,
    duration: '20s'
};

export default function () {
    http.get('http://localhost:8080/api/v1/stream/v/block')
}