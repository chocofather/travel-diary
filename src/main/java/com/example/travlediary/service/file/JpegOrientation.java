package com.example.travlediary.service.file;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/**
 * JPEG 의 EXIF 촬영 방향만 읽어 픽셀을 바로 세운다.
 *
 * <p>휴대전화로 세로로 찍은 사진은 픽셀이 누운 채 저장되고, EXIF 의 Orientation 이
 * "돌려서 보라"고 알려 준다. 브라우저는 그 표시를 보고 돌려서 보여 준다.
 *
 * <p>그런데 사진을 줄여 다시 구우면 그 표시가 사라진다. 그대로 두면 지금까지 똑바로 보이던
 * 사진이 눕는다. 그래서 다시 굽기 전에 표시대로 픽셀을 돌려 놓는다. 돌려 놓고 나면
 * 표시가 없어도 어느 프로그램에서나 똑바로 보인다.
 *
 * <p>EXIF 에서 읽는 값은 Orientation 하나뿐이다. 나머지 정보(촬영 위치 등)는 읽지도
 * 옮기지도 않으므로, 줄여 저장한 사진에는 위치 정보가 남지 않는다.
 */
final class JpegOrientation {

    /** 돌릴 것이 없는 상태. 읽지 못했을 때도 이 값으로 본다. */
    static final int NORMAL = 1;

    private JpegOrientation() {
    }

    /** 가로·세로가 뒤바뀌는 방향인지. 5~8 은 90도 돌아간 상태다. */
    static boolean swapsEdges(int orientation) {
        return orientation >= 5 && orientation <= 8;
    }

    /**
     * JPEG 바이트에서 Orientation 을 읽는다. 없거나 읽을 수 없으면 {@link #NORMAL}.
     *
     * <p>JPEG 가 아니면(PNG 등) 첫 두 바이트에서 걸러져 바로 {@link #NORMAL} 이 된다.
     */
    static int read(byte[] source) {
        if (source == null || source.length < 4
                || unsigned(source[0]) != 0xff || unsigned(source[1]) != 0xd8) {
            return NORMAL;
        }

        int position = 2;
        while (position + 4 <= source.length) {
            if (unsigned(source[position]) != 0xff) {
                return NORMAL;
            }
            int marker = unsigned(source[position + 1]);
            // 길이를 갖지 않는 표시들. 그냥 건너뛴다.
            if (marker == 0x01 || (marker >= 0xd0 && marker <= 0xd8)) {
                position += 2;
                continue;
            }
            // 그림 자료가 시작되면 더 볼 것이 없다.
            if (marker == 0xda || marker == 0xd9) {
                return NORMAL;
            }

            int segmentLength = (unsigned(source[position + 2]) << 8) | unsigned(source[position + 3]);
            if (segmentLength < 2 || position + 2 + segmentLength > source.length) {
                return NORMAL;
            }
            if (marker == 0xe1 && startsWithExif(source, position + 4)) {
                return readFromTiff(source, position + 4 + 6, position + 2 + segmentLength);
            }
            position += 2 + segmentLength;
        }
        return NORMAL;
    }

    /**
     * 표시대로 돌려 놓은 새 그림. 돌릴 것이 없으면 받은 그림을 그대로 돌려준다.
     */
    static BufferedImage apply(BufferedImage image, int orientation, boolean keepAlpha) {
        if (orientation <= NORMAL || orientation > 8) {
            return image;
        }

        int width = image.getWidth();
        int height = image.getHeight();
        int targetWidth = swapsEdges(orientation) ? height : width;
        int targetHeight = swapsEdges(orientation) ? width : height;

        AffineTransform transform = new AffineTransform();
        switch (orientation) {
            case 2 -> {                                     // 좌우 뒤집기
                transform.scale(-1, 1);
                transform.translate(-width, 0);
            }
            case 3 -> {                                     // 180도
                transform.translate(width, height);
                transform.rotate(Math.PI);
            }
            case 4 -> {                                     // 위아래 뒤집기
                transform.scale(1, -1);
                transform.translate(0, -height);
            }
            case 5 -> {                                     // 대각선 뒤집기
                transform.rotate(Math.PI / 2);
                transform.scale(1, -1);
            }
            case 6 -> {                                     // 시계 방향 90도
                transform.translate(height, 0);
                transform.rotate(Math.PI / 2);
            }
            case 7 -> {                                     // 반대 대각선 뒤집기
                transform.scale(-1, 1);
                transform.translate(-height, 0);
                transform.translate(0, width);
                transform.rotate(-Math.PI / 2);
            }
            case 8 -> {                                     // 반시계 방향 90도
                transform.translate(0, width);
                transform.rotate(-Math.PI / 2);
            }
            default -> {
                return image;
            }
        }

        BufferedImage target = new BufferedImage(targetWidth, targetHeight,
                keepAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.drawImage(image, transform, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private static boolean startsWithExif(byte[] source, int offset) {
        return offset + 6 <= source.length
                && source[offset] == 'E' && source[offset + 1] == 'x'
                && source[offset + 2] == 'i' && source[offset + 3] == 'f'
                && source[offset + 4] == 0 && source[offset + 5] == 0;
    }

    /** TIFF 머리말 + IFD0 에서 Orientation(0x0112) 한 칸만 찾는다. */
    private static int readFromTiff(byte[] source, int base, int limit) {
        if (base + 8 > limit) {
            return NORMAL;
        }
        boolean bigEndian;
        if (source[base] == 'M' && source[base + 1] == 'M') {
            bigEndian = true;
        } else if (source[base] == 'I' && source[base + 1] == 'I') {
            bigEndian = false;
        } else {
            return NORMAL;
        }
        if (readShort(source, base + 2, bigEndian) != 42) {
            return NORMAL;
        }

        long directoryOffset = readInt(source, base + 4, bigEndian);
        int directory = (int) (base + directoryOffset);
        if (directory < base || directory + 2 > limit) {
            return NORMAL;
        }

        int entryCount = readShort(source, directory, bigEndian);
        for (int index = 0; index < entryCount; index++) {
            int entry = directory + 2 + index * 12;
            if (entry + 12 > limit) {
                return NORMAL;
            }
            if (readShort(source, entry, bigEndian) != 0x0112) {
                continue;
            }
            // SHORT 한 칸은 값 자리의 앞 두 바이트에 그대로 들어 있다.
            int orientation = readShort(source, entry + 8, bigEndian);
            return orientation >= 1 && orientation <= 8 ? orientation : NORMAL;
        }
        return NORMAL;
    }

    private static int readShort(byte[] source, int offset, boolean bigEndian) {
        int first = unsigned(source[offset]);
        int second = unsigned(source[offset + 1]);
        return bigEndian ? (first << 8) | second : (second << 8) | first;
    }

    private static long readInt(byte[] source, int offset, boolean bigEndian) {
        long a = unsigned(source[offset]);
        long b = unsigned(source[offset + 1]);
        long c = unsigned(source[offset + 2]);
        long d = unsigned(source[offset + 3]);
        return bigEndian ? (a << 24) | (b << 16) | (c << 8) | d
                : (d << 24) | (c << 16) | (b << 8) | a;
    }

    private static int unsigned(byte value) {
        return value & 0xff;
    }
}
