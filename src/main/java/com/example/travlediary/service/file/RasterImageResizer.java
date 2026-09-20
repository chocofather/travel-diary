package com.example.travlediary.service.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * 올라온 JPEG/PNG 를 화면에 필요한 크기까지 줄인다.
 *
 * <p>얼마까지 줄일지는 여기서 정하지 않는다. 쓰는 자리마다 필요한 크기가 다르므로
 * 저장 서비스가 자기 정책값({@code maxEdge}, {@code decodeEdge})을 넘겨 준다.
 * 지금은 프로필 사진(256px)과 여행정보 목록 썸네일(1200px)이 쓴다.
 *
 * <p><b>줄이지 못하면 원본을 그대로 돌려준다.</b> 사진을 줄이는 일은 업로드의 곁가지지
 * 업로드가 되느냐 마느냐를 가르는 조건이 아니다. 여기서 예외를 올리면 지금까지 되던 업로드가
 * 갑자기 막힌다.
 */
final class RasterImageResizer {

    /** 사진을 줄인 크기에서 눈으로 구분되지 않는 선. 쓰는 자리가 달라도 같은 값을 쓴다. */
    private static final float JPEG_QUALITY = 0.85f;

    private static final Logger log = LoggerFactory.getLogger(RasterImageResizer.class);

    private RasterImageResizer() {
    }

    /** 줄여서 저장할 수 있는 형식인지. WEBP 는 이 런타임의 ImageIO 가 읽지도 쓰지도 못한다. */
    static boolean canResize(String imageIoFormat) {
        return ImageIO.getImageReadersByFormatName(imageIoFormat).hasNext()
                && ImageIO.getImageWritersByFormatName(imageIoFormat).hasNext();
    }

    /**
     * 긴 변이 {@code maxEdge} 를 넘으면 줄여서 다시 굽는다.
     *
     * <p>이미 그 안에 드는 사진은 손대지 않고 원본 바이트를 그대로 돌려준다. 다시 구우면
     * 품질만 깎이고 얻는 것이 없다. 원본보다 키우는 일도 하지 않는다.
     *
     * @param source       업로드된 그대로의 바이트
     * @param imageIoFormat {@code "jpeg"} 또는 {@code "png"}
     * @param keepAlpha    투명도를 지닐 수 있는 형식인지 (PNG 는 true, JPEG 는 false)
     * @param maxEdge      저장할 그림의 긴 변 상한
     * @param decodeEdge   펼쳐서 메모리에 올리는 단계의 긴 변 상한
     * @return 줄인 바이트. 줄일 수 없거나 줄일 필요가 없으면 {@code source} 그대로
     */
    static byte[] optimize(byte[] source, String imageIoFormat, boolean keepAlpha,
                           int maxEdge, int decodeEdge) {
        try {
            int[] dimensions = readDimensions(source, imageIoFormat);
            if (dimensions == null) {
                // 머리말은 맞지만 펼칠 수 없는 파일. 예전처럼 그대로 저장한다.
                return source;
            }

            /*
              휴대전화로 세로로 찍은 사진은 픽셀이 누운 채 저장되고, EXIF 가 "돌려서 보라"고
              알려 준다. 지금까지는 그 표시가 파일에 남아 있어 브라우저가 돌려서 보여 줬다.
              다시 구우면 그 표시가 사라지므로, 보이는 모습대로 픽셀을 돌려 두고 저장한다.
             */
            int orientation = JpegOrientation.read(source);
            int uprightWidth = JpegOrientation.swapsEdges(orientation)
                    ? dimensions[1] : dimensions[0];
            int uprightHeight = JpegOrientation.swapsEdges(orientation)
                    ? dimensions[0] : dimensions[1];

            if (Math.max(uprightWidth, uprightHeight) <= maxEdge) {
                // 이미 충분히 작다. 펼쳐 보지도 않는다. 돌리는 표시도 원본에 그대로 남는다.
                return source;
            }

            BufferedImage decoded = decodeWithinBudget(source, imageIoFormat, decodeEdge);
            if (decoded == null) {
                return source;
            }
            BufferedImage upright = JpegOrientation.apply(decoded, orientation, keepAlpha);
            double scale = (double) maxEdge / Math.max(upright.getWidth(), upright.getHeight());
            BufferedImage resized = scale(upright,
                    Math.max(1, (int) Math.round(upright.getWidth() * scale)),
                    Math.max(1, (int) Math.round(upright.getHeight() * scale)),
                    keepAlpha);

            byte[] encoded = encode(resized, imageIoFormat);
            return encoded == null ? source : encoded;
        } catch (IOException | RuntimeException failure) {
            /*
              사진 한 장을 줄이지 못한 것뿐이다. 원본을 그대로 저장하면 업로드는 그대로 된다.

              OutOfMemoryError 는 여기서 잡지 않는다. 그것은 이 사진의 문제가 아니라
              JVM 전체가 메모리를 다 쓴 상태라는 뜻이다. 삼켜서 업로드를 성공시키면
              같은 순간 다른 요청들도 무너지고 있는데 그 사실만 가려진다.
              그대로 올려보내 컨테이너가 알아채게 둔다.
             */
            log.warn("Uploaded image could not be resized, the original is stored as is:"
                    + " format={}, bytes={}, maxEdge={}, failureType={}",
                    imageIoFormat, source.length, maxEdge, failure.getClass().getSimpleName());
            return source;
        }
    }

