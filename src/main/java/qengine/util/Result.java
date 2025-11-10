package qengine.util;

public class Result<T> {

    private T value;

    private Result(T value) {
        this.value = value;
    }

    public static <T> Result<T> success(T value) {
        return new Result<>(value);
    }

    public static <T> Result<T> failure() {
        return new Result<>(null);
    }

    public T value() {
        return value;
    }

    public boolean succeed() {
        return value != null;
    }

    public boolean failed() {
        return value == null;
    }
}
