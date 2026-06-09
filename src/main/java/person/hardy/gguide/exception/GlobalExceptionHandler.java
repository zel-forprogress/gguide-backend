package person.hardy.gguide.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import person.hardy.gguide.model.vo.ResultVO;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ResultVO<?>> handleAccessDeniedException(AccessDeniedException e) {
        return error(HttpStatus.FORBIDDEN, messageOrDefault(e, "没有权限执行此操作"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ResultVO<?>> handleIllegalArgumentException(IllegalArgumentException e) {
        return error(HttpStatus.BAD_REQUEST, messageOrDefault(e, "请求参数不正确"));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ResultVO<?>> handleRuntimeException(RuntimeException e) {
        return error(HttpStatus.BAD_REQUEST, messageOrDefault(e, "请求处理失败"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResultVO<?>> handleException(Exception e) {
        log.error("Unhandled server error", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误");
    }

    private ResponseEntity<ResultVO<?>> error(HttpStatus status, String message) {
        return ResponseEntity
                .status(status)
                .body(ResultVO.error(status.value(), message));
    }

    private String messageOrDefault(Exception e, String fallback) {
        return e.getMessage() == null || e.getMessage().isBlank() ? fallback : e.getMessage();
    }
}