    /**
     * 머리말에서 원본 치수만 읽는다. 픽셀은 한 장도 펼치지 않는다.
     *
     * @return {@code {가로, 세로}}. 읽을 수 없으면 {@code null}
     */
    static int[] readDimensions(byte[] source, String imageIoFormat) throws IOException {
        try (ImageInputStream input =
                     ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            ImageReader reader = readerFor(input, imageIoFormat);
            if (reader == null) {
                return null;
            }
            try {
                reader.setInput(input);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                return width > 0 && height > 0 ? new int[]{width, height} : null;
            } finally {
                reader.dispose();
            }
        }
    }

    /**
     * 몇 칸씩 건너뛰며 읽을지. 펼친 뒤의 긴 변이 {@code decodeEdge} 안쪽이 되게 고른다.
     *
     * <p>예를 들어 12000px 짜리는 12칸씩 건너뛰어 1000px 로 펼쳐진다.
     * 12000×9000 전체를 메모리에 올리는 일이 없다.
     */
    static int subsamplingStep(int longEdge, int decodeEdge) {
        return Math.max(1, (int) Math.ceil((double) longEdge / decodeEdge));
    }

    /**
     * 긴 변이 {@code decodeEdge} 안쪽이 되도록 건너뛰며 펼친다.
     *
     * <p>{@code ImageIO.read} 는 원본 해상도 전체를 {@code BufferedImage} 로 만든다.
     * 업로드에는 파일 크기 상한만 있고 픽셀 수 상한이 없어서, 잘 압축된 거대한 그림 한 장이
     * 수 GB 를 집어삼킬 수 있다. 어차피 줄일 것이라 그렇게까지 펼칠 이유가 없다.
     * ({@code FileUploadService.validateGeneralImage} 가 쓰는 것과 같은 방식이다)
     *
     * @return 펼친 그림. 읽을 수 없으면 {@code null}
     */
    static BufferedImage decodeWithinBudget(byte[] source, String imageIoFormat, int decodeEdge)
            throws IOException {
        try (ImageInputStream input =
                     ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            ImageReader reader = readerFor(input, imageIoFormat);
            if (reader == null) {
                return null;
            }
            try {
                reader.setInput(input);
                int step = subsamplingStep(
                        Math.max(reader.getWidth(0), reader.getHeight(0)), decodeEdge);
                ImageReadParam parameters = reader.getDefaultReadParam();
                parameters.setSourceSubsampling(step, step, 0, 0);
                return reader.read(0, parameters);
            } finally {
                reader.dispose();
            }
        }
    }

    /** 이 형식을 읽을 수 있는 reader. 형식이 다르면 {@code null}. */
    private static ImageReader readerFor(ImageInputStream input, String imageIoFormat) {
        if (input == null) {
            return null;
        }
        Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
        if (!readers.hasNext()) {
            return null;
        }
        ImageReader reader = readers.next();
        try {
            if (!imageIoFormat.equalsIgnoreCase(reader.getFormatName())) {
                reader.dispose();
                return null;
            }
        } catch (IOException exception) {
            reader.dispose();
            return null;
        }
        return reader;
    }

    /**
     * 목표 크기까지 줄인다.
     *
     * <p>한 번에 크게 줄이면 가장자리가 거칠어져서, 절반씩 줄여 목표의 두 배 안쪽까지 온 뒤
     * 마지막 한 걸음만 남긴다. 사진을 줄이는 흔한 방법이고 결과가 눈에 띄게 깨끗하다.
     */
    private static BufferedImage scale(BufferedImage source, int targetWidth, int targetHeight,
                                       boolean keepAlpha) {
        BufferedImage current = source;
        int width = source.getWidth();
        int height = source.getHeight();

        while (width / 2 > targetWidth && height / 2 > targetHeight) {
            width /= 2;
            height /= 2;
            current = draw(current, width, height, keepAlpha);
        }
        return draw(current, targetWidth, targetHeight, keepAlpha);
    }

    private static BufferedImage draw(BufferedImage source, int width, int height,
                                      boolean keepAlpha) {
        BufferedImage target = new BufferedImage(width, height,
                keepAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    /** JPEG 는 품질을 정해 굽고, PNG 는 손실이 없어 그대로 굽는다. */
    private static byte[] encode(BufferedImage image, String imageIoFormat) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(imageIoFormat);
        if (!writers.hasNext()) {
            return null;
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(output);
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            if (parameters.canWriteCompressed()) {
                parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                parameters.setCompressionQuality(JPEG_QUALITY);
            }
            writer.write(null, new IIOImage(image, null, null), parameters);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}
