local workKey = KEYS[1]
local dataCenterKey = KEYS[2]
local maxValue = tonumber(ARGV[1])

local workId = tonumber(redis.call('get', workKey))
local dataCenterId = tonumber(redis.call('get', dataCenterKey))

if workId == nil then
    workId = -1
end

if dataCenterId == nil then
    dataCenterId = 0
end

if workId < maxValue then
    workId = workId + 1
else
    workId = 0
    if dataCenterId < maxValue then
        dataCenterId = dataCenterId + 1
    else
        dataCenterId = 0
    end
end

redis.call('set', workKey, workId)
redis.call('set', dataCenterKey, dataCenterId)

return cjson.encode({
    workId = workId,
    dataCenterId = dataCenterId
})
