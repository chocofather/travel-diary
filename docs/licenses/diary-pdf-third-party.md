# Diary PDF third-party libraries

Phase 1 다이어리 PDF는 아래 라이브러리를 Maven Central의 WebJar로 고정해 애플리케이션과 함께 배포한다. 브라우저는 실행 시 CDN에서 이 JavaScript를 받지 않는다.

| Library | Version | License | Upstream | Local runtime path | Bundled license |
| --- | --- | --- | --- | --- | --- |
| html-to-image | 1.11.13 | MIT | https://github.com/bubkoo/html-to-image | `/webjars/html-to-image/1.11.13/dist/html-to-image.js` | `META-INF/resources/webjars/html-to-image/1.11.13/LICENSE` |
| jsPDF | 3.0.1 | MIT | https://github.com/parallax/jsPDF | `/webjars/jspdf/3.0.1/dist/jspdf.umd.min.js` | `META-INF/resources/webjars/jspdf/3.0.1/LICENSE` |

위 license 파일과 package metadata는 각각 `org.webjars.npm:html-to-image:1.11.13`, `org.webjars.npm:jspdf:3.0.1` JAR 안에 포함된다.
