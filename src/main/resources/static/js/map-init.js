function initMap(lat, lng, countryCode) {
    const parsedLat = parseFloat(lat);
    const parsedLng = parseFloat(lng);

    if (countryCode === 'KR') {
        loadKakaoMap(parsedLat, parsedLng);
    } else {
        loadGoogleMap(parsedLat, parsedLng);
    }
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

// ---------------------- Google Maps ------------------------

function loadGoogleMap(lat, lng) {
    if (!window.google || !window.google.maps) {
        loadGoogleScript(() => drawGoogleMap(lat, lng), lat, lng);
    } else {
        drawGoogleMap(lat, lng);
    }
}

function loadGoogleScript(callback, lat, lng) {
    if (document.getElementById('google-map-script')) {
        callback(lat, lng);
        return;
    }
    window.__googleMapLat = lat;
    window.__googleMapLng = lng;
    window.onGoogleMapLoaded = function () {
        callback(window.__googleMapLat, window.__googleMapLng);
    };
    const script = document.createElement('script');
    script.id = 'google-map-script';
    script.src = 'https://maps.googleapis.com/maps/api/js?key=AIzaSyBHbZyqqBRhW4Wb4dn80c12jHoUe_WtaHI&callback=onGoogleMapLoaded';
    document.head.appendChild(script);
}

function drawGoogleMap(lat, lng) {
    if (!lat || !lng || isNaN(lat) || isNaN(lng)) {
        console.error('Invalid GoogleMap lat/lng', lat, lng);
        return;
    }
    const container = document.getElementById('map');
    if (!container) {
        console.error('drawGoogleMap: map container not found');
        return;
    }
    const map = new google.maps.Map(container, {
        center: { lat: lat, lng: lng },
        zoom: 15
    });
    new google.maps.Marker({ position: { lat: lat, lng: lng }, map: map });
}
