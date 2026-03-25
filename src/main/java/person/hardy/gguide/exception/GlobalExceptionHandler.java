package person.hardy.gguide.exception;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import person.hardy.gguide.model.vo.ResultVO;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 运行时异常
    @ExceptionHandler(RuntimeException.class)
    public ResultVO<?> handleRuntimeException(RuntimeException e) {
        return ResultVO.error(e.getMessage());
    }
    // 所有异常
    @ExceptionHandler(Exception.class)
    public ResultVO<?> handleException(Exception e) {
        return ResultVO.error(500, "服务器内部错误");
    }
}