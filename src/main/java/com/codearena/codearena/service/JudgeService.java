package com.codearena.codearena.service;

import com.codearena.codearena.dto.JudgeResult;
import com.codearena.codearena.entity.Problem;

/**
 * 判题服务。
 *
 * <p>职责：把用户提交的代码编译、运行，再和题目标准答案比较，返回判题结果。
 * 本阶段只支持 Java，且只比较题目的样例输入 / 样例输出。</p>
 */
public interface JudgeService {

    /**
     * 对一次提交进行判题。
     *
     * @param language 提交语言，目前只支持 Java
     * @param code     用户提交的源代码
     * @param problem  题目，提供样例输入、标准答案和时间限制
     * @return 判题结果，不会返回 null，也不会向外抛异常
     */
    JudgeResult judge(String language, String code, Problem problem);
}
