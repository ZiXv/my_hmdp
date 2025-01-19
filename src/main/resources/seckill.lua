-- 订单id
local voucherId = ARGV[1]
-- 用户id
local userId = ARGV[2]
local id = ARGV[3]
-- 优惠券key
local stockKey = 'seckill:stock:'..voucherId
-- 订单key
local orderKey = 'seckill:order:'..voucherId

local stockValue = redis.call('get', stockKey)
if not stockValue or tonumber(stockValue) <= 0 then
    return 1
end

-- 判断用户是否下单
if (redis.call('sismember', orderKey, userId) == 1) then
    return 2
end
-- 扣减库存
redis.call('incrby', stockKey, -1)
-- 将userId存入当前优惠券的set集合
redis.call('sadd', orderKey, userId)

redis.call("xadd", 'stream.orders', '*', 'userId', userId, 'voucherId', voucherId, 'id', id)
return 0