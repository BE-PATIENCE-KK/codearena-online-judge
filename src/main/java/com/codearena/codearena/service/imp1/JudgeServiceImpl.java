package com.codearena.codearena.service.imp1;

import com.codearena.codearena.dto.JudgeResult;
import com.codearena.codearena.entity.Problem;
import com.codearena.codearena.service.JudgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 基础版 Java 判题器。
 *
 * <p>一次判题的完整流程：</p>
 * <ol>
 *     <li>为这次提交创建一个独立的临时目录，并把代码写成 {@code 类名.java}；</li>
 *     <li>用 {@link ProcessBuilder} 执行 {@code javac}，退出码不为 0 就是编译错误（CE）；</li>
 *     <li>用 {@link ProcessBuilder} 执行 {@code java 类名}，同时读取标准输出和标准错误，避免管道缓冲区写满把进程卡死；</li>
 *     <li>超过时间限制就用 {@code destroyForcibly()} 结束进程，判为超时（TLE）；</li>
 *     <li>退出码不为 0 判为运行错误（RE）；</li>
 *     <li>把标准输出和题目标准答案做标准化后比较，得到 AC / WA；</li>
 *     <li>在 finally 里删除临时目录，删除失败只记日志，不影响判题结果。</li>
 * </ol>
 *
 * <p>安全说明：这是教学用途的基础实现，不是安全沙箱。普通 ProcessBuilder 不隔离文件、网络、
 * 系统调用或 CPU；-Xmx 只限制 Java 堆，也不构成完整内存限制。不要在可访问敏感数据或公网的
 * 生产服务器上直接执行不可信代码。</p>
 *
 * <p>注意：判题是同步执行的，会占用一次请求线程，最长不会超过“编译超时 + 运行超时”。</p>
 */
