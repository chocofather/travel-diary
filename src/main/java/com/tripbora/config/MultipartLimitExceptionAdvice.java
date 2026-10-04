package com.tripbora.config;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;

/** Multipart 파싱은 컨트롤러 진입 전에 실패할 수 있으므로 전역에서 413 안내를 반환한다. */
@ControllerAdvice
public class MultipartLimitExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(MultipartLimitExceptionAdvice.class);

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ModelAndView handleMaxUploadSizeExceeded(MaxUploadSizeExceededException exception,
                                                    HttpServletRequest request) {
        Object originalPath = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        log.warn("Multipart request rejected: path={}, contentLength={}, cause={}",
                originalPath instanceof String path ? path : request.getRequestURI(),
                request.getContentLengthLong(),
                exception.getMostSpecificCause().getClass().getSimpleName());
        ModelAndView view = new ModelAndView("error/413");
        view.setStatus(HttpStatus.PAYLOAD_TOO_LARGE);
        return view;
    }
}
