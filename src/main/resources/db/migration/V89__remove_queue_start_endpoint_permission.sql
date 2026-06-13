DELETE FROM endpoint_permissions
WHERE http_method = 'POST'
  AND endpoint_pattern = '/queue/queue-start';
