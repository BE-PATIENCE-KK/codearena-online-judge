package com.codearena.codearena.dto;

/**
 * 判题结果。
 *
 * <p>Day 8 的判题器只需要三个信息：判题状态、执行耗时、提示信息。
 * 提示信息用于保存编译错误、运行错误等日志，正常通过（AC）时为 null。</p>
 */
public class JudgeResult {

    /** 通过 */
    public static final String AC = "AC";
    /** 答案错误 */
    public static final String WA = "WA";
    /** 运行超时 */
    public static final String TLE = "TLE";
    /** 运行错误 */
    public static final String RE = "RE";
    /** 编译错误 */
    public static final String CE = "CE";
    /** 系统错误：语言不支持、题目缺少标准答案、判题器自身异常 */
    public static final String SE = "SE";

    private final String status;

    private final Integer timeUsed;

    private final String message;

    public JudgeResult(String status, Integer timeUsed, String message) {
        this.status = status;
        this.timeUsed = timeUsed;
        this.message = message;
    }

    public static JudgeResult of(String status, long timeUsed, String message) {
        return new JudgeResult(status, (int) Math.max(timeUsed, 0), message);
    }

    public static JudgeResult error(String status, String message) {
        return new JudgeResult(status, 0, message);
    }

    public String getStatus() {
        return status;
    }

    public Integer getTimeUsed() {
        return timeUsed;
    }

    public String getMessage() {
        return message;
    }
}
