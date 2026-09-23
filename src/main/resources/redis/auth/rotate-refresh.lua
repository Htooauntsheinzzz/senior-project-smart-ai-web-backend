local function integer(v) return v and string.match(v, '^%d+$') and tonumber(v) and tonumber(v) < 9007199254740991 end
local function digest(v) return v and #v == 64 and string.match(v,'^[0-9a-f]+$') end
local function mode(v) return v == 'FULL' or v == 'PASSWORD_CHANGE_ONLY' end
if #KEYS ~= 1 or #ARGV ~= 10 then return 'INVALID' end
local old,new,revision,expectedMode,newMode,now,cap,uid,sid,expectedExpiry = unpack(ARGV)
if not digest(old) or not digest(new) or old == new or not digest(revision)
    or not mode(expectedMode) or not mode(newMode) or not integer(now) or not integer(cap)
    or tonumber(cap) < 1 or tonumber(cap) > 2000 or not string.match(uid,'^[1-9]%d*$') or #uid > 19
    or #sid ~= 22 or not string.match(sid,'^[%w_-]+$') or not integer(expectedExpiry) then return 'INVALID' end
if redis.call('EXISTS',KEYS[1]) == 0 then return 'INVALID' end
if redis.call('TYPE',KEYS[1]).ok ~= 'hash' then return 'INVALID' end
local v=redis.call('HMGET',KEYS[1],'schemaVersion','userId','sessionId','currentHash','credentialRevision','mode','createdAt','lastRotatedAt','absoluteExpiresAt','rotationCount')
if v[1] ~= '1' or v[2] ~= uid or v[3] ~= sid or not digest(v[4]) or not digest(v[5])
    or not mode(v[6]) or not integer(v[7]) or not integer(v[8]) or not integer(v[9])
    or not integer(v[10]) or tonumber(v[10]) > 2000 or tonumber(v[7]) > tonumber(v[8])
    or tonumber(v[8]) >= tonumber(v[9]) or redis.call('PTTL',KEYS[1]) <= 0 then return 'INVALID' end
if tonumber(v[9]) <= tonumber(now) then return 'INVALID' end
if redis.call('HEXISTS',KEYS[1],'used:'..old) == 1 then
    redis.call('DEL',KEYS[1]); return 'REPLAY'
end
if v[4] ~= old then return 'INVALID' end
if v[5] ~= revision or v[6] ~= expectedMode or v[9] ~= expectedExpiry or tonumber(now) < tonumber(v[8])
    or (v[6] == 'PASSWORD_CHANGE_ONLY' and newMode ~= 'PASSWORD_CHANGE_ONLY') then return 'CONFLICT' end
if tonumber(v[10]) >= tonumber(cap) then redis.call('DEL',KEYS[1]); return 'REAUTHENTICATE' end
redis.call('HSET',KEYS[1],'used:'..old,'1','currentHash',new,'lastRotatedAt',now,'mode',newMode,'rotationCount',tonumber(v[10])+1)
redis.call('PEXPIREAT',KEYS[1],v[9])
return 'ROTATED'
