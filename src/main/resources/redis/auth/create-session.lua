-- All inputs are validated before the first write. 0 means collision, -1 invalid.
local function integer(v) return v and string.match(v, '^%d+$') and tonumber(v) and tonumber(v) < 9007199254740991 end
local function digest(v) return v and #v == 64 and string.match(v, '^[0-9a-f]+$') end
if #KEYS ~= 1 or #ARGV ~= 7 then return -1 end
local uid,sid,hash,revision,mode,created,expires = unpack(ARGV)
if not string.match(uid,'^[1-9]%d*$') or #uid > 19 or #sid ~= 22 or not string.match(sid,'^[%w_-]+$')
    or not digest(hash) or not digest(revision) or (mode ~= 'FULL' and mode ~= 'PASSWORD_CHANGE_ONLY')
    or not integer(created) or not integer(expires) or tonumber(expires) <= tonumber(created) then return -1 end
if redis.call('EXISTS',KEYS[1]) == 1 then return 0 end
redis.call('HSET',KEYS[1], 'schemaVersion','1', 'userId',uid, 'sessionId',sid,
    'currentHash',hash, 'credentialRevision',revision, 'mode',mode, 'createdAt',created,
    'lastRotatedAt',created, 'absoluteExpiresAt',expires, 'rotationCount','0')
redis.call('PEXPIREAT',KEYS[1],expires)
return 1
