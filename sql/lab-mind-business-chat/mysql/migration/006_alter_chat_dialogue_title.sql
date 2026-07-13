-- lab-mind-business-chat 会话标题迁移脚本
ALTER TABLE lab_mind_chat_dialogue
    ADD COLUMN dialogue_title VARCHAR(100) NOT NULL DEFAULT '' COMMENT '会话标题'
    AFTER dialogue_code;

UPDATE lab_mind_chat_dialogue d
INNER JOIN lab_mind_chat_exchange le
ON le.id = (
    SELECT e.id
    FROM lab_mind_chat_exchange e
    WHERE e.dialogue_code = d.dialogue_code
      AND e.status = 1
    ORDER BY e.create_time DESC, e.id DESC
    LIMIT 1
)
AND le.status = 1
SET d.dialogue_title = LEFT(TRIM(le.user_prompt), 30)
WHERE d.status = 1
  AND d.dialogue_title = '';
