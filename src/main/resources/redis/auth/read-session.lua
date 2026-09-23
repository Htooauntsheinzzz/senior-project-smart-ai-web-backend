-- Read metadata and TTL atomically; never load the potentially large replay history.
if #KEYS ~= 1 or redis.call('TYPE',KEYS[1]).ok ~= 'hash' or redis.call('PTTL',KEYS[1]) <= 0 then return {} end
return redis.call('HMGET',KEYS[1],'schemaVersion','userId','sessionId','currentHash','credentialRevision',
    'mode','createdAt','lastRotatedAt','absoluteExpiresAt','rotationCount')
