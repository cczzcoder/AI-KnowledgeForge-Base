UPDATE chat_message
SET feedback = 'POSITIVE'
WHERE feedback IS NOT NULL
  AND LOWER(TRIM(feedback)) IN ('like', 'positive', 'thumbs_up');

UPDATE chat_message
SET feedback = 'NEGATIVE'
WHERE feedback IS NOT NULL
  AND LOWER(TRIM(feedback)) IN ('dislike', 'negative', 'thumbs_down');

UPDATE chat_message
SET feedback = NULL
WHERE feedback IS NOT NULL
  AND feedback NOT IN ('POSITIVE', 'NEGATIVE');
