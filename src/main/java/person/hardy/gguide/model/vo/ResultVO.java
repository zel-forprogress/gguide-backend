
package person.hardy.gguide.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResultVO<T> {
    private Integer code;
    private String message;
    private T data;

    public static <T> ResultVO<T> success(T data) {
        return new ResultVO<>(200, "success", data);
    }

    public static <T> ResultVO<T> success(String message, T data) {
        return new ResultVO<>(200, message, data);
    }

    public static <T> ResultVO<T> error(String message) {
        return new ResultVO<>(400, message, null);
    }

    public static <T> ResultVO<T> error(Integer code, String message) {
        return new ResultVO<>(code, message, null);
    }
}