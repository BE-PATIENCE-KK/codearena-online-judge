-- Day 8：为判题流程准备一道最小可用的题目。
-- 只复用已有的 problem 表字段（sample_input / sample_output / time_limit），不新增表结构。
-- 用 NOT EXISTS 做幂等保护，应用每次启动执行 data.sql 都不会重复插入。
INSERT INTO problem (title, description, difficulty, input_description, output_description,
                     sample_input, sample_output, time_limit, memory_limit)
SELECT 'A+B 问题',
       '读入两个整数 a 和 b，输出它们的和。',
       '简单',
       '一行，包含两个用空格隔开的整数 a 和 b。',
       '一个整数，表示 a + b 的结果。',
       '2 3',
       '5',
       2000,
       128
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM problem WHERE title = 'A+B 问题');
