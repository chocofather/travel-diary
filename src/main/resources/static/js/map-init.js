/**
 * 국내(KR)만 Kakao 지도를 그린다.
 * 해외 지도는 상세 템플릿의 Maps Embed API iframe 이 담당하므로 여기서 처리하지 않는다.
 */
function initMap(lat, lng, countryCode) {
    if (countryCode !== 'KR') {
        return;
    }
    loadKakaoMap(parseFloat(lat), parseFloat(lng));
}

function loadKakaoMap(lat, lng) {
    if (!window.kakao || !window.kakao.maps) {
        loadKakaoScript(() => drawKakaoMap(lat, lng));
    } else {
        drawKakaoMap(lat, lng);
    }
}

function loadKakaoScript(callback) {
    if (document.getElementById('kakao-map-script')) {
        kakao.maps.load(callback);
        return;
    }
    const script = document.createElement('script');
    script.id = 'kakao-map-script';
    script.src = 'https://dapi.kakao.com/v2/maps/sdk.js?appkey=e5aca11993412c733289f668bac6c606&autoload=false';
    script.onload = function () {
        kakao.maps.load(callback);
    };
    document.head.appendChild(script);
}

function drawKakaoMap(lat, lng) {
    const container = document.getElementById('map');
    if (!container) {
        console.error('drawKakaoMap: map container not found');
        return;
    }
    const options = { center: new kakao.maps.LatLng(lat, lng), level: 3 };
    const map = new kakao.maps.Map(container, options);
    new kakao.maps.Marker({ position: new kakao.maps.LatLng(lat, lng), map });
}
