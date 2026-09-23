if #KEYS ~= 1 or #ARGV ~= 1 or not tonumber(ARGV[1]) or tonumber(ARGV[1]) < 1 then return -1 end
local value=redis.call('GET',KEYS[1])
if value and (not tonumber(value) or tonumber(value) < 0) then return -1 end
if value and tonumber(value) >= tonumber(ARGV[1]) then return 0 end
local count=redis.call('INCR',KEYS[1])
if count == 1 then redis.call('EXPIRE',KEYS[1],60) end
return 1
