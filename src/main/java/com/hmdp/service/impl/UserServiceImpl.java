package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import com.hmdp.utils.JwtUtils;
import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    private final JwtUtils jwtUtils;

    @Autowired
    public UserServiceImpl(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }
    @Override
    public Result sendCode(String phone, HttpSession session){
        //检验
        if(RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("Invalid phone number!");
        }
        String blockKey = "login:blocked:" + phone;
        Boolean isBlocked = stringRedisTemplate.hasKey(blockKey);
        if (Boolean.TRUE.equals(isBlocked)) {
            return Result.fail("你已经太多次失败 请稍后再试");
        }
        String code = RandomUtil.randomNumbers(6);
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        log.debug("验证码发送成功, 验证码:{}", code);


        return Result.ok();
    }


    @Override
    public Result sign() {
        //1. 获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2. 获取日期
        LocalDateTime now = LocalDateTime.now();
        //3. 拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        //4. 获取今天是当月第几天(1~31)
        int dayOfMonth = now.getDayOfMonth();
        //5. 写入Redis  BITSET key offset 1
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }
    @Override
    public Result signCount() {
        //1. 获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2. 获取日期
        LocalDateTime now = LocalDateTime.now();
        //3. 拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        //4. 获取今天是当月第几天(1~31)
        int dayOfMonth = now.getDayOfMonth();
        //5. 获取截止至今日的签到记录  BITFIELD key GET uDay 0
        List<Long> result = stringRedisTemplate.opsForValue().bitField(key, BitFieldSubCommands.create()
                .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        if (result == null || result.isEmpty()) {
            return Result.ok(0);
        }
        //6. 循环遍历
        int count = 0;
        Long num = result.get(0);
        while (true) {
            if ((num & 1) == 0) {
                break;
            } else
                count++;
            //数字右移，抛弃最后一位
            num >>>= 1;
        }
        return Result.ok(count);
    }


    @Override
    public Result loginWithJwT(LoginFormDTO loginForm) {

        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("Invalid phone number!");
        }

        // 2. 验证验证码是否正确
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误");
        }

        // 3. 查询用户是否存在
        User user = query().eq("phone", phone).one();
        if(user==null){
            user=createUserWithphone(phone);
        }

        // 4. 生成 JWT Token
        String token = jwtUtils.createJwtToken(user);

        // 5. 删除 Redis 中的验证码
        stringRedisTemplate.delete(LOGIN_CODE_KEY + phone);

        // 6. 返回生成的 Token
        return Result.ok(token);
    }


    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone=loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("Invalid phone number!");
        }
        Object cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code=loginForm.getCode();
        if(cacheCode==null||!cacheCode.toString().equals(code)){
            String failKey = "login:fail:" + phone;
            Long failCount = stringRedisTemplate.opsForValue().increment(failKey);

            // 设置失败次数记录的过期时间（例如 1 分钟）
            if (failCount == 1) {
                stringRedisTemplate.expire(failKey, 1, TimeUnit.MINUTES);
            }

            // 如果失败次数超过 3 次，限制登录
            if (failCount > 3) {
                stringRedisTemplate.opsForValue().set("login:blocked:" + phone, "1", 10, TimeUnit.MINUTES);
                return Result.fail("请稍后重试");
            }
            return Result.fail("验证码错误");
        }
        stringRedisTemplate.delete("login:fail:" + phone);
        stringRedisTemplate.delete("login:blocked:" + phone);
        User user=query().eq("phone",phone).one();
        if(user==null){
            user=createUserWithphone(phone);
        }
        String token=UUID.randomUUID().toString(true);
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        HashMap<String, String > userMap = new HashMap<>();
        userMap.put("icon", userDTO.getIcon());
        userMap.put("id", String.valueOf(userDTO.getId()));
        userMap.put("nickName", userDTO.getNickName());
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        stringRedisTemplate.expire(tokenKey, 30, TimeUnit.MINUTES);
        stringRedisTemplate.delete(LOGIN_CODE_KEY + phone);
        return Result.ok(token);
    }


    @Override
    public Result logout(HttpHeaders headers) {
        String token = headers.getFirst("Authorization");
        String key = RedisConstants.LOGIN_USER_KEY + token;
        stringRedisTemplate.delete(key);  //清空redis中当前用户的缓存
        UserHolder.removeUser();
        log.info(" logout sucess");
        return Result.ok();
    }


    private User createUserWithphone(String phone) {
        User user=new User();
        user.setPhone(phone);
        user.setNickName("user_"+RandomUtil.randomString(10));
        save(user);
        return null;
    }

}
