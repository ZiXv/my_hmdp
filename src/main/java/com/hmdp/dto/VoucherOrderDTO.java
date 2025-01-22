package com.hmdp.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VoucherOrderDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 订单id
     */
    private Long orderId;

    /**
     * 下单用户id
     */
    private Long userId;

    /**
     * 购买代金券id
     */
    private Long voucherId;
}
