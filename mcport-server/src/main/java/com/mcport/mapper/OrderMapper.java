package com.mcport.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mcport.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 按用户查询订单，{@code status} 为空时不加状态条件。分页用参数绑定，不拼 SQL 字符串。
     */
    @Select("""
            <script>
            SELECT * FROM t_order
             WHERE user_id = #{userId}
             <if test="status != null and status != ''">AND status = #{status}</if>
             ORDER BY created_at DESC
             LIMIT #{limit}
            </script>
            """)
    List<Order> selectByUser(@Param("userId") String userId,
                             @Param("status") String status,
                             @Param("limit") int limit);

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
