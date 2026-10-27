package com.mcport.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mcport.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 带状态条件的更新：读取后状态被他人改动时不生效，返回 0，由调用方判定冲突。
     * {@code remark} 为 null 时保留原值。
     */
    @Update("""
            UPDATE t_order
               SET status = #{newStatus},
                   remark = COALESCE(#{remark}, remark)
             WHERE order_id = #{orderId}
               AND status = #{expectedStatus}
            """)
    int updateStatus(@Param("orderId") String orderId,
                     @Param("expectedStatus") String expectedStatus,
                     @Param("newStatus") String newStatus,
                     @Param("remark") String remark);
}