@Service
public class JudgeServiceImpl implements JudgeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(JudgeServiceImpl.class);

    /** 只支持 Java，忽略大小写 */
    private static final String SUPPORTED_LANGUAGE = "java";

    /** 从代码中提取 public class 的类名；提不到就用 Main */
    private static final Pattern PUBLIC_CLASS_PATTERN =
            Pattern.compile("public\\s+class\\s+([A-Za-z_$][A-Za-z0-9_$]*)");

    private static final String DEFAULT_CLASS_NAME = "Main";

    /** 编译最多等 15 秒，正常编译只需要几百毫秒 */
    private static final long COMPILE_TIMEOUT_MILLIS = 15_000L;

    /** 单次最多保留 64KB 输出，防止用户代码疯狂打印把服务端内存打爆 */
    private static final int MAX_OUTPUT_BYTES = 64 * 1024;

    /** 存进数据库的提示信息最长 1000 个字符，和 error_message 字段对齐 */
    private static final int MAX_MESSAGE_LENGTH = 1000;

    /** 限制用户程序最多用 256MB 堆内存，做个最基础的自我保护 */
    private static final String MAX_HEAP = "-Xmx256m";

    /**
     * 强制 javac / java 用 UTF-8 输出。
     *
     * <p>Windows 中文环境下这两个命令默认用系统本地编码（GBK）写标准输出/标准错误，
     * 我们却按 UTF-8 读取，结果是编译错误信息里的中文变成乱码，
     * 更严重的是：如果题目的标准答案本身是中文，用户程序永远判不成 AC。</p>
     */
    private static final List<String> JAVA_ENCODING_OPTIONS = List.of(
            "-Dfile.encoding=UTF-8",
            "-Dstdout.encoding=UTF-8",
            "-Dstderr.encoding=UTF-8");

    /** 同样的选项传给 javac 时需要加 -J 前缀 */
    private static final List<String> JAVAC_ENCODING_OPTIONS = List.of(
            "-J-Dfile.encoding=UTF-8",
            "-J-Dstdout.encoding=UTF-8",
            "-J-Dstderr.encoding=UTF-8");

    private final Path tempRoot;

    private final int defaultTimeLimit;

    public JudgeServiceImpl(@Value("${judge.temp-dir:}") String tempDir,
                            @Value("${judge.default-time-limit:2000}") int defaultTimeLimit) {
        this.tempRoot = (tempDir == null || tempDir.isBlank())
                ? Paths.get(System.getProperty("java.io.tmpdir"), "codearena-judge")
                : Paths.get(tempDir);
        this.defaultTimeLimit = defaultTimeLimit;
    }

    @Override
    public JudgeResult judge(String language, String code, Problem problem) {
        Path workspace = null;
        try {
            if (language == null || !SUPPORTED_LANGUAGE.equalsIgnoreCase(language.trim())) {
                return JudgeResult.error(JudgeResult.SE, "当前判题器只支持 Java，请把语言切换成 Java 后重新提交");
            }

            String expected = problem == null ? null : problem.getSampleOutput();
            if (expected == null || expected.isBlank()) {
                return JudgeResult.error(JudgeResult.SE, "这道题还没有配置标准答案，暂时无法判题");
            }

            // 1. 独立临时目录 + 写入源文件
            Files.createDirectories(tempRoot);
            workspace = Files.createTempDirectory(tempRoot, "submission_");
            String className = resolveClassName(code);
            Path sourceFile = workspace.resolve(className + ".java");
            Files.writeString(sourceFile, code == null ? "" : code, StandardCharsets.UTF_8);

            // 2. 编译：javac -encoding UTF-8 类名.java
            ProcessResult compile = run(compileCommand(className), workspace, null, COMPILE_TIMEOUT_MILLIS);
            if (compile.isTimedOut()) {
                return JudgeResult.of(JudgeResult.CE, compile.timeUsed,
                        "编译超时，请检查代码后重试\n" + compile.combinedOutput());
            }
            if (compile.exitCode != 0) {
                String message = compile.combinedOutput();
                return JudgeResult.of(JudgeResult.CE, compile.timeUsed,
                        message.isEmpty() ? "编译失败：javac 没有输出错误信息" : message);
            }

            // 3. 运行：java -Xmx256m 类名，并把题目的样例输入喂给标准输入
            int timeLimit = resolveTimeLimit(problem);
            ProcessResult executed = run(runCommand(className), workspace, problem.getSampleInput(), timeLimit);
            if (executed.isTimedOut()) {
                return JudgeResult.of(JudgeResult.TLE, executed.timeUsed,
                        "运行超过时间限制 " + timeLimit + " ms，已强制结束进程");
            }
            if (executed.exitCode != 0) {
                return JudgeResult.of(JudgeResult.RE, executed.timeUsed,
                        "程序非正常退出（exit code = " + executed.exitCode + "）\n" + executed.combinedOutput());
            }

            // 4. 比较标准输出和标准答案
            if (normalize(executed.stdout).equals(normalize(expected))) {
                return JudgeResult.of(JudgeResult.AC, executed.timeUsed, null);
            }
            return JudgeResult.of(JudgeResult.WA, executed.timeUsed,
                    abbreviate("标准答案：\n" + normalize(expected) + "\n你的输出：\n" + normalize(executed.stdout)));
        } catch (Exception exception) {
            LOGGER.error("判题过程出现异常", exception);
            // Keep filesystem paths, command details and environment data in server logs only.
            return JudgeResult.error(JudgeResult.SE, "判题服务暂时异常，请稍后重试");
        } finally {
            deleteQuietly(workspace);
        }
    }

    /**
     * 启动一个进程，读取输出，并处理超时。
     *
     * @param timeoutMillis 从进程启动开始计算的最长等待时间（毫秒）
     */
    private ProcessResult run(List<String> command, Path workspace, String stdin, long timeoutMillis)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workspace.toFile());

        long start = System.currentTimeMillis();
        Process process = builder.start();

        // 标准输出和标准错误必须同时被读走，否则缓冲区写满时子进程会被阻塞
        StreamCollector stdout = StreamCollector.start(process.getInputStream());
        StreamCollector stderr = StreamCollector.start(process.getErrorStream());

        // 把样例输入写进标准输入后立刻关闭，避免程序卡在读取输入上
        try (OutputStream outputStream = process.getOutputStream()) {
            if (stdin != null && !stdin.isEmpty()) {
                outputStream.write(stdin.getBytes(StandardCharsets.UTF_8));
            }
            outputStream.flush();
        } catch (IOException ignored) {
            // 程序不读输入时提前退出，这里忽略即可
        }

        boolean finished = false;
        try {
            finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
        } finally {
            if (!finished) {
                // A submitted program may start children. Terminate them too when the deadline expires.
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                try {
                    process.waitFor(5, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
            closeQuietly(process.getInputStream());
            closeQuietly(process.getErrorStream());
            stdout.await();
            stderr.await();
        }
        return new ProcessResult(finished, finished ? process.exitValue() : -1,
                System.currentTimeMillis() - start, stdout.text(), stderr.text());
    }

    private void closeQuietly(InputStream stream) {
        try {
            stream.close();
        } catch (IOException exception) {
            LOGGER.debug("关闭判题进程输出流失败：{}", exception.getMessage());
        }
    }

    private List<String> compileCommand(String className) {
        List<String> command = new ArrayList<>();
        command.add(javaTool("javac"));
        command.addAll(JAVAC_ENCODING_OPTIONS);
        command.add("-encoding");
        command.add("UTF-8");
        command.add(className + ".java");
        return command;
    }

    private List<String> runCommand(String className) {
        List<String> command = new ArrayList<>();
        command.add(javaTool("java"));
        command.add(MAX_HEAP);
        command.addAll(JAVA_ENCODING_OPTIONS);
        command.add(className);
        return command;
    }

    /**
     * 优先用当前 JVM 所在 JDK 的 javac / java，取不到再退回 PATH 里的命令。
     */
    private String javaTool(String name) {
        String executable = System.getProperty("os.name", "").toLowerCase().contains("win")
                ? name + ".exe"
                : name;
        Path path = Paths.get(System.getProperty("java.home"), "bin", executable);
        return Files.exists(path) ? path.toString() : name;
    }

    /**
     * 取出 public class 后面的类名作为主类名，取不到就按 Main 处理。
     * 返回值只可能是合法 Java 标识符，不会出现路径分隔符。
     */
    private String resolveClassName(String code) {
        if (code != null) {
            Matcher matcher = PUBLIC_CLASS_PATTERN.matcher(code);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return DEFAULT_CLASS_NAME;
    }

    /**
     * 题目没配时间限制时用默认值；同时做上下限兜底：
     * JVM 启动本身要几百毫秒，限制太低容易把正常程序误判成 TLE。
     */
    private int resolveTimeLimit(Problem problem) {
        Integer limit = problem == null ? null : problem.getTimeLimit();
        if (limit == null || limit <= 0) {
            limit = defaultTimeLimit;
        }
        return Math.min(Math.max(limit, 1000), 10000);
    }

    /**
     * 比较前的标准化：统一换行符、去掉每行行尾空白、去掉首尾空白和空行。
     */
    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        String unified = text.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder builder = new StringBuilder();
        for (String line : unified.split("\n", -1)) {
            builder.append(line.stripTrailing()).append('\n');
        }
        return builder.toString().strip();
    }

    /**
     * 递归删除临时目录。任何一步失败都只记日志，绝不影响判题结果。
     */
    private void deleteQuietly(Path directory) {
        if (directory == null) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    LOGGER.warn("删除判题临时文件失败：{}", path, exception);
                }
            });
        } catch (IOException exception) {
            LOGGER.warn("清理判题临时目录失败：{}", directory, exception);
        }
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        String suffix = "…（已截断）";
        return trimmed.length() <= MAX_MESSAGE_LENGTH
                ? trimmed
                : trimmed.substring(0, MAX_MESSAGE_LENGTH - suffix.length()) + suffix;
    }

    /**
     * 一次进程执行的结果。
     */
    private static final class ProcessResult {

        private final boolean finished;

        private final int exitCode;

        private final long timeUsed;

        private final String stdout;

        private final String stderr;

        private ProcessResult(boolean finished, int exitCode, long timeUsed, String stdout, String stderr) {
            this.finished = finished;
            this.exitCode = exitCode;
            this.timeUsed = timeUsed;
            this.stdout = stdout;
            this.stderr = stderr;
        }

        /**
         * 判断超时用的是“进程是否在限制时间内结束”，而不是 exitCode（被强杀也可能是 1）。
         */
        private boolean isTimedOut() {
            return !finished;
        }

        private String combinedOutput() {
            String out = stdout == null ? "" : stdout.strip();
            String err = stderr == null ? "" : stderr.strip();
            String text;
            if (err.isEmpty()) {
                text = out;
            } else if (out.isEmpty()) {
                text = err;
            } else {
                text = out + "\n" + err;
            }
            String abbreviated = abbreviate(text);
            return abbreviated == null ? "" : abbreviated;
        }
    }

    /**
     * 后台把子进程的输出读干净，最多保留 {@link #MAX_OUTPUT_BYTES} 字节。
     */
    private static final class StreamCollector {

        private final InputStream inputStream;

        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        private final Thread thread;

        private StreamCollector(InputStream inputStream) {
            this.inputStream = inputStream;
            this.thread = new Thread(this::read, "judge-stream-reader");
            this.thread.setDaemon(true);
        }

        private static StreamCollector start(InputStream inputStream) {
            StreamCollector collector = new StreamCollector(inputStream);
            collector.thread.start();
            return collector;
        }

        private void read() {
            byte[] chunk = new byte[4096];
            try {
                int length;
                while ((length = inputStream.read(chunk)) != -1) {
                    if (buffer.size() < MAX_OUTPUT_BYTES) {
                        buffer.write(chunk, 0, Math.min(length, MAX_OUTPUT_BYTES - buffer.size()));
                    }
                }
            } catch (IOException exception) {
                LOGGER.debug("读取判题进程输出结束：{}", exception.getMessage());
            }
        }

        private void await() {
            try {
                thread.join(1000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }

        private String text() {
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }
}
